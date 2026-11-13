package it.unibo.tablut.sexyipa.search;

import static it.unibo.tablut.sexyipa.search.AlphaBetaSearcher.WIN;
import static org.junit.jupiter.api.Assertions.*;

import it.unibo.tablut.sexyipa.engine.Board;
import it.unibo.tablut.sexyipa.engine.BoardAshton;
import it.unibo.tablut.sexyipa.eval.Evaluator;
import it.unibo.tablut.sexyipa.eval.SimpleEvaluator;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class AlphaBetaSearcherTest {

    private final Evaluator eval = new SimpleEvaluator();

    private static BoardAshton board(boolean whiteToMove, String... rows) {
        byte[] cells = new byte[81];
        for (int i = 0; i < 81; i++) {
            cells[i] = switch (rows[i / 9].charAt(i % 9)) {
                case 'W' -> Board.WHITE;
                case 'B' -> Board.BLACK;
                case 'K' -> Board.KING;
                default -> Board.EMPTY;
            };
        }
        return new BoardAshton(cells, whiteToMove);
    }

    private static int sq(String name) {
        return (name.charAt(1) - '1') * 9 + (name.charAt(0) - 'a');
    }

    /** Minimax senza potature, con le stesse regole terminali: il riferimento per l'alpha-beta. */
    private int minimax(Board b, int depth, int ply) {
        if (b.result() != Board.ONGOING) {
            return -(WIN - ply);
        }
        if (depth == 0) {
            return eval.evaluate(b);
        }
        int[] moves = new int[300];
        int n = b.generateMoves(moves);
        if (n == 0) {
            return -(WIN - ply);
        }
        int best = -AlphaBetaSearcher.INF;
        for (int i = 0; i < n; i++) {
            b.makeMove(moves[i]);
            best = Math.max(best, -minimax(b, depth - 1, ply + 1));
            b.undoMove();
        }
        return best;
    }

    @Test
    void alphaBetaMatchesMinimax() {
        SplittableRandom rnd = new SplittableRandom(7);
        AlphaBetaSearcher searcher = new AlphaBetaSearcher(eval);
        int[] moves = new int[300];
        for (int game = 0; game < 30; game++) {
            BoardAshton b = new BoardAshton();
            // Posizione casuale: 0-29 mosse casuali dall'inizio
            int plies = rnd.nextInt(30);
            for (int i = 0; i < plies && b.result() == Board.ONGOING; i++) {
                int n = b.generateMoves(moves);
                if (n == 0) {
                    break;
                }
                b.makeMove(moves[rnd.nextInt(n)]);
            }
            if (b.result() != Board.ONGOING) {
                continue;
            }
            for (int depth = 1; depth <= 3; depth++) {
                searcher.search(b, depth);
                assertEquals(minimax(b, depth, 0), searcher.lastScore(), "game " + game + " depth " + depth);
            }
        }
    }

    @Test
    void whiteTakesImmediateEscape() {
        BoardAshton b = board(true,
            ".........",
            ".K.......",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
            "....B....",
            ".........");
        AlphaBetaSearcher searcher = new AlphaBetaSearcher(eval);
        int move = searcher.search(b, 3);
        b.makeMove(move);
        assertEquals(Board.WHITE_WINS, b.result());
        assertEquals(WIN - 1, searcher.lastScore());
    }

    @Test
    void blackCapturesKingWhenPossible() {
        // Re in d2 con il campo e2 alle spalle: il nero in a2 cattura arrivando in c2
        BoardAshton b = board(false,
            ".........",
            "B..K.....",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........");
        AlphaBetaSearcher searcher = new AlphaBetaSearcher(eval);
        int move = searcher.search(b, 2);
        assertEquals(sq("a2") * 81 + sq("c2"), move);
        assertEquals(WIN - 1, searcher.lastScore());
    }

    @Test
    void blackBlocksEscapeInOne() {
        // Il re in b3 ha una sola uscita libera, a3 (b1 è occupata, c3 bloccata dal bianco): il nero deve chiuderla
        BoardAshton b = board(false,
            ".B.......",
            ".........",
            ".KW......",
            "B........",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........");
        AlphaBetaSearcher searcher = new AlphaBetaSearcher(eval);
        int move = searcher.search(b, 2);
        b.makeMove(move);
        assertEquals(Board.BLACK, b.cell(sq("a3")), "il nero deve bloccare l'uscita a3");
        assertTrue(searcher.lastScore() > -WIN / 2, "bloccando, il nero non perde subito");
    }
}
