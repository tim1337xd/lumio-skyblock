package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.motion.AnimClock;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.api.experimental.ui.SlotLayer;
import dev.tim1337.lumio.api.experimental.ui.UiSounds;
import dev.tim1337.lumio.api.experimental.util.CommandText;
import dev.tim1337.lumio.api.experimental.util.InventoryEffects;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.feature.KeyPlace;
import dev.tim1337.lumio.api.setting.Action;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.InfoLine;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockAddon;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;
import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.data.CommandSender;
import dev.tim1337.lumio.skyblock.data.ItemRepo;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockPrices;
import dev.tim1337.lumio.skyblock.data.StorageTracker;
import dev.tim1337.lumio.skyblock.mixin.accessor.ContainerScreenAccess;
import dev.tim1337.lumio.skyblock.mixin.accessor.SlotAccess;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

/**
 * The item search around the inventory and other container screens: separate glass panels for the search bar, the items, Pinned (the player's own
 * shelf), Recent and an optional details window, each moved by its grip and pulled by an edge (see {@link ItemPanels}), Pinned and Recent folding up to their header, placed by a starting layout
 * and kept per kind of window. Typing filters the items; only in highlight mode (a double click on the search bar) the matches also light up in the
 * inventory, in Pinned and in the storage overlay, and the rest dims. Where it shows is the player's choice ("Show in"): by default only in the own
 * inventory; its key shows or hides it on any container screen. It has a look of its own (theme, accent, background strength), the menu's by default.
 * <p>
 * Over an item in a panel or the inventory, keys open its recipe or its uses, its wiki page, quick craft, or pin it; all are game key bindings,
 * unbound at first. Tab focuses the search, and a key shows or hides the panels. Item tooltips stay the game's own.
 */
public class ItemListFeature extends AddonFeature {
	/** The order of the results. */
	public enum Sort {
		RARITY("Rarity"),
		NAME("Name"),
		PRICE("Price");

		final String label;

		Sort(String label) {
			this.label = label;
		}

		public Component displayName() {
			return Component.literal(label);
		}
	}

	private static final String ONLY_WHILE_SEARCHING_KEY = "only_while_searching";
	private static final String SHOW_IN = "Show in";
	/** The saved layout's revision: 2 since the search bar starts at the bottom center of the screen. */
	static final int LAYOUT_REVISION = 2;
	private static final int OFF_SCREEN = -10000;
	private static final int MOST_SEARCH = 60;
	private static final int MOST_HISTORY = 20;
	private static final int MOST_RECENT = 32;
	private static final float LEAST_ITEM_SIZE = 0.75f;
	private static final float MOST_ITEM_SIZE = 1.5f;
	private static final Pattern SPACES = Pattern.compile("\\s+");

	private final Toggle searchLore = add(make().toggle("search_lore", Component.literal("Search item lore"), true), "Search",
		"Finds \"mining speed\" in the lore");
	private final Toggle tabSearch = add(make().toggle("tab_search", Component.literal("Tab focuses the search"), true), "Search");
	private final Toggle rightClear = add(make().toggle("right_clear", Component.literal("Right-click clears"), true), "Search");
	private final Toggle keepSearch = add(make().toggle("keep_search", Component.literal("Remember the search"), true), "Search");
	private final Toggle highlightMatches = add(make().toggle("highlight_matches", Component.literal("Highlight matches in the inventory"), true), "Search");
	private final InfoLine lightNote = add(make().info("info_highlight", Component.literal("Highlight mode"), () -> "Double-click the search bar"), "Search");

	private final Toggle showInventory = add(make().toggle("show_inventory", Component.literal("Inventory"), true), SHOW_IN);

	private final Slider itemSize = add(make().slider("item_size", Component.literal("Item size"), 1f, LEAST_ITEM_SIZE, MOST_ITEM_SIZE, 2), "Items");
	private final Choice<Sort> sort = add(make().choice("sort", Component.literal("Sort"), Sort.RARITY, Sort::displayName, Choice.Style.SEGMENTED), "Items");
	private final Toggle pricesOnHover = add(make().toggle("prices_on_hover", Component.literal("Prices on hover"), true), "Items");
	private final Choice<ItemSearchHover.OnHover> onHover = add(make().choice("on_hover", Component.literal("On hover"), ItemSearchHover.OnHover.TOOLTIP,
		ItemSearchHover.OnHover::displayName, Choice.Style.SEGMENTED), "Items", null);
	private final ItemSearchHover hover = new ItemSearchHover(this);
	private final Toggle onlyWhileSearching = add(make().toggle(ONLY_WHILE_SEARCHING_KEY, Component.literal("Only when searching"), true),
		"Items", "Otherwise only Pinned and Recent");
	private final Toggle searchGlow = add(make().toggle("search_glow", Component.literal("Glow"), false), "Items");

	private final Choice<PinnedPanel.View> pinnedView = add(make().choice("pinned_view", Component.literal("View"), PinnedPanel.View.LIST,
		PinnedPanel.View::displayName, Choice.Style.SEGMENTED), "Pinned");
	private final Toggle pinnedPrice = add(make().toggle("pinned_price", Component.literal("Price"), true), "Pinned");
	private final Toggle pinnedTrend = add(make().toggle("pinned_trend", Component.literal("Trend"), true), "Pinned",
		"The change over the last day");
	private final Toggle pinnedFirst = add(make().toggle("pinned_first", Component.literal("Pinned first in results"), true), "Pinned");
	private final Slider pinnedItemSize = add(make().slider("pinned_item_size", Component.literal("Pinned item size"), 1f, LEAST_ITEM_SIZE, MOST_ITEM_SIZE, 2),
		"Pinned");

	private final Slider recentCount = add(make().slider("recent_count", Component.literal("How many"), 8f, 4f, MOST_RECENT, 0), "Recent");
	private final Action clearRecent = add(make().action("recent_clear", Component.literal("Recent items"), Component.literal("Clear"), true,
		this::clearRecent), "Recent");
	private final Slider recentItemSize = add(make().slider("recent_item_size", Component.literal("Recent item size"), 1f, LEAST_ITEM_SIZE, MOST_ITEM_SIZE, 2),
		"Recent");

	private final Toggle lockLayout = add(make().toggle("lock_layout", Component.literal("Lock layout"), false), "Layout");
	private final Toggle snap = add(make().toggle("snap_grid", Component.literal("Snap to the grid"), true), "Layout",
		"Shift places freely");
	private final Action resetLayout = add(make().action("reset_layout", Component.literal("Layout"), Component.literal("Reset"), false, this::resetLayout),
		"Layout");
	private final Toggle itemsPanel = add(make().toggle("panel_items", Component.literal("Items panel"), true), "Panels");
	private final Toggle pinnedPanel = add(make().toggle("panel_pinned", Component.literal("Pinned panel"), true), "Panels");
	private final Toggle recentPanel = add(make().toggle("panel_recent", Component.literal("Recent panel"), true), "Panels");
	private final Toggle detailsPanel = add(make().toggle("panel_details", Component.literal("Details window"), false), "Panels",
		"The last clicked item, as its own panel");

	private final Choice<ItemSearchLook.Theme> lookTheme = add(make().choice("look_theme", Component.literal("Theme"), ItemSearchLook.Theme.MENU,
		ItemSearchLook.Theme::displayName, Choice.Style.SEGMENTED), "Look", "Menu follows the menu's theme");
	private final Choice<ItemSearchLook.Accent> lookAccent = add(make().choice("look_accent", Component.literal("Accent"), ItemSearchLook.Accent.MENU,
		ItemSearchLook.Accent::displayName, Choice.Style.SEGMENTED), "Look");
	private final Slider lookStrength = add(make().slider("look_background", Component.literal("Background strength"), ItemSearchLook.FULL_STRENGTH,
		ItemSearchLook.LEAST_STRENGTH, ItemSearchLook.FULL_STRENGTH, 0).unit("%"), "Look", "Of the theme's own");

	private final InfoLine status = add(make().info("info_items", Component.literal("Items"), ItemRepo::state), "Data");
	private final Action refresh = add(make().action("refresh_items", Component.literal("Item data"), Component.literal("Refresh"), false, ItemRepo::forceRefresh),
		"Data");

	static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SkyBlockAddon.ID, "item_list"));
	private KeyMapping toggleKey;
	private KeyMapping recipeKey;
	private KeyMapping usageKey;
	private KeyMapping favoriteKey;
	private KeyMapping pinKey;
	private KeyMapping wikiKey;
	private KeyMapping craftKey;
	private KeyMapping marketKey;
	/** The commands a click or key asks for (opening an item's market): one per request, through the send queue. */
	private final CommandSender commands = new CommandSender();

	/** Where the slots were while the details window is in their place, and of which screen. */
	private int[][] hiddenSlots;
	private AbstractContainerScreen<?> hiddenFor;
	/** Whether the panels are shown where "Show in" has them: the panel key toggles it, and the state persists. */
	private boolean visible = true;
	/** What the panel key last said for one screen where it goes against that, and for which screen; forgotten with the screen. */
	private Boolean shownByKey;
	private Screen shownByKeyOn;
	private String search = "";
	private String[] searchWords = new String[0];
	private boolean searchFocused;
	/** Whether the keyboard is in the item grid (Tab from the search), and which result is chosen there. */
	private boolean gridFocused;
	private int gridIndex;
	private final ItemSearchJob job = new ItemSearchJob();
	private String calculation = "";
	private final PinnedItems.Profiles pinned = new PinnedItems.Profiles();
	private final RecentList recent = new RecentList(MOST_RECENT);
	private final RecentList history = new RecentList(MOST_HISTORY);
	/** Where the arrow keys are in the last searches: -1 is what is typed; what was typed is kept meanwhile. */
	private int historyIndex = -1;
	private String typedBeforeHistory = "";
	private final PanelPlaces places = new PanelPlaces();
	/** Pinned and Recent while folded up to their header bars; saved. */
	private final EnumSet<ItemPanelKind> collapsed = EnumSet.noneOf(ItemPanelKind.class);
	private int layoutVersion;
	/** The item list's old favorites (kinds starred for the Favorite items module), read once from an older config. */
	private final Set<String> oldFavorites = new LinkedHashSet<>();
	private final Set<String> pinnedIds = new HashSet<>();
	private int pinnedIdsFor = -1;

	private final ItemDetailView detail = new ItemDetailView(this);
	private final ItemPanels panels = new ItemPanels(this, detail);
	/** How strongly the slots found by the search light up this frame. */
	private float litNow;
	private final SlotHighlights highlights = new SlotHighlights();
	private final SearchLight light = new SearchLight();
	private final AnimClock clock = new AnimClock();
	private final Spring lit = Spring.smooth(Spring.SNAPPY);
	private String hoveredId;
	private String notedState = "";

	public ItemListFeature() {
		super("item_list", "Item list", "Every SkyBlock item beside the inventory.", true);
		pageGroup("Inventory and menus");
		iconRgb(SystemColors.ORANGE_RGB);
		iconName("search");
		summary("Every SkyBlock item, recipes and prices");
		pinnedItemSize.visibleWhen(() -> false);
		recentItemSize.visibleWhen(() -> false);
		pricesOnHover.visibleWhen(() -> onHover.get() != ItemSearchHover.OnHover.NONE);
		pinnedView.visibleWhen(pinnedPanel::get);
		pinnedPrice.visibleWhen(pinnedPanel::get);
		pinnedTrend.visibleWhen(pinnedPanel::get);
		recentCount.visibleWhen(recentPanel::get);
		lightNote.visibleWhen(highlightMatches::get);
		// a locked layout cannot be dragged, and only a drag snaps
		snap.visibleWhen(() -> !lockLayout.get());
		addResetButton();
	}

	/** Registers the key bindings and attaches the panels to container screens as they open. Called during mod initialization. */
	public void register() {
		// the panels stand where the game's list of effects would
		InventoryEffects.hideWhile(() -> panels.drawn() && shown(Minecraft.getInstance().gui.screen()));
		toggleKey = key("toggle");
		recipeKey = key("recipe");
		usageKey = key("usage");
		favoriteKey = key("favorite");
		pinKey = key("pin");
		wikiKey = key("wiki");
		craftKey = key("craft");
		marketKey = key("market");
		// the keys act in inventories, where the panels are; the favorite key is listed with the Favorite items module, which owns it
		for (KeyMapping mapping : new KeyMapping[]{toggleKey, recipeKey, usageKey, wikiKey, pinKey, craftKey, marketKey}) {
			addKey(mapping, KeyPlace.SCREENS);
		}
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			light.tick(client.gui.screen() != null);
			if (client.getConnection() == null || !isEnabled()) {
				commands.clear();
			} else {
				commands.poll(command -> client.getConnection().sendCommand(command));
			}
		});
		// the lights on the slots lie under the tooltip and the cursor's item, the panels over the menu
		SlotLayer.add((screen, graphics, mouseX, mouseY) -> {
			if (litNow > 0f && usable(screen)) {
				Draw.setZoom(1f);
				Canvas ui = Draw.hudCanvas(graphics, 0xFF000000 | accentRgb(), 0f, true);
				graphics.nextStratum();
				highlights.draw(ui, screen, litNow);
			}
		});
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof AbstractContainerScreen<?> container)) {
				return;
			}
			// the slots' light goes on from one container to the next, with its search
			if (!keepSearch.get() && !light.on()) {
				setSearch("");
			}
			searchFocused = false;
			gridFocused = false;
			shownByKey = null;
			shownByKeyOn = null;
			panels.reset();
			if (!detailsPanel.get()) {
				detail.close();
			}
			ScreenEvents.remove(screen).register(shown -> {
				panels.reset();
				if (!detailsPanel.get()) {
					detail.close();
				} else {
					showSlots();
				}
			});
			ScreenEvents.afterExtract(screen).register((shown, graphics, mouseX, mouseY, delta) -> render(container, graphics, mouseX, mouseY));
			ScreenMouseEvents.allowMouseClick(screen).register((shown, event) -> !mouseClicked(container, event.x(), event.y(), ScreenInput.button(event)));
			ScreenMouseEvents.allowMouseRelease(screen).register((shown, event) -> !(shown(container) && panels.release(event.x(), event.y())));
			ScreenMouseEvents.allowMouseScroll(screen).register((shown, mouseX, mouseY, scrollX, scrollY) -> !(shown(container)
				&& panels.scroll(mouseX, mouseY, scrollY)));
		});
	}

	private static KeyMapping key(String name) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + SkyBlockAddon.ID + ".item_list_" + name, InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(), CATEGORY));
	}

	/** @return whether the item search can be on a screen at all: the feature is on, on SkyBlock, over an inventory or chest */
	private boolean usable(Screen screen) {
		return whyUnusable(screen) == null;
	}

	/** @return whether the panels show on a screen: where "Show in" has them and the key has not hidden them, or where the key shows them */
	private boolean shown(Screen screen) {
		return usable(screen) && ItemSearchPlaces.shown(screen == shownByKeyOn ? shownByKey : null, !visible, showIn(placeOf(screen)));
	}

	/** @return the kind of a screen for "Show in" */
	private static ItemSearchPlaces.Kind placeOf(Screen screen) {
		return ItemSearchPlaces.kind(screen instanceof InventoryScreen, screen instanceof ContainerScreen, screen.getTitle().getString());
	}

	private boolean showIn(ItemSearchPlaces.Kind kind) {
		return switch (kind) {
			case INVENTORY -> showInventory.get();
			// the panels show in the player's inventory only; their key still shows them in a chest or a menu for that screen
			case CHEST, MENU -> false;
		};
	}

	/** The panel key: shows or hides the panels on this screen. Where "Show in" has them, that is kept; elsewhere it is for this screen only. */
	private void toggleShown(Screen screen) {
		boolean now = !shown(screen);
		if (showIn(placeOf(screen))) {
			visible = now;
			shownByKey = null;
			shownByKeyOn = null;
			LumioApi.saveSoon();
		} else {
			shownByKey = now;
			shownByKeyOn = screen;
		}
		if (!now) {
			unfocusSearch();
		}
	}

	/** Shows the panels on a screen where they are hidden, for a key that opens an item's details there. */
	private void showOn(Screen screen) {
		if (!shown(screen)) {
			toggleShown(screen);
		}
	}

	/** @return why the item search can not be on a screen, or null when it can */
	private String whyUnusable(Screen screen) {
		if (!isEnabled()) {
			return "the feature is off";
		}
		if (!SkyBlock.allowed()) {
			return "not on SkyBlock, as far as the mod can tell";
		}
		if (!(screen instanceof AbstractContainerScreen<?>)) {
			return "this is no inventory or chest";
		}
		return null;
	}

	/** Under the debug switch, when it changes: why the panels are hidden. */
	private void noteState(Screen screen) {
		String why = whyUnusable(screen);
		if (why == null && !shown(screen)) {
			why = showIn(placeOf(screen)) ? "the list's key has hidden it (it stays so until pressed again)"
				: "\"Show in\" has it off for this kind of screen (" + placeOf(screen) + "); its key shows it here";
		}
		String state = (why != null ? "hidden: " + why : "shown") + " on " + screen.getClass().getSimpleName() + " " + screen.width + "x" + screen.height;
		if (DebugHooks.ACTIVE && !state.equals(notedState)) {
			notedState = state;
			DebugHooks.log(SkyBlockDebug.PANELS, "Item search {}", state);
		}
	}

	// ------------------------------------------------------------------------------------------------ for the panels

	String search() {
		return search;
	}

	public boolean searchFocused() {
		return searchFocused || gridFocused || panels.pinned.renaming();
	}

	void focusSearch() {
		searchFocused = true;
		gridFocused = false;
		if (StorageOverlay.searching()) {
			StorageOverlay.unfocusSearch();
		}
	}

	void unfocusSearch() {
		if (searchFocused) {
			history.note(search.strip());
		}
		searchFocused = false;
		gridFocused = false;
	}

	/** A left click on the field: a double click keeps the slots lit from one container to the next. */
	void clickSearch() {
		if (highlightMatches.get() && light.clicked(System.currentTimeMillis())) {
			UiSounds.click();
		}
	}

	/** A right click on the field: clears it, if wanted. */
	void rightClickSearch() {
		if (rightClear.get()) {
			setSearch("");
		}
	}

	boolean lightOn() {
		return highlightMatches.get() && light.on();
	}

	/** @return the search as the storage overlay lights it up: only in highlight mode, as the slots here; empty for none */
	public String lightText() {
		return isEnabled() && highlightMatches.get() && light.lights(search) ? search : "";
	}

	/** @return a calculation's result in the search ("= 192"), or empty */
	String calculation() {
		return calculation;
	}

	boolean onlyWhileSearching() {
		return onlyWhileSearching.get();
	}

	boolean glow() {
		return searchGlow.get();
	}

	ItemSearchHover.OnHover onHover() {
		return onHover.get();
	}

	/** @return the lines added under an item's tooltip in the panels; registered with the tooltips */
	public ItemSearchHover hover() {
		return hover;
	}

	boolean highlightMatches() {
		return highlightMatches.get();
	}

	void setHighlightMatches(boolean on) {
		highlightMatches.set(on);
		LumioApi.saveSoon();
	}

	/** @return whether Tab focuses the search (else only Ctrl F does) */
	boolean tabFocuses() {
		return tabSearch.get();
	}

	void setTabFocuses(boolean on) {
		tabSearch.set(on);
		LumioApi.saveSoon();
	}

	/** @return whether a panel is folded up to its header bar; only Pinned and Recent fold */
	boolean collapsed(ItemPanelKind kind) {
		return kind.collapses() && collapsed.contains(kind);
	}

	void setCollapsed(ItemPanelKind kind, boolean on) {
		if (!kind.collapses()) {
			return;
		}
		if (on) {
			collapsed.add(kind);
		} else {
			collapsed.remove(kind);
		}
		panels.relayout();
		LumioApi.saveSoon();
	}

	boolean pricesOnHover() {
		return pricesOnHover.get();
	}

	float itemSize() {
		return itemSize.get();
	}

	void setItemSize(float value) {
		itemSize.set(Math.clamp(value, LEAST_ITEM_SIZE, MOST_ITEM_SIZE));
		panels.relayout();
		LumioApi.saveSoon();
	}

	float pinnedItemSize() {
		return pinnedItemSize.get();
	}

	float recentItemSize() {
		return recentItemSize.get();
	}

	/** Ctrl and the wheel over a panel: a step of its own item size. */
	void stepItemSize(ItemPanelKind kind, float step) {
		Slider size = switch (kind) {
			case PINNED -> pinnedItemSize;
			case RECENT -> recentItemSize;
			default -> itemSize;
		};
		size.set(Math.clamp(Math.round((size.get() + step) * 100f) / 100f, LEAST_ITEM_SIZE, MOST_ITEM_SIZE));
		LumioApi.saveSoon();
	}

	Sort sort() {
		return sort.get();
	}

	void nextSort() {
		Sort[] all = Sort.values();
		sort.set(all[(sort.get().ordinal() + 1) % all.length]);
		panels.items.toTop();
	}

	/** @return the key that focuses the search, as a hint in the empty field */
	String searchKeyHint() {
		return tabSearch.get() ? "Tab" : "Ctrl F";
	}

	KeyMapping recipeKey() {
		return recipeKey;
	}

	KeyMapping usageKey() {
		return usageKey;
	}

	KeyMapping marketKey() {
		return marketKey;
	}

	/**
	 * Opens an item's market with its name searched, as the player asked with one click or key press: "/bz <name>" for a Bazaar item, "/ahs <name>"
	 * for an auctionable one; exactly one command, through the send queue. Hypixel answers itself where it does not allow it (no Booster Cookie).
	 *
	 * @return whether the item has a market, so the request was the market's
	 */
	boolean openMarket(String id) {
		ItemRepo.Entry entry = ItemRepo.get(id);
		MarketSearch.Market market = MarketSearch.of(id);
		if (entry == null || market == MarketSearch.Market.NONE) {
			return false;
		}
		String command = MarketSearch.command(market, MarketSearch.searchName(entry.name, entry.lore));
		if (!command.isEmpty()) {
			commands.send(command);
		}
		return true;
	}

	KeyMapping pinKey() {
		return pinKey;
	}

	/** @return the key that stars and protects items, owned by the Favorite items module (it shared the item list's keys); null before registering */
	public KeyMapping favoriteKey() {
		return favoriteKey;
	}

	/** @return the item list's old favorites from an older config, which the Favorite items module takes over as its starred kinds */
	public Set<String> oldFavorites() {
		return oldFavorites;
	}

	// ------------------------------------------------------------------------------------------------ Pinned, Recent and the layout

	/** @return the shelf of the profile played */
	PinnedItems pinned() {
		return pinned.of(StorageTracker.profile());
	}

	boolean isPinned(String id) {
		return pinned().has(id);
	}

	void togglePin(String id) {
		pinned().toggle(id);
		pinnedChanged();
		UiSounds.click();
	}

	void pinnedChanged() {
		LumioApi.saveSoon();
	}

	PinnedPanel.View pinnedView() {
		return pinnedView.get();
	}

	void setPinnedView(PinnedPanel.View view) {
		pinnedView.set(view);
	}

	boolean showPinnedPrice() {
		return pinnedPrice.get();
	}

	boolean showTrend() {
		return pinnedTrend.get();
	}

	RecentList recent() {
		return recent;
	}

	int recentShown() {
		return Math.round(recentCount.get());
	}

	/** Notes an item the player looked at: Recent. */
	void noteRecent(String id) {
		recent.note(id);
		LumioApi.saveSoon();
	}

	private void clearRecent() {
		recent.clear();
		LumioApi.saveSoon();
	}

	PanelPlaces places() {
		return places;
	}

	int layoutVersion() {
		return layoutVersion + Float.floatToIntBits(itemSize.get()) * 31 + (itemsPanel.get() ? 1 : 0) + (pinnedPanel.get() ? 2 : 0)
			+ (recentPanel.get() ? 4 : 0) + (detailsPanel.get() ? 8 : 0);
	}

	void putPlace(String window, ItemPanelKind kind, PanelPlace place) {
		places.put(window, kind, place);
		layoutVersion++;
		LumioApi.saveSoon();
	}

	void resetLayout() {
		places.clear();
		layoutVersion++;
		panels.relayout();
		LumioApi.saveSoon();
	}

	boolean layoutLocked() {
		return lockLayout.get();
	}

	void setLayoutLocked(boolean on) {
		lockLayout.set(on);
		LumioApi.saveSoon();
	}

	boolean snapOn() {
		return snap.get();
	}

	void setSnapOn(boolean on) {
		snap.set(on);
		LumioApi.saveSoon();
	}

	ItemSearchLook.Theme lookTheme() {
		return lookTheme.get();
	}

	void setLookTheme(ItemSearchLook.Theme theme) {
		lookTheme.set(theme);
		LumioApi.saveSoon();
	}

	ItemSearchLook.Accent lookAccent() {
		return lookAccent.get();
	}

	void setLookAccent(ItemSearchLook.Accent accent) {
		lookAccent.set(accent);
		LumioApi.saveSoon();
	}

	/** @return the background's strength in percent */
	float lookStrength() {
		return lookStrength.get();
	}

	void setLookStrength(float percent) {
		lookStrength.set(Math.clamp(percent, ItemSearchLook.LEAST_STRENGTH, ItemSearchLook.FULL_STRENGTH));
		LumioApi.saveSoon();
	}

	/** @return the accent the item search is drawn in: its own, or the menu's */
	private int accentRgb() {
		return lookAccent.get().rgb(MainSettings.accentRgb());
	}

	boolean panelOn(ItemPanelKind kind) {
		return switch (kind) {
			case SEARCH -> true;
			case ITEMS -> itemsPanel.get();
			case PINNED -> pinnedPanel.get();
			case RECENT -> recentPanel.get();
			case DETAILS -> detailsPanel.get();
		};
	}

	void setPanelOn(ItemPanelKind kind, boolean on) {
		switch (kind) {
			case ITEMS -> itemsPanel.set(on);
			case PINNED -> pinnedPanel.set(on);
			case RECENT -> recentPanel.set(on);
			case DETAILS -> {
				detailsPanel.set(on);
				showSlots();
			}
			default -> {
			}
		}
		panels.relayout();
	}

	// ------------------------------------------------------------------------------------------------ actions

	/**
	 * Quick craft: opens SkyBlock's crafting menu for the item, with its recipe and craft button, like /viewrecipe, for items that have such a menu.
	 * One command per press, started by the player; it never moves, crafts or clicks anything itself.
	 *
	 * @return whether it was opened
	 */
	boolean quickCraft(String id) {
		if (!canQuickCraft(id)) {
			return false;
		}
		UiSounds.click();
		Minecraft.getInstance().getConnection().sendCommand("viewrecipe " + id);
		return true;
	}

	/** @return whether Quick craft can run for an item now: it has SkyBlock's crafting menu, and the player is on SkyBlock */
	boolean canQuickCraft(String id) {
		ItemRepo.Entry entry = ItemRepo.get(id);
		return entry != null && entry.craftable && CommandText.safeArgument(entry.id) && SkyBlock.inSkyBlock() && Minecraft.getInstance().getConnection() != null;
	}

	// ------------------------------------------------------------------------------------------------ results

	/** @return the results as last worked out; new ones are worked out in small steps each frame */
	List<ItemRepo.Entry> results() {
		return job.results();
	}

	int resultsVersion() {
		return job.version();
	}

	boolean searchBusy() {
		return job.busy();
	}

	boolean resultsEverDone() {
		return job.everDone();
	}

	private void stepSearch() {
		PinnedItems shelf = pinned();
		if (pinnedIdsFor != shelf.version() + System.identityHashCode(shelf)) {
			pinnedIdsFor = shelf.version() + System.identityHashCode(shelf);
			pinnedIds.clear();
			pinnedIds.addAll(shelf.ids(PinnedItems.ALL));
		}
		ItemSearchJob.Query query = new ItemSearchJob.Query(search, searchLore.get(), sort.get(), pinnedIdsFor, pinnedFirst.get(), onlyWhileSearching.get(),
			ItemRepo.items().size(), ItemRepo.sources().size());
		job.step(query, pinnedIds);
	}

	/** @return whether an item matches the search in highlight mode: Pinned and Recent ring it in the accent */
	boolean lights(ItemRepo.Entry entry) {
		return lightOn() && searchWords.length > 0 && ItemRepo.matches(entry, searchWords, searchLore.get()) > 0;
	}

	// ------------------------------------------------------------------------------------------------ drawing

	/** Takes the slots off the screen while the details window is in their place, so nothing under it is drawn, hovered or clicked. Nothing is sent. */
	void hideSlots(AbstractContainerScreen<?> screen) {
		if (hiddenSlots != null) {
			return;
		}
		var slots = screen.getMenu().slots;
		hiddenSlots = new int[slots.size()][2];
		hiddenFor = screen;
		for (int i = 0; i < slots.size(); i++) {
			Slot slot = slots.get(i);
			hiddenSlots[i][0] = slot.x;
			hiddenSlots[i][1] = slot.y;
			((SlotAccess) slot).evskyblock$setX(OFF_SCREEN);
			((SlotAccess) slot).evskyblock$setY(OFF_SCREEN);
		}
	}

	/** Puts the slots back. */
	void showSlots() {
		if (hiddenSlots == null || hiddenFor == null) {
			return;
		}
		var slots = hiddenFor.getMenu().slots;
		for (int i = 0; i < slots.size() && i < hiddenSlots.length; i++) {
			((SlotAccess) slots.get(i)).evskyblock$setX(hiddenSlots[i][0]);
			((SlotAccess) slots.get(i)).evskyblock$setY(hiddenSlots[i][1]);
		}
		hiddenSlots = null;
		hiddenFor = null;
	}

	private void render(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		hoveredId = null;
		if (DebugHooks.ACTIVE) {
			noteState(screen);
		}
		if (!usable(screen)) {
			panels.notDrawn();
			litNow = 0f;
			return;
		}
		float dt = clock.tick();
		// highlight mode lights the matching slots on every container, also where the panels themselves do not show
		boolean lights = highlightMatches.get() && light.lights(search);
		highlights.update(screen, lights ? search : "", searchLore.get());
		litNow = lit.step(lights ? 1f : 0f, dt);
		if (!shown(screen)) {
			panels.notDrawn();
			showSlots();
			return;
		}
		if (searchFocused || gridFocused || panels.pinned.renaming()) {
			ScreenInput.wantText();
		}
		ItemRepo.loadIfNeeded();
		SkyBlockPrices.refreshIfOld();
		stepSearch();
		Draw.setZoom(1f);
		Canvas ui = Draw.hudCanvas(graphics, 0xFF000000 | accentRgb(), dt, true);
		graphics.nextStratum();
		if (detail.id() != null && !detailCoversGui()) {
			showSlots();
		}
		// its own look: the theme, and how strong the panels' background is
		ItemSearchLook.setStrength(lookStrength.get());
		ItemSearchLook.draw(lookTheme.get(), () -> panels.draw(ui, screen, graphics, dt, mouseX, mouseY));
		panels.endFrame();
		ItemRepo.Entry hovered = panels.hovered();
		if (hovered != null) {
			hoveredId = hovered.id;
		} else {
			// the hovered inventory item, for the keys
			Slot slot = ((ContainerScreenAccess) screen).evskyblock$hoveredSlot();
			if (slot != null && slot.hasItem()) {
				hoveredId = ItemGains.idOf(slot.getItem());
			}
		}
	}

	/** @return whether the details window is in the inventory's place this frame: its slots stay away then */
	private boolean detailCoversGui() {
		return hiddenSlots != null && detail.coversGui();
	}

	// ------------------------------------------------------------------------------------------------ input

	/** @return whether the click was consumed by the panels */
	private boolean mouseClicked(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button) {
		if (!shown(screen)) {
			return false;
		}
		return panels.press(screen, mouseX, mouseY, button);
	}

	void setSearch(String value) {
		search = value;
		historyIndex = -1;
		gridIndex = 0;
		String query = value.strip().toLowerCase(Locale.ROOT);
		searchWords = query.isEmpty() ? new String[0] : SPACES.split(query);
		double math = SearchMath.evaluate(value);
		calculation = Double.isNaN(math) ? "" : SearchMath.shown(math);
		panels.items.toTop();
	}

	/** @return whether an inventory key press was used: typing in the search, the grid, a group's name, or the keys over an item */
	public boolean keyPressed(Screen screen, KeyEvent event) {
		if (!usable(screen)) {
			return false;
		}
		if (!shown(screen)) {
			return hiddenKey(screen, event);
		}
		int key = event.key();
		boolean control = (ScreenInput.modifiers(event.modifiers()) & ScreenInput.CONTROL) != 0;
		boolean escape = key == InputConstants.KEY_ESCAPE;
		boolean enter = key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER;
		if (panels.pinned.renameKey(key, enter, escape, key == InputConstants.KEY_BACKSPACE)) {
			return true;
		}
		// Esc first closes the settings window, then switches highlight mode off
		if (escape && panels.settings.open()) {
			panels.settings.close();
			return true;
		}
		if (escape && light.escape()) {
			UiSounds.click();
			return true;
		}
		// Esc closes the details window step by step; then the search
		if (detail.id() != null && escape && !searchFocused) {
			detail.back();
			UiSounds.click();
			return true;
		}
		if (gridFocused) {
			return gridKey(screen, key, enter, escape);
		}
		if (searchFocused) {
			return searchKey(screen, key, enter, escape, control);
		}
		if (key == InputConstants.KEY_TAB && tabSearch.get()) {
			focusSearch();
			return true;
		}
		if (ScreenInput.letter(key, 'F') && control) {
			focusSearch();
			return true;
		}
		if (detail.id() != null && key == InputConstants.KEY_BACKSPACE) {
			detail.back();
			UiSounds.click();
			return true;
		}
		if (toggleKey != null && toggleKey.matches(event)) {
			toggleShown(screen);
			UiSounds.click();
			return true;
		}
		return itemKey(screen, event);
	}

	/**
	 * A key while the panels do not show on this screen: Esc switches highlight mode off (the slots may be lit here), the panel key shows the
	 * panels for this screen, and the keys over an inventory item (recipe, uses, ...) still work and bring the panels with them.
	 */
	private boolean hiddenKey(Screen screen, KeyEvent event) {
		if (event.key() == InputConstants.KEY_ESCAPE && light.escape()) {
			UiSounds.click();
			return true;
		}
		if (toggleKey != null && toggleKey.matches(event)) {
			toggleShown(screen);
			UiSounds.click();
			return true;
		}
		// the hovered inventory item, as nothing was drawn that would have noted it
		hoveredId = null;
		if (screen instanceof AbstractContainerScreen<?>) {
			Slot slot = ((ContainerScreenAccess) screen).evskyblock$hoveredSlot();
			if (slot != null && slot.hasItem()) {
				hoveredId = ItemGains.idOf(slot.getItem());
			}
		}
		return itemKey(screen, event);
	}

	/** A key while the search has focus: every key goes to it, so E does not close the inventory. */
	private boolean searchKey(Screen screen, int key, boolean enter, boolean escape, boolean control) {
		switch (key) {
			case InputConstants.KEY_ESCAPE -> {
				// Esc clears the search; once empty it lets go of the field
				if (search.isEmpty()) {
					unfocusSearch();
				} else {
					setSearch("");
				}
			}
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
				List<ItemRepo.Entry> results = results();
				if (!results.isEmpty()) {
					history.note(search.strip());
					panels.openDetailBeside(results.getFirst().id, panels.firstResultBox(), ItemPanelKind.ITEMS);
				}
				unfocusSearch();
			}
			case InputConstants.KEY_TAB -> {
				// into the grid of items, where the arrow keys move
				searchFocused = false;
				gridFocused = !results().isEmpty();
				gridIndex = 0;
			}
			case InputConstants.KEY_UP, InputConstants.KEY_DOWN -> stepHistory(key == InputConstants.KEY_UP);
			case InputConstants.KEY_BACKSPACE -> {
				if (control) {
					String trimmed = search.stripTrailing();
					int cut = trimmed.lastIndexOf(' ');
					setSearch(cut < 0 ? "" : trimmed.substring(0, cut + 1));
				} else if (!search.isEmpty()) {
					setSearch(search.substring(0, search.length() - 1));
				}
			}
			case InputConstants.KEY_A -> {
				if (control) {
					setSearch("");
				}
			}
			default -> {
			}
		}
		return true;
	}

	/** Arrow up goes to an older search, down to a newer one and back to what was typed. */
	private void stepHistory(boolean older) {
		if (historyIndex == -1) {
			typedBeforeHistory = search;
		}
		int next = history.step(historyIndex, older);
		String text = next < 0 ? typedBeforeHistory : history.items().get(next);
		setSearch(text);
		historyIndex = next;
	}

	/** A key while the keyboard is in the grid: the arrows choose a result, Enter opens it, Esc or Tab go back to the search. */
	private boolean gridKey(Screen screen, int key, boolean enter, boolean escape) {
		List<ItemRepo.Entry> results = results();
		int columns = Math.max(1, panels.items.columns());
		switch (key) {
			case InputConstants.KEY_LEFT -> gridIndex = Math.max(0, gridIndex - 1);
			case InputConstants.KEY_RIGHT -> gridIndex = Math.min(results.size() - 1, gridIndex + 1);
			case InputConstants.KEY_UP -> gridIndex = Math.max(0, gridIndex - columns);
			case InputConstants.KEY_DOWN -> gridIndex = Math.min(results.size() - 1, gridIndex + columns);
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
				if (gridIndex >= 0 && gridIndex < results.size()) {
					panels.openDetailBeside(results.get(gridIndex).id, panels.firstResultBox(), ItemPanelKind.ITEMS);
				}
			}
			case InputConstants.KEY_ESCAPE, InputConstants.KEY_TAB -> {
				gridFocused = false;
				searchFocused = key == InputConstants.KEY_TAB;
			}
			default -> {
			}
		}
		return true;
	}

	/** @return the result chosen with the arrow keys, or the first one while searching (Enter opens it); -1 for none */
	int markedResult() {
		if (gridFocused) {
			return gridIndex;
		}
		return searchFocused && !search.isBlank() ? 0 : -1;
	}

	/** The keys over a hovered item: recipe, uses, pin, favorite, quick craft, wiki. */
	private boolean itemKey(Screen screen, KeyEvent event) {
		if (hoveredId == null) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		int mouseX = (int) client.mouseHandler.getScaledXPos(client.getWindow());
		int mouseY = (int) client.mouseHandler.getScaledYPos(client.getWindow());
		if (recipeKey != null && recipeKey.matches(event)) {
			return openFromKey(screen, hoveredId, mouseX, mouseY, null);
		}
		if (usageKey != null && usageKey.matches(event)) {
			return openFromKey(screen, hoveredId, mouseX, mouseY, ItemTabs.Kind.USED_IN);
		}
		if (pinKey != null && pinKey.matches(event) && ItemRepo.get(hoveredId) != null) {
			togglePin(hoveredId);
			return true;
		}
		// F also swaps to the offhand in inventories: only taken over a panel's item, never over a slot (the Favorite items module has those)
		if (favoriteKey != null && favoriteKey.matches(event) && panels.hovered() != null) {
			SkyBlockFeatures.ITEM_PROTECTION.toggleKind(panels.hovered().id);
			return true;
		}
		if (craftKey != null && craftKey.matches(event) && quickCraft(hoveredId)) {
			return true;
		}
		if (marketKey != null && marketKey.matches(event) && openMarket(hoveredId)) {
			return true;
		}
		if (wikiKey != null && wikiKey.matches(event)) {
			ItemRepo.Entry entry = ItemRepo.get(hoveredId);
			String link = entry == null ? "" : WikiLink.of(entry.name, ItemRepo.isSource(entry.id));
			if (!link.isEmpty()) {
				// the community wiki's page, made from the name; opened only after the game's "Open link?" prompt
				ItemWindowActions.openLink(link);
				return true;
			}
		}
		return false;
	}

	private boolean openFromKey(Screen screen, String id, int mouseX, int mouseY, ItemTabs.Kind tab) {
		if (ItemRepo.get(id) == null) {
			return false;
		}
		showOn(screen);
		panels.openDetailAt(id, mouseX, mouseY);
		if (tab != null) {
			detail.showTab(tab);
		}
		return true;
	}

	/** @return whether a typed character went into the search or a group's name */
	public boolean charTyped(Screen screen, int codepoint) {
		if (!shown(screen) || !Character.isValidCodePoint(codepoint) || Character.isISOControl(codepoint)) {
			return false;
		}
		if (panels.pinned.renameChar(codepoint)) {
			return true;
		}
		if (!searchFocused) {
			return false;
		}
		if (search.length() < MOST_SEARCH) {
			setSearch(search + new String(Character.toChars(codepoint)));
		}
		return true;
	}

	// ------------------------------------------------------------------------------------------------ persistence

	@Override
	public JsonObject save() {
		JsonObject json = super.save();
		json.addProperty("shown", visible);
		json.addProperty("layout_revision", LAYOUT_REVISION);
		json.add("layout", places.toJson());
		json.add("pinned", pinned.toJson());
		json.add("recent", recent.toJson());
		json.add("history", history.toJson());
		json.add("collapsed", ItemPanelKind.collapsedToJson(collapsed));
		return json;
	}

	@Override
	public void load(JsonObject json) {
		super.load(json);
		visible = !json.has("shown") || json.get("shown").getAsBoolean();
		// older configs: the item list's favorites become Pinned (and stay starred kinds for the Favorite items module), "Favorites first" becomes
		// "Pinned first in results"
		oldFavorites.clear();
		if (json.has("favorites") && json.get("favorites").isJsonArray()) {
			for (JsonElement id : json.getAsJsonArray("favorites")) {
				if (id.isJsonPrimitive()) {
					oldFavorites.add(id.getAsString());
				}
			}
		}
		if (json.has("favorites_first") && !json.has("pinned_first")) {
			pinnedFirst.set(json.get("favorites_first").getAsBoolean());
		}
		pinned.load(json.has("pinned") && json.get("pinned").isJsonObject() ? json.getAsJsonObject("pinned") : null);
		if (!json.has("pinned") && !oldFavorites.isEmpty()) {
			pinned.migrate(oldFavorites);
		}
		places.load(json.has("layout") && json.get("layout").isJsonObject() ? json.getAsJsonObject("layout") : null);
		// a layout saved before the search bar moved to the bottom of the screen: the bar goes to its new place once, the other panels stay
		if (layoutRevision(json) < LAYOUT_REVISION) {
			places.forget(ItemPanelKind.SEARCH);
		}
		recent.load(json.has("recent") && json.get("recent").isJsonArray() ? json.getAsJsonArray("recent") : null);
		history.load(json.has("history") && json.get("history").isJsonArray() ? json.getAsJsonArray("history") : null);
		ItemPanelKind.collapsedFromJson(json.get("collapsed"), collapsed);
		layoutVersion++;
	}

	/** @return the revision of a saved layout; 1 for one saved before revisions were written */
	static int layoutRevision(JsonObject json) {
		try {
			return json.has("layout_revision") ? json.get("layout_revision").getAsInt() : 1;
		} catch (RuntimeException broken) {
			return 1;
		}
	}
}
