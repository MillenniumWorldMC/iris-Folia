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
import art.arcane.iris.engine.framework.PlacedObject;
import art.arcane.iris.engine.object.IObjectLoot;
import art.arcane.iris.engine.object.InventorySlotType;
import art.arcane.iris.engine.object.IrisBiome;
import art.arcane.iris.engine.object.IrisLootTable;
import art.arcane.iris.engine.object.IrisObjectLoot;
import art.arcane.iris.engine.object.IrisObjectPlacement;
import art.arcane.iris.engine.object.IrisObjectVanillaLoot;
import art.arcane.iris.engine.object.IrisRegion;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.mantle.runtime.MantleChunk;
import art.arcane.volmlib.util.math.RNG;
import art.arcane.volmlib.util.matter.Matter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class ModdedLootApplier {
    private ModdedLootApplier() {
    }

    private record Candidate(IrisLootTable table, Identifier vanillaId, int weight) {
    }

    private record Resolution(KList<IrisLootTable> tables, Identifier vanillaTable) {
        boolean isEmpty() {
            return tables.isEmpty() && vanillaTable == null;
        }
    }

    public static void apply(Engine engine, ServerLevel level, BlockPos pos, BlockState state, MantleChunk<Matter> mc, RNG rng, int localX, int localZ) {
        Resolution resolution = resolveTables(engine, rng, pos, state, mc);
        if (resolution.isEmpty()) {
            return;
        }

        if (resolution.vanillaTable() != null) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof RandomizableContainerBlockEntity randomizable) {
                randomizable.setLootTable(ResourceKey.create(Registries.LOOT_TABLE, resolution.vanillaTable()), rng.nextLong());
            } else {
                IrisLogging.debug("Iris loot: no randomizable container at " + pos + " for vanilla table " + resolution.vanillaTable());
            }
        }

        if (resolution.tables().isEmpty()) {
            return;
        }

        Container container = resolveContainer(level, pos, state);
        if (container == null) {
            IrisLogging.debug("Iris loot: no container at " + pos);
            return;
        }

        KList<ItemStack> items = new KList<>();
        for (IrisLootTable table : resolution.tables()) {
            if (table == null) {
                continue;
            }
            items.addAll(ModdedItemTranslator.loot(table, rng, InventorySlotType.STORAGE, level, localX, pos.getY(), localZ));
        }

        for (ItemStack item : items) {
            addItem(container, item);
        }

        scramble(container, rng);
        container.setChanged();
    }

    private static Resolution resolveTables(Engine engine, RNG rng, BlockPos pos, BlockState state, MantleChunk<Matter> mc) {
        int rx = pos.getX();
        int rz = pos.getZ();
        int ry = pos.getY() - engine.getWorld().minHeight();
        double he = engine.getComplex().getHeightStream().get(rx, rz);
        KList<IrisLootTable> tables = new KList<>();
        Identifier vanillaTable = null;

        PlacedObject po = engine.getObjectPlacement(rx, ry, rz, mc);
        if (po != null && po.getPlacement() != null && ModdedBlockResolution.isStorageChest(state)) {
            Candidate picked = pickPlacementTable(engine, po.getPlacement(), state, rng);
            if (picked != null) {
                if (picked.table() != null) {
                    tables.add(picked.table());
                } else {
                    vanillaTable = picked.vanillaId();
                }
                if (po.getPlacement().isOverrideGlobalLoot()) {
                    return new Resolution(tables, vanillaTable);
                }
            }
        }

        IrisRegion region = engine.getComplex().getRegionStream().get(rx, rz);
        IrisBiome biomeSurface = engine.getComplex().getTrueBiomeStream().get(rx, rz);
        IrisBiome biomeUnder = ry < he ? engine.getCaveBiome(rx, ry, rz) : biomeSurface;

        double multiplier = 1D * engine.getDimension().getLoot().getMultiplier() * region.getLoot().getMultiplier() * biomeSurface.getLoot().getMultiplier() * biomeUnder.getLoot().getMultiplier();
        boolean fallback = tables.isEmpty() && vanillaTable == null;
        engine.injectTables(tables, engine.getDimension().getLoot(), fallback);
        engine.injectTables(tables, region.getLoot(), fallback);
        engine.injectTables(tables, biomeSurface.getLoot(), fallback);
        engine.injectTables(tables, biomeUnder.getLoot(), fallback);

        if (tables.isNotEmpty()) {
            int target = (int) Math.round(tables.size() * multiplier);

            while (tables.size() < target && tables.isNotEmpty()) {
                tables.add(tables.get(rng.i(tables.size() - 1)));
            }

            while (tables.size() > target && tables.isNotEmpty()) {
                tables.remove(rng.i(tables.size() - 1));
            }
        }

        return new Resolution(tables, vanillaTable);
    }

    private static Candidate pickPlacementTable(Engine engine, IrisObjectPlacement placement, BlockState state, RNG rng) {
        List<Candidate> exact = new ArrayList<>();
        List<Candidate> basic = new ArrayList<>();
        List<Candidate> global = new ArrayList<>();

        for (IrisObjectLoot loot : placement.getLoot()) {
            if (loot == null) {
                continue;
            }
            IrisLootTable table = engine.getData().getLootLoader().load(loot.getName());
            if (table == null) {
                IrisLogging.warn("Couldn't find loot table " + loot.getName());
                continue;
            }
            bucket(engine, loot, new Candidate(table, null, loot.getWeight()), state, exact, basic, global);
        }

        for (IrisObjectVanillaLoot loot : placement.getVanillaLoot()) {
            if (loot == null) {
                continue;
            }
            Identifier id = loot.getName() == null ? null : Identifier.tryParse(loot.getName());
            if (id == null) {
                IrisLogging.warn("Couldn't parse vanilla loot table " + loot.getName());
                continue;
            }
            bucket(engine, loot, new Candidate(null, id, loot.getWeight()), state, exact, basic, global);
        }

        List<Candidate> pool = !exact.isEmpty() ? exact : !basic.isEmpty() ? basic : global;
        return pickWeighted(pool, rng);
    }

    private static void bucket(Engine engine, IObjectLoot loot, Candidate candidate, BlockState state, List<Candidate> exact, List<Candidate> basic, List<Candidate> global) {
        if (loot.getFilter().isEmpty()) {
            global.add(candidate);
            return;
        }

        for (PlatformBlockState filterState : loot.getFilter(engine.getData())) {
            BlockState filter = (BlockState) filterState.nativeHandle();
            if (loot.isExact() ? filter == state : filter.getBlock() == state.getBlock()) {
                (loot.isExact() ? exact : basic).add(candidate);
                return;
            }
        }
    }

    private static Candidate pickWeighted(List<Candidate> pool, RNG rng) {
        if (pool.isEmpty()) {
            return null;
        }
        int total = 0;
        for (Candidate candidate : pool) {
            total += Math.max(0, candidate.weight());
        }
        if (total <= 0) {
            return pool.get(rng.nextInt(pool.size()));
        }
        int pull = rng.nextInt(total);
        for (Candidate candidate : pool) {
            pull -= Math.max(0, candidate.weight());
            if (pull < 0) {
                return candidate;
            }
        }
        return pool.get(pool.size() - 1);
    }

    private static Container resolveContainer(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof ChestBlock chestBlock) {
            Container combined = ChestBlock.getContainer(chestBlock, state, level, pos, true);
            if (combined != null) {
                return combined;
            }
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof Container container ? container : null;
    }

    private static void addItem(Container container, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack existing = container.getItem(i);
            if (existing.isEmpty()) {
                container.setItem(i, stack);
                return;
            }
            if (ItemStack.isSameItemSameComponents(existing, stack) && existing.getCount() < existing.getMaxStackSize()) {
                int move = Math.min(stack.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(move);
                stack.shrink(move);
            }
        }
    }

    private static void scramble(Container container, RNG rng) {
        int size = container.getContainerSize();
        ItemStack[] items = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            items[i] = container.getItem(i);
        }
        boolean packedFull = false;

        splitting:
        for (int i = 0; i < items.length; i++) {
            ItemStack is = items[i];

            if (!is.isEmpty() && is.getCount() > 1 && !packedFull) {
                for (int j = 0; j < items.length; j++) {
                    if (items[j].isEmpty()) {
                        int take = rng.nextInt(is.getCount());
                        take = take == 0 ? 1 : take;
                        is.setCount(is.getCount() - take);
                        items[j] = is.copyWithCount(take);
                        continue splitting;
                    }
                }

                packedFull = true;
            }
        }

        for (int i = items.length; i > 1; i--) {
            int j = rng.nextInt(i);
            ItemStack tmp = items[i - 1];
            items[i - 1] = items[j];
            items[j] = tmp;
        }

        for (int i = 0; i < size; i++) {
            container.setItem(i, items[i]);
        }
    }
}
