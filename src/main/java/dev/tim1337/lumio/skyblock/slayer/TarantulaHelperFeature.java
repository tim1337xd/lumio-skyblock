package dev.tim1337.lumio.skyblock.slayer;

import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.ColorChoice;
import dev.tim1337.lumio.api.setting.Toggle;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.util.Sight;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Helpers for the Tarantula Broodfather: its Web of Lies egg sacs get an outline in a color of choice, for the player's own boss and the carried
 * one. The sacs are found by the boss reading ({@link BossReader}); the outline is the vanilla glowing outline, colored by a mixin. A part of a
 * sac is outlined only while the player sees it ({@link Sight}): in view and in line of sight, checked again every few ticks and forgotten as
 * soon as it is out of sight, so the outline never shows a sac through a wall.
 */
public class TarantulaHelperFeature extends AddonFeature {
	/** How often the egg sacs in sight are checked: a raycast per sac part, a few times per second. */
	private static final int SIGHT_CHECK_TICKS = 2;

	private final Toggle highlightEggs = add(make().toggle("highlight_eggs", Component.literal("Highlight egg sacs"), true), "Egg sacs");
	private final ColorChoice eggColor = add(make().color("egg_color", Component.literal("Color of the egg sacs"), SystemColors.RED_RGB), "Egg sacs");
	/** The ids of the sac parts in sight at the last check; made anew at every check, so nothing seen before is kept. */
	private final IntOpenHashSet inSight = new IntOpenHashSet();
	private final List<Entity> parts = new ArrayList<>();
	private int tick;

	{
		eggColor.under(highlightEggs);
	}

	public TarantulaHelperFeature() {
		super("tarantula_helper", "Tarantula helper", "Helpers for the Tarantula Broodfather: its egg sacs outlined, so they are easy to shoot.", false);
		iconRgb(SystemColors.RED_RGB);
		iconName("paw");
		summary("Outlines the egg sacs of your boss");
	}

	public void register() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	/** Checks which egg sac parts the player sees, every few ticks; with no egg sacs up or the outline off, nothing is checked. */
	private void tick(Minecraft client) {
		if (++tick % SIGHT_CHECK_TICKS != 0) {
			return;
		}
		inSight.clear();
		if (!isEnabled() || !highlightEggs.get() || client.player == null || !SlayerBosses.anyEggs()) {
			return;
		}
		parts.clear();
		SlayerBosses.addEggParts(parts);
		for (Entity part : parts) {
			// a stand is an egg sac or a cocoon, never both
			if (!part.isRemoved() && !SkyBlockFeatures.COCOONS.claims(part) && Sight.visible(client, part)) {
				inSight.add(part.getId());
			}
		}
		parts.clear();
	}

	/** @return whether the entity is an outlined egg sac part right now; called per entity and frame, so the cheap checks come first */
	public boolean glows(Entity entity) {
		return !inSight.isEmpty() && isEnabled() && highlightEggs.get() && inSight.contains(entity.getId());
	}

	/** @return whether any egg sac is outlined right now */
	public boolean anyEggGlows() {
		return !inSight.isEmpty() && isEnabled() && highlightEggs.get();
	}

	/** @return the egg sac outline color as 0xRRGGBB */
	public int eggColor() {
		return eggColor.get() & 0xFFFFFF;
	}
}
