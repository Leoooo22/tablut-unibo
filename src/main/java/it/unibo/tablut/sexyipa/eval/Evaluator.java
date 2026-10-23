package it.unibo.tablut.sexyipa.eval;

import it.unibo.tablut.sexyipa.engine.Board;

public interface Evaluator {
    /** Punteggio dal punto di vista del giocatore di turno (convenzione negamax). */
    int evaluate(Board b);
}
