package dev.tim1337.lumio.skyblock.data;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading a pet's candy and top level from its item data and lore, as Hypixel writes them. */
class PetCandyTest {
	private static final String NAME = "[Lvl 57] Blue Whale";
	private static final String PROGRESS = "Progress to Level 58: 12.3%";

	private static String info(String candy) {
		return "{\"type\":\"BLUE_WHALE\",\"active\":false,\"exp\":10.0,\"tier\":\"LEGENDARY\"" + (candy == null ? "" : ",\"candyUsed\":" + candy) + "}";
	}

	private static PetCandy.Info fromData(String candy) {
		return PetCandy.parse(info(candy), NAME, List.of(PROGRESS));
	}

	private static PetCandy.Info fromLore(String line) {
		return PetCandy.parse(null, NAME, List.of("Strong Swimmer", line, PROGRESS));
	}

	@Test
	void readsTheCandyFromTheItemData() {
		assertEquals(0, fromData("0").candy());
		assertEquals(1, fromData("1").candy());
		assertEquals(10, fromData("10").candy());
		assertEquals("10", fromData("10").label());
	}

	@Test
	void readsTheCandyFromTheLore() {
		assertEquals(0, fromLore("(0/10) Pet Candy Used").candy());
		assertEquals(1, fromLore("(1/10) Pet Candy Used").candy());
		assertEquals(10, fromLore("(10/10) Pet Candy Used").candy());
		assertEquals(7, fromLore("Pet Candy Used: 7").candy());
	}

	@Test
	void missingCandyIsZero() {
		assertEquals(0, fromData(null).candy());
		assertEquals(0, PetCandy.parse(null, NAME, List.of(PROGRESS)).candy());
	}

	@Test
	void theItemDataGoesBeforeTheLore() {
		assertEquals(4, PetCandy.parse(info("4"), NAME, List.of("(9/10) Pet Candy Used")).candy());
		assertEquals(9, PetCandy.parse(info(null), NAME, List.of("(9/10) Pet Candy Used")).candy());
	}

	@Test
	void unbelievableValuesAreLeftOut() {
		assertEquals(0, fromData("-3").candy());
		assertEquals(0, fromData("99999999999999").candy());
		assertEquals(0, fromData("\"ten\"").candy());
		assertEquals(0, PetCandy.parse("{not json", NAME, List.of()).candy());
		assertEquals(0, PetCandy.parse(null, NAME, List.of("(1234/10) Pet Candy Used")).candy());
	}

	@Test
	void onlyPetsCount() {
		assertSame(PetCandy.NONE, PetCandy.parse(null, "Hyperion", List.of("(5/10) Pet Candy Used")));
		assertSame(PetCandy.NONE, PetCandy.parse("{not json", "Hyperion", List.of()));
		assertEquals(5, PetCandy.parse(info("5"), "Blue Whale", List.of()).candy());
	}

	@Test
	void theLoreSaysWhetherThePetIsAtItsTopLevel() {
		assertTrue(PetCandy.parse(info("10"), "[Lvl 100] Blue Whale", List.of("MAX LEVEL")).maxLevel());
		assertFalse(fromData("10").maxLevel());
		// a Golden Dragon at level 100 has a hundred levels to go, whatever it is called
		assertFalse(PetCandy.parse(goldenDragon(), "[Lvl 100] Golden Dragon", List.of("Progress to Level 101: 0%")).maxLevel());
		assertTrue(PetCandy.parse(goldenDragon(), "[Lvl 200] Golden Dragon", List.of("MAX LEVEL")).maxLevel());
	}

	@Test
	void withoutThoseLinesTheLevelDecides() {
		assertTrue(PetCandy.parse(info("3"), "[Lvl 100] Blue Whale", List.of()).maxLevel());
		assertFalse(PetCandy.parse(info("3"), "[Lvl 99] Blue Whale", List.of()).maxLevel());
		assertFalse(PetCandy.parse(goldenDragon(), "[Lvl 100] Golden Dragon", List.of()).maxLevel());
		assertTrue(PetCandy.parse(goldenDragon(), "[Lvl 200] Golden Dragon", List.of()).maxLevel());
		assertFalse(PetCandy.parse(info("3"), "Blue Whale", List.of()).maxLevel());
	}

	private static String goldenDragon() {
		return "{\"type\":\"GOLDEN_DRAGON\",\"candyUsed\":2}";
	}
}
