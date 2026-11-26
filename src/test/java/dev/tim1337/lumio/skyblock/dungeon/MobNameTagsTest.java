package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Name tags as the Catacombs show them (without colors): only the tags of mobs without ✯ may be hidden. */
class MobNameTagsTest {
	@Test
	void mobsWithoutAStarAreHidden() {
		for (String tag : new String[] {"Crypt Lurker 18k❤", "Zombie Commander 1.2M❤", "Skeleton Soldier 450k/450k❤", "Crypt Dreadlord 2.4M❤"}) {
			assertTrue(MobNameTags.hides(tag), tag);
		}
	}

	@Test
	void everythingElseStays() {
		for (String tag : new String[] {"✯ Skeleton Master 3M❤", "✯ Withermancer 1.2M❤", "Shadow Assassin 40M❤", "Lost Adventurer 32M❤",
			"Angry Archaeologist 50M❤", "King Midas 55M❤", "Revoker 1.5M❤", "Bonzo 3M❤", "The Watcher", "Bat 100❤", "Mimic 1.2M❤", "Wither Key",
			"Blood Key", "CLICK", "Mort", "Ash", "Secret", "", "[Lv5] Some NPC"}) {
			assertFalse(MobNameTags.hides(tag), tag);
		}
		assertFalse(MobNameTags.hides(null));
	}
}
