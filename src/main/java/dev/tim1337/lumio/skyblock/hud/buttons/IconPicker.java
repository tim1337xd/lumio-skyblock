package dev.tim1337.lumio.skyblock.hud.buttons;

import com.mojang.blaze3d.platform.InputConstants;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.skyblock.data.ItemRepo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The icon picker of the editor: a search over the item list (its items, blocks and heads, drawn as the real items with their glint), Lumio's
 * symbols in a color the player picks, and the icons used last. Everything comes from the item list on disk: no skin is looked up anywhere.
 */
final class IconPicker {
	enum Tab {
		ITEMS("Items"), BLOCKS("Blocks"), HEADS("Heads"), SYMBOLS("Symbols"), RECENT("Recent");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	/** The symbols' colors to pick from. */
	static final int[] PALETTE = {0x34C759, 0x64A8FF, 0xFFD60A, 0xFF5E57, 0xC77DFF, 0xFF9F0A, 0xFF6EC7, 0xE8E8EE};
	private static final int WIDTH = 214;
	private static final int HEIGHT = 250;
	private static final int PAD = 10;
	private static final int COLUMNS = 8;
	private static final int CELL = 22;
	private static final int TILE = 20;
	private static final int MOST_RESULTS = 600;
	private static final int TAB_HEIGHT = 14;
	private static final int SWATCH = 10;

	private final TextField search = new TextField(40, "Search items…", "");
	private final List<ItemRepo.Entry> items = new ArrayList<>();
	private final List<ItemRepo.Entry> blocks = new ArrayList<>();
	private final List<ItemRepo.Entry> heads = new ArrayList<>();
	private final List<ButtonIcon> shown = new ArrayList<>();
	private final int[] panel = new int[4];
	private final int[][] tabBoxes = new int[Tab.values().length][4];
	private final int[][] swatchBoxes = new int[PALETTE.length][4];
	private final int[] useBox = new int[4];
	private final int[] closeBox = new int[4];
	private int gridTop;
	private int gridRows;
	private Tab tab = Tab.ITEMS;
	private int scrollRows;
	private int symbolRgb = PALETTE[4];
	private ButtonIcon selected;
	private boolean open;
	private boolean sorted;
	private List<ButtonIcon> recent = List.of();
	private Consumer<ButtonIcon> chosen = icon -> {
	};

	boolean isOpen() {
		return open;
	}

	/** Opens the picker; the chosen icon goes to {@code done}. */
	void open(ButtonIcon current, List<ButtonIcon> recentIcons, Consumer<ButtonIcon> done) {
		open = true;
		chosen = done;
		recent = recentIcons;
		selected = current;
		if (current != null && current.kind() == ButtonIcon.Kind.SYMBOL) {
			symbolRgb = current.rgb();
		}
		search.set("");
		search.focused = true;
		sortItems();
		refilter();
	}

	void close() {
		open = false;
		search.focused = false;
	}

	/** Sorts the item list into items, blocks and heads, once per opening. */
	private void sortItems() {
		sorted = ItemRepo.ready();
		items.clear();
		blocks.clear();
		heads.clear();
		for (ItemRepo.Entry entry : ItemRepo.items()) {
			if (entry.head()) {
				heads.add(entry);
				continue;
			}
			Identifier key = entry.gameItemId() == null ? null : Identifier.tryParse(entry.gameItemId().toLowerCase(Locale.ROOT));
			boolean block = key != null && BuiltInRegistries.ITEM.containsKey(key) && BuiltInRegistries.ITEM.getValue(key) instanceof BlockItem;
			(block ? blocks : items).add(entry);
		}
	}

	private void refilter() {
		shown.clear();
		scrollRows = 0;
		String text = search.value().strip().toLowerCase(Locale.ROOT);
		String[] words = text.isEmpty() ? new String[0] : text.split("\\s+");
		switch (tab) {
			case SYMBOLS -> {
				for (String symbol : ButtonIcon.SYMBOLS) {
					if (text.isEmpty() || symbol.contains(text)) {
						shown.add(ButtonIcon.symbol(symbol, symbolRgb));
					}
				}
			}
			case RECENT -> shown.addAll(recent);
			default -> {
				List<ItemRepo.Entry> from = tab == Tab.ITEMS ? items : tab == Tab.BLOCKS ? blocks : heads;
				for (ItemRepo.Entry entry : from) {
					if (shown.size() >= MOST_RESULTS) {
						break;
					}
					if (words.length == 0 || ItemRepo.matches(entry, words, false) > 0) {
						shown.add(ButtonIcon.item(entry.id));
					}
				}
			}
		}
	}

	void draw(Canvas ui, GuiGraphicsExtractor graphics, int screenWidth, int screenHeight, int mouseX, int mouseY) {
		if (!open) {
			return;
		}
		// opened while the item list was still loading: it fills in as soon as the list is there
		if (!sorted && ItemRepo.ready()) {
			sortItems();
			refilter();
		}
		graphics.nextStratum();
		ui.fill(0, 0, screenWidth, screenHeight, 0x80000000);
		panel[0] = (screenWidth - WIDTH) / 2;
		panel[1] = Math.max(4, (screenHeight - HEIGHT) / 2);
		panel[2] = WIDTH;
		panel[3] = Math.min(HEIGHT, screenHeight - 8);
		HudTheme.panel(ui, panel[0], panel[1], panel[2], panel[3], Radius.LG, Math.max(0.92f, HudTheme.opacity()));
		ui.ring(panel[0], panel[1], panel[2], panel[3], Radius.LG, 1, Draw.withOpacity(HudTheme.text(), 0.12f));
		int x = panel[0] + PAD;
		int y = panel[1] + PAD;
		ui.textAtScaledBold("Choose an icon", x, y, TextScale.BODY, HudTheme.text());
		set(closeBox, panel[0] + panel[2] - PAD - 10, y - 1, 10, 10);
		ui.icon("close", closeBox[0], closeBox[1], 10, PanelKit.inside(closeBox, mouseX, mouseY) ? HudTheme.text() : HudTheme.secondary());
		y += 14;
		search.place(x, y, WIDTH - 2 * PAD, 18);
		search.draw(ui, mouseX, mouseY);
		y += 22;
		int tabX = x;
		for (Tab each : Tab.values()) {
			int width = Math.round(ui.widthScaled(each.label, TextScale.SMALL)) + 10;
			if (each == Tab.RECENT) {
				tabX = panel[0] + panel[2] - PAD - width;
			}
			set(tabBoxes[each.ordinal()], tabX, y, width, TAB_HEIGHT);
			boolean on = each == tab;
			boolean over = PanelKit.inside(tabBoxes[each.ordinal()], mouseX, mouseY);
			ui.rounded(tabX, y, width, TAB_HEIGHT, Radius.XS, Draw.withOpacity(HudTheme.text(), on ? 0.18f : over ? 0.1f : 0.05f));
			ui.textAtScaled(each.label, tabX + 5, y + 3.5f, TextScale.SMALL, on ? HudTheme.text() : HudTheme.secondary());
			tabX += width + 3;
		}
		y += TAB_HEIGHT + 5;
		if (tab == Tab.SYMBOLS) {
			for (int i = 0; i < PALETTE.length; i++) {
				set(swatchBoxes[i], x + i * (SWATCH + 4), y, SWATCH, SWATCH);
				ui.roundedSmooth(swatchBoxes[i][0], y, SWATCH, SWATCH, Radius.XS, 0xFF000000 | PALETTE[i]);
				if (PALETTE[i] == symbolRgb) {
					ui.ring(swatchBoxes[i][0] - 1, y - 1, SWATCH + 2, SWATCH + 2, Radius.XS, 1, HudTheme.text());
				}
			}
			y += SWATCH + 5;
		}
		gridTop = y;
		int bottom = panel[1] + panel[3] - PAD - 22;
		gridRows = Math.max(1, (bottom - gridTop) / CELL);
		int rows = (shown.size() + COLUMNS - 1) / COLUMNS;
		scrollRows = Math.clamp(scrollRows, 0, Math.max(0, rows - gridRows));
		if (shown.isEmpty()) {
			String empty = ItemRepo.ready() || tab == Tab.SYMBOLS || tab == Tab.RECENT ? "Nothing found" : "The item list is still loading";
			ui.textAtScaled(empty, x, gridTop + 4, TextScale.SMALL, HudTheme.secondary());
		}
		for (int row = 0; row < gridRows; row++) {
			for (int column = 0; column < COLUMNS; column++) {
				int index = (scrollRows + row) * COLUMNS + column;
				if (index >= shown.size()) {
					break;
				}
				ButtonIcon icon = shown.get(index);
				int cellX = x + column * CELL + (CELL - TILE) / 2;
				int cellY = gridTop + row * CELL;
				boolean over = mouseX >= cellX && mouseX < cellX + TILE && mouseY >= cellY && mouseY < cellY + TILE;
				drawCell(ui, graphics, icon, cellX, cellY, over, icon.equals(selected));
			}
		}
		drawFooter(ui, x, bottom + 4, mouseX, mouseY);
	}

	private static void drawCell(Canvas ui, GuiGraphicsExtractor graphics, ButtonIcon icon, int x, int y, boolean over, boolean chosenNow) {
		int color = ButtonLook.colorOf(icon);
		if (icon.kind() == ButtonIcon.Kind.ITEM) {
			ItemStack stack = ButtonLook.stackOf(icon);
			PanelKit.tile(ui, graphics, stack, x, y, TILE, color == 0xFFFFFFFF ? 0 : color, over ? 1f : 0f, chosenNow ? 1f : 0f, null);
			return;
		}
		ui.rounded(x, y, TILE, TILE, Radius.SM, Draw.mix(HudTheme.slot(), HudTheme.text(), over ? 0.08f : 0.03f));
		ui.ring(x, y, TILE, TILE, Radius.SM, 1, chosenNow ? ui.accent() : Draw.withOpacity(HudTheme.text(), over ? 0.26f : 0.08f));
		ButtonLook.drawIcon(ui, graphics, icon, x, y, TILE, color);
	}

	private void drawFooter(Canvas ui, int x, int y, int mouseX, int mouseY) {
		if (selected == null) {
			return;
		}
		String name = nameOf(selected);
		ui.textAtScaledBold(ui.fitScaled(name, WIDTH - 2 * PAD - 44, TextScale.BODY), x, y + 3, TextScale.BODY, ButtonLook.colorOf(selected));
		set(useBox, panel[0] + panel[2] - PAD - 34, y, 34, 16);
		boolean over = PanelKit.inside(useBox, mouseX, mouseY);
		ui.rounded(useBox[0], y, useBox[2], useBox[3], Radius.SM, over ? Draw.mix(ui.accent(), 0xFFFFFFFF, 0.15f) : ui.accent());
		float width = ui.boldWidthScaled("Use", TextScale.BODY);
		ui.textAtScaledBold("Use", useBox[0] + (useBox[2] - width) / 2f, y + 4, TextScale.BODY, 0xFFFFFFFF);
	}

	/** @return an icon's name: the item's, or the symbol's */
	static String nameOf(ButtonIcon icon) {
		if (icon.kind() == ButtonIcon.Kind.SYMBOL) {
			return icon.value().substring(0, 1).toUpperCase(Locale.ROOT) + icon.value().substring(1);
		}
		ItemRepo.Entry entry = icon.value().contains(":") ? null : ItemRepo.get(icon.value());
		if (entry != null) {
			return entry.plainName();
		}
		ItemStack stack = ButtonLook.stackOf(icon);
		return stack.isEmpty() ? icon.value() : stack.getHoverName().getString();
	}

	/** @return whether the click was the picker's (while it is open, every click is) */
	boolean click(double mouseX, double mouseY) {
		if (!open) {
			return false;
		}
		search.focused = search.inside(mouseX, mouseY);
		if (PanelKit.inside(closeBox, mouseX, mouseY) || !(mouseX >= panel[0] && mouseX < panel[0] + panel[2] && mouseY >= panel[1]
			&& mouseY < panel[1] + panel[3])) {
			close();
			return true;
		}
		for (Tab each : Tab.values()) {
			if (PanelKit.inside(tabBoxes[each.ordinal()], mouseX, mouseY)) {
				tab = each;
				refilter();
				return true;
			}
		}
		if (tab == Tab.SYMBOLS) {
			for (int i = 0; i < PALETTE.length; i++) {
				if (PanelKit.inside(swatchBoxes[i], mouseX, mouseY)) {
					symbolRgb = PALETTE[i];
					if (selected != null && selected.kind() == ButtonIcon.Kind.SYMBOL) {
						selected = ButtonIcon.symbol(selected.value(), symbolRgb);
					}
					refilter();
					return true;
				}
			}
		}
		if (selected != null && PanelKit.inside(useBox, mouseX, mouseY)) {
			chosen.accept(selected);
			close();
			return true;
		}
		int column = (int) Math.floor((mouseX - panel[0] - PAD) / CELL);
		int row = (int) Math.floor((mouseY - gridTop) / CELL);
		if (column >= 0 && column < COLUMNS && row >= 0 && row < gridRows) {
			int index = (scrollRows + row) * COLUMNS + column;
			if (index < shown.size()) {
				ButtonIcon icon = shown.get(index);
				if (icon.equals(selected)) {
					// a second click on the same icon takes it
					chosen.accept(icon);
					close();
				} else {
					selected = icon;
				}
			}
		}
		return true;
	}

	boolean scroll(double amount) {
		if (!open) {
			return false;
		}
		scrollRows -= (int) Math.signum(amount);
		return true;
	}

	boolean key(KeyEvent event) {
		if (!open) {
			return false;
		}
		if (event.key() == InputConstants.KEY_ESCAPE) {
			close();
			return true;
		}
		if ((event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) && selected != null) {
			chosen.accept(selected);
			close();
			return true;
		}
		String before = search.value();
		search.focused = true;
		search.key(event);
		if (!before.equals(search.value())) {
			refilter();
		}
		return true;
	}

	boolean typed(CharacterEvent event) {
		if (!open) {
			return false;
		}
		search.focused = true;
		if (search.typed(event)) {
			refilter();
		}
		return true;
	}

	private static void set(int[] box, int x, int y, int width, int height) {
		box[0] = x;
		box[1] = y;
		box[2] = width;
		box[3] = height;
	}
}
