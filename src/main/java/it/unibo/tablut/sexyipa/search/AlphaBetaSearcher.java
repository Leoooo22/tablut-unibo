package it.unibo.tablut.sexyipa.search;

import it.unibo.tablut.sexyipa.engine.Board;
import it.unibo.tablut.sexyipa.eval.Evaluator;

/**
 * Negamax con potatura alpha-beta e iterative deepening entro una scadenza.
 * Passo 2: niente TT né ordinamento delle mosse.
 */
public class AlphaBetaSearcher implements Searcher {

    /** Punteggio di vittoria; si vince "prima" con WIN - ply, così si preferisce la vittoria più vicina. */
    public static final int WIN = 1_000_000;
    /** Più grande di qualunque punteggio possibile: è la finestra iniziale (-INF, +INF). */
    public static final int INF = WIN + 1;
    public static final int MAX_PLY = 128;
    public static final int MAX_MOVES = 300;

    /** Ogni quanti nodi si legge l'orologio (potenza di 2: il controllo è un AND). */
    private static final int TIME_CHECK_MASK = 2048 - 1;

    /** Lanciata quando scade il tempo; senza stack trace e creata una volta sola, quindi costa quasi niente. */
    private static final class SearchTimeout extends RuntimeException {
        SearchTimeout() {
            super(null, null, false, false);
        }
    }

    private static final SearchTimeout TIMEOUT = new SearchTimeout();

    private final Evaluator evaluator;
    // Un buffer di mosse per livello: la ricerca non alloca
    private final int[][] moves = new int[MAX_PLY][MAX_MOVES];

    private long deadlineNanos = Long.MAX_VALUE;
    private long nodes;
    private int lastScore;
    private int completedDepth;

    public AlphaBetaSearcher(Evaluator evaluator) {
        this.evaluator = evaluator;
    }

    /**
     * Iterative deepening: profondità 1, 2, 3, ... finché c'è tempo.
     * Restituisce la mossa dell'ultima profondità completata; -1 solo se non ci sono mosse.
     */
    @Override
    public int bestMove(Board b, long deadlineNanos) {
        this.deadlineNanos = deadlineNanos;
        completedDepth = 0;
        long totalNodes = 0;

        // Mossa di riserva: una qualunque mossa legale, se scade il tempo prima della profondità 1
        int n = b.generateMoves(moves[0]);
        int best = n > 0 ? moves[0][0] : -1;
        if (n <= 1) {
            return best; // nessuna scelta da fare
        }

        for (int depth = 1; depth < MAX_PLY; depth++) {
            int move;
            try {
                move = search(b, depth);
            } catch (SearchTimeout e) {
                break; // profondità interrotta a metà: il suo risultato non è affidabile
            } finally {
                totalNodes += nodes;
            }
            best = move;
            completedDepth = depth;

            // Vittoria o sconfitta forzata già dimostrata: andare più a fondo non cambia la scelta
            if (Math.abs(lastScore) >= WIN - MAX_PLY) {
                break;
            }
            if (System.nanoTime() >= deadlineNanos) {
                break;
            }
        }
        nodes = totalNodes;
        this.deadlineNanos = Long.MAX_VALUE;
        return best;
    }

    /** Migliore mossa per chi è di turno, cercando a profondità depth (>= 1); -1 se non ci sono mosse. */
    public int search(Board b, int depth) {
        nodes = 0;
        int n = b.generateMoves(moves[0]);
        int bestMove = n > 0 ? moves[0][0] : -1;
        int alpha = -INF;

        for (int i = 0; i < n; i++) {
            int move = moves[0][i];
            int score;
            b.makeMove(move);
            try {
                score = -negamax(b, depth - 1, -INF, -alpha, 1);
            } finally {
                b.undoMove(); // eseguito anche quando scade il tempo: la scacchiera torna intatta
            }
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
        if ((nodes & TIME_CHECK_MASK) == 0 && System.nanoTime() >= deadlineNanos) {
            throw TIMEOUT;
        }

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
            int score;
            b.makeMove(moves[ply][i]);
            try {
                score = -negamax(b, depth - 1, -beta, -alpha, ply + 1);
            } finally {
                b.undoMove();
            }

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

    /** Nodi visitati dall'ultima search, o da tutte le iterazioni dell'ultimo bestMove. */
    public long nodes() {
        return nodes;
    }

    /** Punteggio della mossa restituita dall'ultima profondità completata, dal punto di vista di chi muoveva. */
    public int lastScore() {
        return lastScore;
    }

    /** Profondità dell'ultima iterazione completata nell'ultimo bestMove (0 se nessuna). */
    public int completedDepth() {
        return completedDepth;
    }
}
