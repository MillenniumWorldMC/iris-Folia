package art.arcane.iris.modded.service;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ModdedChunkUpdateServiceTest {
    @Test
    public void scansWhenPlayersArePresent() {
        assertTrue(ModdedChunkUpdateService.hasUpdateTargets(true, false));
    }

    @Test
    public void scansHeadlessForceLoadedChunks() {
        assertTrue(ModdedChunkUpdateService.hasUpdateTargets(false, true));
    }

    @Test
    public void skipsLevelsWithoutPlayersOrForcedChunks() {
        assertFalse(ModdedChunkUpdateService.hasUpdateTargets(false, false));
    }
}
