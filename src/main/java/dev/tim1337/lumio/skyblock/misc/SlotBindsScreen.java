package dev.tim1337.lumio.skyblock.misc;

import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The bind sets: the list of sets (the one in use marked; a click uses it), a new one, renaming and deleting the one in use, the area it is used
 * in by itself, and a small picture of the inventory with its binds as lines, where a click on a bound slot removes that bind; "Clear all" asks
 * once more before it removes every bind of the set.
 */
final class SlotBindsScreen extends Screen {
	private static final int WIDTH = 360;
	private static final int HEIGHT = 228;
	private static final int PAD = 12;
	private static final int LIST_WIDTH = 112;
	private static final int ROW = 16;
	private static final int BUTTON_HEIGHT = 14;
	private static final int CHIP_HEIGHT = 14;
	private static final int CELL = 18;
	/** The gap between the inventory's rows and the hotbar in the picture, as in the game. */
	private static final int HOTBAR_GAP = 4;
	private static final int MAIN_ROWS = 3;
	private static final long CONFIRM_MILLIS = 3000;
	private static final int ROW_TEXT_DROP = 4;

	private final Screen back;
	private final SlotBindsFeature feature;
	private final SlotBinds binds;
	/** The name being typed while renaming, or null. */
	private String renaming;
	private long clearAskedAt;

	/** The places of what can be clicked, kept by the drawing for the click. */
	private int left;
	private int top;
	private int listBottom;
	private final float[] buttonX = new float[2];
	private final float[] buttonWidth = new float[2];
	private int buttonY;
	private final float[] chipX = new float[SlotBinds.Area.values().length];
	private final float[] chipWidth = new float[SlotBinds.Area.values().length];
	private final float[] chipY = new float[SlotBinds.Area.values().length];
	private int gridX;
	private int gridY;
	private float clearX;
	private float clearWidth;
	private int clearY;

	SlotBindsScreen(Screen back, SlotBindsFeature feature) {
		super(Component.literal("Bind sets"));
		this.back = back;
		this.feature = feature;
		this.binds = feature.binds();
	}

	@Override
	protected void init() {
		ScreenInput.wantText();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		Canvas ui = Draw.hudCanvas(graphics, 0xFF000000 | MainSettings.accentRgb(), 0f, true);
		left = (width - WIDTH) / 2;
		top = (height - HEIGHT) / 2;
		HudTheme.panel(ui, left, top, WIDTH, HEIGHT, Radius.SM, Math.max(0.9f, HudTheme.opacity()));
		ui.sectionLabel("BIND SETS", left + PAD, top + PAD, HudTheme.secondary());
		drawList(ui, mouseX, mouseY);
		drawSet(ui, graphics, mouseX, mouseY);
	}

	private void drawList(Canvas ui, int mouseX, int mouseY) {
		int x = left + PAD;
		int y = top + PAD + 16;
		for (int i = 0; i < binds.count(); i++) {
			boolean inUse = i == binds.active();
			boolean over = inside(mouseX, mouseY, x, y, LIST_WIDTH, ROW);
			if (inUse || over) {
				ui.roundedSmooth(x, y, LIST_WIDTH, ROW, Radius.XS, inUse ? Draw.withOpacity(ui.accent(), 0.25f) : Draw.withOpacity(0xFFFFFFFF, 0.06f));
			}
			String name = inUse && renaming != null ? renaming + (System.currentTimeMillis() / 500 % 2 == 0 ? "|" : "") : binds.name(i);
			ui.textAtScaled(name, x + 6, y + ROW_TEXT_DROP, TextScale.SMALL, HudTheme.text());
			y += ROW;
		}
		listBottom = y;
		if (binds.count() < SlotBinds.MOST_SETS) {
			boolean over = inside(mouseX, mouseY, x, y, LIST_WIDTH, ROW);
			ui.textAtScaled("+ New set", x + 6, y + ROW_TEXT_DROP, TextScale.SMALL, over ? ui.accent() : HudTheme.secondary());
		}
	}

	private void drawSet(Canvas ui, GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int set = binds.active();
		int x = left + PAD + LIST_WIDTH + PAD;
		int y = top + PAD + 16;
		ui.textAtScaledBold(binds.name(set), x, y + 2, TextScale.BODY, HudTheme.text());
		// rename and delete, right of the name
		buttonY = y;
		float bx = left + WIDTH - PAD;
		String[] labels = {renaming != null ? "Done" : "Rename", "Delete"};
		for (int i = labels.length - 1; i >= 0; i--) {
			float w = ui.widthScaled(labels[i], TextScale.SMALL) + 12;
			bx -= w;
			buttonX[i] = bx;
			buttonWidth[i] = w;
			boolean enabled = i != 1 || binds.count() > 1;
			drawButton(ui, labels[i], bx, y, w, enabled, i == 1, inside(mouseX, mouseY, bx, y, w, BUTTON_HEIGHT));
			bx -= 4;
		}
		y += BUTTON_HEIGHT + 8;
		ui.textAtScaled("Use automatically in", x, y, TextScale.SMALL, HudTheme.secondary());
		y += 11;
		float cx = x;
		SlotBinds.Area[] areas = SlotBinds.Area.values();
		for (int i = 0; i < areas.length; i++) {
			float w = ui.widthScaled(areas[i].label, TextScale.SMALL) + 10;
			if (cx + w > left + WIDTH - PAD) {
				cx = x;
				y += CHIP_HEIGHT + 4;
			}
			chipX[i] = cx;
			chipWidth[i] = w;
			chipY[i] = y;
			boolean on = binds.area(set) == areas[i];
			// one box shape for both states: only the fill and the border change
			ui.roundedSmooth(cx, y, w, CHIP_HEIGHT, Radius.box(CHIP_HEIGHT), on ? Draw.withOpacity(ui.accent(), 0.3f) : Draw.withOpacity(0xFFFFFFFF, 0.06f));
			ui.ring(Math.round(cx), y, Math.round(w), CHIP_HEIGHT, Radius.box(CHIP_HEIGHT), 1, on ? ui.accent() : Draw.withOpacity(0xFFFFFFFF, 0.15f));
			ui.textAtScaled(areas[i].label, cx + 5, y + 3, TextScale.SMALL, on ? HudTheme.text() : HudTheme.secondary());
			cx += w + 4;
		}
		y += CHIP_HEIGHT + 10;
		gridX = x;
		gridY = y;
		drawGrid(ui, graphics, set, mouseX, mouseY);
		int gridBottom = gridY + MAIN_ROWS * CELL + HOTBAR_GAP + CELL;
		ui.textAtScaled("Click a bound slot to remove its bind", x, gridBottom + 6, TextScale.SMALL, HudTheme.secondary());
		boolean asking = System.currentTimeMillis() - clearAskedAt < CONFIRM_MILLIS;
		String clear = asking ? "Click again to clear" : "Clear all";
		clearWidth = ui.widthScaled(clear, TextScale.SMALL) + 12;
		clearX = left + WIDTH - PAD - clearWidth;
		clearY = top + HEIGHT - PAD - BUTTON_HEIGHT;
		drawButton(ui, clear, clearX, clearY, clearWidth, binds.hasBinds(set), true, inside(mouseX, mouseY, clearX, clearY, clearWidth, BUTTON_HEIGHT));
		ui.textAtScaled("Esc  Close", left + PAD, clearY + 3, TextScale.SMALL, HudTheme.secondary());
	}

	/** The inventory as a small picture: three rows and the hotbar under them, bound slots marked and joined by lines. */
	private void drawGrid(Canvas ui, GuiGraphicsExtractor graphics, int set, int mouseX, int mouseY) {
		int accent = ui.accent();
		for (int slot = 0; slot < SlotBinds.SLOTS; slot++) {
			int sx = cellX(slot);
			int sy = cellY(slot);
			boolean bound = binds.partner(set, slot) >= 0;
			boolean over = bound && inside(mouseX, mouseY, sx, sy, CELL - 2, CELL - 2);
			ui.roundedSmooth(sx, sy, CELL - 2, CELL - 2, Radius.XXS, bound ? Draw.withOpacity(accent, over ? 0.45f : 0.25f) : Draw.withOpacity(0xFFFFFFFF, 0.06f));
			if (over) {
				ui.ring(sx, sy, CELL - 2, CELL - 2, Radius.XXS, 1, SystemColors.RED);
			}
		}
		for (int slot = 0; slot < SlotBinds.SLOTS; slot++) {
			int partner = binds.partner(set, slot);
			if (partner > slot) {
				float half = (CELL - 2) / 2f;
				SlotBindsFeature.line(graphics, cellX(slot) + half, cellY(slot) + half, cellX(partner) + half, cellY(partner) + half, 1.5f, accent);
			}
		}
	}

	private int cellX(int slot) {
		return gridX + slot % 9 * CELL;
	}

	private int cellY(int slot) {
		if (SlotBinds.hotbar(slot)) {
			return gridY + MAIN_ROWS * CELL + HOTBAR_GAP;
		}
		return gridY + (slot / 9 - 1) * CELL;
	}

	private static void drawButton(Canvas ui, String label, float x, float y, float w, boolean enabled, boolean danger, boolean over) {
		int color = danger ? SystemColors.RED : HudTheme.text();
		ui.roundedSmooth(x, y, w, BUTTON_HEIGHT, Radius.box(BUTTON_HEIGHT),
			Draw.withOpacity(danger ? SystemColors.RED : 0xFFFFFFFF, !enabled ? 0.03f : over ? 0.22f : 0.1f));
		ui.textAtScaled(label, x + 6, y + 3, TextScale.SMALL, enabled ? color : Draw.withOpacity(HudTheme.secondary(), 0.5f));
	}

	private static boolean inside(double mouseX, double mouseY, float x, float y, float w, float h) {
		return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (ScreenInput.button(event) != ScreenInput.LEFT) {
			return super.mouseClicked(event, doubleClick);
		}
		double mouseX = event.x();
		double mouseY = event.y();
		int set = binds.active();
		int x = left + PAD;
		int y = top + PAD + 16;
		for (int i = 0; i < binds.count(); i++) {
			if (inside(mouseX, mouseY, x, y + i * ROW, LIST_WIDTH, ROW)) {
				finishRename();
				binds.choose(i);
				feature.saveSoon();
				if (DebugHooks.ACTIVE) {
					DebugHooks.log(SkyBlockDebug.PANELS, "Slot binds: set \"{}\" chosen", binds.name(i));
				}
				return true;
			}
		}
		if (binds.count() < SlotBinds.MOST_SETS && inside(mouseX, mouseY, x, listBottom, LIST_WIDTH, ROW)) {
			finishRename();
			int added = binds.add("");
			if (added >= 0) {
				binds.choose(added);
				renaming = "";
				feature.saveSoon();
			}
			return true;
		}
		if (inside(mouseX, mouseY, buttonX[0], buttonY, buttonWidth[0], BUTTON_HEIGHT)) {
			if (renaming != null) {
				finishRename();
			} else {
				renaming = binds.name(set);
			}
			return true;
		}
		if (binds.count() > 1 && inside(mouseX, mouseY, buttonX[1], buttonY, buttonWidth[1], BUTTON_HEIGHT)) {
			renaming = null;
			binds.delete(set);
			feature.saveSoon();
			return true;
		}
		SlotBinds.Area[] areas = SlotBinds.Area.values();
		for (int i = 0; i < areas.length; i++) {
			if (chipWidth[i] > 0 && mouseX >= chipX[i] && mouseX < chipX[i] + chipWidth[i] && mouseY >= chipY[i] && mouseY < chipY[i] + CHIP_HEIGHT) {
				binds.setArea(set, areas[i]);
				feature.saveSoon();
				return true;
			}
		}
		for (int slot = 0; slot < SlotBinds.SLOTS; slot++) {
			if (inside(mouseX, mouseY, cellX(slot), cellY(slot), CELL - 2, CELL - 2)) {
				if (binds.unbind(set, slot)) {
					if (DebugHooks.ACTIVE) {
						DebugHooks.log(SkyBlockDebug.PANELS, "Slot binds: unbound {} in \"{}\" from the sheet", slot, binds.name(set));
					}
					feature.saveSoon();
				}
				return true;
			}
		}
		if (binds.hasBinds(set) && inside(mouseX, mouseY, clearX, clearY, clearWidth, BUTTON_HEIGHT)) {
			long now = System.currentTimeMillis();
			if (now - clearAskedAt < CONFIRM_MILLIS) {
				binds.clearBinds(set);
				clearAskedAt = 0;
				if (DebugHooks.ACTIVE) {
					DebugHooks.log(SkyBlockDebug.PANELS, "Slot binds: cleared \"{}\"", binds.name(set));
				}
				feature.saveSoon();
			} else {
				clearAskedAt = now;
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	private void finishRename() {
		if (renaming != null) {
			binds.rename(binds.active(), renaming);
			renaming = null;
			feature.saveSoon();
		}
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (renaming != null && event.isAllowedChatCharacter() && renaming.length() < SlotBinds.MAX_NAME) {
			renaming += new String(Character.toChars(event.codepoint()));
			return true;
		}
		return super.charTyped(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (renaming != null) {
			int key = event.key();
			if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
				finishRename();
				return true;
			}
			if (key == InputConstants.KEY_ESCAPE) {
				renaming = null;
				return true;
			}
			if (key == InputConstants.KEY_BACKSPACE) {
				if (!renaming.isEmpty()) {
					renaming = renaming.substring(0, renaming.length() - 1);
				}
				return true;
			}
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		finishRename();
		Minecraft.getInstance().gui.setScreen(back);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
