package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.hud.GridSnap;
import dev.tim1337.lumio.api.experimental.hud.HudEditor;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.api.experimental.ui.UiSounds;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.skyblock.data.ItemRepo;
import dev.tim1337.lumio.skyblock.mixin.accessor.ContainerScreenAccess;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

/**
 * The item search's panels on an open container screen: where they go this frame (the player's places, or the starting layout, fitted to the screen and
 * around the inventory and the other overlays), drawing them, and what the mouse does on them. A panel moves by its grip only and is pulled by an edge or
 * corner, with no edit mode; Pinned and Recent fold up to their header bar by its arrow, keeping their full height for when they open again: it lifts while held and snaps to the grid and the screen's center, as in the HUD editor (the grid shows meanwhile, accent
 * lines mark the grid lines it sits on; Shift places it freely), and its place is kept per kind of window. Items are dragged from Items, Recent or the details window onto Pinned; a pinned item
 * dragged out of it is taken off. A click opens the details window next to what was clicked, or in its own panel when that is switched on.
 */
final class ItemPanels {
	/** How far the mouse has to move before a press becomes a drag. */
	private static final int DRAG_START = 4;
	private static final float ITEM_SIZE_STEP = 0.05f;
	private static final int GUIDE_LABEL_HEIGHT = 12;
	private static final int BADGE_HEIGHT = 13;
	/** How strong the accent lines on the grid are, as in the HUD editor. */
	private static final float GUIDE_OPACITY = 0.85f;
	/** The panels from the top of the drawing down: the first one under the mouse takes a click. */
	private static final ItemPanelKind[] TOP_FIRST = {ItemPanelKind.SEARCH, ItemPanelKind.DETAILS, ItemPanelKind.ITEMS, ItemPanelKind.PINNED,
		ItemPanelKind.RECENT};

	/** What a press of the mouse is doing. */
	private enum Mode {
		NONE, PRESS_PANEL, MOVE, RESIZE, PRESS_ITEM, DRAG_ITEM
	}

	private final ItemListFeature feature;
	final SearchBarPanel bar;
	final ItemsPanel items;
	final PinnedPanel pinned;
	final RecentPanel recent;
	final ItemSearchSettings settings;
	private final ItemHoverCard card;
	private final ItemDetailView detail;

	private int screenWidth;
	private int screenHeight;
	private final int[] gui = new int[4];
	private String windowKind = PanelPlaces.INVENTORY;
	private ItemPanelLayout.Fit fit = new ItemPanelLayout.Fit();
	private long fitKey = Long.MIN_VALUE;
	/** The details window as a popup: where, and what was clicked to open it. */
	private int[] popup;
	/** Whether the popup lies over the inventory or chest window (its slots are away meanwhile). */
	private boolean popupOverGui;
	private final int[] popupAnchor = new int[4];

	private Mode mode = Mode.NONE;
	private ItemPanelKind held;
	private int edges;
	private double pressX;
	private double pressY;
	private int[] startBox;
	private int[] dragBox;
	/** A collapsed panel's header boxes, one per kind (made once, filled per frame), and the full height of the held one: its place keeps that height. */
	private final int[][] headerBoxes = new int[ItemPanelKind.values().length][4];
	/** How far down each panel's content went last frame, and the areas fitted to it (see {@link #box}). */
	private final int[] usedHeights = new int[ItemPanelKind.values().length];
	private final int[][] areaBoxes = new int[ItemPanelKind.values().length][4];
	private int heldFullHeight;
	private List<PanelSnap.Guide> guides = List.of();
	/** The HUD editor's grid and snapping, started anew with every panel picked up. */
	private final GridSnap grid = HudEditor.gridSnap();
	private String badge = "";
	private String dragId;
	private ItemPanelKind dragFrom;

	private ItemRepo.Entry hovered;
	private ItemPanelKind hoveredIn;
	private final int[] hoveredTile = new int[4];
	private boolean drawn;
	/** How far the Items panel is shown: it fades in and out with "Only when searching". */
	private final Spring itemsShown = Spring.smooth(Spring.SNAPPY);
	/** Below this the Items panel is not drawn, and below the second it takes no clicks. */
	private static final float HIDDEN_ALPHA = 0.01f;
	private static final float SHOWN_ALPHA = 0.5f;

	ItemPanels(ItemListFeature feature, ItemDetailView detail) {
		this.feature = feature;
		this.detail = detail;
		bar = new SearchBarPanel(feature);
		items = new ItemsPanel(feature);
		pinned = new PinnedPanel(feature);
		recent = new RecentPanel(feature);
		settings = new ItemSearchSettings(feature);
		card = new ItemHoverCard(feature);
	}

	/**
	 * @param asTab whether Pinned is a tab of the Items panel, which then always shows
	 * @return whether the Items panel shows: always, or with "Only when searching" once something is typed
	 */
	private boolean itemsWanted(boolean asTab) {
		return asTab || !feature.onlyWhileSearching() || !feature.search().isBlank();
	}

	/** @return whether any panel was drawn in the last frame (the HUD is not drawn under them, nor the game's effects list beside the inventory) */
	boolean drawn() {
		return drawn;
	}

	ItemRepo.Entry hovered() {
		return hovered;
	}

	/** @return whether something is held by the mouse: a panel or an item */
	boolean busy() {
		return mode != Mode.NONE;
	}

	/** Forgets a held panel or item and closes the menus, as a screen opens. */
	void reset() {
		mode = Mode.NONE;
		settings.close();
		pinned.finishRename(false);
		pinned.dragOver(false, 0, 0);
		popup = null;
	}

	// ------------------------------------------------------------------------------------------------ where things go

	/** @return the kind of window a screen is, for its layout */
	static String windowKind(AbstractContainerScreen<?> screen) {
		if (StorageOverlay.active(screen)) {
			return "storage";
		}
		if (WardrobeOverlay.active(screen)) {
			return "wardrobe";
		}
		return PanelPlaces.windowKind(screen instanceof InventoryScreen, screen instanceof ContainerScreen chest ? chest.getMenu().getRowCount() : 0);
	}

	private void place(AbstractContainerScreen<?> screen) {
		screenWidth = screen.width;
		screenHeight = screen.height;
		int[] named = ScreenRoom.namedWindow();
		if (named != null) {
			System.arraycopy(named, 0, gui, 0, 4);
		} else {
			var access = (ContainerScreenAccess) screen;
			gui[0] = access.evskyblock$left();
			gui[1] = access.evskyblock$top();
			gui[2] = access.evskyblock$width();
			gui[3] = access.evskyblock$height();
		}
		windowKind = windowKind(screen);
		List<int[]> obstacles = ScreenRoom.taken();
		long key = fitKey(obstacles);
		if (key == fitKey) {
			return;
		}
		fitKey = key;
		EnumMap<ItemPanelKind, int[]> wanted = wanted(true);
		fit = ItemPanelLayout.fit(screenWidth, screenHeight, gui, obstacles, wanted, feature.itemSize());
	}

	/** @return where each shown panel wants to be: the player's place, or the starting layout's; without the one held, which is drawn where it is held */
	private EnumMap<ItemPanelKind, int[]> wanted(boolean withoutHeld) {
		EnumMap<ItemPanelKind, int[]> preset = null;
		EnumMap<ItemPanelKind, int[]> wanted = new EnumMap<>(ItemPanelKind.class);
		for (ItemPanelKind kind : ItemPanelKind.values()) {
			if (!feature.panelOn(kind) || withoutHeld && kind == held && (mode == Mode.MOVE || mode == Mode.RESIZE)) {
				continue;
			}
			PanelPlace place = feature.places().get(windowKind, kind);
			if (place != null) {
				wanted.put(kind, place.box(screenWidth, screenHeight, gui));
				continue;
			}
			if (preset == null) {
				preset = ItemPanelLayout.starting(screenWidth, screenHeight, gui, feature.itemSize(), feature.panelOn(ItemPanelKind.DETAILS));
			}
			int[] box = preset.get(kind);
			if (box != null) {
				wanted.put(kind, box);
			}
		}
		return wanted;
	}

	/** @return a number for everything the fit depends on, so it is made again only when one changes */
	private long fitKey(List<int[]> obstacles) {
		long key = 17L;
		key = key * 31 + screenWidth;
		key = key * 31 + screenHeight;
		for (int value : gui) {
			key = key * 31 + value;
		}
		for (int i = 0; i < obstacles.size(); i++) {
			int[] box = obstacles.get(i);
			for (int value : box) {
				key = key * 31 + value;
			}
		}
		key = key * 31 + windowKind.hashCode();
		key = key * 31 + feature.layoutVersion();
		key = key * 31 + (held == null ? -1 : held.ordinal()) * 7L + mode.ordinal();
		return key;
	}

	/**
	 * @return a panel's area this frame, for the mouse and its resize corner: Pinned, Recent and Items as far down as their content went last
	 *         frame (an empty part under it is the game's, not the panel's); the others, a panel held and a folded one as {@link #layoutBox}
	 */
	int[] box(ItemPanelKind kind) {
		int[] full = layoutBox(kind);
		int used = usedHeights[kind.ordinal()];
		boolean held = kind == this.held && (mode == Mode.MOVE || mode == Mode.RESIZE);
		if (full == null || used <= 0 || !kind.fitsContent() || held || feature.collapsed(kind)) {
			return full;
		}
		int[] area = areaBoxes[kind.ordinal()];
		area[0] = full[0];
		area[1] = full[1];
		area[2] = full[2];
		area[3] = Math.clamp(used, PanelFrame.HEADER, full[3]);
		return area;
	}

	/** Notes how far down a panel's content went as it was drawn, with a little room under it. */
	private void noteUsed(ItemPanelKind kind, int[] box) {
		usedHeights[kind.ordinal()] = PanelFrame.contentBottom() + PanelFrame.PAD / 2 - box[1];
	}

	/** @return a panel's whole box this frame, where its content is laid out, or null when it is not drawn; a collapsed panel's is only its header */
	int[] layoutBox(ItemPanelKind kind) {
		if (kind == held && dragBox != null && (mode == Mode.MOVE || mode == Mode.RESIZE)) {
			return dragBox;
		}
		int[] full = fit.box(kind);
		if (full == null || !feature.collapsed(kind)) {
			return full;
		}
		int[] header = headerBoxes[kind.ordinal()];
		header[0] = full[0];
		header[1] = full[1];
		header[2] = full[2];
		header[3] = PanelFrame.BAR;
		return header;
	}

	/** Lays the layout out again next frame: a setting or a place changed. */
	void relayout() {
		fitKey = Long.MIN_VALUE;
	}

	// ------------------------------------------------------------------------------------------------ drawing

	void draw(Canvas ui, AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, float dt, int mouseX, int mouseY) {
		place(screen);
		follow(mouseX, mouseY);
		PanelFrame.pointer(mouseX, mouseY, !feature.layoutLocked() && (mode == Mode.NONE || mode == Mode.MOVE || mode == Mode.RESIZE));
		hovered = null;
		hoveredIn = null;
		drawn = true;
		if (startBox != null && (mode == Mode.MOVE || mode == Mode.RESIZE)) {
			// the grid shows only while a panel is dragged or pulled, and only while it snaps to it
			if (snapping()) {
				HudEditor.drawGrid(ui, screenWidth, screenHeight);
			}
			PanelFrame.ghost(ui, startBox);
		}
		boolean asTab = fit.pinnedAsTab && feature.panelOn(ItemPanelKind.PINNED);
		int[] pinnedBox = layoutBox(ItemPanelKind.PINNED);
		if (pinnedBox != null) {
			graphics.nextStratum();
			PanelFrame.useArea(box(ItemPanelKind.PINNED));
			take(pinned.draw(ui, graphics, pinnedBox, lift(ItemPanelKind.PINNED), lifted(ItemPanelKind.PINNED), dt, mouseX, mouseY), ItemPanelKind.PINNED,
				pinned.hoveredTile());
			noteUsed(ItemPanelKind.PINNED, pinnedBox);
		}
		int[] recentBox = layoutBox(ItemPanelKind.RECENT);
		if (recentBox != null) {
			graphics.nextStratum();
			PanelFrame.useArea(box(ItemPanelKind.RECENT));
			take(recent.draw(ui, graphics, recentBox, lift(ItemPanelKind.RECENT), lifted(ItemPanelKind.RECENT), mouseX, mouseY), ItemPanelKind.RECENT,
				recent.hoveredTile());
			noteUsed(ItemPanelKind.RECENT, recentBox);
		}
		int[] itemsBox = layoutBox(ItemPanelKind.ITEMS);
		// with "Only when searching" the panel is away until something is typed; it keeps its place meanwhile
		float itemsAlpha = itemsShown.step(itemsWanted(asTab) ? 1f : 0f, dt);
		if (itemsBox != null && itemsAlpha > HIDDEN_ALPHA) {
			graphics.nextStratum();
			float alphaBefore = Draw.alpha();
			Draw.setAlpha(alphaBefore * itemsAlpha);
			PanelFrame.useArea(box(ItemPanelKind.ITEMS));
			ItemRepo.Entry entry = items.draw(ui, graphics, itemsBox, asTab, pinned, lift(ItemPanelKind.ITEMS), lifted(ItemPanelKind.ITEMS), dt, mouseX, mouseY);
			noteUsed(ItemPanelKind.ITEMS, itemsBox);
			Draw.setAlpha(alphaBefore);
			if (itemsAlpha > SHOWN_ALPHA) {
				take(entry, items.pinnedTab() ? ItemPanelKind.PINNED : ItemPanelKind.ITEMS, items.pinnedTab() ? pinned.hoveredTile() : items.hoveredTile());
			}
		}
		int[] docked = box(ItemPanelKind.DETAILS);
		if (docked != null) {
			graphics.nextStratum();
			if (detail.id() == null) {
				PanelFrame.draw(ui, docked, "window", HudTheme.text(), "Details", "", lift(ItemPanelKind.DETAILS), lifted(ItemPanelKind.DETAILS), true);
				ui.textAtScaled(ui.fitScaled("Click an item to see its details", docked[2] - 2 * PanelFrame.PAD, TextScale.SMALL), docked[0] + PanelFrame.PAD,
					docked[1] + PanelFrame.HEADER + 4, TextScale.SMALL, HudTheme.secondary());
			} else {
				ItemRepo.Entry inWindow = detail.draw(ui, graphics, docked, false, gui, dt, mouseX, mouseY);
				take(inWindow, ItemPanelKind.DETAILS, null);
			}
		}
		int[] search = box(ItemPanelKind.SEARCH);
		if (search != null) {
			graphics.nextStratum();
			dev.tim1337.lumio.api.experimental.util.TooltipRoom.keepClear(search[0], search[1], search[2], search[3]);
			bar.draw(ui, graphics, search, settings.open(), !feature.layoutLocked(), lift(ItemPanelKind.SEARCH), mouseX, mouseY);
		}
		drawPopup(ui, screen, graphics, dt, mouseX, mouseY);
		if (search == null) {
			settings.close();
		}
		settings.draw(ui, bar.settingsButton(), screenWidth, screenHeight, mouseX, mouseY);
		if (settings.contains(mouseX, mouseY)) {
			hovered = null;
		}
		drawDrag(ui, graphics, mouseX, mouseY);
		if (hovered != null && mode == Mode.NONE) {
			switch (feature.onHover()) {
				case TOOLTIP -> {
					// the item's real tooltip, as in the inventory; the search's own lines are added to its block of added lines
					feature.hover().hover(hovered);
					ItemSearchHover.drawTooltip(graphics, hovered.stack(), mouseX, mouseY);
				}
				case CARD -> card.draw(ui, hovered, mouseX, mouseY, screenWidth, screenHeight);
				case NONE -> {
				}
			}
		}
	}

	/** The details window opened beside a click: where there is room next to it, or else over the GUI, whose slots are then away. */
	private void drawPopup(Canvas ui, AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, float dt, int mouseX, int mouseY) {
		if (detail.id() == null || box(ItemPanelKind.DETAILS) != null) {
			popup = null;
			return;
		}
		if (popup == null) {
			// placed once, clear of every panel; it keeps this place while entries inside it are opened
			ItemWindowPlace.Place place = ItemWindowPlace.place(popupAnchor, ItemListLayout.DETAIL_WIDTH, ItemListLayout.DETAIL_HEIGHT,
				ItemListLayout.DETAIL_LEAST_WIDTH, ItemListLayout.DETAIL_LEAST_HEIGHT, screenWidth, screenHeight, gui, panelBoxes());
			popup = place == null ? ItemListLayout.detail(screenWidth, screenHeight, gui, screenWidth) : place.box();
			popupOverGui = place == null || place.overGui();
		}
		boolean overGui = popupOverGui;
		int[] where = popup;
		if (overGui) {
			feature.hideSlots(screen);
		}
		graphics.nextStratum();
		ItemRepo.Entry inWindow = detail.draw(ui, graphics, where, overGui, gui, dt, mouseX, mouseY);
		if (detail.contains(mouseX, mouseY)) {
			// the window lies over the panels: only what is under the mouse in it counts
			hovered = inWindow;
			hoveredIn = ItemPanelKind.DETAILS;
		}
	}

	/** @return every box the item window stays clear of: the item search's panels, the settings window and the screen's other overlays */
	private List<int[]> panelBoxes() {
		List<int[]> boxes = new ArrayList<>(ScreenRoom.taken());
		for (ItemPanelKind kind : ItemPanelKind.values()) {
			int[] box = kind == ItemPanelKind.ITEMS && itemsShown.value() < SHOWN_ALPHA ? null : box(kind);
			if (box != null) {
				boxes.add(box);
			}
		}
		int[] settingsBox = settings.box();
		if (settingsBox != null) {
			boxes.add(settingsBox);
		}
		return boxes;
	}

	private void take(ItemRepo.Entry entry, ItemPanelKind kind, int[] tile) {
		if (entry != null) {
			hovered = entry;
			hoveredIn = kind;
			if (tile != null) {
				System.arraycopy(tile, 0, hoveredTile, 0, 4);
			}
		}
	}

	private float lift(ItemPanelKind kind) {
		return lifted(kind) ? 1f : 0f;
	}

	private boolean lifted(ItemPanelKind kind) {
		return held == kind && (mode == Mode.MOVE || mode == Mode.RESIZE);
	}

	/** The guides of a snap, the size badge, and an item being dragged with where it would land. */
	private void drawDrag(Canvas ui, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (mode == Mode.MOVE || mode == Mode.RESIZE) {
			graphics.nextStratum();
			// the grid lines (or the screen's center line) the panel sits on, across the screen
			int line = Draw.withOpacity(ui.accent(), GUIDE_OPACITY);
			for (PanelSnap.Guide guide : guides) {
				if (guide.vertical()) {
					ui.fill(guide.at(), 0, 1, screenHeight, line);
				} else {
					ui.fill(0, guide.at(), screenWidth, 1, line);
				}
			}
			if (!guides.isEmpty() && dragBox != null) {
				label(ui, guides.getFirst().label(), dragBox[0] + dragBox[2] / 2, dragBox[1] - GUIDE_LABEL_HEIGHT - 3, GUIDE_LABEL_HEIGHT);
			}
			if (!badge.isEmpty() && dragBox != null) {
				label(ui, badge, dragBox[0] + dragBox[2], dragBox[1] + dragBox[3] + 4, BADGE_HEIGHT);
			}
		}
		if (mode == Mode.DRAG_ITEM && dragId != null) {
			ItemRepo.Entry entry = ItemRepo.get(dragId);
			if (entry != null) {
				graphics.nextStratum();
				int size = ItemGrid.tile(feature.itemSize()) + 4;
				ItemTiles.draw(ui, graphics, entry, mouseX - size / 2, mouseY - size / 2, size, 1f, 1f);
				String drop = pinned.dropLabel(feature.pinned().has(dragId));
				if (drop != null) {
					label(ui, drop, mouseX + size / 2 + 4 + (int) (ui.boldWidthScaled(drop, TextScale.SMALL) / 2), mouseY - GUIDE_LABEL_HEIGHT / 2,
						GUIDE_LABEL_HEIGHT);
				} else if (dragFrom == ItemPanelKind.PINNED) {
					label(ui, "Unpin", mouseX + size / 2 + 20, mouseY - GUIDE_LABEL_HEIGHT / 2, GUIDE_LABEL_HEIGHT);
				}
			}
		}
	}

	/** A small accent label centered on x: that a panel snaps to the grid, its size, where an item lands. */
	private static void label(Canvas ui, String text, int centerX, int y, int height) {
		float width = ui.boldWidthScaled(text, TextScale.SMALL) + 10;
		float x = centerX - width / 2f;
		ui.roundedSmooth(x, y, width, height, Radius.box(height), ui.accent());
		ui.textAtScaledBold(text, x + 5, y + height / 2f - 3.5f, TextScale.SMALL, 0xFFFFFFFF);
	}

	// ------------------------------------------------------------------------------------------------ the mouse

	/** @return the panel under a point, topmost first, or null */
	private ItemPanelKind panelAt(double mouseX, double mouseY) {
		for (ItemPanelKind kind : TOP_FIRST) {
			if (kind == ItemPanelKind.ITEMS && itemsShown.value() < SHOWN_ALPHA) {
				continue;
			}
			int[] box = box(kind);
			if (box != null && (PanelKit.inside(box, mouseX, mouseY) || PanelFrame.edgesAt(box, mouseX, mouseY, kind.tall() && !feature.collapsed(kind)) != 0)) {
				return kind;
			}
		}
		return null;
	}

	/** @return whether a point is on something of the panels: a panel, the settings window, the popup */
	boolean contains(double mouseX, double mouseY) {
		return panelAt(mouseX, mouseY) != null || settings.contains(mouseX, mouseY) || bar.contains(mouseX, mouseY) || detail.contains(mouseX, mouseY);
	}

	/** A press of a mouse button. @return whether the panels took it */
	boolean press(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button) {
		if (settings.click(mouseX, mouseY)) {
			UiSounds.click();
			return true;
		}
		if (detail.id() != null && detail.contains(mouseX, mouseY)) {
			if (hovered != null && hoveredIn == ItemPanelKind.DETAILS && button == ScreenInput.LEFT && !detail.onControl(mouseX, mouseY)) {
				// an item in the window: a click opens it there, a drag takes it to Pinned
				startItem(hovered.id, ItemPanelKind.DETAILS, mouseX, mouseY);
				return true;
			}
			if (detail.click(mouseX, mouseY)) {
				UiSounds.click();
			}
			return true;
		}
		ItemPanelKind kind = panelAt(mouseX, mouseY);
		if (kind == null) {
			feature.unfocusSearch();
			pinned.finishRename(true);
			if (popup != null || detail.id() != null && box(ItemPanelKind.DETAILS) == null) {
				// beside it all, the popup closes; the click is used for that, so nothing is thrown out of the inventory by it
				detail.close();
				popup = null;
				return true;
			}
			return false;
		}
		int[] box = box(kind);
		if (kind == ItemPanelKind.SEARCH) {
			return pressBar(box, mouseX, mouseY, button);
		}
		feature.unfocusSearch();
		boolean free = !feature.layoutLocked() && button == ScreenInput.LEFT;
		boolean collapsed = feature.collapsed(kind);
		if (kind.collapses() && button == ScreenInput.LEFT && PanelFrame.onCollapse(box, mouseX, mouseY)) {
			feature.setCollapsed(kind, !collapsed);
			UiSounds.click();
			return true;
		}
		if (PanelFrame.onGrip(box, mouseX, mouseY)) {
			// the grip is the one place a panel is picked up by
			if (free) {
				startPanel(kind, Mode.PRESS_PANEL, 0, box, mouseX, mouseY);
			}
			return true;
		}
		int edgesHere = free ? PanelFrame.edgesAt(box, mouseX, mouseY, kind.tall() && !collapsed) : 0;
		if (edgesHere != 0) {
			startPanel(kind, Mode.RESIZE, edgesHere, box, mouseX, mouseY);
			return true;
		}
		if (collapsed) {
			// a folded-up panel is only its header bar: a click on it opens the panel again
			if (button == ScreenInput.LEFT) {
				feature.setCollapsed(kind, false);
				UiSounds.click();
			}
			return true;
		}
		if (kind == ItemPanelKind.ITEMS && items.click(mouseX, mouseY, fit.pinnedAsTab) || kind == ItemPanelKind.ITEMS && items.pinnedTab()
			&& pinned.click(mouseX, mouseY, button) || kind == ItemPanelKind.PINNED && pinned.click(mouseX, mouseY, button)) {
			UiSounds.click();
			return true;
		}
		if (kind == ItemPanelKind.DETAILS && detail.id() != null && detail.click(mouseX, mouseY)) {
			UiSounds.click();
			return true;
		}
		if (mouseY < box[1] + PanelFrame.HEADER) {
			// the rest of the header takes the click and does nothing: only the grip moves a panel
			return true;
		}
		if (hovered != null && hoveredIn != ItemPanelKind.DETAILS && button == ScreenInput.LEFT) {
			startItem(hovered.id, hoveredIn, mouseX, mouseY);
			return true;
		}
		if (hovered != null && button == ScreenInput.RIGHT && hoveredIn == ItemPanelKind.PINNED && feature.pinned().remove(hovered.id)) {
			feature.pinnedChanged();
			UiSounds.click();
		}
		return true;
	}

	/** A press on the search bar: its Settings button, its grip (a move), its sides (wider or narrower), the clear button or the field (focus, right-click clears, a double click keeps the slots lit). */
	private boolean pressBar(int[] box, double mouseX, double mouseY, int button) {
		if (PanelKit.inside(bar.settingsButton(), mouseX, mouseY)) {
			settings.toggle();
			UiSounds.click();
			return true;
		}
		boolean free = !feature.layoutLocked() && button == ScreenInput.LEFT;
		// the grip moves the bar; a click on the field only focuses the search
		if (free && bar.onGrip(mouseX, mouseY)) {
			feature.unfocusSearch();
			startPanel(ItemPanelKind.SEARCH, Mode.PRESS_PANEL, 0, box, mouseX, mouseY);
			return true;
		}
		// its sides pull it wider or narrower: just at its edge, so the field itself stays easy to click
		int edgesHere = free && (mouseX < box[0] + 2 || mouseX >= box[0] + box[2] - 2) ? PanelFrame.edgesAt(box, mouseX, mouseY, false)
			& (PanelFrame.LEFT | PanelFrame.RIGHT) : 0;
		if (edgesHere != 0) {
			startPanel(ItemPanelKind.SEARCH, Mode.RESIZE, edgesHere, box, mouseX, mouseY);
			return true;
		}
		if (bar.onClear(mouseX, mouseY)) {
			feature.setSearch("");
		} else if (button == ScreenInput.RIGHT) {
			feature.rightClickSearch();
		} else {
			feature.clickSearch();
		}
		feature.focusSearch();
		return true;
	}

	private void startPanel(ItemPanelKind kind, Mode how, int pulled, int[] box, double mouseX, double mouseY) {
		mode = how;
		held = kind;
		edges = pulled;
		pressX = mouseX;
		pressY = mouseY;
		startBox = box.clone();
		int[] full = fit.box(kind);
		heldFullHeight = full == null ? box[3] : full[3];
		dragBox = box.clone();
		guides = List.of();
		badge = "";
		settings.close();
		grid.begin(screenWidth, screenHeight);
	}

	private void startItem(String id, ItemPanelKind from, double mouseX, double mouseY) {
		mode = Mode.PRESS_ITEM;
		dragId = id;
		dragFrom = from;
		pressX = mouseX;
		pressY = mouseY;
		System.arraycopy(hoveredTile, 0, popupAnchor, 0, 4);
	}

	/** Follows the mouse while something is held: a panel moves or is pulled, an item is carried. */
	private void follow(int mouseX, int mouseY) {
		boolean moved = Math.abs(mouseX - pressX) > DRAG_START || Math.abs(mouseY - pressY) > DRAG_START;
		if (mode == Mode.PRESS_PANEL && moved) {
			mode = Mode.MOVE;
			relayout();
		}
		if (mode == Mode.PRESS_ITEM && moved) {
			mode = Mode.DRAG_ITEM;
		}
		if (mode == Mode.MOVE || mode == Mode.RESIZE) {
			dragTo(mouseX, mouseY);
		}
		if (mode == Mode.DRAG_ITEM) {
			int[] target = box(ItemPanelKind.PINNED);
			boolean overPinned = target != null && PanelKit.inside(target, mouseX, mouseY);
			if (!overPinned && items.pinnedTab()) {
				int[] itemsBox = box(ItemPanelKind.ITEMS);
				overPinned = itemsBox != null && PanelKit.inside(itemsBox, mouseX, mouseY);
			}
			pinned.dragOver(overPinned, mouseX, mouseY);
		}
	}

	/** Moves or pulls the held panel to the mouse, snapping to the grid and the screen's center unless Shift is held. */
	private void dragTo(int mouseX, int mouseY) {
		int dx = (int) Math.round(mouseX - pressX);
		int dy = (int) Math.round(mouseY - pressY);
		int[] box = startBox.clone();
		if (mode == Mode.MOVE) {
			box[0] += dx;
			box[1] += dy;
		} else {
			if ((edges & PanelFrame.LEFT) != 0) {
				box[0] = Math.min(startBox[0] + dx, startBox[0] + startBox[2] - held.leastWidth);
				box[2] = startBox[0] + startBox[2] - box[0];
			}
			if ((edges & PanelFrame.RIGHT) != 0) {
				box[2] = Math.max(held.leastWidth, startBox[2] + dx);
			}
			if ((edges & PanelFrame.TOP) != 0) {
				box[1] = Math.min(startBox[1] + dy, startBox[1] + startBox[3] - held.leastHeight);
				box[3] = startBox[1] + startBox[3] - box[1];
			}
			if ((edges & PanelFrame.BOTTOM) != 0) {
				box[3] = Math.max(held.leastHeight, startBox[3] + dy);
			}
		}
		boolean free = !snapping();
		boolean left = (edges & PanelFrame.LEFT) != 0;
		boolean top = (edges & PanelFrame.TOP) != 0;
		PanelSnap.Result snapped = mode == Mode.MOVE ? PanelSnap.move(grid, box, free)
			: PanelSnap.resize(grid, box, left, top, (edges & PanelFrame.RIGHT) != 0, (edges & PanelFrame.BOTTOM) != 0, free);
		box = snapped.box();
		if (mode == Mode.RESIZE) {
			// never smaller than its least size, also after snapping
			if (box[2] < held.leastWidth) {
				box[0] = left ? box[0] + box[2] - held.leastWidth : box[0];
				box[2] = held.leastWidth;
			}
			if (box[3] < held.leastHeight && !feature.collapsed(held)) {
				box[1] = top ? box[1] + box[3] - held.leastHeight : box[1];
				box[3] = held.leastHeight;
			}
		}
		guides = snapped.guides();
		badge = "";
		if (mode == Mode.RESIZE && held == ItemPanelKind.ITEMS) {
			// whole items only: the grid of items snaps to columns and rows, from the edge that stays
			int[] whole = PanelSnap.wholeItems(box, left, top, feature.itemSize());
			box = new int[]{whole[0], whole[1], whole[2], whole[3]};
			badge = ItemGrid.badge(whole[4], whole[5]);
		}
		int[] onScreen = ItemPanelLayout.onScreen(box, screenWidth, screenHeight);
		dragBox = onScreen == null ? box : onScreen;
	}

	/** @return whether a held panel snaps now: "Snap to the grid" is on and Shift is not held */
	private boolean snapping() {
		return feature.snapOn() && !Minecraft.getInstance().hasShiftDown();
	}

	/** The mouse button let go. @return whether the panels used it */
	boolean release(double mouseX, double mouseY) {
		Mode was = mode;
		mode = Mode.NONE;
		settings.release();
		switch (was) {
			case MOVE, RESIZE -> {
				// never left over the inventory: kept beside it, in the room nearest to where it was let go
				// a collapsed panel keeps the height it has when it is opened again
				int[] box = dragBox;
				box[3] = PanelFrame.keptHeight(feature.collapsed(held), box[3], heldFullHeight);
				List<int[]> blocked = new ArrayList<>(ScreenRoom.taken());
				blocked.add(gui);
				int[] free = ItemPanelLayout.placed(box, blocked, held.leastWidth, held.leastHeight, screenWidth, screenHeight);
				if (free != null) {
					feature.putPlace(windowKind, held, PanelPlace.of(free, screenWidth, screenHeight, gui));
				}
				UiSounds.click();
			}
			case PRESS_ITEM -> openDetail(dragId, dragFrom);
			case DRAG_ITEM -> dropItem(mouseX, mouseY);
			default -> {
			}
		}
		held = null;
		startBox = null;
		dragBox = null;
		guides = List.of();
		badge = "";
		relayout();
		return was != Mode.NONE;
	}

	private void dropItem(double mouseX, double mouseY) {
		int[] target = box(ItemPanelKind.PINNED);
		boolean onPinned = target != null && PanelKit.inside(target, mouseX, mouseY);
		if (!onPinned && items.pinnedTab()) {
			int[] itemsBox = box(ItemPanelKind.ITEMS);
			onPinned = itemsBox != null && PanelKit.inside(itemsBox, mouseX, mouseY);
		}
		if (onPinned) {
			pinned.drop(dragId);
			UiSounds.click();
		} else if (dragFrom == ItemPanelKind.PINNED && feature.pinned().remove(dragId)) {
			// dragged out of the panel: off the shelf
			feature.pinnedChanged();
			UiSounds.click();
		}
		pinned.dragOver(false, 0, 0);
		dragId = null;
	}

	/** Opens an item's details: in the docked window, or beside the tile that was clicked. */
	void openDetail(String id, ItemPanelKind from) {
		if (id == null || ItemRepo.get(id) == null) {
			return;
		}
		if (from != ItemPanelKind.DETAILS || detail.id() == null) {
			// opened from the panels: placed anew beside what was clicked; opened from inside the window, it keeps its place
			popup = null;
		}
		detail.open(id);
		feature.noteRecent(id);
		UiSounds.click();
	}

	/** Opens an item's details from a key, beside the mouse. */
	void openDetailAt(String id, int mouseX, int mouseY) {
		popupAnchor[0] = mouseX;
		popupAnchor[1] = mouseY;
		popupAnchor[2] = 1;
		popupAnchor[3] = 1;
		openDetail(id, detail.contains(mouseX, mouseY) ? ItemPanelKind.DETAILS : panelAt(mouseX, mouseY));
	}

	/** Opens an item's details beside a box (the first result for Enter). */
	void openDetailBeside(String id, int[] anchor, ItemPanelKind from) {
		System.arraycopy(anchor, 0, popupAnchor, 0, 4);
		openDetail(id, from);
	}

	/** The wheel. With Ctrl over a panel it changes that panel's item size. @return whether the panels used it */
	boolean scroll(double mouseX, double mouseY, double amount) {
		if (amount == 0) {
			return false;
		}
		ItemPanelKind kind = panelAt(mouseX, mouseY);
		if (kind == null) {
			return false;
		}
		if (Minecraft.getInstance().hasControlDown()) {
			feature.stepItemSize(kind == ItemPanelKind.ITEMS && items.pinnedTab() ? ItemPanelKind.PINNED : kind, amount > 0 ? ITEM_SIZE_STEP : -ITEM_SIZE_STEP);
			relayout();
			return true;
		}
		return switch (kind) {
			case ITEMS -> items.pinnedTab() ? pinned.scroll(mouseX, mouseY, amount) : items.scroll(mouseX, mouseY, amount);
			case PINNED -> pinned.scroll(mouseX, mouseY, amount);
			default -> true;
		};
	}

	/** Lets the frame's other overlays say their room again next frame; called after drawing. */
	void endFrame() {
		// the panels drawn this frame: the HUD behind the menu keeps clear of them, element by element
		for (ItemPanelKind kind : ItemPanelKind.values()) {
			int[] box = kind == ItemPanelKind.ITEMS && itemsShown.value() < SHOWN_ALPHA ? null : box(kind);
			if (box != null) {
				ScreenRoom.hud(box[0], box[1], box[2], box[3]);
			}
		}
		ScreenRoom.clear();
	}

	/** Nothing was drawn this frame (the list is hidden or off). */
	void notDrawn() {
		drawn = false;
		hovered = null;
		ScreenRoom.clear();
	}

	/** @return the box of the first result, for Enter */
	int[] firstResultBox() {
		int[] box = box(ItemPanelKind.ITEMS);
		if (box == null) {
			return box(ItemPanelKind.SEARCH);
		}
		int tile = ItemGrid.tile(feature.itemSize());
		return new int[]{box[0] + PanelFrame.PAD, box[1] + PanelFrame.HEADER, tile, tile};
	}
}
