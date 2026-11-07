package dev.tim1337.lumio.skyblock.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PetNamesTest {
	private static final String GUARDIAN = "Guardian";

	@Test
	void theGuardianWithoutASkin() {
		assertTrue(PetNames.isType("Guardian", GUARDIAN));
		assertTrue(PetNames.isType("[Lvl 100] Guardian", GUARDIAN));
		assertTrue(PetNames.isType("guardian", GUARDIAN));
	}

	@Test
	void theGuardianWithASkin() {
		assertTrue(PetNames.isType("[Lvl 100] Guardian ✦", GUARDIAN), "as the player list writes it");
		assertTrue(PetNames.isType("[Lvl 100] Guardian✦", GUARDIAN));
		assertTrue(PetNames.isType("[324✦] Guardian", GUARDIAN), "with the skin's counter");
		assertTrue(PetNames.isType("⭐ [Lvl 100] Guardian ✦", GUARDIAN), "a favorite");
	}

	@Test
	void levelAndColorCodes() {
		assertTrue(PetNames.isType("§7[Lvl 73] §6Guardian", GUARDIAN));
		assertTrue(PetNames.isType("§7[Lvl 100] §dGuardian §r§f✦", GUARDIAN));
		assertEquals(GUARDIAN, PetNames.type("§l§6Guardian"));
	}

	@Test
	void anythingAfterTheName() {
		assertTrue(PetNames.isType("[Lvl 100] Guardian ✦ (Legendary)", GUARDIAN));
		assertTrue(PetNames.isType("Guardian: 99%", GUARDIAN));
	}

	@Test
	void anotherPet() {
		assertFalse(PetNames.isType("[Lvl 100] Golden Dragon ✦", GUARDIAN));
		assertEquals("Golden Dragon", PetNames.type("[324✦] Golden Dragon"));
		assertFalse(PetNames.isType("Baby Guardian Golem", GUARDIAN), "a name with the word in it is another pet");
	}

	@Test
	void noPetIsNone() {
		assertEquals("", PetNames.type(null));
		assertEquals("", PetNames.type(""));
		assertEquals("", PetNames.type("[Lvl 100] ✦"));
		assertFalse(PetNames.isType(null, GUARDIAN));
		assertFalse(PetNames.isType("", GUARDIAN));
	}
}
