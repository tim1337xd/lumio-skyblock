package dev.tim1337.lumio.skyblock.dungeon.f7;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.util.ServerTicks;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.skyblock.dungeon.DungeonTime;
import dev.tim1337.lumio.skyblock.dungeon.TabularText;

import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The terminal status of Goldor's phase (P3), a panel of its own: the section, how many of its terminals, devices and levers are done, the
 * devices done so far, whether the section's gate is down, and the section's time. From the server's lines only ({@link F7State}); shown only
 * in that phase. A saved "who" value of older configs (the list of who did what, removed) is not read and goes with the next save.
 */
public class TerminalStatusFeature extends AddonPanel {
	private static final int WIDTH = 170;
	private static final int PAD = PanelKit.PAD;
	private static final int HEADER = 16;
	private static final int COUNT = 20;
	private static final int ROW = 13;
	private static final String HEADER_TEXT = "TERMINALS";
	private static final String TIME_LABEL = "Section time";
	private static final int DEVICES = 4;

	/** The panel's texts, made once per server tick. */
	private long textTick = Long.MIN_VALUE;
	private String sectionText = "";
	private String countText = "";
	private String detailsText = "";
	private String timeText = "";

	public TerminalStatusFeature() {
		super("dungeon_terminal_status", "Terminal status", "Goldor's sections: terminals, devices, gate.", false,
			Anchor.LEFT, false, 8f, 96f);
		iconRgb(SystemColors.ORANGE_RGB);
		iconName("grid");
		summary("The section's terminals, devices and gate");
		badge("P3");
		offerNoBackground();
	}

	private boolean shownNow() {
		return isEnabled() && F7State.inFight() && F7State.progress().section() > 0;
	}

	@Override
	public boolean shown(Minecraft client) {
		return shownNow() || simulated();
	}

	@Override
	public boolean inEditorWhenHidden() {
		return true;
	}

	@Override
	public int panelWidth() {
		return WIDTH;
	}

	@Override
	public int panelHeight() {
		return PAD + HEADER + COUNT + ROW + PAD;
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		drawGlass(ui, WIDTH, panelHeight(), PanelKit.RADIUS);
		boolean sample = !shownNow();
		if (!sample) {
			refreshTexts(ServerTicks.count());
		}
		String section = sample ? "S2" : sectionText;
		String count = sample ? "5 / 7" : countText;
		String details = sample ? "devices 2/4 · gate ✓" : detailsText;
		String time = sample ? "21.40 s" : timeText;
		PanelKit.sectionLabel(ui, HEADER_TEXT, PAD, PAD);
		ui.textAtScaledBold(section, WIDTH - PAD - ui.boldWidthScaled(section, TextScale.BODY), PAD, TextScale.BODY, SystemColors.YELLOW);
		int y = PAD + HEADER;
		ui.textAtScaledBold(count, PAD, y, TextScale.HEADING, HudTheme.text());
		ui.textAtScaled(details, PAD + ui.boldWidthScaled(count, TextScale.HEADING) + 6, y + 3, TextScale.SMALL, HudTheme.secondary());
		y += COUNT;
		ui.textAtScaled(TIME_LABEL, PAD, y, TextScale.SMALL, HudTheme.secondary());
		TabularText.drawRight(ui, time, WIDTH - PAD, y, TextScale.SMALL, true, HudTheme.text());
	}

	private void refreshTexts(long tick) {
		if (tick == textTick) {
			return;
		}
		textTick = tick;
		F7Progress progress = F7State.progress();
		sectionText = "S" + progress.section();
		countText = progress.done() + " / " + (progress.total() == 0 ? "?" : progress.total());
		detailsText = "devices " + progress.devices() + "/" + DEVICES + " · gate " + (progress.gate() ? "✓" : "✗");
		timeText = DungeonTime.seconds(tick - progress.sectionStart());
	}

	@Override
	public void load(JsonObject json) {
		super.load(json);
		// a config from before the Boss info module was split up: without Boss info this never showed
		BossInfoSplit.apply();
	}
}
