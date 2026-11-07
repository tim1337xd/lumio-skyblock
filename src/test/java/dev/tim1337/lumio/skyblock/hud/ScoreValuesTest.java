package dev.tim1337.lumio.skyblock.hud;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** A currency line's numbers written short, with the line's colors, also where the server splits a number into parts. */
class ScoreValuesTest {
	private static String shortened(String line) {
		return ScoreValues.shortened(Component.literal(line)).getString();
	}

	@Test
	void thePurseAndItsGain() {
		assertEquals("Purse: 100M", shortened("Purse: 100,081,263"));
		assertEquals("Purse: 100M (+1.5K)", shortened("Purse: 100,081,263 (+1,500)"));
		assertEquals("Piggy: 12.3M (+999)", shortened("Piggy: 12,345,678 (+999)"));
	}

	@Test
	void colorCodesInTheTextStay() {
		assertEquals("Purse: §6100M §e(+1.5K)", shortened("Purse: §6100,081,263 §e(+1,500)"));
	}

	@Test
	void underAThousandStays() {
		Component line = Component.literal("Bits: 999");
		assertSame(line, ScoreValues.shortened(line));
	}

	@Test
	void whatTheServerWroteShortStays() {
		assertEquals("Bank: 1B / 0", shortened("Bank: 1B / 0"));
		assertEquals("Bank: 12.3M / 2.5K", shortened("Bank: 12.3M / 2,500"));
	}

	@Test
	void theLabelIsLeftAlone() {
		assertEquals("Floor 1000: 1K", shortened("Floor 1000: 1,000"));
		assertEquals("No value 1,000", shortened("No value 1,000"));
	}

	@Test
	void aNumberAcrossPartsAndCodes() {
		Component split = Component.literal("Motes: §d12,3").append(Component.literal("§d45,678"));
		assertEquals("Motes: §d12.3M§d", ScoreValues.shortened(split).getString());
	}

	@Test
	void eachPartKeepsItsStyle() {
		Component line = Component.literal("Purse: ").append(Component.literal("100,081,263").withStyle(ChatFormatting.GOLD)).append(Component.literal(" (+1,500)").withStyle(ChatFormatting.YELLOW));
		List<String> parts = new ArrayList<>();
		ScoreValues.shortened(line).visit((Style style, String text) -> {
			parts.add(text + "|" + (style.getColor() == null ? "" : style.getColor().serialize()));
			return Optional.empty();
		}, Style.EMPTY);
		assertEquals(List.of("Purse: |", "100M|gold", " (+1.5K)|yellow"), parts);
	}
}
