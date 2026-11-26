package dev.tim1337.lumio.skyblock.dungeon.f7;

import dev.tim1337.lumio.api.experimental.util.ServerTicks;
import dev.tim1337.lumio.skyblock.dungeon.Dungeon;

/**
 * The Floor 7 boss fight as the server's lines tell it ({@link F7Progress}), read once for everything that shows a part of it: the boss
 * timers, the terminal status, the splits' sections and the device hints. It listens to the run's lines whether any of them is on (a few
 * pattern checks per chat line in a dungeon), so switching one on in the middle of a fight shows the right state.
 */
public final class F7State {
	private static final F7Progress PROGRESS = new F7Progress();
	private static boolean listening;

	private F7State() {
	}

	/** Starts reading the run's lines; once, while the add-on starts. */
	public static void listen() {
		if (listening) {
			return;
		}
		listening = true;
		Dungeon.listen(new Dungeon.Listener() {
			@Override
			public void serverLine(String line) {
				PROGRESS.read(line, ServerTicks.count());
			}

			@Override
			public void left() {
				PROGRESS.reset();
			}
		});
	}

	public static F7Progress progress() {
		return PROGRESS;
	}

	/** @return the phase of the F7 boss, NONE outside the fight */
	public static F7Progress.Phase phase() {
		return PROGRESS.phase();
	}

	/** @return whether the fight is on where the player is: in the boss room of a run, in one of the boss's phases */
	public static boolean inFight() {
		return Dungeon.inBoss() && PROGRESS.phase() != F7Progress.Phase.NONE;
	}
}
