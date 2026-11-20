package it.unibo.tablut.sexyipa.eval;

import it.unibo.tablut.sexyipa.engine.Board;

/**
 * Valutazione provvisoria per sbloccare la ricerca:
 * differenza di pezzi + distanza del re dal bordo.
 */
public class SimpleEvaluator implements Evaluator {

    private static final int WHITE_PIECE = 100;
    private static final int BLACK_PIECE = 50;   // i neri sono il doppio, ognuno vale meno
    private static final int KING_EDGE_STEP = 30; // per ogni passo del re dal bordo più vicino

    @Override
    public int evaluate(Board b) {
        int king = b.kingPos();
        int row = king / 9;
        int col = king % 9;
        int edgeDistance = Math.min(Math.min(row, 8 - row), Math.min(col, 8 - col));

        // Punteggio dal punto di vista del Bianco
        int score = WHITE_PIECE * b.whiteCount()
                  - BLACK_PIECE * b.blackCount()
                  - KING_EDGE_STEP * edgeDistance;

        return b.whiteToMove() ? score : -score;
    }
}
