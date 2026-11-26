package dev.tim1337.lumio.skyblock.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.api.TooltipLine;

import org.junit.jupiter.api.Test;

import java.util.List;

/** The item search's hover: what it adds under an item's real tooltip, and its choices. */
class ItemSearchHoverTest {
	@Test
	void theAddedLinesAreThePriceAndTheKeysNeverWhatThePlayerHas() {
		List<TooltipLine> lines = ItemSearchHover.lines("Lowest BIN", "1.2M", List.of("Click Details", "R Recipe", "U Uses"));
		assertEquals(2, lines.size(), "no \"You have\" line");
		assertEquals(TooltipLine.Kind.ROW, lines.get(0).kind());
		assertEquals("Lowest BIN", lines.get(0).text());
		assertEquals("1.2M", lines.get(0).value());
		assertEquals(TooltipLine.Kind.FOOTNOTE, lines.get(1).kind());
		assertEquals("Click Details · R Recipe · U Uses", lines.get(1).text());
	}

	@Test
	void whatIsNotKnownIsLeftOut() {
		List<TooltipLine> lines = ItemSearchHover.lines("Bazaar", "", List.of("Click Details"));
		assertEquals(1, lines.size());
		assertEquals(TooltipLine.Kind.FOOTNOTE, lines.get(0).kind());
		assertTrue(ItemSearchHover.lines("Bazaar", "", List.of()).isEmpty());
	}

	@Test
	void onlyLinesAreAddedNeverGameLines() {
		// the provider gets the block's own list: everything in it is one of Lumio's line kinds, a row or a footnote
		for (TooltipLine line : ItemSearchHover.lines("Bazaar", "12", List.of("Click Details"))) {
			assertTrue(line.kind() == TooltipLine.Kind.ROW || line.kind() == TooltipLine.Kind.FOOTNOTE);
		}
	}

	@Test
	void theItemTooltipIsTheFirstChoice() {
		assertEquals(ItemSearchHover.OnHover.TOOLTIP, ItemSearchHover.OnHover.values()[0]);
		assertEquals(3, ItemSearchHover.OnHover.values().length);
	}

	/** Six units a letter, as a stand-in for the font. */
	private static double width(String text) {
		return text.length() * 6.0;
	}

	@Test
	void aDetailsTitleShrinksOrWrapsBeforeItIsCut() {
		ItemDetailView.Title fits = ItemDetailView.title("Aspect of the End", 200f, ItemSearchHoverTest::width);
		assertEquals(1f, fits.scale());
		assertEquals(null, fits.second());
		// 22 letters are 132 wide: a little smaller they fit 120
		ItemDetailView.Title smaller = ItemDetailView.title("Aspect of the Dragons!", 120f, ItemSearchHoverTest::width);
		assertEquals(ItemDetailView.TITLE_SHRINK, smaller.scale());
		assertEquals(null, smaller.second());
		// too long for one row: two rows, broken where they are most alike, nothing lost
		ItemDetailView.Title two = ItemDetailView.title("Titanium Drill DR-X655 with a Sapphire Engine", 160f, ItemSearchHoverTest::width);
		assertEquals("Titanium Drill DR-X655", two.first());
		assertEquals("with a Sapphire Engine", two.second());
		assertTrue(width(two.first()) * two.scale() <= 160f && width(two.second()) * two.scale() <= 160f);
		// one long word cannot be broken
		assertEquals(null, ItemDetailView.title("Supercalifragilisticexpialidocious", 60f, ItemSearchHoverTest::width).second());
	}
}
