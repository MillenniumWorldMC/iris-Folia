package art.arcane.iris.engine.mantle;

import art.arcane.iris.core.nms.container.Pair;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.util.common.parallel.MultiBurst;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.volmlib.util.documentation.ChunkCoordinates;
import art.arcane.volmlib.util.mantle.flag.MantleFlag;
import art.arcane.volmlib.util.mantle.runtime.Mantle;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.matter.Matter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public interface MatterGenerator {
    MultiBurst DISPATCHER = MultiBurst.burst;
    ConcurrentHashMap<MatterTaskKey, CompletableFuture<Void>> IN_FLIGHT_COMPONENTS = new ConcurrentHashMap<>();

    Engine getEngine();

    Mantle<Matter> getMantle();

    int getRadius();

    int getRealRadius();

    List<Pair<List<MantleComponent>, Integer>> getComponents();

    @ChunkCoordinates
    default void generateMatter(int x, int z, boolean multicore, ChunkContext context) {
        if (!getEngine().getDimension().isUseMantle()) {
            return;
        }

        int writeRadius = getRadius();
        Set<Long> partialChunks = new HashSet<>();

        try (MantleWriter writer = new MantleWriter(getEngine().getMantle(), getMantle(), x, z, writeRadius, multicore)) {
            for (Pair<List<MantleComponent>, Integer> pair : getComponents()) {
                int passRadius = pair.getB();
                Set<CompletableFuture<Void>> launchedTasks = multicore ? new HashSet<>() : null;

                for (int i = -passRadius; i <= passRadius; i++) {
                    for (int j = -passRadius; j <= passRadius; j++) {
                        int passX = x + i;
                        int passZ = z + j;
                        long passKey = chunkKey(passX, passZ);

                        MantleChunk<Matter> chunk = writer.acquireChunk(passX, passZ);
                        if (chunk.isFlagged(MantleFlag.PLANNED)) {
                            continue;
                        }

                        List<MantleComponent> eligibleComponents = new ArrayList<>(pair.getA().size());
                        for (MantleComponent component : pair.getA()) {
                            if (!component.isEnabled()) {
                                continue;
                            }

                            if (chunk.isFlagged(component.getFlag())) {
                                continue;
                            }

                            int componentRadius = component.getRadius();
                            if (componentRadius > 0) {
                                int componentPassRadius = Math.ceilDiv(componentRadius, 16);
                                if (Math.abs(i) > componentPassRadius || Math.abs(j) > componentPassRadius) {
                                    partialChunks.add(passKey);
                                    continue;
                                }
                            }

                            MantleFlag[] prerequisites = component.getPrerequisiteFlags();
                            if (prerequisites.length > 0) {
                                boolean prerequisitesMet = true;
                                for (MantleFlag prereq : prerequisites) {
                                    if (!chunk.isFlagged(prereq)) {
                                        prerequisitesMet = false;
                                        break;
                                    }
                                }
                                if (!prerequisitesMet) {
                                    partialChunks.add(passKey);
                                    continue;
                                }
                            }

                            eligibleComponents.add(component);
                        }

                        if (eligibleComponents.isEmpty()) {
                            continue;
                        }

                        int finalPassX = passX;
                        int finalPassZ = passZ;
                        Runnable task = () -> {
                            for (MantleComponent component : eligibleComponents) {
                                runComponentInline(chunk, component, writer, finalPassX, finalPassZ, context);
                            }
                        };

                        if (multicore) {
                            for (MantleComponent component : eligibleComponents) {
                                launchedTasks.add(runComponentAsync(chunk, component, writer, finalPassX, finalPassZ, context));
                            }
                        } else {
                            task.run();
                        }
                    }
                }

                if (multicore) {
                    for (CompletableFuture<Void> launchedTask : launchedTasks) {
                        launchedTask.join();
                    }
                }
            }

            for (int i = -getRealRadius(); i <= getRealRadius(); i++) {
                for (int j = -getRealRadius(); j <= getRealRadius(); j++) {
                    int realX = x + i;
                    int realZ = z + j;
                    long realKey = chunkKey(realX, realZ);
                    if (partialChunks.contains(realKey)) {
                        continue;
                    }
                    writer.acquireChunk(realX, realZ).flag(MantleFlag.PLANNED, true);
                }
            }
        }
    }

    private static long chunkKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private CompletableFuture<Void> runComponentAsync(
            MantleChunk<Matter> chunk,
            MantleComponent component,
            MantleWriter writer,
            int chunkX,
            int chunkZ,
            ChunkContext context
    ) {
        MantleFlag flag = component.getFlag();
        if (chunk.isFlagged(flag)) {
            return CompletableFuture.completedFuture(null);
        }

        MatterTaskKey key = new MatterTaskKey(getMantle(), chunkX, chunkZ, flag.ordinal());
        CompletableFuture<Void> future = new CompletableFuture<>();
        CompletableFuture<Void> existing = IN_FLIGHT_COMPONENTS.putIfAbsent(key, future);
        if (existing != null) {
            return existing;
        }

        try {
            if (DISPATCHER.ownsCurrentThread()) {
                completeComponentTask(future, key, chunk, component, writer, chunkX, chunkZ, context);
            } else {
                CompletableFuture.runAsync(() -> completeComponentTask(future, key, chunk, component, writer, chunkX, chunkZ, context), DISPATCHER);
            }
        } catch (Throwable throwable) {
            IN_FLIGHT_COMPONENTS.remove(key, future);
            future.completeExceptionally(throwable);
            throw throwable;
        }

        return future;
    }

    private void completeComponentTask(
            CompletableFuture<Void> future,
            MatterTaskKey key,
            MantleChunk<Matter> chunk,
            MantleComponent component,
            MantleWriter writer,
            int chunkX,
            int chunkZ,
            ChunkContext context
    ) {
        try {
            runComponentInline(chunk, component, writer, chunkX, chunkZ, context);
            future.complete(null);
        } catch (Throwable throwable) {
            future.completeExceptionally(throwable);
            throw throwable;
        } finally {
            IN_FLIGHT_COMPONENTS.remove(key, future);
        }
    }

    private void runComponentInline(
            MantleChunk<Matter> chunk,
            MantleComponent component,
            MantleWriter writer,
            int chunkX,
            int chunkZ,
            ChunkContext context
    ) {
        chunk.raiseFlagSuspend(component.getFlag(), () -> component.generateLayer(writer, chunkX, chunkZ, context));
    }

    final class MatterTaskKey {
        private final Mantle<Matter> mantle;
        private final int chunkX;
        private final int chunkZ;
        private final int flagOrdinal;

        MatterTaskKey(Mantle<Matter> mantle, int chunkX, int chunkZ, int flagOrdinal) {
            this.mantle = mantle;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.flagOrdinal = flagOrdinal;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }

            if (!(object instanceof MatterTaskKey other)) {
                return false;
            }

            return mantle == other.mantle
                    && chunkX == other.chunkX
                    && chunkZ == other.chunkZ
                    && flagOrdinal == other.flagOrdinal;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(mantle);
            result = 31 * result + chunkX;
            result = 31 * result + chunkZ;
            result = 31 * result + flagOrdinal;
            return result;
        }
    }
}
