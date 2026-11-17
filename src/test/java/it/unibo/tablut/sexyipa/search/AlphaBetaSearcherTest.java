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
    void bestMoveRespectsDeadlineAndRestoresBoard() {
        AlphaBetaSearcher searcher = new AlphaBetaSearcher(eval);
        BoardAshton b = new BoardAshton();
        byte[] before = new byte[81];
        for (int i = 0; i < 81; i++) {
            before[i] = b.cell(i);
        }
        long hashBefore = b.hash();

        long start = System.nanoTime();
        int move = searcher.bestMove(b, start + 300_000_000L); // 300 ms
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertTrue(elapsedMs < 400, "ha impiegato " + elapsedMs + " ms");
        assertTrue(searcher.completedDepth() >= 1);
        // La ricerca è stata interrotta a metà: la scacchiera deve essere tornata identica
        for (int i = 0; i < 81; i++) {
            assertEquals(before[i], b.cell(i));
        }
        assertEquals(hashBefore, b.hash());
        int[] legal = new int[300];
        int n = b.generateMoves(legal);
        assertTrue(java.util.Arrays.stream(legal, 0, n).anyMatch(m -> m == move), "mossa illegale");
    }

    @Test
    void bestMoveReturnsLegalMoveEvenWithExpiredDeadline() {
        AlphaBetaSearcher searcher = new AlphaBetaSearcher(eval);
        BoardAshton b = new BoardAshton();
        int move = searcher.bestMove(b, System.nanoTime() - 1);
        int[] legal = new int[300];
        int n = b.generateMoves(legal);
        assertTrue(java.util.Arrays.stream(legal, 0, n).anyMatch(m -> m == move));
    }

    @Test
    void bestMoveStopsEarlyOnForcedWin() {
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
        long start = System.nanoTime();
        int move = searcher.bestMove(b, start + 10_000_000_000L); // 10 s, ma deve fermarsi subito
        assertTrue(System.nanoTime() - start < 1_000_000_000L);
        assertEquals(1, searcher.completedDepth());
        b.makeMove(move);
        assertEquals(Board.WHITE_WINS, b.result());
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
