package it.unibo.tablut.sexyipa.eval;

/**
 * Pesi di {@link AshtonEvaluator}, tutti dal punto di vista del Bianco e tutti positivi:
 * il segno lo decide l'evaluator. Scala: un soldato nero vale 100.
 * <p>
 * I valori di {@link #DEFAULT} sono un punto di partenza scelto a mano, non tarato:
 * vanno verificati con partite contro {@link SimpleEvaluator}.
 *
 * @param whiteSoldier          valore di un soldato bianco
 * @param blackSoldier          valore di un soldato nero
 * @param escapeNow             muove il Bianco e il re ha una via di fuga libera: vince alla prossima mossa
 * @param kingCapturableNow     muove il Nero e può catturare il re con una mossa
 * @param doubleRoute           muove il Nero e il re ha almeno 2 vie libere: il Nero può chiuderne una sola
 * @param singleRoute           muove il Nero e il re ha 1 via libera: il Nero è costretto a chiuderla
 * @param kingThreatened        muove il Bianco e il re è catturabile alla prossima mossa nera
 * @param preEscapeSquare       per ogni casella raggiungibile dal re da cui avrebbe una via libera
 * @param preEscapeCap          numero massimo di caselle contate per {@code preEscapeSquare}
 * @param hostileAdjacent       per ogni nero (o campo, fuori dall'area del castello) adiacente al re
 * @param blackNearKing         per ogni nero a distanza 2 dal re (2 in linea o in diagonale)
 * @param guardSquare           per ogni nero su una delle 8 caselle che chiudono le uscite d'angolo
 */
public record Weights(
        int whiteSoldier,
        int blackSoldier,
        int escapeNow,
        int kingCapturableNow,
        int doubleRoute,
        int singleRoute,
        int kingThreatened,
        int preEscapeSquare,
        int preEscapeCap,
        int hostileAdjacent,
        int blackNearKing,
        int guardSquare) {

    public static final Weights DEFAULT = new Weights(
            200,     // whiteSoldier
            100,     // blackSoldier
            50_000,  // escapeNow
            50_000,  // kingCapturableNow
            40_000,  // doubleRoute
            300,     // singleRoute
            400,     // kingThreatened
            150,     // preEscapeSquare
            4,       // preEscapeCap
            60,      // hostileAdjacent
            25,      // blackNearKing
            80);     // guardSquare
}
