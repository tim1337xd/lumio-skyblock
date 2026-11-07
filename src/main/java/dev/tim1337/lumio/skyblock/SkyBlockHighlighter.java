package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.api.EntityHighlighter;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * The SkyBlock outlines: corpses of the Glacite Mineshafts (after the player has seen them), the Tarantula's egg sacs, and the
 * minibosses, the boss and the drops of the own slayer quest (all only while in sight), in this order.
 */
final class SkyBlockHighlighter implements EntityHighlighter {
	private static final EquipmentSlot[] WORN = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

	@Override
	public boolean glows(Entity entity) {
		return (SkyBlockFeatures.CORPSES.glows(entity) || SkyBlockFeatures.TARANTULA_HELPER.glows(entity) || SkyBlockFeatures.MINIBOSSES.glows(entity)) && hasModel(entity);
	}

	/**
	 * @return whether there is anything to outline: no invisible mob or helper ever is, only a stand that is invisible itself but wears what is seen, as an egg sac or a corpse does
	 */
	private static boolean hasModel(Entity entity) {
		if (!entity.isInvisible()) {
			return true;
		}
		if (entity instanceof ArmorStand stand) {
			for (EquipmentSlot slot : WORN) {
				if (!stand.getItemBySlot(slot).isEmpty()) {
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public int outlineColor(Entity entity) {
		int corpse = SkyBlockFeatures.CORPSES.color(entity);
		if (corpse >= 0) {
			return corpse;
		}
		// the egg sacs' own color; it also tells the armor stand model to hide its sticks
		if (SkyBlockFeatures.TARANTULA_HELPER.glows(entity)) {
			return SkyBlockFeatures.TARANTULA_HELPER.eggColor();
		}
		return SkyBlockFeatures.MINIBOSSES.glows(entity) ? SkyBlockFeatures.MINIBOSSES.color(entity) : -1;
	}
}
