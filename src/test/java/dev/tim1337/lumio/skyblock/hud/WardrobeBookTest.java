package dev.tim1337.lumio.skyblock.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.util.List;

/** What the wardrobe keeps per profile, and what a click on a card sends. */
class WardrobeBookTest {
	private static final List<String> SET = List.of("helmet", "chest", "legs", "boots");
	private static final List<String> NOTHING = List.of("", "", "", "");

	private static WardrobeBook.Slot slot(int number, WardrobeMenu.State state, List<String> pieces) {
		return new WardrobeBook.Slot(number, state, null, pieces, pieces == SET ? "Necron" : "", 0xFF55FF);
	}

	@Test
	void eachProfileHasItsOwnFile() {
		assertEquals("wardrobe-abc.json", WardrobeBook.fileName("abc"));
		assertNotEquals(WardrobeBook.fileName("abc"), WardrobeBook.fileName("def"));
		assertEquals("wardrobe.json", WardrobeBook.fileName(null));
	}

	@Test
	void keepsEachPageAsLastSeen() {
		WardrobeBook book = new WardrobeBook();
		assertTrue(book.see(1, 3, List.of(slot(1, WardrobeMenu.State.READY, SET))));
		assertTrue(book.see(2, 3, List.of(slot(10, WardrobeMenu.State.EMPTY, NOTHING))), "another page joins");
		assertEquals(SET, book.slot(1).pieces(), "the page not open stays as it was seen");
		assertFalse(book.see(1, 3, List.of(slot(1, WardrobeMenu.State.READY, SET))), "nothing new");
		assertNull(book.slot(19), "not seen yet");
		assertEquals(27, book.shown().size(), "every page's slots get a card");
	}

	@Test
	void oneSetIsWorn() {
		WardrobeBook book = new WardrobeBook();
		book.see(1, 2, List.of(slot(1, WardrobeMenu.State.EQUIPPED, SET)));
		assertEquals(1, book.worn());
		book.see(2, 2, List.of(slot(10, WardrobeMenu.State.EQUIPPED, SET)));
		assertEquals(10, book.worn(), "the one worn now");
		assertEquals(WardrobeMenu.State.READY, book.slot(1).state(), "the other is ready again");
	}

	@Test
	void theHeaderCountsWhatIsKnown() {
		WardrobeBook book = new WardrobeBook();
		book.see(1, 2, List.of(slot(1, WardrobeMenu.State.EQUIPPED, SET), slot(2, WardrobeMenu.State.READY, SET), slot(3, WardrobeMenu.State.LOCKED, NOTHING)));
		assertEquals("2 of 17 slots used · slot 1 worn", book.summary());
	}

	@Test
	void aNameIsSavedPerSlotAndAnEmptyOneRemovesIt() {
		WardrobeBook book = new WardrobeBook();
		assertTrue(book.rename(3, "  Dungeons  "));
		assertEquals("Dungeons", book.name(3));
		assertEquals("", book.name(4), "only that slot");
		assertFalse(book.rename(3, "Dungeons"), "nothing new");
		assertTrue(book.rename(3, "   "));
		assertEquals("", book.name(3));
		assertFalse(book.rename(3, ""), "nothing to remove");
		book.rename(5, "A".repeat(40));
		assertEquals(WardrobeBook.NAME_MOST, book.name(5).length());
	}

	@Test
	void favoritesFilterTheCards() {
		WardrobeBook book = new WardrobeBook();
		book.see(1, 2, List.of(slot(1, WardrobeMenu.State.READY, SET)));
		book.toggleFavorite(2);
		book.toggleFavorite(11);
		book.toggleFavorite(3);
		book.toggleFavorite(3);
		assertTrue(book.favorite(2));
		assertFalse(book.favorite(3), "toggled twice");
		assertEquals(18, book.shown().size());
		book.favoritesOnly(true);
		assertEquals(List.of(2, 11), book.shown());
	}

	@Test
	void allOfItComesBackFromTheFile() {
		WardrobeBook book = new WardrobeBook();
		book.see(1, 2, List.of(new WardrobeBook.Slot(1, WardrobeMenu.State.EQUIPPED, null, SET, "Necron", 0xFF55FF), new WardrobeBook.Slot(2, WardrobeMenu.State.LOCKED, "MVP+", NOTHING, "", 0xFFFFFF)));
		book.rename(1, "Dungeons");
		book.toggleFavorite(1);
		book.favoritesOnly(true);
		WardrobeBook back = WardrobeBook.fromJson(JsonParser.parseString(book.toJson().toString()).getAsJsonObject());
		assertEquals(book.slot(1), back.slot(1));
		assertEquals("MVP+", back.slot(2).rank());
		assertEquals("Dungeons", back.name(1));
		assertTrue(back.favorite(1));
		assertTrue(back.favoritesOnly());
		assertEquals(2, back.pages());
	}

	@Test
	void aBrokenFileLeavesOutWhatIsWrong() {
		JsonObject root = JsonParser.parseString("{'pages': 9999, 'favorites_only': 'x', 'slots': [{'number': 1, 'state': 'READY', 'pieces': ['a', 7, {'x': 1}]}, {'number': 900, 'state': 'READY', 'pieces': []}, {'number': 2, 'state': 'NOPE', 'pieces': []}, 'nonsense'], 'names': {'1': 'ok', 'x': 'no', '900': 'no', '2': {}}, 'favorites': [1, 900, 'y']}").getAsJsonObject();
		WardrobeBook book = WardrobeBook.fromJson(root);
		assertEquals(WardrobeBook.PAGES_MOST, book.pages());
		assertEquals(4, book.slot(1).pieces().size(), "always four places");
		assertNull(book.slot(900));
		assertNull(book.slot(2));
		assertEquals("ok", book.name(1));
		assertEquals("", book.name(900));
		assertTrue(book.favorite(1));
		assertFalse(book.favorite(900));
	}

	@Test
	void whatIsKeptNeverOverwritesWhatWasSeenJustNow() {
		WardrobeBook now = new WardrobeBook();
		now.see(1, 2, List.of(slot(1, WardrobeMenu.State.READY, SET)));
		WardrobeBook kept = new WardrobeBook();
		kept.see(1, 2, List.of(slot(1, WardrobeMenu.State.EMPTY, NOTHING), slot(2, WardrobeMenu.State.READY, SET)));
		kept.rename(1, "Old");
		now.adopt(kept);
		assertEquals(SET, now.slot(1).pieces());
		assertEquals(SET, now.slot(2).pieces(), "what this session has not seen comes from the file");
		assertEquals("Old", now.name(1));
	}

	@Test
	void aBrightCardWearsAndAGreyedOneTurnsThePage() {
		assertEquals(WardrobeMenu.CardClick.WEAR, WardrobeMenu.clickFor(3, 1, WardrobeMenu.State.READY));
		assertEquals(WardrobeMenu.CardClick.WEAR, WardrobeMenu.clickFor(3, 1, WardrobeMenu.State.EQUIPPED), "as on the game's button: it takes it off");
		assertEquals(WardrobeMenu.CardClick.NONE, WardrobeMenu.clickFor(3, 1, WardrobeMenu.State.EMPTY));
		assertEquals(WardrobeMenu.CardClick.NONE, WardrobeMenu.clickFor(3, 1, WardrobeMenu.State.LOCKED));
		assertEquals(WardrobeMenu.CardClick.NEXT_PAGE, WardrobeMenu.clickFor(10, 1, WardrobeMenu.State.READY));
		assertEquals(WardrobeMenu.CardClick.NEXT_PAGE, WardrobeMenu.clickFor(19, 1, WardrobeMenu.State.LOCKED), "two pages on: one click, one page");
		assertEquals(WardrobeMenu.CardClick.PREVIOUS_PAGE, WardrobeMenu.clickFor(9, 2, WardrobeMenu.State.READY));
		assertEquals(1, WardrobeBook.pageOf(9));
		assertEquals(2, WardrobeBook.pageOf(10));
	}

	@Test
	void aNameIsNeverCut() {
		// a letter is 10 wide at size 1
		java.util.function.ToDoubleFunction<String> measure = text -> text.length() * 10.0;
		WardrobeMenu.NameFit fits = WardrobeMenu.fitName("Short", 100f, 1f, true, measure);
		assertEquals("Short", fits.first());
		assertEquals("", fits.second());
		assertEquals(1f, fits.scale());
		WardrobeMenu.NameFit two = WardrobeMenu.fitName("Mining Set Two", 100f, 1f, true, measure);
		assertEquals("Mining", two.first());
		assertEquals("Set Two", two.second());
		assertEquals(1f, two.scale(), "two rows first");
		WardrobeMenu.NameFit oneRow = WardrobeMenu.fitName("Mining Set Two", 100f, 1f, false, measure);
		assertEquals("Mining Set Two", oneRow.first(), "a small card has one row");
		assertEquals("", oneRow.second());
		assertEquals(100f, 14 * 10f * oneRow.scale(), 0.01f, "smaller instead");
		WardrobeMenu.NameFit small = WardrobeMenu.fitName("Unbreakablelongword", 100f, 1f, true, measure);
		assertEquals("Unbreakablelongword", small.first(), "all of it");
		assertEquals(100f, small.first().length() * 10f * small.scale(), 0.01f, "smaller, to fit exactly");
	}
}
