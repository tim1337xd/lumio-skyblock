package dev.tim1337.lumio.skyblock.experiments;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.TooltipLine;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.ui.SlotLayer;
import dev.tim1337.lumio.api.experimental.ui.Toasts;
import dev.tim1337.lumio.api.experimental.util.Debug;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.data.PetTracker;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;
import dev.tim1337.lumio.skyblock.data.SkyBlockIsland;
import dev.tim1337.lumio.skyblock.hud.SettingsButton;
import dev.tim1337.lumio.skyblock.mixin.accessor.ContainerScreenAccess;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A helper for the Experimentation Table. It only ever shows what the game has already shown in the current game: cards the player flipped in Superpairs, the order of this round in Chronomatron and Ultrasequencer; never anything not yet
 * revealed. It reads the menu and draws over it, and on the private island it can hold back a wrong click in Chronomatron and Ultrasequencer (see {@link WrongClickGuard}); no click is ever sent, delayed, added or changed. Inspired by SkyHanni's
 * helpers; the code is this mod's own.
 */
public class ExperimentHelperFeature extends AddonFeature {
	private static final int ITEM_SIZE = 16;
	private static final int SECOND_NEXT = 1;
	/** How many steps after the next Ultrasequencer marks light green. */
	private static final int ULTRA_AHEAD = 1;
	/** The button a menu click of the middle mouse button (pick block) carries to the server. */
	private static final int PICK_BLOCK_BUTTON = 2;
	private static final String SEEN_WORD = "Seen";
	private static final String ENCHANTED_BOOK_NAME = "Enchanted Book";
	/** The lore line of a book with its enchant. */
	private static final int BOOK_NAME_LINE = 2;
	private static final int DEBUG_LINE = 9;
	/** The info under the inventory: its lines, the room around them, and how far they stand off the menu. */
	private static final int INFO_LINES = 4;
	private static final int INFO_LINE = 9;
	private static final int INFO_GAP = 4;
	private static final int INFO_WIDTH = 150;
	private static final String INFO_HIDE = "Hide";
	private static final String SETTINGS_TIP = "Experimentation table settings";
	private static final float TINT_OPACITY = 0.5f;
	private static final float HIDDEN_CARD_OPACITY = 0.75f;
	private static final float COLLECTED_DIM = 0.6f;
	private static final int GREEN = 0xFF30D158;
	private static final int LIGHT_GREEN = 0xFFA6EFB8;
	private static final int LIGHT_BLUE = 0xFF64D2FF;
	private static final int WHITE = 0xFFFFFFFF;
	/** The short red flash on a slot whose click was held back, and its soft sound. */
	private static final int WRONG_RED = 0xFFFF453A;
	private static final long WRONG_FLASH_MILLIS = 300L;
	private static final float WRONG_SOUND_PITCH = 0.7f;
	private static final float WRONG_SOUND_VOLUME = 0.3f;
	private static final int SHADOW = 0xFF000000;
	private static final String TERRACOTTA = "terracotta";
	private static final String GUARDIAN = "Guardian";

	private final Toggle keepItems = add(make().toggle("keep_items", Component.literal("Keep flipped items visible"), true), "General");
	private final Toggle highlightPairs = add(make().toggle("highlight_pairs", Component.literal("Highlight pairs"), true), "General");
	private final Toggle nextClicks = add(make().toggle("next_clicks", Component.literal("Next-click colors"), false), "General");
	private final Toggle blockWrongClicks = add(make().toggle("block_wrong_clicks", Component.literal("Block wrong clicks"), false), "General", "Chronomatron and Ultrasequencer, on your island. Hold Shift to click anyway");
	private final Toggle info = add(make().toggle("info", Component.literal("Info under the inventory"), false), "General");
	private final Toggle alerts = add(make().toggle("alerts", Component.literal("Alerts"), true), "General", "An ULTRA-RARE book, and the most clicks reached");
	private final Toggle debugOverlay = add(make().toggle("debug_overlay", Component.literal("Debug overlay"), false), "Advanced",
		"The phase, round, known order and your progress of Chronomatron and Ultrasequencer");
	private final Toggle guardian = add(make().toggle("guardian", Component.literal("Guardian reminder"), false), "General", "When you open the table without a Guardian pet");

	private final SuperpairsBoard<ItemStack> board = new SuperpairsBoard<>();
	private final SequenceMemory sequence = new SequenceMemory();
	private Screen screenFor;
	private ExperimentFormats.Menu menu;
	private boolean tableSeen;
	private boolean guardianAsked;
	private boolean ultraRareSeen;
	private final MostClicks mostClicks = new MostClicks();
	/** The round last written to the debug log, so each is logged once. */
	private int roundLogged = -1;
	private int lastClicked = -1;
	private int clicksLeft = -1;
	private int stateFor = -1;
	private int cardCount;
	/** Whether this game of Superpairs is over: nothing of ours stays on the grid, only the info. */
	private boolean gameOver;
	private final String[] infoLines = new String[INFO_LINES];
	private final int[] hideBox = new int[4];
	private final int[] settingsBox = new int[4];
	private ExperimentProfit profit;
	/** How often each alert came, for what checks that it came once. */
	private int ultraAlerts;
	private int mostClicksAlerts;
	private int guardianAlerts;
	/** How many clicks were held back, for what checks it. */
	private int heldBack;
	private int flashSlot = -1;
	private float mouseX;
	private float mouseY;
	private long flashUntil;

	public ExperimentHelperFeature() {
		super("experiments", "Experimentation table", "Marks what the game already showed you at the Experimentation Table, and tells you of what matters.", true);
		iconRgb(SystemColors.PURPLE_RGB);
		iconName("gem");
		summary("Remembers what the table has shown you");
	}

	private static final int PREVIEW_COLUMNS = 7;
	private static final int PREVIEW_ROWS = 3;
	private static final int PREVIEW_CELL = 22;
	/** Which sample is on a card of the preview: a pair, one seen, a collected pair, a power-up; the rest is face down. */
	private static final int[] PREVIEW_PAIR = {1, 11};
	private static final int PREVIEW_SEEN = 5;
	private static final int[] PREVIEW_COLLECTED = {8, 9};
	private static final int PREVIEW_POWERUP = 16;
	private static final String[] PREVIEW_INFO = {"Pairs found: 1 / 10", "Clicks left: 12", "XP so far: 31.0k", "Best still open: Experience Bottle"};

	@Override
	public boolean hasPreview() {
		return true;
	}

	@Override
	public void drawPreview(Canvas ui, int x, int y, int width, int height) {
		GuiGraphicsExtractor graphics = ui.graphics();
		if (previewItems == null) {
			previewItems = new ItemStack[]{new ItemStack(net.minecraft.world.item.Items.ENCHANTED_BOOK), new ItemStack(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE), new ItemStack(net.minecraft.world.item.Items.PAPER),
				new ItemStack(net.minecraft.world.item.Items.GOLD_NUGGET)};
		}
		int gridWidth = PREVIEW_COLUMNS * PREVIEW_CELL;
		int infoHeight = info.get() ? INFO_LINES * INFO_LINE + INFO_GAP * 2 : 0;
		int left = x + (width - gridWidth) / 2;
		int top = y + (height - PREVIEW_ROWS * PREVIEW_CELL - infoHeight) / 2;
		for (int card = 0; card < PREVIEW_ROWS * PREVIEW_COLUMNS; card++) {
			int cellX = left + card % PREVIEW_COLUMNS * PREVIEW_CELL;
			int cellY = top + card / PREVIEW_COLUMNS * PREVIEW_CELL;
			int inner = PREVIEW_CELL - 4;
			ui.rounded(cellX, cellY, inner, inner, 2f, Draw.withOpacity(SHADOW, HIDDEN_CARD_OPACITY));
			boolean pair = card == PREVIEW_PAIR[0] || card == PREVIEW_PAIR[1];
			boolean collected = card == PREVIEW_COLLECTED[0] || card == PREVIEW_COLLECTED[1];
			boolean powerup = card == PREVIEW_POWERUP;
			if (highlightPairs.get() && pair) {
				ui.fill(cellX, cellY, inner, inner, Draw.withOpacity(GREEN, TINT_OPACITY));
			} else if (highlightPairs.get() && powerup) {
				ui.fill(cellX, cellY, inner, inner, Draw.withOpacity(LIGHT_BLUE, TINT_OPACITY));
			}
			if (keepItems.get() || collected) {
				ItemStack shown = pair ? previewItems[0] : card == PREVIEW_SEEN ? previewItems[1] : collected ? previewItems[2] : powerup ? previewItems[3] : ItemStack.EMPTY;
				if (!shown.isEmpty()) {
					graphics.item(shown, cellX + (inner - ITEM_SIZE) / 2, cellY + (inner - ITEM_SIZE) / 2);
				}
			}
			if (highlightPairs.get() && collected) {
				ui.fill(cellX, cellY, inner, inner, Draw.withOpacity(SHADOW, COLLECTED_DIM));
			}
		}
		if (info.get()) {
			int lineY = top + PREVIEW_ROWS * PREVIEW_CELL + INFO_GAP;
			for (int line = 0; line < INFO_LINES; line++) {
				ui.textAtScaled(PREVIEW_INFO[line], left, lineY + line * INFO_LINE, TextScale.SMALL, WHITE);
			}
		}
	}

	@Override
	public String previewNote() {
		return "A game of Superpairs, as these switches show it";
	}

	/** The preview's items, made when it is first drawn: a pair, one seen, a collected pair, a power-up. */
	private ItemStack[] previewItems;

	/** Lets the tracker of the rewards see the table. */
	public void trackWith(ExperimentProfit tracker) {
		profit = tracker;
	}

	/** Draws over the slots of a game's menu. */
	public void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof ContainerScreen chest && ExperimentFormats.menu(plain(chest.getTitle())) != null) {
				ScreenMouseEvents.allowMouseClick(screen).register((shown, event) -> !clickedHide(chest, event.x(), event.y()) && !clickedSettings(chest, event));
			}
		});
		SlotLayer.add((screen, graphics, mouseX, mouseY) -> {
			if (screen == screenFor && screen instanceof ContainerScreen chest) {
				this.mouseX = mouseX;
				this.mouseY = mouseY;
				draw(chest, graphics);
			}
		});
	}

	/** The server changed slots of the open menu: read it at once, on every update, not on a timer. */
	public void onMenuUpdate() {
		Minecraft client = Minecraft.getInstance();
		if (screenFor != null && client.gui.screen() == screenFor && screenFor instanceof ContainerScreen chest && menu != null) {
			int state = chest.getMenu().getStateId();
			stateFor = state;
			read(chest);
		}
	}

	/** Called every tick: reads the open menu. */
	public void tick(Minecraft client) {
		Screen screen = client.gui.screen();
		if (!isEnabled() || !SkyBlock.allowed() || !(screen instanceof ContainerScreen chest)) {
			if (screenFor != null) {
				// the menu closed: all of the game is forgotten, and the next menu of a game is a new run
				sequence.clear();
				mostClicks.closed();
				menu = null;
			}
			screenFor = null;
			if (screen == null) {
				tableSeen = false;
			}
			return;
		}
		String title = plain(chest.getTitle());
		if (screen != screenFor) {
			screenFor = screen;
			stateFor = -1;
			opened(client, chest, title);
		}
		remindOfGuardian(title);
		if (menu == null) {
			return;
		}
		int state = chest.getMenu().getStateId();
		boolean changed = state != stateFor;
		stateFor = state;
		if (changed) {
			read(chest);
		}
	}

	private void opened(Minecraft client, ContainerScreen chest, String title) {
		ExperimentFormats.Menu now = ExperimentFormats.menu(title);
		if (now != null && (menu == null || !menu.equals(now))) {
			board.clear();
			sequence.clear();
			ultraRareSeen = false;
			mostClicks.runStarted(now, Util.getMillis());
			roundLogged = -1;
			Debug.log("Experiments: {} ({}) started, rounds for the most clicks {}", now.game(), now.tier(), mostClicks.rounds());
			lastClicked = -1;
			clicksLeft = -1;
			gameOver = false;
		}
		menu = now;
		if (title.equals(ExperimentFormats.TABLE_TITLE) && !tableSeen) {
			tableSeen = true;
			guardianAsked = false;
			if (profit != null) {
				profit.tableOpened(client);
			}
		}
	}

	/** Reminds once per visit of the table, and only when it is known which pet is out; while that is not known it waits, and may still say it when the player list tells. */
	private void remindOfGuardian(String title) {
		if (guardianAsked || !tableSeen || !title.equals(ExperimentFormats.TABLE_TITLE)) {
			return;
		}
		Boolean out = PetTracker.isOut(GUARDIAN);
		if (out == null) {
			return;
		}
		guardianAsked = true;
		if (guardian.get() && !out) {
			guardianAlerts++;
			Toasts.info("No Guardian pet", "It gives more Enchanting Exp at the table");
		}
	}

	private void read(ContainerScreen chest) {
		switch (menu.game()) {
			case SUPERPAIRS -> readSuperpairs(chest);
			case CHRONOMATRON -> readChronomatron(chest);
			case ULTRASEQUENCER -> readUltrasequencer(chest);
		}
	}

	private void readSuperpairs(ContainerScreen chest) {
		var slots = chest.getMenu().slots;
		clicksLeft = ExperimentFormats.remainingClicks(plain(slots.get(ExperimentFormats.TOP_STATUS_SLOT).getItem().getHoverName()));
		List<Integer> places = ExperimentFormats.cardSlots(menu.tier());
		cardCount = places.size();
		List<SuperpairsBoard.Card<ItemStack>> cards = new ArrayList<>(places.size());
		int hiddenCards = 0;
		boolean proceed = false;
		for (int place : places) {
			if (place >= slots.size()) {
				continue;
			}
			ItemStack stack = slots.get(place).getItem();
			String name = plain(stack.getHoverName());
			if (stack.isEmpty()) {
				continue;
			}
			if (ExperimentFormats.isProceed(name)) {
				proceed = true;
				continue;
			}
			if (ExperimentFormats.isHiddenCard(name)) {
				hiddenCards++;
				cards.add(new SuperpairsBoard.Card<>(place, true, "", false, 0L, ItemStack.EMPTY));
				continue;
			}
			List<String> lore = loreOf(stack);
			String book = ExperimentFormats.ultraRareBook(lore);
			if (book != null && !ultraRareSeen) {
				ultraRareSeen = true;
				tellUltraRare(book);
			}
			cards.add(new SuperpairsBoard.Card<>(place, false, ExperimentFormats.cardKey(name, lore), ExperimentFormats.isPowerup(lore), ExperimentFormats.cardXp(name), stack.copy()));
		}
		board.update(cards, lastClicked, clicksLeft);
		proceed |= ExperimentFormats.isProceed(plain(slots.get(ExperimentFormats.PHASE_SLOT).getItem().getHoverName()));
		gameOver = ExperimentFormats.superpairsOver(clicksLeft, hiddenCards, cards.size(), proceed);
		ItemStack best = board.bestStillOpen();
		infoLines[0] = "Pairs found: " + board.collectedPairs() + " / " + cardCount / 2;
		infoLines[1] = "Clicks left: " + (clicksLeft < 0 ? "\u2013" : String.valueOf(clicksLeft));
		infoLines[2] = "XP so far: " + PanelKit.compact(board.xpCollected());
		infoLines[3] = "Best still open: " + (best == null ? "\u2013" : plain(best.getHoverName()));
	}

	private void tellUltraRare(String book) {
		if (profit != null) {
			profit.ultraRareFound();
		}
		if (alerts.get()) {
			ultraAlerts++;
			Toasts.info("ULTRA-RARE book", book);
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f));
		}
	}

	private void readChronomatron(ContainerScreen chest) {
		if (!onPrivateIsland()) {
			return;
		}
		var slots = chest.getMenu().slots;
		ExperimentFormats.Phase phase = ExperimentFormats.phase(plain(slots.get(ExperimentFormats.PHASE_SLOT).getItem().getHoverName()));
		int round = ExperimentFormats.round(plain(slots.get(ExperimentFormats.TOP_STATUS_SLOT).getItem().getHoverName()));
		Set<String> flashing = new HashSet<>();
		for (int i = 0; i < ExperimentFormats.PHASE_SLOT && i < slots.size(); i++) {
			ItemStack stack = slots.get(i).getItem();
			if (!stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith(TERRACOTTA)) {
				flashing.add(plain(stack.getHoverName()));
			}
		}
		sequence.seeChronomatron(phase, round, flashing);
		logRound(round, round);
		tellMostClicks(round);
	}

	private void readUltrasequencer(ContainerScreen chest) {
		if (!onPrivateIsland()) {
			return;
		}
		var slots = chest.getMenu().slots;
		ExperimentFormats.Phase phase = ExperimentFormats.phase(plain(slots.get(ExperimentFormats.PHASE_SLOT).getItem().getHoverName()));
		int round = ExperimentFormats.round(plain(slots.get(ExperimentFormats.TOP_STATUS_SLOT).getItem().getHoverName()));
		Map<Integer, Integer> numbers = new HashMap<>();
		StringBuilder read = Debug.on() ? new StringBuilder() : null;
		for (int i = 0; i < ExperimentFormats.PHASE_SLOT && i < slots.size(); i++) {
			if (i == ExperimentFormats.TOP_STATUS_SLOT) {
				continue;
			}
			ItemStack stack = slots.get(i).getItem();
			// the number is the item's name; should the game show it only as the stack's count, that counts too
			int named = ExperimentFormats.sequenceNumber(plain(stack.getHoverName()));
			int counted = stack.isEmpty() ? 0 : stack.getCount();
			int number = named > 0 ? named : counted > 1 ? counted : 0;
			if (number > 0) {
				numbers.put(i, number);
			}
			if (read != null && (named > 0 || counted > 1)) {
				read.append(' ').append(i).append(":name ").append(named).append("/count ").append(counted);
			}
		}
		sequence.seeUltrasequencer(phase, numbers);
		if (read != null) {
			Debug.log("Experiments: Ultrasequencer read: round {}, phase {}, slots{}; order {}", round, phase, read, sequence.known());
		}
		// the round the game shows; without it, the steps of the order
		int counted = round > 0 ? round : sequence.length();
		logRound(round, counted);
		tellMostClicks(counted);
	}

	/** Writes each round once to the debug log, with the rounds the run needs, to check when the alert comes against the game. */
	private void logRound(int shown, int counted) {
		if (counted != roundLogged) {
			roundLogged = counted;
			Debug.log("Experiments: {} round {} (shown {}), rounds for the most clicks {}", menu.game(), counted, shown, mostClicks.rounds());
		}
	}

	private void tellMostClicks(int round) {
		if (mostClicks.reached(round)) {
			Debug.log("Experiments: most clicks reached at round {}: {}", round, mostClicks.text());
			if (alerts.get()) {
				mostClicksAlerts++;
				Toasts.info("Most clicks reached", mostClicks.text());
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1f));
			}
		}
	}

	/**
	 * The player clicked a slot of the menu. A click that goes on reaches the game as it is; in the stakes, the tier clicked is read (only read).
	 *
	 * @param button the mouse button, as {@link ScreenInput#LEFT}
	 * @return whether the click is taken here: held back (not sent, the player's progress stays), or sent as the same click of the middle button
	 */
	public boolean onSlotClick(Screen screen, Slot slot, int button) {
		if (!isEnabled() || screen != screenFor || slot == null) {
			return false;
		}
		if (menu == null) {
			pickedTier(screen, slot);
			return false;
		}
		if (menu.game() == ExperimentFormats.Game.SUPERPAIRS) {
			lastClicked = slot.index;
			return false;
		}
		return clickInSequence((ContainerScreen) screen, slot, button);
	}

	/** A tier clicked in the stakes of Chronomatron or Ultrasequencer: its lore says how many rounds give the most clicks. */
	private void pickedTier(Screen screen, Slot slot) {
		ExperimentFormats.Game game = ExperimentFormats.stakesGame(plain(screen.getTitle()));
		if (game == null || game == ExperimentFormats.Game.SUPERPAIRS || slot.getItem().isEmpty()) {
			return;
		}
		MostClicks.Tier tier = mostClicks.picked(game, loreOf(slot.getItem()), Util.getMillis());
		Debug.log("Experiments: {} stakes, clicked '{}': line '{}', rounds {}, practice {}, locked {}", game, plain(slot.getItem().getHoverName()), tier.line(),
			tier.rounds(), tier.practice(), tier.locked());
	}

	private boolean clickInSequence(ContainerScreen chest, Slot slot, int button) {
		// the menu may have changed since the last tick: the click is judged by what the player sees now
		int state = chest.getMenu().getStateId();
		if (state != stateFor) {
			stateFor = state;
			read(chest);
		}
		boolean colors = menu.game() == ExperimentFormats.Game.CHRONOMATRON;
		String color = plain(slot.getItem().getHoverName());
		boolean right = colors ? sequence.isNextColor(color) : sequence.isNextSlot(slot.index);
		boolean stepsLeft = colors ? sequence.colorStepsLeft() : sequence.slotStepsLeft();
		String expected = colors ? sequence.nextColor(0) : String.valueOf(sequence.nextSlot(0));
		// a click on an empty slot is no step of the game either way
		if (!slot.getItem().isEmpty() && WrongClickGuard.holdsBack(blockWrongClicks.get(), onPrivateIsland(), Minecraft.getInstance().hasShiftDown(), stepsLeft, right)) {
			Debug.log("Experiments: click on slot {} held back, expected {}", slot.index, expected);
			heldBack(slot);
			return true;
		}
		// the expected step counts at once, without waiting for the server, so the next one lights up at once
		boolean accepted = colors ? sequence.clickedColor(color) : sequence.clickedSlot(slot.index);
		Debug.log("Experiments: click on slot {} ({}), expected {}, {}", slot.index, color, expected, accepted ? "accepted" : "not the expected step");
		if (accepted && (button == ScreenInput.LEFT || button == ScreenInput.RIGHT)) {
			// the same click, as a middle click (pick block): the game takes it as a click, and the item never lands on the cursor, so a click in
			// lag leaves no ghost item and shifts nothing (the idea is SkyHanni's)
			Minecraft client = Minecraft.getInstance();
			if (client.gameMode != null && client.player != null) {
				client.gameMode.handleContainerInput(chest.getMenu().containerId, slot.index, PICK_BLOCK_BUTTON, ContainerInput.CLONE, client.player);
				return true;
			}
		}
		return false;
	}

	private static boolean onPrivateIsland() {
		return SkyBlockIsland.ofArea(SkyBlockArea.island()) == SkyBlockIsland.PRIVATE_ISLAND;
	}

	private void heldBack(Slot slot) {
		heldBack++;
		flashSlot = slot.index;
		flashUntil = Util.getMillis() + WRONG_FLASH_MILLIS;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), WRONG_SOUND_PITCH, WRONG_SOUND_VOLUME));
	}

	/** Fades out on the slot whose click was held back. */
	private void drawWrongFlash(Canvas ui, ContainerScreen chest, int left, int top) {
		long millisLeft = flashUntil - Util.getMillis();
		var slots = chest.getMenu().slots;
		if (flashSlot < 0 || flashSlot >= slots.size() || millisLeft <= 0) {
			flashSlot = -1;
			return;
		}
		Slot slot = slots.get(flashSlot);
		ui.fill(left + slot.x, top + slot.y, ITEM_SIZE, ITEM_SIZE, Draw.withOpacity(WRONG_RED, TINT_OPACITY * millisLeft / WRONG_FLASH_MILLIS));
	}

	private void draw(ContainerScreen chest, GuiGraphicsExtractor graphics) {
		if (!isEnabled() || menu == null || chest != screenFor) {
			return;
		}
		Draw.setZoom(1f);
		Canvas ui = Draw.hudCanvas(graphics, 0xFF000000 | MainSettings.accentRgb(), 0f, true);
		graphics.nextStratum();
		var access = (ContainerScreenAccess) chest;
		int left = access.evskyblock$left();
		int top = access.evskyblock$top();
		SettingsButton.place(settingsBox, left + access.evskyblock$width(), top + access.evskyblock$height(), chest.width);
		SettingsButton.draw(ui, graphics, settingsBox, SETTINGS_TIP, Math.round(mouseX), Math.round(mouseY));
		switch (menu.game()) {
			case SUPERPAIRS -> {
				if (!gameOver) {
					drawSuperpairs(ui, graphics, chest, left, top);
				}
				drawInfo(ui, chest, left, top, access.evskyblock$width(), access.evskyblock$height());
			}
			case CHRONOMATRON -> {
				drawChronomatron(ui, chest, left, top);
				drawWrongFlash(ui, chest, left, top);
				drawDebug(ui, left, top + access.evskyblock$height() + INFO_GAP);
			}
			case ULTRASEQUENCER -> {
				drawUltrasequencer(ui, chest, left, top);
				drawWrongFlash(ui, chest, left, top);
				drawDebug(ui, left, top + access.evskyblock$height() + INFO_GAP);
			}
		}
	}

	/** One plain tint per card, and the item the game has shown on a card it has turned back; nothing else. */
	private void drawSuperpairs(Canvas ui, GuiGraphicsExtractor graphics, ContainerScreen chest, int left, int top) {
		var slots = chest.getMenu().slots;
		for (int place : ExperimentFormats.cardSlots(menu.tier())) {
			if (place >= slots.size()) {
				continue;
			}
			SuperpairsBoard.Shown<ItemStack> known = board.at(place);
			if (known == null) {
				continue;
			}
			Slot slot = slots.get(place);
			int x = left + slot.x;
			int y = top + slot.y;
			boolean keep = keepItems.get() && known.faceDown() && !known.payload().isEmpty();
			if (keep) {
				ui.fill(x, y, ITEM_SIZE, ITEM_SIZE, Draw.withOpacity(SHADOW, HIDDEN_CARD_OPACITY));
			}
			if (highlightPairs.get()) {
				if (known.mark() == SuperpairsBoard.Mark.KNOWN_PAIR) {
					ui.fill(x, y, ITEM_SIZE, ITEM_SIZE, Draw.withOpacity(GREEN, TINT_OPACITY));
				} else if (known.mark() == SuperpairsBoard.Mark.POWERUP) {
					ui.fill(x, y, ITEM_SIZE, ITEM_SIZE, Draw.withOpacity(LIGHT_BLUE, TINT_OPACITY));
				}
			}
			if (keep) {
				graphics.item(known.payload(), x, y);
			}
			if (highlightPairs.get() && known.mark() == SuperpairsBoard.Mark.COLLECTED) {
				ui.fill(x, y, ITEM_SIZE, ITEM_SIZE, Draw.withOpacity(SHADOW, COLLECTED_DIM));
			}
		}
	}

	/** The state of the memory games, for a screenshot when the marks are wrong: phase, round, known order, progress. */
	private void drawDebug(Canvas ui, int x, int y) {
		if (!debugOverlay.get()) {
			return;
		}
		ui.textAtScaled("Phase " + sequence.phase() + " \u00b7 round " + sequence.round(), x, y, TextScale.SMALL, WHITE);
		ui.textAtScaled("Known (" + sequence.length() + "): " + sequence.known(), x, y + DEBUG_LINE, TextScale.SMALL, WHITE);
		ui.textAtScaled("Progress " + sequence.done() + ", confirmed " + sequence.confirmed(), x, y + 2 * DEBUG_LINE, TextScale.SMALL, WHITE);
	}

	/** A small plain text under the menu, or beside it where the screen has no room below, with a word at its side that turns it off. */
	private void drawInfo(Canvas ui, ContainerScreen chest, int left, int top, int width, int height) {
		setBox(hideBox, 0, 0, 0, 0);
		if (!info.get() || infoLines[0] == null) {
			return;
		}
		int block = INFO_LINES * INFO_LINE;
		int x = left;
		int y = top + height + INFO_GAP;
		if (y + block > chest.height) {
			x = left + width + INFO_GAP;
			y = top;
		}
		for (int line = 0; line < INFO_LINES; line++) {
			ui.textAtScaled(ui.fitScaled(infoLines[line], INFO_WIDTH, TextScale.SMALL), x, y + line * INFO_LINE, TextScale.SMALL, WHITE);
		}
		int wordWidth = Math.round(ui.widthScaled(INFO_HIDE, TextScale.SMALL));
		int wordX = y == top ? x + INFO_WIDTH - wordWidth : left + width - wordWidth;
		setBox(hideBox, wordX, y, wordWidth, INFO_LINE);
		ui.textAtScaled(INFO_HIDE, wordX, y, TextScale.SMALL, Draw.withOpacity(WHITE, 0.6f));
	}

	private static void setBox(int[] box, int x, int y, int width, int height) {
		box[0] = x;
		box[1] = y;
		box[2] = width;
		box[3] = height;
	}

	private boolean clickedSettings(ContainerScreen chest, net.minecraft.client.input.MouseButtonEvent event) {
		return chest == screenFor && ScreenInput.button(event) == ScreenInput.LEFT && SettingsButton.clicked(settingsBox, chest, this, event.x(), event.y());
	}

	/** @return whether a click turned the info off: the setting, saved as usual. It never reaches the game's menu, and neither does anything else of ours. */
	private boolean clickedHide(ContainerScreen chest, double mouseX, double mouseY) {
		if (chest != screenFor || hideBox[2] == 0 || !PanelKit.inside(hideBox, mouseX, mouseY)) {
			return false;
		}
		info.set(false);
		setBox(hideBox, 0, 0, 0, 0);
		LumioApi.saveSoon();
		return true;
	}

	/** Only the next color green and the one after light green. */
	private void drawChronomatron(Canvas ui, ContainerScreen chest, int left, int top) {
		if (!nextClicks.get() || !sequence.repeating()) {
			return;
		}
		String next = sequence.nextColor(0);
		String after = sequence.nextColor(SECOND_NEXT);
		var slots = chest.getMenu().slots;
		for (int i = 0; i < ExperimentFormats.PHASE_SLOT && i < slots.size(); i++) {
			Slot slot = slots.get(i);
			ItemStack stack = slot.getItem();
			if (stack.isEmpty() || i == ExperimentFormats.TOP_STATUS_SLOT) {
				continue;
			}
			String name = SequenceMemory.colorOf(plain(stack.getHoverName()));
			if (name.equals(next)) {
				ui.fill(left + slot.x, top + slot.y, ITEM_SIZE, ITEM_SIZE, Draw.withOpacity(GREEN, TINT_OPACITY));
			} else if (name.equals(after)) {
				ui.fill(left + slot.x, top + slot.y, ITEM_SIZE, ITEM_SIZE, Draw.withOpacity(LIGHT_GREEN, TINT_OPACITY));
			}
		}
	}

	/** The next slot green, the ones after it light green and fading, and the number the game showed on each again. */
	private void drawUltrasequencer(Canvas ui, ContainerScreen chest, int left, int top) {
		if (!nextClicks.get() || !sequence.repeating()) {
			return;
		}
		var slots = chest.getMenu().slots;
		for (int ahead = 0; ahead <= ULTRA_AHEAD; ahead++) {
			int place = sequence.nextSlot(ahead);
			if (place < 0 || place >= slots.size()) {
				break;
			}
			Slot slot = slots.get(place);
			int color = ahead == 0 ? GREEN : LIGHT_GREEN;
			float opacity = ahead == 0 ? TINT_OPACITY : TINT_OPACITY * (1f - (ahead - 1) / (float) ULTRA_AHEAD);
			ui.fill(left + slot.x, top + slot.y, ITEM_SIZE, ITEM_SIZE, Draw.withOpacity(color, opacity));
		}
		for (int i = 0; i < ExperimentFormats.PHASE_SLOT && i < slots.size(); i++) {
			int number = sequence.numberAt(i);
			if (number > 0) {
				Slot slot = slots.get(i);
				String text = String.valueOf(number);
				ui.textAtScaledBold(text, left + slot.x + ITEM_SIZE - ui.boldWidthScaled(text, TextScale.SMALL) - 1, top + slot.y + ITEM_SIZE - 7, TextScale.SMALL, WHITE);
			}
		}
	}

	/**
	 * @return the line "Seen" with the name of the card the game showed on the face-down card of Superpairs under the mouse, in its rarity color (a
	 * book's enchant and level), or null: for a card never shown, and anywhere else
	 */
	public TooltipLine seenLine(ItemStack stack) {
		if (!isEnabled() || menu == null || menu.game() != ExperimentFormats.Game.SUPERPAIRS || !(screenFor instanceof ContainerScreen chest)
			|| Minecraft.getInstance().gui.screen() != screenFor || !ExperimentFormats.isHiddenCard(plain(stack.getHoverName()))) {
			return null;
		}
		Slot slot = ((ContainerScreenAccess) chest).evskyblock$hoveredSlot();
		if (slot == null || slot.getItem() != stack && !ItemStack.isSameItemSameComponents(slot.getItem(), stack)
			|| !ExperimentFormats.cardSlots(menu.tier()).contains(slot.index)) {
			return null;
		}
		SuperpairsBoard.Card<ItemStack> seen = board.seenFaceDown(slot.index);
		if (seen == null || seen.key().isEmpty()) {
			return null;
		}
		return new TooltipLine(TooltipLine.Kind.ROW, SEEN_WORD, seen.key(), "", rarityRgb(seen.payload()));
	}

	/** @return the color of a card's name, which is its rarity's, or of a book's enchant line; the usual color if it has none */
	private static int rarityRgb(ItemStack stack) {
		Component shown = stack.getHoverName();
		ItemLore lore = stack.get(DataComponents.LORE);
		if (plain(shown).equals(ENCHANTED_BOOK_NAME) && lore != null && lore.lines().size() > BOOK_NAME_LINE) {
			shown = lore.lines().get(BOOK_NAME_LINE);
		}
		return shown.visit((style, text) -> style.getColor() != null && !text.isBlank() ? Optional.of(style.getColor().getValue()) : Optional.<Integer>empty(),
			Style.EMPTY).orElse(TooltipLine.PLAIN);
	}

	static String plain(Component text) {
		return TextColors.noColors(text.getString()).strip();
	}

	static List<String> loreOf(ItemStack stack) {
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

	/** Logs a form that was not understood, for the player to send. */
	static void unknown(String what, Object line) {
		Debug.log("Experiments: {} not understood: {}", what, line);
	}
}
