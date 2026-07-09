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

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.InventorySlotType;
import art.arcane.iris.engine.object.IrisLootTable;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.chunk.ChunkGenerator;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ModdedDeathLoot {
    private static final String TAG_PREFIX = "iris_loot|";
    private static final int LOOT_RNG_SALT = 345911;
    private static final Set<String> WARNED_TABLES = ConcurrentHashMap.newKeySet();

    private ModdedDeathLoot() {
    }

    public static void tag(LivingEntity entity, KList<String> tableKeys, int spawnX, int spawnY, int spawnZ, RNG rng) {
        if (entity == null || tableKeys == null || tableKeys.isEmpty()) {
            return;
        }
        entity.addTag(TAG_PREFIX + rng.getSeed() + "|" + spawnX + "|" + spawnY + "|" + spawnZ + "|" + String.join(",", tableKeys));
    }

    public static void handle(LivingEntity entity) {
        if (entity == null || !(entity.level() instanceof ServerLevel level)) {
            return;
        }
        String encoded = findTag(entity);
        if (encoded == null) {
            return;
        }
        String[] parts = encoded.substring(TAG_PREFIX.length()).split("\\|", 5);
        if (parts.length < 5) {
            IrisLogging.debug("Iris death loot: malformed loot tag '" + encoded + "'");
            return;
        }
        long seed;
        int spawnX;
        int spawnY;
        int spawnZ;
        try {
            seed = Long.parseLong(parts[0]);
            spawnX = Integer.parseInt(parts[1]);
            spawnY = Integer.parseInt(parts[2]);
            spawnZ = Integer.parseInt(parts[3]);
        } catch (NumberFormatException error) {
            IrisLogging.debug("Iris death loot: malformed loot tag '" + encoded + "'");
            return;
        }
        Engine engine = engineFor(level);
        if (engine == null) {
            return;
        }
        RNG lootRng = new RNG(seed).nextParallelRNG(LOOT_RNG_SALT);
        KList<ItemStack> drops = new KList<>();
        for (String key : parts[4].split(",")) {
            String trimmed = key.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            IrisLootTable table = engine.getData().getLootLoader().load(trimmed);
            if (table == null) {
                if (WARNED_TABLES.add(trimmed)) {
                    IrisLogging.warn("Iris death loot: unknown loot table '" + trimmed + "'");
                }
                continue;
            }
            drops.addAll(ModdedItemTranslator.loot(table, lootRng, InventorySlotType.STORAGE, level, spawnX, spawnY, spawnZ));
        }
        emit(level, entity, drops);
    }

    private static void emit(ServerLevel level, LivingEntity entity, KList<ItemStack> drops) {
        double x = entity.getX();
        double y = entity.getY() + 0.5D;
        double z = entity.getZ();
        for (ItemStack stack : drops) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            ItemEntity itemEntity = new ItemEntity(level, x, y, z, stack);
            itemEntity.setDefaultPickUpDelay();
            level.addFreshEntity(itemEntity);
        }
    }

    private static String findTag(LivingEntity entity) {
        for (String tag : entity.entityTags()) {
            if (tag.startsWith(TAG_PREFIX)) {
                return tag;
            }
        }
        return null;
    }

    private static Engine engineFor(ServerLevel level) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (generator instanceof IrisModdedChunkGenerator irisGenerator) {
            return irisGenerator.engineIfBound();
        }
        return null;
    }
}
