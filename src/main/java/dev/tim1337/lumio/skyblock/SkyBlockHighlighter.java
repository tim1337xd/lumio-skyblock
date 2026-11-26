package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.api.EntityHighlighter;
import dev.tim1337.lumio.skyblock.dungeon.DungeonHighlightsFeature;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;

/**
 * The SkyBlock outlines: corpses of the Glacite Mineshafts (after the player has seen them), the Tarantula's egg sacs, and the
 * minibosses, the boss and the drops of the own slayer quest (all only while in sight), in this order; and a rare sea creature of someone else nearby,
 * when the fishing alerts are set to mark it (only where it is seen); and the pests of the Garden for the Pest highlight (only where they are seen).
 */
final class SkyBlockHighlighter implements EntityHighlighter {
	private static final EquipmentSlot[] WORN = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
	/** The width of the outline of a sea creature the fishing alerts mark, in screen pixels. */
	private static final float FISHING_OUTLINE_PIXELS = 2f;

	@Override
	public boolean glows(Entity entity) {
		return (SkyBlockFeatures.CORPSES.glows(entity) || SkyBlockFeatures.TARANTULA_HELPER.glows(entity) || SkyBlockFeatures.MINIBOSSES.glows(entity)
			|| SkyBlockFeatures.DUNGEON_HIGHLIGHTS.glows(entity) || SkyBlockFeatures.DUNGEON_THORN.glows(entity) || SkyBlockFeatures.DUNGEON_LIVID.glows(entity)
			|| SkyBlockFeatures.FISHING_ALERTS.glows(entity) || SkyBlockFeatures.PEST_HIGHLIGHT.glows(entity)) && hasModel(entity);
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

	/** The dungeon's outlines are Lumio's own, as wide as the Highlights page sets, never through walls; the others stay the game's glow. */
	@Override
	public float outlineWidth(Entity entity) {
		if (SkyBlockFeatures.PEST_HIGHLIGHT.glows(entity)) {
			// Lumio's own outline: drawn only where the pest is seen, never through walls or crops
			return SkyBlockFeatures.PEST_HIGHLIGHT.lineWidth();
		}
		if (SkyBlockFeatures.FISHING_ALERTS.glows(entity)) {
			// Lumio's own outline: drawn only where the creature is seen, never through walls
			return FISHING_OUTLINE_PIXELS;
		}
		return dungeonOutline(entity) ? SkyBlockFeatures.DUNGEON_HIGHLIGHTS.lineWidth() : 0f;
	}

	@Override
	public float glowWidth(Entity entity) {
		if (SkyBlockFeatures.PEST_HIGHLIGHT.glows(entity)) {
			return SkyBlockFeatures.PEST_HIGHLIGHT.glowPixels();
		}
		return dungeonOutline(entity) && SkyBlockFeatures.DUNGEON_HIGHLIGHTS.starredLook() == DungeonHighlightsFeature.Look.GLOW
			? SkyBlockFeatures.DUNGEON_HIGHLIGHTS.glowPixels() : 0f;
	}

	private static boolean dungeonOutline(Entity entity) {
		return SkyBlockFeatures.DUNGEON_HIGHLIGHTS.glows(entity) || SkyBlockFeatures.DUNGEON_THORN.glows(entity) || SkyBlockFeatures.DUNGEON_LIVID.glows(entity);
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
		if (SkyBlockFeatures.DUNGEON_THORN.glows(entity)) {
			return SkyBlockFeatures.DUNGEON_THORN.color(entity);
		}
		if (SkyBlockFeatures.DUNGEON_LIVID.glows(entity)) {
			return SkyBlockFeatures.DUNGEON_LIVID.color(entity);
		}
		if (SkyBlockFeatures.DUNGEON_HIGHLIGHTS.glows(entity)) {
			return SkyBlockFeatures.DUNGEON_HIGHLIGHTS.color(entity);
		}
		if (SkyBlockFeatures.FISHING_ALERTS.glows(entity)) {
			return SkyBlockFeatures.FISHING_ALERTS.color();
		}
		if (SkyBlockFeatures.PEST_HIGHLIGHT.glows(entity)) {
			return SkyBlockFeatures.PEST_HIGHLIGHT.color();
		}
		return SkyBlockFeatures.MINIBOSSES.glows(entity) ? SkyBlockFeatures.MINIBOSSES.color(entity) : -1;
	}
}
