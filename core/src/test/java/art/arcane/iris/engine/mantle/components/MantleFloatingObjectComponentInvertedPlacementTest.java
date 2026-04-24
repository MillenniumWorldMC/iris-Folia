package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.engine.object.FloatingObjectFootprint;
import art.arcane.iris.engine.object.IrisObjectRotation;
import org.junit.Test;

import java.lang.reflect.Constructor;

import static org.junit.Assert.assertEquals;

public class MantleFloatingObjectComponentInvertedPlacementTest {

    private FloatingObjectFootprint footprint(int lowestSolidKeyY, int highestSolidKeyY, int tallestKx, int tallestKz) throws Exception {
        Constructor<FloatingObjectFootprint> constructor = FloatingObjectFootprint.class.getDeclaredConstructor(
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                long[].class
        );
        constructor.setAccessible(true);
        return constructor.newInstance(
                lowestSolidKeyY,
                highestSolidKeyY,
                0,
                0,
                0,
                tallestKx,
                tallestKz,
                99,
                99,
                new long[0]
        );
    }

    @Test
    public void invertedBaseY_anchorsOriginalLowestSolidBelowBottomFace() throws Exception {
        FloatingObjectFootprint footprint = footprint(5, 30, 2, 3);

        assertEquals(104, MantleFloatingObjectComponent.invertedBaseY(100, footprint));
    }

    @Test
    public void invertedBaseX_usesTopFootprintAnchor() throws Exception {
        FloatingObjectFootprint footprint = footprint(5, 30, 2, 3);

        assertEquals(106, MantleFloatingObjectComponent.invertedBaseX(100, 8, footprint));
    }

    @Test
    public void invertedBaseZ_mirrorsTopFootprintAnchor() throws Exception {
        FloatingObjectFootprint footprint = footprint(5, 30, 2, 3);

        assertEquals(111, MantleFloatingObjectComponent.invertedBaseZ(100, 8, footprint));
    }

    @Test
    public void invertedBaseX_usesFixedYRotationAnchor() throws Exception {
        FloatingObjectFootprint footprint = footprint(5, 30, 2, 3);
        IrisObjectRotation rotation = IrisObjectRotation.xFlip180WithY(90);

        assertEquals(111, MantleFloatingObjectComponent.invertedBaseX(100, 8, footprint, rotation));
    }

    @Test
    public void invertedBaseZ_usesFixedYRotationAnchor() throws Exception {
        FloatingObjectFootprint footprint = footprint(5, 30, 2, 3);
        IrisObjectRotation rotation = IrisObjectRotation.xFlip180WithY(90);

        assertEquals(110, MantleFloatingObjectComponent.invertedBaseZ(100, 8, footprint, rotation));
    }

    @Test
    public void invertedBaseY_isStableAcrossFixedYRotation() throws Exception {
        FloatingObjectFootprint footprint = footprint(5, 30, 2, 3);
        IrisObjectRotation rotation = IrisObjectRotation.xFlip180WithY(270);

        assertEquals(104, MantleFloatingObjectComponent.invertedBaseY(100, footprint, rotation));
    }
}
