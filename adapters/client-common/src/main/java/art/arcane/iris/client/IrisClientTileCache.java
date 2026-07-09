package art.arcane.iris.client;

import art.arcane.iris.spi.protocol.IrisMessage;
import art.arcane.iris.spi.protocol.IrisMessageCodec;
import art.arcane.iris.spi.protocol.IrisProtocol;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;

public final class IrisClientTileCache {
    private static final int MAX_CACHED_TILES = 256;
    private static final long REQUEST_RETRY_MILLIS = 3000L;
    private static final int REQUESTS_PER_SECOND = IrisProtocol.MAX_VISION_TILE_REQUESTS_PER_SECOND;

    private final ClientPacketSink sink;
    private final LongSupplier clock;
    private final IrisTileAssembler assembler;
    private final LinkedHashMap<IrisTileKey, IrisTileImage> cache;
    private final Map<IrisTileKey, Long> pending;
    private final Deque<IrisTileKey> queue;
    private final Set<IrisTileKey> queued;
    private long windowStartMillis;
    private int sentInWindow;

    public IrisClientTileCache(ClientPacketSink sink, LongSupplier clock) {
        this.sink = sink;
        this.clock = clock;
        this.assembler = new IrisTileAssembler();
        this.cache = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<IrisTileKey, IrisTileImage> eldest) {
                return size() > MAX_CACHED_TILES;
            }
        };
        this.pending = new HashMap<>();
        this.queue = new ArrayDeque<>();
        this.queued = new HashSet<>();
        this.windowStartMillis = 0L;
        this.sentInWindow = 0;
    }

    public synchronized void onVisionTile(IrisMessage.VisionTile tile) {
        IrisTileImage image = assembler.add(tile);
        if (image == null) {
            return;
        }
        IrisTileKey key = new IrisTileKey(tile.tileX(), tile.tileZ(), tile.zoomLevel());
        cache.put(key, image);
        pending.remove(key);
        queued.remove(key);
    }

    public synchronized IrisTileImage get(IrisTileKey key) {
        return cache.get(key);
    }

    public synchronized void resetRequestQueue() {
        queue.clear();
        queued.clear();
    }

    public synchronized void request(IrisTileKey key) {
        if (cache.containsKey(key)) {
            return;
        }
        long now = clock.getAsLong();
        Long lastRequest = pending.get(key);
        if (lastRequest != null && now - lastRequest < REQUEST_RETRY_MILLIS) {
            return;
        }
        if (queued.add(key)) {
            queue.addLast(key);
        }
    }

    public synchronized void pump() {
        long now = clock.getAsLong();
        if (now - windowStartMillis >= 1000L) {
            windowStartMillis = now;
            sentInWindow = 0;
        }
        while (sentInWindow < REQUESTS_PER_SECOND && !queue.isEmpty()) {
            IrisTileKey key = queue.pollFirst();
            queued.remove(key);
            if (cache.containsKey(key)) {
                continue;
            }
            Long lastRequest = pending.get(key);
            if (lastRequest != null && now - lastRequest < REQUEST_RETRY_MILLIS) {
                continue;
            }
            sink.send(IrisMessageCodec.encode(new IrisMessage.VisionTileRequest(key.tileX(), key.tileZ(), key.zoom())));
            pending.put(key, now);
            sentInWindow++;
        }
    }

    public synchronized void clear() {
        assembler.clear();
        cache.clear();
        pending.clear();
        queue.clear();
        queued.clear();
        sentInWindow = 0;
        windowStartMillis = 0L;
    }
}
