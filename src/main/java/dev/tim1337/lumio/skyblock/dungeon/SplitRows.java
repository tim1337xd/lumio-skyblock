package dev.tim1337.lumio.skyblock.dungeon;

import java.util.ArrayList;
import java.util.List;

/**
 * The rows the Splits panel shows, made from a run's splits by the player's choices: without the boss's phases (the rows between "Portal" and
 * the total are left out), or with the F7/M7 "Terminals" row as the four sections of Goldor's phase, S1 to S4. Each row keeps where its best
 * is stored, since the stored bests do not change with these choices. Plain logic, tested without the game.
 */
final class SplitRows {
	/** The sections of Goldor's phase. */
	static final int SECTIONS = 4;
	static final String TERMINALS = "Terminals";
	private static final String LAST_CLEAR_ROW = "Portal";

	/**
	 * The rows as shown.
	 *
	 * @param rows  the rows
	 * @param slots for each row where its best is: the split's place in the run's stored splits (0 and up), or for a section minus its number
	 *              (-1 for S1 to -4 for S4)
	 */
	record Shown(List<DungeonSplits.Row> rows, int[] slots) {
	}

	/**
	 * When the sections of Goldor's phase began, noted as the fight goes on: the start of S1 to S4 and the end of S4, in real time and server
	 * ticks; 0 where not reached yet.
	 */
	static final class Sections {
		final long[] millis = new long[SECTIONS + 1];
		final long[] ticks = new long[SECTIONS + 1];
		private int reached;

		/**
		 * Takes the fight's state of now.
		 *
		 * @param section the section of Goldor's phase, 1 to 4; 0 outside that phase
		 * @param after   whether Goldor's phase is over (the core opened)
		 */
		void see(int section, boolean after, long nowMillis, long nowTick) {
			int now = after ? SECTIONS + 1 : section;
			// a section the lines skipped over begins and ends at once, as a split does
			while (reached < now && reached <= SECTIONS) {
				millis[reached] = nowMillis;
				ticks[reached] = nowTick;
				reached++;
			}
		}

		void reset() {
			java.util.Arrays.fill(millis, 0);
			java.util.Arrays.fill(ticks, 0);
			reached = 0;
		}

		/** @return whether all four sections are over */
		boolean over() {
			return reached > SECTIONS;
		}

		/** @return the time each section took, 0 for one that was not finished: for the bests */
		long[] spent(boolean inTicks) {
			long[] at = inTicks ? ticks : millis;
			long[] spent = new long[SECTIONS];
			for (int i = 0; i < SECTIONS; i++) {
				spent[i] = i + 1 < reached ? at[i + 1] - at[i] : 0;
			}
			return spent;
		}

		private DungeonSplits.Row row(int index, long nowMillis, long nowTick) {
			boolean started = index < reached;
			boolean done = index + 1 < reached;
			long millisSpent = !started ? 0 : (done ? millis[index + 1] : nowMillis) - millis[index];
			long ticksSpent = !started ? 0 : (done ? ticks[index + 1] : nowTick) - ticks[index];
			return new DungeonSplits.Row("S" + (index + 1), millisSpent, ticksSpent, started && !done, done);
		}
	}

	private SplitRows() {
	}

	/**
	 * @param rows       a run's rows, the total last
	 * @param bossPhases whether the boss's phases show
	 * @param sections   the sections of Goldor's phase to show in place of the "Terminals" row, or null to keep that row
	 */
	static Shown shown(List<DungeonSplits.Row> rows, boolean bossPhases, Sections sections, long nowMillis, long nowTick) {
		List<DungeonSplits.Row> out = new ArrayList<>(rows.size() + SECTIONS);
		List<Integer> slots = new ArrayList<>(rows.size() + SECTIONS);
		int total = rows.size() - 1;
		boolean boss = false;
		for (int i = 0; i < rows.size(); i++) {
			DungeonSplits.Row row = rows.get(i);
			if (i == total || !boss || bossPhases) {
				if (boss && sections != null && row.name().equals(TERMINALS)) {
					for (int section = 0; section < SECTIONS; section++) {
						DungeonSplits.Row part = sections.row(section, nowMillis, nowTick);
						// the phase running without a section seen yet: its first section runs
						out.add(section == 0 && row.current() && !part.current() && !part.done()
							? new DungeonSplits.Row(part.name(), row.millis(), row.ticks(), true, false) : part);
						slots.add(-(section + 1));
					}
				} else {
					out.add(row);
					slots.add(i);
				}
			}
			boss |= row.name().equals(LAST_CLEAR_ROW);
		}
		int[] places = new int[slots.size()];
		for (int i = 0; i < places.length; i++) {
			places[i] = slots.get(i);
		}
		return new Shown(out, places);
	}
}
