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

package art.arcane.iris.platform.bukkit;

import art.arcane.iris.core.nms.INMS;
import art.arcane.iris.spi.PlatformBlockState;
import art.arcane.iris.util.common.data.IrisCustomData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Interned Bukkit adapter for a neutral block state backed by BlockData.
 */
public final class BukkitBlockState implements PlatformBlockState {
    private static final ConcurrentHashMap<String, BukkitBlockState> CACHE = new ConcurrentHashMap<>();

    private final BlockData data;
    private final String key;
    private final String namespace;
    private final Boolean air;
    private final Boolean solid;
    private final Boolean occluding;
    private final Boolean fluid;
    private final Boolean water;
    private final Boolean waterLogged;
    private final Boolean lit;
    private final Boolean updatable;
    private final Boolean foliage;
    private final Boolean foliagePlantable;
    private final Boolean decorant;
    private final Boolean storage;
    private final Boolean storageChest;
    private final Boolean ore;
    private final Boolean deepSlate;
    private final Boolean vineBlock;
    private volatile Boolean tileEntity;

    private BukkitBlockState(BlockData data, String key) {
        this.data = data;
        this.key = key;
        this.namespace = parseNamespace(key);
        Material material = data.getMaterial();
        if (material == null) {
            this.air = null;
            this.solid = null;
            this.occluding = null;
            this.fluid = null;
            this.water = null;
            this.waterLogged = null;
            this.lit = null;
            this.updatable = null;
            this.foliage = null;
            this.foliagePlantable = null;
            this.decorant = null;
            this.storage = null;
            this.storageChest = null;
            this.ore = null;
            this.deepSlate = null;
            this.vineBlock = null;
            return;
        }

        this.air = BukkitBlockResolution.isAir(data);
        this.solid = BukkitBlockResolution.isSolid(data);
        this.occluding = material.isOccluding();
        this.fluid = BukkitBlockResolution.isFluid(data);
        this.water = BukkitBlockResolution.isWater(data);
        this.waterLogged = BukkitBlockResolution.isWaterLogged(data);
        this.lit = BukkitBlockResolution.isLit(data);
        this.updatable = BukkitBlockResolution.isUpdatable(data);
        this.foliage = BukkitBlockResolution.isFoliage(data);
        this.foliagePlantable = BukkitBlockResolution.isFoliagePlantable(data);
        this.decorant = BukkitBlockResolution.isDecorant(data);
        this.storage = BukkitBlockResolution.isStorage(data);
        this.storageChest = BukkitBlockResolution.isStorageChest(data);
        this.ore = BukkitBlockResolution.isOre(data);
        this.deepSlate = BukkitBlockResolution.isDeepSlate(data);
        this.vineBlock = BukkitBlockResolution.isVineBlock(data);
    }

    public static BukkitBlockState of(BlockData data) {
        if (data instanceof IrisCustomData custom) {
            return new BukkitBlockState(data, custom.getAsString());
        }
        String key = data.getAsString();
        return CACHE.computeIfAbsent(key, (String k) -> new BukkitBlockState(data, k));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BukkitBlockState state)) {
            return false;
        }
        return data.equals(state.data);
    }

    @Override
    public int hashCode() {
        return key.hashCode();
    }

    private static String parseNamespace(String key) {
        String base = key;
        int bracket = base.indexOf('[');
        if (bracket >= 0) {
            base = base.substring(0, bracket);
        }
        int colon = base.indexOf(':');
        return colon >= 0 ? base.substring(0, colon) : "minecraft";
    }

    private static String mergeProperty(String key, String name, String value) {
        int bracket = key.indexOf('[');
        if (bracket < 0) {
            return key + "[" + name + "=" + value + "]";
        }
        String base = key.substring(0, bracket);
        String body = key.substring(bracket + 1, key.lastIndexOf(']'));
        LinkedHashMap<String, String> properties = new LinkedHashMap<>();
        for (String entry : body.split(",")) {
            int equals = entry.indexOf('=');
            if (equals < 0) {
                continue;
            }
            properties.put(entry.substring(0, equals).trim(), entry.substring(equals + 1).trim());
        }
        properties.put(name, value);
        StringBuilder merged = new StringBuilder(base).append('[');
        boolean first = true;
        for (Map.Entry<String, String> property : properties.entrySet()) {
            if (!first) {
                merged.append(',');
            }
            merged.append(property.getKey()).append('=').append(property.getValue());
            first = false;
        }
        return merged.append(']').toString();
    }

    @Override
    public String key() {
        return key;
    }

    @Override
    public String namespace() {
        return namespace;
    }

    @Override
    public boolean isAir() {
        return air != null ? air : BukkitBlockResolution.isAir(data);
    }

    @Override
    public boolean isSolid() {
        return solid != null ? solid : BukkitBlockResolution.isSolid(data);
    }

    @Override
    public boolean isOccluding() {
        return occluding != null ? occluding : data.getMaterial().isOccluding();
    }

    @Override
    public boolean isCustom() {
        return data instanceof IrisCustomData;
    }

    @Override
    public boolean isFluid() {
        return fluid != null ? fluid : BukkitBlockResolution.isFluid(data);
    }

    @Override
    public boolean isWater() {
        return water != null ? water : BukkitBlockResolution.isWater(data);
    }

    @Override
    public boolean isWaterLogged() {
        return waterLogged != null ? waterLogged : BukkitBlockResolution.isWaterLogged(data);
    }

    @Override
    public boolean isLit() {
        return lit != null ? lit : BukkitBlockResolution.isLit(data);
    }

    @Override
    public boolean isUpdatable() {
        return updatable != null ? updatable : BukkitBlockResolution.isUpdatable(data);
    }

    @Override
    public boolean isFoliage() {
        return foliage != null ? foliage : BukkitBlockResolution.isFoliage(data);
    }

    @Override
    public boolean isFoliagePlantable() {
        return foliagePlantable != null ? foliagePlantable : BukkitBlockResolution.isFoliagePlantable(data);
    }

    @Override
    public boolean isDecorant() {
        return decorant != null ? decorant : BukkitBlockResolution.isDecorant(data);
    }

    @Override
    public boolean isStorage() {
        return storage != null ? storage : BukkitBlockResolution.isStorage(data);
    }

    @Override
    public boolean isStorageChest() {
        return storageChest != null ? storageChest : BukkitBlockResolution.isStorageChest(data);
    }

    @Override
    public boolean isOre() {
        return ore != null ? ore : BukkitBlockResolution.isOre(data);
    }

    @Override
    public boolean isDeepSlate() {
        return deepSlate != null ? deepSlate : BukkitBlockResolution.isDeepSlate(data);
    }

    @Override
    public boolean isVineBlock() {
        return vineBlock != null ? vineBlock : BukkitBlockResolution.isVineBlock(data);
    }

    @Override
    public boolean canPlaceOnto(PlatformBlockState onto) {
        return BukkitBlockResolution.canPlaceOnto(data.getMaterial(), ((BlockData) onto.nativeHandle()).getMaterial());
    }

    @Override
    public boolean matches(PlatformBlockState state) {
        return data.matches((BlockData) state.nativeHandle());
    }

    @Override
    public boolean hasTileEntity() {
        Boolean cached = tileEntity;
        if (cached == null) {
            cached = INMS.get().hasTile(data.getMaterial());
            tileEntity = cached;
        }
        return cached;
    }

    @Override
    public boolean isAirOrFluid() {
        if (air != null && fluid != null) {
            return air || fluid;
        }
        return PlatformBlockState.super.isAirOrFluid();
    }

    @Override
    public PlatformBlockState withProperty(String name, String value) {
        String merged = mergeProperty(key, name, value);
        BlockData resolved = Bukkit.createBlockData(merged);
        return of(resolved);
    }

    @Override
    public Object nativeHandle() {
        return data;
    }
}
