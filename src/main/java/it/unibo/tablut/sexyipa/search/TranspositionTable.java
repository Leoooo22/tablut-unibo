package it.unibo.tablut.sexyipa.search;

/**
 * Tabella di trasposizione: per ogni posizione (hash Zobrist) ricorda profondità, punteggio,
 * tipo di limite e mossa migliore. Una voce per cella, due long per voce (16 byte):
 * con 2^22 voci occupa 64 MB.
 * <p>
 * Sostituzione: una voce viene sovrascritta se è della stessa posizione, se è di una ricerca
 * precedente o se la nuova è almeno altrettanto profonda.
 * <p>
 * I punteggi di vittoria/sconfitta vanno passati già "relativi al nodo" (vedi AlphaBetaSearcher).
 */
final class TranspositionTable {

    static final int EXACT = 0;
    /** Il punteggio vero è >= score (taglio beta). */
    static final int LOWER = 1;
    /** Il punteggio vero è <= score (nessuna mossa ha superato alpha). */
    static final int UPPER = 2;

    static final int DEFAULT_BITS = 22;

    private static final long VALID = 1L << 63;

    private final long[] keys;
    private final long[] data;
    private final int mask;
    private int generation;

    // Valori dell'ultima probe riuscita: così probe non alloca
    private int move;
    private int score;
    private int depth;
    private int flag;

    TranspositionTable(int bits) {
        int size = 1 << bits;
        keys = new long[size];
        data = new long[size];
        mask = size - 1;
    }

    /** Da chiamare all'inizio di ogni ricerca: le voci vecchie diventano sostituibili. */
    void newSearch() {
        generation = (generation + 1) & 0x7F;
    }

    void clear() {
        java.util.Arrays.fill(keys, 0);
        java.util.Arrays.fill(data, 0);
    }

    /** true se la posizione è in tabella; i valori si leggono poi con move(), score(), depth(), flag(). */
    boolean probe(long key) {
        int i = index(key);
        long d = data[i];
        if (d == 0 || keys[i] != key) {
            return false;
        }
        score = (int) d;
        move = (int) ((d >>> 32) & 0x3FFF) - 1;
        depth = (int) ((d >>> 46) & 0xFF);
        flag = (int) ((d >>> 54) & 0x3);
        return true;
    }

    void store(long key, int depth, int score, int flag, int move) {
        int i = index(key);
        long old = data[i];
        if (old != 0 && keys[i] != key) {
            int oldGen = (int) ((old >>> 56) & 0x7F);
            int oldDepth = (int) ((old >>> 46) & 0xFF);
            if (oldGen == generation && oldDepth > depth) {
                return; // voce più profonda della stessa ricerca: la si tiene
            }
        }
        keys[i] = key;
        data[i] = VALID
                | ((long) generation << 56)
                | ((long) (flag & 0x3) << 54)
                | ((long) (depth & 0xFF) << 46)
                | ((long) ((move + 1) & 0x3FFF) << 32)
                | (score & 0xFFFFFFFFL);
    }

    int move() {
        return move;
    }

    int score() {
        return score;
    }

    int depth() {
        return depth;
    }

    int flag() {
        return flag;
    }

    private int index(long key) {
        return (int) (key ^ (key >>> 32)) & mask;
    }
}
