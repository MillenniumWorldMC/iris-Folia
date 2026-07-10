/*
 * Iris is a World Generator for Minecraft Servers
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

package art.arcane.iris.modded;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

final class InitialSpawnQueue {
    private static final long DEFAULT_MAX_AGE_NANOS = TimeUnit.MINUTES.toNanos(5);

    private final int capacity;
    private final long maxAgeNanos;
    private final LongSupplier nanoTime;
    private final ArrayDeque<Long> queue;
    private final Map<Long, Long> pending;
    private final Set<Long> queued;
    private boolean closed;

    InitialSpawnQueue(int capacity) {
        this(capacity, DEFAULT_MAX_AGE_NANOS, System::nanoTime);
    }

    InitialSpawnQueue(int capacity, long maxAgeNanos, LongSupplier nanoTime) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (maxAgeNanos < 1L) {
            throw new IllegalArgumentException("maxAgeNanos must be positive");
        }
        this.capacity = capacity;
        this.maxAgeNanos = maxAgeNanos;
        this.nanoTime = nanoTime;
        this.queue = new ArrayDeque<>(Math.min(capacity, 256));
        this.pending = new HashMap<>();
        this.queued = new HashSet<>();
    }

    synchronized boolean offer(long key) {
        if (closed || pending.containsKey(key)) {
            return false;
        }
        if (pending.size() >= capacity) {
            expire(nanoTime.getAsLong());
        }
        if (pending.size() >= capacity) {
            return false;
        }
        pending.put(key, nanoTime.getAsLong());
        queued.add(key);
        queue.addLast(key);
        return true;
    }

    synchronized int batchSize(int limit) {
        if (closed || limit <= 0) {
            return 0;
        }
        return Math.min(limit, queue.size());
    }

    synchronized Long poll() {
        long now = nanoTime.getAsLong();
        Long key;
        while ((key = queue.pollFirst()) != null) {
            queued.remove(key);
            Long offeredAt = pending.get(key);
            if (offeredAt == null) {
                continue;
            }
            if (expired(offeredAt, now)) {
                pending.remove(key);
                continue;
            }
            return key;
        }
        return null;
    }

    synchronized void retry(long key) {
        Long offeredAt = pending.get(key);
        if (closed || offeredAt == null || expired(offeredAt, nanoTime.getAsLong())) {
            pending.remove(key);
            queued.remove(key);
            return;
        }
        if (!queued.add(key)) {
            return;
        }
        queue.addLast(key);
    }

    synchronized void complete(long key) {
        pending.remove(key);
        if (queued.remove(key)) {
            queue.removeFirstOccurrence(key);
        }
    }

    synchronized boolean isEmpty() {
        return queue.isEmpty();
    }

    synchronized int size() {
        return pending.size();
    }

    synchronized void clear() {
        queue.clear();
        pending.clear();
        queued.clear();
    }

    synchronized void close() {
        closed = true;
        clear();
    }

    private void expire(long now) {
        Set<Long> expired = new HashSet<>();
        for (Map.Entry<Long, Long> entry : pending.entrySet()) {
            if (expired(entry.getValue(), now)) {
                expired.add(entry.getKey());
            }
        }
        if (expired.isEmpty()) {
            return;
        }
        for (Long key : expired) {
            pending.remove(key);
            queued.remove(key);
        }
        queue.removeIf(expired::contains);
    }

    private boolean expired(long offeredAt, long now) {
        return now - offeredAt >= maxAgeNanos;
    }
}
