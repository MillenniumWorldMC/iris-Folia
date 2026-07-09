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

package art.arcane.iris.spi.protocol;

import java.util.List;

public sealed interface IrisMessage {
    int messageTypeId();

    record ClientHello(int protocolVersion, long capabilities) implements IrisMessage {
        @Override
        public int messageTypeId() {
            return IrisProtocol.TYPE_CLIENT_HELLO;
        }
    }

    record ServerHello(int protocolVersion, long capabilities, String serverBrand, boolean irisActive) implements IrisMessage {
        @Override
        public int messageTypeId() {
            return IrisProtocol.TYPE_SERVER_HELLO;
        }
    }

    record PregenProgress(long jobId, long chunksDone, long chunksTotal, double chunksPerSecond, long etaMillis, int state) implements IrisMessage {
        public static final int STATE_RUNNING = 0;
        public static final int STATE_PAUSED = 1;

        @Override
        public int messageTypeId() {
            return IrisProtocol.TYPE_PREGEN_PROGRESS;
        }
    }

    record PregenEnd(long jobId, boolean completed) implements IrisMessage {
        @Override
        public int messageTypeId() {
            return IrisProtocol.TYPE_PREGEN_END;
        }
    }

    record DimensionStatus(String dimensionKey, String packKey, long seed, int minY, int maxY, boolean irisWorld) implements IrisMessage {
        @Override
        public int messageTypeId() {
            return IrisProtocol.TYPE_DIMENSION_STATUS;
        }
    }

    record CursorInfoRequest(int blockX, int blockZ) implements IrisMessage {
        @Override
        public int messageTypeId() {
            return IrisProtocol.TYPE_CURSOR_INFO_REQUEST;
        }
    }

    record CursorInfo(int blockX, int blockZ, String biomeKey, String regionKey, String caveBiomeKey, int height, String dimensionKey) implements IrisMessage {
        @Override
        public int messageTypeId() {
            return IrisProtocol.TYPE_CURSOR_INFO;
        }
    }

    record VisionTileRequest(int tileX, int tileZ, int zoomLevel) implements IrisMessage {
        @Override
        public int messageTypeId() {
            return IrisProtocol.TYPE_VISION_TILE_REQUEST;
        }
    }

    record VisionTile(int tileX, int tileZ, int zoomLevel, int sequence, int chunkIndex, int chunkCount, byte[] data) implements IrisMessage {
        @Override
        public int messageTypeId() {
            return IrisProtocol.TYPE_VISION_TILE;
        }
    }

    record VisionMarkers(int tileX, int tileZ, int zoomLevel, List<Marker> markers) implements IrisMessage {
        public record Marker(int blockX, int blockZ, int kind, String label) {
        }

        @Override
        public int messageTypeId() {
            return IrisProtocol.TYPE_VISION_MARKERS;
        }
    }

    record PregenRegionDelta(long jobId, int regionX, int regionZ, int state) implements IrisMessage {
        public static final int STATE_PENDING = 0;
        public static final int STATE_GENERATING = 1;
        public static final int STATE_DONE = 2;

        @Override
        public int messageTypeId() {
            return IrisProtocol.TYPE_PREGEN_REGION_DELTA;
        }
    }

    record StudioHotload(String packKey, int changedFiles, boolean failed, String message) implements IrisMessage {
        @Override
        public int messageTypeId() {
            return IrisProtocol.TYPE_STUDIO_HOTLOAD;
        }
    }

    record Toast(int kind, String title, String body) implements IrisMessage {
        public static final int KIND_INFO = 0;
        public static final int KIND_SUCCESS = 1;
        public static final int KIND_WARNING = 2;
        public static final int KIND_ERROR = 3;

        @Override
        public int messageTypeId() {
            return IrisProtocol.TYPE_TOAST;
        }
    }
}
