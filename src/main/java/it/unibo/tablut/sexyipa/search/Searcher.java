package it.unibo.tablut.sexyipa.search;

import it.unibo.tablut.sexyipa.engine.Board;

public interface Searcher {
    /** Restituisce sempre una mossa legale entro deadlineNanos (System.nanoTime). */
    int bestMove(Board b, long deadlineNanos);
}
