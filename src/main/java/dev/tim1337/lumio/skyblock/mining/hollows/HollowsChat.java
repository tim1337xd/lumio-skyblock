package dev.tim1337.lumio.skyblock.mining.hollows;

import dev.tim1337.lumio.api.experimental.util.ChatCoords;

/**
 * Coordinates in the Crystal Hollows that a player shared in chat ("Divan 512 80 300", "x: 512, y: 80, z: 300", "512, 80, 300"), read with Lumio's
 * chat reader, kept only inside the island, with the place they name when they name one. Reading only. Plain text, so it is unit tested.
 */
final class HollowsChat {
	/** A place shared in chat: where, and which structure it names (null when it names none). */
	record Offer(int x, int y, int z, HollowsStructure structure) {
	}

	private HollowsChat() {
	}

	/**
	 * @param said what the player wrote, after their name
	 * @return the place in the Crystal Hollows the message shares, or null when it shares none or one outside the island
	 */
	static Offer read(String said) {
		ChatCoords.Found found = ChatCoords.inChat(said);
		if (found == null || !HollowsGeometry.inside(found.x(), found.y(), found.z())) {
			return null;
		}
		// the whole message, not only the words right before the numbers: "Divan: 512 80 300" and "Divan is at 512 80 300" both name it
		return new Offer(found.x(), found.y(), found.z(), HollowsStructure.byName(said));
	}

	/** @return the line that shares a place, as the share key and the settings send it: "Mines of Divan: 512, 80, 300" */
	static String shareText(HollowsStructure structure, int x, int y, int z) {
		return structure.title + ": " + x + ", " + y + ", " + z;
	}
}
