package dev.tim1337.lumio.skyblock.dungeon.f7;

import dev.tim1337.lumio.skyblock.SkyBlockFeatures;

/**
 * The former "Boss info" module split up, once, for a config from before: its phase display is the Splits panel's boss rows now, its section
 * the Terminal status panel, its timers the Boss timers panel (which keeps Boss info's saved name, so its switch, place and settings carry
 * over by themselves). What is left to decide is in {@link #after}. The features are loaded in no fixed order, so the old switch is noted when
 * the Boss timers load, and applied then and again after the Splits and the Terminal status loaded their own saved values.
 */
public final class BossInfoSplit {
	/** What the modules are after the split. */
	public record After(boolean bossTimers, boolean terminalStatus, boolean splits) {
	}

	/** Whether the old Boss info was on; null when the config is not from before the split (or is a fresh one). */
	private static Boolean bossInfoWasOn;

	private BossInfoSplit() {
	}

	/**
	 * @param bossInfo       whether Boss info was on
	 * @param terminalStatus whether its Terminal status part was on
	 * @param splits         whether the Splits panel was on
	 * @return the modules after: with Boss info on, the Boss timers on, the Terminal status as it was, and the Splits on (they show the phases
	 *         now); with Boss info off, the timers and the Terminal status off (it only showed inside Boss info), the Splits as they were
	 */
	public static After after(boolean bossInfo, boolean terminalStatus, boolean splits) {
		return new After(bossInfo, bossInfo && terminalStatus, bossInfo || splits);
	}

	/** The old Boss info's switch, from the Boss timers' load of a config from before the split. */
	static void old(boolean wasOn) {
		bossInfoWasOn = wasOn;
		apply();
	}

	/** Applies the split to the Splits and the Terminal status; called again after each of them loaded, so the order of loading does not matter. */
	public static void apply() {
		if (bossInfoWasOn == null) {
			return;
		}
		After after = after(bossInfoWasOn, SkyBlockFeatures.DUNGEON_TERMINAL_STATUS.isEnabled(), SkyBlockFeatures.DUNGEON_SPLITS.isEnabled());
		SkyBlockFeatures.DUNGEON_TERMINAL_STATUS.setEnabled(after.terminalStatus());
		SkyBlockFeatures.DUNGEON_SPLITS.setEnabled(after.splits());
	}
}
