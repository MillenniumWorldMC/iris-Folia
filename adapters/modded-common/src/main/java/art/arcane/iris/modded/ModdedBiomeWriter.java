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

import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBiomeWriter;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ModdedBiomeWriter implements PlatformBiomeWriter {
    private final Supplier<MinecraftServer> server;

    public ModdedBiomeWriter(Supplier<MinecraftServer> server) {
        this.server = server;
    }

    @Override
    public int biomeIdFor(String key) {
        Registry<Biome> registry = biomeRegistry();
        Identifier identifier = Identifier.tryParse(key);
        if (registry == null || identifier == null) {
            return 0;
        }
        Biome biome = registry.getValue(identifier);
        return biome == null ? 0 : registry.getId(biome);
    }

    @Override
    public List<PlatformBiome> allBiomes() {
        Registry<Biome> registry = biomeRegistry();
        List<PlatformBiome> biomes = new ArrayList<>();
        if (registry == null) {
            return biomes;
        }
        for (Identifier identifier : registry.keySet()) {
            Biome biome = registry.getValue(identifier);
            if (biome != null) {
                biomes.add(ModdedBiome.of(biome, identifier.toString()));
            }
        }
        return biomes;
    }

    private Registry<Biome> biomeRegistry() {
        MinecraftServer instance = server.get();
        if (instance == null) {
            return null;
        }
        return instance.registryAccess().lookupOrThrow(Registries.BIOME);
    }
}
