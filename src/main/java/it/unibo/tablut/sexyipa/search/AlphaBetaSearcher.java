package it.unibo.tablut.sexyipa.search;

import it.unibo.tablut.sexyipa.engine.Board;
import it.unibo.tablut.sexyipa.eval.Evaluator;

/**
 * Negamax con potatura alpha-beta a profondità fissa.
 * Passo 1: niente gestione del tempo, TT né ordinamento delle mosse.
 */
public class AlphaBetaSearcher {

    /** Punteggio di vittoria; si vince "prima" con WIN - ply, così si preferisce la vittoria più vicina. */
    public static final int WIN = 1_000_000;
    /** Più grande di qualunque punteggio possibile: è la finestra iniziale (-INF, +INF). */
    public static final int INF = WIN + 1;
    public static final int MAX_PLY = 128;
    public static final int MAX_MOVES = 300;

    private final Evaluator evaluator;
    // Un buffer di mosse per livello: la ricerca non alloca
    private final int[][] moves = new int[MAX_PLY][MAX_MOVES];

    private long nodes;
    private int lastScore;

    public AlphaBetaSearcher(Evaluator evaluator) {
        this.evaluator = evaluator;
    }

    /** Migliore mossa per chi è di turno, cercando a profondità depth (>= 1); -1 se non ci sono mosse. */
    public int search(Board b, int depth) {
        nodes = 0;
        int n = b.generateMoves(moves[0]);
        int bestMove = n > 0 ? moves[0][0] : -1;
        int alpha = -INF;

        for (int i = 0; i < n; i++) {
            int move = moves[0][i];
            b.makeMove(move);
            int score = -negamax(b, depth - 1, -INF, -alpha, 1);
            b.undoMove();
            if (score > alpha) {
                alpha = score;
                bestMove = move;
            }
        }
        lastScore = n > 0 ? alpha : -WIN;
        return bestMove;
    }

    /** Valore della posizione per chi è di turno, a ply mosse dalla radice. */
    private int negamax(Board b, int depth, int alpha, int beta, int ply) {
        nodes++;

        // Partita finita dall'ultima mossa: ha vinto chi ha appena mosso, quindi chi è di turno ha perso
        if (b.result() != Board.ONGOING) {
            return -(WIN - ply);
        }
        if (depth == 0) {
            return evaluator.evaluate(b);
        }

        int n = b.generateMoves(moves[ply]);
        if (n == 0) {
            return -(WIN - ply); // nessuna mossa: chi deve muovere perde
        }

        int best = -INF;
        for (int i = 0; i < n; i++) {
            b.makeMove(moves[ply][i]);
            int score = -negamax(b, depth - 1, -beta, -alpha, ply + 1);
            b.undoMove();

            if (score > best) {
                best = score;
            }
            if (score > alpha) {
                alpha = score;
            }
            if (alpha >= beta) {
                break; // taglio: l'avversario non permetterà di arrivare qui
            }
        }
        return best;
    }

    /** Nodi visitati dall'ultima search. */
    public long nodes() {
        return nodes;
    }

    /** Punteggio della mossa restituita dall'ultima search, dal punto di vista di chi muoveva. */
    public int lastScore() {
        return lastScore;
    }
}
