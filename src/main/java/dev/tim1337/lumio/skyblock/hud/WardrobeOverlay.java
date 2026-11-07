package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.ui.SlotLayer;
import dev.tim1337.lumio.api.experimental.ui.UiSounds;
import dev.tim1337.lumio.api.experimental.util.Debug;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;
import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.data.PetTracker;
import dev.tim1337.lumio.skyblock.data.SkyBlockPrices;
import dev.tim1337.lumio.skyblock.data.StorageTracker;
import dev.tim1337.lumio.skyblock.mixin.accessor.ContainerScreenAccess;
import dev.tim1337.lumio.skyblock.mixin.accessor.GuiStateAccess;
import dev.tim1337.lumio.skyblock.mixin.accessor.SlotAccess;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import org.joml.Matrix3x2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The game's Wardrobe menu as one window per page, under a small bar: every slot as a card with the player wearing that set, the open page at full size and the others smaller and grey, as last seen. As with the storage overlay it is not a screen of its
 * own: nothing is sent to open it, and no slot of the game is left on the screen. A click on a bright card is one click on its button, a click on a greyed one one click on the game's page button. "Edit" shows the game's own menu, to move pieces, and goes
 * back. The models are drawn once per change into one image ({@link WardrobeModelRenderer}), with a player that exists only here: it never enters the world, and nothing about it is sent. Measures and tones are in {@link WardrobeWindows} and
 * {@link WardrobeCardLook}.
 */
public final class WardrobeOverlay {
	private static final int COLUMNS = WardrobeMenu.SETS;
	private static final int OFF = -10000;
	private static final int CHIP_GAP = 5;
	private static final int BAR_PAD = 4;
	private static final int MODEL_TOP = 5;
	/** The room between a model's feet and its name. */
	private static final int NAME_GAP = 2;
	/** The name under the model: two rows at most, and the number. */
	private static final float NAME_ROOM_SHARE = 0.24f;
	/** The number under a name. */
	private static final int NUMBER_ROW = 8;
	private static final float BLOCKS_TALL = 2.05f;
	/** Room under the feet in the image: the boots reach a little below them, and must stay out of the cell of the row below. */
	private static final int FEET_ROOM = 3;
	/** The image of the figures is a little sharper than the screen: the worn one is shown larger. */
	private static final float IMAGE_DENSITY = 1.25f;
	/** How far a model stands turned, and how it follows the mouse, as the game's inventory model does. */
	private static final float STANDING_TURN_DEGREES = 22f;
	private static final float FOLLOW_DEGREES = 20f;
	private static final float FOLLOW_DISTANCE = 40f;
	private static final int FULL_LIGHT = 15728880;
	/** The stand-in player never enters the world, but the game asks every entity it draws for an id. */
	private static final int STAND_ID = -1337;
	/** Which part of a model is which piece, from the top. */
	private static final float[] PIECE_ENDS = {0.27f, 0.54f, 0.8f, 1f};
	private static final float SCROLL_STEP = 40f;
	private static final float SCROLL_RATE = 18f;
	/** The windows of the other pages let more of what is behind them through. */
	private static final float OTHER_WINDOW_OPACITY = 0.86f;
	private static final float HOVER_RATE = 16f;
	private static final float HOVER_LIFT = 2f;
	private static final float CARD_RADIUS = Radius.LG;
	private static final float CARD_LINE = 0.07f;
	private static final float CARD_LINE_HOVER = 0.25f;
	/** The worn set: its tinted background, its frame in real pixels, its glow. */
	private static final float WORN_TINT = 0.22f;
	private static final float WORN_FRAME_PIXELS = 2.5f;
	private static final float WORN_GLOW = 0.55f;
	private static final float WORN_FIGURE_GLOW = 0.45f;
	private static final int DOT = 4;
	private static final float DOT_OFF = 0.3f;
	private static final int STAR_SIZE = 9;
	private static final int STAR_INSET = 4;
	private static final int STAR_COLOR = 0xFFFFD60A;
	private static final float SHADOW_OPACITY = 0.3f;
	private static final String WORN_TAG = "WORN";
	private static final float TAG_SCALE = TextScale.SMALL * 0.85f;
	private static final String GLASS_PANE = "glass_pane";
	private static final String SETTINGS_TIP = "Wardrobe settings";
	private static final String TITLE = "Wardrobe";
	private static final String FAVORITES = "Favorites only";
	private static final String EDIT = "Edit";
	private static final String EMPTY = "Empty";
	private static final String LOCKED = "Locked";
	private static final String NOT_SEEN = "Not seen yet";
	private static final String PAGE_OPEN = "open";
	private static final String PAGE_OTHER = "click a set to open this page";
	/** A row of a name, at size 1. */
	private static final float NAME_LINE = 10f;
	private static final int DECODED_MOST = 2 * WardrobeBook.SLOTS_MOST * WardrobeMenu.PIECE_ROWS;
	private static final long FRESH_OPEN_NANOS = 1_500_000_000L;
	/** How long the menu has to stay as it is before it is read, with all of a page's buttons there. */
	private static final long SETTLE_MILLIS = 80L;
	/** The same with some of the buttons missing: they may never come. */
	private static final long SETTLE_PARTLY_MILLIS = 500L;
	private static final float BACKDROP_FADE_SECONDS = 0.15f;
	/** The lock on the card of a set with a protected piece. */
	private static final int LOCK_SIZE = 6;
	private static final int LOCK_COLOR = 0xB3FFFFFF;
	private static final long NANOS_PER_SECOND = 1_000_000_000L;
	private static final float LONGEST_FRAME_SECONDS = 0.1f;
	/** A head's skin arrives a moment after it is asked for: the image is drawn again after these times since a change, in ms. */
	private static final long[] DRAW_AGAIN_MS = {1000L, 3000L, 8000L};

	private static final int[] favoritesBox = new int[4];
	private static final int[] editBox = new int[4];
	private static final int[] settingsBox = new int[4];
	private static final int[] backBox = new int[4];
	private static final float[] onScreen = new float[5];
	private static final Map<String, ItemStack> decoded = new HashMap<>();
	/** Each page's even way to being the open one, and each slot's way to being under the mouse. */
	private static final float[] pageStep = new float[WardrobeBook.PAGES_MOST + 1];
	private static final boolean[] pageOpening = new boolean[WardrobeBook.PAGES_MOST + 1];
	private static final float[] hoverAmount = new float[WardrobeBook.SLOTS_MOST + 1];
	/** The windows this frame: their pages, scales and tops. */
	private static final int[] windowPage = new int[WardrobeBook.PAGES_MOST];
	private static final float[] windowScale = new float[WardrobeBook.PAGES_MOST];
	private static final float[] windowTop = new float[WardrobeBook.PAGES_MOST];
	private static final float[] restScale = new float[WardrobeBook.PAGES_MOST];

	private static WardrobeBook book;
	private static Object screenFor;
	private static boolean wardrobeResult;
	private static WardrobeMenu.Page page;
	private static Object readFor;
	private static int stateFor = -1;
	private static long lastSeen;
	/** When a real opening began, for the fade of the backdrop, which a page switch leaves alone. */
	private static long openedAt;
	/** When the menu's contents last changed, and whether that is not read yet. */
	private static long menuChangedAt;
	private static boolean unread;
	private static long lastFrame;
	private static boolean editing;
	private static WardrobeWindows.Sizes sizes = new WardrobeWindows.Sizes(1, 1, 1, 1);
	private static int screenWidth;
	private static int screenHeight;
	private static int windowCount;
	private static int gridTop;
	private static float scroll;
	private static float scrollShown;
	private static float contentHeight;
	private static List<Integer> cards = List.of();
	private static int renaming;
	private static String nameDraft = "";
	/** The first opening after the menu was closed puts the open page in view and shows the windows at rest. */
	private static boolean freshOpen;
	private static int hoveredNumber;

	private static RemotePlayer stand;
	private static Object standFor;
	private static List<WardrobeModelRenderer.Model> models = List.of();
	private static int version;
	private static boolean modelsStale = true;
	private static long changedAt;
	private static int drawAgain;
	private static double followedX = Double.NaN;
	private static double followedY = Double.NaN;
	private static int cellWidth = 1;
	private static int cellHeight = 1;
	private static int imageRows = 1;

	/** Slots' places in the game's own menu, kept to give them back for "Edit". */
	private static Object placesFor;
	private static int[] placeX = new int[0];
	private static int[] placeY = new int[0];
	private static int imageWidth;
	private static int imageHeight;

	private WardrobeOverlay() {
	}

	/** Draws the way back over the game's menu while it is being edited. */
	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(WardrobeOverlay::release);
		SlotLayer.add((screen, graphics, mouseX, mouseY) -> {
			if (screen instanceof ContainerScreen chest && editing(chest)) {
				drawBack(chest, graphics, mouseX, mouseY);
			}
		});
	}

	/** Lets go of all that belongs to the menu once no screen has been open for a moment: a page switch may pass through none, and keeps all of it (its settings page opens over it and comes back to it): the stand-in player holds on to the world it was made in. */
	private static void release(Minecraft client) {
		if (stand != null && client.gui.screen() == null && System.nanoTime() - lastSeen > FRESH_OPEN_NANOS) {
			stand = null;
			standFor = null;
			models = List.of();
			modelsStale = true;
			decoded.clear();
			screenFor = null;
			readFor = null;
			placesFor = null;
		}
	}

	/** @return whether a screen is the Wardrobe menu, with the feature on. Asked for every slot: a screen's title does not change, so it is checked once per screen */
	private static boolean wardrobe(Object screen) {
		if (!(screen instanceof ContainerScreen chest) || !SkyBlockFeatures.WARDROBE.isEnabled() || !SkyBlockFeatures.SKYBLOCK_ON.get()) {
			return false;
		}
		if (screen != screenFor) {
			screenFor = screen;
			page = WardrobeMenu.page(chest.getTitle().getString());
			wardrobeResult = page != null && chest.getMenu().getRowCount() > WardrobeMenu.BUTTON_ROW;
			if (wardrobeResult && System.nanoTime() - lastSeen > FRESH_OPEN_NANOS) {
				// a page switch opens a new menu as well; only a real opening starts over
				editing = false;
				renaming = 0;
				freshOpen = true;
				openedAt = System.nanoTime();
			}
		}
		return wardrobeResult;
	}

	/** @return whether the overlay covers this screen */
	public static boolean active(Object screen) {
		return wardrobe(screen) && !editing;
	}

	/** @return whether the game's own menu is shown for editing */
	public static boolean editing(Object screen) {
		return editing && wardrobe(screen);
	}

	/** Moves the game's slots out of the way: nothing of the menu is on the screen, and nothing can be clicked by accident. */
	public static void layout(ContainerScreen screen) {
		var access = (ContainerScreenAccess) screen;
		var slots = screen.getMenu().slots;
		if (screen != placesFor) {
			placesFor = screen;
			imageWidth = access.evskyblock$width();
			imageHeight = access.evskyblock$height();
			placeX = new int[slots.size()];
			placeY = new int[slots.size()];
			for (int i = 0; i < slots.size(); i++) {
				placeX[i] = slots.get(i).x;
				placeY[i] = slots.get(i).y;
			}
		}
		access.evskyblock$setLeft(0);
		access.evskyblock$setTop(0);
		access.evskyblock$setWidth(screen.width);
		access.evskyblock$setHeight(screen.height);
		for (Slot slot : slots) {
			((SlotAccess) slot).evskyblock$setX(OFF);
			((SlotAccess) slot).evskyblock$setY(OFF);
		}
		read(screen);
		measure(screen);
	}

	/** Gives the game's menu its own places back. */
	private static void restore(ContainerScreen screen) {
		var access = (ContainerScreenAccess) screen;
		if (screen != placesFor) {
			return;
		}
		access.evskyblock$setWidth(imageWidth);
		access.evskyblock$setHeight(imageHeight);
		access.evskyblock$setLeft((screen.width - imageWidth) / 2);
		access.evskyblock$setTop((screen.height - imageHeight) / 2);
		var slots = screen.getMenu().slots;
		for (int i = 0; i < slots.size() && i < placeX.length; i++) {
			((SlotAccess) slots.get(i)).evskyblock$setX(placeX[i]);
			((SlotAccess) slots.get(i)).evskyblock$setY(placeY[i]);
		}
	}

	/** Works out the sizes, and which pages have a window. */
	private static void measure(ContainerScreen screen) {
		screenWidth = screen.width;
		screenHeight = screen.height;
		List<Integer> pages = WardrobeWindows.windowsOf(cards);
		windowCount = Math.min(pages.size(), windowPage.length);
		for (int i = 0; i < windowCount; i++) {
			windowPage[i] = pages.get(i);
		}
		WardrobeWindows.Sizes now = WardrobeWindows.sizes(screen.width, screen.height, book.pages());
		if (!now.equals(sizes)) {
			sizes = now;
			modelsStale = true;
		}
	}

	/** Moves the windows on, and places them: each below the one before as it is drawn now. */
	private static void placeWindows(float dt) {
		boolean animated = MainSettings.animations() && !freshOpen;
		for (int number = 1; number < pageStep.length; number++) {
			boolean open = number == page.page();
			if (open != pageOpening[number] || !animated) {
				pageOpening[number] = open;
			}
			pageStep[number] = WardrobeWindows.advance(pageStep[number], open, dt, animated);
		}
		for (int i = 0; i < windowCount; i++) {
			int number = windowPage[i];
			windowScale[i] = WardrobeWindows.scale(WardrobeWindows.eased(pageStep[number], pageOpening[number]));
			restScale[i] = number == page.page() ? 1f : WardrobeWindows.OTHER_SCALE;
		}
		float rest = WardrobeWindows.height(restScale, windowCount, sizes.windowHeight());
		float start = WardrobeWindows.start(screenHeight, rest);
		gridTop = Math.round(start) + WardrobeWindows.BAR + WardrobeWindows.BAR_GAP;
		int room = screenHeight - WardrobeWindows.EDGE - gridTop;
		contentHeight = WardrobeWindows.height(windowScale, windowCount, sizes.windowHeight());
		float most = Math.max(0f, rest - room);
		if (freshOpen) {
			freshOpen = false;
			float[] tops = new float[windowCount];
			WardrobeWindows.tops(restScale, windowCount, sizes.windowHeight(), 0f, tops);
			for (int i = 0; i < windowCount; i++) {
				if (windowPage[i] == page.page()) {
					scroll = tops[i];
				}
			}
			scrollShown = Math.clamp(scroll, 0f, most);
		}
		scroll = Math.clamp(scroll, 0f, most);
		scrollShown += (scroll - scrollShown) * (1f - (float) Math.exp(-dt * SCROLL_RATE));
		if (Math.abs(scroll - scrollShown) < 0.5f) {
			scrollShown = scroll;
		}
		WardrobeWindows.tops(windowScale, windowCount, sizes.windowHeight(), gridTop - scrollShown, windowTop);
	}

	private static int placeInWindow(int window, int number) {
		int place = 0;
		for (int card : cards) {
			if (card == number) {
				return place;
			}
			if (WardrobeBook.pageOf(card) == windowPage[window]) {
				place++;
			}
		}
		return -1;
	}

	/**
	 * Writes where a card is on the screen: x, y, width, height and the scale of its window.
	 *
	 * @return whether it has a place
	 */
	private static boolean cardOnScreen(int number) {
		int cardPage = WardrobeBook.pageOf(number);
		for (int i = 0; i < windowCount; i++) {
			if (windowPage[i] != cardPage) {
				continue;
			}
			int place = placeInWindow(i, number);
			if (place < 0) {
				return false;
			}
			float scale = windowScale[i];
			onScreen[0] = WardrobeWindows.left(screenWidth, sizes.windowWidth(), scale) + WardrobeWindows.cardLeft(place, sizes.cardWidth()) * scale;
			onScreen[1] = windowTop[i] + WardrobeWindows.cardsTop() * scale;
			onScreen[2] = sizes.cardWidth() * scale;
			onScreen[3] = sizes.cardHeight() * scale;
			onScreen[4] = scale;
			return true;
		}
		return false;
	}

	/** @return the slot of the card under the mouse, or 0 */
	private static int cardUnder(double mouseX, double mouseY) {
		if (mouseY < gridTop || mouseY >= screenHeight - WardrobeWindows.EDGE) {
			return 0;
		}
		for (int number : cards) {
			if (cardOnScreen(number) && mouseX >= onScreen[0] && mouseX < onScreen[0] + onScreen[2] && mouseY >= onScreen[1] && mouseY < onScreen[1] + onScreen[3]) {
				return number;
			}
		}
		return 0;
	}

	private static int nameRoom() {
		return Math.max(Math.round(sizes.cardHeight() * NAME_ROOM_SHARE), NAME_GAP + Math.round(NAME_LINE * TextScale.SMALL) + NUMBER_ROW + 1);
	}

	private static int figureHeight() {
		return sizes.cardHeight() - nameRoom();
	}

	/** Reads the open page again when the menu changed. */
	private static void read(ContainerScreen screen) {
		book = WardrobeShelf.book(StorageTracker.profile());
		boolean changed = WardrobeShelf.arrived();
		int state = screen.getMenu().getStateId();
		long nowMillis = System.currentTimeMillis();
		if (state != stateFor || screen != readFor) {
			readFor = screen;
			stateFor = state;
			menuChangedAt = nowMillis;
			unread = true;
		}
		if (unread && nowMillis - menuChangedAt >= SETTLE_MILLIS) {
			List<WardrobeBook.Slot> seen = new ArrayList<>(COLUMNS);
			int withButton = 0;
			for (int set = 0; set < COLUMNS; set++) {
				WardrobeBook.Slot slot = readSet(screen, set);
				if (slot.state() != WardrobeMenu.State.UNKNOWN) {
					withButton++;
				}
				seen.add(slot);
			}
			// the server fills the menu a moment after it opens, perhaps in steps. What is known stays on the screen until the whole page is here: a page
			// read half way would draw the players without their armor for a moment. An empty read says nothing about the page
			boolean whole = withButton == COLUMNS;
			if (whole || nowMillis - menuChangedAt >= SETTLE_PARTLY_MILLIS) {
				unread = false;
				if (withButton > 0 && book.see(page.page(), page.pages(), seen)) {
					changed = true;
					WardrobeShelf.save();
				}
			}
			if (Debug.on()) {
				logMenu(screen);
			}
		}
		if (changed) {
			markChanged();
		}
		List<Integer> now = book.shown();
		if (!now.equals(cards)) {
			cards = now;
		}
	}

	private static void markChanged() {
		modelsStale = true;
		changedAt = System.currentTimeMillis();
		drawAgain = 0;
	}

	private static WardrobeBook.Slot readSet(ContainerScreen screen, int set) {
		var slots = screen.getMenu().slots;
		ItemStack button = slots.get(WardrobeMenu.BUTTON_ROW * COLUMNS + set).getItem();
		String buttonName = plain(button.getHoverName());
		WardrobeMenu.State state = WardrobeMenu.state(buttonName, BuiltInRegistries.ITEM.getKey(button.getItem()).getPath());
		int number = WardrobeMenu.number(buttonName, (page.page() - 1) * COLUMNS + set + 1);
		List<String> pieces = new ArrayList<>(WardrobeMenu.PIECE_ROWS);
		List<String> clean = new ArrayList<>(WardrobeMenu.PIECE_ROWS);
		int color = 0xFFFFFF;
		boolean first = true;
		for (int row = 0; row < WardrobeMenu.PIECE_ROWS; row++) {
			ItemStack piece = slots.get(row * COLUMNS + set).getItem();
			if (emptyPlace(piece)) {
				pieces.add("");
				continue;
			}
			pieces.add(PetTracker.encodeIcon(piece));
			String id = ItemGains.idOf(piece);
			clean.add(id == null ? plain(piece.getHoverName()) : SkyBlockPrices.nameOf(id));
			if (first) {
				color = nameRgb(piece.getHoverName(), color);
				first = false;
			}
		}
		String rank = state == WardrobeMenu.State.LOCKED ? WardrobeMenu.neededRank(loreOf(button)) : null;
		return new WardrobeBook.Slot(number, state, rank, List.copyOf(pieces), clean.isEmpty() ? "" : WardrobeMenu.setName(clean), color);
	}

	/** @return whether a stack in a piece's place is no piece: the server's glass pane there, whatever it is named */
	private static boolean emptyPlace(ItemStack stack) {
		return stack.isEmpty() || ItemGains.idOf(stack) == null && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith(GLASS_PANE);
	}

	private static void logMenu(ContainerScreen screen) {
		var slots = screen.getMenu().slots;
		for (int set = 0; set < COLUMNS; set++) {
			ItemStack button = slots.get(WardrobeMenu.BUTTON_ROW * COLUMNS + set).getItem();
			String name = plain(button.getHoverName());
			WardrobeMenu.State state = WardrobeMenu.state(name, BuiltInRegistries.ITEM.getKey(button.getItem()).getPath());
			if (state == WardrobeMenu.State.UNKNOWN || state == WardrobeMenu.State.LOCKED && WardrobeMenu.neededRank(loreOf(button)) == null) {
				Debug.log("Wardrobe: a button that could not be read: {} / {}", name, loreOf(button));
			}
		}
		for (int i = (WardrobeMenu.BUTTON_ROW + 1) * COLUMNS; i < (WardrobeMenu.BUTTON_ROW + 2) * COLUMNS && i < slots.size(); i++) {
			ItemStack item = slots.get(i).getItem();
			if (!item.isEmpty() && !StorageOverlay.isFiller(item)) {
				Debug.log("Wardrobe: the last row has {} at {}", plain(item.getHoverName()), i);
			}
		}
	}

	private static String plain(Component text) {
		return TextColors.noColors(text.getString()).strip();
	}

	private static List<String> loreOf(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return List.of();
		}
		List<String> lines = new ArrayList<>(lore.lines().size());
		for (Component line : lore.lines()) {
			lines.add(plain(line));
		}
		return lines;
	}

	/** @return the color of the first words of a name, as 0xRRGGBB: from its style, or from a color code in its text, as the server may send it */
	private static int nameRgb(Component name, int otherwise) {
		Optional<Integer> found = name.visit((Style style, String text) -> {
			if (text.isBlank()) {
				return Optional.empty();
			}
			if (text.length() > 1 && text.charAt(0) == '§') {
				ChatFormatting code = ChatFormatting.getByCode(text.charAt(1));
				TextColor color = code == null ? null : TextColor.fromLegacyFormat(code);
				if (color != null) {
					return Optional.of(color.getValue());
				}
			}
			return style.getColor() != null ? Optional.of(style.getColor().getValue()) : Optional.empty();
		}, Style.EMPTY);
		return found.orElse(otherwise);
	}

	private static ItemStack stackOf(String encoded) {
		return encoded.isEmpty() ? ItemStack.EMPTY : decoded.computeIfAbsent(encoded, PetTracker::decodeIcon);
	}

	/** Builds the model of every slot again, with the stand-in player wearing that slot's set: each has its cell in one image, wherever its card stands. */
	private static void rebuildModels(Minecraft client, double mouseX, double mouseY) {
		if (client.level == null || client.player == null) {
			return;
		}
		if (stand == null || standFor != client.level) {
			stand = new RemotePlayer(client.level, client.player.getGameProfile());
			stand.setId(STAND_ID);
			standFor = client.level;
		}
		if (decoded.size() > DECODED_MOST) {
			decoded.clear();
		}
		boolean follow = SkyBlockFeatures.WARDROBE.followsMouse();
		cellWidth = Math.round(sizes.cardWidth() * IMAGE_DENSITY);
		cellHeight = Math.round(figureHeight() * IMAGE_DENSITY);
		float perBlock = (figureHeight() - MODEL_TOP - FEET_ROOM) * IMAGE_DENSITY / BLOCKS_TALL;
		int slots = book.pages() * COLUMNS;
		imageRows = book.pages();
		var renderer = client.getEntityRenderDispatcher();
		EquipmentSlot[] order = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
		List<WardrobeModelRenderer.Model> built = new ArrayList<>(slots);
		for (int number = 1; number <= slots; number++) {
			WardrobeBook.Slot slot = book.slot(number);
			for (int row = 0; row < order.length; row++) {
				stand.setItemSlot(order[row], slot == null ? ItemStack.EMPTY : stackOf(slot.pieces().get(row)));
			}
			var drawer = renderer.getRenderer(stand);
			if (!(drawer.createRenderState(stand, 1f) instanceof AvatarRenderState state)) {
				continue;
			}
			state.nameTag = null;
			state.shadowPieces.clear();
			state.outlineColor = 0;
			state.lightCoords = FULL_LIGHT;
			state.showHat = client.player.isModelPartShown(PlayerModelPart.HAT);
			state.showJacket = client.player.isModelPartShown(PlayerModelPart.JACKET);
			state.showLeftPants = client.player.isModelPartShown(PlayerModelPart.LEFT_PANTS_LEG);
			state.showRightPants = client.player.isModelPartShown(PlayerModelPart.RIGHT_PANTS_LEG);
			state.showLeftSleeve = client.player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
			state.showRightSleeve = client.player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
			state.showCape = false;
			float turn = STANDING_TURN_DEGREES;
			float look = 0f;
			if (follow && cardOnScreen(number)) {
				turn = (float) Math.atan((onScreen[0] + onScreen[2] / 2f - mouseX) / FOLLOW_DISTANCE) * FOLLOW_DEGREES;
				look = (float) Math.atan((onScreen[1] + onScreen[3] * 0.2f - mouseY) / FOLLOW_DISTANCE) * FOLLOW_DEGREES;
			}
			state.bodyRot = 180f + turn;
			state.yRot = follow ? turn : 0f;
			state.xRot = -look;
			int column = (number - 1) % COLUMNS;
			int row = (number - 1) / COLUMNS;
			built.add(new WardrobeModelRenderer.Model(state, (column + 0.5f) * cellWidth, (row + 1) * cellHeight - FEET_ROOM * IMAGE_DENSITY, perBlock));
		}
		models = built;
		version++;
		modelsStale = false;
	}

	/** Draws the windows in place of the chest. */
	public static void drawBackground(ContainerScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		long now = System.nanoTime();
		float dt = lastFrame == 0L ? 0f : Math.min(LONGEST_FRAME_SECONDS, (float) (now - lastFrame) / NANOS_PER_SECOND);
		lastFrame = now;
		lastSeen = now;
		layout(screen);
		placeWindows(dt);
		Draw.setZoom(1f);
		OverlayBackdrop.draw(Draw.hudCanvas(graphics, 0xFF000000 | MainSettings.accentRgb(), dt, true), graphics, screen.width, screen.height, SkyBlockFeatures.WARDROBE.background(),
			SkyBlockFeatures.WARDROBE.backgroundStrength(), Mth.clamp((float) (now - openedAt) / NANOS_PER_SECOND / BACKDROP_FADE_SECONDS, 0f, 1f));
		drawBar(graphics, dt, mouseX, mouseY);

		Minecraft client = Minecraft.getInstance();
		if (SkyBlockFeatures.WARDROBE.followsMouse() && (mouseX != followedX || mouseY != followedY)) {
			followedX = mouseX;
			followedY = mouseY;
			modelsStale = true;
		}
		if (drawAgain < DRAW_AGAIN_MS.length && System.currentTimeMillis() - changedAt >= DRAW_AGAIN_MS[drawAgain]) {
			drawAgain++;
			modelsStale = true;
		}
		if (modelsStale) {
			rebuildModels(client, mouseX, mouseY);
		}
		hoveredNumber = renaming == 0 ? cardUnder(mouseX, mouseY) : 0;
		float hoverStep = MainSettings.animations() ? 1f - (float) Math.exp(-dt * HOVER_RATE) : 1f;
		for (int number = 1; number < hoverAmount.length; number++) {
			hoverAmount[number] += ((number == hoveredNumber ? 1f : 0f) - hoverAmount[number]) * hoverStep;
		}

		int clipLeft = 0;
		int clipTop = gridTop;
		graphics.enableScissor(clipLeft, clipTop, screen.width, screen.height - WardrobeWindows.EDGE);
		for (int i = 0; i < windowCount; i++) {
			if (windowVisible(i)) {
				drawWindowBack(graphics, i, dt, mouseX, mouseY);
			}
		}
		graphics.nextStratum();
		addFigures(graphics);
		graphics.nextStratum();
		for (int i = 0; i < windowCount; i++) {
			if (windowVisible(i)) {
				drawWindowFront(graphics, i, dt, mouseX, mouseY);
			}
		}
		graphics.disableScissor();
		Draw.setZoom(1f);
		Canvas ui = canvas(graphics, dt);
		drawScrollBar(ui);
		if (cards.isEmpty()) {
			String none = "No favorites yet";
			ui.textAtScaled(none, (screen.width - ui.widthScaled(none, TextScale.SMALL)) / 2f, gridTop + 8f, TextScale.SMALL, HudTheme.secondary());
		}
		if (hoveredNumber != 0) {
			ItemStack piece = pieceUnder(hoveredNumber, mouseX, mouseY);
			if (!piece.isEmpty()) {
				graphics.setTooltipForNextFrame(client.font, piece, mouseX, mouseY);
			}
		}
	}

	private static Canvas canvas(GuiGraphicsExtractor graphics, float dt) {
		return Draw.hudCanvas(graphics, 0xFF000000 | MainSettings.accentRgb(), dt, true);
	}

	private static boolean windowVisible(int window) {
		float height = sizes.windowHeight() * windowScale[window];
		return windowTop[window] + height > gridTop && windowTop[window] < screenHeight;
	}

	/** The title and the buttons, each on a small panel of its own above the windows. */
	private static void drawBar(GuiGraphicsExtractor graphics, float dt, int mouseX, int mouseY) {
		Canvas ui = canvas(graphics, dt);
		int left = (screenWidth - sizes.windowWidth()) / 2;
		int right = left + sizes.windowWidth();
		int top = gridTop - WardrobeWindows.BAR - WardrobeWindows.BAR_GAP;
		int favoritesWidth = PanelKit.chipWidth(ui, FAVORITES);
		int editWidth = PanelKit.chipWidth(ui, EDIT);
		int buttonsWidth = 2 * BAR_PAD + favoritesWidth + editWidth + SettingsButton.SIZE + 2 * CHIP_GAP;
		int buttonsLeft = right - buttonsWidth;
		HudTheme.panel(ui, buttonsLeft, top, buttonsWidth, WardrobeWindows.BAR, Radius.LG, HudTheme.opacity());
		int chipTop = top + (WardrobeWindows.BAR - PanelKit.CHIP_HEIGHT) / 2;
		place(favoritesBox, buttonsLeft + BAR_PAD, chipTop, favoritesWidth, PanelKit.CHIP_HEIGHT);
		place(editBox, favoritesBox[0] + favoritesWidth + CHIP_GAP, chipTop, editWidth, PanelKit.CHIP_HEIGHT);
		place(settingsBox, editBox[0] + editWidth + CHIP_GAP, top + (WardrobeWindows.BAR - SettingsButton.SIZE) / 2, SettingsButton.SIZE, SettingsButton.SIZE);
		PanelKit.chip(ui, favoritesBox, FAVORITES, book.favoritesOnly(), mouseX, mouseY);
		PanelKit.chip(ui, editBox, EDIT, false, mouseX, mouseY);
		SettingsButton.draw(ui, graphics, settingsBox, SETTINGS_TIP, mouseX, mouseY);

		float titleWidth = ui.widthScaled(TITLE, 1f);
		String summary = book.summary();
		float room = buttonsLeft - left - 2 * BAR_PAD - 2 * CHIP_GAP - titleWidth - CHIP_GAP;
		String shown = ui.fitScaled(summary, Math.max(0f, room), TextScale.SMALL);
		int titleBar = Math.round(2 * BAR_PAD + 2f + titleWidth + CHIP_GAP + ui.widthScaled(shown, TextScale.SMALL));
		HudTheme.panel(ui, left, top, titleBar, WardrobeWindows.BAR, Radius.LG, HudTheme.opacity());
		ui.textAtScaledBold(TITLE, left + BAR_PAD + 1f, top + 7f, 1f, HudTheme.text());
		ui.textAtScaled(shown, left + BAR_PAD + 1f + titleWidth + CHIP_GAP, top + 8f, TextScale.SMALL, HudTheme.secondary());
	}

	private static void place(int[] box, int x, int y, int width, int height) {
		box[0] = x;
		box[1] = y;
		box[2] = width;
		box[3] = height;
	}

	/** Moves the drawing into a window: everything in it is measured at its full size. */
	private static void enterWindow(GuiGraphicsExtractor graphics, int window) {
		float scale = windowScale[window];
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(WardrobeWindows.left(screenWidth, sizes.windowWidth(), scale), windowTop[window]);
		pose.scale(scale, scale);
		Draw.setZoom(scale);
	}

	/** Moves the drawing onto a card as it stands: the worn one larger around its middle, the one under the mouse lifted. */
	private static void enterCard(GuiGraphicsExtractor graphics, int window, int number, int x, boolean worn) {
		var pose = graphics.pose();
		pose.pushMatrix();
		float scale = worn ? WardrobeWindows.WORN_SCALE : 1f;
		int y = WardrobeWindows.cardsTop();
		pose.translate(x + sizes.cardWidth() / 2f, y + sizes.cardHeight() / 2f - HOVER_LIFT * hoverAmount[number]);
		pose.scale(scale, scale);
		pose.translate(-(x + sizes.cardWidth() / 2f), -(y + sizes.cardHeight() / 2f));
		Draw.setZoom(windowScale[window] * scale);
	}

	private static void leaveCard(GuiGraphicsExtractor graphics, int window) {
		graphics.pose().popMatrix();
		Draw.setZoom(windowScale[window]);
	}

	private static void leaveWindow(GuiGraphicsExtractor graphics) {
		graphics.pose().popMatrix();
		Draw.setZoom(1f);
	}

	private static WardrobeCardLook.Kind kindOf(int number) {
		WardrobeBook.Slot slot = book.slot(number);
		return WardrobeCardLook.kind(slot == null ? null : slot.state(), slot != null && slot.hasPieces(), WardrobeBook.pageOf(number) == page.page());
	}

	/** The window's panel, its header and its cards' backs: the worn card last, since it stands over its neighbours. */
	private static void drawWindowBack(GuiGraphicsExtractor graphics, int window, float dt, int mouseX, int mouseY) {
		enterWindow(graphics, window);
		Canvas ui = canvas(graphics, dt);
		int pageNumber = windowPage[window];
		boolean open = pageNumber == page.page();
		float openness = (windowScale[window] - WardrobeWindows.OTHER_SCALE) / (1f - WardrobeWindows.OTHER_SCALE);
		HudTheme.panel(ui, 0, 0, sizes.windowWidth(), sizes.windowHeight(), Radius.XL, HudTheme.opacity() * (OTHER_WINDOW_OPACITY + (1f - OTHER_WINDOW_OPACITY) * openness));
		drawHeader(ui, pageNumber, open);
		int place = 0;
		int wornNumber = 0;
		int wornX = 0;
		for (int number : cards) {
			if (WardrobeBook.pageOf(number) != pageNumber) {
				continue;
			}
			int x = WardrobeWindows.cardLeft(place++, sizes.cardWidth());
			if (kindOf(number) == WardrobeCardLook.Kind.WORN) {
				wornNumber = number;
				wornX = x;
			} else {
				drawCardBack(graphics, ui, window, number, x, false, dt);
			}
		}
		if (wornNumber != 0) {
			drawCardBack(graphics, ui, window, wornNumber, wornX, true, dt);
		}
		leaveWindow(graphics);
	}

	private static void drawHeader(Canvas ui, int pageNumber, boolean open) {
		int middle = WardrobeWindows.TOP + WardrobeWindows.HEADER / 2;
		int x = WardrobeWindows.PAD + 1;
		ui.rounded(x, middle - DOT / 2, DOT, DOT, DOT / 2f, open ? 0xFF000000 | MainSettings.accentRgb() : Draw.withOpacity(HudTheme.text(), DOT_OFF));
		String title = "Page " + pageNumber;
		float textY = middle - 3.5f;
		float titleX = x + DOT + 4f;
		ui.textAtScaledBold(title, titleX, textY, TextScale.SMALL, HudTheme.text());
		float afterTitle = titleX + ui.widthScaled(title, TextScale.SMALL) * 1.05f + 5f;
		int sets = setsOn(pageNumber);
		String count = sets < 0 ? "" : sets == 1 ? "1 set" : sets + " sets";
		float countWidth = ui.widthScaled(count, TextScale.SMALL);
		float right = sizes.windowWidth() - WardrobeWindows.PAD - 1f;
		ui.textAtScaled(count, right - countWidth, textY, TextScale.SMALL, HudTheme.secondary());
		String status = ui.fitScaled(open ? PAGE_OPEN : PAGE_OTHER, Math.max(0f, right - countWidth - afterTitle - 5f), TextScale.SMALL);
		ui.textAtScaled(status, afterTitle, textY, TextScale.SMALL, HudTheme.secondary());
	}

	/** @return how many of a page's slots hold a set, or -1 for a page of which nothing was seen yet */
	private static int setsOn(int pageNumber) {
		int sets = -1;
		int first = (pageNumber - 1) * COLUMNS + 1;
		for (int number = first; number < first + COLUMNS; number++) {
			WardrobeBook.Slot slot = book.slot(number);
			if (slot != null) {
				sets = Math.max(sets, 0) + (slot.hasPieces() ? 1 : 0);
			}
		}
		return sets;
	}

	private static void drawCardBack(GuiGraphicsExtractor graphics, Canvas ui, int window, int number, int x, boolean worn, float dt) {
		enterCard(graphics, window, number, x, worn);
		Canvas card = canvas(graphics, dt);
		int y = WardrobeWindows.cardsTop();
		int width = sizes.cardWidth();
		int height = sizes.cardHeight();
		float centerX = x + width / 2f;
		float centerY = y + height / 2f;
		int accent = 0xFF000000 | MainSettings.accentRgb();
		if (worn) {
			card.softLight(centerX, centerY, width * 0.82f, height * 0.72f, Draw.withOpacity(accent, WORN_GLOW));
			card.rounded(x, y, width, height, CARD_RADIUS, Draw.withOpacity(0xFF101014, 0.85f));
			card.rounded(x, y, width, height, CARD_RADIUS, Draw.withOpacity(accent, WORN_TINT));
			card.softLight(centerX, y + figureHeight() * 0.5f, width * 0.5f, figureHeight() * 0.5f, Draw.withOpacity(accent, WORN_FIGURE_GLOW));
			int frame = Math.max(1, Math.round(WORN_FRAME_PIXELS / Minecraft.getInstance().getWindow().getGuiScale()));
			card.ring(x, y, width, height, CARD_RADIUS, frame, accent);
		} else {
			float hover = hoverAmount[number];
			WardrobeCardLook look = WardrobeCardLook.of(kindOf(number), hover);
			card.rounded(x, y, width, height, CARD_RADIUS, Draw.withOpacity(0xFFFFFFFF, look.background()));
			card.ring(x, y, width, height, CARD_RADIUS, 1, Draw.withOpacity(0xFFFFFFFF, CARD_LINE + (CARD_LINE_HOVER - CARD_LINE) * hover));
		}
		// a soft shadow under the feet
		int shadow = Math.round(width * 0.46f);
		card.rounded(x + (width - shadow) / 2, y + figureHeight() - FEET_ROOM - 1, shadow, 3, Radius.XS, Draw.withOpacity(0xFF000000, SHADOW_OPACITY));
		leaveCard(graphics, window);
	}

	/** Adds the image of the models, and the part of it each card shows. */
	private static void addFigures(GuiGraphicsExtractor graphics) {
		if (models.isEmpty()) {
			return;
		}
		int imageWide = COLUMNS * cellWidth;
		int imageHigh = imageRows * cellHeight;
		List<WardrobeModelRenderer.Part> parts = new ArrayList<>(cards.size());
		for (int i = 0; i < windowCount; i++) {
			if (!windowVisible(i)) {
				continue;
			}
			enterWindow(graphics, i);
			int place = 0;
			for (int number : cards) {
				if (WardrobeBook.pageOf(number) != windowPage[i]) {
					continue;
				}
				int x = WardrobeWindows.cardLeft(place++, sizes.cardWidth());
				WardrobeCardLook.Kind kind = kindOf(number);
				enterCard(graphics, i, number, x, kind == WardrobeCardLook.Kind.WORN);
				int column = (number - 1) % COLUMNS;
				int row = (number - 1) / COLUMNS;
				int y = WardrobeWindows.cardsTop();
				// the image lies upside down in its texture, as all of the game's pictures do
				parts.add(new WardrobeModelRenderer.Part(new Matrix3x2f(graphics.pose()), x, y, x + sizes.cardWidth(), y + figureHeight(), (float) column / COLUMNS, (float) (column + 1) / COLUMNS,
					1f - (float) row / imageRows, 1f - (float) (row + 1) / imageRows, WardrobeCardLook.of(kind, hoverAmount[number])));
				leaveCard(graphics, i);
			}
			leaveWindow(graphics);
		}
		ScreenRectangle scissor = new ScreenRectangle(0, gridTop, screenWidth, Math.max(1, screenHeight - WardrobeWindows.EDGE - gridTop));
		((GuiStateAccess) graphics).evskyblock$state().addPicturesInPictureState(new WardrobeModelRenderer.Picture(version, models, parts, 0, 0, imageWide, imageHigh, scissor,
			PictureInPictureRenderState.getBounds(0, gridTop, screenWidth, screenHeight, scissor)));
	}

	/** What lies over the figures: names, numbers, stars and the worn set's tag. */
	private static void drawWindowFront(GuiGraphicsExtractor graphics, int window, float dt, int mouseX, int mouseY) {
		enterWindow(graphics, window);
		int place = 0;
		for (int number : cards) {
			if (WardrobeBook.pageOf(number) != windowPage[window]) {
				continue;
			}
			int x = WardrobeWindows.cardLeft(place++, sizes.cardWidth());
			boolean worn = kindOf(number) == WardrobeCardLook.Kind.WORN;
			enterCard(graphics, window, number, x, worn);
			drawCardFront(canvas(graphics, dt), number, x, mouseX, mouseY);
			leaveCard(graphics, window);
		}
		leaveWindow(graphics);
	}

	private static void drawCardFront(Canvas ui, int number, int x, int mouseX, int mouseY) {
		WardrobeBook.Slot slot = book.slot(number);
		WardrobeMenu.State state = slot == null ? WardrobeMenu.State.UNKNOWN : slot.state();
		WardrobeCardLook.Kind kind = kindOf(number);
		boolean worn = kind == WardrobeCardLook.Kind.WORN;
		boolean locked = kind == WardrobeCardLook.Kind.LOCKED;
		boolean holds = slot != null && slot.hasPieces();
		boolean over = number == hoveredNumber;
		int y = WardrobeWindows.cardsTop();
		int width = sizes.cardWidth();
		int nameTop = y + figureHeight() + NAME_GAP;
		float rest = WardrobeCardLook.of(kind, hoverAmount[number]).opacity();
		Draw.setAlpha(Math.max(rest, 0.45f));
		drawLabel(ui, number, slot, x, nameTop, state, holds);
		String under = String.valueOf(number);
		float underY = y + sizes.cardHeight() - 8f;
		if (worn) {
			ui.textAtScaledBold(under, x + (width - ui.widthScaled(under, TextScale.SMALL)) / 2f, underY, TextScale.SMALL, 0xFFFFFFFF);
		} else {
			ui.textAtScaled(under, x + (width - ui.widthScaled(under, TextScale.SMALL)) / 2f, underY, TextScale.SMALL, HudTheme.secondary());
		}
		if (holds) {
			int starX = x + width - STAR_INSET - STAR_SIZE;
			int starY = y + STAR_INSET;
			boolean overStar = over && inStar(number, mouseX, mouseY);
			ui.icon("star", starX, starY, STAR_SIZE, book.favorite(number) ? STAR_COLOR : Draw.withOpacity(HudTheme.text(), overStar ? 0.9f : over ? 0.5f : 0.22f));
		}
		if (holds && !over && pieceProtected(slot)) {
			ui.icon("lock", x + STAR_INSET, y + STAR_INSET, LOCK_SIZE, LOCK_COLOR);
		}
		if (over && !locked && renaming != number) {
			drawPencil(ui, x + STAR_INSET, y + STAR_INSET, Draw.withOpacity(HudTheme.text(), inPencil(number, mouseX, mouseY) ? 0.9f : 0.45f));
		}
		Draw.setAlpha(1f);
		if (worn) {
			float tagWidth = ui.widthScaled(WORN_TAG, TAG_SCALE) + 8f;
			int tagX = Math.round(x + (width - tagWidth) / 2f);
			int tagY = y - WardrobeWindows.TAG_ABOVE;
			ui.rounded(tagX, tagY, Math.round(tagWidth), WardrobeWindows.TAG_HEIGHT, Radius.XS, 0xFF000000 | MainSettings.accentRgb());
			ui.textAtScaledBold(WORN_TAG, tagX + 4f, tagY + 2f, TAG_SCALE, 0xFFFFFFFF);
		}
	}

	/** @return whether the mouse is on a part of a card, in the card's own measures from its corner */
	private static boolean inCardPart(int number, double mouseX, double mouseY, float left, float top, float size) {
		if (!cardOnScreen(number)) {
			return false;
		}
		double x = (mouseX - onScreen[0]) / onScreen[4];
		double y = (mouseY - onScreen[1]) / onScreen[4];
		return x >= left && x < left + size && y >= top && y < top + size;
	}

	/** @return whether a set holds a protected piece; the models show no slots to put the lock on, so the set's card has it */
	private static boolean pieceProtected(WardrobeBook.Slot slot) {
		for (String piece : slot.pieces()) {
			if (SkyBlockFeatures.ITEM_PROTECTION.isProtected(stackOf(piece))) {
				return true;
			}
		}
		return false;
	}

	private static boolean inStar(int number, double mouseX, double mouseY) {
		return inCardPart(number, mouseX, mouseY, sizes.cardWidth() - STAR_INSET - STAR_SIZE, STAR_INSET, STAR_SIZE);
	}

	private static boolean inPencil(int number, double mouseX, double mouseY) {
		return inCardPart(number, mouseX, mouseY, STAR_INSET, STAR_INSET, STAR_SIZE);
	}

	/** A small pencil, of little squares along a diagonal: the icons have none. */
	private static void drawPencil(Canvas ui, int x, int y, int color) {
		for (int i = 1; i < STAR_SIZE - 1; i++) {
			ui.fill(x + STAR_SIZE - 1 - i, y + i, 2, 2, color);
		}
		ui.fill(x, y + STAR_SIZE - 1, 2, 1, color);
	}

	private static void drawLabel(Canvas ui, int number, WardrobeBook.Slot slot, int x, int top, WardrobeMenu.State state, boolean holds) {
		int width = sizes.cardWidth();
		float room = width - 4f;
		if (renaming == number) {
			ui.rounded(x + 2, top, width - 4, nameRoom() - NUMBER_ROW - NAME_GAP, Radius.XS, HudTheme.slot());
			String shown = nameDraft + (System.currentTimeMillis() / 500L % 2L == 0L ? "|" : " ");
			drawFit(ui, fit(ui, shown, room), x, top + 1, 0xFFFFFFFF);
			return;
		}
		String own = book.name(number);
		if (!own.isEmpty()) {
			drawFit(ui, fit(ui, own, room), x, top, 0xFFFFFFFF);
			return;
		}
		if (slot == null) {
			drawFit(ui, fit(ui, NOT_SEEN, room), x, top, HudTheme.secondary());
		} else if (state == WardrobeMenu.State.LOCKED) {
			String text = slot.rank() == null ? LOCKED : LOCKED + " · " + slot.rank();
			drawFit(ui, fit(ui, text, room), x, top, HudTheme.secondary());
		} else if (!holds) {
			drawFit(ui, fit(ui, EMPTY, room), x, top, HudTheme.secondary());
		} else if (SkyBlockFeatures.WARDROBE.showsSetNames() && !slot.setName().isEmpty()) {
			drawFit(ui, fit(ui, slot.setName(), room), x, top, PanelKit.readable(0xFF000000 | slot.color()));
		}
	}

	/** @return how a text stands on a card: two rows only where the card has room for them above its number */
	private static WardrobeMenu.NameFit fit(Canvas ui, String text, float room) {
		boolean twoRows = nameRoom() - NAME_GAP - NUMBER_ROW >= 2 * NAME_LINE * TextScale.SMALL;
		return WardrobeMenu.fitName(text, room, TextScale.SMALL, twoRows, line -> ui.widthScaled(line, 1f));
	}

	private static void drawFit(Canvas ui, WardrobeMenu.NameFit fit, int x, int top, int color) {
		float lineHeight = NAME_LINE * fit.scale();
		int width = sizes.cardWidth();
		ui.textAtScaled(fit.first(), x + (width - ui.widthScaled(fit.first(), fit.scale())) / 2f, top, fit.scale(), color);
		if (!fit.second().isEmpty()) {
			ui.textAtScaled(fit.second(), x + (width - ui.widthScaled(fit.second(), fit.scale())) / 2f, top + lineHeight, fit.scale(), color);
		}
	}

	private static void drawScrollBar(Canvas ui) {
		int room = screenHeight - WardrobeWindows.EDGE - gridTop;
		if (contentHeight <= room) {
			return;
		}
		int barHeight = Math.max(12, Math.round(room * room / contentHeight));
		int barY = gridTop + Math.round((room - barHeight) * scrollShown / Math.max(1f, contentHeight - room));
		int x = (screenWidth + sizes.windowWidth()) / 2 + 1;
		ui.rounded(Math.min(x, screenWidth - 3), barY, 2, barHeight, 1f, Draw.withOpacity(HudTheme.text(), 0.25f));
	}

	/** @return the piece of a set the mouse is over on its model: the game's whole item, with its tooltip */
	private static ItemStack pieceUnder(int number, double mouseX, double mouseY) {
		WardrobeBook.Slot slot = book.slot(number);
		if (slot == null || !cardOnScreen(number)) {
			return ItemStack.EMPTY;
		}
		double x = (mouseX - onScreen[0]) / onScreen[4];
		double y = (mouseY - onScreen[1]) / onScreen[4] - MODEL_TOP;
		int height = figureHeight() - MODEL_TOP;
		if (y < 0.0 || y >= height || Math.abs(x - sizes.cardWidth() / 2f) > sizes.cardWidth() * 0.32f) {
			return ItemStack.EMPTY;
		}
		float along = (float) (y / height);
		for (int row = 0; row < PIECE_ENDS.length; row++) {
			if (along < PIECE_ENDS[row]) {
				return stackOf(slot.pieces().get(row));
			}
		}
		return ItemStack.EMPTY;
	}

	/**
	 * Handles every click over the windows: on a card it is at most one click in the game's menu.
	 *
	 * @return whether the overlay used it
	 */
	public static boolean mouseClicked(ContainerScreen screen, double mouseX, double mouseY, int button) {
		if (button == ScreenInput.LEFT && SettingsButton.clicked(settingsBox, screen, SkyBlockFeatures.WARDROBE, mouseX, mouseY)) {
			return true;
		}
		if (renaming != 0) {
			finishRename(true);
		}
		if (button == ScreenInput.LEFT && PanelKit.inside(favoritesBox, mouseX, mouseY)) {
			UiSounds.click();
			book.favoritesOnly(!book.favoritesOnly());
			scroll = 0f;
			WardrobeShelf.save();
			return true;
		}
		if (button == ScreenInput.LEFT && PanelKit.inside(editBox, mouseX, mouseY)) {
			UiSounds.click();
			editing = true;
			restore(screen);
			return true;
		}
		int number = cardUnder(mouseX, mouseY);
		if (number == 0) {
			return true;
		}
		WardrobeBook.Slot slot = book.slot(number);
		boolean locked = slot != null && slot.state() == WardrobeMenu.State.LOCKED;
		if (button != ScreenInput.LEFT) {
			if (!locked) {
				startRename(number);
			}
			return true;
		}
		if (slot != null && slot.hasPieces() && inStar(number, mouseX, mouseY)) {
			UiSounds.click();
			book.toggleFavorite(number);
			WardrobeShelf.save();
			return true;
		}
		if (!locked && inPencil(number, mouseX, mouseY)) {
			startRename(number);
			return true;
		}
		// a piece held on the cursor is the game's own business: it is put down in "Edit"
		if (!screen.getMenu().getCarried().isEmpty()) {
			return true;
		}
		switch (WardrobeMenu.clickFor(number, page.page(), slot == null ? WardrobeMenu.State.UNKNOWN : slot.state())) {
			case WEAR -> clickOnce(screen, WardrobeMenu.BUTTON_ROW * COLUMNS + (number - 1) % COLUMNS);
			case NEXT_PAGE -> clickOnce(screen, WardrobeMenu.NEXT_PAGE_SLOT);
			case PREVIOUS_PAGE -> clickOnce(screen, WardrobeMenu.PREVIOUS_PAGE_SLOT);
			case NONE -> {
			}
		}
		return true;
	}

	private static void startRename(int number) {
		UiSounds.click();
		renaming = number;
		nameDraft = book.name(number);
	}

	private static void finishRename(boolean keep) {
		if (keep && book.rename(renaming, nameDraft)) {
			WardrobeShelf.save();
		}
		renaming = 0;
		nameDraft = "";
	}

	/** The one click the player would make in the game's menu, and then nothing further: the menu answers by itself. */
	private static void clickOnce(ContainerScreen screen, int slot) {
		UiSounds.click();
		Minecraft client = Minecraft.getInstance();
		client.gameMode.handleContainerInput(screen.getMenu().containerId, slot, 0, ContainerInput.PICKUP, client.player);
		screen.getMenu().setCarried(ItemStack.EMPTY);
	}

	/** @return whether the wheel scrolled the windows */
	public static boolean mouseScrolled(double amount) {
		scroll = Math.max(0f, scroll - (float) amount * SCROLL_STEP);
		return true;
	}

	/** @return whether a key was for the windows: a name being typed, or Esc leaving "Edit" */
	public static boolean keyPressed(int key, ContainerScreen screen) {
		if (editing) {
			if (key == InputConstants.KEY_ESCAPE) {
				leaveEdit(screen);
				return true;
			}
			return false;
		}
		if (renaming == 0) {
			return false;
		}
		if (key == InputConstants.KEY_BACKSPACE) {
			if (!nameDraft.isEmpty()) {
				nameDraft = nameDraft.substring(0, nameDraft.length() - 1);
			}
		} else if (key == InputConstants.KEY_ESCAPE) {
			finishRename(false);
		} else if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			finishRename(true);
		}
		return true;
	}

	/** @return whether a typed character went into a name */
	public static boolean charTyped(int codepoint) {
		if (renaming == 0) {
			return false;
		}
		if (codepoint >= ' ' && nameDraft.length() < WardrobeBook.NAME_MOST) {
			nameDraft += new String(Character.toChars(codepoint));
		}
		return true;
	}

	/** @return whether keys are being typed into a name */
	public static boolean typing() {
		return renaming != 0;
	}

	private static void leaveEdit(ContainerScreen screen) {
		editing = false;
		layout(screen);
	}

	/** Draws "Edit" over the game's menu, the way back. */
	private static void drawBack(ContainerScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		lastSeen = System.nanoTime();
		var access = (ContainerScreenAccess) screen;
		Canvas ui = Draw.hudCanvas(graphics, 0xFF000000 | MainSettings.accentRgb(), 0f, true);
		graphics.nextStratum();
		backBox[0] = access.evskyblock$left() + access.evskyblock$width() - PanelKit.chipWidth(ui, EDIT);
		backBox[1] = Math.max(WardrobeWindows.EDGE, access.evskyblock$top() - PanelKit.CHIP_HEIGHT - 3);
		backBox[2] = PanelKit.chipWidth(ui, EDIT);
		backBox[3] = PanelKit.CHIP_HEIGHT;
		PanelKit.chip(ui, backBox, EDIT, true, mouseX, mouseY);
	}

	/** @return whether a click over the game's menu was the way back */
	public static boolean editClicked(ContainerScreen screen, double mouseX, double mouseY, int button) {
		if (button != ScreenInput.LEFT || !PanelKit.inside(backBox, mouseX, mouseY)) {
			return false;
		}
		UiSounds.click();
		leaveEdit(screen);
		return true;
	}
}
