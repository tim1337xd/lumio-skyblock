package dev.tim1337.lumio.skyblock.hud;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What the storage preview shows from what was seen: pages never seen, the tabs and the search. */
class StoragePreviewModelTest {
	private static final List<StoragePreviewModel.Page> PAGES = List.of(
		new StoragePreviewModel.Page("e1", true, List.of("Null Atom", "Summoning Eye")),
		new StoragePreviewModel.Page("e2", false, List.of()),
		new StoragePreviewModel.Page("b1", true, List.of("Enchanted Diamond", "Null Sphere")),
		new StoragePreviewModel.Page("b2", false, List.of()));

	private static List<String> keys(List<StoragePreviewModel.Page> pages) {
		return pages.stream().map(StoragePreviewModel.Page::key).toList();
	}

	@Test
	void pagesNeverSeenAreInTheList() {
		List<String> keys = StoragePreviewModel.keys(List.of("b2", "e3"), 3, 2, true);
		assertEquals(List.of("e1", "e2", "e3", "b1", "b2"), keys);
	}

	@Test
	void withoutThemOnlyWhatIsKnown() {
		assertEquals(List.of("e3", "b2"), StoragePreviewModel.keys(List.of("b2", "e3"), 3, 2, false));
	}

	@Test
	void pagesAreInTheirNumbersOrder() {
		assertEquals(List.of("e2", "e10", "b3", "b12"), StoragePreviewModel.keys(List.of("b12", "e10", "b3", "e2"), 0, 0, true));
	}

	@Test
	void theTabs() {
		assertEquals(List.of("e1", "e2", "b1", "b2"), keys(StoragePreviewModel.shown(PAGES, StoragePreviewModel.ALL, "")));
		assertEquals(List.of("e1", "e2"), keys(StoragePreviewModel.shown(PAGES, StoragePreviewModel.ENDER, "")));
		assertEquals(List.of("b1", "b2"), keys(StoragePreviewModel.shown(PAGES, StoragePreviewModel.BACKPACKS, "")));
	}

	@Test
	void theSearchIsOnWhatWasKept() {
		assertEquals(List.of("e1", "b1"), keys(StoragePreviewModel.shown(PAGES, StoragePreviewModel.ALL, "null")));
		assertEquals(List.of("e1"), keys(StoragePreviewModel.shown(PAGES, StoragePreviewModel.ENDER, "NULL ")));
		assertEquals(List.of(), keys(StoragePreviewModel.shown(PAGES, StoragePreviewModel.ALL, "nothing like this")));
	}

	@Test
	void aPageNeverSeenFindsNothing() {
		assertFalse(StoragePreviewModel.matches(PAGES.get(1), "null"));
		assertTrue(StoragePreviewModel.matches(PAGES.get(1), ""));
		assertEquals(0, StoragePreviewModel.count(PAGES.get(1), "null"));
	}

	@Test
	void howManyMatch() {
		assertEquals(1, StoragePreviewModel.count(PAGES.get(0), "eye"));
		assertEquals(0, StoragePreviewModel.count(PAGES.get(0), ""));
	}

	@Test
	void theTagSaysHowOld() {
		long now = 1_000_000_000L;
		assertEquals("Preview · seen just now", StoragePreviewModel.tag(now - 30_000L, now));
		assertEquals("Preview · seen 12 min ago", StoragePreviewModel.tag(now - 12 * 60_000L, now));
		assertEquals("Preview · seen 3 h ago", StoragePreviewModel.tag(now - 3 * 3_600_000L, now));
		assertEquals("Preview · seen 2 d ago", StoragePreviewModel.tag(now - 2 * 86_400_000L, now));
		assertEquals("Preview · nothing seen yet", StoragePreviewModel.tag(0L, now));
	}

	@Test
	void aCardShowsItemsWhenSeenOrOpenNow() {
		assertTrue(StoragePreviewModel.hasItems(true, false));
		assertTrue(StoragePreviewModel.hasItems(false, true), "open now, also if it was never kept");
		assertFalse(StoragePreviewModel.hasItems(false, false));
	}
}
