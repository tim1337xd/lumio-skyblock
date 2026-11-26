package dev.tim1337.lumio.skyblock.mining.hollows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** Coordinates from chat: both formats, with and without a place name, the island's bounds, and the share line read back. */
class HollowsChatTest {
	@Test
	void plainWithAPlaceName() {
		HollowsChat.Offer offer = HollowsChat.read("Divan 512 80 300");
		assertNotNull(offer);
		assertEquals(HollowsStructure.MINES_OF_DIVAN, offer.structure());
		assertEquals(512, offer.x());
		assertEquals(80, offer.y());
		assertEquals(300, offer.z());
	}

	@Test
	void commasAndLongNames() {
		assertEquals(HollowsStructure.LOST_PRECURSOR_CITY, HollowsChat.read("Lost Precursor City: 700, 120, 700").structure());
		assertEquals(HollowsStructure.KHAZAD_DUM, HollowsChat.read("khazad at 540, 50, 540").structure());
		assertEquals(HollowsStructure.GOBLIN_QUEENS_DEN, HollowsChat.read("goblin queen 300 120 700").structure());
		assertEquals(HollowsStructure.GOBLIN_KING, HollowsChat.read("king 300 120 400").structure());
	}

	@Test
	void labelledFormat() {
		HollowsChat.Offer offer = HollowsChat.read("temple x: 330, y: 110, z: 360");
		assertEquals(HollowsStructure.JUNGLE_TEMPLE, offer.structure());
		assertEquals(330, offer.x());
	}

	@Test
	void withoutANameItIsOnlyOffered() {
		HollowsChat.Offer offer = HollowsChat.read("512 80 300");
		assertNotNull(offer);
		assertNull(offer.structure());
		assertNull(HollowsChat.read("here 512 80 300").structure(), "a word that is no place");
	}

	@Test
	void outsideTheIslandIsNothing() {
		assertNull(HollowsChat.read("Divan 100 80 300"), "west of the island");
		assertNull(HollowsChat.read("Divan 512 20 300"), "below it");
		assertNull(HollowsChat.read("Divan 512 200 300"), "above it");
		assertNull(HollowsChat.read("selling 512 80 300k"), "not coordinates");
		assertNull(HollowsChat.read("no numbers here"));
	}

	@Test
	void theShareLineIsReadBack() {
		String line = HollowsChat.shareText(HollowsStructure.KHAZAD_DUM, 540, 50, 541);
		assertEquals("Khazad-dûm: 540, 50, 541", line);
		HollowsChat.Offer offer = HollowsChat.read(line);
		assertEquals(HollowsStructure.KHAZAD_DUM, offer.structure());
		assertEquals(541, offer.z());
		for (HollowsStructure structure : HollowsStructure.values()) {
			assertEquals(structure, HollowsChat.read(HollowsChat.shareText(structure, 400, 100, 400)).structure(), structure.title);
		}
	}
}
