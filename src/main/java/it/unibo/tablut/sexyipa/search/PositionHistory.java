package it.unibo.tablut.sexyipa.search;

/**
 * Hash Zobrist delle posizioni già comparse nella partita, per riconoscere il pareggio per ripetizione.
 * <p>
 * Regola del server (GameAshtonTablut, con le opzioni di default): dopo ogni mossa la nuova posizione
 * (scacchiera + turno) viene confrontata con quelle comparse dopo le mosse precedenti; se è già comparsa,
 * la partita è patta. La posizione iniziale non conta. Il server svuota l'elenco dopo ogni cattura, ma non
 * serve imitarlo: una posizione precedente a una cattura ha più pezzi e non può più ripetersi.
 * <p>
 * Insieme a indirizzamento aperto: contains() non alloca, add() alloca solo quando la tabella cresce.
 */
public final class PositionHistory {

    private long[] table = new long[1024];
    private int size;
    /** Lo 0 marca le celle vuote: l'hash 0 (improbabile) si tiene a parte. */
    private boolean hasZero;

    public void add(long hash) {
        if (hash == 0) {
            hasZero = true;
            return;
        }
        if ((size + 1) * 2 > table.length) {
            grow();
        }
        if (insert(table, hash)) {
            size++;
        }
    }

    public boolean contains(long hash) {
        if (hash == 0) {
            return hasZero;
        }
        int mask = table.length - 1;
        for (int i = slot(hash, mask); ; i = (i + 1) & mask) {
            long v = table[i];
            if (v == hash) {
                return true;
            }
            if (v == 0) {
                return false;
            }
        }
    }

    /** Numero di posizioni diverse registrate. */
    public int size() {
        return size + (hasZero ? 1 : 0);
    }

    public void clear() {
        java.util.Arrays.fill(table, 0);
        size = 0;
        hasZero = false;
    }

    private void grow() {
        long[] bigger = new long[table.length * 2];
        for (long v : table) {
            if (v != 0) {
                insert(bigger, v);
            }
        }
        table = bigger;
    }

    /** Inserisce hash (diverso da 0); false se c'era già. */
    private static boolean insert(long[] t, long hash) {
        int mask = t.length - 1;
        for (int i = slot(hash, mask); ; i = (i + 1) & mask) {
            if (t[i] == hash) {
                return false;
            }
            if (t[i] == 0) {
                t[i] = hash;
                return true;
            }
        }
    }

    private static int slot(long hash, int mask) {
        return (int) (hash ^ (hash >>> 32)) & mask;
    }
}
