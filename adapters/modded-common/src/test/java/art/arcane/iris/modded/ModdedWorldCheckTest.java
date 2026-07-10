package art.arcane.iris.modded;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModdedWorldCheckTest {
    @Test
    public void coordinatorThreadIsNonDaemon() {
        Thread thread = ModdedWorldCheck.coordinatorThread(() -> {
        });

        assertFalse(thread.isDaemon());
    }

    @Test
    public void passStopsServerBeforeZeroExit() {
        List<String> events = new ArrayList<>();

        ModdedWorldCheck.stopAndExit(
                () -> events.add("stop"),
                0,
                status -> events.add("exit:" + status)
        );

        assertEquals(List.of("stop", "exit:0"), events);
    }

    @Test
    public void failureStopsServerBeforeNonzeroExit() {
        List<String> events = new ArrayList<>();

        ModdedWorldCheck.stopAndExit(
                () -> events.add("stop"),
                1,
                status -> events.add("exit:" + status)
        );

        assertEquals(List.of("stop", "exit:1"), events);
    }

    @Test
    public void shutdownFailureForcesNonzeroExit() {
        AtomicInteger status = new AtomicInteger(-1);

        ModdedWorldCheck.stopAndExit(
                () -> {
                    throw new IllegalStateException("shutdown failed");
                },
                0,
                status::set
        );

        assertEquals(1, status.get());
    }

    @Test
    public void interruptionIsClearedForShutdownAndRestoredBeforeExit() {
        AtomicBoolean interruptedDuringStop = new AtomicBoolean(true);
        AtomicBoolean interruptedDuringExit = new AtomicBoolean(false);
        AtomicInteger status = new AtomicInteger(-1);
        Thread.currentThread().interrupt();
        try {
            ModdedWorldCheck.stopAndExit(
                    () -> interruptedDuringStop.set(Thread.currentThread().isInterrupted()),
                    0,
                    exitStatus -> {
                        status.set(exitStatus);
                        interruptedDuringExit.set(Thread.currentThread().isInterrupted());
                    }
            );

            assertFalse(interruptedDuringStop.get());
            assertTrue(interruptedDuringExit.get());
            assertEquals(1, status.get());
        } finally {
            Thread.interrupted();
        }
    }
}
