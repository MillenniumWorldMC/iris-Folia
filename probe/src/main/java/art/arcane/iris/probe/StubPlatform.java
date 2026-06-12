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

package art.arcane.iris.probe;

import art.arcane.iris.spi.IrisPlatform;
import art.arcane.iris.spi.LogLevel;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBiomeWriter;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformCapabilities;
import art.arcane.iris.spi.PlatformEntityType;
import art.arcane.iris.spi.PlatformItem;
import art.arcane.iris.spi.PlatformRegistries;
import art.arcane.iris.spi.PlatformScheduler;
import art.arcane.iris.spi.PlatformStructureHooks;

import java.io.File;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public final class StubPlatform implements IrisPlatform {
    private final StubRegistries registries = new StubRegistries();

    private static final class StubBlockState implements PlatformBlockState {
        private static final ConcurrentHashMap<String, StubBlockState> CACHE = new ConcurrentHashMap<>();
        private final String key;

        private StubBlockState(String key) {
            this.key = key;
        }

        static StubBlockState of(String key) {
            return CACHE.computeIfAbsent(key, StubBlockState::new);
        }

        @Override
        public String key() {
            return key;
        }

        @Override
        public String namespace() {
            int colon = key.indexOf(':');
            return colon >= 0 ? key.substring(0, colon) : "minecraft";
        }

        @Override
        public boolean isAir() {
            return key.endsWith("air");
        }

        @Override
        public boolean isSolid() {
            return !isAir();
        }

        @Override
        public boolean isFluid() {
            return false;
        }

        @Override
        public boolean isWater() {
            return false;
        }

        @Override
        public boolean isWaterLogged() {
            return false;
        }

        @Override
        public boolean isLit() {
            return false;
        }

        @Override
        public boolean isUpdatable() {
            return false;
        }

        @Override
        public boolean isFoliage() {
            return false;
        }

        @Override
        public boolean isFoliagePlantable() {
            return false;
        }

        @Override
        public boolean isDecorant() {
            return false;
        }

        @Override
        public boolean isStorage() {
            return false;
        }

        @Override
        public boolean isStorageChest() {
            return false;
        }

        @Override
        public boolean isOre() {
            return false;
        }

        @Override
        public boolean isDeepSlate() {
            return false;
        }

        @Override
        public boolean isVineBlock() {
            return false;
        }

        @Override
        public boolean canPlaceOnto(PlatformBlockState onto) {
            return true;
        }

        @Override
        public boolean matches(PlatformBlockState state) {
            return equals(state);
        }

        @Override
        public boolean hasTileEntity() {
            return false;
        }

        @Override
        public PlatformBlockState withProperty(String name, String value) {
            return this;
        }

        @Override
        public Object nativeHandle() {
            return key;
        }
    }

    private static final class StubRegistries implements PlatformRegistries {
        @Override
        public PlatformBlockState block(String key) {
            return StubBlockState.of(key);
        }

        @Override
        public PlatformBlockState blockOrNull(String key) {
            return StubBlockState.of(key);
        }

        @Override
        public PlatformBlockState blockOrNull(String key, boolean warn) {
            return StubBlockState.of(key);
        }

        @Override
        public PlatformBlockState air() {
            return StubBlockState.of("minecraft:air");
        }

        @Override
        public PlatformBlockState deepSlateOre(PlatformBlockState block, PlatformBlockState ore) {
            return ore;
        }

        @Override
        public PlatformBiome biome(String key) {
            return null;
        }

        @Override
        public PlatformItem item(String key) {
            return null;
        }

        @Override
        public PlatformEntityType entity(String key) {
            return null;
        }

        @Override
        public List<String> blockKeys() {
            return List.of();
        }

        @Override
        public List<String> biomeKeys() {
            return List.of();
        }

        @Override
        public List<String> structureKeys() {
            return List.of();
        }
    }

    @Override
    public String platformName() {
        return "probe";
    }

    @Override
    public String minecraftVersion() {
        return "probe";
    }

    @Override
    public PlatformRegistries registries() {
        return registries;
    }

    @Override
    public PlatformScheduler scheduler() {
        return null;
    }

    @Override
    public PlatformCapabilities capabilities() {
        return null;
    }

    @Override
    public PlatformStructureHooks structureHooks() {
        return null;
    }

    @Override
    public PlatformBiomeWriter biomeWriter() {
        return null;
    }

    @Override
    public File dataFolder() {
        return new File(System.getProperty("java.io.tmpdir"), "iris-probe");
    }

    @Override
    public File dataFile(String... path) {
        return new File(dataFolder(), String.join(File.separator, path));
    }

    @Override
    public File pluginJar() {
        return new File(dataFolder(), "probe.jar");
    }

    @Override
    public int irisVersionNumber() {
        return 0;
    }

    @Override
    public int minecraftVersionNumber() {
        return 0;
    }

    @Override
    public void callEvent(Object event) {
    }

    @Override
    public void dispatchConsoleCommand(String command) {
    }

    @Override
    public void log(LogLevel level, String message) {
    }

    @Override
    public void msg(String message) {
    }

    @Override
    public void reportError(Throwable error) {
    }
}
