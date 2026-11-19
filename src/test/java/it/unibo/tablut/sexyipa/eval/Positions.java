package it.unibo.tablut.sexyipa.eval;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import it.unibo.tablut.sexyipa.engine.Board;
import it.unibo.tablut.sexyipa.engine.BoardAshton;
import it.unibo.tablut.sexyipa.engine.Tables;

/** Posizioni per test e benchmark dell'evaluator. */
final class Positions {

    private Positions() {
    }

    /** Scacchiera da diagramma: riga 0 = riga "1" del server. '.' vuoto, W bianco, B nero, K re. */
    static BoardAshton board(boolean whiteToMove, String... rows) {
        byte[] cells = new byte[Tables.CELLS];
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                cells[r * 9 + c] = switch (rows[r].charAt(c)) {
                    case 'W' -> Board.WHITE;
                    case 'B' -> Board.BLACK;
                    case 'K' -> Board.KING;
                    default -> Board.EMPTY;
                };
            }
        }
        return new BoardAshton(cells, whiteToMove);
    }

    static int sq(String name) {
        return (name.charAt(1) - '1') * 9 + (name.charAt(0) - 'a');
    }

    static byte[] cells(Board b) {
        byte[] cells = new byte[Tables.CELLS];
        for (int i = 0; i < Tables.CELLS; i++) {
            cells[i] = b.cell(i);
        }
        return cells;
    }

    /** Stessa posizione, con il turno indicato. */
    static BoardAshton withTurn(Board b, boolean whiteToMove) {
        return new BoardAshton(cells(b), whiteToMove);
    }

    /**
     * Posizioni di partita ancora in corso, ottenute con mosse casuali dalla posizione iniziale.
     * Seed fisso: stesse posizioni a ogni esecuzione.
     */
    static List<BoardAshton> random(int count, long seed) {
        SplittableRandom rng = new SplittableRandom(seed);
        int[] moves = new int[300];
        List<BoardAshton> out = new ArrayList<>(count);
        while (out.size() < count) {
            BoardAshton b = new BoardAshton();
            int plies = 4 + rng.nextInt(80);
            boolean ongoing = true;
            for (int i = 0; i < plies && ongoing; i++) {
                int n = b.generateMoves(moves);
                if (n == 0) {
                    ongoing = false;
                } else {
                    b.makeMove(moves[rng.nextInt(n)]);
                    ongoing = b.result() == Board.ONGOING;
                }
            }
            if (ongoing) {
                out.add(withTurn(b, b.whiteToMove()));
            }
        }
        return out;
    }
}
