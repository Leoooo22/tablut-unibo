package it.unibo.tablut.sexyipa.eval;

import static it.unibo.tablut.sexyipa.engine.Tables.*;

import it.unibo.tablut.sexyipa.engine.Board;

/**
 * Valutazione per le regole Ashton.
 * <p>
 * Prima controlla le situazioni decise "a una mossa", che la ricerca non vede quando
 * cadono proprio al limite della profondità (effetto orizzonte):
 * <ul>
 *     <li>muove il Bianco e il re ha una via di fuga libera: il Bianco vince;</li>
 *     <li>muove il Nero e può catturare il re: il Nero vince;</li>
 *     <li>muove il Nero e il re ha 2+ vie libere: il Nero ne può chiudere una sola.</li>
 * </ul>
 * Altrimenti somma termini di posizione: materiale, minacce sul re, caselle da cui il re
 * avrebbe una via libera, neri attorno al re, neri sulle caselle che chiudono le uscite d'angolo.
 * <p>
 * Il punteggio è dal punto di vista di chi muove (convenzione negamax) e resta sempre
 * molto lontano da {@code AlphaBetaSearcher.WIN}. Non alloca memoria.
 */
public final class AshtonEvaluator implements Evaluator {

    /** Le 8 caselle che, a coppie, chiudono le 4 uscite attorno a ogni angolo (b3+c2 per a1, ecc.). */
    private static final int[] GUARDS = {
        index(2, 1), index(1, 2), // b3 c2
        index(1, 6), index(2, 7), // g2 h3
        index(6, 1), index(7, 2), // b7 c8
        index(7, 6), index(6, 7), // g8 h7
    };

    /** Per ogni cella, le celle a distanza 2: due passi in linea retta o uno in diagonale. */
    private static final int[][] RING2 = new int[CELLS][];

    /** Nessun campo incontrato lungo il percorso di un nero. */
    private static final int NO_CAMP = -2;

    /**
     * ROUTE_CANDIDATE[c][d]: la linea da c in direzione d arriva al bordo senza campi né castello,
     * quindi può diventare una via di fuga se si svuota. Le altre non lo saranno mai: si scartano
     * senza leggere la scacchiera.
     */
    private static final boolean[][] ROUTE_CANDIDATE = new boolean[CELLS][4];

    /** STOP_LIMIT[k][d]: quante celle della linea da k in direzione d precedono il primo campo o il castello. */
    private static final int[][] STOP_LIMIT = new int[CELLS][4];

    /**
     * PRE_ESCAPE_CANDIDATE[k][d][j]: la j-esima cella della linea da k in direzione d non è un'uscita
     * e ha almeno una linea perpendicolare candidata a via di fuga.
     */
    private static final boolean[][][] PRE_ESCAPE_CANDIDATE = new boolean[CELLS][4][];

    static {
        for (int i = 0; i < CELLS; i++) {
            for (int d = 0; d < 4; d++) {
                boolean candidate = RAYS[i][d].length > 0;
                for (int c : RAYS[i][d]) {
                    if (CELL_TYPE[c] == CAMP || CELL_TYPE[c] == CASTLE) {
                        candidate = false;
                        break;
                    }
                }
                ROUTE_CANDIDATE[i][d] = candidate;
            }
        }
        for (int k = 0; k < CELLS; k++) {
            for (int d = 0; d < 4; d++) {
                int[] ray = RAYS[k][d];
                int p1 = d < 2 ? LEFT : UP;
                int p2 = d < 2 ? RIGHT : DOWN;
                int limit = 0;
                while (limit < ray.length && CELL_TYPE[ray[limit]] != CAMP && CELL_TYPE[ray[limit]] != CASTLE) {
                    limit++;
                }
                STOP_LIMIT[k][d] = limit;
                PRE_ESCAPE_CANDIDATE[k][d] = new boolean[limit];
                for (int j = 0; j < limit; j++) {
                    int c = ray[j];
                    PRE_ESCAPE_CANDIDATE[k][d][j] = CELL_TYPE[c] != ESCAPE
                            && (ROUTE_CANDIDATE[c][p1] || ROUTE_CANDIDATE[c][p2]);
                }
            }
        }
    }

    static {
        int[][] offsets = {{-2, 0}, {2, 0}, {0, -2}, {0, 2}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        for (int i = 0; i < CELLS; i++) {
            int[] tmp = new int[offsets.length];
            int n = 0;
            for (int[] o : offsets) {
                int r = row(i) + o[0];
                int c = col(i) + o[1];
                if (r >= 0 && r < SIZE && c >= 0 && c < SIZE) {
                    tmp[n++] = index(r, c);
                }
            }
            RING2[i] = java.util.Arrays.copyOf(tmp, n);
        }
    }

    private final Weights w;

    public AshtonEvaluator() {
        this(Weights.DEFAULT);
    }

    public AshtonEvaluator(Weights weights) {
        this.w = weights;
    }

    @Override
    public int evaluate(Board b) {
        int score = whiteScore(b);
        return b.whiteToMove() ? score : -score;
    }

    /** Punteggio dal punto di vista del Bianco. */
    int whiteScore(Board b) {
        int material = w.whiteSoldier() * b.whiteCount() - w.blackSoldier() * b.blackCount();
        int king = b.kingPos();
        if (king < 0) {
            return material; // re assente: in partita non succede, la ricerca si ferma prima
        }

        boolean whiteToMove = b.whiteToMove();
        int routes = openRoutes(b, king);

        // Situazioni decise alla prossima mossa. Con il Bianco al tratto e una via libera
        // la cattura non conta: si esce prima di calcolarla.
        if (whiteToMove && routes > 0) {
            return w.escapeNow() + material;
        }
        boolean capturable = kingCapturable(b, king);
        if (!whiteToMove) {
            if (capturable) {
                return -w.kingCapturableNow() + material;
            }
            if (routes >= 2) {
                return w.doubleRoute() + material;
            }
        }

        int score = material;
        if (!whiteToMove && routes == 1) {
            score += w.singleRoute();
        }
        if (whiteToMove && capturable) {
            score -= w.kingThreatened();
        }
        score += w.preEscapeSquare() * Math.min(preEscapeSquares(b, king), w.preEscapeCap());
        score -= w.hostileAdjacent() * hostileAdjacent(b, king);
        score -= w.blackNearKing() * blacksNearKing(b, king);
        score -= w.guardSquare() * guardSquares(b);
        return score;
    }

    /** Neri a distanza 2 dal re. */
    static int blacksNearKing(Board b, int king) {
        return countBlacks(b, RING2[king]);
    }

    /** Neri sulle 8 caselle di guardia delle uscite d'angolo. */
    static int guardSquares(Board b) {
        return countBlacks(b, GUARDS);
    }

    // ─────────────────── vie di fuga ───────────────────

    /** Il re libero di muoversi in cella from: la linea in direzione d è libera fino a un'uscita? */
    static boolean openRoute(Board b, int from, int d) {
        if (!ROUTE_CANDIDATE[from][d]) {
            return false; // campo o castello sulla linea, oppure from già sul bordo
        }
        for (int c : RAYS[from][d]) {
            if (b.cell(c) != Board.EMPTY) {
                return false;
            }
        }
        // Tutte le celle libere e nessun campo: l'ultima è una cella di bordo non campo, cioè un'uscita
        return true;
    }

    /** Numero di vie di fuga libere dalla cella from (0..4). */
    static int openRoutes(Board b, int from) {
        int n = 0;
        for (int d = 0; d < 4; d++) {
            if (openRoute(b, from, d)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Caselle raggiungibili dal re con una mossa (escluse le uscite) da cui avrebbe almeno
     * una via libera in direzione perpendicolare. La direzione di arrivo non si conta:
     * se è libera, il re ha già una via diretta.
     */
    static int preEscapeSquares(Board b, int king) {
        int count = 0;
        for (int d = 0; d < 4; d++) {
            int p1 = d < 2 ? LEFT : UP;
            int p2 = d < 2 ? RIGHT : DOWN;
            int[] ray = RAYS[king][d];
            boolean[] candidate = PRE_ESCAPE_CANDIDATE[king][d];
            int limit = STOP_LIMIT[king][d]; // oltre c'è un campo o il castello: il re non ci arriva
            for (int j = 0; j < limit; j++) {
                int c = ray[j];
                if (b.cell(c) != Board.EMPTY) {
                    break;
                }
                if (candidate[j] && (openRoute(b, c, p1) || openRoute(b, c, p2))) {
                    count++;
                }
            }
        }
        return count;
    }

    // ─────────────────── sicurezza del re ───────────────────

    /** Neri adiacenti al re, più i campi adiacenti se il re è fuori dall'area del castello. */
    static int hostileAdjacent(Board b, int king) {
        boolean castleArea = CASTLE_AREA[king];
        int n = 0;
        for (int d = 0; d < 4; d++) {
            int nb = NEIGHBOR[king][d];
            if (nb < 0) {
                continue;
            }
            if (b.cell(nb) == Board.BLACK || (!castleArea && CELL_TYPE[nb] == CAMP)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Il Nero può catturare il re con la prossima mossa (chiunque sia di turno ora)?
     * Stesse regole di BoardAshton.kingCaptured: 4 neri sul castello, 3 accanto al castello,
     * altrove nero o campo da un lato e un nero che arriva dal lato opposto.
     */
    static boolean kingCapturable(Board b, int king) {
        if (CASTLE_AREA[king]) {
            int missing = -1;
            for (int d = 0; d < 4; d++) {
                int nb = NEIGHBOR[king][d];
                if (nb == CASTLE_POS || b.cell(nb) == Board.BLACK) {
                    continue;
                }
                if (b.cell(nb) != Board.EMPTY || missing >= 0) {
                    return false; // un bianco in mezzo, oppure mancano almeno due neri
                }
                missing = nb;
            }
            return missing >= 0 && blackCanReach(b, missing);
        }
        for (int d = 0; d < 4; d += 2) { // assi: (UP, DOWN) e (LEFT, RIGHT)
            int a = NEIGHBOR[king][d];
            int c = NEIGHBOR[king][d + 1];
            if (a < 0 || c < 0) {
                continue;
            }
            if (hostileToKing(b, a) && isFreeTarget(b, c) && blackCanReach(b, c)) {
                return true;
            }
            if (hostileToKing(b, c) && isFreeTarget(b, a) && blackCanReach(b, a)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hostileToKing(Board b, int c) {
        return b.cell(c) == Board.BLACK || CELL_TYPE[c] == CAMP;
    }

    /**
     * Cella in cui un nero può fermarsi per chiudere il re. Può essere anche un campo vuoto:
     * un nero ancora nel suo campo ci arriva muovendosi dentro lo stesso gruppo.
     */
    private static boolean isFreeTarget(Board b, int c) {
        return b.cell(c) == Board.EMPTY && CELL_TYPE[c] != CASTLE;
    }

    /**
     * Un nero può arrivare nella cella vuota target con una mossa legale?
     * Stesse regole di BoardAshton.generateMoves: il castello blocca, e un nero attraversa
     * celle di campo (target compreso) solo se partono dal suo stesso gruppo di campo.
     */
    static boolean blackCanReach(Board b, int target) {
        int targetGroup = CELL_TYPE[target] == CAMP ? CAMP_GROUP[target] : NO_CAMP;
        for (int d = 0; d < 4; d++) {
            int pathGroup = targetGroup;
            for (int c : RAYS[target][d]) {
                byte p = b.cell(c);
                if (p == Board.EMPTY) {
                    if (CELL_TYPE[c] == CASTLE) {
                        break;
                    }
                    if (CELL_TYPE[c] == CAMP) {
                        if (pathGroup == NO_CAMP) {
                            pathGroup = CAMP_GROUP[c];
                        } else if (pathGroup != CAMP_GROUP[c]) {
                            break;
                        }
                    }
                    continue;
                }
                if (p == Board.BLACK && (pathGroup == NO_CAMP || CAMP_GROUP[c] == pathGroup)) {
                    return true;
                }
                break; // primo pezzo incontrato: oltre non si passa
            }
        }
        return false;
    }

    private static int countBlacks(Board b, int[] cells) {
        int n = 0;
        for (int c : cells) {
            if (b.cell(c) == Board.BLACK) {
                n++;
            }
        }
        return n;
    }
}
