package dev.tim1337.lumio.skyblock.dungeon.f7;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.ui.Toasts;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.api.experimental.util.ServerTicks;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.feature.KeyPlace;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;
import dev.tim1337.lumio.skyblock.dungeon.Dungeon;
import dev.tim1337.lumio.skyblock.dungeon.DungeonTime;
import dev.tim1337.lumio.skyblock.mixin.accessor.ContainerScreenAccess;
import dev.tim1337.lumio.skyblock.mixin.accessor.SlotAccess;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The terminal solver for the six Floor 7 terminals ({@link Terminals}). Either Lumio's own grid over the game's menu, where one click on a cell is
 * exactly one click on that slot of the menu, or highlights on the game's own menu. Optionally the first clicks after the menu opens are held back
 * for a moment (a click that early is lost on the server anyway). It never clicks by itself and never queues a click: every click is the player's,
 * one for one.
 */
public class TerminalSolverFeature extends AddonFeature {
	/** Lumio's grid, or highlights on the game's menu. */
	public enum Look {
		LUMIO("Lumio's"), GAME("The game's");

		final String label;

		Look(String label) {
			this.label = label;
		}
	}

	/** How long the first clicks after the menu opens are held back. */
	public enum FirstClick {
		OFF("Off"), MILLIS("200 ms"), TICKS("Lag ticks");

		final String label;

		FirstClick(String label) {
			this.label = label;
		}
	}

	private static final int ROW = 9;
	private static final String FILLER = "black_stained_glass_pane";
	private static final int OFF = -10000;
	private static final long FIRST_CLICK_MILLIS = 200;
	private static final long FIRST_CLICK_TICKS = 4;
	/** The click data of a container click: the left and the right button. */
	private static final int CLICK_LEFT = 0;
	private static final int CLICK_RIGHT = 1;
	private static final int CELL = 22;
	private static final int CELL_GAP = 3;
	private static final int PAD = 12;
	private static final int HEADER = 18;
	private static final int FOOTER = 18;
	private static final float CELL_RADIUS = 5f;
	private static final int NEXT = SystemColors.GREEN;
	private static final int THEN = 0xFF1F8A3A;
	/** The footer's note, longest first. */
	private static final String[] HINTS = {"Lumio GUI · you click, one click per slot", "One click per slot"};
	private static final int FOOTER_GAP = 12;
	private static final int LATER = 0xFF3A3A40;
	private static final int RIGHT_CLICKS = 0xFFB55A1E;
	private static final float SOUND_PITCH = 1.8f;

	private final Choice<Look> look = add(make().choice("look", Component.literal("Look"), Look.LUMIO, value -> Component.literal(value.label),
		Choice.Style.SEGMENTED), "General", "One click on a cell, one slot click");
	private final Toggle hideWrong = add(make().toggle("hide_wrong", Component.literal("Hide wrong items"), true), "General", "Only the items to click show");
	private final Choice<Terminals.RubixMode> rubix = add(make().choice("rubix", Component.literal("Color terminal"), Terminals.RubixMode.FEWEST,
		value -> Component.literal(value == Terminals.RubixMode.FEWEST ? "Fewest clicks" : "Left only"), Choice.Style.SEGMENTED), "General");
	private final Choice<FirstClick> firstClick = add(make().choice("first_click", Component.literal("First-click protection"), FirstClick.MILLIS,
		value -> Component.literal(value.label), Choice.Style.SEGMENTED), "General", "Holds back clicks right at the start");
	private final Toggle sounds = add(make().toggle("sounds", Component.literal("Click sound"), true), "General");
	private final Toggle times = add(make().toggle("times", Component.literal("Show time"), true), "General", "A pop-up with the terminal's time");
	private final Toggle blockWrong = add(make().toggle("block_wrong_clicks", Component.literal("Block wrong clicks"), false), "Block wrong clicks",
		"Known wrong clicks are not sent");
	private final Toggle blockedSound = add(make().toggle("blocked_sound", Component.literal("Blocked click sound"), true), "Block wrong clicks");

	private Screen kindScreen;
	private Terminals.Kind kindOfScreen;
	/** The menu being solved: its id, kind, when it opened, the slots clicked, and the solution of its current contents. */
	private int containerId = -1;
	private Terminals.Kind kind;
	private String title = "";
	private long openedMillis;
	private long openedTick;
	private final Set<Integer> clicked = new HashSet<>();
	private String rubixTarget;
	private int solvedState = -1;
	private Terminals.Solution solution = Terminals.Solution.NONE;
	private int clicks;
	/** Whether the menu's contents were read as a terminal's; the right clicks sent that the menu has not shown yet, and when the last one went. */
	private boolean loaded;
	private int ahead;
	private long aheadMillis;
	/** The slot whose click was blocked last and when, for the short red flash. */
	private int blockedSlot = -1;
	private long blockedMillis;
	private static final long AHEAD_MILLIS = 1000;
	private static final long FLASH_MILLIS = 250;
	private static final int BLOCKED = SystemColors.RED;

	public TerminalSolverFeature() {
		super("dungeon_terminals", "Terminal solver", "F7 terminal helpers. Every click is yours.", false);
		iconRgb(SystemColors.GREEN_RGB);
		iconName("terminal");
		summary("Six terminals, every click yours");
		badge("P3");
		// Lumio's grid has only the answers; the switch is for the game's menu
		hideWrong.visibleWhen(() -> look.get() != Look.LUMIO);
		blockedSound.visibleWhen(blockWrong::get);
	}

	/** Starts with the add-on: the key that lets every click through while held, listed in this sheet too. */
	public void register() {
		addKey(WrongClickKey.key(), KeyPlace.SCREENS);
	}

	/** @return the clicks sent that the menu has not shown yet; forgotten when the menu does not answer for a while */
	private int ahead() {
		if (ahead > 0 && System.currentTimeMillis() - aheadMillis > AHEAD_MILLIS) {
			ahead = 0;
		}
		return ahead;
	}

	/** A click that was not sent: the slot flashes red, the sound plays if wanted, and the debug log counts it. */
	private void blocked(int slot, String reason) {
		blockedSlot = slot;
		blockedMillis = System.currentTimeMillis();
		if (blockedSound.get()) {
			WrongClickKey.playBlocked();
		}
		if (DebugHooks.ACTIVE) {
			DebugHooks.log(SkyBlockDebug.DUNGEON, "Terminal: {}", WrongClicks.note(kind == null ? "terminal" : kind.title(), reason));
		}
	}

	private boolean flashes(int slot) {
		return slot == blockedSlot && System.currentTimeMillis() - blockedMillis < FLASH_MILLIS;
	}

	/** @return the terminal of a screen, while in a dungeon, or null */
	private Terminals.Kind kindOf(Screen screen) {
		if (!isEnabled() || !Dungeon.in() || !(screen instanceof ContainerScreen)) {
			return null;
		}
		// asked several times a frame: the title is read once per screen
		if (screen != kindScreen) {
			kindScreen = screen;
			kindOfScreen = Terminals.kindOf(screen.getTitle().getString());
		}
		return kindOfScreen;
	}

	/** @return whether Lumio's grid covers this screen (the melody keeps the game's menu: it is about timing, not about which slot) */
	public boolean takesOver(Screen screen) {
		Terminals.Kind found = kindOf(screen);
		return found != null && found != Terminals.Kind.MELODY && look.get() == Look.LUMIO;
	}

	/** The menu was laid out: under Lumio's grid its slots go out of the way, so no click lands on them. */
	public void layout(ContainerScreen screen) {
		for (Slot slot : screen.getMenu().slots) {
			((SlotAccess) slot).evskyblock$setX(OFF);
			((SlotAccess) slot).evskyblock$setY(OFF);
		}
	}

	/** Keeps the solution of the menu's current contents; solved again only when the server changed them. */
	private boolean refresh(AbstractContainerScreen<?> screen) {
		Terminals.Kind found = kindOf(screen);
		if (found == null) {
			return false;
		}
		int id = screen.getMenu().containerId;
		if (id != containerId || found != kind) {
			containerId = id;
			kind = found;
			title = screen.getTitle().getString();
			openedMillis = System.currentTimeMillis();
			openedTick = ServerTicks.count();
			clicked.clear();
			rubixTarget = null;
			solvedState = -1;
			clicks = 0;
			loaded = false;
			ahead = 0;
			blockedSlot = -1;
		}
		int state = screen.getMenu().getStateId();
		if (state != solvedState) {
			solvedState = state;
			List<Terminals.Cell> cells = cells(screen);
			// the menu answered: one click on its way has arrived
			ahead = WrongClicks.settle(ahead, 1);
			loaded = kind == Terminals.Kind.MELODY ? Terminals.melodyColumn(cells) >= 0 : !cells.isEmpty();
			if (kind == Terminals.Kind.RUBIX && rubixTarget == null && !cells.isEmpty()) {
				rubixTarget = Terminals.rubixTarget(cells, rubix.get());
			}
			Terminals.Solution previous = solution;
			solution = Terminals.solve(kind, title, cells, clicked, rubix.get(), rubixTarget);
			if (solution.isEmpty() && !previous.isEmpty() && clicks > 0 && kind != Terminals.Kind.MELODY) {
				finished();
			}
		}
		return true;
	}

	private static List<Terminals.Cell> cells(AbstractContainerScreen<?> screen) {
		List<Slot> slots = screen.getMenu().slots;
		int chest = chestSize(screen);
		List<Terminals.Cell> cells = new ArrayList<>(chest);
		for (int i = 0; i < chest && i < slots.size(); i++) {
			ItemStack stack = slots.get(i).getItem();
			if (stack.isEmpty()) {
				continue;
			}
			String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
			boolean shines = stack.hasFoil() && !new ItemStack(stack.getItem()).hasFoil();
			cells.add(new Terminals.Cell(i, id, stack.getCount(), stack.getHoverName().getString(), shines));
		}
		return cells;
	}

	private static int chestSize(AbstractContainerScreen<?> screen) {
		return screen instanceof ContainerScreen container ? container.getMenu().getRowCount() * ROW : 0;
	}

	private void finished() {
		if (times.get()) {
			long ticks = ServerTicks.count() - openedTick;
			Toasts.brief("terminal", "Terminal done", DungeonTime.seconds(ticks) + " · " + clicks + " clicks", 4f);
		}
	}

	/** @return whether a click now would be one of the first ones the player chose to hold back */
	private boolean tooEarly() {
		return switch (firstClick.get()) {
			case OFF -> false;
			case MILLIS -> System.currentTimeMillis() - openedMillis < FIRST_CLICK_MILLIS;
			case TICKS -> ServerTicks.count() - openedTick < FIRST_CLICK_TICKS;
		};
	}

	/** Sends exactly one click on a slot, as the player's own click on it would. */
	private void clickSlot(AbstractContainerScreen<?> screen, int slot, int button) {
		Minecraft client = Minecraft.getInstance();
		if (client.gameMode == null || client.player == null) {
			return;
		}
		client.gameMode.handleContainerInput(screen.getMenu().containerId, slot, button, ContainerInput.PICKUP, client.player);
		if (kind == Terminals.Kind.STARTS_WITH || kind == Terminals.Kind.SELECT) {
			clicked.add(slot);
		}
		clicks++;
		if (sounds.get()) {
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, SOUND_PITCH));
		}
	}

	// ---- Lumio's grid -------------------------------------------------------------------------------------------------------------------------

	private int gridWidth() {
		return ROW * CELL + (ROW - 1) * CELL_GAP;
	}

	private int panelWidth() {
		return gridWidth() + 2 * PAD;
	}

	private int panelHeight(int rows) {
		return PAD + HEADER + rows * CELL + (rows - 1) * CELL_GAP + FOOTER + PAD;
	}

	/** Draws the grid over the dimmed world. */
	public void draw(ContainerScreen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!refresh(screen)) {
			return;
		}
		int rows = screen.getMenu().getRowCount();
		int width = panelWidth();
		int height = panelHeight(rows);
		int left = (screen.width - width) / 2;
		int top = (screen.height - height) / 2;
		graphics.fill(0, 0, screen.width, screen.height, 0x99000000);
		Canvas ui = Draw.hudCanvas(graphics, SystemColors.GREEN, 0f, false);
		HudTheme.panel(ui, left, top, width, height, 12f, Math.max(0.85f, HudTheme.opacity()));
		ui.sectionLabel(kind.title().toUpperCase(java.util.Locale.ROOT), left + PAD, top + PAD, HudTheme.secondary());
		String progress = solution.order().size() + " left · " + DungeonTime.seconds(ServerTicks.count() - openedTick);
		ui.textAtScaled(progress, left + width - PAD - ui.widthScaled(progress, TextScale.SMALL), top + PAD, TextScale.SMALL, HudTheme.secondary());
		int gridTop = top + PAD + HEADER;
		boolean early = tooEarly();
		for (int slot = 0; slot < rows * ROW; slot++) {
			int x = left + PAD + slot % ROW * (CELL + CELL_GAP);
			int y = gridTop + slot / ROW * (CELL + CELL_GAP);
			int place = solution.place(slot);
			int color = place < 0 ? Draw.withOpacity(HudTheme.slot(), 0.6f) : cellColor(slot, place);
			if (early && place >= 0) {
				color = Draw.withOpacity(color, 0.5f);
			}
			if (flashes(slot)) {
				color = BLOCKED;
			}
			ui.roundedSmooth(x, y, CELL, CELL, CELL_RADIUS, color);
			if (kind == Terminals.Kind.STARTS_WITH || kind == Terminals.Kind.SELECT) {
				// the items themselves, so the player sees what the cell is (the frame's black panes stay empty cells)
				ItemStack stack = screen.getMenu().slots.get(slot).getItem();
				if (!stack.isEmpty() && !BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().equals(FILLER)) {
					graphics.item(stack, x + (CELL - 16) / 2, y + (CELL - 16) / 2);
				}
			}
			String label = label(screen, slot, place);
			if (label != null) {
				ui.textCentered(label, x + CELL / 2, y + CELL / 2, 0xFFFFFFFF);
			}
		}
		int footerY = top + height - PAD - 8;
		ui.discAt(left + PAD + 4, footerY + 4, 7, NEXT);
		ui.textAtScaled("Next", left + PAD + 12, footerY, TextScale.SMALL, HudTheme.secondary());
		ui.discAt(left + PAD + 46, footerY + 4, 7, THEN);
		ui.textAtScaled("Then", left + PAD + 54, footerY, TextScale.SMALL, HudTheme.secondary());
		// the note on the right only where it fits beside the legend, else its short form, else nothing
		float legendEnd = left + PAD + 54 + ui.widthScaled("Then", TextScale.SMALL) + FOOTER_GAP;
		for (String hint : HINTS) {
			float hintX = left + width - PAD - ui.widthScaled(hint, TextScale.SMALL);
			if (hintX >= legendEnd) {
				ui.textAtScaled(hint, hintX, footerY, TextScale.SMALL, HudTheme.secondary());
				break;
			}
		}
	}


	private int cellColor(int slot, int place) {
		if (kind == Terminals.Kind.RUBIX && solution.clicksAt(slot) < 0) {
			return RIGHT_CLICKS;
		}
		if (kind == Terminals.Kind.NUMBERS) {
			return place == 0 ? NEXT : place == 1 ? THEN : LATER;
		}
		return NEXT;
	}

	/** @return the text on a cell: the number for the order terminal, the clicks for the color terminal, else none */
	private String label(ContainerScreen screen, int slot, int place) {
		if (place < 0) {
			return null;
		}
		if (kind == Terminals.Kind.NUMBERS) {
			return String.valueOf(screen.getMenu().slots.get(slot).getItem().getCount());
		}
		if (kind == Terminals.Kind.RUBIX) {
			return String.valueOf(Math.abs(solution.clicksAt(slot)));
		}
		return null;
	}

	/** A click on the grid: on a cell of the solution, exactly one click on that slot; everything else does nothing. */
	public boolean mouseClicked(ContainerScreen screen, double mouseX, double mouseY, int button) {
		if (!refresh(screen) || tooEarly()) {
			return true;
		}
		int rows = screen.getMenu().getRowCount();
		int left = (screen.width - panelWidth()) / 2 + PAD;
		int top = (screen.height - panelHeight(rows)) / 2 + PAD + HEADER;
		int column = (int) Math.floor((mouseX - left) / (CELL + CELL_GAP));
		int row = (int) Math.floor((mouseY - top) / (CELL + CELL_GAP));
		if (column < 0 || column >= ROW || row < 0 || row >= rows) {
			return true;
		}
		int slot = row * ROW + column;
		int place = solution.place(slot);
		// the order terminal takes only the next number; the others any cell of the solution
		if (place < 0 || kind == Terminals.Kind.NUMBERS && place != 0) {
			// the grid never sends such a click; with "Block wrong clicks" on it also says so
			if (WrongClicks.terminal(blockWrong.get(), false, kind, solution, loaded, slot, rows * ROW, 0)) {
				blocked(slot, "slot " + slot + " is not the next click (Lumio GUI)");
			}
			return true;
		}
		int clickButton = ScreenInput.button(button) == ScreenInput.RIGHT ? CLICK_RIGHT : CLICK_LEFT;
		clickSlot(screen, slot, clickButton);
		return true;
	}

	// ---- highlights on the game's menu ---------------------------------------------------------------------------------------------------------

	/** Highlights on the game's menu (and the melody, which always keeps the game's menu). */
	public void drawOnMenu(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics) {
		if (takesOver(screen) || !refresh(screen)) {
			return;
		}
		ContainerScreenAccess access = (ContainerScreenAccess) screen;
		int chest = chestSize(screen);
		List<Slot> slots = screen.getMenu().slots;
		for (int i = 0; i < chest && i < slots.size(); i++) {
			Slot slot = slots.get(i);
			int x = access.evskyblock$left() + slot.x;
			int y = access.evskyblock$top() + slot.y;
			int place = solution.place(i);
			if (place >= 0) {
				graphics.fill(x, y, x + 16, y + 16, Draw.withOpacity(cellColor(i, place), 0.55f));
			} else if (hideWrong.get() && kind != Terminals.Kind.MELODY && !slot.getItem().isEmpty()) {
				graphics.fill(x, y, x + 16, y + 16, 0xF0202024);
			}
			if (flashes(i)) {
				graphics.fill(x, y, x + 16, y + 16, Draw.withOpacity(BLOCKED, 0.7f));
			}
		}
	}

	/**
	 * A click on the game's menu: held back while it is too early, and, with "Block wrong clicks" on, when the solver knows it is wrong (not
	 * sent, nothing sent in its place); otherwise it goes to the game unchanged and is only noted.
	 */
	public boolean clickOnMenu(Screen screen, Slot hovered, int button) {
		if (!(screen instanceof AbstractContainerScreen<?> container) || takesOver(screen) || !refresh(container)) {
			return false;
		}
		if (tooEarly()) {
			return true;
		}
		if (hovered != null && WrongClicks.terminal(blockWrong.get(), WrongClickKey.holding(), kind, solution, loaded, hovered.index, chestSize(container),
			ahead())) {
			blocked(hovered.index, "slot " + hovered.index + (solution.place(hovered.index) < 0 ? " is no answer" : " is not the next number"));
			return true;
		}
		if (hovered != null && kind == Terminals.Kind.NUMBERS && solution.place(hovered.index) == ahead()) {
			// the number after it may follow before the menu shows this one gone
			ahead++;
			aheadMillis = System.currentTimeMillis();
		}
		if (hovered != null && solution.place(hovered.index) >= 0) {
			if (kind == Terminals.Kind.STARTS_WITH || kind == Terminals.Kind.SELECT) {
				clicked.add(hovered.index);
			}
			clicks++;
		}
		return false;
	}
}
