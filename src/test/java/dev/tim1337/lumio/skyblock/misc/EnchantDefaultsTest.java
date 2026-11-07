package dev.tim1337.lumio.skyblock.misc;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every default enchant color reads on all a tooltip can lie on, chroma stays a rainbow, and only colors still at an old default change, once. */
class EnchantDefaultsTest {
	/** All that lies behind a tooltip's text or the settings' preview, without anything of the world: the frames, and the tints of Dark and Glass. */
	private static final Map<String, Integer> BACKGROUNDS = Map.of(
		"Lumio frame", 0x0E1018,
		"Lumio frame on a white world", 0x24262D,
		"Vanilla frame on a white world", 0x1E0F1E,
		"Dark panel", 0x1C1C1E,
		"Dark panel on a white world", EnchantPaint.LIGHTEST_BACKGROUND,
		"Glass tint", 0x1C1E2A);
	private static final Map<String, Integer> COLORS = Map.of(
		"Ultimate", EnchantDefaults.ULTIMATE,
		"Perfect", EnchantDefaults.PERFECT,
		"Great", EnchantDefaults.GREAT,
		"Good", EnchantDefaults.GOOD,
		"Poor", EnchantDefaults.POOR,
		"First own color", EnchantDefaults.FLOW_A,
		"Second own color", EnchantDefaults.FLOW_B,
		"Third own color", EnchantDefaults.FLOW_C);
	private static final int SAMPLES = 1000;

	@Test
	void everyDefaultReadsOnEveryBackground() {
		COLORS.forEach((color, rgb) -> BACKGROUNDS.forEach((background, under) -> {
			double contrast = EnchantPaint.contrast(rgb, under);
			assertTrue(contrast >= EnchantPaint.LEAST_CONTRAST, color + " on " + background + " is only " + contrast);
		}));
	}

	@Test
	void everyLetterOfChromaReads() {
		for (int i = 0; i < SAMPLES; i++) {
			int letter = EnchantPaint.chroma(i * 0.1f, i % 40);
			for (int under : BACKGROUNDS.values()) {
				assertTrue(EnchantPaint.contrast(letter, under) >= EnchantPaint.LEAST_CONTRAST, Integer.toHexString(letter));
			}
		}
	}

	@Test
	void chromaStaysARainbow() {
		// every part of the circle still leans to its own hue: red, green and blue each come out on top
		boolean[] top = new boolean[3];
		for (int i = 0; i < SAMPLES; i++) {
			int letter = EnchantPaint.chroma(i * 0.01f, 0);
			int red = letter >> 16 & 0xFF;
			int green = letter >> 8 & 0xFF;
			int blue = letter & 0xFF;
			top[0] |= red > green && red > blue;
			top[1] |= green > red && green > blue;
			top[2] |= blue > red && blue > green;
			assertTrue(Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue)) > 80, Integer.toHexString(letter));
		}
		assertTrue(top[0] && top[1] && top[2]);
	}

	@Test
	void theTiersStayEasyToTellApart() {
		int[] tiers = {EnchantDefaults.ULTIMATE, EnchantDefaults.PERFECT, EnchantDefaults.GREAT, EnchantDefaults.GOOD, EnchantDefaults.POOR};
		for (int a = 0; a < tiers.length; a++) {
			for (int b = a + 1; b < tiers.length; b++) {
				assertNotEquals(tiers[a], tiers[b]);
				int apart = Math.abs((tiers[a] >> 16 & 0xFF) - (tiers[b] >> 16 & 0xFF)) + Math.abs((tiers[a] >> 8 & 0xFF) - (tiers[b] >> 8 & 0xFF)) + Math.abs((tiers[a] & 0xFF) - (tiers[b] & 0xFF));
				assertTrue(apart > 100, Integer.toHexString(tiers[a]) + " and " + Integer.toHexString(tiers[b]));
			}
		}
	}

	@Test
	void anOldDefaultGetsTheNewOne() {
		JsonObject json = JsonParser.parseString("{\"perfect\":\"#FF5577\",\"good\":\"#5555FF\",\"great\":\"#FFAA00\"}").getAsJsonObject();
		EnchantDefaults.bringUpToDate(json);
		assertEquals("#FF6A88", json.get("perfect").getAsString());
		assertEquals("#9191FF", json.get("good").getAsString());
		assertEquals("#FFAA00", json.get("great").getAsString());
	}

	@Test
	void aPickedColorStays() {
		JsonObject json = JsonParser.parseString("{\"perfect\":\"#123456\",\"good\":\"#5555FE\"}").getAsJsonObject();
		EnchantDefaults.bringUpToDate(json);
		assertEquals("#123456", json.get("perfect").getAsString());
		assertEquals("#5555FE", json.get("good").getAsString());
	}

	@Test
	void itHappensOnlyOnce() {
		// the old blue picked on purpose after the change stays
		JsonObject json = JsonParser.parseString("{\"good\":\"#5555FF\",\"" + EnchantDefaults.APPLIED + "\":true}").getAsJsonObject();
		EnchantDefaults.bringUpToDate(json);
		assertEquals("#5555FF", json.get("good").getAsString());
	}
}
