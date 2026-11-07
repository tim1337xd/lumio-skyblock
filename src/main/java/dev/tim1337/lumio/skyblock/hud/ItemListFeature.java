package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.experimental.ui.SlotLayer;
import dev.tim1337.lumio.api.experimental.hud.HudEditor;
import dev.tim1337.lumio.api.experimental.util.InventoryEffects;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.feature.KeyPlace;
import dev.tim1337.lumio.api.setting.Action;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.InfoLine;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;
import net.minecraft.resources.Identifier;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.motion.AnimClock;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.api.experimental.ui.UiSounds;
import dev.tim1337.lumio.api.experimental.util.CommandText;
import dev.tim1337.lumio.api.experimental.util.Debug;
import dev.tim1337.lumio.skyblock.SkyBlockAddon;
import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.data.ItemHoldings;
import dev.tim1337.lumio.skyblock.data.ItemRepo;
import dev.tim1337.lumio.skyblock.data.ItemTypes;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockPrices;
import dev.tim1337.lumio.skyblock.mixin.accessor.ContainerScreenAccess;
import dev.tim1337.lumio.skyblock.mixin.accessor.SlotAccess;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The item search beside the inventory and other container screens, like SkySoft and NotEnoughUpdates: a panel with the search, filter chips by
 * type and a grid of every Hypixel SkyBlock item, sorted by rarity, name or price. While searching, the matching slots of the open screen light up.
 * Clicking an item opens its detail in the same panel (recipe with quick craft, price, where the player has it, uses and lore).
 * <p>
 * Over an item in the panel or the inventory, a key opens its recipe (R), its uses (U) or its wiki page (W); F pins it as a favorite, which sorts it
 * first and shows it under an empty search. Tab focuses the search, right-clicking it clears it, and a key toggles the panel. All keys are game key
 * bindings and can be changed in the controls. Item tooltips are the game's own.
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

	/** With room for fewer columns than this beside the window, the item area goes below or above it. */
	private static final int LEAST_COLUMNS = 6;
	private static final String ONLY_WHILE_SEARCHING_KEY = "only_while_searching";
	/** The panel's widest, and how far it keeps from the screen's edges and the container. */
	private static final int OFF_SCREEN = -10000;
	private static final int MOST_SEARCH = 60;

	private final Toggle searchLore = add(make().toggle("search_lore", Component.literal("Search item lore"), true), "Search",
		"Finds \"mining speed\" in the lines under the name");
	private final Toggle tabSearch = add(make().toggle("tab_search", Component.literal("Tab focuses the search"), true), "Search");
	private final Toggle rightClear = add(make().toggle("right_clear", Component.literal("Right-click clears the search"), true), "Search");
	private final Toggle keepSearch = add(make().toggle("keep_search", Component.literal("Remember the search"), true), "Search");
	private final Toggle onlyWhileSearching = add(make().toggle(ONLY_WHILE_SEARCHING_KEY, Component.literal("Only show items while searching"),
		true), "Appearance", "An empty search shows just the search field and your favorites");
	private final Toggle pricesOnHover = add(make().toggle("prices_on_hover", Component.literal("Prices on hover"), true), "Appearance");
	private final Toggle favoritesFirst = add(make().toggle("favorites_first", Component.literal("Favorites first"), true), "Appearance");
	private final Slider itemSize = add(make().slider("item_size", Component.literal("Item size"), 1f, 0.75f, 1.5f, 2), "Appearance");
	private final Toggle searchGlow = add(make().toggle("search_glow", Component.literal("Glow"), false), "Appearance");
	private final Choice<Sort> sort = add(make().choice("sort", Component.literal("Sort"), Sort.RARITY, Sort::displayName,
		Choice.Style.SEGMENTED), "Appearance");
	private final InfoLine lightNote = add(make().info("info_highlight", Component.literal("Highlight"), () -> "Double-click the search bar"), "Search");
	private final InfoLine status = add(make().info("info_items", Component.literal("Items"), ItemRepo::state), "Data");
	private final Action refresh = add(make().action("refresh_items", Component.literal("Item data"), Component.literal("Refresh"), false, ItemRepo::forceRefresh), "Data");

	static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SkyBlockAddon.ID, "item_list"));
	private KeyMapping toggleKey;
	private KeyMapping recipeKey;
	private KeyMapping usageKey;
	private KeyMapping favoriteKey;
	private KeyMapping wikiKey;
	private KeyMapping craftKey;

	private ItemListLayout.Places places = new ItemListLayout.Places(new int[4], new int[4]);
	private final int[] window = new int[4];
	/** Where the slots were while an item's window is in their place, and of which screen. */
	private int[][] hiddenSlots;
	private AbstractContainerScreen<?> hiddenFor;
	/** Whether the panel is shown: the panel key toggles it, and the state persists. */
	private boolean visible = true;
	/** Whether the results had room and were drawn, in the last frame. */
	private boolean listDrawn;
	/** The screen the item area was shown over in the last frame, which the HUD is not drawn under; null for none. */
	private static Screen coveredScreen;
	private final Set<String> favorites = new LinkedHashSet<>();
	private String search = "";
	private boolean searchFocused;
	private ItemTypes.Category category = ItemTypes.Category.ALL;
	private List<ItemRepo.Entry> filtered = List.of();
	private String filteredFor;
	private int filteredSize = -1;
	private List<ItemRepo.Entry> favoriteEntries = List.of();
	private int favoritesFor = -1;

	private final ItemSearchPanel list = new ItemSearchPanel(this);
	private final ItemDetailView detail = new ItemDetailView(this);
	/** How strongly the slots found by the search light up this frame. */
	private float litNow;
	private final SlotHighlights highlights = new SlotHighlights();
	private final SearchLight light = new SearchLight();
	private final AnimClock clock = new AnimClock();
	/** How visible the slot highlights are. */
	private final Spring lit = Spring.smooth(Spring.SNAPPY);
	private int[] panel = new int[4];
	/** The search field's place at the bottom of the screen, or null when it has no room there and sits at the top of the results. */
	private int[] searchBar;
	private ItemRepo.Entry hoveredEntry;
	private String hoveredId;

	public ItemListFeature() {
		super("item_list", "Item list", "Every item of SkyBlock beside the inventory, with a search, recipes (R), uses (U), the wiki (W) and favorites (F).", true);
		iconRgb(SystemColors.ORANGE_RGB);
		iconName("search");
		summary("Every SkyBlock item, with recipes, uses and prices");
		sort.visibleWhen(() -> false);
		addResetButton();
	}

	/** Registers the key bindings and attaches the panel to inventories as they open. Called during mod initialization. */
	public void register() {
		// the results stand where the game's list of effects would
		InventoryEffects.hideWhile(() -> listDrawn && active(Minecraft.getInstance().gui.screen()));
		toggleKey = key("toggle", InputConstants.KEY_O);
		recipeKey = key("recipe", InputConstants.KEY_R);
		usageKey = key("usage", InputConstants.KEY_U);
		favoriteKey = key("favorite", InputConstants.KEY_F);
		wikiKey = key("wiki", InputConstants.KEY_W);
		craftKey = key("craft", InputConstants.KEY_C);
		// the keys act in inventories, where the panel is shown
		for (KeyMapping mapping : new KeyMapping[]{toggleKey, recipeKey, usageKey, favoriteKey, wikiKey, craftKey}) {
			addKey(mapping, KeyPlace.SCREENS);
		}
		ClientTickEvents.END_CLIENT_TICK.register(client -> light.tick(client.gui.screen() != null));
		// the lights on the slots lie under the tooltip and the cursor's item, the panel itself over the menu
		SlotLayer.add((screen, graphics, mouseX, mouseY) -> {
			if (litNow > 0f && active(screen)) {
				Draw.setZoom(1f);
				Canvas ui = Draw.hudCanvas(graphics, 0xFF000000 | MainSettings.accentRgb(), 0f, true);
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
				search = "";
			}
			searchFocused = false;
			detail.close();
			ScreenEvents.remove(screen).register(shown -> detail.close());
			ScreenEvents.afterExtract(screen).register((shown, graphics, mouseX, mouseY, delta) -> render(container, graphics, mouseX, mouseY));
			ScreenMouseEvents.allowMouseClick(screen).register((shown, event) -> !mouseClicked(container, event.x(), event.y(), ScreenInput.button(event)));
			ScreenMouseEvents.allowMouseScroll(screen).register((shown, mouseX, mouseY, scrollX, scrollY) -> !mouseScrolled(container, mouseX, mouseY, scrollY));
		});
	}

	private static KeyMapping key(String name, int key) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + SkyBlockAddon.ID + ".item_list_" + name, InputConstants.Type.KEYSYM, key, CATEGORY));
	}

	private boolean active(Screen screen) {
		return whyHidden(screen) == null;
	}

	/** @return why the bar is not shown over a screen, or null when it is */
	private String whyHidden(Screen screen) {
		if (!isEnabled()) {
			return "the feature is off";
		}
		if (!SkyBlock.allowed()) {
			return "not on SkyBlock, as far as the mod can tell";
		}
		if (!(screen instanceof AbstractContainerScreen<?>)) {
			return "this is no inventory or chest";
		}
		if (StorageOverlay.active(screen) || WardrobeOverlay.active(screen)) {
			return "an overlay of its own covers this screen";
		}
		return null;
	}

	/** Under the debug switch, when it changes: why the bar is hidden or the list left out. */
	private void noteState(Screen screen) {
		String why = whyHidden(screen);
		if (why == null && !visible) {
			why = "the list's key has hidden it (it stays so until pressed again)";
		}
		String state = why != null ? "hidden: " + why : "shown" + (places.hasList() ? "" : ", without the list: " + panel[2] + " by " + panel[3] + " is too small");
		state += " on " + screen.getClass().getSimpleName() + " " + screen.width + "x" + screen.height;
		if (Debug.on() && !state.equals(notedState)) {
			notedState = state;
			Debug.log("Item search bar {}", state);
		}
	}

	private String notedState = "";

	// ------------------------------------------------------------------------------------------------ for the panel parts

	String search() {
		return search;
	}

	/** @return whether the slots' light is on: the bar then says so */
	boolean lightOn() {
		return light.on();
	}

	public boolean searchFocused() {
		return searchFocused;
	}

	boolean onlyWhileSearching() {
		return onlyWhileSearching.get();
	}

	boolean glow() {
		return searchGlow.get();
	}

	/** @return whether the HUD is hidden under a screen: the item area is shown over it, and the HUD panels would lie behind it (as under the storage overlay) */
	public static boolean coversHud(Screen screen) {
		return screen != null && screen == coveredScreen;
	}

	float itemSize() {
		return itemSize.get();
	}

	Sort sort() {
		return sort.get();
	}

	void nextSort() {
		Sort[] all = Sort.values();
		sort.set(all[(sort.get().ordinal() + 1) % all.length]);
		list.toTop();
		UiSounds.click();
	}

	ItemTypes.Category category() {
		return category;
	}

	void setCategory(ItemTypes.Category value) {
		category = value;
		UiSounds.click();
	}

	/** @return the key that focuses the search, as a hint in the empty field */
	String searchKeyHint() {
		return "Ctrl F";
	}

	boolean isFavorite(String id) {
		return favorites.contains(id);
	}

	void toggleFavorite(String id) {
		if (!favorites.remove(id)) {
			favorites.add(id);
		}
		UiSounds.click();
		LumioApi.saveSoon();
	}

	/** @return the favorites as items, for the row under an empty search */
	List<ItemRepo.Entry> favoriteEntries() {
		if (favoritesFor != favorites.hashCode() + ItemRepo.items().size()) {
			favoritesFor = favorites.hashCode() + ItemRepo.items().size();
			List<ItemRepo.Entry> entries = new ArrayList<>();
			for (String id : favorites) {
				ItemRepo.Entry entry = ItemRepo.get(id);
				if (entry != null) {
					entries.add(entry);
				}
			}
			favoriteEntries = entries;
		}
		return favoriteEntries;
	}

	/** Opens the storage overlay with the item's name searched: the /storage command, one per click, as the player would type it. */
	void findInStorage(String id) {
		Minecraft client = Minecraft.getInstance();
		if (!SkyBlock.inSkyBlock() || client.getConnection() == null) {
			return;
		}
		ItemRepo.Entry entry = ItemRepo.get(id);
		StorageOverlay.presetSearch(entry == null ? SkyBlockPrices.nameOf(id) : entry.plainName());
		UiSounds.click();
		client.getConnection().sendCommand("storage");
	}

	/**
	 * Quick craft: opens SkyBlock's crafting menu for the item, with its recipe and craft button, like /viewrecipe, for items that have such a menu.
	 * One command per click, started by the player; it never moves, crafts or clicks anything itself.
	 *
	 * @return whether it was opened
	 */
	boolean quickCraft(String id) {
		ItemRepo.Entry entry = ItemRepo.get(id);
		Minecraft client = Minecraft.getInstance();
		if (entry == null || !entry.craftable || !CommandText.safeArgument(entry.id) || !SkyBlock.inSkyBlock() || client.getConnection() == null) {
			return false;
		}
		UiSounds.click();
		client.getConnection().sendCommand("viewrecipe " + entry.id);
		return true;
	}

	// ------------------------------------------------------------------------------------------------ results

	/** @return the items to show: the matches of the search in the chosen group, favorites first, sorted as chosen; worked out once per change */
	List<ItemRepo.Entry> filtered() {
		String query = search.strip().toLowerCase(Locale.ROOT);
		// mobs and NPCs are found by a search, for their drops and shops, and are not listed otherwise
		List<ItemRepo.Entry> all = ItemRepo.items();
		if (!query.isEmpty() && category == ItemTypes.Category.ALL) {
			all = new ArrayList<>(all);
			all.addAll(ItemRepo.sources());
		}
		String key = query + "|" + searchLore.get() + "|" + favoritesFirst.get() + "|" + favorites.hashCode() + "|" + onlyWhileSearching.get() + "|" + category
			+ "|" + sort.get();
		if (key.equals(filteredFor) && all.size() == filteredSize) {
			return filtered;
		}
		filteredFor = key;
		filteredSize = all.size();
		String[] words = query.isEmpty() ? new String[0] : query.split("\\s+");
		if (words.length == 0 && onlyWhileSearching.get()) {
			filtered = List.of();
			return filtered;
		}
		List<ItemRepo.Entry> favorite = new ArrayList<>();
		List<ItemRepo.Entry> names = new ArrayList<>();
		List<ItemRepo.Entry> lore = new ArrayList<>();
		for (ItemRepo.Entry entry : all) {
			if (category != ItemTypes.Category.ALL && ItemTypes.of(entry).category() != category) {
				continue;
			}
			int match = words.length == 0 ? 2 : ItemRepo.matches(entry, words, searchLore.get());
			if (match == 0) {
				continue;
			}
			if (favoritesFirst.get() && favorites.contains(entry.id)) {
				favorite.add(entry);
			} else if (match == 2) {
				names.add(entry);
			} else {
				lore.add(entry);
			}
		}
		Comparator<ItemRepo.Entry> order = order();
		favorite.sort(order);
		names.sort(order);
		lore.sort(order);
		List<ItemRepo.Entry> result = new ArrayList<>(favorite.size() + names.size() + lore.size());
		result.addAll(favorite);
		result.addAll(names);
		// items that only mention the words in their lore ("aspect of the dragons" in a pet's drops) are only shown when no name matches: a search
		// for an item finds the item, not everything mentioning it
		if (names.isEmpty() && favorite.isEmpty()) {
			result.addAll(lore);
		}
		filtered = result;
		return filtered;
	}

	private Comparator<ItemRepo.Entry> order() {
		Comparator<ItemRepo.Entry> byName = Comparator.comparing(ItemRepo.Entry::plainName, String.CASE_INSENSITIVE_ORDER);
		return switch (sort.get()) {
			case RARITY -> Comparator.<ItemRepo.Entry>comparingInt(entry -> -ItemTypes.of(entry).rank()).thenComparing(byName);
			case NAME -> byName;
			case PRICE -> Comparator.<ItemRepo.Entry>comparingDouble(entry -> -SkyBlockPrices.price(entry.id)).thenComparing(byName);
		};
	}

	// ------------------------------------------------------------------------------------------------ drawing

	private void layout(AbstractContainerScreen<?> screen) {
		var access = (ContainerScreenAccess) screen;
		// the slots of an open item's window are moved away, and the window stays where it was
		window[0] = access.evskyblock$left();
		window[1] = access.evskyblock$top();
		window[2] = access.evskyblock$width();
		window[3] = access.evskyblock$height();
		places = ItemListLayout.places(screen.width, screen.height, window, ItemSearchPanel.MOST_FIELD_WIDTH, PanelKit.FIELD_HEIGHT,
			ItemSearchPanel.areaWidthFor(LEAST_COLUMNS, itemSize()));
		searchBar = places.bar();
		panel = places.list();
	}

	/** Takes the slots off the screen while an item's window is in their place, so nothing under it is drawn, hovered or clicked. Only where they are drawn: nothing is sent. */
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
		hoveredEntry = null;
		hoveredId = null;
		coveredScreen = null;
		if (!active(screen)) {
			if (Debug.on()) {
				noteState(screen);
			}
			return;
		}
		if (searchFocused) {
			ScreenInput.wantText();
		}
		ItemRepo.loadIfNeeded();
		SkyBlockPrices.refreshIfOld();
		float dt = clock.tick();
		layout(screen);
		if (Debug.on()) {
			noteState(screen);
		}
		Minecraft client = Minecraft.getInstance();
		Draw.setZoom(1f);
		Canvas ui = Draw.hudCanvas(graphics, 0xFF000000 | MainSettings.accentRgb(), dt, true);
		graphics.nextStratum();
		boolean lights = visible && light.lights(search);
		highlights.update(screen, lights ? search : "", searchLore.get());
		litNow = lit.step(lights ? 1f : 0f, dt);
		boolean fromResults = false;
		listDrawn = visible && places.hasList();
		if (visible) {
			hoveredEntry = list.draw(ui, graphics, panel, listDrawn, searchBar, dt, mouseX, mouseY);
			fromResults = hoveredEntry != null;
		}
		coveredScreen = listDrawn && list.open() ? screen : null;
		ItemRepo.Entry inWindow = detail.draw(ui, graphics, screen, window, listDrawn && list.open() ? panel[0] : screen.width, dt, mouseX, mouseY);
		if (detail.contains(mouseX, mouseY)) {
			// the window lies over the results: only what is under the mouse in it counts
			hoveredEntry = inWindow;
			fromResults = false;
		}
		if (hoveredEntry != null) {
			hoveredId = hoveredEntry.id;
			graphics.nextStratum();
			hoverCard(ui, hoveredEntry, fromResults, mouseX, mouseY, screen.width, screen.height);
		}
		// the hovered inventory item, for the keys
		if (hoveredId == null) {
			Slot slot = ((ContainerScreenAccess) screen).evskyblock$hoveredSlot();
			if (slot != null && slot.hasItem()) {
				hoveredId = ItemGains.idOf(slot.getItem());
			}
		}
	}

	/** The calm hover card: its width, padding and row height, and its gap to the mouse. */
	private static final int CARD_WIDTH = 132;
	private static final int CARD_PAD = 8;
	private static final int CARD_ROW = 10;
	private static final int CARD_OFFSET = 10;
	private static final int GOLD = 0xFFFFC94A;

	/**
	 * Draws the hover card of an item: its name in its rarity color, rarity and type, and for a result also its price (if wanted), how many the
	 * player has and "Click for details"; an item in the detail window only shows its name. No buttons: a click opens the details.
	 */
	private void hoverCard(Canvas ui, ItemRepo.Entry entry, boolean result, int mouseX, int mouseY, int screenWidth, int screenHeight) {
		int rarity = entry.rarityColor();
		String label = ItemTypes.of(entry).label().toUpperCase(Locale.ROOT);
		double buy = SkyBlockPrices.bazaarBuy(entry.id);
		double price = buy > 0 ? buy : SkyBlockPrices.lowestBin(entry.id);
		boolean withPrice = result && pricesOnHover.get() && price > 0;
		int have = result ? ItemHoldings.count(entry.id) : 0;
		int rows = 1 + (label.isEmpty() ? 0 : 1) + (result ? 1 : 0) + (withPrice ? 1 : 0) + (have > 0 ? 1 : 0);
		int height = 2 * CARD_PAD + rows * CARD_ROW + (result ? 3 : 0);
		int x = mouseX + CARD_OFFSET + CARD_WIDTH <= screenWidth - 4 ? mouseX + CARD_OFFSET : mouseX - CARD_OFFSET - CARD_WIDTH;
		int y = Math.max(4, Math.min(mouseY - 4, screenHeight - 4 - height));
		HudTheme.panel(ui, x, y, CARD_WIDTH, height, Radius.LG, HudTheme.opacity(), false);
		int left = x + CARD_PAD;
		int width = CARD_WIDTH - 2 * CARD_PAD;
		float rowY = y + CARD_PAD;
		ui.textAtScaledBold(ui.fitScaled(entry.plainName(), width, TextScale.SMALL), left, rowY, TextScale.SMALL, HudTheme.readable(rarity));
		rowY += CARD_ROW;
		if (!label.isEmpty()) {
			ui.textAtScaledBold(ui.fitScaled(label, width, TextScale.SMALL), left, rowY, TextScale.SMALL, Draw.withOpacity(HudTheme.readable(rarity), 0.85f));
			rowY += CARD_ROW;
		}
		if (!result) {
			return;
		}
		rowY += 3;
		if (withPrice) {
			cardRow(ui, buy > 0 ? "Bazaar buy" : "Lowest BIN", ItemDetailView.coins(price), GOLD, left, width, rowY);
			rowY += CARD_ROW;
		}
		if (have > 0) {
			cardRow(ui, "You have", PanelKit.count(have), HudTheme.text(), left, width, rowY);
			rowY += CARD_ROW;
		}
		ui.textAtScaled("Click for details", left, rowY, TextScale.SMALL, HudTheme.secondary());
	}

	private static void cardRow(Canvas ui, String label, String value, int color, int left, int width, float y) {
		ui.textAtScaled(label, left, y, TextScale.SMALL, HudTheme.secondary());
		float valueWidth = ui.boldWidthScaled(value, TextScale.SMALL);
		ui.textAtScaledBold(value, left + width - valueWidth, y, TextScale.SMALL, color);
	}

	// ------------------------------------------------------------------------------------------------ input

	private boolean inPanel(double mouseX, double mouseY) {
		return visible && PanelKit.inside(panel, mouseX, mouseY);
	}

	/** @return whether the click was consumed by the panel */
	private boolean mouseClicked(AbstractContainerScreen<?> screen, double mouseX, double mouseY, int button) {
		if (!active(screen) || !visible) {
			return false;
		}
		// the item's window first: a click on it acts there. It takes the inventory's place: nothing under it is clicked, and with the slots away a click beside the list would be one outside the window, which throws what is held
		boolean detailOpen = detail.id() != null;
		if (detailOpen && detail.click(mouseX, mouseY)) {
			UiSounds.click();
			return true;
		}
		{
			int[] field = list.field();
			if (PanelKit.inside(field, mouseX, mouseY)) {
				if (!search.isEmpty() && PanelKit.inside(PanelKit.clearBox(field), mouseX, mouseY)) {
					setSearch("");
				} else if (button == 1 && rightClear.get()) {
					setSearch("");
				} else if (button == 0 && light.clicked(System.currentTimeMillis())) {
					UiSounds.click();
				}
				searchFocused = true;
				return true;
			}
		}
		searchFocused = false;
		if (list.click(mouseX, mouseY)) {
			return true;
		}
		if (hoveredEntry != null && inPanel(mouseX, mouseY)) {
			openIn(screen, hoveredEntry.id);
			UiSounds.click();
			return true;
		}
		// beside it all, the window closes
		if (detailOpen) {
			if (!inPanel(mouseX, mouseY)) {
				detail.close();
			}
			return true;
		}
		// on the panel but on nothing: consumed only where the panel is actually drawn
		return listDrawn && list.open() && inPanel(mouseX, mouseY);
	}

	private boolean mouseScrolled(AbstractContainerScreen<?> screen, double mouseX, double mouseY, double scrollY) {
		if (!active(screen) || !inPanel(mouseX, mouseY) || scrollY == 0) {
			return false;
		}
		return list.scroll(mouseX, mouseY, scrollY);
	}

	/** Opens an item in the window that takes the inventory's place. */
	private void openIn(AbstractContainerScreen<?> screen, String id) {
		hideSlots(screen);
		detail.open(id);
	}

	private void setSearch(String value) {
		search = value;
		list.toTop();
	}

	/** @return whether an inventory key press was consumed by the panel: typing in the search, or the panel keys over an item */
	public boolean keyPressed(Screen screen, KeyEvent event) {
		if (!active(screen)) {
			return false;
		}
		int key = event.key();
		boolean control = (ScreenInput.modifiers(event.modifiers()) & ScreenInput.CONTROL) != 0;
		// Esc first lets the slots' light go, before anything else it does
		if (key == InputConstants.KEY_ESCAPE && light.escape()) {
			UiSounds.click();
			return true;
		}
		// Esc first closes the detail window; a second Esc clears the search
		if (detail.id() != null && key == InputConstants.KEY_ESCAPE) {
			detail.back();
			// back at the inventory with a search typed, the field takes the keyboard again, so the next Esc clears it
			if (detail.id() == null && !search.isEmpty()) {
				searchFocused = true;
			}
			UiSounds.click();
			return true;
		}
		if (searchFocused) {
			switch (key) {
				case InputConstants.KEY_ESCAPE -> {
					// Esc clears the search; once empty it lets go of the field
					if (search.isEmpty()) {
						searchFocused = false;
					} else {
						setSearch("");
					}
				}
				case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
					// Enter opens the first result's details
					List<ItemRepo.Entry> results = filtered();
					if (!results.isEmpty()) {
						if (screen instanceof AbstractContainerScreen<?> container) {
							openIn(container, results.get(0).id);
						}
						UiSounds.click();
					}
					searchFocused = false;
				}
				case InputConstants.KEY_TAB -> searchFocused = false;
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
			// while the search has focus every other key goes to it (characters arrive separately), so E does not close the inventory
			return true;
		}
		if (key == InputConstants.KEY_TAB && tabSearch.get()) {
			searchFocused = true;
			return true;
		}
		if (ScreenInput.letter(key, 'F') && control) {
			searchFocused = true;
			return true;
		}
		if (detail.id() != null && key == InputConstants.KEY_BACKSPACE) {
			// Backspace goes back to the item shown before in the window
			detail.back();
			UiSounds.click();
			return true;
		}
		if (toggleKey != null && toggleKey.matches(event)) {
			visible = !visible;
			UiSounds.click();
			return true;
		}
		if (hoveredId == null) {
			return false;
		}
		if (recipeKey != null && recipeKey.matches(event)) {
			return openDetail(screen, hoveredId);
		}
		if (usageKey != null && usageKey.matches(event)) {
			boolean opened = openDetail(screen, hoveredId);
			if (opened) {
				detail.showTab(ItemTabs.Kind.USED_IN);
			}
			return opened;
		}
		// F also swaps to the offhand in inventories: only intercept it over a panel item, never over a slot
		if (favoriteKey != null && favoriteKey.matches(event) && hoveredEntry != null) {
			toggleFavorite(hoveredEntry.id);
			return true;
		}
		if (craftKey != null && craftKey.matches(event) && quickCraft(hoveredId)) {
			return true;
		}
		if (wikiKey != null && wikiKey.matches(event)) {
			ItemRepo.Entry entry = ItemRepo.get(hoveredId);
			if (entry != null && !entry.wiki.isEmpty()) {
				// the link comes from downloaded item data: shown and confirmed first, like links in chat
				//#if 26.3
				//$ try {
				//$ 	ConfirmLinkScreen.confirmLinkNow(Minecraft.getInstance().gui.screen(), java.net.URI.create(entry.wiki));
				//$ } catch (IllegalArgumentException notALink) {
				//$ 	return true;
				//$ }
				//#else
				ConfirmLinkScreen.confirmLinkNow(Minecraft.getInstance().gui.screen(), entry.wiki);
				//#endif
				return true;
			}
		}
		return false;
	}

	private boolean openDetail(Screen screen, String id) {
		if (ItemRepo.get(id) == null || !(screen instanceof AbstractContainerScreen<?> container)) {
			return false;
		}
		visible = true;
		openIn(container, id);
		UiSounds.click();
		return true;
	}

	/** @return whether a typed character went into the search */
	public boolean charTyped(Screen screen, int codepoint) {
		if (!active(screen) || !searchFocused || !Character.isValidCodePoint(codepoint) || Character.isISOControl(codepoint)) {
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
		JsonArray list = new JsonArray();
		favorites.forEach(list::add);
		json.add("favorites", list);
		json.addProperty("shown", visible);
		return json;
	}

	@Override
	public void load(JsonObject json) {
		super.load(json);
		favorites.clear();
		if (json.has("favorites") && json.get("favorites").isJsonArray()) {
			for (JsonElement id : json.getAsJsonArray("favorites")) {
				favorites.add(id.getAsString());
			}
		}
		visible = !json.has("shown") || json.get("shown").getAsBoolean();
	}
}
