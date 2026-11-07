package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.ui.UiSounds;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;
import dev.tim1337.lumio.skyblock.data.StorageTracker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;

/**
 * The "Storages" panel directly right of the inventory: a shortcut per ender chest page and backpack, like SkySoft's. Each slot shows the page's own item from the game's storage menu, as kept per profile, with its number like a stack's count, and
 * the game's tooltip. A click opens that page, one click for one opening; the button in the head only folds the panel, and sends nothing.
 */
final class StoragePanel {
	private static final String TITLE = "Storages";
	private static final String HIDE = "Hide";
	private static final String SHOW = "Show";
	private static final String HIDE_TIP = "Keep only this bar beside the inventory";
	private static final String SHOW_TIP = "Show your ender chest pages and backpacks";
	private static final String CHEVRON = "chevron";
	private static final String ENDER_ICON = "chest";
	private static final String BACKPACK_ICON = "backpack";
	private static final int BUTTON_HEIGHT = 12;
	private static final int BUTTON_TOP = 3;
	private static final int BUTTON_PAD = 5;
	private static final int BUTTON_ICON = 7;
	private static final int BUTTON_ICON_GAP = 3;
	private static final int BUTTON_RIGHT = 4;
	private static final int TITLE_TOP = 6;
	private static final float SLOT_RADIUS_SHARE = 0.22f;
	/** The game draws an item 16 wide in a slot of 18. */
	private static final int ITEM_SIZE = 16;
	private static final int FULL_SLOT = 18;
	private static final float PLACEHOLDER_ICON_SHARE = 0.55f;
	private static final float PLACEHOLDER_OPACITY = 0.22f;
	private static final float PLACEHOLDER_NUMBER_OPACITY = 0.35f;
	private static final float EMPTY_OPACITY = 0.4f;
	private static final float HOVER_OPACITY = 0.14f;
	private static final float OPEN_FILL = 0.25f;
	private static final float OPEN_GLOW = 0.4f;
	/** How often the panel looks again at what is known about the pages. */
	private static final long REFRESH_NANOS = 250_000_000L;

	private final int[] box = new int[4];
	private final int[] toggleBox = new int[4];
	private final StoragePages.State[] states = new StoragePages.State[StoragePages.ALL];
	private final ItemStack[] icons = new ItemStack[StoragePages.ALL];
	private final String[] keys = new String[StoragePages.ALL];
	private final String[] numbers = new String[StoragePages.ALL];
	private StorageLayout.ShortcutGrid grid;
	private int gridRoom = -1;
	private long refreshedAt;
	private String refreshedFor;
	private boolean refreshed;

	StoragePanel() {
		for (int page = 0; page < StoragePages.ALL; page++) {
			keys[page] = StoragePages.key(page);
			int number = StoragePages.number(page);
			// like a stack's count: nothing on a one
			numbers[page] = number == 1 ? null : String.valueOf(number);
		}
	}

	/** Places the panel at the inventory's top, in the room beside it; where it does not fit even with its smallest slots, only the inventory stays. */
	void place(int x, int y, int roomWidth) {
		if (roomWidth != gridRoom) {
			gridRoom = roomWidth;
			grid = StorageLayout.shortcutGrid(roomWidth);
		}
		if (grid == null) {
			setBox(box, 0, 0, 0, 0);
			setBox(toggleBox, 0, 0, 0, 0);
		} else {
			setBox(box, x, y, grid.width(), SkyBlockFeatures.STORAGE.panelOpen() ? grid.height() : StorageLayout.SHORTCUT_FOLDED_HEIGHT);
		}
	}

	/** Looks again at the pages a few times a second and when another page opens: what is kept changes seldom. */
	private void refresh(String open, long now) {
		if (refreshed && Objects.equals(open, refreshedFor) && now - refreshedAt < REFRESH_NANOS) {
			return;
		}
		refreshed = true;
		refreshedAt = now;
		refreshedFor = open;
		var menu = StorageTracker.menuItems();
		for (int page = 0; page < StoragePages.ALL; page++) {
			String key = keys[page];
			ItemStack item = menu.get(key);
			StorageTracker.Page kept = StorageTracker.page(key);
			String name = item == null ? null : TextColors.noColors(item.getHoverName().getString());
			states[page] = StoragePages.state(key.equals(open), kept != null && kept.rows > 0, name);
			icons[page] = item;
		}
	}

	private int slotX(int page) {
		return box[0] + StorageLayout.SHORTCUT_PAD + StoragePages.column(page) * grid.slot();
	}

	private int slotY(int page) {
		return box[1] + StorageLayout.SHORTCUT_TOP + grid.rowY(StoragePages.row(page));
	}

	/** @return the page under the mouse, or -1 */
	private int pageUnder(double mouseX, double mouseY) {
		if (box[2] == 0 || !refreshed || !SkyBlockFeatures.STORAGE.panelOpen() || !PanelKit.inside(box, mouseX, mouseY)) {
			return -1;
		}
		for (int page = 0; page < StoragePages.ALL; page++) {
			int x = slotX(page);
			int y = slotY(page);
			if (mouseX >= x && mouseX < x + grid.slot() && mouseY >= y && mouseY < y + grid.slot()) {
				return page;
			}
		}
		return -1;
	}

	void draw(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client, String open, long now, int mouseX, int mouseY) {
		if (box[2] == 0) {
			return;
		}
		HudTheme.panel(ui, box[0], box[1], box[2], box[3], Radius.XL, HudTheme.opacity());
		boolean expanded = SkyBlockFeatures.STORAGE.panelOpen();
		int titleLeft = box[0] + StorageLayout.SHORTCUT_PAD;
		int buttonLeft = drawToggle(ui, graphics, client, expanded, mouseX, mouseY);
		if (ui.widthScaled(TITLE, TextScale.SMALL) <= buttonLeft - titleLeft) {
			ui.textAtScaled(TITLE, titleLeft, box[1] + TITLE_TOP, TextScale.SMALL, HudTheme.secondary());
		}
		if (!expanded) {
			return;
		}
		refresh(open, now);
		int slot = grid.slot();
		float radius = slot * SLOT_RADIUS_SHARE;
		int gridX = box[0] + StorageLayout.SHORTCUT_PAD;
		int gridY = box[1] + StorageLayout.SHORTCUT_TOP;
		ui.slotGrid(gridX, gridY, StoragePages.PER_ROW, 1, slot, slot, radius, HudTheme.slot());
		ui.slotGrid(gridX, gridY + grid.rowY(1), StoragePages.PER_ROW, 2, slot, slot, radius, HudTheme.slot());
		int under = pageUnder(mouseX, mouseY);
		graphics.nextStratum();
		for (int page = 0; page < StoragePages.ALL; page++) {
			drawSlot(ui, graphics, client, page, slotX(page), slotY(page), slot, radius, page == under);
		}
		if (under >= 0) {
			tooltip(graphics, client, under, mouseX, mouseY);
		}
	}

	private void drawSlot(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client, int page, int x, int y, int slot, float radius, boolean hover) {
		StoragePages.State state = states[page];
		if (state == StoragePages.State.LOCKED) {
			// locked or empty: dark, with nothing in it
			ui.roundedSmooth(x, y, slot, slot, radius, Draw.withOpacity(0xFF000000, EMPTY_OPACITY));
			return;
		}
		if (state == StoragePages.State.OPEN) {
			ui.softLight(x + slot / 2f, y + slot / 2f, slot, slot, Draw.withOpacity(ui.accent(), OPEN_GLOW));
			ui.roundedSmooth(x, y, slot, slot, radius, Draw.withOpacity(ui.accent(), OPEN_FILL));
		} else if (hover) {
			ui.roundedSmooth(x, y, slot, slot, radius, Draw.withOpacity(HudTheme.text(), HOVER_OPACITY));
		}
		ItemStack icon = icons[page];
		if (icon == null) {
			drawPlaceholder(ui, page, x, y, slot);
		} else {
			float scale = slot / (float) FULL_SLOT;
			var pose = graphics.pose();
			pose.pushMatrix();
			pose.translate(x + scale, y + scale);
			pose.scale(scale, scale);
			graphics.item(icon, 0, 0);
			if (numbers[page] != null) {
				graphics.itemDecorations(client.font, icon, 0, 0, numbers[page]);
			}
			pose.popMatrix();
		}
		if (state == StoragePages.State.OPEN) {
			graphics.nextStratum();
			ui.ring(x, y, slot, slot, radius, 1, ui.accent());
		}
	}

	/** A slot whose item the storage menu has not shown yet: the kind of the page, faint, with its number. */
	private void drawPlaceholder(Canvas ui, int page, int x, int y, int slot) {
		int size = Math.round(slot * PLACEHOLDER_ICON_SHARE);
		ui.icon(page < StoragePages.ENDER_PAGES ? ENDER_ICON : BACKPACK_ICON, x + (slot - size) / 2, y + (slot - size) / 2, size, Draw.withOpacity(HudTheme.text(), PLACEHOLDER_OPACITY));
		if (numbers[page] != null) {
			float scale = TextScale.SMALL * slot / FULL_SLOT;
			float width = ui.widthScaled(numbers[page], scale);
			ui.textAtScaled(numbers[page], x + slot - width - 1f, y + slot - ITEM_SIZE / 2f * scale - 1f, scale, Draw.withOpacity(HudTheme.text(), PLACEHOLDER_NUMBER_OPACITY));
		}
	}

	/** The page's item from the storage menu has the game's own tooltip; without it, its name and what it is now. */
	private void tooltip(GuiGraphicsExtractor graphics, Minecraft client, int page, int mouseX, int mouseY) {
		ItemStack icon = icons[page];
		if (icon != null) {
			graphics.setTooltipForNextFrame(client.font, icon, mouseX, mouseY);
			return;
		}
		String what = states[page] == StoragePages.State.OPEN ? "open now" : states[page] == StoragePages.State.UNSEEN ? "not opened yet" : "click to open";
		graphics.setTooltipForNextFrame(client.font, Component.literal(StoragePages.shortName(page) + " · " + what), mouseX, mouseY);
	}

	/** The button in the head, which folds the panel and opens it again; it sends nothing. @return where it starts */
	private int drawToggle(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client, boolean expanded, int mouseX, int mouseY) {
		String label = expanded ? HIDE : SHOW;
		int width = Math.round(ui.widthScaled(label, TextScale.SMALL)) + BUTTON_ICON + BUTTON_ICON_GAP + 2 * BUTTON_PAD;
		setBox(toggleBox, box[0] + box[2] - BUTTON_RIGHT - width, box[1] + BUTTON_TOP, width, BUTTON_HEIGHT);
		boolean over = PanelKit.inside(toggleBox, mouseX, mouseY);
		ui.roundedSmooth(toggleBox[0], toggleBox[1], width, BUTTON_HEIGHT, BUTTON_HEIGHT / 2f, Draw.withOpacity(HudTheme.text(), over ? 0.2f : 0.09f));
		int color = Draw.withOpacity(HudTheme.text(), over ? 1f : 0.85f);
		ui.textAtScaled(label, toggleBox[0] + BUTTON_PAD, toggleBox[1] + BUTTON_HEIGHT / 2f - 4.5f * TextScale.SMALL, TextScale.SMALL, color);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(toggleBox[0] + width - BUTTON_PAD - BUTTON_ICON / 2f, toggleBox[1] + BUTTON_HEIGHT / 2f);
		if (!expanded) {
			pose.rotate((float) Math.PI);
		}
		ui.icon(CHEVRON, -BUTTON_ICON / 2, -BUTTON_ICON / 2, BUTTON_ICON, color);
		pose.popMatrix();
		if (over) {
			graphics.setTooltipForNextFrame(client.font, Component.literal(expanded ? HIDE_TIP : SHOW_TIP), mouseX, mouseY);
		}
		return toggleBox[0];
	}

	/** @return whether the click was on the panel; it does not reach the game then. A click on a slot opens that page, one click for one opening. */
	boolean click(ContainerScreen screen, double mouseX, double mouseY) {
		if (box[2] == 0 || !PanelKit.inside(box, mouseX, mouseY)) {
			return false;
		}
		if (PanelKit.inside(toggleBox, mouseX, mouseY)) {
			UiSounds.click();
			SkyBlockFeatures.STORAGE.togglePanel();
			return true;
		}
		int page = pageUnder(mouseX, mouseY);
		if (page >= 0 && (states[page] == StoragePages.State.READY || states[page] == StoragePages.State.UNSEEN)) {
			StorageOverlay.openPage(screen, keys[page]);
		}
		return true;
	}

	private static void setBox(int[] target, int x, int y, int width, int height) {
		target[0] = x;
		target[1] = y;
		target[2] = width;
		target[3] = height;
	}
}
