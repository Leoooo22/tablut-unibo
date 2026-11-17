package it.unibo.tablut.sexyipa.search;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TimeManagerTest {

    private static final long MS = 1_000_000L;

    /** Millisecondi a disposizione per cercare, con lo stato ricevuto all'istante 0. */
    private static long budgetMs(int timeoutSeconds, boolean firstMove) {
        return TimeManager.deadline(0, timeoutSeconds, firstMove) / MS;
    }

    @Test
    void marginIsTenPercentOrTwoSeconds() {
        assertEquals(54_000, budgetMs(60, false)); // margine 6 s (10%)
        assertEquals(8_000, budgetMs(10, false));  // margine 2 s (minimo)
        assertEquals(1_000, budgetMs(3, false));   // margine 2 s (minimo)
    }

    @Test
    void firstMoveHasDoubleMargin() {
        assertEquals(48_000, budgetMs(60, true));
        assertEquals(6_000, budgetMs(10, true));
    }

    @Test
    void veryLowTimeoutStillLeavesSomeTime() {
        assertEquals(750, budgetMs(3, true));  // 3 - 4 < 0: si usa 1/4 del timeout
        assertEquals(250, budgetMs(1, false));
    }

    @Test
    void deadlineIsRelativeToReception() {
        long received = 123_456_789L;
        assertEquals(received + 54_000 * MS, TimeManager.deadline(received, 60, false));
    }
}
