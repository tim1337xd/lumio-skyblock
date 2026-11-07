package dev.tim1337.lumio.skyblock.arrows;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The forms Hypixel really writes: from a screenshot of the preview item, and the chat lines from real logs. */
class QuiverTextTest {
	private static final List<String> REAL_LORE = List.of("Damage: +3", "", "Passive: Explosive", "This arrow explodes after 2s or on", "impact.", "", "The explosion deals the arrow's", "damage split to mobs within 2.5 blocks.", "",
		"Stats added when shot!", "RARE ARROW", "", "Arrows Remaining: 2,864", "", "This item is a preview of your", "currently selected arrow.");

	@Test
	void thePreviewItem() {
		assertEquals(new QuiverText.Selected("Explosive Arrow", 2864), QuiverText.ofItem("Explosive Arrow", REAL_LORE));
	}

	@Test
	void anotherItemIsNoArrow() {
		assertNull(QuiverText.ofItem("Aspect of the End", List.of("Damage: +100", "LEGENDARY SWORD")));
		assertNull(QuiverText.ofItem("", REAL_LORE));
	}

	@Test
	void anAbsurdNumberIsNotBelieved() {
		assertNull(QuiverText.ofItem("Flint Arrow", List.of("Arrows Remaining: 9,999,999,999")));
	}

	@Test
	void arrowsLeft() {
		assertEquals(new QuiverText.Message(QuiverText.Kind.LEFT, "Flint Arrow", 50), QuiverText.ofMessage("QUIVER! You only have 50 Flint Arrows left!"));
		assertEquals(new QuiverText.Message(QuiverText.Kind.LEFT, "Explosive Arrow", 10), QuiverText.ofMessage("QUIVER! You only have 10 Explosive Arrows left!"));
	}

	@Test
	void runOut() {
		assertEquals(new QuiverText.Message(QuiverText.Kind.OUT, "Explosive Arrow", 0), QuiverText.ofMessage("QUIVER! You have run out of Explosive Arrows!"));
	}

	@Test
	void forgedByJax() {
		assertEquals(new QuiverText.Message(QuiverText.Kind.FORGED, "Flint Arrow", 2842), QuiverText.ofMessage("Jax forged Flint Arrow x2842 for 14k Coins!"));
		assertEquals(new QuiverText.Message(QuiverText.Kind.FORGED, "Explosive Arrow", 2880), QuiverText.ofMessage("Jax forged Explosive Arrow x2880!"));
	}

	@Test
	void otherMessagesAreNothing() {
		assertNull(QuiverText.ofMessage("RARE DROP! (2x Toxic Arrow Poison) (+200% Magic Find)"));
		assertNull(QuiverText.ofMessage("The Arrow Trap hit you for 12.5 damage!"));
		assertNull(QuiverText.ofMessage("[MVP+] Steve123: QUIVER! You only have 50 Flint Arrows left!"));
		assertNull(QuiverText.ofMessage("QUIVER! Something new about arrows"));
	}
}
