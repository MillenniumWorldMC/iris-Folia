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

public final class IrisProtocol {
    public static final int PROTOCOL_VERSION = 1;
    public static final String CHANNEL = "irisworldgen:main";
    public static final int MAX_FRAME_BYTES = 24576;
    public static final int MAX_INBOUND_FRAMES_PER_SECOND = 32;
    public static final int MAX_VISION_TILE_REQUESTS_PER_SECOND = 8;
    public static final int VISION_TILE_HEADER_BYTES = 25;
    public static final int VISION_TILE_MAX_CHUNK_BYTES = 24000;
    public static final int MAX_VISION_MARKERS = 256;
    public static final long CAPABILITY_PREGEN = 1L << 0;
    public static final long CAPABILITY_VISION = 1L << 1;
    public static final long CAPABILITY_CURSOR = 1L << 2;
    public static final long CAPABILITY_STUDIO = 1L << 3;
    public static final int TYPE_CLIENT_HELLO = 1;
    public static final int TYPE_SERVER_HELLO = 2;
    public static final int TYPE_PREGEN_PROGRESS = 3;
    public static final int TYPE_PREGEN_END = 4;
    public static final int TYPE_DIMENSION_STATUS = 5;
    public static final int TYPE_CURSOR_INFO_REQUEST = 6;
    public static final int TYPE_CURSOR_INFO = 7;
    public static final int TYPE_VISION_TILE_REQUEST = 8;
    public static final int TYPE_VISION_TILE = 9;
    public static final int TYPE_VISION_MARKERS = 10;
    public static final int TYPE_PREGEN_REGION_DELTA = 11;
    public static final int TYPE_STUDIO_HOTLOAD = 12;
    public static final int TYPE_TOAST = 13;

    private IrisProtocol() {
    }
}
