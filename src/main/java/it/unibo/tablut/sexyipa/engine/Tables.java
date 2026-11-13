package it.unibo.tablut.sexyipa.engine;

/**
 * Tabelle statiche precalcolate sulla geometria della scacchiera Ashton.
 * Indice cella = riga * 9 + colonna (riga 0 = riga "1" del server, colonna 0 = "a").
 */
public final class Tables {

    public static final int SIZE = 9;
    public static final int CELLS = SIZE * SIZE;

    // Tipi di cella
    public static final byte NORMAL = 0;
    public static final byte CASTLE = 1;
    public static final byte CAMP = 2;
    public static final byte ESCAPE = 3;

    // Direzioni: l'opposta di d è d ^ 1
    public static final int UP = 0;    // riga - 1
    public static final int DOWN = 1;  // riga + 1
    public static final int LEFT = 2;  // colonna - 1
    public static final int RIGHT = 3; // colonna + 1

    private static final int[] DROW = {-1, 1, 0, 0};
    private static final int[] DCOL = {0, 0, -1, 1};

    public static final int CASTLE_POS = 4 * SIZE + 4; // e5

    public static final byte[] CELL_TYPE = new byte[CELLS];
    /** Gruppo di campo 0..3 (alto, basso, sinistra, destra), -1 se la cella non è un campo. */
    public static final byte[] CAMP_GROUP = new byte[CELLS];
    /** Vicino ortogonale in direzione d, -1 se fuori dalla scacchiera. */
    public static final int[][] NEIGHBOR = new int[CELLS][4];
    /** Celle in linea retta in direzione d, dalla più vicina al bordo (origine esclusa). */
    public static final int[][][] RAYS = new int[CELLS][4][];

    /**
     * Celle che fanno da incudine quando cattura il Bianco, a prescindere dal contenuto:
     * castello e campi, esclusi i 4 centrali a5 e1 e9 i5 (punto C).
     */
    public static final boolean[] ANVIL_FOR_WHITE = new boolean[CELLS];
    /** Celle che fanno da incudine quando cattura il Nero: castello e tutti i campi. */
    public static final boolean[] ANVIL_FOR_BLACK = new boolean[CELLS];
    /** Castello e le 4 celle adiacenti: lì il re si cattura circondandolo su tutti i lati liberi. */
    public static final boolean[] CASTLE_AREA = new boolean[CELLS];

    /** Chiavi Zobrist per cella e pezzo (indice = valore di Board.cell, EMPTY non usato). */
    public static final long[][] ZOBRIST = new long[CELLS][4];
    /** Si applica allo hash quando muove il Nero. */
    public static final long ZOBRIST_BLACK_TO_MOVE;
    private static final long ZOBRIST_SEED = 0x5E7A1B0A5ADL;

    private Tables() {}

    public static int index(int row, int col) {
        return row * SIZE + col;
    }

    public static int row(int index) {
        return index / SIZE;
    }

    public static int col(int index) {
        return index % SIZE;
    }

    static {
        java.util.Arrays.fill(CAMP_GROUP, (byte) -1);

        // Campi: 3 celle sul bordo + 1 cella verso l'interno, per ogni lato
        int[][][] camps = {
            {{0, 3}, {0, 4}, {0, 5}, {1, 4}}, // alto:     d1 e1 f1 e2
            {{8, 3}, {8, 4}, {8, 5}, {7, 4}}, // basso:    d9 e9 f9 e8
            {{3, 0}, {4, 0}, {5, 0}, {4, 1}}, // sinistra: a4 a5 a6 b5
            {{3, 8}, {4, 8}, {5, 8}, {4, 7}}, // destra:   i4 i5 i6 h5
        };
        for (int g = 0; g < camps.length; g++) {
            for (int[] rc : camps[g]) {
                int i = index(rc[0], rc[1]);
                CELL_TYPE[i] = CAMP;
                CAMP_GROUP[i] = (byte) g;
            }
        }

        CELL_TYPE[CASTLE_POS] = CASTLE;

        // Uscite: qualunque cella di bordo che non sia un campo, angoli compresi (punto A)
        for (int i = 0; i < CELLS; i++) {
            int r = row(i), c = col(i);
            boolean border = r == 0 || r == SIZE - 1 || c == 0 || c == SIZE - 1;
            if (border && CELL_TYPE[i] != CAMP) {
                CELL_TYPE[i] = ESCAPE;
            }
        }

        for (int i = 0; i < CELLS; i++) {
            for (int d = 0; d < 4; d++) {
                int r = row(i) + DROW[d];
                int c = col(i) + DCOL[d];
                NEIGHBOR[i][d] = inside(r, c) ? index(r, c) : -1;

                int len = 0;
                for (int rr = r, cc = c; inside(rr, cc); rr += DROW[d], cc += DCOL[d]) {
                    len++;
                }
                int[] ray = new int[len];
                for (int k = 0; k < len; k++) {
                    ray[k] = index(r + k * DROW[d], c + k * DCOL[d]);
                }
                RAYS[i][d] = ray;
            }
        }

        for (int i = 0; i < CELLS; i++) {
            ANVIL_FOR_BLACK[i] = CELL_TYPE[i] == CASTLE || CELL_TYPE[i] == CAMP;
            ANVIL_FOR_WHITE[i] = ANVIL_FOR_BLACK[i];
        }
        for (int i : new int[] {index(0, 4), index(4, 0), index(4, 8), index(8, 4)}) {
            ANVIL_FOR_WHITE[i] = false;
        }

        CASTLE_AREA[CASTLE_POS] = true;
        for (int d = 0; d < 4; d++) {
            CASTLE_AREA[NEIGHBOR[CASTLE_POS][d]] = true;
        }

        // Seed fisso: stesso hash a ogni esecuzione, test riproducibili
        java.util.SplittableRandom rng = new java.util.SplittableRandom(ZOBRIST_SEED);
        for (int i = 0; i < CELLS; i++) {
            for (int p = 0; p < 4; p++) {
                ZOBRIST[i][p] = rng.nextLong();
            }
        }
        ZOBRIST_BLACK_TO_MOVE = rng.nextLong();
    }

    private static boolean inside(int r, int c) {
        return r >= 0 && r < SIZE && c >= 0 && c < SIZE;
    }
}
