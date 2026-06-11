package art.arcane.iris.engine.mantle.components;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineMetrics;
import art.arcane.iris.engine.framework.SeedManager;
import art.arcane.iris.engine.mantle.MantleWriter;
import art.arcane.iris.engine.object.IrisCaveFieldModule;
import art.arcane.iris.engine.object.IrisCaveProfile;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.engine.object.IrisGeneratorStyle;
import art.arcane.iris.engine.object.IrisRange;
import art.arcane.iris.engine.object.IrisStyledRange;
import art.arcane.iris.engine.object.IrisWorld;
import art.arcane.iris.engine.object.NoiseStyle;
import art.arcane.iris.util.project.noise.CNG;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterCavern;
import art.arcane.volmlib.util.matter.MatterSlice;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.data.BlockData;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class IrisCaveCarver3DNearParityTest {
    private static Method sampleDensityMethod;
    private static Field engineField;
    private static Field dataField;
    private static Field profileField;
    private static Field surfaceBreakDensityField;
    private static Field thresholdRngField;
    private static Field carveAirField;
    private static Field carveLavaField;
    private static Field carveForcedAirField;

    @BeforeClass
    public static void setupBukkit() throws Exception {
        if (Bukkit.getServer() == null) {
            Server server = mock(Server.class);
            doReturn(Logger.getLogger("IrisTest")).when(server).getLogger();
            doReturn("IrisTestServer").when(server).getName();
            doReturn("1.0").when(server).getVersion();
            doReturn("1.0").when(server).getBukkitVersion();
            doAnswer((InvocationOnMock invocation) -> namedBlockData(invocation.getArgument(0, Material.class).name().toLowerCase(Locale.ROOT))).when(server).createBlockData(any(Material.class));
            doAnswer((InvocationOnMock invocation) -> namedBlockData(invocation.getArgument(0, String.class))).when(server).createBlockData(anyString());
            Bukkit.setServer(server);
        }

        sampleDensityMethod = IrisCaveCarver3D.class.getDeclaredMethod("sampleDensityOptimized", int.class, int.class, int.class);
        sampleDensityMethod.setAccessible(true);
        engineField = IrisCaveCarver3D.class.getDeclaredField("engine");
        engineField.setAccessible(true);
        dataField = IrisCaveCarver3D.class.getDeclaredField("data");
        dataField.setAccessible(true);
        profileField = IrisCaveCarver3D.class.getDeclaredField("profile");
        profileField.setAccessible(true);
        surfaceBreakDensityField = IrisCaveCarver3D.class.getDeclaredField("surfaceBreakDensity");
        surfaceBreakDensityField.setAccessible(true);
        thresholdRngField = IrisCaveCarver3D.class.getDeclaredField("thresholdRng");
        thresholdRngField.setAccessible(true);
        carveAirField = IrisCaveCarver3D.class.getDeclaredField("carveAir");
        carveAirField.setAccessible(true);
        carveLavaField = IrisCaveCarver3D.class.getDeclaredField("carveLava");
        carveLavaField.setAccessible(true);
        carveForcedAirField = IrisCaveCarver3D.class.getDeclaredField("carveForcedAir");
        carveForcedAirField.setAccessible(true);
    }

    private static BlockData namedBlockData(String key) {
        String canonical = key.indexOf(':') >= 0 ? key : "minecraft:" + key;
        BlockData data = mock(BlockData.class);
        doReturn(canonical).when(data).getAsString();
        return data;
    }

    @Test
    public void carvedCellDistributionStableAcrossEquivalentCarvers() {
        Engine engine = createEngine(128, 92);

        IrisCaveCarver3D firstCarver = new IrisCaveCarver3D(engine, createProfile(true, true));
        WriterCapture firstCapture = createWriterCapture(128);
        int firstCarved = firstCarver.carve(firstCapture.writer, 7, -3);

        IrisCaveCarver3D secondCarver = new IrisCaveCarver3D(engine, createProfile(true, true));
        WriterCapture secondCapture = createWriterCapture(128);
        int secondCarved = secondCarver.carve(secondCapture.writer, 7, -3);

        assertTrue(firstCarved > 0);
        assertEquals(firstCarved, secondCarved);
        assertEquals(firstCapture.carvedCells, secondCapture.carvedCells);
        assertEquals(firstCapture.carvedLiquids, secondCapture.carvedLiquids);
    }

    @Test
    public void exactPathCarvesChunkEdgesAndRespectsWorldHeightClipping() {
        Engine engine = createEngine(48, 46);
        IrisCaveCarver3D carver = new IrisCaveCarver3D(engine, createProfile(true, true));
        WriterCapture capture = createWriterCapture(48);
        double[] columnWeights = fullWeights();
        int[] precomputedSurfaceHeights = filledHeights(46);

        int carved = carver.carve(capture.writer, 0, 0, columnWeights, 0D, 0D, new IrisRange(0D, 80D), precomputedSurfaceHeights);

        assertTrue(carved > 0);
        assertTrue(hasX(capture.carvedCells, 14));
        assertTrue(hasX(capture.carvedCells, 15));
        assertTrue(hasZ(capture.carvedCells, 14));
        assertTrue(hasZ(capture.carvedCells, 15));
        assertTrue(maxY(capture.carvedCells) <= 47);
        assertTrue(minY(capture.carvedCells) >= 0);
    }

    @Test
    public void exactPathMatchesNaiveReferenceWithoutWarpOrModules() throws Exception {
        assertExactParity(false, false, false);
    }

    @Test
    public void exactPathMatchesNaiveReferenceWithWarpAndModules() throws Exception {
        assertExactParity(true, true, false);
    }

    @Test
    public void legacySampleStepTwoMatchesExactReference() throws Exception {
        Engine engine = createEngine(96, 90);
        double[] columnWeights = fullWeights();
        int[] precomputedSurfaceHeights = filledHeights(90);
        IrisRange worldYRange = new IrisRange(0D, 88D);

        IrisCaveProfile exactProfile = createProfile(true, true).setSampleStep(1).setAdaptiveSampling(false);
        IrisCaveCarver3D exactCarver = new IrisCaveCarver3D(engine, exactProfile);
        WriterCapture exactCapture = createWriterCapture(96);
        int exactCarved = exactCarver.carve(exactCapture.writer, 5, -1, columnWeights, 0D, 0D, worldYRange, precomputedSurfaceHeights);

        IrisCaveProfile legacyProfile = createProfile(true, true).setSampleStep(2).setAdaptiveSampling(false);
        IrisCaveCarver3D legacyCarver = new IrisCaveCarver3D(engine, legacyProfile);
        WriterCapture legacyCapture = createWriterCapture(96);
        int legacyCarved = legacyCarver.carve(legacyCapture.writer, 5, -1, columnWeights, 0D, 0D, worldYRange, precomputedSurfaceHeights);

        assertEquals(exactCarved, legacyCarved);
        assertEquals(exactCapture.carvedCells, legacyCapture.carvedCells);
        assertEquals(exactCapture.carvedLiquids, legacyCapture.carvedLiquids);
    }

    @Test
    public void exactPathUsesExpectedLavaAndForcedAirBands() {
        Engine engine = createEngine(48, 46);
        double[] columnWeights = fullWeights();
        int[] precomputedSurfaceHeights = filledHeights(46);

        IrisCaveProfile lavaProfile = createProfile(false, false).setAllowLava(true).setAllowWater(false);
        IrisCaveCarver3D lavaCarver = new IrisCaveCarver3D(engine, lavaProfile);
        WriterCapture lavaCapture = createWriterCapture(48);
        lavaCarver.carve(lavaCapture.writer, 0, 0, columnWeights, 0D, 0D, new IrisRange(0D, 80D), precomputedSurfaceHeights);

        IrisCaveProfile forcedAirProfile = createProfile(false, false).setAllowLava(false).setAllowWater(false);
        IrisCaveCarver3D forcedAirCarver = new IrisCaveCarver3D(engine, forcedAirProfile);
        WriterCapture forcedAirCapture = createWriterCapture(48);
        forcedAirCarver.carve(forcedAirCapture.writer, 0, 0, columnWeights, 0D, 0D, new IrisRange(0D, 80D), precomputedSurfaceHeights);

        assertTrue(containsLiquidInRange(lavaCapture.carvedLiquids, 0, 18, (byte) 2));
        assertTrue(containsLiquidInRange(forcedAirCapture.carvedLiquids, 0, 18, (byte) 3));
        assertTrue(containsLiquidInRange(lavaCapture.carvedLiquids, 19, 47, (byte) 0));
        assertTrue(containsLiquidInRange(forcedAirCapture.carvedLiquids, 19, 47, (byte) 0));
    }

    @Test
    public void optimizedExactPathOutperformsNaiveReference() throws Exception {
        Engine engine = createEngine(128, 92);
        IrisCaveCarver3D optimizedCarver = new IrisCaveCarver3D(engine, createProfile(true, true).setAdaptiveSampling(false));
        IrisCaveCarver3D naiveCarver = new IrisCaveCarver3D(engine, createProfile(true, true).setAdaptiveSampling(false));
        double[] columnWeights = fullWeights();
        int[] precomputedSurfaceHeights = filledHeights(92);
        IrisRange worldYRange = new IrisRange(0D, 96D);

        for (int warmup = 0; warmup < 4; warmup++) {
            runOptimizedOnce(optimizedCarver, 3, -2, columnWeights, worldYRange, precomputedSurfaceHeights, 128);
            runNaiveOnce(naiveCarver, 3, -2, columnWeights, worldYRange, precomputedSurfaceHeights, 128);
        }

        long optimizedTime = 0L;
        long naiveTime = 0L;
        for (int iteration = 0; iteration < 10; iteration++) {
            if ((iteration & 1) == 0) {
                optimizedTime += runOptimizedOnce(optimizedCarver, 3, -2, columnWeights, worldYRange, precomputedSurfaceHeights, 128);
                naiveTime += runNaiveOnce(naiveCarver, 3, -2, columnWeights, worldYRange, precomputedSurfaceHeights, 128);
                continue;
            }

            naiveTime += runNaiveOnce(naiveCarver, 3, -2, columnWeights, worldYRange, precomputedSurfaceHeights, 128);
            optimizedTime += runOptimizedOnce(optimizedCarver, 3, -2, columnWeights, worldYRange, precomputedSurfaceHeights, 128);
        }

        double speedup = naiveTime / (double) optimizedTime;
        assertTrue("expected at least 2.0x speedup but was " + speedup, speedup >= 2D);
    }

    @Test
    public void adaptivePathStaysNearExactReferenceWithWarpAndModules() throws Exception {
        Engine engine = createEngine(96, 90);
        double[] columnWeights = fullWeights();
        int[] precomputedSurfaceHeights = filledHeights(90);
        IrisRange worldYRange = new IrisRange(0D, 88D);

        IrisCaveCarver3D adaptiveCarver = new IrisCaveCarver3D(engine, createProfile(true, true).setAdaptiveSampling(true));
        WriterCapture adaptiveCapture = createWriterCapture(96);
        int adaptiveCarved = adaptiveCarver.carve(adaptiveCapture.writer, 5, -1, columnWeights, 0D, 0D, worldYRange, precomputedSurfaceHeights);

        IrisCaveCarver3D exactCarver = new IrisCaveCarver3D(engine, createProfile(true, true).setAdaptiveSampling(false));
        WriterCapture exactCapture = createWriterCapture(96);
        int exactCarved = exactCarver.carve(exactCapture.writer, 5, -1, columnWeights, 0D, 0D, worldYRange, precomputedSurfaceHeights);

        Set<String> differingCells = new HashSet<>(adaptiveCapture.carvedCells);
        differingCells.addAll(exactCapture.carvedCells);
        Set<String> sharedCells = new HashSet<>(adaptiveCapture.carvedCells);
        sharedCells.retainAll(exactCapture.carvedCells);
        differingCells.removeAll(sharedCells);

        int baselineCells = Math.max(1, exactCapture.carvedCells.size());
        double carveDeltaRatio = Math.abs(adaptiveCarved - exactCarved) / (double) baselineCells;
        double differingRatio = differingCells.size() / (double) baselineCells;
        assertTrue("expected carve count delta below 2.5% but was " + carveDeltaRatio, carveDeltaRatio <= 0.025D);
        assertTrue("expected carved cell delta below 3.5% but was " + differingRatio, differingRatio <= 0.035D);
    }

    private void assertExactParity(boolean warp, boolean modules, boolean adaptiveSampling) throws Exception {
        Engine engine = createEngine(96, 90);
        double[] columnWeights = fullWeights();
        int[] precomputedSurfaceHeights = filledHeights(90);
        IrisRange worldYRange = new IrisRange(0D, 88D);

        IrisCaveCarver3D optimizedCarver = new IrisCaveCarver3D(engine, createProfile(warp, modules).setAdaptiveSampling(adaptiveSampling));
        WriterCapture optimizedCapture = createWriterCapture(96);
        int optimizedCarved = optimizedCarver.carve(optimizedCapture.writer, 5, -1, columnWeights, 0D, 0D, worldYRange, precomputedSurfaceHeights);

        IrisCaveCarver3D naiveCarver = new IrisCaveCarver3D(engine, createProfile(warp, modules).setAdaptiveSampling(false));
        WriterCapture naiveCapture = createWriterCapture(96);
        int naiveCarved = carveNaiveExact(naiveCarver, naiveCapture.writer, 5, -1, columnWeights, worldYRange, precomputedSurfaceHeights);

        assertEquals(optimizedCarved, naiveCarved);
        assertEquals(optimizedCapture.carvedCells, naiveCapture.carvedCells);
        assertEquals(optimizedCapture.carvedLiquids, naiveCapture.carvedLiquids);
    }

    private long runOptimizedOnce(IrisCaveCarver3D carver, int chunkX, int chunkZ, double[] columnWeights, IrisRange worldYRange, int[] precomputedSurfaceHeights, int worldHeight) {
        WriterCapture capture = createWriterCapture(worldHeight);
        long start = System.nanoTime();
        carver.carve(capture.writer, chunkX, chunkZ, columnWeights, 0D, 0D, worldYRange, precomputedSurfaceHeights);
        long elapsed = System.nanoTime() - start;
        assertTrue(!capture.carvedCells.isEmpty());
        return elapsed;
    }

    private long runNaiveOnce(IrisCaveCarver3D carver, int chunkX, int chunkZ, double[] columnWeights, IrisRange worldYRange, int[] precomputedSurfaceHeights, int worldHeight) throws Exception {
        WriterCapture capture = createWriterCapture(worldHeight);
        long start = System.nanoTime();
        int carved = carveNaiveExact(carver, capture.writer, chunkX, chunkZ, columnWeights, worldYRange, precomputedSurfaceHeights);
        long elapsed = System.nanoTime() - start;
        assertTrue(carved > 0);
        return elapsed;
    }

    private int carveNaiveExact(IrisCaveCarver3D carver, MantleWriter writer, int chunkX, int chunkZ, double[] columnWeights, IrisRange worldYRange, int[] precomputedSurfaceHeights) throws Exception {
        Engine engine = (Engine) engineField.get(carver);
        IrisData data = (IrisData) dataField.get(carver);
        IrisCaveProfile profile = (IrisCaveProfile) profileField.get(carver);
        CNG surfaceBreakDensity = (CNG) surfaceBreakDensityField.get(carver);
        RNG thresholdRng = (RNG) thresholdRngField.get(carver);
        MatterCavern carveAir = (MatterCavern) carveAirField.get(carver);
        MatterCavern carveLava = (MatterCavern) carveLavaField.get(carver);
        MatterCavern carveForcedAir = (MatterCavern) carveForcedAirField.get(carver);

        double[] resolvedWeights = columnWeights;
        if (resolvedWeights == null || resolvedWeights.length < 256) {
            resolvedWeights = fullWeights();
        }

        int worldHeight = writer.getMantle().getWorldHeight();
        int minY = Math.max(0, (int) Math.floor(profile.getVerticalRange().getMin()));
        int maxY = Math.min(worldHeight - 1, (int) Math.ceil(profile.getVerticalRange().getMax()));
        if (worldYRange != null) {
            int worldMinHeight = engine.getWorld().minHeight();
            int rangeMinY = (int) Math.floor(worldYRange.getMin() - worldMinHeight);
            int rangeMaxY = (int) Math.ceil(worldYRange.getMax() - worldMinHeight);
            minY = Math.max(minY, rangeMinY);
            maxY = Math.min(maxY, rangeMaxY);
        }
        if (maxY < minY) {
            return 0;
        }

        boolean allowSurfaceBreak = profile.isAllowSurfaceBreak();
        int surfaceClearance = Math.max(0, profile.getSurfaceClearance());
        int surfaceBreakDepth = Math.max(0, profile.getSurfaceBreakDepth());
        double surfaceBreakNoiseThreshold = profile.getSurfaceBreakNoiseThreshold();
        double surfaceBreakThresholdBoost = Math.max(0D, profile.getSurfaceBreakThresholdBoost());
        int[] columnTopY = new int[256];
        int[] surfaceBreakFloorY = new int[256];
        boolean[] surfaceBreakColumn = new boolean[256];
        double[] passThreshold = new double[256];
        double[] verticalEdgeFade = computeVerticalEdgeFade(profile, minY, maxY);
        MatterCavern[] matterByY = computeMatterByY(engine, profile, carveAir, carveLava, carveForcedAir, minY, maxY);

        int x0 = chunkX << 4;
        int z0 = chunkZ << 4;
        for (int localX = 0; localX < 16; localX++) {
            int x = x0 + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int z = z0 + localZ;
                int columnIndex = (localX << 4) | localZ;
                int columnSurfaceY;
                if (precomputedSurfaceHeights != null && precomputedSurfaceHeights.length > columnIndex) {
                    columnSurfaceY = precomputedSurfaceHeights[columnIndex];
                } else {
                    columnSurfaceY = engine.getHeight(x, z);
                }

                int clearanceTopY = Math.min(maxY, Math.max(minY, columnSurfaceY - surfaceClearance));
                boolean breakColumn = allowSurfaceBreak && signed(surfaceBreakDensity.noiseFast2D(x, z)) >= surfaceBreakNoiseThreshold;
                int resolvedTopY = breakColumn ? Math.min(maxY, Math.max(minY, columnSurfaceY)) : clearanceTopY;
                columnTopY[columnIndex] = resolvedTopY;
                surfaceBreakFloorY[columnIndex] = Math.max(minY, columnSurfaceY - surfaceBreakDepth);
                surfaceBreakColumn[columnIndex] = breakColumn;
                double columnWeight = clampColumnWeight(resolvedWeights[columnIndex]);
                if (columnWeight <= 0D || resolvedTopY < minY) {
                    passThreshold[columnIndex] = Double.NaN;
                    continue;
                }

                passThreshold[columnIndex] = profile.getDensityThreshold().get(thresholdRng, x, z, data) - profile.getThresholdBias();
            }
        }

        @SuppressWarnings("unchecked")
        MantleChunk<Matter> chunk = writer.acquireChunk(chunkX, chunkZ);
        if (chunk == null) {
            return 0;
        }

        int carved = 0;
        for (int localX = 0; localX < 16; localX++) {
            int x = x0 + localX;
            for (int localZ = 0; localZ < 16; localZ++) {
                int z = z0 + localZ;
                int columnIndex = (localX << 4) | localZ;
                if (Double.isNaN(passThreshold[columnIndex])) {
                    continue;
                }

                int topY = columnTopY[columnIndex];
                for (int y = minY; y <= topY; y++) {
                    double localThreshold = passThreshold[columnIndex];
                    if (surfaceBreakColumn[columnIndex] && y >= surfaceBreakFloorY[columnIndex]) {
                        localThreshold += surfaceBreakThresholdBoost;
                    }
                    localThreshold -= verticalEdgeFade[y - minY];

                    double density = (double) sampleDensityMethod.invoke(carver, x, y, z);
                    if (density > localThreshold) {
                        continue;
                    }

                    Matter sectionMatter = chunk.getOrCreate(y >> 4);
                    MatterSlice<MatterCavern> cavernSlice = sectionMatter.slice(MatterCavern.class);
                    cavernSlice.set(localX, y & 15, localZ, matterByY[y - minY]);
                    carved++;
                }
            }
        }

        return carved;
    }

    private double[] computeVerticalEdgeFade(IrisCaveProfile profile, int minY, int maxY) {
        int size = Math.max(0, maxY - minY + 1);
        double[] verticalEdgeFade = new double[size];
        int fadeRange = Math.max(0, profile.getVerticalEdgeFade());
        double fadeStrength = Math.max(0D, profile.getVerticalEdgeFadeStrength());
        if (size == 0 || fadeRange <= 0 || maxY <= minY || fadeStrength <= 0D) {
            return verticalEdgeFade;
        }

        for (int y = minY; y <= maxY; y++) {
            int floorDistance = y - minY;
            int ceilingDistance = maxY - y;
            int edgeDistance = Math.min(floorDistance, ceilingDistance);
            int offsetIndex = y - minY;
            if (edgeDistance >= fadeRange) {
                continue;
            }

            double t = Math.max(0D, Math.min(1D, edgeDistance / (double) fadeRange));
            double smooth = t * t * (3D - (2D * t));
            verticalEdgeFade[offsetIndex] = (1D - smooth) * fadeStrength;
        }

        return verticalEdgeFade;
    }

    private MatterCavern[] computeMatterByY(Engine engine, IrisCaveProfile profile, MatterCavern carveAir, MatterCavern carveLava, MatterCavern carveForcedAir, int minY, int maxY) {
        MatterCavern[] matterByY = new MatterCavern[Math.max(0, maxY - minY + 1)];
        boolean allowLava = profile.isAllowLava();
        boolean allowWater = profile.isAllowWater();
        int lavaHeight = engine.getDimension().getCaveLavaHeight();
        int fluidHeight = engine.getDimension().getFluidHeight();

        for (int y = minY; y <= maxY; y++) {
            int offset = y - minY;
            if (allowLava && y <= lavaHeight) {
                matterByY[offset] = carveLava;
                continue;
            }
            if (allowWater && y <= fluidHeight) {
                matterByY[offset] = carveAir;
                continue;
            }
            if (!allowLava && y <= lavaHeight) {
                matterByY[offset] = carveForcedAir;
                continue;
            }

            matterByY[offset] = carveAir;
        }

        return matterByY;
    }

    private Engine createEngine(int worldHeight, int sampledHeight) {
        Engine engine = mock(Engine.class);
        IrisData data = mock(IrisData.class);
        IrisDimension dimension = mock(IrisDimension.class);
        SeedManager seedManager = new SeedManager(942_337_445L);
        EngineMetrics metrics = new EngineMetrics(16);
        IrisWorld world = IrisWorld.builder().minHeight(0).maxHeight(worldHeight).build();

        doReturn(data).when(engine).getData();
        doReturn(dimension).when(engine).getDimension();
        doReturn(seedManager).when(engine).getSeedManager();
        doReturn(metrics).when(engine).getMetrics();
        doReturn(world).when(engine).getWorld();
        doReturn(sampledHeight).when(engine).getHeight(anyInt(), anyInt());

        doReturn(18).when(dimension).getCaveLavaHeight();
        doReturn(64).when(dimension).getFluidHeight();

        return engine;
    }

    private IrisCaveProfile createProfile(boolean warp, boolean modules) {
        IrisCaveProfile profile = new IrisCaveProfile();
        profile.setEnabled(true);
        profile.setVerticalRange(new IrisRange(0D, 120D));
        profile.setVerticalEdgeFade(14);
        profile.setVerticalEdgeFadeStrength(0.21D);
        profile.setBaseDensityStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.07D));
        profile.setDetailDensityStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.17D));
        profile.setWarpStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.12D));
        profile.setSurfaceBreakStyle(new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.09D));
        profile.setBaseWeight(1D);
        profile.setDetailWeight(0.48D);
        profile.setWarpStrength(warp ? 0.37D : 0D);
        profile.setDensityThreshold(new IrisStyledRange(1D, 1D, new IrisGeneratorStyle(NoiseStyle.FLAT)));
        profile.setThresholdBias(0D);
        profile.setSampleStep(1);
        profile.setMinCarveCells(0);
        profile.setRecoveryThresholdBoost(0D);
        profile.setSurfaceClearance(5);
        profile.setAllowSurfaceBreak(true);
        profile.setSurfaceBreakNoiseThreshold(0.16D);
        profile.setSurfaceBreakDepth(12);
        profile.setSurfaceBreakThresholdBoost(0.17D);
        profile.setAllowWater(true);
        profile.setWaterMinDepthBelowSurface(8);
        profile.setWaterRequiresFloor(false);
        profile.setAllowLava(true);
        if (modules) {
            KList<IrisCaveFieldModule> caveModules = new KList<>();
            caveModules.add(new IrisCaveFieldModule(
                    new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.11D),
                    0.23D,
                    0.04D,
                    new IrisRange(0D, 72D),
                    false
            ));
            caveModules.add(new IrisCaveFieldModule(
                    new IrisGeneratorStyle(NoiseStyle.SIMPLEX).zoomed(0.19D),
                    0.17D,
                    -0.06D,
                    new IrisRange(24D, 120D),
                    true
            ));
            profile.setModules(caveModules);
        } else {
            profile.setModules(new KList<>());
        }
        return profile;
    }

    private WriterCapture createWriterCapture(int worldHeight) {
        MantleWriter writer = mock(MantleWriter.class);
        @SuppressWarnings("unchecked")
        Mantle<Matter> mantle = mock(Mantle.class);
        @SuppressWarnings("unchecked")
        MantleChunk<Matter> chunk = mock(MantleChunk.class);
        Map<Integer, Matter> sections = new HashMap<>();
        Map<Integer, Map<Integer, MatterCavern>> sectionCells = new HashMap<>();
        Set<String> carvedCells = new HashSet<>();
        Map<String, Byte> carvedLiquids = new HashMap<>();

        doReturn(mantle).when(writer).getMantle();
        doReturn(worldHeight).when(mantle).getWorldHeight();
        doReturn(chunk).when(writer).acquireChunk(anyInt(), anyInt());
        doAnswer(invocation -> {
            int sectionIndex = invocation.getArgument(0);
            Matter section = sections.get(sectionIndex);
            if (section != null) {
                return section;
            }

            Matter created = createSection(sectionIndex, sectionCells, carvedCells, carvedLiquids);
            sections.put(sectionIndex, created);
            return created;
        }).when(chunk).getOrCreate(anyInt());

        return new WriterCapture(writer, carvedCells, carvedLiquids);
    }

    private Matter createSection(int sectionIndex, Map<Integer, Map<Integer, MatterCavern>> sectionCells, Set<String> carvedCells, Map<String, Byte> carvedLiquids) {
        Matter matter = mock(Matter.class);
        @SuppressWarnings("unchecked")
        MatterSlice<MatterCavern> slice = mock(MatterSlice.class);
        Map<Integer, MatterCavern> localCells = sectionCells.computeIfAbsent(sectionIndex, key -> new HashMap<>());

        doReturn(slice).when(matter).slice(MatterCavern.class);
        doAnswer(invocation -> {
            int localX = invocation.getArgument(0);
            int localY = invocation.getArgument(1);
            int localZ = invocation.getArgument(2);
            return localCells.get(packLocal(localX, localY, localZ));
        }).when(slice).get(anyInt(), anyInt(), anyInt());
        doAnswer(invocation -> {
            int localX = invocation.getArgument(0);
            int localY = invocation.getArgument(1);
            int localZ = invocation.getArgument(2);
            MatterCavern value = invocation.getArgument(3);
            localCells.put(packLocal(localX, localY, localZ), value);
            int worldY = (sectionIndex << 4) + localY;
            String cellKey = cellKey(localX, worldY, localZ);
            carvedCells.add(cellKey);
            carvedLiquids.put(cellKey, value.getLiquid());
            return null;
        }).when(slice).set(anyInt(), anyInt(), anyInt(), any(MatterCavern.class));

        return matter;
    }

    private double[] fullWeights() {
        double[] columnWeights = new double[256];
        Arrays.fill(columnWeights, 1D);
        return columnWeights;
    }

    private int[] filledHeights(int height) {
        int[] heights = new int[256];
        Arrays.fill(heights, height);
        return heights;
    }

    private double clampColumnWeight(double weight) {
        if (Double.isNaN(weight) || Double.isInfinite(weight)) {
            return 0D;
        }
        if (weight <= 0D) {
            return 0D;
        }
        if (weight >= 1D) {
            return 1D;
        }
        return weight;
    }

    private double signed(double value) {
        return (value * 2D) - 1D;
    }

    private int packLocal(int x, int y, int z) {
        return (x << 8) | (y << 4) | z;
    }

    private String cellKey(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    private boolean containsLiquidInRange(Map<String, Byte> carvedLiquids, int minY, int maxY, byte liquid) {
        for (Map.Entry<String, Byte> entry : carvedLiquids.entrySet()) {
            if (entry.getValue() != liquid) {
                continue;
            }

            String[] split = entry.getKey().split(":");
            int y = Integer.parseInt(split[1]);
            if (y >= minY && y <= maxY) {
                return true;
            }
        }
        return false;
    }

    private boolean hasX(Set<String> carvedCells, int x) {
        for (String cell : carvedCells) {
            String[] split = cell.split(":");
            if (Integer.parseInt(split[0]) == x) {
                return true;
            }
        }

        return false;
    }

    private boolean hasZ(Set<String> carvedCells, int z) {
        for (String cell : carvedCells) {
            String[] split = cell.split(":");
            if (Integer.parseInt(split[2]) == z) {
                return true;
            }
        }

        return false;
    }

    private int maxY(Set<String> carvedCells) {
        int max = Integer.MIN_VALUE;
        for (String cell : carvedCells) {
            String[] split = cell.split(":");
            int y = Integer.parseInt(split[1]);
            if (y > max) {
                max = y;
            }
        }
        return max;
    }

    private int minY(Set<String> carvedCells) {
        int min = Integer.MAX_VALUE;
        for (String cell : carvedCells) {
            String[] split = cell.split(":");
            int y = Integer.parseInt(split[1]);
            if (y < min) {
                min = y;
            }
        }
        return min;
    }

    private static final class WriterCapture {
        private final MantleWriter writer;
        private final Set<String> carvedCells;
        private final Map<String, Byte> carvedLiquids;

        private WriterCapture(MantleWriter writer, Set<String> carvedCells, Map<String, Byte> carvedLiquids) {
            this.writer = writer;
            this.carvedCells = carvedCells;
            this.carvedLiquids = carvedLiquids;
        }
    }
}
