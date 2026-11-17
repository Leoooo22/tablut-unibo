package it.unibo.tablut.sexyipa.search;

/**
 * Calcolo della scadenza per una mossa (CLAUDE.md, sezione 6.3):
 * deadline = ricezione_stato + timeout - margine, margine = max(2 s, 10% del timeout).
 */
public final class TimeManager {

    private static final long SECOND = 1_000_000_000L;
    private static final long MIN_MARGIN = 2 * SECOND;

    private TimeManager() {}

    /**
     * @param receivedNanos  System.nanoTime() al momento in cui è arrivato lo stato dal server
     * @param timeoutSeconds timeout per mossa passato a runmyplayer.sh
     * @param firstMove      true alla prima mossa: margine doppio, la JVM non è ancora "calda"
     */
    public static long deadline(long receivedNanos, int timeoutSeconds, boolean firstMove) {
        long timeout = timeoutSeconds * SECOND;
        long margin = Math.max(MIN_MARGIN, timeout / 10);
        if (firstMove) {
            margin *= 2;
        }
        // Con timeout molto bassi (<= 2 s) il margine supererebbe il timeout: si cerca comunque per 1/4 del tempo
        long budget = Math.max(timeout - margin, timeout / 4);
        return receivedNanos + budget;
    }
}
