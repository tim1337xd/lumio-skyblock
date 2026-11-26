package dev.tim1337.lumio.skyblock.dungeon;

import dev.tim1337.lumio.api.experimental.filter.VisualFilters;
import dev.tim1337.lumio.skyblock.data.SkullTextures;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The dungeon's rules in Lumio's Visual Filter: clutter of the Catacombs, recognized by item or by the skin of a skull (its texture hash, never a
 * name). The skull hashes of the healer fairy, the Soul Weaver head and the tentacle head are from Odin's data (BSD-3-Clause, credited). Players,
 * the Watcher and the blood mobs are never left out; the rules hide only these skulls and the Archer's bone meal, never keys or secret items.
 */
public final class DungeonVisualRules {
	static final String HEALER_FAIRY = "2eedcffc6a11a3834a28849cc316af7a2752a376d536cf84039cf79108b167ae";
	static final String SOUL_WEAVER = "2f24ed6875304fa4a1f0c785b2cb6a6a72563e9f3e24ea55e18178452119aa66";
	static final String TENTACLE = "37d83674926b89714e6b5a55470501c04b066dd87bf6c335cddc6e60a1a1a5f5";
	/** The skulls of the player's own Soulweaver Gloves; not known yet (/lumio debug skulls finds it), so the rule matches nothing until then. */
	static final String SOULWEAVER_GLOVES = "";
	private static final String SECTION = "Dungeons";

	private DungeonVisualRules() {
	}

	/** Adds the rules; once, while the add-on starts. */
	public static void register() {
		VisualFilters.add("dungeon_bone_meal", "Archer passive bone meal", "The bone meal the Archer's passive scatters", SECTION, true,
			entity -> Dungeon.in() && entity instanceof ItemEntity item && boneMeal(item.getItem()));
		VisualFilters.add("dungeon_healer_fairy", "Healer fairy on mobs", "The fairy some mobs hold", SECTION, true,
			entity -> Dungeon.in() && skull(entity, EquipmentSlot.MAINHAND, HEALER_FAIRY));
		VisualFilters.add("dungeon_soul_weaver", "Soul Weaver heads", "The Soul Weaver head some mobs wear", SECTION, true,
			entity -> Dungeon.in() && skull(entity, EquipmentSlot.HEAD, SOUL_WEAVER));
		VisualFilters.add("dungeon_tentacle", "Tentacle heads", "The tentacle head some mobs wear", SECTION, true,
			entity -> Dungeon.in() && skull(entity, EquipmentSlot.HEAD, TENTACLE));
		VisualFilters.add("soulweaver_gloves", "Your Soulweaver Gloves' skulls", "Everywhere, not only in dungeons", SECTION, true,
			entity -> !SOULWEAVER_GLOVES.isEmpty() && skull(entity, EquipmentSlot.HEAD, SOULWEAVER_GLOVES));
	}

	private static boolean boneMeal(ItemStack stack) {
		return stack.is(Items.BONE_MEAL);
	}

	/** @return whether the entity wears or holds a skull of this skin in that slot, and is nothing that must stay seen */
	private static boolean skull(Entity entity, EquipmentSlot slot, String hash) {
		if (!(entity instanceof LivingEntity living) || neverHidden(entity)) {
			return false;
		}
		ItemStack stack = living.getItemBySlot(slot);
		return stack.is(Items.PLAYER_HEAD) && matches(SkullTextures.of(stack), hash);
	}

	/** @return whether a skull's texture hash is the one a rule looks for */
	static boolean matches(String texture, String hash) {
		return !hash.isEmpty() && hash.equals(texture);
	}

	/** @return whether an entity must never be left out: players, the Watcher and the blood mobs, by the skull they wear */
	static boolean neverHidden(Entity entity) {
		if (entity instanceof Player) {
			return true;
		}
		if (entity instanceof LivingEntity living) {
			ItemStack head = living.getItemBySlot(EquipmentSlot.HEAD);
			if (head.is(Items.PLAYER_HEAD)) {
				String texture = SkullTextures.of(head);
				return BloodMobs.watcher(texture) || BloodMobs.mob(texture);
			}
		}
		return false;
	}
}
