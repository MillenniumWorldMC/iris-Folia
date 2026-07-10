package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.spi.PlatformBlockState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class IrisStructureComponentMarkerTest {
    @Test
    public void markerFilterAcceptsOnlyStorageContainers() {
        PlatformBlockState storage = mock(PlatformBlockState.class);
        PlatformBlockState solid = mock(PlatformBlockState.class);
        when(storage.isStorageChest()).thenReturn(true);
        when(solid.isStorageChest()).thenReturn(false);

        assertTrue(IrisStructureComponent.shouldWriteStructureMarker(storage));
        assertFalse(IrisStructureComponent.shouldWriteStructureMarker(solid));
        assertFalse(IrisStructureComponent.shouldWriteStructureMarker(null));
    }

    @Test
    public void placementIdIsStableAndCoordinateSensitive() {
        int first = IrisStructureComponent.structurePlacementId("structures/village", "pieces/house", 20, -14, 35);
        int repeated = IrisStructureComponent.structurePlacementId("structures/village", "pieces/house", 20, -14, 35);
        int moved = IrisStructureComponent.structurePlacementId("structures/village", "pieces/house", 21, -14, 35);

        assertEquals(first, repeated);
        assertNotEquals(first, moved);
    }
}
