package dev.tim1337.lumio.skyblock.dungeon.partyfinder;

import dev.tim1337.lumio.skyblock.dungeon.DungeonClass;

/**
 * What the Party Finder Helper says about a party: whether the player can join it and whether the player's class is still free there. It only
 * tells; nothing is ever clicked or sent. Plain logic, tested without the game.
 */
public final class PartyFinderVerdict {
	/** The color of a party's slot. */
	public enum Mark {
		/** The player can join and nobody plays the player's class. */
		GREEN,
		/** The player can join, but the player's class is taken. */
		YELLOW,
		/** The player cannot join. */
		RED,
		/** No color: the player's own party, or a head that could not be read. */
		NONE
	}

	/** How a party where the player's class is taken counts. */
	public enum Taken {
		YELLOW("Yellow"),
		RED("Red"),
		IGNORE("Ignore");

		private final String label;

		Taken(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	/**
	 * What is known about the player.
	 *
	 * @param name           the player's name
	 * @param selected       the class selected in the menu, or NONE while not known
	 * @param catacombsLevel the player's Catacombs level, or -1 while not known
	 * @param classLevel     the level of the selected class, or -1 while not known
	 */
	public record Me(String name, DungeonClass selected, int catacombsLevel, int classLevel) {
	}

	/**
	 * The verdict.
	 *
	 * @param mark    the slot's color
	 * @param reason  why the player cannot join, for the tooltip; "" otherwise
	 * @param takenBy who plays the player's class; "" when it is free or not known
	 */
	public record Verdict(Mark mark, String reason, String takenBy) {
	}

	private static final Verdict NO_MARK = new Verdict(Mark.NONE, "", "");

	private PartyFinderVerdict() {
	}

	/**
	 * @param party the party, or null for a head that could not be read
	 * @return the verdict. Whether a requirement is met: the game's own red line first; else the player's levels where they are known. A level
	 *         that is not known never makes a party red.
	 */
	public static Verdict of(PartyFinderLore.Party party, Me me, Taken taken) {
		if (party == null || party.has(me.name())) {
			return NO_MARK;
		}
		PartyFinderLore.Member sameClass = me.selected() == DungeonClass.NONE ? null : party.playing(me.selected());
		String takenBy = sameClass == null ? "" : sameClass.name();
		if (!party.refusal().isEmpty()) {
			return new Verdict(Mark.RED, party.refusal(), takenBy);
		}
		if (party.full()) {
			return new Verdict(Mark.RED, "The party is full", takenBy);
		}
		if (party.dungeonLevel() > 0 && me.catacombsLevel() >= 0 && me.catacombsLevel() < party.dungeonLevel()) {
			return new Verdict(Mark.RED, "Needs Catacombs " + party.dungeonLevel() + " (you: " + me.catacombsLevel() + ")", takenBy);
		}
		if (party.classLevel() > 0 && me.classLevel() >= 0 && me.classLevel() < party.classLevel()) {
			return new Verdict(Mark.RED, "Needs class level " + party.classLevel() + " (you: " + me.classLevel() + ")", takenBy);
		}
		if (sameClass != null && taken != Taken.IGNORE) {
			return new Verdict(taken == Taken.RED ? Mark.RED : Mark.YELLOW, taken == Taken.RED ? me.selected().label() + " is taken by " + takenBy : "", takenBy);
		}
		return new Verdict(Mark.GREEN, "", takenBy);
	}
}
