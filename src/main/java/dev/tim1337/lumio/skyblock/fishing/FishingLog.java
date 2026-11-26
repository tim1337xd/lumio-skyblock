package dev.tim1337.lumio.skyblock.fishing;

import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;

import net.minecraft.client.Minecraft;

/**
 * For the debug log: the game's own lines around a catch, as the server sent them (they are read from the packet, before another mod can
 * rewrite what the chat shows). Every line that names a sea creature of the list, and every other line of the server while the player's hook
 * is out or was a moment ago, so a creature the list lacks shows up with its line. Costs nothing while nothing listens.
 */
public final class FishingLog {
	/** A line this long after the hook was last seen out still belongs to the catch. */
	static final long AFTER_HOOK_MILLIS = 3000L;

	private static long hookOutAt;

	private FishingLog() {
	}

	/** A line of the server, without colors. */
	public static void onServerLine(String text) {
		if (!DebugHooks.ACTIVE || text.isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		if (client.player != null && client.player.fishing != null) {
			hookOutAt = now;
		}
		SeaCreatures.Creature known = SeaCreatures.caughtIn(text);
		if (!logged(known != null, now - hookOutAt)) {
			return;
		}
		if (DebugHooks.ACTIVE) {
			DebugHooks.log(SkyBlockDebug.FISHING, "Line while fishing ({}): {}", known == null ? "no creature of the list" : known.name(), text);
		}
	}

	/** @return whether a line goes into the log: one of a listed creature always, any other only around a cast */
	static boolean logged(boolean known, long sinceHookMillis) {
		return known || sinceHookMillis >= 0 && sinceHookMillis <= AFTER_HOOK_MILLIS;
	}
}
