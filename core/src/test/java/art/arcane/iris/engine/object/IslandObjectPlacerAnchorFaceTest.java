package art.arcane.iris.engine.object;

import art.arcane.iris.engine.mantle.components.IslandObjectPlacer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IslandObjectPlacerAnchorFaceTest {

    private FloatingIslandSample sampleWithBottomAt(int baseY, int bottomOffset) {
        boolean[] mask = new boolean[10];
        mask[bottomOffset] = true;
        mask[9] = true;
        return FloatingIslandSample.constructForTest(baseY, 10, 9, 2, mask);
    }

    @Test
    public void bottomFace_getHighest_inFootprint_returnsSampleBottomY() {
        FloatingIslandSample[] samples = new FloatingIslandSample[256];
        samples[0] = sampleWithBottomAt(100, 0);

        IslandObjectPlacer placer = new IslandObjectPlacer(null, samples, 0, 0, 100, IslandObjectPlacer.AnchorFace.BOTTOM);

        int result = placer.getHighest(0, 0, null);
        assertEquals(100, result);
    }

    @Test
    public void bottomFace_getHighest_offFootprint_returnsChunkMinBottomY() {
        FloatingIslandSample[] samples = new FloatingIslandSample[256];
        samples[0] = sampleWithBottomAt(100, 0);

        IslandObjectPlacer placer = new IslandObjectPlacer(null, samples, 0, 0, 100, IslandObjectPlacer.AnchorFace.BOTTOM);

        int result = placer.getHighest(15, 15, null);
        assertEquals(100, result);
    }

    @Test
    public void bottomFace_set_aboveAnchor_dropsWrite() {
        FloatingIslandSample[] samples = new FloatingIslandSample[256];
        samples[0] = sampleWithBottomAt(100, 0);

        IslandObjectPlacer placer = new IslandObjectPlacer(null, samples, 0, 0, 100, IslandObjectPlacer.AnchorFace.BOTTOM);

        assertEquals(false, placer.canWriteObjectBlock(0, 101, 0));
        placer.set(0, 101, 0, null);
    }

    @Test
    public void bottomFace_canWriteObjectBlock_allowsBelowAnchor() {
        FloatingIslandSample[] samples = new FloatingIslandSample[256];
        samples[0] = sampleWithBottomAt(100, 0);

        IslandObjectPlacer placer = new IslandObjectPlacer(null, samples, 0, 0, 100, IslandObjectPlacer.AnchorFace.BOTTOM);

        assertEquals(true, placer.canWriteObjectBlock(0, 99, 0));
    }

    @Test
    public void bottomFace_canWriteObjectBlock_blocksAnchorAndAbove() {
        FloatingIslandSample[] samples = new FloatingIslandSample[256];
        samples[0] = sampleWithBottomAt(100, 0);

        IslandObjectPlacer placer = new IslandObjectPlacer(null, samples, 0, 0, 100, IslandObjectPlacer.AnchorFace.BOTTOM);

        assertEquals(false, placer.canWriteObjectBlock(0, 100, 0));
        assertEquals(false, placer.canWriteObjectBlock(0, 101, 0));
    }

    @Test
    public void topFace_existingConstructor_dropsBelowAnchor_noRegression() {
        FloatingIslandSample[] samples = new FloatingIslandSample[256];
        samples[0] = sampleWithBottomAt(100, 0);

        IslandObjectPlacer placer = new IslandObjectPlacer(null, samples, 0, 0, 105);

        assertEquals(false, placer.canWriteObjectBlock(1, 104, 0));
        placer.set(1, 104, 0, null);
    }
}
