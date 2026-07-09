/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2026 Arcane Arts (Volmit Software)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package art.arcane.iris.core.protocol;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.PreservationRegistry;
import art.arcane.iris.spi.IrisServices;
import art.arcane.iris.spi.protocol.IrisMessage;
import art.arcane.iris.spi.protocol.IrisProtocol;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class IrisVisionRequestService implements VisionTileRequestHandler {
    private static final int DEFAULT_MAX_PENDING = 64;

    private final EngineResolver engineResolver;
    private final IrisSessionRegistry registry;
    private final Executor executor;
    private final int maxPending;
    private final ArrayDeque<PendingRequest> pending;
    private final ConcurrentHashMap<String, Integer> sequences;
    private final AtomicLong droppedSaturated;
    private final AtomicLong droppedNoEngine;
    private final AtomicLong droppedNoSession;
    private final AtomicLong tilesEncoded;

    IrisVisionRequestService(EngineResolver engineResolver, IrisSessionRegistry registry, Executor executor, int maxPending) {
        this.engineResolver = Objects.requireNonNull(engineResolver, "engine resolver");
        this.registry = Objects.requireNonNull(registry, "session registry");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.maxPending = Math.max(1, maxPending);
        this.pending = new ArrayDeque<>();
        this.sequences = new ConcurrentHashMap<>();
        this.droppedSaturated = new AtomicLong(0L);
        this.droppedNoEngine = new AtomicLong(0L);
        this.droppedNoSession = new AtomicLong(0L);
        this.tilesEncoded = new AtomicLong(0L);
    }

    public static IrisVisionRequestService create(EngineResolver engineResolver, IrisSessionRegistry registry) {
        AtomicInteger threadIndex = new AtomicInteger(0);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 2, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("Iris Vision Tile " + threadIndex.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
        executor.allowCoreThreadTimeOut(true);
        PreservationRegistry preservation = IrisServices.getOrNull(PreservationRegistry.class);
        if (preservation != null) {
            preservation.register(executor);
        }
        return new IrisVisionRequestService(engineResolver, registry, executor, DEFAULT_MAX_PENDING);
    }

    @Override
    public void handle(String sessionId, int tileX, int tileZ, int zoomLevel) {
        PendingRequest request = new PendingRequest(sessionId, tileX, tileZ, zoomLevel);
        synchronized (pending) {
            while (pending.size() >= maxPending) {
                pending.pollFirst();
                droppedSaturated.incrementAndGet();
            }
            pending.addLast(request);
        }
        executor.execute(this::drainOne);
    }

    public long droppedSaturatedCount() {
        return droppedSaturated.get();
    }

    public long droppedNoEngineCount() {
        return droppedNoEngine.get();
    }

    public long droppedNoSessionCount() {
        return droppedNoSession.get();
    }

    public long tilesEncodedCount() {
        return tilesEncoded.get();
    }

    public int pendingSize() {
        synchronized (pending) {
            return pending.size();
        }
    }

    private void drainOne() {
        PendingRequest request;
        synchronized (pending) {
            request = pending.pollFirst();
        }
        if (request == null) {
            return;
        }
        process(request);
    }

    private void process(PendingRequest request) {
        IrisSession session = registry.get(request.sessionId());
        if (session == null || !session.isReady() || !session.hasCapability(IrisProtocol.CAPABILITY_VISION)) {
            droppedNoSession.incrementAndGet();
            return;
        }
        Engine engine = engineResolver.resolve(request.sessionId());
        if (engine == null) {
            droppedNoEngine.incrementAndGet();
            return;
        }
        int sequence = nextSequence(request);
        List<IrisMessage.VisionTile> chunks = IrisTileEncoder.encode(engine, request.tileX(), request.tileZ(), request.zoomLevel(), sequence);
        for (IrisMessage.VisionTile chunk : chunks) {
            session.send(chunk);
        }
        tilesEncoded.incrementAndGet();
    }

    private int nextSequence(PendingRequest request) {
        String key = request.sessionId() + ":" + request.tileX() + ":" + request.tileZ() + ":" + request.zoomLevel();
        return sequences.merge(key, 1, Integer::sum);
    }

    private record PendingRequest(String sessionId, int tileX, int tileZ, int zoomLevel) {
    }
}
