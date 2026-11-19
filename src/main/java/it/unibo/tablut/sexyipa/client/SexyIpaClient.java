package it.unibo.tablut.sexyipa.client;

import java.io.IOException;
import java.net.UnknownHostException;

import it.unibo.ai.didattica.competition.tablut.client.TablutClient;
import it.unibo.ai.didattica.competition.tablut.domain.Action;
import it.unibo.ai.didattica.competition.tablut.domain.State;
import it.unibo.ai.didattica.competition.tablut.domain.State.Pawn;
import it.unibo.ai.didattica.competition.tablut.domain.State.Turn;

import it.unibo.tablut.sexyipa.engine.Board;
import it.unibo.tablut.sexyipa.engine.BoardAshton;
import it.unibo.tablut.sexyipa.engine.Tables;
import it.unibo.tablut.sexyipa.eval.Evaluator;
import it.unibo.tablut.sexyipa.eval.AshtonEvaluator;
import it.unibo.tablut.sexyipa.search.AlphaBetaSearcher;
import it.unibo.tablut.sexyipa.search.PositionHistory;
import it.unibo.tablut.sexyipa.search.Searcher;
import it.unibo.tablut.sexyipa.search.TimeManager;

/**
 * Client che collega il motore di ricerca di SexyIpa al server del torneo.
 * <p>
 * Flusso per ogni turno:
 * <ol>
 *     <li>Legge lo stato dal server ({@link State} / {@link Pawn}[][])</li>
 *     <li>Lo converte in una {@link BoardAshton} interna</li>
 *     <li>Lancia la ricerca alpha-beta entro la scadenza calcolata da {@link TimeManager}</li>
 *     <li>Converte la mossa interna (int) in una {@link Action} e la invia al server</li>
 * </ol>
 */
public class SexyIpaClient extends TablutClient {

    private final int timeoutSeconds;
    private final Searcher searcher;
    private boolean firstMove = true;
    /** Posizioni comparse dopo ogni mossa: servono a riconoscere il pareggio per ripetizione. */
    private final PositionHistory history = new PositionHistory();
    /** Il primo stato ricevuto è la posizione iniziale, che il server non conta tra le ripetizioni. */
    private boolean firstState = true;

    public SexyIpaClient(String role, String name, int timeout, String serverIp)
            throws UnknownHostException, IOException {
        super(role, name, timeout, serverIp);
        this.timeoutSeconds = timeout;
        Evaluator evaluator = new AshtonEvaluator();
        AlphaBetaSearcher alphaBeta = new AlphaBetaSearcher(evaluator);
        alphaBeta.setGameHistory(history);
        this.searcher = alphaBeta;
    }

    @Override
    public void run() {
        // 1. Dichiara il nome al server
        try {
            declareName();
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        System.out.println("[SexyIpa] Ruolo: " + getPlayer() + "  timeout=" + timeoutSeconds + "s");

        // 2. Game loop
        while (true) {
            // Leggi lo stato aggiornato dal server
            try {
                read();
            } catch (ClassNotFoundException | IOException e) {
                System.err.println("[SexyIpa] Errore nella lettura dello stato dal server");
                e.printStackTrace();
                System.exit(1);
            }
            long receivedNanos = System.nanoTime();

            State state = getCurrentState();
            Turn turn = state.getTurn();

            // Controlla condizioni terminali
            if (turn.equals(Turn.WHITEWIN)) {
                System.out.println("[SexyIpa] " +
                        (getPlayer().equals(Turn.WHITE) ? "VITTORIA!" : "SCONFITTA."));
                System.exit(0);
            }
            if (turn.equals(Turn.BLACKWIN)) {
                System.out.println("[SexyIpa] " +
                        (getPlayer().equals(Turn.BLACK) ? "VITTORIA!" : "SCONFITTA."));
                System.exit(0);
            }
            if (turn.equals(Turn.DRAW)) {
                System.out.println("[SexyIpa] PAREGGIO.");
                System.exit(0);
            }

            // Converti lo stato del server nella rappresentazione interna e registralo nella storia
            // (tutti gli stati tranne quello iniziale, come fa il server per il pareggio)
            BoardAshton board = fromServerState(state);
            if (!firstState) {
                history.add(board.hash());
            }
            firstState = false;

            // È il mio turno?
            if ((getPlayer().equals(Turn.WHITE) && turn.equals(Turn.WHITE))
                    || (getPlayer().equals(Turn.BLACK) && turn.equals(Turn.BLACK))) {

                // Calcola la scadenza
                long deadline = TimeManager.deadline(receivedNanos, timeoutSeconds, firstMove);
                firstMove = false;

                // Cerca la mossa migliore
                int move = searcher.bestMove(board, deadline);

                if (move < 0) {
                    // Nessuna mossa legale: il server ci dichiarerà perdenti
                    System.err.println("[SexyIpa] Nessuna mossa legale disponibile!");
                    continue;
                }

                // Converti e invia
                Action action = toAction(move, turn);
                System.out.println("[SexyIpa] Mossa: " + action);
                if (searcher instanceof AlphaBetaSearcher abs) {
                    System.out.println("[SexyIpa] depth=" + abs.completedDepth()
                            + " score=" + abs.lastScore()
                            + " nodes=" + abs.nodes());
                }

                try {
                    write(action);
                } catch (ClassNotFoundException | IOException e) {
                    System.err.println("[SexyIpa] Errore nell'invio della mossa");
                    e.printStackTrace();
                    System.exit(1);
                }

            } else {
                System.out.println("[SexyIpa] In attesa della mossa dell'avversario...");
            }
        }
    }

    // ─────────────────── conversione stato ───────────────────

    /**
     * Converte lo {@link State} del server (Pawn[][]) in una {@link BoardAshton}.
     * <p>
     * Mappatura dei pezzi:
     * <ul>
     *     <li>{@code Pawn.WHITE}  → {@link Board#WHITE}</li>
     *     <li>{@code Pawn.BLACK}  → {@link Board#BLACK}</li>
     *     <li>{@code Pawn.KING}   → {@link Board#KING}</li>
     *     <li>tutto il resto      → {@link Board#EMPTY}</li>
     * </ul>
     * Il turno del server (WHITE/BLACK) definisce chi muove.
     */
    static BoardAshton fromServerState(State state) {
        Pawn[][] serverBoard = state.getBoard();
        byte[] cells = new byte[Tables.CELLS];

        for (int r = 0; r < Tables.SIZE; r++) {
            for (int c = 0; c < Tables.SIZE; c++) {
                Pawn p = serverBoard[r][c];
                byte val = Board.EMPTY;
                if (p == Pawn.WHITE)     val = Board.WHITE;
                else if (p == Pawn.BLACK) val = Board.BLACK;
                else if (p == Pawn.KING)  val = Board.KING;
                // EMPTY e THRONE → EMPTY (il castello è codificato nella geometria statica)
                cells[Tables.index(r, c)] = val;
            }
        }

        boolean whiteToMove = state.getTurn().equals(Turn.WHITE);
        return new BoardAshton(cells, whiteToMove);
    }

    // ─────────────────── conversione mossa ───────────────────

    /**
     * Converte una mossa interna ({@code from * 81 + to}) in una {@link Action}
     * compatibile con il protocollo del server.
     * <p>
     * Il server usa la notazione tipo scacchi: colonna (a-i) + riga (1-9).
     * L'indice cella = riga * 9 + colonna, dove riga 0 → "1", colonna 0 → "a".
     */
    static Action toAction(int move, Turn turn) {
        int from = move / Tables.CELLS;
        int to = move % Tables.CELLS;

        String fromStr = cellToString(from);
        String toStr = cellToString(to);

        try {
            return new Action(fromStr, toStr, turn);
        } catch (IOException e) {
            // Non dovrebbe mai succedere con stringhe di lunghezza 2
            throw new RuntimeException("Errore nella creazione dell'Action: " + fromStr + " -> " + toStr, e);
        }
    }

    /**
     * Converte un indice di cella (0..80) nella notazione del server (es. "e5").
     * Colonna 0 → 'a', riga 0 → '1'.
     */
    private static String cellToString(int index) {
        int row = Tables.row(index);
        int col = Tables.col(index);
        char colChar = (char) ('a' + col);
        int rowNum = row + 1;
        return "" + colChar + rowNum;
    }
}
