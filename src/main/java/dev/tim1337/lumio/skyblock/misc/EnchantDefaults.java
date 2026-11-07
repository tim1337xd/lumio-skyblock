package dev.tim1337.lumio.skyblock.misc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Locale;
import java.util.Map;

/**
 * The enchant colors a new config starts with, as 0xRRGGBB, each readable on all a tooltip can lie on: at least {@link EnchantPaint#LEAST_CONTRAST} against the lightest of those, {@link EnchantPaint#LIGHTEST_BACKGROUND}.
 * A config that still holds an earlier default gets the new one, once. Plain data, so it is unit tested.
 */
final class EnchantDefaults {
	static final int ULTIMATE = 0xFF55FF;
	static final int PERFECT = 0xFF6A88;
	static final int GREAT = 0xFFAA00;
	static final int GOOD = 0x9191FF;
	static final int POOR = 0xAAAAAA;
	static final int FLOW_A = 0xFF7AD9;
	static final int FLOW_B = 0x7A9CFF;
	static final int FLOW_C = 0x7AFFD0;
	/** What the setting's save holds once a config has had its colors brought up to date. */
	static final String APPLIED = "bright_defaults_applied";
	/** The earlier defaults that were too dark, by the setting's key, as they are saved. */
	private static final Map<String, String> EARLIER = Map.of("perfect", "#FF5577", "good", "#5555FF");
	private static final Map<String, Integer> NOW = Map.of("perfect", PERFECT, "good", GOOD);

	private EnchantDefaults() {
	}

	/** Changes the saved form of the colors: a color still at an earlier default gets the new one; one the player picked stays, and this happens only once. */
	static void bringUpToDate(JsonObject json) {
		if (json.has(APPLIED)) {
			return;
		}
		for (Map.Entry<String, String> earlier : EARLIER.entrySet()) {
			JsonElement saved = json.get(earlier.getKey());
			if (saved != null && saved.isJsonPrimitive() && earlier.getValue().equalsIgnoreCase(saved.getAsString())) {
				json.addProperty(earlier.getKey(), String.format(Locale.ROOT, "#%06X", NOW.get(earlier.getKey())));
			}
		}
	}
}
