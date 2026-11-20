package it.unibo.tablut.sexyipa.engine;

import static it.unibo.tablut.sexyipa.engine.Tables.*;

/**
 * Scacchiera Ashton. Le regole replicano GameAshtonTablut dei docenti.
 * Le ripetizioni (pareggio) non sono gestite qui: le rileva la ricerca tramite hash().
 */
public class BoardAshton implements Board {
    /** Profondità massima dello stack di undo (mosse annullabili una dopo l'altra). */
    public static final int MAX_PLY = 128;

    private final byte[] board = new byte[CELLS];
    private int kingPos;
    private int whitePieces;  // soldati, re escluso
    private int blackPieces;
    private boolean whiteTurn;
    private long hash;
    private int result = ONGOING;

    // Stack di undo, preallocati: makeMove/undoMove non allocano
    private final int[] moveStack = new int[MAX_PLY];
    private final int[] capturedStack = new int[MAX_PLY]; // bit d = catturato il vicino in direzione d
    private final long[] hashStack = new long[MAX_PLY];
    private int ply;

    /** Posizione iniziale Ashton, muove il Bianco. */
    public BoardAshton() {
        this(initialCells(), true);
    }

    /** Posizione arbitraria (per test e per la conversione dallo stato del server). */
    public BoardAshton(byte[] cells, boolean whiteToMove) {
        System.arraycopy(cells, 0, board, 0, CELLS);
        kingPos = -1;
        for (int i = 0; i < CELLS; i++) {
            switch (board[i]) {
                case WHITE -> whitePieces++;
                case BLACK -> blackPieces++;
                case KING -> kingPos = i;
                default -> { }
            }
        }
        whiteTurn = whiteToMove;
        hash = computeHash();
    }

    private static byte[] initialCells() {
        byte[] cells = new byte[CELLS];
        // Neri: occupano tutte le 16 celle di campo
        for (int i = 0; i < CELLS; i++) {
            if (CELL_TYPE[i] == CAMP) {
                cells[i] = BLACK;
            }
        }
        // Bianchi: croce attorno al castello (e3 e4 e6 e7, c5 d5 f5 g5)
        int[] whites = {
            index(2, 4), index(3, 4), index(5, 4), index(6, 4),
            index(4, 2), index(4, 3), index(4, 5), index(4, 6),
        };
        for (int i : whites) {
            cells[i] = WHITE;
        }
        cells[CASTLE_POS] = KING;
        return cells;
    }

    /** Hash ricalcolato da zero; makeMove deve tenere hash sempre uguale a questo valore. */
    public long computeHash() {
        long h = 0;
        for (int i = 0; i < CELLS; i++) {
            if (board[i] != EMPTY) {
                h ^= ZOBRIST[i][board[i]];
            }
        }
        if (!whiteTurn) {
            h ^= ZOBRIST_BLACK_TO_MOVE;
        }
        return h;
    }

    @Override
    public int generateMoves(int[] out) {
        int n = 0;
        for (int from = 0; from < CELLS; from++) {
            byte p = board[from];
            boolean mine = whiteTurn ? (p == WHITE || p == KING) : p == BLACK;
            if (!mine) {
                continue;
            }
            // -1 se il pezzo non è su un campo: allora nessuna cella di campo è attraversabile
            int group = CAMP_GROUP[from];
            for (int d = 0; d < 4; d++) {
                for (int to : RAYS[from][d]) {
                    if (board[to] != EMPTY || CELL_TYPE[to] == CASTLE) {
                        break;
                    }
                    // Un nero ancora nel campo di partenza si muove solo dentro lo stesso gruppo
                    if (CELL_TYPE[to] == CAMP && CAMP_GROUP[to] != group) {
                        break;
                    }
                    out[n++] = from * CELLS + to;
                }
            }
        }
        return n;
    }

    @Override
    public void makeMove(int move) {
        int from = move / CELLS;
        int to = move % CELLS;
        byte piece = board[from];

        moveStack[ply] = move;
        hashStack[ply] = hash;

        board[from] = EMPTY;
        board[to] = piece;
        hash ^= ZOBRIST[from][piece] ^ ZOBRIST[to][piece];
        if (piece == KING) {
            kingPos = to;
        }

        int captured = 0;
        if (whiteTurn) {
            for (int d = 0; d < 4; d++) {
                int n = NEIGHBOR[to][d];
                if (n < 0 || board[n] != BLACK) {
                    continue;
                }
                int o = NEIGHBOR[n][d];
                if (o >= 0 && (board[o] == WHITE || board[o] == KING || ANVIL_FOR_WHITE[o])) {
                    board[n] = EMPTY;
                    hash ^= ZOBRIST[n][BLACK];
                    blackPieces--;
                    captured |= 1 << d;
                }
            }
            if (piece == KING && CELL_TYPE[to] == ESCAPE) {
                result = WHITE_WINS;
            }
        } else {
            for (int d = 0; d < 4; d++) {
                int n = NEIGHBOR[to][d];
                if (n < 0) {
                    continue;
                }
                if (board[n] == WHITE) {
                    int o = NEIGHBOR[n][d];
                    if (o >= 0 && (board[o] == BLACK || ANVIL_FOR_BLACK[o])) {
                        board[n] = EMPTY;
                        hash ^= ZOBRIST[n][WHITE];
                        whitePieces--;
                        captured |= 1 << d;
                    }
                } else if (board[n] == KING && kingCaptured(n, d)) {
                    result = BLACK_WINS;
                }
            }
        }
        capturedStack[ply] = captured;

        whiteTurn = !whiteTurn;
        hash ^= ZOBRIST_BLACK_TO_MOVE;
        ply++;
    }

    /** Il re in k, attaccato dal nero appena arrivato dalla direzione opposta a d, è catturato? */
    private boolean kingCaptured(int k, int d) {
        if (CASTLE_AREA[k]) {
            // Sul castello: 4 neri. Accanto al castello: neri sui 3 lati diversi dal castello.
            for (int e = 0; e < 4; e++) {
                int nb = NEIGHBOR[k][e];
                if (nb != CASTLE_POS && board[nb] != BLACK) {
                    return false;
                }
            }
            return true;
        }
        // Altrove: nero o campo (centrali compresi) dal lato opposto
        int o = NEIGHBOR[k][d];
        return o >= 0 && (board[o] == BLACK || CELL_TYPE[o] == CAMP);
    }

    @Override
    public void undoMove() {
        ply--;
        whiteTurn = !whiteTurn;
        int move = moveStack[ply];
        int from = move / CELLS;
        int to = move % CELLS;
        byte piece = board[to];

        board[to] = EMPTY;
        board[from] = piece;
        if (piece == KING) {
            kingPos = from;
        }

        int captured = capturedStack[ply];
        if (captured != 0) {
            byte victim = whiteTurn ? BLACK : WHITE;
            for (int d = 0; d < 4; d++) {
                if ((captured & (1 << d)) != 0) {
                    board[NEIGHBOR[to][d]] = victim;
                    if (victim == BLACK) {
                        blackPieces++;
                    } else {
                        whitePieces++;
                    }
                }
            }
        }

        hash = hashStack[ply];
        // Si muove solo in partite in corso, quindi prima della mossa il risultato era ONGOING
        result = ONGOING;
    }

    /**
     * Re su un'uscita o re catturato. Il caso "nessuna mossa disponibile" (chi muove perde)
     * lo gestisce la ricerca, quando generateMoves restituisce 0.
     */
    @Override
    public int result() {
        return result;
    }

    @Override
    public long hash() {
        return hash;
    }

    @Override
    public byte cell(int index) {
        return board[index];
    }

    @Override
    public boolean whiteToMove() {
        return whiteTurn;
    }

    @Override
    public int kingPos() {
        return kingPos;
    }

    @Override
    public int whiteCount() {
        return whitePieces;
    }

    @Override
    public int blackCount() {
        return blackPieces;
    }
}
