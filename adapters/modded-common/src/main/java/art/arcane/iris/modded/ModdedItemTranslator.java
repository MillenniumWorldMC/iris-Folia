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

import art.arcane.iris.engine.object.InventorySlotType;
import art.arcane.iris.engine.object.IrisAttributeModifier;
import art.arcane.iris.engine.object.IrisEnchantment;
import art.arcane.iris.engine.object.IrisLoot;
import art.arcane.iris.engine.object.IrisLootTable;
import art.arcane.iris.engine.object.NoiseStyle;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.collection.KMap;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.json.JSONObject;
import art.arcane.volmlib.util.math.RNG;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ModdedItemTranslator {
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();
    private static final Field TYPE_FIELD = lootField("type");
    private static final Field DYE_COLOR_FIELD = lootField("dyeColor");
    private static final String COLOR_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

    private ModdedItemTranslator() {
    }

    public static KList<ItemStack> loot(IrisLootTable table, RNG rng, InventorySlotType slot, ServerLevel level, int x, int y, int z) {
        KList<ItemStack> out = new KList<>();
        KList<IrisLoot> entries = table.getLoot();
        if (entries.isEmpty()) {
            return out;
        }

        int m = 0;
        int c = 0;
        int mx = rng.i(table.getMinPicked(), table.getMaxPicked());

        while (m < mx && c++ < table.getMaxTries()) {
            IrisLoot entry = entries.get(rng.i(entries.size()));
            if (entry.getSlotTypes() != slot) {
                continue;
            }
            ItemStack item = item(entry, table, rng, level, x, y, z);
            if (item != null && !item.isEmpty()) {
                out.add(item);
                m++;
            }
        }

        return out;
    }

    public static ItemStack item(IrisLoot loot, IrisLootTable table, RNG rng, ServerLevel level, int x, int y, int z) {
        if (loot.getChance().aquire(() -> NoiseStyle.STATIC.create(rng)).fit(1, loot.getRarity() * table.getRarity(), x, y, z) != 1) {
            return null;
        }

        try {
            ItemStack stack = baseStack(loot, rng);
            if (stack == null || stack.isEmpty()) {
                return null;
            }
            applyComponents(loot, stack, rng, level);
            applyCustomNbt(stack, loot.getCustomNbt());
            return stack;
        } catch (Throwable e) {
            IrisLogging.reportError(e);
            return null;
        }
    }

    private static ItemStack baseStack(IrisLoot loot, RNG rng) {
        String raw = readString(TYPE_FIELD, loot);
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String key = raw.toLowerCase(Locale.ROOT);
        Identifier id = Identifier.tryParse(key.contains(":") ? key : "minecraft:" + key);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            warnOnce("item:" + key, "Iris loot: unknown item '" + raw + "'");
            return null;
        }

        Item item = BuiltInRegistries.ITEM.getValue(id);
        return new ItemStack(item, Math.max(1, rng.i(loot.getMinAmount(), loot.getMaxAmount())));
    }

    private static void applyComponents(IrisLoot loot, ItemStack stack, RNG rng, ServerLevel level) {
        applyEnchantments(loot, stack, rng, level);
        consumeAttributeDraws(loot, rng);

        if (loot.isUnbreakable()) {
            stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
        }

        if (loot.getItemFlags().isNotEmpty()) {
            warnOnce("itemFlags", "Iris loot: itemFlags are not supported on modded; skipping flags");
        }

        if (loot.getCustomModel() != null) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(loot.getCustomModel().floatValue()), List.of(), List.of(), List.of()));
        }

        if (stack.getMaxDamage() > 0) {
            int max = stack.getMaxDamage();
            int damage = (int) Math.round(Math.max(0, Math.min(max, (1D - rng.d(loot.getMinDurability(), loot.getMaxDurability())) * max)));
            stack.set(DataComponents.DAMAGE, damage);
        }

        if (loot.getLeatherColor() != null) {
            try {
                int rgb = Integer.decode(loot.getLeatherColor()) & 0xFFFFFF;
                stack.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb));
            } catch (NumberFormatException e) {
                warnOnce("leatherColor:" + loot.getLeatherColor(), "Iris loot: invalid leatherColor '" + loot.getLeatherColor() + "'");
            }
        }

        String dye = readString(DYE_COLOR_FIELD, loot);
        if (dye != null) {
            warnOnce("dyeColor", "Iris loot: dyeColor is not supported on modded; skipping");
        }

        if (loot.getDisplayName() != null) {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(colorize(loot.getDisplayName())));
        }

        applyLore(loot, stack);
    }

    private static void applyEnchantments(IrisLoot loot, ItemStack stack, RNG rng, ServerLevel level) {
        if (loot.getEnchantments().isEmpty()) {
            return;
        }

        Registry<Enchantment> registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        DataComponentType<ItemEnchantments> component = stack.is(Items.ENCHANTED_BOOK) ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS;
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(stack.getOrDefault(component, ItemEnchantments.EMPTY));
        boolean changed = false;

        for (IrisEnchantment enchantment : loot.getEnchantments()) {
            String name = enchantment.getEnchantment();
            if (name == null || name.isBlank()) {
                continue;
            }
            String key = name.toLowerCase(Locale.ROOT);
            Identifier id = Identifier.tryParse(key.contains(":") ? key : "minecraft:" + key);
            Optional<Holder.Reference<Enchantment>> holder = id == null ? Optional.empty() : registry.get(id);
            if (holder.isEmpty()) {
                warnOnce("enchantment:" + key, "Iris loot: unknown enchantment '" + name + "'");
                continue;
            }
            if (rng.nextDouble() < enchantment.getChance()) {
                mutable.set(holder.get(), enchantment.getLevel(rng));
                changed = true;
            }
        }

        if (changed) {
            stack.set(component, mutable.toImmutable());
        }
    }

    private static void consumeAttributeDraws(IrisLoot loot, RNG rng) {
        if (loot.getAttributes().isEmpty()) {
            return;
        }
        warnOnce("attributes", "Iris loot: attribute modifiers are not supported on modded; skipping");
        for (IrisAttributeModifier attribute : loot.getAttributes()) {
            if (rng.nextDouble() < attribute.getChance()) {
                attribute.getAmount(rng);
            }
        }
    }

    private static void applyLore(IrisLoot loot, ItemStack stack) {
        if (loot.getLore().isEmpty()) {
            return;
        }

        List<Component> lines = new ArrayList<>();
        for (String line : loot.getLore()) {
            String colored = colorize(line);
            if (colored.length() > 24) {
                for (String wrapped : Form.wrapWords(colored, 24).split("\\Q\n\\E")) {
                    lines.add(Component.literal(wrapped.trim()));
                }
            } else {
                lines.add(Component.literal(colored));
            }
        }
        stack.set(DataComponents.LORE, new ItemLore(lines));
    }

    private static void applyCustomNbt(ItemStack stack, KMap<String, Object> customNbt) {
        if (customNbt == null || customNbt.isEmpty()) {
            return;
        }
        try {
            CompoundTag tag = TagParser.parseCompoundFully(new JSONObject(customNbt).toString());
            tag.merge(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        } catch (CommandSyntaxException e) {
            warnOnce("customNbt:" + e.getMessage(), "Iris loot: invalid customNbt: " + e.getMessage());
        }
    }

    private static String colorize(String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (chars[i] == '&' && COLOR_CODES.indexOf(chars[i + 1]) > -1) {
                chars[i] = '§';
                chars[i + 1] = Character.toLowerCase(chars[i + 1]);
            }
        }
        return new String(chars);
    }

    private static void warnOnce(String key, String message) {
        if (WARNED.add(key)) {
            IrisLogging.warn(message);
        }
    }

    private static Field lootField(String name) {
        try {
            Field field = IrisLoot.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("IrisLoot field missing: " + name, e);
        }
    }

    private static String readString(Field field, IrisLoot loot) {
        try {
            Object value = field.get(loot);
            return value == null ? null : value.toString();
        } catch (IllegalAccessException e) {
            return null;
        }
    }
}
