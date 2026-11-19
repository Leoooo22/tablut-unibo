package it.unibo.tablut.sexyipa.eval;

import static it.unibo.tablut.sexyipa.eval.Positions.board;
import static it.unibo.tablut.sexyipa.eval.Positions.sq;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

import it.unibo.tablut.sexyipa.engine.Board;
import it.unibo.tablut.sexyipa.engine.BoardAshton;
import it.unibo.tablut.sexyipa.engine.Tables;

class AshtonEvaluatorTest {

    private static final Weights W = Weights.DEFAULT;
    private final AshtonEvaluator eval = new AshtonEvaluator();

    private static int mv(String from, String to) {
        return sq(from) * Tables.CELLS + sq(to);
    }

    // ─────────────────── situazioni tattiche ───────────────────

    @Test
    void whiteToMoveWithOpenRouteIsNearWin() {
        // Re in c3: la colonna c verso c1 è libera
        BoardAshton b = board(true,
            ".........",
            ".........",
            "..K......",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
            "........B");
        assertEquals(W.escapeNow() - W.blackSoldier(), eval.evaluate(b));
    }

    @Test
    void blackToMoveCapturesKingAgainstCamp() {
        // Re in e3 sotto il campo e2; il nero in a4 arriva in e4 e lo cattura
        BoardAshton b = board(false,
            ".........",
            ".........",
            "...WKW...",
            "B........",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........");
        int material = 2 * W.whiteSoldier() - W.blackSoldier();
        assertEquals(W.kingCapturableNow() - material, eval.evaluate(b));

        b.makeMove(mv("a4", "e4"));
        assertEquals(Board.BLACK_WINS, b.result(), "lo scenario deve essere una cattura vera per il motore");
    }

    @Test
    void kingOnCastleCapturableOnlyWhenFourthBlackArrives() {
        BoardAshton open = board(false,
            ".........",
            ".........",
            ".........",
            "....B....",
            "...BKB...",
            "B........",
            ".........",
            ".........",
            ".........");
        assertTrue(AshtonEvaluator.kingCapturable(open, sq("e5")));
        open.makeMove(mv("a6", "e6"));
        assertEquals(Board.BLACK_WINS, open.result());

        // Un bianco in c6 sbarra la strada al quarto nero
        BoardAshton blocked = board(false,
            ".........",
            ".........",
            ".........",
            "....B....",
            "...BKB...",
            "B.W......",
            ".........",
            ".........",
            ".........");
        assertFalse(AshtonEvaluator.kingCapturable(blocked, sq("e5")));
    }

    @Test
    void blackInsideCampCapturesKingByMovingWithinCamp() {
        // Re in e3 con un nero sotto (e4): il nero in e1 scende in e2, dentro lo stesso campo,
        // e chiude il re contro e4. La cella d'arrivo è un campo: va considerata anche quella.
        BoardAshton b = board(false,
            "....B....",
            ".........",
            "...WKW...",
            "....B....",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........");
        assertTrue(AshtonEvaluator.kingCapturable(b, sq("e3")));
        b.makeMove(mv("e1", "e2"));
        assertEquals(Board.BLACK_WINS, b.result());
    }

    @Test
    void blackOutsideCampCannotReachThroughCamp() {
        // Il nero in a3 è fuori dal campo: per arrivare in a7 dovrebbe attraversare a4-a6
        BoardAshton b = board(false,
            ".........",
            ".........",
            "B........",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
            ".........");
        assertFalse(AshtonEvaluator.blackCanReach(b, sq("a7")));
        assertTrue(AshtonEvaluator.blackCanReach(b, sq("a1")));
    }

    // ─────────────────── termini di posizione ───────────────────

    @Test
    void preEscapeSquaresFromD6() {
        // Re in d6 senza vie dirette (campi su tutti e 4 i lati). Caselle da cui avrebbe
        // una via perpendicolare: d3 d2 d7 d8 (verso la colonna a), c6 b6 g6 h6 (verso le righe 1 e 9)
        BoardAshton b = board(true,
            ".........",
            ".........",
            ".........",
            ".........",
            ".........",
            "...K.....",
            ".........",
            ".........",
            ".........");
        assertEquals(0, AshtonEvaluator.openRoutes(b, sq("d6")));
        assertEquals(8, AshtonEvaluator.preEscapeSquares(b, sq("d6")));
    }

    @Test
    void blackOnGuardSquareLowersWhiteScoreByGuardWeight() {
        // Re bloccato dai suoi soldati: niente vie, niente minacce; cambia solo la casella del nero
        BoardAshton onGuard = board(true,
            ".........",
            ".........",
            ".B.......",
            "....W....",
            "...WKW...",
            "....W....",
            ".........",
            ".........",
            ".........");
        BoardAshton offGuard = board(true,
            ".........",
            ".........",
            ".........",
            ".B..W....",
            "...WKW...",
            "....W....",
            ".........",
            ".........",
            ".........");
        assertEquals(W.guardSquare(), eval.whiteScore(offGuard) - eval.whiteScore(onGuard));
    }

    @Test
    void quietPositionIsAntisymmetricInTurn() {
        BoardAshton white = board(true,
            ".........",
            ".........",
            ".B.......",
            "....W....",
            "...WKW...",
            "....W....",
            ".........",
            ".........",
            ".........");
        BoardAshton black = Positions.withTurn(white, false);
        assertEquals(eval.evaluate(white), -eval.evaluate(black));
    }

    // ─────────────────── confronto con il motore su posizioni casuali ───────────────────

    @Test
    void openRoutesMatchEngineEscapeMoves() {
        int[] moves = new int[300];
        int positives = 0;
        for (BoardAshton p : Positions.random(3000, 1)) {
            BoardAshton b = Positions.withTurn(p, true);
            int king = b.kingPos();
            int n = b.generateMoves(moves);
            boolean engineEscape = false;
            for (int i = 0; i < n; i++) {
                int from = moves[i] / Tables.CELLS;
                int to = moves[i] % Tables.CELLS;
                if (from == king && Tables.CELL_TYPE[to] == Tables.ESCAPE) {
                    engineEscape = true;
                }
            }
            assertEquals(engineEscape, AshtonEvaluator.openRoutes(b, king) > 0);
            if (engineEscape) {
                positives++;
            }
        }
        assertTrue(positives > 0, "nessuna posizione con via libera: il test non verifica niente");
    }

    @Test
    void kingCapturableMatchesEngineCaptures() {
        int[] moves = new int[300];
        int positives = 0;
        for (BoardAshton p : Positions.random(5000, 2)) {
            BoardAshton b = Positions.withTurn(p, false);
            int n = b.generateMoves(moves);
            boolean engineCapture = false;
            for (int i = 0; i < n && !engineCapture; i++) {
                b.makeMove(moves[i]);
                engineCapture = b.result() == Board.BLACK_WINS;
                b.undoMove();
            }
            assertEquals(engineCapture, AshtonEvaluator.kingCapturable(b, b.kingPos()));
            if (engineCapture) {
                positives++;
            }
        }
        assertTrue(positives > 0, "nessuna posizione con re catturabile: il test non verifica niente");
    }

    @Test
    void scoreIsInvariantUnderBoardSymmetries() {
        for (BoardAshton b : Positions.random(500, 3)) {
            int expected = eval.evaluate(b);
            byte[] cells = Positions.cells(b);
            for (int t = 1; t < 8; t++) {
                BoardAshton s = new BoardAshton(transform(cells, t), b.whiteToMove());
                assertEquals(expected, eval.evaluate(s), "simmetria " + t);
            }
        }
    }

    @Test
    void scoreStaysFarFromWinValue() {
        for (BoardAshton b : Positions.random(2000, 4)) {
            assertTrue(Math.abs(eval.evaluate(b)) < 100_000);
            assertTrue(Math.abs(eval.evaluate(Positions.withTurn(b, !b.whiteToMove()))) < 100_000);
        }
    }

    /** Una delle 8 simmetrie del quadrato: bit 0 trasposizione, bit 1 righe invertite, bit 2 colonne invertite. */
    private static byte[] transform(byte[] cells, int t) {
        byte[] out = new byte[Tables.CELLS];
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                int rr = r;
                int cc = c;
                if ((t & 1) != 0) {
                    rr = c;
                    cc = r;
                }
                if ((t & 2) != 0) {
                    rr = 8 - rr;
                }
                if ((t & 4) != 0) {
                    cc = 8 - cc;
                }
                out[rr * 9 + cc] = cells[r * 9 + c];
            }
        }
        return out;
    }
}
