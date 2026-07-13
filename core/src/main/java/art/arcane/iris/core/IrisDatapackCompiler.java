package art.arcane.iris.core;

import art.arcane.iris.core.loader.IrisData;
import art.arcane.iris.core.loader.ResourceLoader;
import art.arcane.iris.core.nms.datapack.IDataFixer;
import art.arcane.iris.engine.object.IrisDimension;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KSet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.stream.Stream;

public final class IrisDatapackCompiler {
    private static final int WORLD_PACK_SCAN_DEPTH = 8;

    private IrisDatapackCompiler() {
    }

    public static List<File> collectPackRoots(Path dataDirectory, Path serverRoot) throws IOException {
        LinkedHashMap<Path, File> roots = new LinkedHashMap<>();
        collectInstalledPackRoots(dataDirectory.resolve("packs"), roots);
        collectWorldPackRoots(serverRoot.resolve("dimensions"), roots);
        return new ArrayList<>(roots.values());
    }

    public static CompilationResult compile(
            List<File> packRoots,
            KList<File> datapackRoots,
            IDataFixer fixer,
            int packFormat,
            boolean adjustVanillaHeight
    ) throws IOException {
        Objects.requireNonNull(packRoots, "packRoots");
        Objects.requireNonNull(datapackRoots, "datapackRoots");
        Objects.requireNonNull(fixer, "fixer");
        if (datapackRoots.isEmpty()) {
            throw new IOException("No Iris datapack output roots were provided");
        }

        for (File datapackRoot : datapackRoots) {
            Files.createDirectories(datapackRoot.toPath());
        }
        IrisDimension.clearGeneratedBiomeTags(datapackRoots);

        DimensionHeight height = new DimensionHeight(fixer);
        Map<String, KSet<String>> biomes = new LinkedHashMap<>();
        int packCount = 0;
        int dimensionCount = 0;
        for (File packRoot : packRoots) {
            if (!hasDimensions(packRoot.toPath())) {
                continue;
            }
            IrisData data = IrisData.openDatapackCompiler(packRoot);
            try {
                ResourceLoader<IrisDimension> loader = data.getDimensionLoader();
                String[] possibleKeys = loader.getPossibleKeys();
                if (possibleKeys == null || possibleKeys.length == 0) {
                    throw new IOException("Iris pack has no dimension definitions: " + packRoot);
                }

                int installedDimensions = 0;
                for (String possibleKey : possibleKeys) {
                    IrisDimension dimension = loader.load(possibleKey);
                    if (dimension == null) {
                        throw new IOException("Unable to load Iris dimension '" + possibleKey + "' from " + packRoot);
                    }
                    IrisLogging.debug("  Compiling Dimension " + dimension.getLoadFile().getPath());
                    height.merge(dimension);
                    KSet<String> seenBiomes = biomes.computeIfAbsent(dimension.getLoadKey(), ignored -> new KSet<>());
                    dimension.installBiomes(fixer, dimension::getLoader, datapackRoots, seenBiomes);
                    dimension.installDimensionType(fixer, datapackRoots);
                    installedDimensions++;
                    dimensionCount++;
                }
                if (installedDimensions > 0) {
                    packCount++;
                }
            } finally {
                data.close();
            }
        }

        IrisDimension.writeShared(datapackRoots, height, packFormat, adjustVanillaHeight);
        validateOutputs(datapackRoots);
        return new CompilationResult(packCount, dimensionCount, countBiomes(biomes));
    }

    private static void collectInstalledPackRoots(Path packsRoot, Map<Path, File> roots) throws IOException {
        if (!Files.isDirectory(packsRoot)) {
            return;
        }
        try (Stream<Path> stream = Files.list(packsRoot)) {
            List<Path> candidates = stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            for (Path candidate : candidates) {
                addPackRoot(candidate, roots);
            }
        }
    }

    private static void collectWorldPackRoots(Path dimensionsRoot, Map<Path, File> roots) throws IOException {
        if (!Files.isDirectory(dimensionsRoot)) {
            return;
        }
        try (Stream<Path> stream = Files.find(
                dimensionsRoot,
                WORLD_PACK_SCAN_DEPTH,
                (path, attributes) -> attributes.isDirectory()
                        && "pack".equals(path.getFileName().toString())
                        && path.getParent() != null
                        && "iris".equals(path.getParent().getFileName().toString())
                        && hasDimensions(path)
        )) {
            List<Path> candidates = stream.sorted(Comparator.comparing(Path::toString)).toList();
            for (Path candidate : candidates) {
                addPackRoot(candidate, roots);
            }
        }
    }

    private static void addPackRoot(Path root, Map<Path, File> roots) throws IOException {
        if (!hasDimensions(root)) {
            return;
        }
        Path normalized = root.toAbsolutePath().normalize();
        Path identity = normalized.toRealPath();
        roots.putIfAbsent(identity, normalized.toFile());
    }

    private static boolean hasDimensions(Path root) {
        Path dimensions = root.resolve("dimensions");
        if (!Files.isDirectory(dimensions)) {
            return false;
        }
        try (Stream<Path> stream = Files.list(dimensions)) {
            return stream.anyMatch(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json"));
        } catch (IOException e) {
            return false;
        }
    }

    private static void validateOutputs(Collection<File> datapackRoots) throws IOException {
        for (File datapackRoot : datapackRoots) {
            Path root = datapackRoot.toPath();
            if (!Files.isRegularFile(root.resolve("pack.mcmeta"))) {
                throw new IOException("Iris datapack metadata was not generated at " + root);
            }
            if (!Files.isDirectory(root.resolve("data/iris/dimension_type"))) {
                throw new IOException("Iris dimension types were not generated at " + root);
            }
        }
    }

    private static int countBiomes(Map<String, KSet<String>> biomes) {
        int count = 0;
        for (KSet<String> values : biomes.values()) {
            count += values.size();
        }
        return count;
    }

    public record CompilationResult(int packCount, int dimensionCount, int biomeCount) {
    }

    public static final class DimensionHeight {
        private final IDataFixer fixer;
        private final AtomicIntegerArray[] dimensions = new AtomicIntegerArray[3];

        public DimensionHeight(IDataFixer fixer) {
            this.fixer = Objects.requireNonNull(fixer, "fixer");
            for (int index = 0; index < dimensions.length; index++) {
                dimensions[index] = new AtomicIntegerArray(new int[]{
                        Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE
                });
            }
        }

        public void merge(IrisDimension dimension) {
            AtomicIntegerArray values = dimensions[dimension.getBaseDimension().ordinal()];
            values.updateAndGet(0, current -> Math.min(current, dimension.getMinHeight()));
            values.updateAndGet(1, current -> Math.max(current, dimension.getMaxHeight()));
            values.updateAndGet(2, current -> Math.max(current, dimension.getLogicalHeight()));
        }

        public String[] jsonStrings() {
            IDataFixer.Dimension[] types = IDataFixer.Dimension.values();
            String[] output = new String[types.length];
            for (int index = 0; index < types.length; index++) {
                output[index] = jsonString(types[index]);
            }
            return output;
        }

        private String jsonString(IDataFixer.Dimension dimension) {
            AtomicIntegerArray values = dimensions[dimension.ordinal()];
            int minY = values.get(0);
            int maxY = values.get(1);
            int logicalHeight = values.get(2);
            if (minY == Integer.MAX_VALUE || maxY == Integer.MIN_VALUE || logicalHeight == Integer.MIN_VALUE) {
                return null;
            }
            return fixer.createDimension(dimension, minY, maxY - minY, logicalHeight, null).toString(4);
        }
    }
}
