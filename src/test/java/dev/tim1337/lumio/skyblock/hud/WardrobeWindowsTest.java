package dev.tim1337.lumio.skyblock.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.tim1337.lumio.skyblock.hud.WardrobeCardLook.Kind;

import java.util.List;

/** The wardrobe's windows and the tones of its cards. */
class WardrobeWindowsTest {
	private static final int[][] SCREENS = {{960, 540}, {1920, 1080}, {480, 270}, {640, 360}, {427, 240}};

	@Test
	void threeWindowsFitOnTheUsualScreens() {
		for (int[] screen : SCREENS) {
			if (screen[1] < 270) {
				continue;
			}
			WardrobeWindows.Sizes sizes = WardrobeWindows.sizes(screen[0], screen[1], 3);
			float[] scales = {1f, WardrobeWindows.OTHER_SCALE, WardrobeWindows.OTHER_SCALE};
			float all = WardrobeWindows.BAR + WardrobeWindows.BAR_GAP + WardrobeWindows.height(scales, 3, sizes.windowHeight());
			assertTrue(all <= screen[1] - 2 * WardrobeWindows.EDGE + 1f, screen[0] + "x" + screen[1] + " needs " + all);
			assertTrue(sizes.windowWidth() <= screen[0] - 2 * WardrobeWindows.EDGE, "and across");
		}
	}

	@Test
	void theSmallestWindowStillHasNineCardsAcross() {
		WardrobeWindows.Sizes sizes = WardrobeWindows.sizes(427, 240, 3);
		assertTrue(sizes.windowWidth() <= 427 - 2 * WardrobeWindows.EDGE, "they fit: " + sizes.windowWidth());
		assertEquals(sizes.windowWidth(), 2 * WardrobeWindows.PAD + 9 * sizes.cardWidth() + 8 * WardrobeWindows.CARD_GAP);
		assertTrue(sizes.cardWidth() >= 36);
	}

	@Test
	void cardsGrowWithTheScreenButNotWithoutEnd() {
		assertTrue(WardrobeWindows.sizes(960, 540, 3).cardWidth() > WardrobeWindows.sizes(480, 270, 3).cardWidth());
		assertEquals(100, WardrobeWindows.sizes(3840, 2160, 3).cardWidth());
		assertEquals(Math.round(100 * 1.3f), WardrobeWindows.sizes(3840, 2160, 3).cardHeight());
	}

	@Test
	void theOtherPagesAreSmaller() {
		assertEquals(0.86f, WardrobeWindows.scale(0f), 0.001f);
		assertEquals(1f, WardrobeWindows.scale(1f), 0.001f);
		assertEquals(0.93f, WardrobeWindows.scale(0.5f), 0.001f);
	}

	@Test
	void windowsAreCenteredAtAnySize() {
		float small = WardrobeWindows.left(960, 800, 0.86f);
		assertEquals(960f / 2f, small + 800 * 0.86f / 2f, 0.01f);
		assertEquals(80f, WardrobeWindows.left(960, 800, 1f), 0.01f);
	}

	@Test
	void changingPagesTakesAQuarterSecondEaseOut() {
		float step = 0f;
		for (int i = 0; i < 5; i++) {
			step = WardrobeWindows.advance(step, true, 0.05f, true);
		}
		assertEquals(1f, step, 0.001f, "250 ms");
		assertEquals(0f, WardrobeWindows.advance(1f, false, 0.25f, true), 0.001f);
		assertEquals(1f, WardrobeWindows.advance(0f, true, 0.001f, false), "without animations at once");
		assertTrue(WardrobeWindows.eased(0.5f, true) > 0.5f, "growing is quick first");
		assertTrue(WardrobeWindows.eased(0.5f, false) < 0.5f, "shrinking too: it has come further than half of its way");
		assertEquals(1f, WardrobeWindows.eased(1f, true), 0.001f);
		assertEquals(0f, WardrobeWindows.eased(0f, false), 0.001f);
	}

	@Test
	void theWindowsBelowFollowAndNothingJumps() {
		float[] tops = new float[3];
		float[] scales = {1f, 0.86f, 0.86f};
		WardrobeWindows.tops(scales, 3, 100, 20f, tops);
		assertEquals(20f, tops[0]);
		assertEquals(20f + 100 + WardrobeWindows.WINDOW_GAP, tops[1]);
		float before = tops[2];
		// page 2 opens while page 1 shrinks: halfway both are of a size, and the third moves by no more than the change
		float[] halfway = {0.93f, 0.93f, 0.86f};
		WardrobeWindows.tops(halfway, 3, 100, 20f, tops);
		assertEquals(before, tops[2], 0.01f, "one grows as much as the other shrinks: the last stays where it is");
		assertEquals(20f + 93 + WardrobeWindows.WINDOW_GAP, tops[1], 0.01f, "the one between moves smoothly");
	}

	@Test
	void theWornTagNeverOverlapsTheHeader() {
		for (int[] screen : SCREENS) {
			WardrobeWindows.Sizes sizes = WardrobeWindows.sizes(screen[0], screen[1], 3);
			assertTrue(WardrobeWindows.wornTagTop(sizes.cardHeight()) >= WardrobeWindows.headerBottom(), screen[0] + "x" + screen[1]);
		}
	}

	@Test
	void favoritesOnlyLeavesOutEmptyWindows() {
		WardrobeBook book = new WardrobeBook();
		book.see(1, 3, List.of());
		book.toggleFavorite(2);
		book.toggleFavorite(20);
		book.favoritesOnly(true);
		assertEquals(List.of(1, 3), WardrobeWindows.windowsOf(book.shown()), "page 2 has no favorite");
		book.favoritesOnly(false);
		assertEquals(List.of(1, 2, 3), WardrobeWindows.windowsOf(book.shown()));
		assertEquals(27, book.shown().size(), "9 cards per page");
	}

	@Test
	void theCardsAreToned() {
		assertEquals(Kind.WORN, WardrobeCardLook.kind(WardrobeMenu.State.EQUIPPED, true, true));
		assertEquals(Kind.WORN, WardrobeCardLook.kind(WardrobeMenu.State.EQUIPPED, true, false), "on any page");
		assertEquals(Kind.OPEN_PAGE, WardrobeCardLook.kind(WardrobeMenu.State.READY, true, true));
		assertEquals(Kind.OTHER_PAGE, WardrobeCardLook.kind(WardrobeMenu.State.READY, true, false));
		assertEquals(Kind.OTHER_PAGE, WardrobeCardLook.kind(null, false, false), "not seen yet");
		assertEquals(Kind.EMPTY, WardrobeCardLook.kind(WardrobeMenu.State.EMPTY, false, true));
		assertEquals(Kind.EMPTY, WardrobeCardLook.kind(WardrobeMenu.State.EMPTY, false, false));
		assertEquals(Kind.LOCKED, WardrobeCardLook.kind(WardrobeMenu.State.LOCKED, false, false));

		WardrobeCardLook open = WardrobeCardLook.of(Kind.OPEN_PAGE, 0f);
		assertEquals(0.55f, open.saturation());
		assertEquals(0.7f, open.brightness());
		WardrobeCardLook other = WardrobeCardLook.of(Kind.OTHER_PAGE, 0f);
		assertEquals(0f, other.saturation(), "fully grey");
		assertEquals(0.4f, other.brightness());
		assertEquals(0.55f, other.opacity());
		WardrobeCardLook worn = WardrobeCardLook.of(Kind.WORN, 0f);
		assertEquals(1f, worn.saturation());
		assertEquals(1f, worn.brightness());
	}

	@Test
	void hoverBringsAnyCardBack() {
		for (Kind kind : Kind.values()) {
			WardrobeCardLook full = WardrobeCardLook.of(kind, 1f);
			assertEquals(1f, full.saturation(), kind.name());
			assertEquals(1f, full.brightness(), kind.name());
			assertEquals(1f, full.opacity(), kind.name());
		}
		assertEquals(0.5f, WardrobeCardLook.of(Kind.OTHER_PAGE, 0.5f).saturation(), 0.001f, "on the way");
	}

	@Test
	void theShaderGetsItsNumbersInTheColor() {
		int color = new WardrobeCardLook(0f, 0.4f, 0.55f, 0f).shaderColor();
		assertEquals(Math.round(0.55f * 255f), color >>> 24);
		assertEquals(0, color >> 16 & 0xFF);
		assertEquals(Math.round(0.4f * 255f), color >> 8 & 0xFF);
		int plain = new WardrobeCardLook(0f, 0.4f, 0.5f, 0f).plainColor();
		assertEquals(Math.round(0.2f * 255f), plain & 0xFF, "premultiplied, as the image is");
	}
}
