package it.unibo.tablut.sexyipa.search;

import static it.unibo.tablut.sexyipa.engine.Tables.*;

import it.unibo.tablut.sexyipa.engine.Board;
import it.unibo.tablut.sexyipa.eval.Evaluator;

/**
 * Negamax con potatura alpha-beta e iterative deepening entro una scadenza.
 * <ul>
 *     <li><b>Tabella di trasposizione</b> (hash Zobrist del motore): riusa i risultati delle posizioni
 *         già viste e fornisce la mossa migliore da provare per prima.</li>
 *     <li><b>Ordinamento delle mosse</b>: mossa della tabella, fuga immediata del re, catture,
 *         due mosse killer per livello, history heuristic.</li>
 *     <li><b>Pareggio per ripetizione</b> come il server: una posizione già comparsa nella partita
 *         ({@link #setGameHistory}) o lungo il percorso di ricerca vale {@link #DRAW}.</li>
 * </ul>
 */
public class AlphaBetaSearcher implements Searcher {

    /** Punteggio di vittoria; si vince "prima" con WIN - ply, così si preferisce la vittoria più vicina. */
    public static final int WIN = 1_000_000;
    /** Più grande di qualunque punteggio possibile: è la finestra iniziale (-INF, +INF). */
    public static final int INF = WIN + 1;
    /** Punteggio di una posizione ripetuta: la partita finisce patta. */
    public static final int DRAW = 0;
    public static final int MAX_PLY = 128;
    public static final int MAX_MOVES = 300;

    /** Ogni quanti nodi si legge l'orologio (potenza di 2: il controllo è un AND). */
    private static final int TIME_CHECK_MASK = 2048 - 1;

    /** Punteggi oltre questa soglia sono vittorie/sconfitte forzate, legate alla distanza dalla radice. */
    private static final int WIN_THRESHOLD = WIN - MAX_PLY;

    // Priorità di ordinamento (più alto = provata prima)
    private static final int ORDER_TT = 1 << 30;
    private static final int ORDER_ESCAPE = 1 << 29;
    private static final int ORDER_CAPTURE = 1 << 28;
    private static final int ORDER_KILLER_1 = 1 << 27;
    private static final int ORDER_KILLER_2 = (1 << 27) - 1;
    private static final int HISTORY_MAX = 1 << 26;

    /** Lanciata quando scade il tempo; senza stack trace e creata una volta sola, quindi costa quasi niente. */
    private static final class SearchTimeout extends RuntimeException {
        SearchTimeout() {
            super(null, null, false, false);
        }
    }

    private static final SearchTimeout TIMEOUT = new SearchTimeout();

    private final Evaluator evaluator;
    private final TranspositionTable tt;
    // Buffer per livello: la ricerca non alloca
    private final int[][] moves = new int[MAX_PLY][MAX_MOVES];
    private final int[][] orderScores = new int[MAX_PLY][MAX_MOVES];
    private final int[][] killers = new int[MAX_PLY][2];
    /** history[lato][mossa]: quanto spesso la mossa (non cattura) ha causato un taglio. */
    private final int[][] history = new int[2][CELLS * CELLS];
    /** Hash delle posizioni lungo il percorso corrente, indicizzati per ply. */
    private final long[] pathHash = new long[MAX_PLY + 1];

    private PositionHistory gameHistory = new PositionHistory();

    private long deadlineNanos = Long.MAX_VALUE;
    private long nodes;
    private int lastScore;
    private int completedDepth;

    public AlphaBetaSearcher(Evaluator evaluator) {
        this(evaluator, TranspositionTable.DEFAULT_BITS);
    }

    /** @param ttBits la tabella di trasposizione ha 2^ttBits voci (16 byte ciascuna) */
    public AlphaBetaSearcher(Evaluator evaluator, int ttBits) {
        this.evaluator = evaluator;
        this.tt = new TranspositionTable(ttBits);
        clearKillers();
    }

    /**
     * Posizioni comparse nella partita dopo ogni mossa (esclusa quella iniziale). Il client la aggiorna
     * a ogni stato ricevuto; il searcher la legge soltanto.
     */
    public void setGameHistory(PositionHistory history) {
        this.gameHistory = history;
    }

    /** Dimentica tutto ciò che è stato imparato nelle ricerche precedenti (tabella, killer, history). */
    public void clear() {
        tt.clear();
        clearKillers();
        for (int[] h : history) {
            java.util.Arrays.fill(h, 0);
        }
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
        tt.newSearch();
        clearKillers();
        ageHistory();

        // Mossa di riserva: una qualunque mossa legale, se scade il tempo prima della profondità 1
        int n = b.generateMoves(moves[0]);
        int best = n > 0 ? moves[0][0] : -1;
        if (n <= 1) {
            this.deadlineNanos = Long.MAX_VALUE;
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
            if (Math.abs(lastScore) >= WIN_THRESHOLD) {
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
        long hash = b.hash();
        pathHash[0] = hash;
        int n = b.generateMoves(moves[0]);
        if (n == 0) {
            lastScore = -WIN;
            return -1;
        }
        int ttMove = tt.probe(hash) ? tt.move() : -1;
        scoreMoves(b, 0, n, ttMove);

        int bestMove = moves[0][0];
        int alpha = -INF;
        for (int i = 0; i < n; i++) {
            int move = pickNext(0, i, n);
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
        tt.store(hash, depth, toTable(alpha, 0), TranspositionTable.EXACT, bestMove);
        lastScore = alpha;
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
        long hash = b.hash();
        if (isRepetition(hash, ply)) {
            return DRAW;
        }
        pathHash[ply] = hash;
        if (depth == 0) {
            return evaluator.evaluate(b);
        }

        int ttMove = -1;
        if (tt.probe(hash)) {
            ttMove = tt.move();
            if (tt.depth() >= depth) {
                int s = fromTable(tt.score(), ply);
                int flag = tt.flag();
                if (flag == TranspositionTable.EXACT
                        || (flag == TranspositionTable.LOWER && s >= beta)
                        || (flag == TranspositionTable.UPPER && s <= alpha)) {
                    return s;
                }
            }
        }

        int n = b.generateMoves(moves[ply]);
        if (n == 0) {
            return -(WIN - ply); // nessuna mossa: chi deve muovere perde
        }
        scoreMoves(b, ply, n, ttMove);

        int alphaOrig = alpha;
        int best = -INF;
        int bestMove = moves[ply][0];
        for (int i = 0; i < n; i++) {
            int move = pickNext(ply, i, n);
            int score;
            b.makeMove(move);
            try {
                score = -negamax(b, depth - 1, -beta, -alpha, ply + 1);
            } finally {
                b.undoMove();
            }

            if (score > best) {
                best = score;
                bestMove = move;
            }
            if (score > alpha) {
                alpha = score;
            }
            if (alpha >= beta) {
                if (orderScores[ply][i] < ORDER_CAPTURE) {
                    rememberQuietCutoff(b, ply, move, depth);
                }
                break; // taglio: l'avversario non permetterà di arrivare qui
            }
        }

        int flag = best <= alphaOrig ? TranspositionTable.UPPER
                : best >= beta ? TranspositionTable.LOWER
                : TranspositionTable.EXACT;
        tt.store(hash, depth, toTable(best, ply), flag, bestMove);
        return best;
    }

    // ─────────────────── ripetizioni ───────────────────

    /**
     * La posizione al livello ply ripete una posizione già comparsa? Si guardano la partita e il percorso
     * dalla radice, escludendo la radice stessa (ply 0): se non è la posizione iniziale è già nella storia
     * della partita, e se lo è il server non la conta.
     */
    private boolean isRepetition(long hash, int ply) {
        for (int j = ply - 2; j >= 1; j -= 2) { // stesso giocatore al tratto: stessa parità
            if (pathHash[j] == hash) {
                return true;
            }
        }
        return gameHistory.contains(hash);
    }

    // ─────────────────── punteggi in tabella ───────────────────

    /** Vittorie/sconfitte in tabella: distanza dal nodo, non dalla radice, così valgono da qualunque percorso. */
    private static int toTable(int score, int ply) {
        if (score >= WIN_THRESHOLD) {
            return score + ply;
        }
        if (score <= -WIN_THRESHOLD) {
            return score - ply;
        }
        return score;
    }

    private static int fromTable(int score, int ply) {
        if (score >= WIN_THRESHOLD) {
            return score - ply;
        }
        if (score <= -WIN_THRESHOLD) {
            return score + ply;
        }
        return score;
    }

    // ─────────────────── ordinamento delle mosse ───────────────────

    private void scoreMoves(Board b, int ply, int n, int ttMove) {
        boolean white = b.whiteToMove();
        int side = white ? 0 : 1;
        int king = b.kingPos();
        int[] ms = moves[ply];
        int[] os = orderScores[ply];
        for (int i = 0; i < n; i++) {
            int m = ms[i];
            int from = m / CELLS;
            int to = m % CELLS;
            int s;
            if (m == ttMove) {
                s = ORDER_TT;
            } else if (white && from == king && CELL_TYPE[to] == ESCAPE) {
                s = ORDER_ESCAPE;
            } else {
                int captures = captureCount(b, from, to, white);
                if (captures > 0) {
                    s = ORDER_CAPTURE + captures;
                } else if (m == killers[ply][0]) {
                    s = ORDER_KILLER_1;
                } else if (m == killers[ply][1]) {
                    s = ORDER_KILLER_2;
                } else {
                    s = history[side][m];
                }
            }
            os[i] = s;
        }
    }

    /**
     * Stima quanti pezzi cattura la mossa from→to (regole di BoardAshton.makeMove), contando come
     * "cattura" anche l'arrivo di un nero accanto al re: serve solo a ordinare, non deve essere esatta.
     */
    private static int captureCount(Board b, int from, int to, boolean white) {
        int count = 0;
        for (int d = 0; d < 4; d++) {
            int n = NEIGHBOR[to][d];
            if (n < 0) {
                continue;
            }
            byte p = b.cell(n);
            int o = NEIGHBOR[n][d];
            if (white) {
                if (p == Board.BLACK && o >= 0) {
                    byte q = o == from ? Board.EMPTY : b.cell(o); // from si svuota con la mossa
                    if (q == Board.WHITE || q == Board.KING || ANVIL_FOR_WHITE[o]) {
                        count++;
                    }
                }
            } else if (p == Board.WHITE && o >= 0) {
                byte q = o == from ? Board.EMPTY : b.cell(o);
                if (q == Board.BLACK || ANVIL_FOR_BLACK[o]) {
                    count++;
                }
            } else if (p == Board.KING) {
                count++;
            }
        }
        return count;
    }

    /** Porta in posizione i la mossa con priorità più alta tra i e n-1 e la restituisce. */
    private int pickNext(int ply, int i, int n) {
        int[] ms = moves[ply];
        int[] os = orderScores[ply];
        int bestIdx = i;
        for (int j = i + 1; j < n; j++) {
            if (os[j] > os[bestIdx]) {
                bestIdx = j;
            }
        }
        if (bestIdx != i) {
            int tm = ms[i];
            ms[i] = ms[bestIdx];
            ms[bestIdx] = tm;
            int ts = os[i];
            os[i] = os[bestIdx];
            os[bestIdx] = ts;
        }
        return ms[i];
    }

    private void rememberQuietCutoff(Board b, int ply, int move, int depth) {
        if (killers[ply][0] != move) {
            killers[ply][1] = killers[ply][0];
            killers[ply][0] = move;
        }
        int[] h = history[b.whiteToMove() ? 0 : 1];
        h[move] = Math.min(HISTORY_MAX - 1, h[move] + depth * depth);
    }

    private void clearKillers() {
        for (int[] k : killers) {
            k[0] = -1;
            k[1] = -1;
        }
    }

    /** Dimezza la history a ogni nuova mossa: conta di più quello che è successo di recente. */
    private void ageHistory() {
        for (int[] h : history) {
            for (int i = 0; i < h.length; i++) {
                h[i] >>= 1;
            }
        }
    }

    // ─────────────────── statistiche ───────────────────

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
