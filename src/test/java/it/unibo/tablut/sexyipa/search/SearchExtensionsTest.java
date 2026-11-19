package it.unibo.tablut.sexyipa.search;

import static org.junit.jupiter.api.Assertions.*;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

import it.unibo.tablut.sexyipa.engine.Board;
import it.unibo.tablut.sexyipa.engine.BoardAshton;
import it.unibo.tablut.sexyipa.eval.SimpleEvaluator;

/** Test di storia delle posizioni, tabella di trasposizione e pareggio per ripetizione. */
class SearchExtensionsTest {

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

    private static int mv(String from, String to) {
        return sq(from) * 81 + sq(to);
    }

    private static int sq(String name) {
        return (name.charAt(1) - '1') * 9 + (name.charAt(0) - 'a');
    }

    // ─────────────────── PositionHistory ───────────────────

    @Test
    void historyRemembersManyHashesAcrossGrowth() {
        PositionHistory h = new PositionHistory();
        SplittableRandom rng = new SplittableRandom(1);
        long[] added = new long[5000];
        for (int i = 0; i < added.length; i++) {
            added[i] = rng.nextLong();
            h.add(added[i]);
        }
        h.add(added[0]); // duplicato: non cambia la dimensione
        assertEquals(added.length, h.size());
        for (long v : added) {
            assertTrue(h.contains(v));
        }
        assertFalse(h.contains(rng.nextLong()));
        assertFalse(h.contains(0));
        h.add(0);
        assertTrue(h.contains(0));
        h.clear();
        assertEquals(0, h.size());
        assertFalse(h.contains(added[0]));
    }

    // ─────────────────── TranspositionTable ───────────────────

    @Test
    void tableStoresAndReadsBackAllFields() {
        TranspositionTable tt = new TranspositionTable(10);
        tt.newSearch();
        tt.store(0x1234_5678_9ABC_DEF0L, 7, -999_950, TranspositionTable.UPPER, 6560);
        assertTrue(tt.probe(0x1234_5678_9ABC_DEF0L));
        assertEquals(7, tt.depth());
        assertEquals(-999_950, tt.score());
        assertEquals(TranspositionTable.UPPER, tt.flag());
        assertEquals(6560, tt.move());
        assertFalse(tt.probe(0x1234_5678_9ABC_DEF1L));
    }

    @Test
    void deeperEntryOfSameSearchIsNotReplacedByShallowerCollision() {
        TranspositionTable tt = new TranspositionTable(4);
        long a = 1;
        long b = 17; // stessa cella di a con 2^4 voci
        tt.newSearch();
        tt.store(a, 5, 100, TranspositionTable.EXACT, 10);
        tt.store(b, 2, 200, TranspositionTable.EXACT, 20);
        assertTrue(tt.probe(a), "la voce più profonda deve restare");
        assertFalse(tt.probe(b));

        tt.newSearch(); // nuova ricerca: la voce vecchia diventa sostituibile
        tt.store(b, 2, 200, TranspositionTable.EXACT, 20);
        assertTrue(tt.probe(b));
        assertFalse(tt.probe(a));
    }

    // ─────────────────── ripetizioni ───────────────────

    /** Re bloccato dai suoi soldati; nessuna cattura possibile; il materiale decide chi sta meglio. */
    private static BoardAshton blockedKing(String row7, String row8) {
        return board(true,
            ".........",
            ".........",
            ".........",
            "....W....",
            "...WKW...",
            "....W....",
            ".........",
            row7,
            row8);
    }

    @Test
    void losingSideChoosesRepetitionDraw() {
        // 11 neri: per il Bianco ogni mossa normale vale 400 - 550 - 120 = -270 (SimpleEvaluator)
        BoardAshton b = blockedKing("B.......B", "BBBBBBBBB");
        int repeat = mv("e4", "e3");
        PositionHistory history = new PositionHistory();
        b.makeMove(repeat);
        history.add(b.hash());
        b.undoMove();

        AlphaBetaSearcher searcher = new AlphaBetaSearcher(new SimpleEvaluator(), 16);
        searcher.setGameHistory(history);
        assertEquals(repeat, searcher.search(b, 1));
        assertEquals(AlphaBetaSearcher.DRAW, searcher.lastScore());
    }

    @Test
    void winningSideAvoidsRepetitionDraw() {
        // 1 nero: per il Bianco ogni mossa normale vale 400 - 50 - 120 = 230 > 0
        BoardAshton b = blockedKing(".........", "B........");
        int repeat = mv("e4", "e3");
        PositionHistory history = new PositionHistory();
        b.makeMove(repeat);
        history.add(b.hash());
        b.undoMove();

        AlphaBetaSearcher searcher = new AlphaBetaSearcher(new SimpleEvaluator(), 16);
        searcher.setGameHistory(history);
        assertNotEquals(repeat, searcher.search(b, 1));
        assertEquals(230, searcher.lastScore());
    }

    @Test
    void withoutHistoryTheSameMoveIsJustAnOrdinaryMove() {
        BoardAshton b = blockedKing("B.......B", "BBBBBBBBB");
        AlphaBetaSearcher searcher = new AlphaBetaSearcher(new SimpleEvaluator(), 16);
        searcher.search(b, 1);
        assertEquals(-270, searcher.lastScore());
    }

    // ─────────────────── coerenza della ricerca ───────────────────

    @Test
    void searchWithTableGivesSameScoreAsFreshSearch() {
        // Ripetere la stessa profondità con la tabella piena deve dare lo stesso punteggio
        SplittableRandom rnd = new SplittableRandom(11);
        int[] moves = new int[300];
        for (int game = 0; game < 10; game++) {
            BoardAshton b = new BoardAshton();
            int plies = rnd.nextInt(20);
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
            AlphaBetaSearcher warm = new AlphaBetaSearcher(new SimpleEvaluator(), 18);
            for (int d = 1; d <= 4; d++) {
                warm.search(b, d);
            }
            int warmScore = warm.lastScore();
            AlphaBetaSearcher fresh = new AlphaBetaSearcher(new SimpleEvaluator(), 18);
            fresh.search(b, 4);
            assertEquals(fresh.lastScore(), warmScore, "game " + game);
        }
    }
}
