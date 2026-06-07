package art.arcane.iris.engine.object.tree;

import art.arcane.iris.engine.object.IrisProceduralTree;
import art.arcane.iris.engine.object.IrisTreeBranchProbability;
import art.arcane.iris.engine.object.IrisTreeBranches;
import art.arcane.iris.engine.object.IrisTreeFunction;
import art.arcane.iris.engine.object.IrisTreeLeafMode;
import art.arcane.iris.engine.object.IrisTreeProfile;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProceduralTreeGeneratorTest {

    @Test
    public void trunkBuildsConnectedColumnOfRequestedHeight() {
        IrisProceduralTree tree = new IrisProceduralTree();
        tree.setTrunk("minecraft:oak_log");
        tree.setTrunkWidth(1);

        TreeBlockCanvas canvas = new TreeBlockCanvas();
        int height = 12;
        TreeTrunkBuilder.build(canvas, tree, height);

        for (int y = 0; y < height; y++) {
            assertTrue("trunk column missing at y=" + y, canvas.has(0, y, 0));
        }
        assertEquals(height, canvas.getTrunk().size());
    }

    @Test
    public void leaningTrunkStaysConnected() {
        IrisProceduralTree tree = new IrisProceduralTree();
        tree.setTrunk("minecraft:oak_log");
        tree.setLeanAngle(35);
        tree.setLeanAzimuth(45);

        TreeBlockCanvas canvas = new TreeBlockCanvas();
        int height = 16;
        TreeTrunkBuilder.build(canvas, tree, height);

        for (int y = 0; y < height; y++) {
            boolean layerHasBlock = false;
            for (TreeBlockCanvas.Vec v : canvas.getTrunk()) {
                if (v.y() == y) {
                    layerHasBlock = true;
                    break;
                }
            }
            assertTrue("leaning trunk has a gap at y=" + y, layerHasBlock);
        }
    }

    @Test
    public void volumeCanopyProducesLeavesAboveTrunk() {
        IrisProceduralTree tree = new IrisProceduralTree();
        tree.setTrunk("minecraft:oak_log");
        tree.setLeaves("minecraft:oak_leaves");
        tree.setProfile(IrisTreeProfile.OAK);

        TreeBlockCanvas canvas = new TreeBlockCanvas();
        int height = 9;
        double[][] offsets = TreeTrunkBuilder.build(canvas, tree, height);
        TreeCanopyBuilder.build(canvas, tree, height, offsets, 1234L, null);

        assertFalse("canopy produced no leaves", canvas.getLeaf().isEmpty());

        int maxLeafY = Integer.MIN_VALUE;
        for (TreeBlockCanvas.Vec v : canvas.getLeaf()) {
            maxLeafY = Math.max(maxLeafY, v.y());
        }
        assertTrue("leaves should rise above the trunk top", maxLeafY >= height - 1);
    }

    @Test
    public void hollowCanopyHasFewerLeavesThanFilled() {
        TreeBlockCanvas filled = canopyOnly(IrisTreeLeafMode.FILLED);
        TreeBlockCanvas hollow = canopyOnly(IrisTreeLeafMode.HOLLOW);
        assertTrue("hollow canopy should place fewer leaves than filled",
                hollow.getLeaf().size() < filled.getLeaf().size());
        assertFalse("hollow canopy still places a leaf shell", hollow.getLeaf().isEmpty());
    }

    private TreeBlockCanvas canopyOnly(IrisTreeLeafMode mode) {
        IrisProceduralTree tree = new IrisProceduralTree();
        tree.setTrunk("minecraft:oak_log");
        tree.setLeaves("minecraft:oak_leaves");
        tree.setProfile(IrisTreeProfile.OAK);
        tree.getCanopy().setMode(mode);
        TreeBlockCanvas canvas = new TreeBlockCanvas();
        int height = 10;
        double[][] offsets = TreeTrunkBuilder.build(canvas, tree, height);
        TreeCanopyBuilder.build(canvas, tree, height, offsets, 99L, null);
        return canvas;
    }

    @Test
    public void branchCanopyRecordsEndpoints() {
        IrisProceduralTree tree = new IrisProceduralTree();
        tree.setTrunk("minecraft:jungle_log");
        tree.setLeaves("minecraft:jungle_leaves");
        tree.setProfile(IrisTreeProfile.JUNGLE);
        IrisTreeBranches branches = new IrisTreeBranches();
        branches.setProbabilityFunction(IrisTreeBranchProbability.CONSTANT);
        branches.setProbabilityConstant(1.0);
        branches.setLengthFunction(IrisTreeFunction.CONSTANT);
        branches.setLengthConstant(4);
        branches.setAzimuthMode(art.arcane.iris.engine.object.IrisTreeAzimuthMode.GOLDEN_ANGLE);
        tree.getCanopy().setBranches(branches);

        TreeBlockCanvas canvas = new TreeBlockCanvas();
        int height = 14;
        double[][] offsets = TreeTrunkBuilder.build(canvas, tree, height);
        List<int[]> endpoints = new java.util.ArrayList<>();
        TreeCanopyBuilder.build(canvas, tree, height, offsets, 777L, endpoints);

        assertFalse("branch system recorded no endpoints", endpoints.isEmpty());
        assertFalse("branch system placed no leaves", canvas.getLeaf().isEmpty());
    }

    @Test
    public void rootsExtendBelowTheBase() {
        IrisProceduralTree tree = new IrisProceduralTree();
        tree.setTrunk("minecraft:oak_log");

        TreeBlockCanvas canvas = new TreeBlockCanvas();
        int height = 14;
        TreeTrunkBuilder.build(canvas, tree, height);
        TreeRootBuilder.build(canvas, tree, height, 42L);

        int minY = Integer.MAX_VALUE;
        for (TreeBlockCanvas.Vec v : canvas.getTrunk()) {
            minY = Math.min(minY, v.y());
        }
        assertTrue("roots should descend below the trunk base", minY < 0);
    }

    @Test
    public void plausibilityDistancesPropagateFromWood() {
        Set<TreeBlockCanvas.Vec> trunk = new HashSet<>();
        trunk.add(new TreeBlockCanvas.Vec(0, 0, 0));
        trunk.add(new TreeBlockCanvas.Vec(0, 1, 0));

        TreeBlockCanvas.Vec a = new TreeBlockCanvas.Vec(1, 1, 0);
        TreeBlockCanvas.Vec b = new TreeBlockCanvas.Vec(2, 1, 0);
        TreeBlockCanvas.Vec c = new TreeBlockCanvas.Vec(3, 1, 0);
        TreeBlockCanvas.Vec far = new TreeBlockCanvas.Vec(40, 1, 0);
        Set<TreeBlockCanvas.Vec> realLeaves = new HashSet<>();
        realLeaves.add(a);
        realLeaves.add(b);
        realLeaves.add(c);
        realLeaves.add(far);

        Map<TreeBlockCanvas.Vec, Integer> distances = TreePlausibility.computeDistances(trunk, realLeaves);

        assertEquals(Integer.valueOf(1), distances.get(a));
        assertEquals(Integer.valueOf(2), distances.get(b));
        assertEquals(Integer.valueOf(3), distances.get(c));
        assertNull("disconnected leaf must remain unsupported", distances.get(far));
    }

    @Test
    public void plausibilityDistanceCapsAtSeven() {
        Set<TreeBlockCanvas.Vec> trunk = new HashSet<>();
        trunk.add(new TreeBlockCanvas.Vec(0, 0, 0));

        Set<TreeBlockCanvas.Vec> realLeaves = new HashSet<>();
        for (int x = 1; x <= 12; x++) {
            realLeaves.add(new TreeBlockCanvas.Vec(x, 0, 0));
        }

        Map<TreeBlockCanvas.Vec, Integer> distances = TreePlausibility.computeDistances(trunk, realLeaves);

        for (int x = 1; x <= 7; x++) {
            assertEquals("leaf at distance " + x + " should be reachable",
                    Integer.valueOf(x), distances.get(new TreeBlockCanvas.Vec(x, 0, 0)));
        }
        for (int x = 8; x <= 12; x++) {
            assertNull("leaves beyond distance 7 must remain unsupported",
                    distances.get(new TreeBlockCanvas.Vec(x, 0, 0)));
        }
    }
}
