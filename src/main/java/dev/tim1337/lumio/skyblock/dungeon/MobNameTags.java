package dev.tim1337.lumio.skyblock.dungeon;

import java.util.List;

/**
 * Which floating name tags of the Catacombs the highlights may hide: a mob's tag (its name and health, "Crypt Lurker 18k❤") without the star ✯.
 * Never hidden: starred mobs, mini bosses, bosses, the Watcher and his blood mobs, bats, the Mimic, and anything that is not a mob's health tag
 * (players, NPCs, keys, secrets and other labels). Plain text in, so it is tested without the game.
 */
public final class MobNameTags {
	private static final String STAR = "✯";
	private static final String HEALTH = "❤";
	/** Names that always keep their tag: the bosses, the mini bosses, the Watcher and the mobs he sends, bats and the Mimic. */
	private static final List<String> KEPT = List.of("Watcher", "Bonzo", "Scarf", "The Professor", "Thorn", "Livid", "Sadan", "Maxor", "Storm", "Goldor",
		"Necron", "Wither King", "Shadow Assassin", "Lost Adventurer", "Frozen Adventurer", "Angry Archaeologist", "King Midas", "Bat", "Mimic", "Revoker",
		"Psycho", "Reaper", "Cannibal", "Mute", "Ooze", "Putrid", "Freak", "Leech", "Tear", "Parasite", "Flamer", "Skull", "Mr. Dead", "Vader", "Frost",
		"Walker", "Spirit Bear");

	private MobNameTags() {
	}

	/** @return whether a tag (its text without colors) is the tag of a mob without a star, which may be hidden */
	public static boolean hides(String tag) {
		if (tag == null || !tag.contains(HEALTH) || tag.contains(STAR)) {
			return false;
		}
		for (String kept : KEPT) {
			if (tag.contains(kept)) {
				return false;
			}
		}
		return true;
	}
}
