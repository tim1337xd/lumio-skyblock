package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;

/**
 * The item window's action bar: Quick craft as a filled button in the menu's accent, with what is missing beside its label (greyed only when it
 * cannot run at all), and on the right the market (AH or Bazaar) and Wiki. Boxes with a small radius. Each button does one thing per click: Quick
 * craft and the market send the one command they always sent, Wiki only opens the game's "Open link?" prompt.
 */
final class ItemWindowActions {
	static final int HEIGHT = 16;
	/** What a click on the bar does. */
	enum Action {
		NONE, CRAFT, MARKET, WIKI
	}

	private static final int GAP = 5;
	private static final int SIDE = 7;
	private static final int ICON = 7;
	private static final int ICON_GAP = 4;
	private static final float TEXT_HALF = 3.5f;
	/** How the filled button lightens under the mouse, how strong its glow is, and the missing part's opacity on it. */
	private static final float HOVER_LIGHT = 0.15f;
	private static final float GLOW = 0.35f;
	private static final float MISSING_OPACITY = 0.8f;
	/** The quiet buttons: their fill (and under the mouse) and their edge; a greyed button's fill. */
	private static final float QUIET_FILL = 0.09f;
	private static final float QUIET_HOVER = 0.16f;
	private static final float QUIET_EDGE = 0.12f;
	private static final float GREYED_FILL = 0.06f;
	private static final String CRAFT = "Quick craft";
	private static final String WIKI = "Wiki";

	private final int[] craft = new int[4];
	private final int[] market = new int[4];
	private final int[] wiki = new int[4];

	/**
	 * @param showCraft whether the item has Quick craft at all (not for a mob or an NPC)
	 * @param canCraft  whether it can run now; greyed otherwise
	 * @param missing   how many ingredients are missing, 0 for none
	 * @param marketOf  the market the item is traded in, NONE for none
	 * @param withWiki  whether there is a wiki page to open
	 */
	void draw(Canvas ui, int left, int width, int y, boolean showCraft, boolean canCraft, int missing, MarketSearch.Market marketOf, boolean withWiki,
		int mouseX, int mouseY) {
		craft[2] = 0;
		market[2] = 0;
		wiki[2] = 0;
		int right = left + width;
		String marketLabel = marketOf == MarketSearch.Market.BAZAAR ? "Bazaar" : "AH";
		// in a narrow window the market and Wiki give up their words first, so Quick craft always keeps its own
		float labelled = (withWiki ? quietWidth(ui, WIKI, true) + GAP : 0) + (marketOf != MarketSearch.Market.NONE ? quietWidth(ui, marketLabel, true) + GAP : 0);
		boolean words = !showCraft || labelled + SIDE + ICON + ICON_GAP + ui.boldWidthScaled(CRAFT, TextScale.SMALL) + SIDE <= width;
		if (withWiki) {
			right = quiet(ui, wiki, WIKI, "page", words, right, y, mouseX, mouseY) - GAP;
		}
		if (marketOf != MarketSearch.Market.NONE) {
			right = quiet(ui, market, marketLabel, "coins", words, right, y, mouseX, mouseY) - GAP;
		}
		if (showCraft) {
			drawCraft(ui, left, right - left, y, canCraft, missing, mouseX, mouseY);
		}
	}

	private void drawCraft(Canvas ui, int left, int room, int y, boolean canRun, int missing, int mouseX, int mouseY) {
		String extra = missing > 0 ? " · " + missing + " missing" : "";
		float labelWidth = ui.boldWidthScaled(CRAFT, TextScale.SMALL);
		float extraWidth = extra.isEmpty() ? 0f : ui.widthScaled(extra, TextScale.SMALL);
		if (SIDE + ICON + ICON_GAP + labelWidth + extraWidth + SIDE > room) {
			extra = "";
			extraWidth = 0f;
		}
		int width = Math.round(SIDE + ICON + ICON_GAP + labelWidth + extraWidth + SIDE);
		if (width > room) {
			return;
		}
		craft[0] = left;
		craft[1] = y;
		craft[2] = width;
		craft[3] = HEIGHT;
		boolean over = PanelKit.inside(craft, mouseX, mouseY);
		int text;
		if (canRun) {
			int fill = over ? Draw.mix(ui.accent(), 0xFFFFFFFF, HOVER_LIGHT) : ui.accent();
			ui.softLight(left + width / 2f, y + HEIGHT / 2f + 2f, width / 2f + 4f, HEIGHT / 2f + 4f, Draw.withOpacity(ui.accent(), GLOW));
			ui.rounded(left, y, width, HEIGHT, Radius.SM, fill);
			text = 0xFFFFFFFF;
		} else {
			ui.rounded(left, y, width, HEIGHT, Radius.SM, Draw.withOpacity(HudTheme.text(), GREYED_FILL));
			text = HudTheme.secondary();
		}
		float middle = y + HEIGHT / 2f;
		ui.icon("pickaxe", left + SIDE, Math.round(middle - ICON / 2f), ICON, text);
		float x = left + SIDE + ICON + ICON_GAP;
		ui.textAtScaledBold(CRAFT, x, middle - TEXT_HALF, TextScale.SMALL, text);
		if (!extra.isEmpty()) {
			ui.textAtScaled(extra, x + labelWidth, middle - TEXT_HALF, TextScale.SMALL, Draw.withOpacity(text, MISSING_OPACITY));
		}
	}

	/** @return a quiet button's width, with its word or as its icon alone */
	private static int quietWidth(Canvas ui, String label, boolean words) {
		return Math.round(words ? SIDE + ICON + ICON_GAP + ui.boldWidthScaled(label, TextScale.SMALL) + SIDE : SIDE + ICON + SIDE);
	}

	/** A quiet button ending at a right edge, with its word or as its icon alone. @return its left edge */
	private static int quiet(Canvas ui, int[] box, String label, String icon, boolean words, int right, int y, int mouseX, int mouseY) {
		int width = quietWidth(ui, label, words);
		box[0] = right - width;
		box[1] = y;
		box[2] = width;
		box[3] = HEIGHT;
		boolean over = PanelKit.inside(box, mouseX, mouseY);
		ui.rounded(box[0], y, width, HEIGHT, Radius.SM, Draw.withOpacity(HudTheme.text(), over ? QUIET_HOVER : QUIET_FILL));
		ui.ring(box[0], y, width, HEIGHT, Radius.SM, 1, Draw.withOpacity(HudTheme.text(), QUIET_EDGE));
		float middle = y + HEIGHT / 2f;
		ui.icon(icon, box[0] + SIDE, Math.round(middle - ICON / 2f), ICON, HudTheme.text());
		if (words) {
			ui.textAtScaledBold(label, box[0] + SIDE + ICON + ICON_GAP, middle - TEXT_HALF, TextScale.SMALL, HudTheme.text());
		}
		return box[0];
	}

	/** @return what a click at a point does */
	Action at(double x, double y) {
		if (PanelKit.inside(craft, x, y)) {
			return Action.CRAFT;
		}
		if (PanelKit.inside(market, x, y)) {
			return Action.MARKET;
		}
		return PanelKit.inside(wiki, x, y) ? Action.WIKI : Action.NONE;
	}

	/** Opens a web address only through the game's own "Open link?" prompt, as links in chat; nothing is fetched. */
	static void openLink(String url) {
		if (url == null || !url.startsWith("https://")) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		//#if 26.3
		//$ try {
		//$ 	ConfirmLinkScreen.confirmLinkNow(client.gui.screen(), java.net.URI.create(url));
		//$ } catch (IllegalArgumentException notALink) {
		//$ 	return;
		//$ }
		//#else
		ConfirmLinkScreen.confirmLinkNow(client.gui.screen(), url);
		//#endif
	}
}
