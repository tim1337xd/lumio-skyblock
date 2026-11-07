package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.draw.TextSelection;
import dev.tim1337.lumio.api.experimental.ui.UiSounds;
import dev.tim1337.lumio.api.experimental.util.CommandText;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;
import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.data.StorageTracker;
import dev.tim1337.lumio.skyblock.mixin.accessor.ContainerScreenAccess;
import dev.tim1337.lumio.skyblock.mixin.accessor.SlotAccess;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Hypixel SkyBlock storage at a glance, like SkySoft's, drawn over the open chest screen: the storage menu, or an ender chest or backpack page.
 * It is not a screen of its own: the game's screen stays, with its slots, which are only repositioned. Every ender chest page and backpack is a
 * card with its items as last seen (the open page has the vanilla slots in its card, and an accent frame). Under the window is the player's inventory, centered, and
 * directly to its right the "Storages" panel with a shortcut per page (see {@link StoragePanel}), and under that the button to its settings. So every slot click, shift-click, drag and number key is the game's own, exactly as without the
 * overlay: nothing about it is changed or added. When the window is small, the cards' area gets smaller and scrolls; the inventory keeps its size.
 * <p>
 * The overlay only adds what is not a slot: clicking the card of a page that is not open opens it (like clicking its item in the menu, or with
 * the SkyBlock command while a page is open), the filter (all, ender chest, backpacks), the search, which rings matching items and hides the
 * pages without any (the open page always stays), and scrolling the cards.
 */
public final class StorageOverlay {
	/** Slots per row of every container (ender chest pages, backpacks, the inventory). */
	static final int COLUMNS = 9;
	static final int SLOT = 18;
	static final int CARD_PAD = 8;
	static final int CARD_HEAD = 20;

	private static final int GAP = 8;
	private static final int HEADER = 44;
	private static final int HINT = 16;
	private static final int SPACE = 8;
	/** The inventory panel: as wide as a card, with its title's row, its three main rows, and the gap above the hotbar. */
	private static final int INVENTORY_PAD = 8;
	private static final int INVENTORY_TITLE = 13;
	private static final int INVENTORY_ROWS = 3;
	private static final int HOTBAR_GAP = 4;
	private static final int INVENTORY_WIDTH = COLUMNS * SLOT + 2 * INVENTORY_PAD;
	private static final int INVENTORY_HEIGHT = 2 * INVENTORY_PAD + INVENTORY_TITLE + (INVENTORY_ROWS + 1) * SLOT + HOTBAR_GAP;
	/** The least height of the storage window above the inventory (header, hint and two rows of slots), and the room to the screen's edges. */
	private static final int LEAST_PANEL = HEADER + HINT + 4 + 2 * SLOT;
	/** The sections, in their order: the ender chest first. */
	private static final String[] SECTION_PREFIX = {"e", "b"};
	private static final String[] SECTION_TITLE = {"Ender chest", "Backpacks"};
	private static final String[] SECTION_ICON = {"chest", "backpack"};
	private static final int EDGE = 4;
	private static final int OFF = -10000;
	/** The rows shown for a page never opened. */
	static final int UNSEEN_ROWS = 3;
	/** A section's heading above its cards, the room between two sections, and how far the soft fade at the bottom reaches. */
	private static final int SECTION_HEAD = 16;
	private static final int SECTION_GAP = 6;
	private static final int FADE = 22;
	private static final float FADE_OPACITY = 0.45f;
	/** The room under the last row, and for the note in an empty window. */
	private static final int CONTENT_END = 4;
	private static final int EMPTY_CONTENT = 48;
	/** Below these window widths the filter gets a row of its own under the title, and then the search too. */
	private static final int FILTER_BESIDE_WIDTH = 470;
	private static final int SEARCH_BESIDE_WIDTH = 300;
	private static final int HEADER_ROW = 24;
	private static final int LEAST_HINT_ROOM = 170;
	private static final int SEARCH_MOST = 30;
	private static final int SCROLL_STEP = 36;
	/** The whole overlay fades in once when the storage is opened; opening another page changes nothing. */
	private static final float FADE_SECONDS = 0.15f;
	private static final long NANOS_PER_SECOND = 1_000_000_000L;
	private static final String[] FILTERS = {"All", "Ender chest", "Backpacks"};
	private static final String HINT_LEFT = "Click a card to open it · the open page is live, the rest are previews";
	private static final String HINT_HIDDEN_END = " while you search · click a card to open it";
	private static final String HINT_RIGHT = "Shift-click moves items like in the normal menu";
	/** The slot or page number at the end of a backpack title, which the card name has already: "(Slot #3)". */
	private static final Pattern TITLE_NUMBER = Pattern.compile("\\s*\\([^)]*\\)\\s*$");

	private record Card(String key, int x, int y, int w, int h, int rows, boolean live, boolean unseen) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}

		/** @return where its slot grid starts, centered in the card */
		int gridX() {
			return x + (w - COLUMNS * SLOT) / 2;
		}
	}

	private static final List<Card> cards = new ArrayList<>();
	private static final List<Card> cardRow = new ArrayList<>();
	/** A section's heading: which one, where it stands and how many pages it shows. */
	private record Section(int kind, int y, int count) {
	}

	private static final List<Section> sections = new ArrayList<>();
	/** How many rows the header takes under the title, for a narrow window. */
	private static int headerRows;
	private static final List<String> keys = new ArrayList<>();
	private static final StorageCards stats = new StorageCards();
	private static final int[][] filterBoxes = new int[FILTERS.length][4];
	/** Card names per page, worked out again only when the page's title changes. */
	private static final Map<String, String[]> names = new HashMap<>();
	private static int filter;
	private static double scroll;
	private static double scrollTarget;
	private static int contentHeight;
	private static String search = "";
	private static boolean searchFocused;
	private static int areaTop;
	private static int areaBottom;
	private static final int[] panel = new int[4];
	private static final int[] searchBox = new int[4];
	private static final int[] inventoryCard = new int[4];
	private static final float SECTION_LINE_OPACITY = 0.08f;
	private static final int FRAME = 1;
	private static final String[] PAGES_TEXT = new String[StoragePages.ALL + 1];
	private static final StoragePanel storages = new StoragePanel();
	private static final int[] settingsBox = new int[4];
	private static final String SETTINGS_TIP = "Storage settings";
	private static long lastFrame;
	private static long openedAt;
	private static ItemStack hoveredItem = ItemStack.EMPTY;
	/** How far the search's highlights are shown, fading in and out with the search. */
	private static float searchShown;
	/** The header's texts, made again only when their numbers change. */
	private static int countFor = -1;
	private static String countText = "";
	private static long foundFor = -1;
	private static String foundText = "";
	/** The pages hidden by the search (they have no match), and the hint saying so, made again only when their count changes. */
	private static int hidden;
	private static int hiddenFor = -1;
	private static String hiddenText = "";
	/** The screen the layout was built for, so scroll and search reset for another one. */
	private static Object lastScreen;

	private StorageOverlay() {
	}

	/** @return whether the overlay covers this screen: the storage menu, or an ender chest or backpack page, with the feature enabled */
	public static boolean active(Object screen) {
		if (!(screen instanceof ContainerScreen chest) || SkyBlockFeatures.STORAGE == null || !SkyBlockFeatures.STORAGE.isEnabled()) {
			return false;
		}
		// queried many times per frame (for every slot): a screen's title does not change, so it is checked once per screen
		if (screen != activeFor) {
			activeFor = screen;
			String title = chest.getTitle().getString().strip();
			activeResult = StorageTracker.isStorageMenu(title) || StorageTracker.keyOf(title) != null;
		}
		return activeResult;
	}

	private static Object activeFor;
	private static boolean activeResult;

	/** The page of a screen and whether it is the storage menu, derived from its title once per screen. */
	private static Object titleFor;
	private static String titleKey;
	private static boolean titleMenu;

	private static void readTitle(ContainerScreen screen) {
		if (screen != titleFor) {
			titleFor = screen;
			String title = screen.getTitle().getString().strip();
			titleKey = StorageTracker.keyOf(title);
			titleMenu = StorageTracker.isStorageMenu(title);
		}
	}

	private static String openKey(ContainerScreen screen) {
		readTitle(screen);
		return titleKey;
	}

	private static boolean isMenu(ContainerScreen screen) {
		readTitle(screen);
		return titleMenu;
	}

	private static void setBox(int[] box, int x, int y, int w, int h) {
		box[0] = x;
		box[1] = y;
		box[2] = w;
		box[3] = h;
	}

	/**
	 * Lays out the overlay and moves the vanilla slots to their displayed positions: the open page in its card, the player inventory in its panel
	 * below the window, and every hidden slot (the storage menu's, the page's button row, scrolled-out ones) off screen where it cannot
	 * be clicked. The screen spans the whole window, so a click beside a slot is not a click outside it (which would drop the held item). While
	 * searching, the pages without a match are left out, so the others close the gaps.
	 */
	public static void layout(ContainerScreen screen) {
		if (lastScreen != screen) {
			lastScreen = screen;
			lastMenuState = -1;
			// scroll and search persist from the storage menu to a page and back, which is one trip through the storage; the overlay fades in only
			// when the storage is opened, not on every page change
			StorageTracker.rememberMenu(screen.getMenu());
			if (System.nanoTime() - lastFrame > NANOS_PER_SECOND) {
				openedAt = System.nanoTime();
			}
		}
		String open = openKey(screen);
		// storage menu items are cached when they change, not copied every frame; the open page's numbers are worked out again
		if (screen.getMenu().getStateId() != lastMenuState) {
			lastMenuState = screen.getMenu().getStateId();
			if (isMenu(screen)) {
				StorageTracker.rememberMenu(screen.getMenu());
			}
			if (open != null) {
				stats.changed(open);
			}
		}
		var access = (ContainerScreenAccess) screen;
		access.evskyblock$setLeft(0);
		access.evskyblock$setTop(0);
		access.evskyblock$setWidth(screen.width);
		access.evskyblock$setHeight(screen.height);

		// the cards first, from the top of the content: the window's height follows from them
		int columns = StorageLayout.columns(SkyBlockFeatures.STORAGE.columns(), screen.width - 2 * EDGE);
		int windowWidth = StorageLayout.windowWidth(columns);
		int rowsOpen = screen.getMenu().getRowCount();
		keys.clear();
		keys.addAll(StorageTracker.knownKeys());
		if (open != null && !keys.contains(open)) {
			keys.add(open);
		}
		cards.clear();
		sections.clear();
		hidden = 0;
		int y = 0;
		for (int kind = 0; kind < SECTION_PREFIX.length; kind++) {
			y = placeSection(screen, kind, open, rowsOpen, columns, y);
		}
		contentHeight = cards.isEmpty() ? EMPTY_CONTENT : y - GAP + CONTENT_END;

		// the window, and below it the inventory card, centered together on the screen with the game visible around them. The window is only as tall as
		// its content, up to the most set or what the screen leaves; beyond that the content scrolls inside. The inventory always keeps its size.
		headerRows = windowWidth >= FILTER_BESIDE_WIDTH ? 0 : windowWidth >= SEARCH_BESIDE_WIDTH ? 1 : 2;
		int header = HEADER + headerRows * HEADER_ROW;
		int chrome = header + HINT + 4;
		int setMost = Math.round(screen.height * SkyBlockFeatures.STORAGE.heightFraction()) - INVENTORY_HEIGHT - SPACE;
		int screenMost = screen.height - 2 * EDGE - INVENTORY_HEIGHT - SPACE;
		int most = Math.min(screenMost, Math.max(setMost, chrome + 2 * SLOT));
		int panelHeight = StorageLayout.windowHeight(chrome, contentHeight, Math.min(most, LEAST_PANEL), most);
		int top = Math.max(EDGE, (screen.height - panelHeight - SPACE - INVENTORY_HEIGHT) / 2);
		setBox(panel, (screen.width - windowWidth) / 2, top, windowWidth, panelHeight);
		areaTop = panel[1] + header;
		areaBottom = panel[1] + panel[3] - HINT - 4;
		scrollTarget = Mth.clamp(scrollTarget, 0, Math.max(0, contentHeight - (areaBottom - areaTop)));
		scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight - (areaBottom - areaTop)));
		// now that the window's place is known, the content moves to it
		int startX = panel[0] + StorageLayout.SIDE;
		int offset = areaTop - (int) Math.round(scroll);
		for (int i = 0; i < cards.size(); i++) {
			Card card = cards.get(i);
			cards.set(i, new Card(card.key(), startX + card.x(), offset + card.y(), card.w(), card.h(), card.rows(), card.live(), card.unseen()));
		}
		for (int i = 0; i < sections.size(); i++) {
			Section section = sections.get(i);
			sections.set(i, new Section(section.kind(), offset + section.y(), section.count()));
		}

		// under the window: the inventory in the middle, and the storages panel directly to its right, as far as the screen has room for it
		int rowY = panel[1] + panel[3] + SPACE;
		int inventoryX = StorageLayout.inventoryX(screen.width, INVENTORY_WIDTH);
		setBox(inventoryCard, inventoryX, rowY, INVENTORY_WIDTH, INVENTORY_HEIGHT);
		storages.place(inventoryX + INVENTORY_WIDTH + SPACE, rowY, StorageLayout.roomBeside(screen.width, INVENTORY_WIDTH, SPACE, EDGE));
		SettingsButton.place(settingsBox, inventoryX + INVENTORY_WIDTH, rowY + INVENTORY_HEIGHT, screen.width);

		int gridX = inventoryCard[0] + INVENTORY_PAD;
		int gridY = inventoryCard[1] + INVENTORY_PAD + INVENTORY_TITLE;
		for (int i = 0; i < screen.getMenu().slots.size(); i++) {
			Slot slot = screen.getMenu().slots.get(i);
			SlotAccess accessor = (SlotAccess) slot;
			int sx = OFF;
			int sy = OFF;
			if (slot.container instanceof Inventory) {
				int index = slot.getContainerSlot();
				// as in the game's inventory: the three main rows (slots 9 to 35), then the hotbar (0 to 8) a little apart below them
				if (index >= COLUMNS && index < (INVENTORY_ROWS + 1) * COLUMNS) {
					sx = gridX + index % COLUMNS * SLOT + 1;
					sy = gridY + (index / COLUMNS - 1) * SLOT + 1;
				} else if (index >= 0 && index < COLUMNS) {
					sx = gridX + index * SLOT + 1;
					sy = gridY + INVENTORY_ROWS * SLOT + HOTBAR_GAP + 1;
				}
			} else if (isMenu(screen)) {
				// the storage menu's big bar stays hidden: the panel stands for it
				sx = OFF;
			} else {
				// the open page: in its card, without its first row, which holds the server's buttons
				for (Card card : cards) {
					if (card.live() && i >= COLUMNS && i < (card.rows() + 1) * COLUMNS && !isFiller(slot.getItem())) {
						int row = i / COLUMNS - 1;
						int slotY = card.y() + CARD_HEAD + row * SLOT + 1;
						if (slotY >= areaTop && slotY + 16 <= areaBottom) {
							sx = card.gridX() + i % COLUMNS * SLOT + 1;
							sy = slotY;
						}
					}
				}
			}
			accessor.evskyblock$setX(sx);
			accessor.evskyblock$setY(sy);
		}
	}

	/**
	 * Lays out a section with its heading: its known pages, left to right in rows. Every card in a row starts at the same y and is as tall as the tallest in
	 * the row, slots at the top; placing each card in the shortest column instead let a small backpack next to an ender chest shift the columns out of alignment.
	 *
	 * @return the y where the next section starts
	 */
	private static int placeSection(ContainerScreen screen, int kind, String open, int rowsOpen, int columns, int top) {
		String prefix = SECTION_PREFIX[kind];
		if (filter != 0 && filter != kind + 1) {
			return top;
		}
		int y = top + SECTION_HEAD;
		int count = 0;
		for (String key : keys) {
			if (!key.startsWith(prefix)) {
				continue;
			}
			boolean live = key.equals(open);
			StorageTracker.Page page = StorageTracker.page(key);
			List<ItemStack> items = live ? menuItems(screen) : page == null ? List.of() : page.items();
			int rows = live ? rowsOpen - 1 : page == null || page.rows <= 1 ? 0 : page.rows - 1;
			// a backpack smaller than its window has its last rows filled with glass panes: the card is only as tall as the backpack
			rows = usedRows(items, rows);
			boolean unseen = rows == 0 && !live;
			if (unseen && !SkyBlockFeatures.STORAGE.showUnseen()) {
				continue;
			}
			// while searching, a page without a match is left out (counted, for the hint); the open page stays, its slots must stay reachable
			if (!search.isEmpty() && !live && (unseen || stats.matches(key, items, search) == 0)) {
				hidden++;
				continue;
			}
			int shownRows = unseen ? UNSEEN_ROWS : rows;
			cardRow.add(new Card(key, cardRow.size() * (StorageLayout.CARD_WIDTH + StorageLayout.GAP), y, StorageLayout.CARD_WIDTH, CARD_HEAD + shownRows * SLOT + CARD_PAD,
				shownRows, live, unseen));
			count++;
			if (cardRow.size() == columns) {
				y = placeRow(y);
			}
		}
		y = placeRow(y);
		if (count == 0) {
			return top;
		}
		sections.add(new Section(kind, top, count));
		return y + SECTION_GAP;
	}

	/** Lays out the collected row of cards, each as tall as the tallest. @return the y where the next row starts */
	private static int placeRow(int y) {
		if (cardRow.isEmpty()) {
			return y;
		}
		int height = 0;
		for (Card card : cardRow) {
			height = Math.max(height, card.h());
		}
		for (Card card : cardRow) {
			cards.add(new Card(card.key(), card.x(), card.y(), card.w(), height, card.rows(), card.live(), card.unseen()));
		}
		cardRow.clear();
		return y + height + GAP;
	}

	private static int lastMenuState = -1;

	/** @return a card's items: the open page's slots, or the page as last seen */
	private static List<ItemStack> itemsOf(ContainerScreen screen, Card card) {
		if (card.live()) {
			return menuItems(screen);
		}
		StorageTracker.Page page = StorageTracker.page(card.key());
		return page == null ? List.of() : page.items();
	}

	/** @return the item under the mouse on a card of the overlay, if it is open */
	public static ItemStack hoveredItem() {
		Minecraft client = Minecraft.getInstance();
		return active(client.gui.screen()) ? hoveredItem : ItemStack.EMPTY;
	}

	/** Draws the overlay in place of the chest texture, below the vanilla slots, which are drawn on top where the layout put them. */
	public static void drawBackground(ContainerScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0f : Math.min(0.1f, (float) (now - lastFrame) / NANOS_PER_SECOND);
		lastFrame = now;
		scroll += (scrollTarget - scroll) * (1f - Math.exp(-dt * 14f));
		searchShown += ((search.isEmpty() ? 0f : 1f) - searchShown) * (1f - (float) Math.exp(-dt * 12f));
		layout(screen);
		Minecraft client = Minecraft.getInstance();
		Draw.setZoom(1f);
		Canvas ui = Draw.hudCanvas(graphics, 0xFF000000 | MainSettings.accentRgb(), dt, true);
		// one short fade of the whole overlay when the storage opens; the items, which the game draws, are there at once
		float shown = Mth.clamp((float) (now - openedAt) / NANOS_PER_SECOND / FADE_SECONDS, 0f, 1f);
		OverlayBackdrop.draw(ui, graphics, screen.width, screen.height, SkyBlockFeatures.STORAGE.background(), SkyBlockFeatures.STORAGE.backgroundStrength(), shown);
		Draw.setAlpha(shown);
		HudTheme.panel(ui, panel[0], panel[1], panel[2], panel[3], Radius.XXL, HudTheme.opacity());
		drawHeader(ui, screen, mouseX, mouseY);

		// the cards, clipped at the top and bottom of their area
		graphics.enableScissor(panel[0] + 2, areaTop - 4, panel[0] + panel[2] - 2, areaBottom);
		ItemStack hovered = ItemStack.EMPTY;
		hoveredItem = ItemStack.EMPTY;
		for (int index = 0; index < cards.size(); index++) {
			Card card = cards.get(index);
			if (card.y() > areaBottom || card.y() + card.h() < areaTop - 4) {
				continue;
			}
			// a stratum per card: the game scans every element of a stratum for each new one, and with all cards in one stratum (hundreds of slots and
			// items) that was most of the frame time. Cards do not overlap, so nothing visible changes.
			graphics.nextStratum();
			List<ItemStack> items = itemsOf(screen, card);
			int matches = stats.matches(card.key(), items, search);
			ItemStack over = drawCard(ui, graphics, client, card, items, matches, mouseX, mouseY);
			if (!over.isEmpty()) {
				hovered = over;
				hoveredItem = over;
			}
		}
		// the sections' headings, and a soft fade at the bottom while there is more to scroll to
		graphics.nextStratum();
		for (Section section : sections) {
			if (section.y() <= areaBottom && section.y() + SECTION_HEAD >= areaTop - 4) {
				drawSectionHead(ui, section);
			}
		}
		if (contentHeight - scroll > areaBottom - areaTop + 1) {
			graphics.fillGradient(panel[0] + 2, areaBottom - FADE, panel[0] + panel[2] - 2, areaBottom, 0x00000000, Draw.withOpacity(0xFF000000, FADE_OPACITY));
		}
		graphics.disableScissor();
		if (cards.isEmpty()) {
			String empty = search.isEmpty() ? "Open the pages once to see them here" : "Nothing found in your storage";
			ui.textCentered(empty, panel[0] + panel[2] / 2, (areaTop + areaBottom) / 2, HudTheme.secondary());
		}

		// the hints along the bottom of the window
		float hintY = areaBottom + 6f;
		float rightWidth = ui.widthScaled(HINT_RIGHT, TextScale.SMALL);
		float leftRoom = panel[2] - 28 - rightWidth - 16;
		// a narrow window keeps the one about its pages
		boolean bothHints = leftRoom >= LEAST_HINT_ROOM;
		if (!bothHints) {
			leftRoom = panel[2] - 28;
		}
		String left = HINT_LEFT;
		if (!search.isEmpty() && hidden > 0) {
			if (hiddenFor != hidden) {
				hiddenFor = hidden;
				hiddenText = hidden == 1 ? "1 page without results is hidden" + HINT_HIDDEN_END : hidden + " pages without results are hidden" + HINT_HIDDEN_END;
			}
			left = hiddenText;
		}
		ui.textAtScaled(ui.fitScaled(left, leftRoom, TextScale.SMALL), panel[0] + 14, hintY, TextScale.SMALL, HudTheme.secondary());
		if (bothHints) {
			ui.textAtScaled(HINT_RIGHT, panel[0] + panel[2] - 14 - rightWidth, hintY, TextScale.SMALL, HudTheme.secondary());
		}

		// below the window: the inventory, and the storages panel beside it
		graphics.nextStratum();
		drawInventoryCard(ui);
		storages.draw(ui, graphics, client, openKey(screen), now, mouseX, mouseY);
		SettingsButton.draw(ui, graphics, settingsBox, SETTINGS_TIP, mouseX, mouseY);
		Draw.setAlpha(1f);
		if (!hovered.isEmpty()) {
			graphics.setTooltipForNextFrame(client.font, hovered, mouseX, mouseY);
		}
	}

	/** Draws a section's heading: its icon, its name and how many pages it shows. */
	private static void drawSectionHead(Canvas ui, Section section) {
		int x = panel[0] + StorageLayout.SIDE + 2;
		int y = section.y() + 3;
		int color = HudTheme.secondary();
		ui.icon(SECTION_ICON[section.kind()], x, y, 9, color);
		String title = SECTION_TITLE[section.kind()];
		float titleX = x + 13f;
		ui.textAtScaledBold(title, titleX, y + 1f, TextScale.SMALL, HudTheme.text());
		String count = pagesText(section.count());
		float countX = titleX + ui.boldWidthScaled(title, TextScale.SMALL) + 6f;
		ui.textAtScaled(count, countX, y + 1f, TextScale.SMALL, color);
		int lineX = Math.round(countX + ui.widthScaled(count, TextScale.SMALL)) + 8;
		ui.fill(lineX, y + 4, panel[0] + panel[2] - StorageLayout.SIDE - lineX, 1, HudTheme.line(SECTION_LINE_OPACITY));
	}

	/** @return a section's count of pages, the same text object for the same count */
	private static String pagesText(int count) {
		if (count >= PAGES_TEXT.length) {
			return count + " pages";
		}
		String text = PAGES_TEXT[count];
		if (text == null) {
			text = count == 1 ? "1 page" : count + " pages";
			PAGES_TEXT[count] = text;
		}
		return text;
	}

	/** Draws the header: the title with the count of pages, the filter, and the search with what it found. */
	private static void drawHeader(Canvas ui, ContainerScreen screen, int mouseX, int mouseY) {
		int left = panel[0] + 14;
		int right = panel[0] + panel[2] - 14;
		int top = panel[1] + 12;
		ui.textAtScaledBold("Storage", left, top - 1f, TextScale.HEADING, HudTheme.text());

		int ender = 0;
		int backpacks = 0;
		for (String key : StorageTracker.knownKeys()) {
			if (key.startsWith("e")) {
				ender++;
			} else {
				backpacks++;
			}
		}
		if (countFor != ender * 1000 + backpacks) {
			countFor = ender * 1000 + backpacks;
			countText = ender + (ender == 1 ? " ender chest page · " : " ender chest pages · ") + backpacks + (backpacks == 1 ? " backpack" : " backpacks");
		}
		ui.textAtScaled(countText, left, top + 13f, TextScale.SMALL, HudTheme.secondary());

		int found = 0;
		int foundIn = 0;
		for (Card card : cards) {
			if (card.unseen()) {
				continue;
			}
			int matches = stats.matches(card.key(), itemsOf(screen, card), search);
			found += matches;
			foundIn += matches > 0 ? 1 : 0;
		}

		long foundKey = (long) found << 16 | foundIn;
		if (foundFor != foundKey) {
			foundFor = foundKey;
			foundText = found + " found in " + foundIn;
		}
		int titleWidth = Math.round(Math.max(ui.boldWidthScaled("Storage", TextScale.HEADING), ui.widthScaled(countText, TextScale.SMALL)));
		int filterWidth = PanelKit.segmentedWidth(ui, FILTERS);
		int searchRight = right;
		int room = searchRight - (left + titleWidth + 14);
		int fieldY = top + 1;
		// beside the title in a wide window; a narrow one gives the filter, and then the search, a row of their own under it
		int filterY = fieldY + (PanelKit.FIELD_HEIGHT - PanelKit.SEGMENT_HEIGHT) / 2;
		int filterX;
		int searchW;
		if (headerRows == 0) {
			searchW = Math.min(panel[2] / 3, room - filterWidth - 10);
			setBox(searchBox, searchRight - searchW, fieldY, Math.max(0, searchW), PanelKit.FIELD_HEIGHT);
			filterX = searchBox[0] - 10 - filterWidth;
		} else if (headerRows == 1) {
			searchW = room;
			setBox(searchBox, searchRight - searchW, fieldY, Math.max(0, searchW), PanelKit.FIELD_HEIGHT);
			filterX = left;
			filterY = top + HEADER - 12;
		} else {
			searchW = right - left;
			setBox(searchBox, left, top + HEADER - 12 + HEADER_ROW, searchW, PanelKit.FIELD_HEIGHT);
			filterX = left;
			filterY = top + HEADER - 12;
		}
		if (searchFocused) {
			ScreenInput.wantText();
		}
		PanelKit.searchField(ui, searchBox, search, "Search items", search.isEmpty() ? null : foundText, searchFocused, mouseX, mouseY);
		if (searchFocused && !search.isEmpty() && SEARCH_SELECTION.all()) {
			SEARCH_SELECTION.draw(ui, searchBox[0] + 23, searchBox[1] + searchBox[3] / 2, ui.width(ui.fit(search, searchW - 80)));
		}
		PanelKit.segmented(ui, filterX, filterY, FILTERS, filter, mouseX, mouseY, filterBoxes);
	}

	/** Draws a card: its head with icon, name and matches, its slots, and the items as last seen. @return the item under the mouse */
	private static ItemStack drawCard(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client, Card card, List<ItemStack> items, int matches,
									  int mouseX, int mouseY) {
		boolean over = card.contains(mouseX, mouseY) && mouseY >= areaTop && mouseY < areaBottom;
		PanelKit.card(ui, card.x(), card.y(), card.w(), card.h(), card.live() ? 0.9f : over && !card.unseen() ? 0.45f : 0f);
		if (card.live()) {
			// the open page's frame, around the card, in the accent
			ui.ring(card.x() - FRAME, card.y() - FRAME, card.w() + 2 * FRAME, card.h() + 2 * FRAME, Radius.LG + FRAME, FRAME, ui.accent());
		}
		int headY = card.y() + 6;
		int textColor = card.unseen() ? HudTheme.secondary() : HudTheme.text();
		ui.icon(card.unseen() ? "lock" : card.key().startsWith("e") ? "chest" : "backpack", card.x() + CARD_PAD, headY, 9, textColor);
		float right = card.x() + card.w() - CARD_PAD;
		if (matches > 0) {
			// the match count in an accent badge, fading in with the search
			String badge = String.valueOf(matches);
			float badgeWidth = ui.boldWidthScaled(badge, TextScale.SMALL) + 8;
			right -= badgeWidth;
			ui.roundedSmooth(right, headY - 0.5f, badgeWidth, 10f, Radius.XS, Draw.withOpacity(ui.accent(), 0.35f + 0.45f * searchShown));
			ui.textAtScaledBold(badge, right + 4, headY + 1.5f, TextScale.SMALL, 0xFFFFFFFF);
		}
		int nameX = card.x() + CARD_PAD + 13;
		ui.textAtScaledBold(ui.fitScaled(cardName(card.key()), right - nameX - 6, TextScale.SMALL), nameX, headY + 1f, TextScale.SMALL, textColor);

		int slotsY = card.y() + CARD_HEAD;
		int gridX = card.gridX();
		// all slots of a card as one texture, not a shape each
		ui.slotGrid(gridX, slotsY, COLUMNS, card.rows(), SLOT, SLOT, 4f, HudTheme.slot());
		if (card.unseen()) {
			drawLocked(ui, gridX + COLUMNS * SLOT / 2f, slotsY + card.rows() * SLOT / 2f);
			return ItemStack.EMPTY;
		}
		ItemStack hovered = ItemStack.EMPTY;
		for (int r = 0; r < card.rows(); r++) {
			int rowY = slotsY + r * SLOT;
			if (rowY + SLOT < areaTop - 4 || rowY > areaBottom) {
				continue; // scrolled out of view
			}
			for (int c = 0; c < COLUMNS; c++) {
				int sx = gridX + c * SLOT;
				int index = (r + 1) * COLUMNS + c;
				ItemStack stack = index < items.size() ? items.get(index) : ItemStack.EMPTY;
				if (stack.isEmpty()) {
					continue;
				}
				if (isFiller(stack)) {
					if (!card.live()) {
						// a slot the backpack does not have: darker than an empty slot, so it is clear nothing can go there
						ui.rounded(sx, rowY, SLOT, SLOT, Radius.XS, Draw.withOpacity(0xFF000000, 0.35f));
					}
					continue;
				}
				if (!card.live()) {
					// the open page's vanilla slots draw their live items themselves; only the search's ring is added to them
					graphics.item(stack, sx + 1, rowY + 1);
					// draw count and durability bar only when present: each adds sorting work for the game every frame
					if (stack.getCount() > 1 || stack.isBarVisible()) {
						graphics.itemDecorations(client.font, stack, sx + 1, rowY + 1);
					}
					SkyBlockFeatures.PET_CANDY.drawOn(ui, stack, sx + 1, rowY + 1, SLOT - 2);
					if (mouseX >= sx && mouseX < sx + SLOT && mouseY >= rowY && mouseY < rowY + SLOT && mouseY >= areaTop && mouseY < areaBottom) {
						hovered = stack;
					}
				}
				searchMark(ui, stack, sx, rowY);
			}
		}
		return hovered;
	}

	/** Draws the lock and "Not opened yet" over the empty grid of a page never opened. */
	static void drawLocked(Canvas ui, float centerX, float centerY) {
		String text = "Not opened yet";
		float width = 11 + ui.widthScaled(text, TextScale.SMALL);
		float x = centerX - width / 2f;
		ui.roundedSmooth(x - 6, centerY - 7, width + 12, 14, Radius.SM, Draw.withOpacity(HudTheme.text(), 0.08f));
		ui.icon("lock", Math.round(x), Math.round(centerY - 4), 8, HudTheme.secondary());
		ui.textAtScaled(text, x + 11, centerY - 3.5f, TextScale.SMALL, HudTheme.secondary());
	}

	/** Marks an item for the search: a ring and soft glow in the accent on a match, the rest dimmed, fading with the search. */
	private static void searchMark(Canvas ui, ItemStack stack, int x, int y) {
		if (search.isEmpty() || searchShown <= 0.01f) {
			return;
		}
		if (stats.matches(stack, search)) {
			ui.softLight(x + SLOT / 2f, y + SLOT / 2f, SLOT * 0.75f, Draw.withOpacity(ui.accent(), 0.35f * searchShown));
			ui.ring(x, y, SLOT, SLOT, Radius.XS, 1, Draw.withOpacity(ui.accent(), searchShown));
		} else {
			ui.fill(x, y, SLOT, SLOT, Draw.withOpacity(0xB0101014, searchShown));
		}
	}

	/** @return a page's card name: "Ender chest 3", or "Backpack 2" with the backpack's own name ("Backpack 2 · Jumbo Backpack") */
	static String cardName(String key) {
		StorageTracker.Page page = StorageTracker.page(key);
		String title = page == null || page.title == null ? "" : page.title;
		String[] cached = names.get(key);
		if (cached == null || !cached[0].equals(title)) {
			String number = key.substring(1);
			String name = key.startsWith("e") ? "Ender chest " + number : "Backpack " + number;
			if (key.startsWith("b")) {
				String own = TITLE_NUMBER.matcher(TextColors.noColors(title)).replaceAll("").strip();
				if (!own.isEmpty() && !own.equalsIgnoreCase("Backpack")) {
					name += " · " + own;
				}
			}
			cached = new String[]{title, name};
			names.put(key, cached);
		}
		return cached[1];
	}

	/**
	 * Draws the inventory panel below the window: "Inventory" small at the top, the three main rows and the hotbar a little apart. The game's own
	 * slots draw the items on it, with their counts and durability as everywhere else.
	 */
	private static void drawInventoryCard(Canvas ui) {
		int[] box = inventoryCard;
		HudTheme.panel(ui, box[0], box[1], box[2], box[3], Radius.XL, HudTheme.opacity());
		ui.textAtScaled("Inventory", box[0] + INVENTORY_PAD, box[1] + INVENTORY_PAD - 2f, TextScale.SMALL, HudTheme.secondary());
		int x = box[0] + INVENTORY_PAD;
		int y = box[1] + INVENTORY_PAD + INVENTORY_TITLE;
		ui.slotGrid(x, y, COLUMNS, INVENTORY_ROWS, SLOT, SLOT, 4f, HudTheme.slot());
		ui.slotGrid(x, y + INVENTORY_ROWS * SLOT + HOTBAR_GAP, COLUMNS, 1, SLOT, SLOT, 4f, HudTheme.slot());
	}

	/**
	 * @return whether a stack is the server's filler in a slot that a backpack or ender chest page does not have: a glass pane without name or
	 *         SkyBlock id
	 */
	static boolean isFiller(ItemStack stack) {
		if (stack.isEmpty() || ItemGains.idOf(stack) != null) {
			return false;
		}
		String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return item.endsWith("glass_pane") && TextColors.noColors(stack.getHoverName().getString()).isBlank();
	}

	/** @return the rows a page actually has: trailing rows consisting only of filler are not counted (the server's first row is excluded) */
	private static int usedRows(List<ItemStack> items, int rows) {
		for (int r = rows - 1; r >= 0; r--) {
			for (int c = 0; c < COLUMNS; c++) {
				int index = (r + 1) * COLUMNS + c;
				ItemStack stack = index < items.size() ? items.get(index) : ItemStack.EMPTY;
				if (!isFiller(stack)) {
					return r + 1;
				}
			}
		}
		// all filler (a page not filled yet): keep it as is
		return rows;
	}

	/** The open menu's items, collected again only when the menu changes, so the page's numbers are cached too. */
	private static final List<ItemStack> menuItems = new ArrayList<>();
	private static Object menuItemsFor;
	private static int menuItemsState = -1;

	/** @return the contents of the open menu's slots, excluding the player inventory */
	private static List<ItemStack> menuItems(ContainerScreen screen) {
		int state = screen.getMenu().getStateId();
		if (screen != menuItemsFor || state != menuItemsState) {
			menuItemsFor = screen;
			menuItemsState = state;
			menuItems.clear();
			for (Slot slot : screen.getMenu().slots) {
				if (!(slot.container instanceof Inventory)) {
					menuItems.add(slot.getItem());
				}
			}
		}
		return menuItems;
	}

	/** @return whether a point is on a visible vanilla slot, where the click belongs to the game */
	private static boolean onSlot(ContainerScreen screen, double mouseX, double mouseY) {
		for (Slot slot : screen.getMenu().slots) {
			if (slot.x > OFF / 2 && mouseX >= slot.x - 1 && mouseX < slot.x + 17 && mouseY >= slot.y - 1 && mouseY < slot.y + 17) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Handles a click: on a slot it is left to the game unchanged; on the card of a page that is not open it opens the page; on the search it
	 * focuses it (its clear button or a right-click clears it); on the filter it picks the pages shown. @return whether the overlay consumed it
	 */
	public static boolean mouseClicked(ContainerScreen screen, double mouseX, double mouseY, int button) {
		if (onSlot(screen, mouseX, mouseY)) {
			searchFocused = false;
			return false;
		}
		if (PanelKit.inside(searchBox, mouseX, mouseY)) {
			if (button == 1 || !search.isEmpty() && PanelKit.inside(PanelKit.clearBox(searchBox), mouseX, mouseY)) {
				search = "";
				scrollTarget = 0;
			}
			searchFocused = true;
			SEARCH_SELECTION.clear();
			return true;
		}
		searchFocused = false;
		if (button != 0) {
			return true;
		}
		if (SettingsButton.clicked(settingsBox, screen, SkyBlockFeatures.STORAGE, mouseX, mouseY) || storages.click(screen, mouseX, mouseY)) {
			return true;
		}
		for (int i = 0; i < filterBoxes.length; i++) {
			if (PanelKit.inside(filterBoxes[i], mouseX, mouseY)) {
				if (filter != i) {
					filter = i;
					scrollTarget = 0;
					UiSounds.click();
				}
				return true;
			}
		}
		if (mouseY < areaTop || mouseY >= areaBottom) {
			return true; // on the overlay but on nothing: consume it, so it is not a click outside the chest, which would drop the held item
		}
		String open = openKey(screen);
		for (Card card : cards) {
			if (card.contains(mouseX, mouseY) && !card.key().equals(open)) {
				openPage(screen, card.key());
				return true;
			}
		}
		return true;
	}

	/** Opens a page: in the storage menu by clicking its item as the player would, from a page with the SkyBlock command for it. */
	static void openPage(ContainerScreen screen, String key) {
		Minecraft client = Minecraft.getInstance();
		UiSounds.click();
		if (isMenu(screen)) {
			for (int i = 0; i < screen.getMenu().slots.size(); i++) {
				ItemStack stack = screen.getMenu().slots.get(i).getItem();
				if (!stack.isEmpty() && key.equals(StorageTracker.keyOfMenuItem(TextColors.noColors(stack.getHoverName().getString()).strip()))) {
					client.gameMode.handleContainerInput(screen.getMenu().containerId, i, 0, ContainerInput.PICKUP, client.player);
					// The game assumes the item was picked up and shows it on the cursor until the server opens the page: it never was, since a click in
					// this menu only opens a page, so nothing is held.
					screen.getMenu().setCarried(ItemStack.EMPTY);
					return;
				}
			}
			return;
		}
		sendOpenCommand(key);
	}

	/** Sends the one command that opens a page, like typing it: /ec with the page's number, /bp with the backpack's. @return whether it was sent */
	static boolean sendOpenCommand(String key) {
		Minecraft client = Minecraft.getInstance();
		// the key comes from the saved storage file: only a plain page number becomes part of the command
		if (client.getConnection() != null && key.length() > 1 && CommandText.safeArgument(key.substring(1))) {
			UiSounds.click();
			client.getConnection().sendCommand((key.startsWith("e") ? "ec " : "bp ") + key.substring(1));
			return true;
		}
		return false;
	}

	public static boolean mouseScrolled(double scrollY) {
		scrollTarget = Math.max(0, scrollTarget - scrollY * SCROLL_STEP);
		return true;
	}

	/** @return whether the search consumed the key, which it does while focused */
	public static boolean keyPressed(int key, int modifiers) {
		if (!searchFocused) {
			return false;
		}
		if (SEARCH_SELECTION.key(key, modifiers, () -> search, value -> {
			search = value;
			scrollTarget = 0;
		}, SEARCH_MOST)) {
			return true;
		}
		if (key == InputConstants.KEY_BACKSPACE) {
			if (!search.isEmpty()) {
				search = search.substring(0, search.length() - 1);
			}
			return true;
		}
		if (key == InputConstants.KEY_ESCAPE && !search.isEmpty()) {
			// Esc clears the search first, and lets go of the field once it is empty
			search = "";
			scrollTarget = 0;
			return true;
		}
		if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			searchFocused = false;
			return true;
		}
		// every other key goes to the search (including the inventory key, which would close the screen); characters arrive separately
		return true;
	}

	/** @return whether the search consumed the character */
	public static boolean charTyped(int codepoint) {
		if (!searchFocused || codepoint < 32) {
			return searchFocused;
		}
		String next = SEARCH_SELECTION.typed(search, new String(Character.toChars(codepoint)));
		if (next.length() <= SEARCH_MOST) {
			search = next;
			scrollTarget = 0;
		}
		return true;
	}

	/** Fills the search in for the next time the overlay opens: the item detail's "Find in storage". */
	public static void presetSearch(String text) {
		search = text.length() > SEARCH_MOST ? text.substring(0, SEARCH_MOST) : text;
		scrollTarget = 0;
		filter = 0;
	}

	/** Select-all state of the search field. */
	private static final TextSelection SEARCH_SELECTION = new TextSelection();

	/** @return whether the search has focus */
	public static boolean searching() {
		return searchFocused;
	}
}
