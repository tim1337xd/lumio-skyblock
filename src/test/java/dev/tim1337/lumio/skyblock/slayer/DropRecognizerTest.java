package dev.tim1337.lumio.skyblock.slayer;

import dev.tim1337.lumio.skyblock.data.DropMessage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Whether an item is a drop, with the chat lines as the server sends them: in parentheses, with an amount, a rune's sign, Magic Find and color codes; before and after the item. */
class DropRecognizerTest {
	private static final long NOW = 1_000_000L;
	private static final double NEAR = 3.0;
	private static final double FAR = 40.0;

	private DropRecognizer recognizer;

	@BeforeEach
	void empty() {
		recognizer = new DropRecognizer();
	}

	private void say(String line, long millis) {
		recognizer.announce(DropMessage.parse(line), millis);
	}

	private DropRecognizer.Verdict judge(String name, String id) {
		return recognizer.judge(name, id, NOW, FAR, 0L);
	}

	@Test
	void theChatLineBeforeTheItem() {
		say("VERY RARE DROP! (Null Atom) (+437% ✯ Magic Find)", NOW - 1_000L);
		assertEquals(DropRecognizer.Verdict.ANNOUNCED, judge("Null Atom", "NULL_ATOM"));
	}

	@Test
	void theChatLineAfterTheItem() {
		say("RARE DROP! Enchanted Ender Pearl (+355% ✯ Magic Find)", NOW + 4_000L);
		assertEquals(DropRecognizer.Verdict.ANNOUNCED, judge("Enchanted Ender Pearl", "ENCHANTED_ENDER_PEARL"));
	}

	@Test
	void tooLongApartIsNoMatch() {
		say("RARE DROP! Enchanted Ender Pearl (+355% ✯ Magic Find)", NOW - DropRecognizer.ANNOUNCED_MILLIS - 1L);
		assertEquals(DropRecognizer.Verdict.NOT_A_DROP, judge("Enchanted Ender Pearl", null));
	}

	@Test
	void colorCodesAnywhere() {
		say("§d§lVERY RARE DROP! §7(§fTransmission Tuner§7) §b(+437% ✯ Magic Find)", NOW);
		assertEquals(DropRecognizer.Verdict.ANNOUNCED, judge("§5Transmission Tuner", null));
	}

	@Test
	void anAmountInTheLineOrOnTheItem() {
		say("RARE DROP! (63x Twilight Arrow Poison) (+437% ✯ Magic Find)", NOW);
		assertEquals(DropRecognizer.Verdict.ANNOUNCED, judge("Twilight Arrow Poison", null));
		assertEquals(DropRecognizer.Verdict.ANNOUNCED, judge("63x Twilight Arrow Poison", null));
		assertEquals(DropRecognizer.Verdict.ANNOUNCED, judge("Twilight Arrow Poison x63", null));
	}

	@Test
	void aRunesSignAndLevel() {
		say("VERY RARE DROP! (◆ End Rune I) (+437% ✯ Magic Find)", NOW);
		assertEquals(DropRecognizer.Verdict.ANNOUNCED, judge("◆ End Rune I", null));
		assertEquals(DropRecognizer.Verdict.ANNOUNCED, judge("End Rune", null));
	}

	@Test
	void aBookIsCalledEnchantedBookOnTheGround() {
		say("VERY RARE DROP! (Mana Steal I) (+437% ✯ Magic Find)", NOW);
		assertEquals(DropRecognizer.Verdict.ANNOUNCED, judge("Enchanted Book", "ENCHANTED_BOOK"));
	}

	@Test
	void theIdWhenTheNameIsNotThereYet() {
		say("CRAZY RARE DROP! (Judgement Core) (+437% ✯ Magic Find)", NOW);
		assertEquals(DropRecognizer.Verdict.ANNOUNCED, judge("", "JUDGEMENT_CORE"));
	}

	@Test
	void anItemWithoutARealNameYetIsNoDrop() {
		say("CRAZY RARE DROP! (Judgement Core) (+437% ✯ Magic Find)", NOW);
		assertEquals(DropRecognizer.Verdict.NOT_A_DROP, judge("Rotten Flesh", null));
	}

	@Test
	void anotherItemIsNoMatch() {
		say("RARE DROP! Summoning Eye (+437% ✯ Magic Find)", NOW);
		assertEquals(DropRecognizer.Verdict.NOT_A_DROP, judge("Enchanted Ender Pearl", null));
		assertEquals(DropRecognizer.Verdict.NOT_A_DROP, judge("Summoning Stone", null));
	}

	@Test
	void aMergedStackIsTheSameItem() {
		say("RARE DROP! 2x Enchanted Ender Pearl (+120% ✯ Magic Find)", NOW);
		assertEquals(DropRecognizer.Verdict.ANNOUNCED, judge("Enchanted Ender Pearl", null));
		assertEquals(DropRecognizer.Verdict.ANNOUNCED, recognizer.judge("Enchanted Ender Pearl", null, NOW + 500L, FAR, 0L), "a line stays for all items of a drop, not only the first");
	}

	@Test
	void aSlayerDropWithoutALineWhereTheBossDied() {
		assertEquals(DropRecognizer.Verdict.BOSS_DROP, recognizer.judge("Null Sphere", null, NOW, NEAR, NOW - 1_000L));
		assertEquals(DropRecognizer.Verdict.BOSS_DROP, recognizer.judge("Null Sphere", null, NOW, NEAR, NOW + 4_000L), "the boss's death can be known a little after");
	}

	@Test
	void theReasonsASlayerDropIsNotOne() {
		assertEquals(DropRecognizer.Verdict.NO_BOSS_DIED, recognizer.judge("Null Sphere", null, NOW, NEAR, 0L));
		assertEquals(DropRecognizer.Verdict.TOO_FAR, recognizer.judge("Null Sphere", null, NOW, FAR, NOW));
		assertEquals(DropRecognizer.Verdict.OTHER_TIME, recognizer.judge("Null Sphere", null, NOW, NEAR, NOW - 60_000L));
		assertFalse(DropRecognizer.Verdict.TOO_FAR.drop);
		assertTrue(DropRecognizer.Verdict.BOSS_DROP.drop);
	}

	@Test
	void aSlayerDropsNameHasItsSignAndLevel() {
		assertTrue(BossDrops.onList("◆ End Rune I"));
		assertTrue(BossDrops.onList("Null Atom"));
		assertTrue(BossDrops.onList("3x Null Sphere"));
		assertFalse(BossDrops.onList("Rotten Flesh"));
	}

	@Test
	void anAnnouncementChangesWhatWasDecided() {
		int before = recognizer.changes();
		say("RARE DROP! Summoning Eye", NOW);
		assertTrue(recognizer.changes() != before);
	}

	@Test
	void theLinesAreBounded() {
		for (int i = 0; i < 100; i++) {
			say("RARE DROP! Item " + (char) ('a' + i / 26) + (char) ('a' + i % 26), NOW);
		}
		assertEquals(DropRecognizer.Verdict.ANNOUNCED, judge("Item dv", null));
		assertEquals(DropRecognizer.Verdict.NOT_A_DROP, judge("Item aa", null));
	}
}
