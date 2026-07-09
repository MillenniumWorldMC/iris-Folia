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

import art.arcane.iris.core.IrisSettings;
import art.arcane.iris.engine.framework.Engine;
import art.arcane.iris.engine.object.IrisAttributeModifier;
import art.arcane.iris.engine.object.IrisCommand;
import art.arcane.iris.engine.object.IrisEntity;
import art.arcane.iris.engine.object.IrisLoot;
import art.arcane.iris.modded.api.ModdedCustomContentRegistry;
import art.arcane.iris.spi.IrisLogging;
import art.arcane.volmlib.util.collection.KList;
import art.arcane.volmlib.util.math.RNG;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ModdedEntitySpawner {
    private static final int PASSENGER_RNG_BASE = 234858;
    private static final int LEASH_RNG_SEED = 234548;
    private static final String COLOR_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";
    private static final Set<String> WARNED_TYPES = ConcurrentHashMap.newKeySet();
    private static final Set<String> WARNED_ATTRIBUTES = ConcurrentHashMap.newKeySet();
    private static boolean warnedSpawnEffect = false;

    private ModdedEntitySpawner() {
    }

    public static Entity spawn(Engine engine, IrisEntity irisEntity, ServerLevel level, int blockX, int blockY, int blockZ, RNG rng) {
        if (engine == null || irisEntity == null || level == null) {
            return null;
        }

        double x = blockX + 0.5;
        double y = blockY + 0.5;
        double z = blockZ + 0.5;

        Entity created = create(irisEntity, level, x, y, z);
        if (created == null) {
            return null;
        }

        if (irisEntity.isSpecialType() && !irisEntity.isApplySettingsToCustomMobAnyways()) {
            return created;
        }

        applyConfig(engine, irisEntity, created, level, blockX, blockY, blockZ, rng);
        return created;
    }

    private static Entity create(IrisEntity irisEntity, ServerLevel level, double x, double y, double z) {
        if (irisEntity.isSpecialType()) {
            return ModdedCustomContentRegistry.spawnMob(level, x, y, z, irisEntity.getSpecialType());
        }

        EntityType<?> type = resolveType(irisEntity.getType());
        if (type == null) {
            return null;
        }

        return type.spawn(level, BlockPos.containing(x, y, z), reasonFor(irisEntity.getReason()));
    }

    private static void applyConfig(Engine engine, IrisEntity irisEntity, Entity entity, ServerLevel level, int blockX, int blockY, int blockZ, RNG rng) {
        String customName = irisEntity.getCustomName();
        if (customName != null && !customName.isBlank()) {
            entity.setCustomName(Component.literal(colorize(customName)));
        }
        entity.setCustomNameVisible(irisEntity.isCustomNameVisible());
        entity.setGlowingTag(irisEntity.isGlowing());
        entity.setNoGravity(!irisEntity.isGravity());
        entity.setInvulnerable(irisEntity.isInvulnerable());
        entity.setSilent(irisEntity.isSilent());

        boolean persistent = irisEntity.isKeepEntity() || forcePersist();
        if (persistent && entity instanceof Mob persistentMob) {
            persistentMob.setPersistenceRequired();
        }

        applyPassengers(engine, irisEntity, entity, level, blockX, blockY, blockZ, rng);

        if (entity instanceof LivingEntity living) {
            applyAttributes(irisEntity, living, level, rng);
        }

        if (entity instanceof LivingEntity lootHolder && !irisEntity.getLoot().getTables().isEmpty()) {
            ModdedDeathLoot.tag(lootHolder, irisEntity.getLoot().getTables(), blockX, blockY, blockZ, rng);
        }

        if (entity instanceof Mob mob) {
            mob.setNoAi(!irisEntity.isAi());
            mob.setCanPickUpLoot(irisEntity.isPickupItems());
            if (!irisEntity.isRemovable()) {
                mob.setPersistenceRequired();
            }
            if (irisEntity.getLeashHolder() != null) {
                Entity holder = spawn(engine, irisEntity.getLeashHolder(), level, blockX, blockY, blockZ, rng.nextParallelRNG(LEASH_RNG_SEED));
                if (holder != null) {
                    mob.setLeashedTo(holder, true);
                }
            }
        }

        if (entity instanceof LivingEntity equippable) {
            applyEquipment(irisEntity, equippable, level, rng);
        }

        if (irisEntity.isBaby()) {
            applyBaby(entity);
        }

        if (entity instanceof Panda panda) {
            panda.setMainGene(gene(irisEntity.getPandaMainGene()));
            panda.setHiddenGene(gene(irisEntity.getPandaHiddenGene()));
        }

        if (entity instanceof Villager villager) {
            villager.setPersistenceRequired();
        }

        if (irisEntity.getSpawnEffect() != null || irisEntity.isSpawnEffectRiseOutOfGround()) {
            noteSpawnEffectGap();
        }

        applyRawCommands(irisEntity, level, blockX, blockY, blockZ);
    }

    private static void applyPassengers(Engine engine, IrisEntity irisEntity, Entity entity, ServerLevel level, int blockX, int blockY, int blockZ, RNG rng) {
        int index = 0;
        for (IrisEntity passengerEntity : irisEntity.getPassengers()) {
            Entity passenger = spawn(engine, passengerEntity, level, blockX, blockY, blockZ, rng.nextParallelRNG(PASSENGER_RNG_BASE + index++));
            if (passenger != null) {
                passenger.startRiding(entity);
            }
        }
    }

    private static void applyAttributes(IrisEntity irisEntity, LivingEntity living, ServerLevel level, RNG rng) {
        KList<IrisAttributeModifier> modifiers = irisEntity.getAttributes();
        if (modifiers.isEmpty()) {
            return;
        }

        Registry<Attribute> registry = level.registryAccess().lookupOrThrow(Registries.ATTRIBUTE);
        int index = 0;
        for (IrisAttributeModifier modifier : modifiers) {
            index++;
            if (rng.nextDouble() >= modifier.getChance()) {
                continue;
            }
            Holder<Attribute> holder = ModdedItemTranslator.resolveAttribute(registry, modifier.getAttribute());
            if (holder == null) {
                if (WARNED_ATTRIBUTES.add(modifier.getAttribute())) {
                    IrisLogging.warn("Iris entity: unknown attribute '" + modifier.getAttribute() + "'");
                }
                continue;
            }
            AttributeInstance instance = living.getAttributes().getInstance(holder);
            if (instance == null) {
                continue;
            }
            Identifier id = Identifier.tryParse("iris:" + normalizeName(modifier.getName()) + "_" + index);
            if (id == null) {
                continue;
            }
            instance.addOrReplacePermanentModifier(new AttributeModifier(id, modifier.getAmount(rng), ModdedItemTranslator.operationFor(modifier.getOperation())));
        }
    }

    private static void applyEquipment(IrisEntity irisEntity, LivingEntity living, ServerLevel level, RNG rng) {
        setSlot(living, EquipmentSlot.HEAD, irisEntity.getHelmet(), level, rng);
        setSlot(living, EquipmentSlot.CHEST, irisEntity.getChestplate(), level, rng);
        setSlot(living, EquipmentSlot.LEGS, irisEntity.getLeggings(), level, rng);
        setSlot(living, EquipmentSlot.FEET, irisEntity.getBoots(), level, rng);
        setSlot(living, EquipmentSlot.MAINHAND, irisEntity.getMainHand(), level, rng);
        setSlot(living, EquipmentSlot.OFFHAND, irisEntity.getOffHand(), level, rng);
    }

    private static void setSlot(LivingEntity living, EquipmentSlot slot, IrisLoot loot, ServerLevel level, RNG rng) {
        if (loot == null || rng.i(1, loot.getRarity()) != 1) {
            return;
        }
        ItemStack stack = ModdedItemTranslator.stack(loot, rng, level);
        if (stack != null && !stack.isEmpty()) {
            living.setItemSlot(slot, stack);
        }
    }

    private static void applyBaby(Entity entity) {
        if (entity instanceof AgeableMob ageable) {
            ageable.setBaby(true);
            return;
        }
        if (entity instanceof Zombie zombie) {
            zombie.setBaby(true);
        }
    }

    private static void applyRawCommands(IrisEntity irisEntity, ServerLevel level, int blockX, int blockY, int blockZ) {
        KList<IrisCommand> rawCommands = irisEntity.getRawCommands();
        if (rawCommands.isEmpty()) {
            return;
        }

        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }

        for (IrisCommand command : rawCommands) {
            for (String raw : command.getCommands()) {
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                String prepared = (raw.startsWith("/") ? raw.substring(1) : raw)
                        .replace("{x}", String.valueOf(blockX))
                        .replace("{y}", String.valueOf(blockY))
                        .replace("{z}", String.valueOf(blockZ));
                ModdedServerCommands.dispatch(server, prepared);
            }
        }
    }

    private static EntityType<?> resolveType(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        Identifier id = Identifier.tryParse(normalized.indexOf(':') >= 0 ? normalized : "minecraft:" + normalized);
        if (id == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            if (WARNED_TYPES.add(normalized)) {
                IrisLogging.warn("Iris entity: unknown entity type '" + key + "'; skipping spawn");
            }
            return null;
        }
        return BuiltInRegistries.ENTITY_TYPE.getValue(id);
    }

    private static EntitySpawnReason reasonFor(String reason) {
        if (reason == null || reason.isBlank()) {
            return EntitySpawnReason.NATURAL;
        }
        String value = reason.trim().toUpperCase(Locale.ROOT);
        String mapped = switch (value) {
            case "CHUNK_GEN" -> "CHUNK_GENERATION";
            case "SPAWNER_EGG" -> "SPAWN_EGG";
            case "DISPENSE_EGG" -> "DISPENSER";
            case "BUILD_SNOWMAN", "BUILD_IRONGOLEM", "BUILD_WITHER", "CUSTOM", "DEFAULT" -> "MOB_SUMMONED";
            default -> value;
        };
        try {
            return EntitySpawnReason.valueOf(mapped);
        } catch (IllegalArgumentException ignored) {
            return EntitySpawnReason.NATURAL;
        }
    }

    private static Panda.Gene gene(String value) {
        if (value == null || value.isBlank()) {
            return Panda.Gene.NORMAL;
        }
        try {
            return Panda.Gene.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Panda.Gene.NORMAL;
        }
    }

    private static boolean forcePersist() {
        return IrisSettings.get().getWorld().isForcePersistEntities();
    }

    private static void noteSpawnEffectGap() {
        if (!warnedSpawnEffect) {
            warnedSpawnEffect = true;
            IrisLogging.debug("Iris entity spawnEffect / spawnEffectRiseOutOfGround are Bukkit-only visual paths and are skipped on modded.");
        }
    }

    private static String normalizeName(String name) {
        String source = name == null || name.isBlank() ? "modifier" : name;
        String normalized = source.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
        return normalized.isBlank() ? "modifier" : normalized;
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
}
