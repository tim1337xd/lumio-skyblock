package dev.tim1337.lumio.skyblock.slayer;

import dev.tim1337.lumio.skyblock.misc.CocoonLook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The label's height follows what it shows, and one part always stays on. */
class BossLabelLookTest {
	private static final float CLOSE = 0.001f;

	private static float height(int parts, boolean card) {
		return BossLabelLook.height(parts, card, 5f, 13f, 7f, 9f, 10f, 4f);
	}

	@Test
	void theCardHasARowForEach() {
		assertEquals(5 + 13 + 7 + 9 + 4, height(BossLabelLook.ALL, true), CLOSE);
		assertEquals(5 + 13 + 4, height(BossLabelLook.VALUE, true), CLOSE);
		assertEquals(5 + 9 + 4, height(BossLabelLook.STEPS, true), CLOSE);
		assertEquals(5 + 7 + 4, height(BossLabelLook.BAR, true), CLOSE);
	}

	@Test
	void theOtherStylesHaveOneLine() {
		assertEquals(5 + 10 + 7 + 4, height(BossLabelLook.ALL, false), CLOSE);
		assertEquals(5 + 10 + 4, height(BossLabelLook.COUNT | BossLabelLook.KIND, false), CLOSE);
		assertEquals(5 + 7 + 4, height(BossLabelLook.BAR, false), CLOSE);
	}

	@Test
	void theLastPartStaysOn() {
		assertEquals(BossLabelLook.BAR, CocoonLook.kept(BossLabelLook.BAR, 0));
		assertEquals(BossLabelLook.KIND, CocoonLook.kept(BossLabelLook.BAR, BossLabelLook.KIND));
		assertTrue(CocoonLook.kept(0, 0) != 0);
	}
}
