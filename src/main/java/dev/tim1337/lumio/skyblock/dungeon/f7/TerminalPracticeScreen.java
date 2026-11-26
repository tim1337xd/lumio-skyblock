package dev.tim1337.lumio.skyblock.dungeon.f7;

import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Terminal practice: the five terminals made up on the player's screen ({@link TerminalSim}), with or without the solver's hints, timed. Nothing
 * is sent anywhere; it works in any world and in the menus.
 */
public final class TerminalPracticeScreen extends Screen {
	private static final int ROW = 9;
	private static final int CELL = 22;
	private static final int CELL_GAP = 3;
	private static final int PAD = 12;
	private static final int HEADER = 18;
	private static final int FOOTER = 20;
	private static final int DIM = 0xB0000000;
	private static final float RADIUS = 12f;
	private static final int NEXT = SystemColors.GREEN;
	private static final int THEN = 0xFF1F8A3A;

	private final Screen back;
	private final Random random = new Random();
	private final Map<String, ItemStack> stacks = new HashMap<>();
	private TerminalSim sim;
	private int kindIndex;
	private boolean hints = true;
	private long startedMillis;
	private long lastMillis = -1;

	public TerminalPracticeScreen(Screen back) {
		super(Component.literal("Terminal practice"));
		this.back = back;
		start();
	}

	private void start() {
		sim = TerminalSim.create(TerminalSim.KINDS.get(kindIndex), random);
		startedMillis = System.currentTimeMillis();
	}

	private int panelWidth() {
		return ROW * CELL + (ROW - 1) * CELL_GAP + 2 * PAD;
	}

	private int panelHeight() {
		return PAD + HEADER + sim.rows() * CELL + (sim.rows() - 1) * CELL_GAP + FOOTER + PAD;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partial) {
		graphics.fill(0, 0, width, height, DIM);
		Canvas ui = Draw.hudCanvas(graphics, 0xFF000000 | MainSettings.accentRgb(), 0f, true);
		int panelWidth = panelWidth();
		int panelHeight = panelHeight();
		int left = (width - panelWidth) / 2;
		int top = (height - panelHeight) / 2;
		HudTheme.panel(ui, left, top, panelWidth, panelHeight, RADIUS, Math.max(0.85f, HudTheme.opacity()));
		ui.sectionLabel(sim.title().toUpperCase(Locale.ROOT), left + PAD, top + PAD, HudTheme.secondary());
		String time = seconds(System.currentTimeMillis() - startedMillis) + (lastMillis >= 0 ? " · last " + seconds(lastMillis) : "");
		ui.textAtScaled(time, left + panelWidth - PAD - ui.widthScaled(time, TextScale.SMALL), top + PAD, TextScale.SMALL, HudTheme.secondary());
		Terminals.Solution solution = sim.solution();
		int gridTop = top + PAD + HEADER;
		for (Terminals.Cell cell : sim.cells()) {
			int x = left + PAD + cell.slot() % ROW * (CELL + CELL_GAP);
			int y = gridTop + cell.slot() / ROW * (CELL + CELL_GAP);
			int place = hints ? solution.place(cell.slot()) : -1;
			ui.roundedSmooth(x, y, CELL, CELL, 5f, cellColor(cell, place));
			drawContent(graphics, ui, cell, x, y, solution);
		}
		int footerY = top + panelHeight - PAD - 9;
		String keys = "Tab: next terminal · H: hints " + (hints ? "on" : "off") + " · R: new";
		ui.textAtScaled(keys, left + PAD, footerY, TextScale.SMALL, HudTheme.secondary());
	}

	private int cellColor(Terminals.Cell cell, int place) {
		if (cell.item().equals("black_stained_glass_pane")) {
			return Draw.withOpacity(HudTheme.slot(), 0.35f);
		}
		if (place == 0 && sim.kind() == Terminals.Kind.NUMBERS) {
			return NEXT;
		}
		if (place >= 0) {
			return sim.kind() == Terminals.Kind.NUMBERS && place > 1 ? Draw.withOpacity(HudTheme.slot(), 0.9f) : THEN;
		}
		return Draw.withOpacity(HudTheme.slot(), 0.8f);
	}

	/** The pane's color for the pane terminals, the number for the order terminal, the item for the others. */
	private void drawContent(GuiGraphicsExtractor graphics, Canvas ui, Terminals.Cell cell, int x, int y, Terminals.Solution solution) {
		switch (sim.kind()) {
			case PANES, RUBIX -> {
				if (cell.item().equals("black_stained_glass_pane")) {
					return;
				}
				int inset = 6;
				ui.roundedSmooth(x + inset, y + inset, CELL - 2 * inset, CELL - 2 * inset, 3f, 0xFF000000 | paneRgb(cell.item()));
				if (sim.kind() == Terminals.Kind.RUBIX && hints && solution.clicksAt(cell.slot()) != 0) {
					ui.textCentered(String.valueOf(Math.abs(solution.clicksAt(cell.slot()))), x + CELL / 2, y + CELL / 2, 0xFFFFFFFF);
				}
			}
			case NUMBERS -> {
				if (cell.item().startsWith("red")) {
					ui.textCentered(String.valueOf(cell.count()), x + CELL / 2, y + CELL / 2, 0xFFFFFFFF);
				}
			}
			default -> {
				if (!cell.item().equals("black_stained_glass_pane")) {
					graphics.item(stackOf(cell.item()), x + (CELL - 16) / 2, y + (CELL - 16) / 2);
					if (cell.shines()) {
						ui.ring(x, y, CELL, CELL, 5f, 1, SystemColors.GREEN);
					}
				}
			}
		}
	}

	private ItemStack stackOf(String id) {
		return stacks.computeIfAbsent(id, key -> new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(key))));
	}

	private static int paneRgb(String item) {
		return switch (item.substring(0, item.indexOf("_stained"))) {
			case "red" -> SystemColors.RED_RGB;
			case "lime", "green" -> SystemColors.GREEN_RGB;
			case "orange" -> SystemColors.ORANGE_RGB;
			case "yellow" -> SystemColors.YELLOW_RGB;
			case "blue" -> SystemColors.BLUE_RGB;
			default -> SystemColors.GRAY_RGB;
		};
	}

	private static String seconds(long millis) {
		long hundredths = millis / 10;
		long rest = hundredths % 100;
		return hundredths / 100 + (rest < 10 ? ".0" : ".") + rest + " s";
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		int left = (width - panelWidth()) / 2 + PAD;
		int top = (height - panelHeight()) / 2 + PAD + HEADER;
		int column = (int) Math.floor((event.x() - left) / (CELL + CELL_GAP));
		int row = (int) Math.floor((event.y() - top) / (CELL + CELL_GAP));
		if (column >= 0 && column < ROW && row >= 0 && row < sim.rows()) {
			sim.click(row * ROW + column, ScreenInput.button(event) == ScreenInput.RIGHT);
			if (sim.solved()) {
				lastMillis = System.currentTimeMillis() - startedMillis;
				start();
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
		if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_TAB) {
			kindIndex = (kindIndex + 1) % TerminalSim.KINDS.size();
			lastMillis = -1;
			start();
			return true;
		}
		if (ScreenInput.letter(event.key(), 'h')) {
			hints = !hints;
			return true;
		}
		if (ScreenInput.letter(event.key(), 'r')) {
			start();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().gui.setScreen(back);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
