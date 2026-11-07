package dev.tim1337.lumio.skyblock.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rare drop messages split into item, amount and magic find, with recorded messages; every kind of drop is read the same. */
class DropMessageTest {
	@Test
	void aNormalItem() {
		DropMessage drop = DropMessage.parse("RARE DROP! Shard of the Shredded (+312% ✯ Magic Find)");
		assertEquals("Shard of the Shredded", drop.name());
		assertEquals(1, drop.amount());
		assertEquals(312, drop.magicFind());
		assertFalse(drop.book());
	}

	@Test
	void anEnchantedBook() {
		DropMessage drop = DropMessage.parse("VERY RARE DROP! (Mana Steal I) (+437% ✯ Magic Find)");
		assertEquals("Mana Steal I", drop.name());
		assertEquals(437, drop.magicFind());
		assertTrue(drop.book());
		assertEquals("ENCHANTMENT_MANA_STEAL_1", drop.bookId());
		assertEquals("ENCHANTMENT_ULTIMATE_WISE_5", DropMessage.parse("CRAZY RARE DROP! Enchanted Book (Ultimate Wise V)").bookId());
	}

	@Test
	void namesInParenthesesAsTheServerWritesThem() {
		assertEquals("Null Atom", DropMessage.parse("VERY RARE DROP! (Null Atom) (+437% ✯ Magic Find)").name());
		assertEquals("Transmission Tuner", DropMessage.parse("VERY RARE DROP! (Transmission Tuner) (+437% ✯ Magic Find)").name());
		DropMessage poison = DropMessage.parse("RARE DROP! (63x Twilight Arrow Poison) (+437% ✯ Magic Find)");
		assertEquals("Twilight Arrow Poison", poison.name());
		assertEquals(63, poison.amount());
		DropMessage rune = DropMessage.parse("VERY RARE DROP! (◆ End Rune I) (+437% ✯ Magic Find)");
		assertEquals("End Rune I", rune.name());
		assertFalse(rune.book(), "a rune is no book");
		assertEquals("Judgement Core", DropMessage.parse("CRAZY RARE DROP! (Judgement Core)").name());
	}

	@Test
	void everyKindOfDropEndingInDrop() {
		assertEquals("Summoning Eye", DropMessage.parse("PRAY TO RNGESUS DROP! Summoning Eye").name());
		assertEquals("Summoning Eye", DropMessage.parse("RNGESUS INCARNATE DROP! Summoning Eye").name());
		assertTrue(DropMessage.parse("PET DROP! Phoenix").pet());
	}

	@Test
	void aPlayersWordsAreNoDrop() {
		assertEquals(null, DropMessage.parse("Party > [VIP] Bob: RARE DROP! Summoning Eye"));
		assertEquals(null, DropMessage.parse("Bob: rare drop! Summoning Eye"));
	}

	@Test
	void anAmount() {
		DropMessage before = DropMessage.parse("RARE DROP! 2x Enchanted Ender Pearl (+120% ✯ Magic Find)");
		assertEquals(2, before.amount());
		assertEquals("Enchanted Ender Pearl", before.name());
		DropMessage after = DropMessage.parse("RARE DROP! Enchanted Ender Pearl x3");
		assertEquals(3, after.amount());
		assertEquals("Enchanted Ender Pearl", after.name());
	}

	@Test
	void aPetDrop() {
		DropMessage drop = DropMessage.parse("PET DROP! Baby Yeti (+200% ✯ Magic Find)");
		assertEquals("Baby Yeti", drop.name());
		assertEquals(200, drop.magicFind());
	}

	@Test
	void withoutMagicFind() {
		DropMessage drop = DropMessage.parse("CRAZY RARE DROP! Judgement Core");
		assertEquals("Judgement Core", drop.name());
		assertEquals(-1, drop.magicFind());
		assertEquals("Overflux Capacitor", DropMessage.parse("RNGESUS DROP! Overflux Capacitor").name(), "rarer drops are read the same");
	}

	@Test
	void colorCodesInTheMiddle() {
		DropMessage drop = DropMessage.parse("§6§lVERY RARE DROP! §r§7(§9Mana Steal I§7) §b(+§b437% §b✯ Magic Find§b)");
		assertEquals("Mana Steal I", drop.name());
		assertEquals(437, drop.magicFind());
		DropMessage item = DropMessage.parse("§9§lRARE DROP! §r§5Shard §5of the Shredded §r§b(+312% §b✦ Magic Find)");
		assertEquals("Shard of the Shredded", item.name());
		assertEquals(312, item.magicFind(), "any symbol before Magic Find");
	}

	@Test
	void otherMessagesAreNoDrop() {
		assertNull(DropMessage.parse("You dropped your Aspect of the End"));
		assertNull(DropMessage.parse("RARE DROP!"));
		assertNull(DropMessage.parse("[MVP+] Someone: RARE DROP! Fake"));
	}
}
