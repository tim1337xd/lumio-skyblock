package dev.tim1337.lumio.skyblock.hud;

/**
 * The wardrobe's measures: a bar above one window per page, the open page's at full size and the others a little smaller. Plain numbers, so it is unit tested. Everything inside a window is measured at full size; a window is then drawn scaled around
 * its middle, so the math of its cards stays the same at every size.
 */
public final class WardrobeWindows {
	/** How large the windows of the other pages are drawn. */
	public static final float OTHER_SCALE = 0.86f;
	/** How long a window takes to grow or shrink when another page opens. */
	public static final float CHANGE_SECONDS = 0.25f;
	/** How much larger the worn set is drawn, around its middle, without pushing the cards beside it. */
	public static final float WORN_SCALE = 1.06f;
	public static final int EDGE = 4;
	public static final int BAR = 22;
	public static final int BAR_GAP = 6;
	public static final int WINDOW_GAP = 7;
	public static final int PAD = 7;
	public static final int TOP = 5;
	public static final int HEADER = 12;
	/** Between the header and the cards: the worn set's tag stands here. */
	public static final int HEADER_GAP = 10;
	public static final int BOTTOM = 7;
	public static final int CARD_GAP = 5;
	public static final int CARDS = WardrobeMenu.SETS;
	/** How much of the tag stands above the worn card's top edge, and how high it is. */
	public static final int TAG_ABOVE = 5;
	public static final int TAG_HEIGHT = 9;
	private static final int CARD_LEAST_WIDTH = 36;
	private static final int CARD_MOST_WIDTH = 100;
	private static final float CARD_RATIO = 1.3f;
	/** Windows that fit without scrolling where there is room. */
	private static final int WINDOWS_IN_VIEW = 3;

	private WardrobeWindows() {
	}

	/** A card's size and the size of a window at full size. */
	public record Sizes(int cardWidth, int cardHeight, int windowWidth, int windowHeight) {
	}

	/** @return the sizes for a screen: nine cards across, as large as width and height let them be */
	public static Sizes sizes(int screenWidth, int screenHeight, int windows) {
		int byWidth = (screenWidth - 2 * EDGE - 2 * PAD - (CARDS - 1) * CARD_GAP) / CARDS;
		int inView = Math.clamp(windows, 1, WINDOWS_IN_VIEW);
		float room = screenHeight - 2 * EDGE - BAR - BAR_GAP - (inView - 1) * WINDOW_GAP;
		float cardHeight = room / (1f + (inView - 1) * OTHER_SCALE) - chrome();
		int cardWidth = Math.clamp(Math.min(byWidth, (int) (cardHeight / CARD_RATIO)), CARD_LEAST_WIDTH, CARD_MOST_WIDTH);
		int height = Math.round(cardWidth * CARD_RATIO);
		return new Sizes(cardWidth, height, 2 * PAD + CARDS * cardWidth + (CARDS - 1) * CARD_GAP, chrome() + height);
	}

	private static int chrome() {
		return TOP + HEADER + HEADER_GAP + BOTTOM;
	}

	/** @return where the row of cards starts in a window */
	public static int cardsTop() {
		return TOP + HEADER + HEADER_GAP;
	}

	/** @return how far a card starts in a window, by its place in the row */
	public static int cardLeft(int place, int cardWidth) {
		return PAD + place * (cardWidth + CARD_GAP);
	}

	/** @return the bottom of a window's header, inside the window */
	public static int headerBottom() {
		return TOP + HEADER;
	}

	/** @return the top of the worn set's tag, inside the window: it stands on the card's edge, and the card is drawn larger around its middle */
	public static float wornTagTop(int cardHeight) {
		return cardsTop() - cardHeight * (WORN_SCALE - 1f) / 2f - TAG_ABOVE;
	}

	/** @return a window's size as the way from another page's window (0) to the open one (1) goes */
	public static float scale(float openness) {
		return OTHER_SCALE + (1f - OTHER_SCALE) * openness;
	}

	/**
	 * @param step    how far it has come on its even way from 0 to 1
	 * @param opening whether it is growing
	 * @return how open it is seen: both ways quick at first, then settling
	 */
	public static float eased(float step, boolean opening) {
		float rest = opening ? 1f - step : step;
		float out = 1f - rest * rest * rest;
		return opening ? out : 1f - out;
	}

	/** @return the next step on a window's even way, after a moment */
	public static float advance(float step, boolean open, float seconds, boolean animated) {
		if (!animated) {
			return open ? 1f : 0f;
		}
		float moved = seconds / CHANGE_SECONDS;
		return open ? Math.min(1f, step + moved) : Math.max(0f, step - moved);
	}

	/** @return how high all the windows are together, at these scales */
	public static float height(float[] scales, int count, int windowHeight) {
		float sum = 0f;
		for (int i = 0; i < count; i++) {
			sum += windowHeight * scales[i] + (i > 0 ? WINDOW_GAP : 0);
		}
		return sum;
	}

	/** Writes where each window starts: each below the one before as it is drawn now, so the windows below follow one that grows. */
	public static void tops(float[] scales, int count, int windowHeight, float start, float[] out) {
		float y = start;
		for (int i = 0; i < count; i++) {
			out[i] = y;
			y += windowHeight * scales[i] + WINDOW_GAP;
		}
	}

	/** @return where a window starts across: centered, whatever its size */
	public static float left(int screenWidth, int windowWidth, float scale) {
		return (screenWidth - windowWidth * scale) / 2f;
	}

	/** @return the pages that have a window: those with a card to show, in order */
	public static java.util.List<Integer> windowsOf(java.util.List<Integer> cards) {
		java.util.List<Integer> pages = new java.util.ArrayList<>();
		for (int number : cards) {
			int page = WardrobeBook.pageOf(number);
			if (pages.isEmpty() || pages.getLast() != page) {
				pages.add(page);
			}
		}
		return pages;
	}

	/** @return where the whole of it starts: centered by the size it has at rest, so nothing shifts while the windows change */
	public static float start(int screenHeight, float restHeight) {
		return Math.max(EDGE, (screenHeight - BAR - BAR_GAP - restHeight) / 2f);
	}
}
