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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public final class IrisWireWriter {
    private byte[] buffer;
    private int length;

    public IrisWireWriter() {
        this.buffer = new byte[64];
        this.length = 0;
    }

    public void writeVarInt(int value) {
        int remaining = value;
        while (true) {
            int chunk = remaining & 0x7F;
            remaining >>>= 7;
            if (remaining != 0) {
                writeByte(chunk | 0x80);
            } else {
                writeByte(chunk);
                return;
            }
        }
    }

    public void writeInt(int value) {
        ensure(4);
        buffer[length++] = (byte) (value >>> 24);
        buffer[length++] = (byte) (value >>> 16);
        buffer[length++] = (byte) (value >>> 8);
        buffer[length++] = (byte) value;
    }

    public void writeLong(long value) {
        ensure(8);
        buffer[length++] = (byte) (value >>> 56);
        buffer[length++] = (byte) (value >>> 48);
        buffer[length++] = (byte) (value >>> 40);
        buffer[length++] = (byte) (value >>> 32);
        buffer[length++] = (byte) (value >>> 24);
        buffer[length++] = (byte) (value >>> 16);
        buffer[length++] = (byte) (value >>> 8);
        buffer[length++] = (byte) value;
    }

    public void writeDouble(double value) {
        writeLong(Double.doubleToLongBits(value));
    }

    public void writeBoolean(boolean value) {
        writeByte(value ? 1 : 0);
    }

    public void writeString(String value) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(encoded.length);
        ensure(encoded.length);
        System.arraycopy(encoded, 0, buffer, length, encoded.length);
        length += encoded.length;
    }

    public void writeBytes(byte[] value) {
        writeVarInt(value.length);
        ensure(value.length);
        System.arraycopy(value, 0, buffer, length, value.length);
        length += value.length;
    }

    public byte[] toByteArray() {
        return Arrays.copyOf(buffer, length);
    }

    private void writeByte(int value) {
        ensure(1);
        buffer[length++] = (byte) value;
    }

    private void ensure(int extra) {
        if ((long) length + extra > IrisProtocol.MAX_FRAME_BYTES) {
            throw new IllegalStateException("Iris protocol frame exceeds " + IrisProtocol.MAX_FRAME_BYTES + " byte cap");
        }
        int required = length + extra;
        if (required <= buffer.length) {
            return;
        }
        int grown = buffer.length;
        while (grown < required) {
            grown <<= 1;
        }
        if (grown > IrisProtocol.MAX_FRAME_BYTES) {
            grown = IrisProtocol.MAX_FRAME_BYTES;
        }
        buffer = Arrays.copyOf(buffer, grown);
    }
}
