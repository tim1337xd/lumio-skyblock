package dev.tim1337.lumio.skyblock.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

class WardrobeMenuTest {
	@Test
	void readsTheTitle() {
		assertEquals(new WardrobeMenu.Page(1, 3), WardrobeMenu.page("(1/3) Armor Sets"));
		assertEquals(new WardrobeMenu.Page(2, 2), WardrobeMenu.page(" (2/2) Armor Sets "));
		assertNull(WardrobeMenu.page("Armor Sets"));
		assertNull(WardrobeMenu.page("(1/2) Equipment Sets"));
		assertNull(WardrobeMenu.page("Ender Chest (1/9)"));
		assertNull(WardrobeMenu.page("(3/2) Armor Sets"));
		assertNull(WardrobeMenu.page("(0/2) Armor Sets"));
		assertNull(WardrobeMenu.page("(123456789123/2) Armor Sets"), "a number from the server stays bounded");
	}

	@Test
	void readsAButton() {
		assertEquals(WardrobeMenu.State.READY, WardrobeMenu.state("Slot 2: Ready"));
		assertEquals(WardrobeMenu.State.EQUIPPED, WardrobeMenu.state("Slot 1: Equipped"));
		assertEquals(WardrobeMenu.State.EMPTY, WardrobeMenu.state("Slot 9: Empty"));
		assertEquals(WardrobeMenu.State.LOCKED, WardrobeMenu.state("Slot 18: Locked"));
		assertEquals(WardrobeMenu.State.UNKNOWN, WardrobeMenu.state("Slot 3: Something new"));
		assertEquals(WardrobeMenu.State.UNKNOWN, WardrobeMenu.state("Close"));
		assertEquals(WardrobeMenu.State.EQUIPPED, WardrobeMenu.state("Slot 4: Equipped", "lime_dye"), "the name goes first");
		assertEquals(WardrobeMenu.State.LOCKED, WardrobeMenu.state("Slot 9", "red_dye"));
		assertEquals(WardrobeMenu.State.EMPTY, WardrobeMenu.state("Slot 9", "gray_dye"));
		assertEquals(WardrobeMenu.State.READY, WardrobeMenu.state("Slot 9", "pink_dye"));
		assertEquals(WardrobeMenu.State.UNKNOWN, WardrobeMenu.state("Close", "barrier"));
		assertEquals(14, WardrobeMenu.number("Slot 14: Ready", 5));
		assertEquals(5, WardrobeMenu.number("Close", 5));
	}

	@Test
	void readsTheNeededRank() {
		assertEquals("VIP", WardrobeMenu.neededRank(List.of("This slot is locked", "Requires VIP rank")));
		assertEquals("VIP+", WardrobeMenu.neededRank(List.of("Requires VIP+")));
		assertEquals("MVP", WardrobeMenu.neededRank(List.of("You need [MVP] to use this")));
		assertEquals("MVP+", WardrobeMenu.neededRank(List.of("Requires MVP+ rank")));
		assertEquals("MVP++", WardrobeMenu.neededRank(List.of("Requires MVP++ rank")));
		assertNull(WardrobeMenu.neededRank(List.of("Locked", "VIPER is not a rank")));
		assertNull(WardrobeMenu.neededRank(List.of()));
	}

	@Test
	void namesASet() {
		assertEquals("Necron", WardrobeMenu.setName(List.of("Necron's Helmet", "Necron's Chestplate", "Necron's Leggings", "Necron's Boots")));
		assertEquals("Shadow Assassin", WardrobeMenu.setName(List.of("Shadow Assassin Helmet", "Shadow Assassin Boots")));
		assertEquals("Storm", WardrobeMenu.setName(List.of("Ancient Storm's Helmet", "Fierce Storm's Boots")), "reforges differ");
		assertEquals("Storm", WardrobeMenu.setName(List.of("✪✪✪ Storm's Helmet ✪", "Storm's Boots ➌")), "stars are no part of the name");
		assertEquals("Lapis", WardrobeMenu.setName(List.of("Lapis Helmet")), "one piece");
		assertEquals("Helmet", WardrobeMenu.setName(List.of("Helmet")), "one word");
		assertEquals("", WardrobeMenu.setName(List.of()));
		assertEquals("Diamond", WardrobeMenu.setName(List.of("Diamond Helmet", "Iron Boots")), "a mixed set keeps the first piece's name");
	}

	@Test
	void summarizesTheHeader() {
		assertEquals("8 of 18 slots used · slot 1 worn", WardrobeMenu.summary(new int[]{8, 0}, new int[]{0, 0}, 1));
		assertEquals("3 of 18 slots used · nothing worn", WardrobeMenu.summary(new int[]{3, -1}, new int[]{0, 0}, 0), "a page not seen counts as empty");
		assertEquals("2 of 5 slots used · slot 2 worn", WardrobeMenu.summary(new int[]{2}, new int[]{4}, 2), "locked slots are not yours");
	}
}
