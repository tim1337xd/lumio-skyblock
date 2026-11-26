package dev.tim1337.lumio.skyblock.dungeon;

import dev.tim1337.lumio.skyblock.hud.ScreenRoom;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;
import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.data.SkyBlockPrices;
import dev.tim1337.lumio.skyblock.data.StorageTracker;
import dev.tim1337.lumio.skyblock.mixin.accessor.ContainerScreenAccess;

import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * Chest profit for the dungeon reward chests: inside a chest, its card (its items, books by their enchantment, Contents, Chest cost, Kismet / key and
 * the Profit); in the list of a run's chests, all chests by profit (green when they gain, red when they lose), each chest not opened yet
 * boxed in green behind its item, with ✓ and the Kismet Feather on the slots. Opened and Kismet come from the game's own lines and from the player's clicks, kept
 * per run. Only reads the menus; every click stays the player's.
 */
public class DungeonChestsFeature extends AddonFeature {
	/** Which prices the contents are worth. */
	public enum Prices {
		BUY_ORDER("Buy order", SkyBlockPrices.Mode.BUY_ORDER), INSTANT("Instant", SkyBlockPrices.Mode.SELL_OFFER);

		final String label;
		final SkyBlockPrices.Mode mode;

		Prices(String label, SkyBlockPrices.Mode mode) {
			this.label = label;
			this.mode = mode;
		}
	}

	/** Which side of the menu the breakdown is on; the list of all chests is on the other. */
	public enum Side {
		RIGHT("Right"), LEFT("Left");

		final String label;

		Side(String label) {
			this.label = label;
		}
	}

	private static final String OPEN_BUTTON = "Open Reward Chest";
	private static final String REROLL_BUTTON = "Reroll Chest";
	private static final String FEATHER = "Kismet Feather";
	private static final int MOST_DEBUGGED = 64;
	private final java.util.Set<String> debugged = new java.util.HashSet<>();
	private static final String CONTENTS = "Contents";
	private static final String COST = "Cost";
	/** The panels beside the menu: their gap to it, the list's and the least card width, padding, the heights of the head, a row, the divider and the
	 *  profit, the gap between a name and its value, the shade of an opened chest and the fade-in. */
	private static final int GAP = 8;
	private static final int LIST_WIDTH = 128;
	private static final int CARD_WIDTH = 150;
	private static final int PANEL_PAD = 7;
	private static final int HEAD_ROW = 13;
	private static final int ITEM_ROW = 10;
	private static final int DIVIDER = 5;
	private static final int PROFIT_ROW = 12;
	private static final int COLUMN_GAP = 10;
	private static final float OPENED_SHADE = 0.45f;
	private static final long FADE_MILLIS = 150L;
	/** A slot's box and the badges in its corners. */
	private static final int SLOT_BOX = 18;
	private static final int BADGE = 8;
	/** The feather in the list's rows, as tall as a row's text. */
	private static final int FEATHER_SIZE = 9;
	/** The game's own feather item, drawn small as the Kismet Feather's mark; made on first use, since items have no components yet while the
	 *  add-on starts. */
	private static ItemStack featherItem;
	private static final int ITEM_PIXELS = 16;
	private static final String NO_PRICE = "no price";
	private static final int LOSS = 0xFFFF453A;
	/** A chest whose price is not known: grey. */
	private static final int UNKNOWN = 0xFF8E8E93;
	/** How dark an opened chest's slot is, and the legend under the list: its row, its squares and the room it needs under the panel. */
	private static final float OPENED_DIM = 0.5f;
	private static final int LEGEND_ROW = 11;
	private static final int LEGEND_SQUARE = 5;
	private static final int LEGEND_GAP = 8;
	private static final String[] LEGEND = {"profit", "loss", "no price"};
	private static final int MOST_COLORS = 256;
	private static final java.util.regex.Pattern COUNTED_LINE = java.util.regex.Pattern.compile("^(.+?) x[\\d,]{1,6}$");
	private static final int GAIN = 0xFF34C759;
	/** The switch of the best-chest line that was in Croesus' tooltip; dropped from configs from before. */
	static final String BEST_IN_TOOLTIP_KEY = "best_in_tooltip";
	/** A run or a chest not opened yet: how strong its green fill and border are (the same in Croesus' list and in a run's chests). */
	private static final float UNOPENED_FILL = 0.22f;
	private static final float UNOPENED_BORDER = 0.9f;
	private static final java.util.regex.Pattern COUNTED = java.util.regex.Pattern.compile(".* x\\d+$");

	private final Choice<Prices> prices = add(make().choice("prices", Component.literal("Prices"), Prices.BUY_ORDER, value -> Component.literal(value.label)),
		"General", "Which bazaar price the items are valued at");
	private final Choice<Side> side = add(make().choice("breakdown", Component.literal("Breakdown"), Side.RIGHT, value -> Component.literal(value.label)),
		"General", "Where the item list shows");

	/** The chests seen this run, by kind, in their order. */
	private final Map<String, ChestValue.Chest> seen = new LinkedHashMap<>();
	/** Croesus' chests of the open menu, by slot, read when it changes. */
	private final Map<Integer, ChestValue.Chest> listed = new LinkedHashMap<>();
	private int listedFor = -1;
	/** The runs of Croesus' list per profile, with the best profit seen and the marks of each; saved with the settings. */
	private final CroesusRuns croesus = new CroesusRuns(StorageTracker::profile, LumioApi::saveSoon);
	/** The run items of the open list page: slot, floor and whether unopened, read when the menu changes. */
	private final List<int[]> runSlots = new ArrayList<>();
	private final List<String> runFloors = new ArrayList<>();
	private int runsFor = -1;
	/** The open chest's menu, read when it changes. */
	private ChestValue.Chest openChest;
	/** The colors of the items' names, by name, as the game shows them; the chests opened and where the Kismet Feather was used, in this run. */
	private final Map<String, Integer> itemColors = new java.util.HashMap<>();
	private final java.util.Set<String> opened = new java.util.HashSet<>();
	/** Books without a price, logged once each (Debug). */
	private final java.util.Set<String> unpricedBooks = new java.util.HashSet<>();
	private static final int MOST_LOGGED_BOOKS = 64;
	private String kismetKind;
	private boolean kismetUsed;
	private AbstractContainerScreen<?> fadeScreen;
	private long fadeSince;
	private int openFor = -1;

	public DungeonChestsFeature() {
		super("dungeon_chests", "Chest profit", "What each reward chest is worth.", false);
		iconRgb(SystemColors.GREEN_RGB);
		iconName("chest");
		summary("Profit by color, unopened runs green");
		Dungeon.listen(new Dungeon.Listener() {
			@Override
			public void serverLine(String line) {
				if (isEnabled() && DungeonParse.TEAM_SCORE.matcher(line).find() && Dungeon.floor() != null) {
					// the run is over: Croesus lists it in front now, and the marks of its chests here are kept for it
					croesus.runEnded(Dungeon.floor().name());
				}
			}

			@Override
			public void left() {
				seen.clear();
				opened.clear();
				kismetKind = null;
				kismetUsed = false;
				croesus.closed();
			}
		});
	}

	/**
	 * Draws over a chest's menu or Croesus' list of a run's chests. Inside one chest: only that chest's card. In the list of a run's chests: only the
	 * list of all chests, the opened ones marked and dimmed, with ✓ and the Kismet Feather on the chests' slots (the green of the chests not opened
	 * yet lies behind the items, {@link #drawBehind}).
	 */
	public void draw(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!isEnabled()) {
			return;
		}
		String title = TextColors.noColors(screen.getTitle().getString()).strip();
		int page = CroesusRuns.page(title);
		if (page > 0) {
			drawRunList(screen, graphics, page);
			return;
		}
		if (screen != fadeScreen) {
			fadeScreen = screen;
			fadeSince = System.currentTimeMillis();
		}
		String kind = ChestValue.kindOf(title);
		ContainerScreenAccess access = (ContainerScreenAccess) screen;
		int left = access.evskyblock$left();
		int top = access.evskyblock$top();
		int right = left + access.evskyblock$width();
		boolean onRight = side.get() == Side.RIGHT;
		Canvas ui = Draw.hudCanvas(graphics, 0xFF000000 | MainSettings.accentRgb(), 0f, true);
		float alphaBefore = Draw.alpha();
		Draw.setAlpha(alphaBefore * Math.min(1f, (System.currentTimeMillis() - fadeSince) / (float) FADE_MILLIS));
		try {
			if (kind != null) {
				int stateId = screen.getMenu().getStateId();
				if (stateId != openFor || openChest == null || !openChest.kind().equals(kind)) {
					openFor = stateId;
					openChest = readOpenChest(screen, kind);
					if (openChest != null) {
						seen.put(kind, openChest);
					}
				}
				if (openChest != null) {
					int width = cardWidth(ui, openChest);
					drawCard(ui, openChest, onRight ? right + GAP : left - GAP - width, top, width);
				}
				return;
			}
			readList(screen);
			if (listed.isEmpty()) {
				return;
			}
			markSlots(ui, graphics, screen, access);
			int width = LIST_WIDTH;
			drawList(ui, graphics, onRight ? right + GAP : left - GAP - width, top, width, screen.height);
		} finally {
			Draw.setAlpha(alphaBefore);
		}
	}

	/** The marks on the chests' slots: an opened chest with ✓, the Kismet Feather's chest with a feather. */
	private void markSlots(Canvas ui, GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, ContainerScreenAccess access) {
		for (Map.Entry<Integer, ChestValue.Chest> entry : listed.entrySet()) {
			Slot slot = screen.getMenu().slots.get(entry.getKey());
			int x = access.evskyblock$left() + slot.x - 1;
			int y = access.evskyblock$top() + slot.y - 1;
			String kind = entry.getValue().kind();
			if (opened.contains(kind)) {
				// an opened chest: dimmed, no colored border, the small check mark
				ui.roundedSmooth(x, y, SLOT_BOX, SLOT_BOX, Radius.XXS, Draw.withOpacity(0xFF000000, OPENED_DIM));
				ui.roundedSmooth(x + SLOT_BOX - BADGE, y, BADGE, BADGE, Radius.XXS, Draw.withOpacity(0xFF000000, 0.6f));
				ui.icon("check", x + SLOT_BOX - BADGE + 1, y + 1, BADGE - 2, HudTheme.secondary());
			}
			if (kind.equals(kismetKind)) {
				ui.roundedSmooth(x, y, BADGE, BADGE, Radius.XXS, Draw.withOpacity(0xFF000000, 0.6f));
				feather(graphics, x, y, BADGE);
			}
		}
	}

	/** Draws the game's feather item, scaled down to this size, with its top left here. */
	private static void feather(GuiGraphicsExtractor graphics, float x, float y, int size) {
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(size / (float) ITEM_PIXELS);
		if (featherItem == null) {
			featherItem = new ItemStack(Items.FEATHER);
		}
		graphics.item(featherItem, 0, 0);
		graphics.pose().popMatrix();
	}

	/** @return the width a chest's card needs: its widest row, never less than the usual width */
	private int cardWidth(Canvas ui, ChestValue.Chest chest) {
		float widest = 0;
		for (ChestValue.Item item : chest.items()) {
			String value = item.priced() ? ChestValue.coins(item.value()).substring(1) : NO_PRICE;
			String count = item.count() > 1 ? " ×" + item.count() : "";
			widest = Math.max(widest, ui.widthScaled(ChestMarks.shownName(item.name()) + count, TextScale.SMALL) + ui.widthScaled(value, TextScale.SMALL));
		}
		return Math.max(CARD_WIDTH, Math.round(widest) + 2 * PANEL_PAD + COLUMN_GAP);
	}

	/** One chest's card: its items (each in its rarity's color, the count dim, the value right), then Contents, Chest cost, Kismet / key and the Profit. */
	private void drawCard(Canvas ui, ChestValue.Chest chest, int x, int y, int width) {
		int rows = chest.items().size();
		int height = PANEL_PAD + HEAD_ROW + rows * ITEM_ROW + DIVIDER + 2 * ITEM_ROW + ITEM_ROW + PROFIT_ROW + PANEL_PAD;
		// the item search's panels step aside for it
		ScreenRoom.take(x, y, width, height);
		HudTheme.panel(ui, x, y, width, height, Radius.SM, HudTheme.opacity());
		int dim = HudTheme.secondary();
		ui.textAtScaledBold(chest.kind().toUpperCase(java.util.Locale.ROOT) + " CHEST", x + PANEL_PAD, y + PANEL_PAD, TextScale.SMALL, dim);
		float row = y + PANEL_PAD + HEAD_ROW;
		float valueRight = x + width - PANEL_PAD;
		for (ChestValue.Item item : chest.items()) {
			String name = ChestMarks.shownName(item.name());
			ui.textAtScaled(name, x + PANEL_PAD, row, TextScale.SMALL, 0xFF000000 | (ChestBooks.isBook(item.id()) ? ChestBooks.color(item.id()) : itemColors.getOrDefault(item.name(), 0xE5E5EA)));
			if (item.count() > 1) {
				ui.textAtScaled(" ×" + item.count(), x + PANEL_PAD + ui.widthScaled(name, TextScale.SMALL), row, TextScale.SMALL, dim);
			}
			String value = item.priced() ? ChestValue.coins(item.value()).substring(1) : NO_PRICE;
			TabularText.drawRight(ui, value, valueRight, row, TextScale.SMALL, true, item.priced() ? HudTheme.text() : dim);
			row += ITEM_ROW;
		}
		ui.fill(x + PANEL_PAD, Math.round(row) + 1, width - 2 * PANEL_PAD, 1, HudTheme.line(0.2f));
		row += DIVIDER;
		cardLine(ui, "Contents", ChestValue.coins(chest.contents()).substring(1), x + PANEL_PAD, valueRight, row, HudTheme.text());
		row += ITEM_ROW;
		cardLine(ui, "Chest cost", ChestValue.coins(-chest.cost()), x + PANEL_PAD, valueRight, row, HudTheme.text());
		row += ITEM_ROW;
		cardLine(ui, "Kismet / key", chest.key() ? "key" : chest.kind().equals(kismetKind) ? "Kismet" : "—", x + PANEL_PAD, valueRight, row, dim);
		row += ITEM_ROW + 2;
		ui.textAtScaledBold("Profit", x + PANEL_PAD, row, TextScale.BODY, HudTheme.text());
		TabularText.drawRight(ui, ChestValue.coins(chest.profit()), valueRight, row, TextScale.BODY, true, chest.profit() >= 0 ? GAIN : LOSS);
	}

	private static void cardLine(Canvas ui, String label, String value, float x, float right, float y, int valueColor) {
		ui.textAtScaled(label, x, y, TextScale.SMALL, HudTheme.secondary());
		TabularText.drawRight(ui, value, right, y, TextScale.SMALL, true, valueColor);
	}

	/** All chests of the run by profit: each with its color, its profit, ✓ when opened (dimmed) and the feather where the Kismet Feather was used. */
	private void drawList(Canvas ui, GuiGraphicsExtractor graphics, int x, int y, int width, int screenHeight) {
		List<Map.Entry<Integer, ChestValue.Chest>> rows = new ArrayList<>(listed.entrySet());
		rows.sort((a, b) -> Double.compare(b.getValue().profit(), a.getValue().profit()));
		int listHeight = PANEL_PAD + HEAD_ROW + rows.size() * ITEM_ROW + PANEL_PAD;
		// the legend of the borders' colors: only where it has room on the screen and in the panel's width
		boolean legend = y + listHeight + LEGEND_ROW <= screenHeight - PANEL_PAD && legendWidth(ui) <= width - 2 * PANEL_PAD;
		int height = listHeight + (legend ? LEGEND_ROW : 0);
		ScreenRoom.take(x, y, width, height);
		HudTheme.panel(ui, x, y, width, height, Radius.SM, HudTheme.opacity());
		int dim = HudTheme.secondary();
		ui.textAtScaledBold("ALL CHESTS", x + PANEL_PAD, y + PANEL_PAD, TextScale.SMALL, dim);
		if (kismetUsed && kismetKind == null) {
			// the run's Kismet Feather is used, but not on a chest Lumio saw: marked for the run
			feather(graphics, x + width - PANEL_PAD - FEATHER_SIZE, y + PANEL_PAD - 1, FEATHER_SIZE);
		}
		float row = y + PANEL_PAD + HEAD_ROW;
		for (Map.Entry<Integer, ChestValue.Chest> entry : rows) {
			ChestValue.Chest chest = entry.getValue();
			boolean isOpened = opened.contains(chest.kind());
			float shade = isOpened ? OPENED_SHADE : 1f;
			ui.roundedSmooth(x + PANEL_PAD, row + 1.5f, 5, 5, Radius.XXS, Draw.withOpacity(0xFF000000 | kindColor(chest.kind()), shade));
			ui.textAtScaled(chest.kind(), x + PANEL_PAD + 9, row, TextScale.SMALL, Draw.withOpacity(HudTheme.text(), shade));
			float markX = x + PANEL_PAD + 9 + ui.widthScaled(chest.kind() + " ", TextScale.SMALL);
			if (isOpened) {
				ui.textAtScaled("✓", markX, row, TextScale.SMALL, dim);
				markX += ui.widthScaled("✓ ", TextScale.SMALL);
			}
			if (chest.kind().equals(kismetKind)) {
				feather(graphics, markX, row - 1, FEATHER_SIZE);
			}
			int color = isOpened ? dim : colorOf(verdict(chest));
			TabularText.drawRight(ui, ChestValue.coins(chest.profit()), x + width - PANEL_PAD, row, TextScale.SMALL, true, color);
			row += ITEM_ROW;
		}
		if (legend) {
			float at = x + PANEL_PAD;
			int[] colors = {GAIN, LOSS, UNKNOWN};
			for (int i = 0; i < LEGEND.length; i++) {
				ui.ring(Math.round(at), Math.round(row) + 3, LEGEND_SQUARE, LEGEND_SQUARE, Radius.XXS, 1, colors[i]);
				at += LEGEND_SQUARE + 3;
				ui.textAtScaled(LEGEND[i], at, row + 1.5f, TextScale.SMALL, dim);
				at += ui.widthScaled(LEGEND[i], TextScale.SMALL) + LEGEND_GAP;
			}
		}
	}

	private static float legendWidth(Canvas ui) {
		float width = 0;
		for (String word : LEGEND) {
			width += LEGEND_SQUARE + 3 + ui.widthScaled(word, TextScale.SMALL) + LEGEND_GAP;
		}
		return width - LEGEND_GAP;
	}

	/** @return what a chest's border and its number say: profit, loss, unknown, or opened */
	private ChestMarks.Verdict verdict(ChestValue.Chest chest) {
		int unpriced = 0;
		for (ChestValue.Item item : chest.items()) {
			if (!item.priced()) {
				unpriced++;
			}
		}
		return ChestMarks.verdict(opened.contains(chest.kind()), chest.profit(), chest.items().size(), unpriced);
	}

	private static int colorOf(ChestMarks.Verdict verdict) {
		return switch (verdict) {
			case PROFIT -> GAIN;
			case LOSS -> LOSS;
			default -> UNKNOWN;
		};
	}

	/**
	 * Croesus' list of runs: the run under the mouse is noted (its chests open next).
	 */
	private void drawRunList(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int page) {
		readRuns(screen);
		Slot hovered = ((ContainerScreenAccess) screen).evskyblock$hoveredSlot();
		for (int i = 0; i < runSlots.size(); i++) {
			int[] run = runSlots.get(i);
			if (hovered != null && hovered.index == run[0]) {
				croesus.hover(page, run[0], runFloors.get(i), System.currentTimeMillis(), runMarks(hovered.getItem()));
			}
		}
	}

	/**
	 * Behind the items, so the item stays on top: in Croesus' run list a run whose chests the player has not opened yet gets a soft green fill and a
	 * thin green border (green means "not opened yet" there). In a run's list of chests the border follows the profit instead, as all of them are
	 * unopened at first: green for a profit, red for a loss, grey where the price is not known; an opened chest gets none.
	 */
	public void drawBehind(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics) {
		if (!isEnabled()) {
			return;
		}
		String title = TextColors.noColors(screen.getTitle().getString()).strip();
		ContainerScreenAccess access = (ContainerScreenAccess) screen;
		Canvas ui = null;
		if (CroesusRuns.page(title) > 0) {
			readRuns(screen);
			for (int[] run : runSlots) {
				if (run[1] != 0) {
					ui = box(ui, graphics, screen, access, run[0], GAIN);
				}
			}
			return;
		}
		if (ChestValue.kindOf(title) != null) {
			return;
		}
		readList(screen);
		for (Map.Entry<Integer, ChestValue.Chest> entry : listed.entrySet()) {
			// a run's chests: the border follows the profit (green, red, grey without a price); an opened one has none
			ChestMarks.Verdict verdict = verdict(entry.getValue());
			if (verdict != ChestMarks.Verdict.OPENED) {
				ui = box(ui, graphics, screen, access, entry.getKey(), colorOf(verdict));
			}
		}
	}

	/** Boxes a slot in a color, a soft fill and a thin border; @return the canvas, made on the first box */
	private static Canvas box(Canvas canvas, GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen, ContainerScreenAccess access,
		int slotIndex, int color) {
		Canvas ui = canvas != null ? canvas : Draw.hudCanvas(graphics, 0xFF000000 | MainSettings.accentRgb(), 0f, false);
		Slot slot = screen.getMenu().slots.get(slotIndex);
		int x = access.evskyblock$left() + slot.x - 1;
		int y = access.evskyblock$top() + slot.y - 1;
		ui.roundedSmooth(x, y, SLOT_BOX, SLOT_BOX, Radius.XXS, Draw.withOpacity(color, UNOPENED_FILL));
		ui.ring(x, y, SLOT_BOX, SLOT_BOX, Radius.XXS, 1, Draw.withOpacity(color, UNOPENED_BORDER));
		return ui;
	}
	/** The run items of the open page of Croesus' list, read again only when the menu changes. */
	private void readRuns(AbstractContainerScreen<?> screen) {
		int stateId = screen.getMenu().getStateId();
		if (stateId == runsFor) {
			return;
		}
		runsFor = stateId;
		runSlots.clear();
		runFloors.clear();
		List<CroesusRuns.Listed> listedRuns = new ArrayList<>();
		int page = CroesusRuns.page(TextColors.noColors(screen.getTitle().getString()).strip());
		List<Slot> slots = screen.getMenu().slots;
		int chestSlots = Math.max(0, slots.size() - 36);
		for (int i = 0; i < chestSlots; i++) {
			ItemStack stack = slots.get(i).getItem();
			if (stack.isEmpty()) {
				continue;
			}
			List<String> lines = lore(stack);
			String floor = CroesusRuns.floorOf(TextColors.noColors(stack.getHoverName().getString()).strip(), lines);
			if (floor != null) {
				// not opened yet, as the run's lore says; where it says neither, by what Lumio kept of the run
				runSlots.add(new int[] {i, CroesusRuns.notOpened(lines, croesus.marksAt(page, i, floor)) ? 1 : 0});
				runFloors.add(floor);
				int index = CroesusRuns.index(page, i);
				if (index >= 0) {
					listedRuns.add(new CroesusRuns.Listed(index, floor, runMarks(stack)));
				}
			}
		}
		// the kept runs lined up with the list; a page with fewer runs than it holds is the last one, and the runs after it are gone (a page
		// read before its items arrived shows none and changes nothing)
		if (!listedRuns.isEmpty()) {
			croesus.sync(page, listedRuns, listedRuns.size() < CroesusRuns.RUNS_PER_PAGE);
		}
	}

	private static ChestValue.Chest best(java.util.Collection<ChestValue.Chest> chests) {
		ChestValue.Chest best = null;
		for (ChestValue.Chest chest : chests) {
			if (best == null || chest.profit() > best.profit()) {
				best = chest;
			}
		}
		return best;
	}

	/** Writes to the debug log once per menu title why a chest's value is not shown. */
	private void debugOnce(String title, String step) {
		if (DebugHooks.ACTIVE && debugged.size() < MOST_DEBUGGED && debugged.add(title + step)) {
			DebugHooks.log(SkyBlockDebug.DUNGEON, "Chest profit: {}: {}", title, step);
		}
	}

	/** The open chest's menu: its items, and the cost on the button that opens it. */
	private ChestValue.Chest readOpenChest(AbstractContainerScreen<?> screen, String kind) {
		List<String> contents = new ArrayList<>();
		boolean foundButton = false;
		List<String> cost = List.of();
		List<Slot> slots = screen.getMenu().slots;
		int chestSlots = Math.max(0, slots.size() - 36);
		for (int i = 0; i < chestSlots; i++) {
			ItemStack stack = slots.get(i).getItem();
			if (stack.isEmpty() || stack.is(Items.GLASS_PANE) || stack.getItem().toString().contains("stained_glass_pane")) {
				continue;
			}
			String name = TextColors.noColors(stack.getHoverName().getString()).strip();
			List<String> lines = lore(stack);
			if (name.equals(OPEN_BUTTON)) {
				foundButton = true;
				cost = section(lines, COST);
				continue;
			}
			if (name.equals(REROLL_BUTTON) && ChestMarks.rerolled(lines) && kismetKind == null) {
				// the run's Kismet Feather is used: on this chest when Lumio saw the click, else only known for the run
				kismetUsed = true;
			}
			// buttons (the reroll, close, back, anything that asks for a click) are no items of the chest
			if (ChestMarks.isButton(name, lines)) {
				continue;
			}
			name = ChestMarks.bookName(name, lines);
			noteColor(name, stack.getHoverName());
			String id = ItemGains.idOf(stack);
			if (ChestBooks.BOOK_ID.equals(id)) {
				// a book is priced by its enchantment, from the item's own data: {ultimate_wise: 1} is ENCHANTMENT_ULTIMATE_WISE_1
				var data = stack.get(DataComponents.CUSTOM_DATA);
				id = data == null ? null : ChestBooks.idFromData(data.copyTag());
				if (id != null && !name.startsWith("Enchanted Book (")) {
					name = "Enchanted Book (" + ChestBooks.display(id) + ")";
				}
			}
			if (id != null) {
				SkyBlockPrices.learnName(id, name);
			}
			contents.add(stack.getCount() > 1 && !COUNTED.matcher(name).matches() ? name + " x" + stack.getCount() : name);
		}
		String title = TextColors.noColors(screen.getTitle().getString()).strip();
		if (contents.isEmpty()) {
			debugOnce(title, "no items read");
			return null;
		}
		if (!foundButton && !ChestValue.namedChest(title)) {
			// a menu named only "Gold" without the reward chest's button is not a reward chest
			debugOnce(title, "no Open Reward Chest button, not a reward chest");
			return null;
		}
		ChestValue.Chest chest = value(kind, contents, cost);
		for (ChestValue.Item item : chest.items()) {
			if (!item.priced() && ChestBooks.isBook(item.id()) && DebugHooks.ACTIVE && unpricedBooks.size() < MOST_LOGGED_BOOKS && unpricedBooks.add(item.id())) {
				DebugHooks.log(SkyBlockDebug.DUNGEON, "Chest profit: no price for the book {} on the bazaar or the auction house", item.id());
			}
		}
		if (chest.items().stream().allMatch(item -> item.value() <= 0)) {
			debugOnce(title, "no price for any item (" + contents.size() + " items)");
		}
		return chest;
	}

	/** Croesus' list of a run's chests: each chest item's contents and cost from its lines; read again only when the menu changes. */
	private void readList(AbstractContainerScreen<?> screen) {
		List<Slot> slots = screen.getMenu().slots;
		int stateId = screen.getMenu().getStateId();
		if (stateId == listedFor) {
			return;
		}
		listedFor = stateId;
		listed.clear();
		int chestSlots = Math.max(0, slots.size() - 36);
		for (int i = 0; i < chestSlots; i++) {
			ItemStack stack = slots.get(i).getItem();
			if (stack.isEmpty()) {
				continue;
			}
			String kind = ChestValue.kindOf(TextColors.noColors(stack.getHoverName().getString()).strip());
			if (kind == null) {
				continue;
			}
			List<String> lines = lore(stack);
			List<String> contents = section(lines, CONTENTS);
			noteLoreColors(stack, contents);
			if (!contents.isEmpty()) {
				listed.put(i, value(kind, contents, section(lines, COST)));
			}
		}
		if (!listed.isEmpty()) {
			// a run's chests opened from Croesus' list: its best profit shows on the run there
			CroesusRuns.RunMarks marks = croesus.opened(best(listed.values()).profit(), System.currentTimeMillis());
			opened.clear();
			if (marks.opened() != null) {
				opened.add(marks.opened());
			}
			kismetUsed = marks.kismet();
			kismetKind = marks.kismetKind();
		}
	}

	private ChestValue.Chest value(String kind, List<String> contents, List<String> cost) {
		SkyBlockPrices.Mode mode = prices.get().mode;
		return ChestValue.of(kind, contents, cost, SkyBlockPrices::idForName, id -> SkyBlockPrices.price(id, mode),
			SkyBlockPrices.price("DUNGEON_CHEST_KEY", mode));
	}

	/** Keeps the colors of a chest's content lines in Croesus' list, by the lines. */
	private void noteLoreColors(ItemStack stack, List<String> contents) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return;
		}
		for (Component line : lore.lines()) {
			String plain = TextColors.noColors(line.getString()).strip();
			if (contents.contains(plain)) {
				Matcher counted = COUNTED_LINE.matcher(plain);
				noteColor(counted.matches() ? counted.group(1) : plain, line);
			}
		}
	}

	/** Keeps the color of an item's name, as the game shows it, for its row. */
	private void noteColor(String name, Component shown) {
		if (itemColors.size() > MOST_COLORS) {
			itemColors.clear();
		}
		shown.visit((style, text) -> {
			if (style.getColor() != null && !text.isBlank()) {
				itemColors.put(name, style.getColor().getValue());
				return java.util.Optional.of(Boolean.TRUE);
			}
			return java.util.Optional.empty();
		}, net.minecraft.network.chat.Style.EMPTY);
	}

	@Override
	public JsonObject save() {
		JsonObject json = super.save();
		json.add("croesus_runs", croesus.save());
		return json;
	}

	@Override
	public void load(JsonObject json) {
		// the run's best-chest line in Croesus' tooltip is gone, and its switch with it
		json.remove(BEST_IN_TOOLTIP_KEY);
		super.load(json);
		croesus.load(json.get("croesus_runs"));
	}

	/** @return what a run's line in Croesus' list says of it: the chest opened, and the Kismet Feather struck through once it is used */
	private static CroesusRuns.RunMarks runMarks(ItemStack run) {
		ItemLore lore = run.get(DataComponents.LORE);
		boolean kismet = false;
		if (lore != null) {
			for (Component line : lore.lines()) {
				if (line.getString().contains(FEATHER) && struck(line)) {
					kismet = true;
				}
			}
		}
		return new CroesusRuns.RunMarks(ChestMarks.openedKind(lore(run)), kismet, null);
	}

	private static boolean struck(Component line) {
		return line.visit((style, text) -> text.contains(FEATHER) && style.isStrikethrough() ? java.util.Optional.of(Boolean.TRUE) : java.util.Optional.empty(),
			net.minecraft.network.chat.Style.EMPTY).isPresent();
	}

	/**
	 * The player clicked in a chest's menu (asked before the click is sent; it is never held back): the Open Reward Chest button opens this chest,
	 * the Reroll Chest button uses the run's Kismet Feather on it. Kept for the run, also for Croesus' list.
	 */
	public void clicked(AbstractContainerScreen<?> screen, int slotId) {
		if (!isEnabled() || slotId < 0 || slotId >= screen.getMenu().slots.size()) {
			return;
		}
		String kind = ChestValue.kindOf(TextColors.noColors(screen.getTitle().getString()).strip());
		if (kind == null) {
			return;
		}
		String name = TextColors.noColors(screen.getMenu().slots.get(slotId).getItem().getHoverName().getString()).strip();
		if (name.equals(OPEN_BUTTON)) {
			opened.add(kind);
			croesus.note(new CroesusRuns.RunMarks(kind, false, null));
		} else if (name.equals(REROLL_BUTTON)) {
			kismetKind = kind;
			kismetUsed = true;
			croesus.note(new CroesusRuns.RunMarks(null, true, kind));
		}
	}

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		List<String> lines = new ArrayList<>();
		if (lore != null) {
			for (Component line : lore.lines()) {
				lines.add(TextColors.noColors(line.getString()).strip());
			}
		}
		return lines;
	}

	/** @return the lines under a heading ("Contents", "Cost"), up to the next empty line */
	private static List<String> section(List<String> lines, String heading) {
		List<String> found = new ArrayList<>();
		boolean in = false;
		for (String line : lines) {
			if (line.equals(heading)) {
				in = true;
				continue;
			}
			if (in) {
				if (line.isEmpty()) {
					break;
				}
				found.add(line);
			}
		}
		return found;
	}

	private static int kindColor(String kind) {
		return switch (kind) {
			case "Wood" -> 0xA0703C;
			case "Gold" -> 0xFFD60A;
			case "Diamond" -> 0x5AC8FA;
			case "Emerald" -> 0x34C759;
			case "Obsidian" -> 0xAF52DE;
			default -> 0x636366;
		};
	}
}
