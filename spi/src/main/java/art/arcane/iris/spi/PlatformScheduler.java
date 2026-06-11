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

package art.arcane.iris.spi;

/**
 * Platform task dispatch; region scheduling targets the owning region thread on regionized platforms and the global thread elsewhere.
 */
public interface PlatformScheduler {
    void global(Runnable task);

    void region(PlatformWorld world, int chunkX, int chunkZ, Runnable task);

    void async(Runnable task);

    void laterGlobal(Runnable task, int ticks);

    void laterRegion(PlatformWorld world, int chunkX, int chunkZ, Runnable task, int ticks);
}
