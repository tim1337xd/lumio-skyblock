package dev.tim1337.lumio.skyblock.hud.buttons;

import com.mojang.blaze3d.platform.InputConstants;

import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.skyblock.data.ItemGains;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * The editor of the inventory buttons: an inventory in the middle with the 28 spots around it (free ones dashed), Quick add on the left, the
 * selected button on the right, and a bar with Undo and Done. Ready-made buttons are dragged onto a spot or clicked onto the next free one; buttons
 * are dragged between spots and dragged out (or ×) to remove them. Every change is saved at once and Undo takes the last steps back. Nothing is
 * sent from here.
 */
final class ButtonEditorScreen extends Screen {
	/** The inventory shown in the middle, as large as the game's. */
	private static final int WINDOW_WIDTH = 176;
	private static final int WINDOW_HEIGHT = 166;
	private static final int MARGIN = 10;
	private static final int SIDE_PANEL = 200;
	/** What the editor needs to show everything at full size, in GUI pixels: two side panels, the inventory with its spots, and the room between. */
	private static final int CONTENT_WIDTH = 2 * SIDE_PANEL + WINDOW_WIDTH + 2 * 24 + 4 * MARGIN;
	private static final int CONTENT_HEIGHT = 420;
	private static final int BAR_HEIGHT = 20;
	private static final int DRAG_START = 3;
	private static final int REMOVE = 7;
	private static final int SLOT = 18;
	private static final int SLOTS_LEFT = 8;
	private static final int MAIN_TOP = 84;
	private static final int HOTBAR_TOP = 142;
	private static final float DIM = 0.3f;
	private static final int MOST_RECENT_ICONS = 16;

	private final InventoryButtonsFeature feature;
	private final Screen back;
	private final EditorQuickAdd quickAdd = new EditorQuickAdd();
	private final EditorDetails details = new EditorDetails();
	private final IconPicker picker = new IconPicker();
	private final int[] window = new int[4];
	private final int[] place = new int[2];
	private final int[] undoBox = new int[4];
	private final int[] doneBox = new int[4];
	private int selected;
	/** The editor is drawn this much smaller where the screen (in GUI pixels) is too small for it, and its size in those smaller pixels. */
	private float zoom = 1f;
	private int viewWidth;
	private int viewHeight;
	/** The draft of a free spot's button: its icon, where it shows and its switches, until a command makes it a button. */
	private ButtonIcon draftIcon = ButtonIcon.DEFAULT;
	private InventoryButton.ShowIn draftShowIn = InventoryButton.ShowIn.BOTH;
	private boolean draftOnly = true;
	private boolean draftClose;
	/** What is held: a ready-made button, or the button of a spot; and where the press began. */
	private ButtonCatalog.Ready heldReady;
	private InventoryButton heldOwn;
	private int heldFrom = -1;
	private double pressX;
	private double pressY;
	private boolean dragging;

	ButtonEditorScreen(InventoryButtonsFeature feature, Screen back, int selected) {
		super(Component.literal("Inventory Buttons"));
		this.feature = feature;
		this.back = back;
		this.selected = selected;
		loadFields();
	}

	private ButtonLayout layout() {
		return feature.layout();
	}

	private int size() {
		return feature.buttonSize();
	}

	// ----------------------------------------------------------------------------------------------------------- layout

	private void layoutParts() {
		zoom = Math.min(1f, Math.min(width / (float) CONTENT_WIDTH, height / (float) CONTENT_HEIGHT));
		viewWidth = Math.round(width / zoom);
		viewHeight = Math.round(height / zoom);
		window[2] = WINDOW_WIDTH;
		window[3] = WINDOW_HEIGHT;
		window[0] = (viewWidth - WINDOW_WIDTH) / 2;
		int room = BAR_HEIGHT + 8 + ButtonSpots.GAP + size() + 6;
		window[1] = Math.max(MARGIN + room, (viewHeight - WINDOW_HEIGHT) / 2);
		int side = Math.max(120, Math.min(SIDE_PANEL, window[0] - ButtonSpots.GAP - size() - 2 * MARGIN));
		quickAdd.place(MARGIN, MARGIN, side, viewHeight);
		details.place(viewWidth - MARGIN - side, MARGIN, side);
	}

	// ------------------------------------------------------------------------------------------------------------- draw

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int screenMouseX, int screenMouseY, float partial) {
		layoutParts();
		graphics.fill(0, 0, width, height, 0xB0000000);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.scale(zoom, zoom);
		try {
			drawEditor(graphics, Math.round(screenMouseX / zoom), Math.round(screenMouseY / zoom));
		} finally {
			pose.popMatrix();
		}
	}

	private void drawEditor(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		Canvas ui = Draw.hudCanvas(graphics, 0xFF000000 | MainSettings.accentRgb(), 0f, true);
		drawInventory(ui, graphics);
		graphics.nextStratum();
		drawBar(ui, mouseX, mouseY);
		int hovered = drawSpots(ui, graphics, mouseX, mouseY);
		graphics.nextStratum();
		quickAdd.draw(ui, graphics, mouseX, mouseY);
		graphics.nextStratum();
		if (selected >= 0) {
			InventoryButton button = layout().get(selected);
			details.icons(iconChoices(button));
			details.draw(ui, graphics, button, button == null ? draftIcon : button.icon(), button == null ? draftShowIn : button.showIn(),
				button == null ? draftOnly : button.onlySkyBlock(), button == null ? draftClose : button.closeAfter(), selected, mouseX, mouseY);
		}
		if (hovered >= 0 && !dragging && !picker.isOpen()) {
			graphics.nextStratum();
			ButtonSpots.place(hovered, window, size(), viewWidth, viewHeight, place);
			ButtonLook.card(ui, layout().get(hovered), place[0], place[1], size(), viewWidth, true);
		}
		if (dragging) {
			drawHeld(ui, graphics, mouseX, mouseY);
		}
		picker.draw(ui, graphics, viewWidth, viewHeight, mouseX, mouseY);
	}

	/** The inventory in the middle: a glass window with its slots and the player's items, dimmed, as it is behind the buttons. */
	private void drawInventory(Canvas ui, GuiGraphicsExtractor graphics) {
		HudTheme.panel(ui, window[0], window[1], window[2], window[3], Radius.LG, HudTheme.opacity());
		ui.ring(window[0], window[1], window[2], window[3], Radius.LG, 1, Draw.withOpacity(HudTheme.text(), 0.1f));
		Minecraft client = Minecraft.getInstance();
		for (int row = 0; row < 4; row++) {
			int y = window[1] + (row < 3 ? MAIN_TOP + row * SLOT : HOTBAR_TOP);
			for (int column = 0; column < 9; column++) {
				int x = window[0] + SLOTS_LEFT + column * SLOT - 1;
				ui.rounded(x, y - 1, SLOT - 1, SLOT - 1, Radius.XS, Draw.withOpacity(0xFF000000, 0.3f));
				if (client.player != null) {
					ItemStack stack = client.player.getInventory().getItem(row < 3 ? 9 + row * 9 + column : column);
					if (!stack.isEmpty()) {
						graphics.item(stack, x, y - 1);
					}
				}
			}
		}
		graphics.nextStratum();
		ui.rounded(window[0], window[1], window[2], window[3], Radius.LG, Draw.withOpacity(0xFF000000, DIM));
		ui.textAtScaledBold("Inventory", window[0] + SLOTS_LEFT, window[1] + 8, TextScale.BODY, HudTheme.secondary());
	}

	private void drawBar(Canvas ui, int mouseX, int mouseY) {
		int y = window[1] - ButtonSpots.GAP - size() - 8 - BAR_HEIGHT;
		int x = window[0] - 12;
		int barWidth = window[2] + 24;
		HudTheme.panel(ui, x, y, barWidth, BAR_HEIGHT, Radius.SM, HudTheme.opacity());
		ui.ring(x, y, barWidth, BAR_HEIGHT, Radius.SM, 1, Draw.withOpacity(HudTheme.text(), 0.1f));
		ui.icon("pencil", x + 6, y + 5, 10, HudTheme.text());
		ui.textAtScaledBold("Editing buttons", x + 20, y + 6, TextScale.SMALL, HudTheme.text());
		String used = layout().used() + " of " + ButtonSpots.COUNT + " spots used";
		float usedX = x + 22 + ui.boldWidthScaled("Editing buttons", TextScale.SMALL) + 6;
		int doneWidth = EditorWidgets.buttonWidth(ui, "Done");
		int undoWidth = EditorWidgets.buttonWidth(ui, "Undo");
		EditorWidgets.set(doneBox, x + barWidth - 4 - doneWidth, y + 3, doneWidth, BAR_HEIGHT - 6);
		EditorWidgets.set(undoBox, doneBox[0] - 4 - undoWidth, y + 3, undoWidth, BAR_HEIGHT - 6);
		if (usedX + ui.widthScaled(used, TextScale.SMALL) < undoBox[0] - 4) {
			ui.textAtScaled(used, usedX, y + 6, TextScale.SMALL, HudTheme.secondary());
		}
		float before = Draw.alpha();
		if (!layout().canUndo()) {
			Draw.setAlpha(before * 0.4f);
		}
		EditorWidgets.button(ui, undoBox, "Undo", false, mouseX, mouseY);
		Draw.setAlpha(before);
		EditorWidgets.button(ui, doneBox, "Done", true, mouseX, mouseY);
	}

	/** @return the spot under the mouse with a button on it, or -1 */
	private int drawSpots(Canvas ui, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int size = size();
		int hovered = -1;
		int target = dragging ? spotAt(mouseX, mouseY) : -1;
		for (int i = 0; i < ButtonSpots.COUNT; i++) {
			if (!ButtonSpots.place(i, window, size, viewWidth, viewHeight, place)) {
				continue;
			}
			InventoryButton button = layout().get(i);
			boolean over = mouseX >= place[0] && mouseX < place[0] + size && mouseY >= place[1] && mouseY < place[1] + size;
			if (button == null || dragging && heldFrom == i) {
				EditorWidgets.dashed(ui, place[0], place[1], size, size, i == target || i == selected);
				if (i == selected && button == null) {
					ui.icon("plus", place[0] + size / 2 - 3, place[1] + size / 2 - 3, 7, ui.accent());
				}
				continue;
			}
			ButtonLook.State state = over && !dragging ? ButtonLook.State.HOVER : ButtonLook.State.NORMAL;
			ButtonLook.draw(ui, graphics, button.icon(), place[0], place[1], size, state, feature.boxed(), feature.glow());
			if (i == selected || i == target) {
				ui.ring(place[0] - 1, place[1] - 1, size + 2, size + 2, Radius.SM, 1, ui.accent());
			}
			if (over && !dragging) {
				hovered = i;
			}
			if ((over || i == selected) && !dragging) {
				drawRemove(ui, place[0] + size - REMOVE / 2 - 1, place[1] - REMOVE / 2 + 1);
			}
		}
		return hovered;
	}

	private static void drawRemove(Canvas ui, int x, int y) {
		ui.discAt(x + REMOVE / 2f, y + REMOVE / 2f, REMOVE, 0xFFFF453A);
		ui.icon("close", x + 1, y + 1, REMOVE - 2, 0xFFFFFFFF);
	}

	/** What is dragged, under the mouse, with what dropping it does. */
	private void drawHeld(Canvas ui, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.nextStratum();
		int size = size();
		ButtonIcon icon = heldReady != null ? heldReady.icon() : heldOwn.icon();
		ButtonLook.draw(ui, graphics, icon, mouseX - size / 2, mouseY - size / 2, size, ButtonLook.State.HOVER, true, true);
		int target = spotAt(mouseX, mouseY);
		String hint;
		if (heldReady != null) {
			hint = "Add \"" + heldReady.name() + "\" · " + heldReady.command();
		} else if (target < 0 && !overWindow(mouseX, mouseY)) {
			hint = "Remove \"" + heldOwn.name() + "\"";
		} else {
			hint = "Move \"" + heldOwn.name() + "\"";
		}
		float hintWidth = ui.boldWidthScaled(hint, TextScale.SMALL) + 10;
		int hintX = Math.min(mouseX + size / 2 + 4, viewWidth - Math.round(hintWidth) - 2);
		int hintY = mouseY + size / 2;
		ui.rounded(hintX, hintY, Math.round(hintWidth), 13, Radius.XS, ui.accent());
		ui.textAtScaledBold(hint, hintX + 5, hintY + 3, TextScale.SMALL, 0xFFFFFFFF);
	}

	// ------------------------------------------------------------------------------------------------------------- data

	/** @return the spot whose box is under the mouse (free or not), or -1 */
	private int spotAt(double mouseX, double mouseY) {
		int size = size();
		for (int i = 0; i < ButtonSpots.COUNT; i++) {
			if (ButtonSpots.place(i, window, size, viewWidth, viewHeight, place) && mouseX >= place[0] - 1 && mouseX < place[0] + size + 1 && mouseY >= place[1] - 1
				&& mouseY < place[1] + size + 1) {
				return i;
			}
		}
		return -1;
	}

	private boolean overWindow(double mouseX, double mouseY) {
		return EditorWidgets.inside(window, mouseX, mouseY);
	}

	/** @return the icons offered in the panel: the current one, the recent ones, then the ready-made ones */
	private List<ButtonIcon> iconChoices(InventoryButton button) {
		List<ButtonIcon> choices = new ArrayList<>();
		choices.add(button == null ? draftIcon : button.icon());
		for (ButtonIcon icon : feature.recentIcons()) {
			if (!choices.contains(icon)) {
				choices.add(icon);
			}
		}
		for (ButtonCatalog.Ready ready : ButtonCatalog.ALL) {
			if (!choices.contains(ready.icon())) {
				choices.add(ready.icon());
			}
		}
		return choices;
	}

	/** The fields show the selected button (or empty for a free spot). */
	private void loadFields() {
		InventoryButton button = selected >= 0 ? layout().get(selected) : null;
		details.command.set(button == null ? "" : button.command());
		details.name.set(button == null ? "" : button.name());
		details.command.focused = selected >= 0 && button == null;
		details.name.focused = false;
		draftIcon = ButtonIcon.DEFAULT;
		draftShowIn = InventoryButton.ShowIn.BOTH;
		draftOnly = true;
		draftClose = false;
		details.suggest();
	}

	private void select(int spot) {
		commitFields();
		selected = spot;
		loadFields();
	}

	/**
	 * Takes what the fields say into the selected button, as one step: a free spot gets its button once the command is valid; a known command
	 * brings its name along unless the player named the button.
	 */
	private void commitFields() {
		if (selected < 0) {
			return;
		}
		String command = InventoryButton.clean(details.command.value());
		String name = details.name.value().strip();
		InventoryButton button = layout().get(selected);
		if (button == null) {
			if (command == null) {
				return;
			}
			InventoryButton made = InventoryButton.of(command).withShowIn(draftShowIn).withOnlySkyBlock(draftOnly).withCloseAfter(draftClose);
			if (draftIcon != ButtonIcon.DEFAULT || ButtonCatalog.byCommand(command) == null) {
				made = made.withIcon(draftIcon);
			}
			put(selected, name.isEmpty() ? made : made.withName(name));
			return;
		}
		InventoryButton changed = button;
		if (command != null && !command.equals(button.command())) {
			changed = changed.withCommand(command);
			// a name that only said what the old command was follows the new command
			if (name.equals(button.name()) && isAutomaticName(button)) {
				ButtonCatalog.Ready ready = ButtonCatalog.byCommand(command);
				name = ready == null ? ButtonCatalog.guessName(command) : ready.name();
				if (ready != null && button.icon().equals(ButtonIcon.DEFAULT)) {
					changed = changed.withIcon(ready.icon());
				}
			}
		}
		if (!name.isEmpty() && !name.equals(changed.name())) {
			changed = changed.withName(name);
		}
		if (!changed.equals(button)) {
			put(selected, changed);
		}
	}

	private static boolean isAutomaticName(InventoryButton button) {
		ButtonCatalog.Ready ready = ButtonCatalog.byCommand(button.command());
		return ready != null ? ready.name().equals(button.name()) : ButtonCatalog.guessName(button.command()).equals(button.name());
	}

	/** Changes one spot as one step, and saves. */
	private void put(int spot, InventoryButton button) {
		layout().set(spot, button);
		feature.changed();
		if (spot == selected) {
			details.command.set(button == null ? "" : button.command());
			details.name.set(button == null ? "" : button.name());
		}
	}

	/** Changes the selected button (or the draft of a free spot). */
	private void change(UnaryOperator<InventoryButton> edit, Runnable draft) {
		InventoryButton button = selected >= 0 ? layout().get(selected) : null;
		if (button == null) {
			draft.run();
		} else {
			put(selected, edit.apply(button));
		}
	}

	private void setIcon(ButtonIcon icon) {
		feature.usedIcon(icon, MOST_RECENT_ICONS);
		change(button -> button.withIcon(icon), () -> draftIcon = icon);
	}

	/** @return the icon of the item in the player's hand: its SkyBlock id, or the game's id for an item of the game; null for an empty hand */
	private static ButtonIcon heldIcon() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return null;
		}
		ItemStack stack = client.player.getMainHandItem();
		if (stack.isEmpty()) {
			return null;
		}
		String id = ItemGains.idOf(stack);
		return ButtonIcon.item(id != null ? id : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
	}

	private ButtonIcon currentIcon() {
		InventoryButton button = selected >= 0 ? layout().get(selected) : null;
		return button == null ? draftIcon : button.icon();
	}

	/** Puts a ready-made button on the next free spot, selected. */
	private void addToNextFree(InventoryButton button) {
		int free = layout().nextFree();
		if (free < 0) {
			return;
		}
		commitFields();
		put(free, button);
		selected = free;
		loadFields();
	}

	// ------------------------------------------------------------------------------------------------------------ input

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x() / zoom;
		double mouseY = event.y() / zoom;
		int button = ScreenInput.button(event);
		if (picker.isOpen()) {
			return picker.click(mouseX, mouseY);
		}
		if (button != ScreenInput.LEFT) {
			return true;
		}
		if (EditorWidgets.inside(doneBox, mouseX, mouseY)) {
			onClose();
			return true;
		}
		if (EditorWidgets.inside(undoBox, mouseX, mouseY)) {
			undo();
			return true;
		}
		quickAdd.search.focused = quickAdd.search.inside(mouseX, mouseY);
		if (quickAdd.inside(mouseX, mouseY)) {
			details.command.focused = false;
			details.name.focused = false;
			ButtonCatalog.Ready ready = quickAdd.readyAt(mouseX, mouseY);
			if (ready != null) {
				press(mouseX, mouseY);
				heldReady = ready;
			} else if (EditorWidgets.inside(quickAdd.ownBox, mouseX, mouseY)) {
				ownCommand();
			}
			return true;
		}
		if (selected >= 0 && details.inside(mouseX, mouseY)) {
			clickDetails(mouseX, mouseY);
			return true;
		}
		int spot = spotAt(mouseX, mouseY);
		if (spot >= 0) {
			InventoryButton there = layout().get(spot);
			ButtonSpots.place(spot, window, size(), viewWidth, viewHeight, place);
			if (there != null && mouseX >= place[0] + size() - REMOVE && mouseY < place[1] + REMOVE - 1) {
				commitFields();
				put(spot, null);
				return true;
			}
			select(spot);
			if (there != null) {
				press(mouseX, mouseY);
				heldOwn = there;
				heldFrom = spot;
			}
			return true;
		}
		select(-1);
		return true;
	}

	private void press(double mouseX, double mouseY) {
		pressX = mouseX;
		pressY = mouseY;
		dragging = false;
		heldReady = null;
		heldOwn = null;
		heldFrom = -1;
	}

	/** "Own command…": the typed command onto the next free spot, or a free spot selected with its command field ready. */
	private void ownCommand() {
		String typed = InventoryButton.clean(quickAdd.search.value());
		int free = layout().nextFree();
		if (free < 0) {
			return;
		}
		if (typed != null && quickAdd.search.value().strip().startsWith("/")) {
			addToNextFree(InventoryButton.of(typed));
			quickAdd.search.set("");
			quickAdd.refilter();
			return;
		}
		select(free);
		details.command.focused = true;
	}

	private void clickDetails(double mouseX, double mouseY) {
		boolean hadFocus = details.command.focused || details.name.focused;
		EditorDetails.Action action = details.click(mouseX, mouseY, selected >= 0 && layout().get(selected) != null);
		if (hadFocus && action != EditorDetails.Action.FIELD && action != EditorDetails.Action.SUGGESTION) {
			commitFields();
		}
		switch (action) {
			case SUGGESTION -> {
				details.command.set(details.clickedSuggestion.command());
				details.name.set(details.clickedSuggestion.name());
				if (layout().get(selected) == null) {
					draftIcon = details.clickedSuggestion.icon();
				}
				commitFields();
				details.command.focused = false;
				details.suggest();
			}
			case DUPLICATE -> {
				int copy = layout().duplicate(selected);
				if (copy >= 0) {
					feature.changed();
					select(copy);
				}
			}
			case DELETE -> {
				put(selected, null);
				loadFields();
			}
			case ICON -> setIcon(details.clickedIcon);
			case PICK_ICON -> picker.open(currentIcon(), feature.recentIcons(), this::setIcon);
			case HELD_ICON -> {
				ButtonIcon held = heldIcon();
				if (held != null) {
					setIcon(held);
				}
			}
			case NEXT_COLOR -> {
				ButtonIcon icon = currentIcon();
				int next = 0;
				for (int i = 0; i < IconPicker.PALETTE.length; i++) {
					if (IconPicker.PALETTE[i] == icon.rgb()) {
						next = (i + 1) % IconPicker.PALETTE.length;
					}
				}
				setIcon(ButtonIcon.symbol(icon.value(), IconPicker.PALETTE[next]));
			}
			case SHOW_IN -> change(button -> button.withShowIn(details.clickedShowIn), () -> draftShowIn = details.clickedShowIn);
			case ONLY_SKYBLOCK -> change(button -> button.withOnlySkyBlock(!button.onlySkyBlock()), () -> draftOnly = !draftOnly);
			case CLOSE_AFTER -> change(button -> button.withCloseAfter(!button.closeAfter()), () -> draftClose = !draftClose);
			default -> {
			}
		}
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if ((heldReady != null || heldOwn != null) && !dragging
			&& Math.abs(event.x() / zoom - pressX) + Math.abs(event.y() / zoom - pressY) > DRAG_START) {
			dragging = true;
		}
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (heldReady == null && heldOwn == null) {
			return true;
		}
		double mouseX = event.x() / zoom;
		double mouseY = event.y() / zoom;
		int target = spotAt(mouseX, mouseY);
		if (heldReady != null) {
			if (!dragging) {
				addToNextFree(InventoryButton.of(heldReady.command()));
			} else if (target >= 0) {
				commitFields();
				put(target, InventoryButton.of(heldReady.command()));
				selected = target;
				loadFields();
			}
		} else if (dragging) {
			if (target >= 0 && target != heldFrom) {
				commitFields();
				layout().move(heldFrom, target);
				feature.changed();
				selected = target;
				loadFields();
			} else if (target < 0 && !overWindow(mouseX, mouseY) && !quickAdd.inside(mouseX, mouseY) && !details.inside(mouseX, mouseY)) {
				commitFields();
				put(heldFrom, null);
				selected = -1;
				loadFields();
			}
		}
		heldReady = null;
		heldOwn = null;
		heldFrom = -1;
		dragging = false;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		picker.scroll(scrollY);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (picker.key(event)) {
			return true;
		}
		boolean control = (ScreenInput.modifiers(event.modifiers()) & ScreenInput.CONTROL) != 0;
		boolean enter = event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER;
		if (details.command.focused || details.name.focused) {
			if (enter || event.key() == InputConstants.KEY_ESCAPE || event.key() == InputConstants.KEY_TAB) {
				boolean toName = event.key() == InputConstants.KEY_TAB && details.command.focused;
				commitFields();
				details.command.focused = false;
				details.name.focused = toName;
				details.suggest();
				return true;
			}
			if (details.command.key(event) || details.name.key(event)) {
				details.suggest();
				return true;
			}
		}
		if (quickAdd.search.focused) {
			if (enter) {
				ownCommand();
				return true;
			}
			if (event.key() == InputConstants.KEY_ESCAPE) {
				quickAdd.search.focused = false;
				return true;
			}
			if (quickAdd.search.key(event)) {
				quickAdd.refilter();
				return true;
			}
		}
		if (control && ScreenInput.letter(event.key(), 'z')) {
			undo();
			return true;
		}
		if (event.key() == InputConstants.KEY_DELETE && selected >= 0 && layout().get(selected) != null) {
			put(selected, null);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (picker.typed(event)) {
			return true;
		}
		if (details.command.typed(event) || details.name.typed(event)) {
			details.suggest();
			return true;
		}
		if (quickAdd.search.typed(event)) {
			quickAdd.refilter();
			return true;
		}
		return super.charTyped(event);
	}

	private void undo() {
		if (layout().canUndo()) {
			layout().undo();
			feature.changed();
			loadFields();
		}
	}

	@Override
	public void onClose() {
		commitFields();
		Minecraft.getInstance().gui.setScreen(back);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
