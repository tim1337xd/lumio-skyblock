package dev.tim1337.lumio.skyblock.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The heads the player's pets really have: where a head comes from, best first, and the memory per profile and pet. */
class PetHeadsTest {
	private static final int LEGENDARY = 0xFFAA00;
	private static final int EPIC = 0xAA00AA;
	private static final String SKIN = "dark-skin-texture";
	private static final String WORLD = "world-texture";

	@Test
	void thePetsOwnMenuItemComesFirstThenTheRememberedHeadThenTheDefault() {
		assertEquals(PetIcons.OWN_ITEM, PetIcons.headSource(SKIN, WORLD));
		assertEquals(PetIcons.OWN_ITEM, PetIcons.headSource(SKIN, ""));
		assertEquals(PetIcons.REMEMBERED, PetIcons.headSource("", WORLD));
		// never seen: the default of its type, until a head is seen
		assertEquals(PetIcons.DEFAULT_HEAD, PetIcons.headSource("", ""));
	}

	@Test
	void aHeadFromTheMenuIsNotReplacedByOneFromTheWorld() {
		PetHeads heads = new PetHeads();
		assertEquals("", heads.texture("Apple", "Golden Dragon", LEGENDARY));
		assertNull(heads.source("Apple", "Golden Dragon", LEGENDARY));
		// first the pet beside the player
		assertTrue(heads.seen("Apple", "Golden Dragon", LEGENDARY, WORLD, PetHeads.Source.WORLD));
		assertEquals(WORLD, heads.texture("Apple", "Golden Dragon", LEGENDARY));
		// then the Pets menu: better
		assertTrue(heads.seen("Apple", "Golden Dragon", LEGENDARY, SKIN, PetHeads.Source.MENU));
		assertEquals(SKIN, heads.texture("Apple", "Golden Dragon", LEGENDARY));
		assertEquals(PetHeads.Source.MENU, heads.source("Apple", "Golden Dragon", LEGENDARY));
		// the world never takes it back
		assertFalse(heads.seen("Apple", "Golden Dragon", LEGENDARY, WORLD, PetHeads.Source.WORLD));
		assertEquals(SKIN, heads.texture("Apple", "Golden Dragon", LEGENDARY));
		// the menu shows another head later (a skin put on): updated
		assertTrue(heads.seen("Apple", "Golden Dragon", LEGENDARY, "new-skin", PetHeads.Source.MENU));
		assertEquals("new-skin", heads.texture("Apple", "Golden Dragon", LEGENDARY));
	}

	@Test
	void everyPetAndEveryProfileHasItsOwnHead() {
		PetHeads heads = new PetHeads();
		heads.seen("Apple", "Golden Dragon", LEGENDARY, SKIN, PetHeads.Source.MENU);
		heads.seen("Apple", "Ender Dragon", LEGENDARY, "ender", PetHeads.Source.MENU);
		heads.seen("Apple", "Golden Dragon", EPIC, "epic", PetHeads.Source.WORLD);
		heads.seen("Pear", "Golden Dragon", LEGENDARY, "pear", PetHeads.Source.WORLD);
		// autopet switches between them: each has its own
		assertEquals(SKIN, heads.texture("Apple", "Golden Dragon", LEGENDARY));
		assertEquals("ender", heads.texture("Apple", "Ender Dragon", LEGENDARY));
		assertEquals("epic", heads.texture("Apple", "Golden Dragon", EPIC));
		assertEquals("pear", heads.texture("Pear", "Golden Dragon", LEGENDARY));
		// the name's case and spaces around it make no other pet
		assertEquals(SKIN, heads.texture("Apple", " golden dragon ", LEGENDARY));
		// a pet never seen: nothing, so the default shows
		assertEquals("", heads.texture("Apple", "Phoenix", LEGENDARY));
	}

	@Test
	void nothingIsKeptWithoutATexture() {
		PetHeads heads = new PetHeads();
		int before = heads.version();
		assertFalse(heads.seen("Apple", "Golden Dragon", LEGENDARY, "", PetHeads.Source.MENU));
		assertFalse(heads.seen("Apple", "Golden Dragon", LEGENDARY, null, PetHeads.Source.MENU));
		assertFalse(heads.seen("Apple", "", LEGENDARY, SKIN, PetHeads.Source.MENU));
		assertEquals(before, heads.version());
		// the same head seen again changes nothing either, so no flicker and no save
		heads.seen("Apple", "Golden Dragon", LEGENDARY, SKIN, PetHeads.Source.MENU);
		int kept = heads.version();
		assertTrue(heads.seen("Apple", "Golden Dragon", LEGENDARY, SKIN, PetHeads.Source.MENU));
		assertEquals(kept, heads.version());
	}

	@Test
	void theHeadsSurviveARestart() {
		PetHeads heads = new PetHeads();
		heads.seen("Apple", "Golden Dragon", LEGENDARY, SKIN, PetHeads.Source.MENU);
		heads.seen("Apple", "Ender Dragon", LEGENDARY, WORLD, PetHeads.Source.WORLD);
		PetHeads read = new PetHeads();
		read.restore(heads.export());
		assertEquals(SKIN, read.texture("Apple", "Golden Dragon", LEGENDARY));
		assertEquals(PetHeads.Source.MENU, read.source("Apple", "Golden Dragon", LEGENDARY));
		assertEquals(PetHeads.Source.WORLD, read.source("Apple", "Ender Dragon", LEGENDARY));
		// and the order still holds after it
		assertFalse(read.seen("Apple", "Golden Dragon", LEGENDARY, WORLD, PetHeads.Source.WORLD));
		// odd entries are left out
		PetHeads odd = new PetHeads();
		odd.restore(java.util.Map.of("a", new String[] {"x"}, "b", new String[] {"", "MENU"}, "c", new String[] {"x", "ELSEWHERE"}));
		assertTrue(odd.export().isEmpty());
	}
}
