package dev.tim1337.lumio.skyblock.dungeon.f7;

/**
 * The timers of the Floor 7 boss, in server ticks, from the moments {@link F7Progress} keeps: Storm's pad (it crushes every second while Storm
 * fights), his lightning (26 seconds after he starts), when the terminals can be done after Storm (3.2 seconds), Goldor's death tick (every three
 * seconds while his phase lasts) and Necron's drop (three seconds after his line). Each is -1 while it does not run.
 */
public final class F7Timers {
	static final int PAD_PERIOD_TICKS = 20;
	static final int LIGHTNING_TICKS = 520;
	static final int TERMINALS_OPEN_TICKS = 64;
	static final int GOLDOR_PERIOD_TICKS = 60;
	static final int NECRON_DROP_TICKS = 60;

	private F7Timers() {
	}

	/** @return the ticks until Storm's pad crushes again, or -1 */
	public static long stormPad(F7Progress progress, long tick) {
		if (progress.phase() != F7Progress.Phase.STORM || progress.stormEnd() > progress.stormStart()) {
			return -1;
		}
		return PAD_PERIOD_TICKS - Math.floorMod(tick - progress.stormStart(), PAD_PERIOD_TICKS);
	}

	/** @return the ticks until Storm's lightning, or -1 */
	public static long lightning(F7Progress progress, long tick) {
		if (progress.phase() != F7Progress.Phase.STORM) {
			return -1;
		}
		return untilOrNone(progress.stormStart() + LIGHTNING_TICKS, tick);
	}

	/** @return the ticks until the terminals can be done after Storm, or -1 */
	public static long terminalsOpen(F7Progress progress, long tick) {
		return progress.stormEnd() == F7Progress.NEVER ? -1 : untilOrNone(progress.stormEnd() + TERMINALS_OPEN_TICKS, tick);
	}

	/** @return the ticks until Goldor's next death tick, or -1 */
	public static long goldorDeath(F7Progress progress, long tick) {
		if (progress.phase() != F7Progress.Phase.GOLDOR) {
			return -1;
		}
		return GOLDOR_PERIOD_TICKS - Math.floorMod(tick - progress.goldorStart(), GOLDOR_PERIOD_TICKS);
	}

	/** @return the ticks until Necron drops, or -1 */
	public static long necronDrop(F7Progress progress, long tick) {
		return progress.necronDrops() == F7Progress.NEVER ? -1 : untilOrNone(progress.necronDrops() + NECRON_DROP_TICKS, tick);
	}

	private static long untilOrNone(long at, long tick) {
		long left = at - tick;
		return left > 0 ? left : -1;
	}
}
