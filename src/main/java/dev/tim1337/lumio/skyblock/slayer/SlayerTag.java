package dev.tim1337.lumio.skyblock.slayer;

import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** A name tag line in the world and the entity carrying it: on an armor stand, on the mob itself, or one line of a text display. */
record SlayerTag(Entity entity, String text) {
	/** @return every name tag line of the entities */
	static List<SlayerTag> of(List<Entity> entities) {
		List<SlayerTag> tags = new ArrayList<>();
		for (Entity entity : entities) {
			String text = null;
			if (entity instanceof Display.TextDisplay display) {
				text = display.getText().getString();
			} else if (entity.hasCustomName()) {
				text = entity.getCustomName().getString();
			}
			if (text == null || text.isBlank()) {
				continue;
			}
			for (String line : text.split("\n")) {
				if (!line.isBlank()) {
					tags.add(new SlayerTag(entity, line));
				}
			}
		}
		return tags;
	}

	/** @return the first tag within range of an entity whose text passes the test, or null */
	static SlayerTag near(List<SlayerTag> tags, Entity to, double range, Predicate<String> test) {
		for (SlayerTag tag : tags) {
			if (tag.entity().distanceTo(to) <= range && test.test(tag.text())) {
				return tag;
			}
		}
		return null;
	}
}
