package dev.tim1337.lumio.skyblock.dragons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DragonFormatsTest {
	private static final double EXACT = 0.001;

	@Test
	void readsTheEyes() {
		assertTrue(DragonFormats.eyePlaced("☬ You placed a Summoning Eye! (2/8)"));
		assertTrue(DragonFormats.eyePlaced("☬ You placed a Summoning Eye! Brace yourselves! (8/8)"));
		assertFalse(DragonFormats.eyePlaced("☬ Steve123 placed a Summoning Eye! (3/8)"), "another player's eye costs nothing");
		assertTrue(DragonFormats.eyeRecovered("You recovered a Summoning Eye!"));
		assertFalse(DragonFormats.eyeRecovered("Steve123 recovered a Summoning Eye!"));
	}

	@Test
	void readsTheFight() {
		assertEquals("Old", DragonFormats.spawned("☬ The Old Dragon has spawned!"));
		assertEquals("Superior", DragonFormats.spawned("☬ The Superior Dragon has spawned!"));
		assertNull(DragonFormats.spawned("☬ The Dragon Egg has spawned!"));
		assertEquals("Protector", DragonFormats.down("                      PROTECTOR DRAGON DOWN!"));
		assertNull(DragonFormats.down("                    ENDSTONE PROTECTOR DOWN!"));
	}

	@Test
	void readsThePlace() {
		assertEquals(new DragonFormats.Standing(5, 88_966.0), DragonFormats.standing("                   Your Damage: 88,966 (Position #5)"));
		assertEquals(1, DragonFormats.standing("                 Your Damage: 3,198,068 (NEW RECORD!) (Position #1)").place());
		assertNull(DragonFormats.standing("Your Damage: 12"));
		assertEquals(9_659_033.0, DragonFormats.topDamage("             1st Damager - [VIP] Steve123 - 9,659,033"), EXACT);
		assertEquals(-1.0, DragonFormats.topDamage("             2nd Damager - [VIP] Steve123 - 9,659,033"), EXACT);
	}

	@Test
	void worksOutTheWeight() {
		assertEquals(200 + 300 + 100, DragonFormats.weight(3, 1, 1000.0, 1000.0));
		assertEquals(150 + 200 + 50, DragonFormats.weight(2, 3, 1000.0, 500.0));
		assertEquals(10, DragonFormats.weight(0, 8, 1000.0, 0.0), "no damage, no place");
		assertEquals(70, DragonFormats.placeWeight(40));
	}

	@Test
	void everyKindHasItsColor() {
		assertEquals(0xFFAA00, DragonFormats.rgb("Superior"));
		assertEquals(0xCCCCCC, DragonFormats.rgb("Something new"));
	}
}
