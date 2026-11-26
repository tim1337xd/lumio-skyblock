package dev.tim1337.lumio.skyblock.dungeon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Which player goes in which of the leap overlay's four quadrants (0 top left, 1 top right, 2 bottom left, 3 bottom right). Odin: the cards
 * stand where Odin's leap menu puts them (its behaviour, our own way): each class has a home quadrant, and players without one fill the
 * quadrants left. Class sorts by class, then name; Name by name; Custom by the quadrant the player chose for each class.
 */
public final class LeapOrder {
	/** How the players are placed. */
	public enum Sort {
		ODIN("Odin"), CLASS("Class"), NAME("Name"), CUSTOM("Custom");

		final String label;

		Sort(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	public static final int QUADRANTS = 4;

	private LeapOrder() {
	}

	/**
	 * @param players the run's other players
	 * @param custom  for Custom: the quadrant each class takes, by the class's ordinal
	 * @return the player of each quadrant, null where none
	 */
	public static DungeonPlayer[] place(List<DungeonPlayer> players, Sort sort, int[] custom) {
		DungeonPlayer[] result = new DungeonPlayer[QUADRANTS];
		switch (sort) {
			case ODIN -> placeByHome(players, result);
			case CLASS -> {
				List<DungeonPlayer> sorted = new ArrayList<>(players);
				sorted.sort(Comparator.comparingInt((DungeonPlayer player) -> player.dungeonClass().ordinal()).thenComparing(DungeonPlayer::name,
					String.CASE_INSENSITIVE_ORDER));
				fill(result, sorted);
			}
			case NAME -> {
				List<DungeonPlayer> sorted = new ArrayList<>(players);
				sorted.sort(Comparator.comparing(DungeonPlayer::name, String.CASE_INSENSITIVE_ORDER));
				fill(result, sorted);
			}
			case CUSTOM -> {
				List<DungeonPlayer> left = new ArrayList<>();
				for (DungeonPlayer player : players) {
					int ordinal = player.dungeonClass().ordinal();
					int quadrant = custom != null && ordinal < custom.length ? custom[ordinal] : -1;
					if (quadrant >= 0 && quadrant < QUADRANTS && result[quadrant] == null) {
						result[quadrant] = player;
					} else {
						left.add(player);
					}
				}
				fill(result, left);
			}
		}
		return result;
	}

	/**
	 * Each quadrant goes to the player whose class calls it home, the one with the strongest claim where several do (the first listed when they
	 * claim alike); everyone else then fills the quadrants left, the strongest claims first.
	 */
	private static void placeByHome(List<DungeonPlayer> players, DungeonPlayer[] result) {
		List<DungeonPlayer> unplaced = new ArrayList<>();
		for (int quadrant = 0; quadrant < QUADRANTS; quadrant++) {
			DungeonPlayer claimant = null;
			for (DungeonPlayer player : players) {
				if (player.dungeonClass().homeQuadrant() == quadrant
					&& (claimant == null || player.dungeonClass().claim() < claimant.dungeonClass().claim())) {
					claimant = player;
				}
			}
			result[quadrant] = claimant;
		}
		for (DungeonPlayer player : players) {
			if (!java.util.Arrays.asList(result).contains(player)) {
				unplaced.add(player);
			}
		}
		unplaced.sort(Comparator.comparingInt(player -> player.dungeonClass().claim()));
		fill(result, unplaced);
	}

	/** Puts the players into the empty quadrants, in order. */
	private static void fill(DungeonPlayer[] result, List<DungeonPlayer> players) {
		int next = 0;
		for (int i = 0; i < QUADRANTS && next < players.size(); i++) {
			if (result[i] == null) {
				result[i] = players.get(next++);
			}
		}
	}
}
