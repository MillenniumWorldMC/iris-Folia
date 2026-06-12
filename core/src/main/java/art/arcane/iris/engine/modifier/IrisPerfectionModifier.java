/*
 * Iris is a World Generator for Minecraft Bukkit Servers
 * Copyright (c) 2022 Arcane Arts (Volmit Software)
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

package art.arcane.iris.engine.modifier;

import art.arcane.iris.platform.bukkit.BukkitBlockResolution;

import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.framework.EngineAssignedModifier;
import art.arcane.iris.util.project.context.ChunkContext;
import art.arcane.iris.util.common.data.B;
import art.arcane.iris.util.project.hunk.Hunk;
import art.arcane.iris.util.common.parallel.BurstExecutor;
import art.arcane.volmlib.util.scheduling.PrecisionStopwatch;
import art.arcane.iris.spi.PlatformBlockState;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class IrisPerfectionModifier extends EngineAssignedModifier<PlatformBlockState> {
    private static final class States {
        private static final PlatformBlockState AIR = B.getState("AIR");
        private static final PlatformBlockState WATER = B.getState("WATER");
        private static final Map<String, PlatformBlockState> ORE_BASES = buildOreBases();
    }

    public IrisPerfectionModifier(Engine engine) {
        super(engine, "Perfection");
    }

    private static Map<String, PlatformBlockState> buildOreBases() {
        Map<String, PlatformBlockState> map = new HashMap<>();
        PlatformBlockState stone = B.getState("STONE");
        PlatformBlockState deepslate = B.getState("DEEPSLATE");
        PlatformBlockState netherrack = B.getState("NETHERRACK");
        PlatformBlockState blackstone = B.getState("BLACKSTONE");
        map.put("minecraft:coal_ore", stone);
        map.put("minecraft:copper_ore", stone);
        map.put("minecraft:iron_ore", stone);
        map.put("minecraft:gold_ore", stone);
        map.put("minecraft:redstone_ore", stone);
        map.put("minecraft:lapis_ore", stone);
        map.put("minecraft:diamond_ore", stone);
        map.put("minecraft:emerald_ore", stone);
        map.put("minecraft:deepslate_coal_ore", deepslate);
        map.put("minecraft:deepslate_copper_ore", deepslate);
        map.put("minecraft:deepslate_iron_ore", deepslate);
        map.put("minecraft:deepslate_gold_ore", deepslate);
        map.put("minecraft:deepslate_redstone_ore", deepslate);
        map.put("minecraft:deepslate_lapis_ore", deepslate);
        map.put("minecraft:deepslate_diamond_ore", deepslate);
        map.put("minecraft:deepslate_emerald_ore", deepslate);
        map.put("minecraft:nether_gold_ore", netherrack);
        map.put("minecraft:nether_quartz_ore", netherrack);
        map.put("minecraft:ancient_debris", netherrack);
        map.put("minecraft:gilded_blackstone", blackstone);
        return map;
    }

    private static String baseKey(PlatformBlockState state) {
        String key = state.key();
        int bracket = key.indexOf('[');
        return bracket < 0 ? key : key.substring(0, bracket);
    }

    @Override
    public void onModify(int x, int z, Hunk<PlatformBlockState> output, boolean multicore, ChunkContext context) {
        PrecisionStopwatch p = PrecisionStopwatch.start();
        if (getDimension().isHideOresForHiddenOre()) {
            hideOres(output, multicore);
        }
        AtomicBoolean changed = new AtomicBoolean(true);
        int passes = 0;
        AtomicInteger changes = new AtomicInteger();
        List<Integer> surfaces = new ArrayList<>();
        List<Integer> ceilings = new ArrayList<>();
        BurstExecutor burst = burst().burst(multicore);
        while (changed.get()) {
            passes++;
            changed.set(false);
            for (int i = 0; i < 16; i++) {
                int finalI = i;
                burst.queue(() -> {
                    for (int j = 0; j < 16; j++) {
                        surfaces.clear();
                        ceilings.clear();
                        int top = getHeight(output, finalI, j);
                        boolean inside = true;
                        surfaces.add(top);

                        for (int k = top; k >= 0; k--) {
                            PlatformBlockState b = output.get(finalI, k, j);
                            BlockData rawB = unwrap(b);
                            boolean now = b != null && !(BukkitBlockResolution.isAir(rawB) || BukkitBlockResolution.isFluid(rawB));

                            if (now != inside) {
                                inside = now;

                                if (inside) {
                                    surfaces.add(k);
                                } else {
                                    ceilings.add(k + 1);
                                }
                            }
                        }

                        for (int k : surfaces) {
                            PlatformBlockState tip = output.get(finalI, k, j);

                            if (tip == null) {
                                continue;
                            }

                            boolean remove = false;
                            boolean remove2 = false;

                            BlockData rawTip = (BlockData) tip.nativeHandle();
                            if (BukkitBlockResolution.isDecorant(rawTip)) {
                                BlockData bel = unwrap(output.get(finalI, k - 1, j));

                                if (bel == null) {
                                    remove = true;
                                } else if (!BukkitBlockResolution.canPlaceOnto(rawTip.getMaterial(), bel.getMaterial())) {
                                    remove = true;
                                } else if (bel instanceof Bisected) {
                                    BlockData bb = unwrap(output.get(finalI, k - 2, j));
                                    if (bb == null || !BukkitBlockResolution.canPlaceOnto(bel.getMaterial(), bb.getMaterial())) {
                                        remove = true;
                                        remove2 = true;
                                    }
                                }

                                if (remove) {
                                    changed.set(true);
                                    changes.getAndIncrement();
                                    output.set(finalI, k, j, States.AIR);

                                    if (remove2) {
                                        changes.getAndIncrement();
                                        output.set(finalI, k - 1, j, States.AIR);
                                    }
                                }
                            }
                        }
                    }
                });
            }
        }

        getEngine().getMetrics().getPerfection().put(p.getMilliseconds());
    }

    private void hideOres(Hunk<PlatformBlockState> output, boolean multicore) {
        BurstExecutor burst = burst().burst(multicore);
        int height = output.getHeight();
        for (int i = 0; i < 16; i++) {
            int finalI = i;
            burst.queue(() -> {
                for (int j = 0; j < 16; j++) {
                    for (int k = height - 1; k >= 0; k--) {
                        PlatformBlockState block = output.get(finalI, k, j);
                        if (block == null) {
                            continue;
                        }
                        PlatformBlockState base = States.ORE_BASES.get(baseKey(block));
                        if (base != null) {
                            output.set(finalI, k, j, base);
                        }
                    }
                }
            });
        }
        burst.complete();
    }

    private int getHeight(Hunk<PlatformBlockState> output, int x, int z) {
        for (int i = output.getHeight() - 1; i >= 0; i--) {
            PlatformBlockState b = output.get(x, i, z);

            if (b != null) {
                BlockData rawB = (BlockData) b.nativeHandle();
                if (!BukkitBlockResolution.isAir(rawB) && !BukkitBlockResolution.isFluid(rawB)) {
                    return i;
                }
            }
        }

        return 0;
    }

    private static BlockData unwrap(PlatformBlockState state) {
        return state == null ? null : (BlockData) state.nativeHandle();
    }
}
