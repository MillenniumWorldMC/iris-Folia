package art.arcane.iris.core.pack;

import art.arcane.iris.core.pack.ContentKeyValidator.ContentKeyError;
import art.arcane.iris.core.pack.ContentKeyValidator.ContentRegistry;
import art.arcane.iris.spi.PlatformBiome;
import art.arcane.iris.spi.PlatformBlockProperty;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.spi.PlatformEntityType;
import art.arcane.iris.spi.PlatformItem;
import art.arcane.iris.spi.PlatformRegistries;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ContentKeyValidatorTest {
    private static PlatformRegistries registries() {
        return new FakeRegistries(
                List.of("minecraft:stone", "minecraft:cobblestone", "minecraft:oak_log", "minecraft:grass_block"),
                List.of("minecraft:diamond", "minecraft:wooden_pickaxe", "minecraft:stone_pickaxe"),
                List.of("minecraft:zombie", "minecraft:creeper"));
    }

    @Test
    public void validateFlagsUnknownBlockKeyWithSingleError() {
        List<ContentKeyError> errors = ContentKeyValidator.validate(registries(),
                List.of("minecraft:stone", "minecraft:not_a_real_block"), List.of(), List.of());
        assertEquals(1, errors.size());
        ContentKeyError error = errors.get(0);
        assertEquals("minecraft:not_a_real_block", error.key());
        assertEquals(ContentRegistry.BLOCK, error.registry());
        assertTrue(error.namespaceLoaded());
    }

    @Test
    public void validateFlagsUnloadedNamespaceWhenNamespaceMissing() {
        List<ContentKeyError> errors = ContentKeyValidator.validate(registries(),
                List.of("create:cogwheel"), List.of(), List.of());
        assertEquals(1, errors.size());
        assertFalse(errors.get(0).namespaceLoaded());
        assertEquals("create", ContentKeyValidator.namespaceOf(errors.get(0).key()));
    }

    @Test
    public void validateSuggestsNearestKeyForTypo() {
        List<ContentKeyError> errors = ContentKeyValidator.validate(registries(),
                List.of("minecraft:cobblstone"), List.of(), List.of());
        assertEquals(1, errors.size());
        assertEquals("minecraft:cobblestone", errors.get(0).suggestion());
    }

    @Test
    public void validateDedupsRepeatedKeyIntoOneError() {
        List<ContentKeyError> errors = ContentKeyValidator.validate(registries(),
                List.of("minecraft:ghostblock", "minecraft:ghostblock", "ghostblock"), List.of(), List.of());
        assertEquals(1, errors.size());
        assertEquals("minecraft:ghostblock", errors.get(0).key());
    }

    @Test
    public void validateDefaultsBareKeyToMinecraftNamespace() {
        List<ContentKeyError> errors = ContentKeyValidator.validate(registries(),
                List.of("stone", "oak_log"), List.of(), List.of());
        assertTrue(errors.isEmpty());
    }

    @Test
    public void validateStripsBlockStateBeforeLookup() {
        List<ContentKeyError> errors = ContentKeyValidator.validate(registries(),
                List.of("minecraft:oak_log[axis=y]"), List.of(), List.of());
        assertTrue(errors.isEmpty());
    }

    @Test
    public void validateReportsUnknownItemAndEntityPerRegistry() {
        List<ContentKeyError> errors = ContentKeyValidator.validate(registries(),
                List.of(), List.of("minecraft:not_an_item"), List.of("minecraft:not_an_entity"));
        assertEquals(2, errors.size());
        assertTrue(errors.stream().anyMatch(e -> e.registry() == ContentRegistry.ITEM && e.key().equals("minecraft:not_an_item")));
        assertTrue(errors.stream().anyMatch(e -> e.registry() == ContentRegistry.ENTITY && e.key().equals("minecraft:not_an_entity")));
    }

    @Test
    public void validateReturnsEmptyWhenRegistriesNull() {
        assertTrue(ContentKeyValidator.validate(null, List.of("minecraft:whatever"), List.of(), List.of()).isEmpty());
    }

    private record FakeRegistries(List<String> blocks, List<String> items, List<String> entities) implements PlatformRegistries {
        @Override
        public PlatformBlockState block(String key) {
            return null;
        }

        @Override
        public PlatformBlockState blockOrNull(String key) {
            return null;
        }

        @Override
        public PlatformBlockState blockOrNull(String key, boolean warn) {
            return null;
        }

        @Override
        public PlatformBlockState air() {
            return null;
        }

        @Override
        public PlatformBlockState deepSlateOre(PlatformBlockState block, PlatformBlockState ore) {
            return null;
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
            return blocks;
        }

        @Override
        public List<String> biomeKeys() {
            return List.of();
        }

        @Override
        public List<String> structureKeys() {
            return List.of();
        }

        @Override
        public List<String> itemKeys() {
            return items;
        }

        @Override
        public List<String> entityKeys() {
            return entities;
        }

        @Override
        public List<String> blockTypeKeys() {
            return List.of();
        }

        @Override
        public List<String> enchantmentKeys() {
            return List.of();
        }

        @Override
        public List<String> potionEffectKeys() {
            return List.of();
        }

        @Override
        public Map<String, List<PlatformBlockProperty>> blockStateProperties() {
            return Map.of();
        }
    }
}
