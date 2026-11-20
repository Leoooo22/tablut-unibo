package it.unibo.tablut.sexyipa.engine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class BoardAshtonTest {

    /** Scacchiera da diagramma: riga 0 = riga "1" del server. '.' vuoto, W bianco, B nero, K re. */
    private static BoardAshton board(boolean whiteToMove, String... rows) {
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

    private static int sq(String name) {
        return (name.charAt(1) - '1') * 9 + (name.charAt(0) - 'a');
    }

    private static int mv(String from, String to) {
        return sq(from) * Tables.CELLS + sq(to);
    }

    private static boolean isLegal(Board b, int move) {
        int[] out = new int[300];
        int n = b.generateMoves(out);
        return Arrays.stream(out, 0, n).anyMatch(m -> m == move);
    }

    @Test
    void initialPosition() {
        BoardAshton b = new BoardAshton();
        assertEquals(8, b.whiteCount());
        assertEquals(16, b.blackCount());
        assertEquals(Tables.CASTLE_POS, b.kingPos());
        assertTrue(b.whiteToMove());
        assertEquals(56, b.generateMoves(new int[300]));
        assertEquals(b.computeHash(), b.hash());
    }

    @Test
    void blackInCampMovesThroughOwnCamp() {
        // a4 -> a7 attraversando a5 e a6 dello stesso campo
        BoardAshton b = board(false,
            ".........",
            ".........",
            ".........",
            "B........",
            "....K....",
            ".........",
            ".........",
            ".........",
            ".........");
        assertTrue(isLegal(b, mv("a4", "a7")));
    }

    @Test
    void blackOutsideCampCannotEnterCamp() {
        BoardAshton b = board(false,
            ".........",
            ".........",
            "B........",
            ".........",
            "....K....",
            ".........",
            ".........",
            ".........",
            ".........");
        assertFalse(isLegal(b, mv("a3", "a4")));
        assertFalse(isLegal(b, mv("a3", "a7")));
    }

    @Test
    void nobodyCrossesOrStopsOnEmptyCastle() {
        BoardAshton b = board(true,
            ".........",
            ".........",
            ".........",
            "....W....",
            ".........",
            ".........",
            ".........",
            ".........",
            "..K......");
        assertFalse(isLegal(b, mv("e4", "e5")));
        assertFalse(isLegal(b, mv("e4", "e6")));
    }

    @Test
    void whiteCapturesAgainstCampButNotCentralCamp() {
        // b4 contro a4 (campo laterale) si cattura
        BoardAshton b = board(true,
            ".........",
            ".........",
            ".........",
            ".B.......",
            "....K....",
            ".........",
            "..W......",
            ".........",
            ".........");
        b.makeMove(mv("c7", "c4"));
        assertEquals(Board.EMPTY, b.cell(sq("b4")));
        assertEquals(0, b.blackCount());

        // b5 contro a5 (campo centrale) non si cattura
        BoardAshton c = board(true,
            ".........",
            ".........",
            ".........",
            ".........",
            ".B..K....",
            ".........",
            "..W......",
            ".........",
            ".........");
        c.makeMove(mv("c7", "c5"));
        assertEquals(Board.BLACK, c.cell(sq("b5")));
    }

    @Test
    void blackCapturesAgainstCastleOccupiedByKing() {
        // e4 stretto tra e3 e il castello con il re sopra
        BoardAshton b = board(false,
            ".........",
            ".........",
            "B........",
            "....W....",
            "....K....",
            ".........",
            ".........",
            ".........",
            ".........");
        b.makeMove(mv("a3", "e3"));
        assertEquals(Board.EMPTY, b.cell(sq("e4")));
        assertEquals(0, b.whiteCount());
    }

    @Test
    void kingOnCastleNeedsFourBlacks() {
        BoardAshton b = board(false,
            ".........",
            ".........",
            ".........",
            "....B....",
            "...BKB...",
            "B........",
            ".........",
            ".........",
            ".........");
        b.makeMove(mv("a6", "e6"));
        assertEquals(Board.BLACK_WINS, b.result());

        BoardAshton c = board(false,
            ".........",
            ".........",
            ".........",
            "....B....",
            "...BK....",
            "B........",
            ".........",
            ".........",
            ".........");
        c.makeMove(mv("a6", "e6"));
        assertEquals(Board.ONGOING, c.result());
    }

    @Test
    void kingNextToCastleNeedsThreeBlacks() {
        BoardAshton b = board(false,
            ".........",
            ".........",
            "B........",
            "...BKB...",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........");
        b.makeMove(mv("a3", "e3"));
        assertEquals(Board.BLACK_WINS, b.result());

        // Due neri ai lati opposti non bastano accanto al castello
        BoardAshton c = board(false,
            ".........",
            ".........",
            ".........",
            "B...KB...",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........");
        c.makeMove(mv("a4", "d4"));
        assertEquals(Board.ONGOING, c.result());
    }

    @Test
    void kingElsewhereCapturedAgainstCamp() {
        // Re in d2, campo e2 dal lato opposto
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
        b.makeMove(mv("a2", "c2"));
        assertEquals(Board.BLACK_WINS, b.result());
    }

    @Test
    void kingOnEdgeWins() {
        BoardAshton b = board(true,
            ".........",
            ".K.......",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........");
        b.makeMove(mv("b2", "b1"));
        assertEquals(Board.WHITE_WINS, b.result());
        b.undoMove();
        assertEquals(Board.ONGOING, b.result());
        assertEquals(sq("b2"), b.kingPos());
    }

    @Test
    void makeUndoRestoresBoardAndHash() {
        SplittableRandom rnd = new SplittableRandom(1);
        int[][] moves = new int[BoardAshton.MAX_PLY][300];
        long totalMoves = 0;
        while (totalMoves < 1_000_000) {
            BoardAshton b = new BoardAshton();
            byte[][] cells = new byte[BoardAshton.MAX_PLY + 1][Tables.CELLS];
            long[] hashes = new long[BoardAshton.MAX_PLY + 1];
            int[] counts = new int[BoardAshton.MAX_PLY + 1];
            int depth = 0;
            while (depth < 100 && b.result() == Board.ONGOING) {
                for (int i = 0; i < Tables.CELLS; i++) {
                    cells[depth][i] = b.cell(i);
                }
                hashes[depth] = b.hash();
                counts[depth] = b.whiteCount() * 100 + b.blackCount();
                int n = b.generateMoves(moves[depth]);
                if (n == 0) {
                    break;
                }
                b.makeMove(moves[depth][rnd.nextInt(n)]);
                assertEquals(b.computeHash(), b.hash(), "hash incrementale");
                depth++;
                totalMoves++;
            }
            while (depth > 0) {
                b.undoMove();
                depth--;
                for (int i = 0; i < Tables.CELLS; i++) {
                    assertEquals(cells[depth][i], b.cell(i));
                }
                assertEquals(hashes[depth], b.hash());
                assertEquals(counts[depth], b.whiteCount() * 100 + b.blackCount());
                assertEquals(Board.ONGOING, b.result());
            }
            assertEquals(Tables.CASTLE_POS, b.kingPos());
        }
    }
}
