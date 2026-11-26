package dev.tim1337.lumio.skyblock.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

/** Pinned: add, groups, order, remove, the moved favorites and the shelves per profile. */
class PinnedItemsTest {
	@Test
	void addMoveReorderRemove() {
		PinnedItems shelf = new PinnedItems();
		assertTrue(shelf.add("HYPERION", PinnedItems.ALL, null));
		assertTrue(shelf.add("ASPECT_OF_THE_VOID", PinnedItems.ALL, null));
		assertTrue(shelf.add("DIVAN_DRILL", PinnedItems.ALL, null));
		assertEquals(List.of("HYPERION", "ASPECT_OF_THE_VOID", "DIVAN_DRILL"), shelf.ids(PinnedItems.ALL));
		// dropped before the first: reordered
		assertTrue(shelf.add("DIVAN_DRILL", PinnedItems.ALL, "HYPERION"));
		assertEquals(List.of("DIVAN_DRILL", "HYPERION", "ASPECT_OF_THE_VOID"), shelf.ids(PinnedItems.ALL));
		String mining = shelf.addGroup("Mining");
		assertEquals("Mining", mining);
		assertTrue(shelf.move("DIVAN_DRILL", mining));
		assertEquals(List.of("DIVAN_DRILL"), shelf.ids(mining));
		assertEquals(1, shelf.count(mining));
		assertEquals(3, shelf.count(PinnedItems.ALL));
		assertEquals(mining, shelf.groupOf("DIVAN_DRILL"));
		assertTrue(shelf.remove("HYPERION"));
		assertFalse(shelf.has("HYPERION"));
		assertFalse(shelf.remove("HYPERION"));
		assertNull(shelf.groupOf("HYPERION"));
	}

	@Test
	void thePinKeyToggles() {
		PinnedItems shelf = new PinnedItems();
		assertTrue(shelf.toggle("HYPERION"));
		assertTrue(shelf.has("HYPERION"));
		assertFalse(shelf.toggle("HYPERION"));
		assertFalse(shelf.has("HYPERION"));
	}

	@Test
	void groupsRenameAndDelete() {
		PinnedItems shelf = new PinnedItems();
		String mining = shelf.addGroup("Mining");
		assertEquals("Mining 2", shelf.addGroup("Mining"));
		shelf.add("DIVAN_DRILL", mining, null);
		assertTrue(shelf.renameGroup(mining, "Ores"));
		assertEquals(List.of("DIVAN_DRILL"), shelf.ids("Ores"));
		assertFalse(shelf.renameGroup("Ores", "Mining 2"));
		assertFalse(shelf.renameGroup("Ores", "  "));
		assertTrue(shelf.deleteGroup("Ores"));
		// its items stay pinned, in All
		assertTrue(shelf.has("DIVAN_DRILL"));
		assertEquals(PinnedItems.ALL, shelf.groupOf("DIVAN_DRILL"));
		// an unknown group takes nothing
		assertFalse(shelf.add("HYPERION", "Nope", null));
		assertEquals("Line", PinnedItems.cleanName("  §cLine\n "));
		assertEquals(PinnedItems.MOST_GROUP_NAME, PinnedItems.cleanName("x".repeat(40)).length());
	}

	@Test
	void savedAndRead() {
		PinnedItems shelf = new PinnedItems();
		String selling = shelf.addGroup("Selling");
		shelf.add("ENCHANTED_DIAMOND", selling, null);
		shelf.add("HYPERION", PinnedItems.ALL, null);
		PinnedItems read = PinnedItems.fromJson(shelf.toJson());
		assertEquals(List.of("ENCHANTED_DIAMOND", "HYPERION"), read.ids(PinnedItems.ALL));
		assertEquals(List.of("ENCHANTED_DIAMOND"), read.ids(selling));
		assertEquals(List.of(selling), read.groups());
	}

	@Test
	void theOldFavoritesMoveInOnceForEveryProfile() {
		PinnedItems.Profiles profiles = new PinnedItems.Profiles();
		profiles.migrate(List.of("HYPERION", "TERMINATOR"));
		PinnedItems first = profiles.of("profile-a");
		assertEquals(List.of("HYPERION", "TERMINATOR"), first.ids(PinnedItems.ALL));
		first.remove("HYPERION");
		// another profile starts with the favorites too, the first keeps its change
		assertEquals(List.of("HYPERION", "TERMINATOR"), profiles.of("profile-b").ids(PinnedItems.ALL));
		assertEquals(List.of("TERMINATOR"), profiles.of("profile-a").ids(PinnedItems.ALL));
		// read back from the file
		PinnedItems.Profiles read = new PinnedItems.Profiles();
		read.load(profiles.toJson());
		assertEquals(List.of("TERMINATOR"), read.of("profile-a").ids(PinnedItems.ALL));
		assertEquals(List.of("HYPERION", "TERMINATOR"), read.of("profile-c").ids(PinnedItems.ALL));
	}

	@Test
	void beforeTheProfileIsNamedTheLastOneShows() {
		PinnedItems.Profiles profiles = new PinnedItems.Profiles();
		profiles.of("profile-a").add("HYPERION", PinnedItems.ALL, null);
		assertTrue(profiles.of(null).has("HYPERION"));
		// pinned before any profile was ever named: it goes with the first one named
		PinnedItems.Profiles fresh = new PinnedItems.Profiles();
		fresh.of(null).add("TERMINATOR", PinnedItems.ALL, null);
		assertTrue(fresh.of("profile-z").has("TERMINATOR"));
	}

	@Test
	void boundedLikeInTheGame() {
		PinnedItems shelf = new PinnedItems();
		for (int i = 0; i < PinnedItems.MOST_PINS + 5; i++) {
			shelf.add("ITEM_" + i, PinnedItems.ALL, null);
		}
		assertEquals(PinnedItems.MOST_PINS, shelf.size());
		for (int i = 0; i < PinnedItems.MOST_GROUPS + 3; i++) {
			shelf.addGroup("G" + i);
		}
		assertEquals(PinnedItems.MOST_GROUPS, shelf.groups().size());
	}
}
