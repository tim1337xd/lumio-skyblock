package dev.tim1337.lumio.skyblock.dungeon.f7;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.util.ServerTicks;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.dungeon.DungeonTime;
import dev.tim1337.lumio.skyblock.dungeon.TabularText;

import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The boss timers of Floor 7: the countdowns of the fight's mechanics (Storm's pad and lightning, the terminals opening, Goldor's death tick,
 * Necron's drop) in seconds or server ticks, with an optional title when the lightning strikes or Necron drops. From the server's lines only
 * ({@link F7State}). It is what is left of the former "Boss info" panel, whose phase display went to the Splits panel and whose section went
 * to the Terminal status panel; it keeps that panel's saved name ({@code dungeon_f7}), so its switch, its place and its two settings stay as
 * the player had them.
 */
public class BossTimersFeature extends AddonPanel {
	/** How the timers are written. */
	public enum Unit {
		SECONDS("Seconds"), TICKS("Ticks");

		final String label;

		Unit(String label) {
			this.label = label;
		}
	}

	private static final int WIDTH = 170;
	private static final int PAD = PanelKit.PAD;
	private static final int HEADER = 16;
	private static final int ROW = 13;
	private static final int TIMERS = 5;
	private static final int SOON_TICKS = 20;
	private static final int TITLE_FADE_IN_TICKS = 2;
	private static final int TITLE_STAY_TICKS = 20;
	private static final int TITLE_FADE_OUT_TICKS = 6;
	private static final String HEADER_TEXT = "BOSS TIMERS";
	private static final String[] LABELS = {"Storm pad", "Lightning", "Terminals open", "Goldor death", "Necron drops"};
	private static final String[] SAMPLE_VALUES = {"—", "—", "—", "in 1.20 s", "—"};
	/** Saved once the former Boss info module was split up, so that happens once. */
	private static final String SPLIT_UP = "split_up";

	private final Choice<Unit> unit = add(make().choice("unit", Component.literal("Timers in"), Unit.SECONDS, value -> Component.literal(value.label),
		Choice.Style.SEGMENTED), "General");
	private final Toggle titles = add(make().toggle("titles", Component.literal("Titles"), false), "General", "When lightning strikes, Necron drops");

	/** The panel's texts, made once per server tick. */
	private long textTick = Long.MIN_VALUE;
	private Unit textUnit;
	private final String[] values = new String[TIMERS];
	private final int[] colors = new int[TIMERS];
	private long lastLightning = -1;
	private long lastNecron = -1;

	public BossTimersFeature() {
		super("dungeon_f7", "Boss timers", "F7 and M7 boss mechanic timers.", false, Anchor.LEFT, false, 8f, 8f);
		iconRgb(SystemColors.PURPLE_RGB);
		iconName("skull");
		summary("Storm, Goldor and Necron timers");
		badge("F7 · M7");
		offerNoBackground();
	}

	/** The titles when a timer runs out, checked every tick. */
	public void tick(Minecraft client) {
		F7Progress progress = F7State.progress();
		if (!isEnabled() || progress.phase() == F7Progress.Phase.NONE) {
			return;
		}
		long tick = ServerTicks.count();
		long lightning = F7Timers.lightning(progress, tick);
		long necron = F7Timers.necronDrop(progress, tick);
		if (titles.get() && lastLightning > 0 && lightning < 0) {
			title(client, "Lightning");
		}
		if (titles.get() && lastNecron > 0 && necron < 0) {
			title(client, "Necron drops");
		}
		lastLightning = lightning;
		lastNecron = necron;
	}

	private static void title(Minecraft client, String text) {
		dev.tim1337.lumio.skyblock.hud.TitleQueue.show(Component.literal(text).withColor(SystemColors.YELLOW_RGB), Component.empty(), TITLE_FADE_IN_TICKS,
			TITLE_STAY_TICKS, TITLE_FADE_OUT_TICKS, true);
	}

	private boolean shownNow() {
		return isEnabled() && F7State.inFight();
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
		return PAD + HEADER + TIMERS * ROW + PAD;
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		drawGlass(ui, WIDTH, panelHeight(), PanelKit.RADIUS);
		boolean sample = !shownNow();
		if (!sample) {
			refreshTexts(ServerTicks.count());
		}
		PanelKit.sectionLabel(ui, HEADER_TEXT, PAD, PAD);
		int y = PAD + HEADER;
		for (int i = 0; i < TIMERS; i++) {
			ui.textAtScaled(LABELS[i], PAD, y, TextScale.SMALL, HudTheme.secondary());
			String value = sample ? SAMPLE_VALUES[i] : values[i];
			int color = sample ? HudTheme.text() : colors[i] == 0 ? HudTheme.secondary() : colors[i];
			TabularText.drawRight(ui, value, WIDTH - PAD, y, TextScale.SMALL, true, color);
			y += ROW;
		}
	}

	/** The texts of the panel, made once per server tick (the frames between draw the same strings). */
	private void refreshTexts(long tick) {
		if (tick == textTick && unit.get() == textUnit) {
			return;
		}
		textTick = tick;
		textUnit = unit.get();
		F7Progress progress = F7State.progress();
		timer(0, F7Timers.stormPad(progress, tick), SystemColors.BLUE);
		timer(1, F7Timers.lightning(progress, tick), SystemColors.YELLOW);
		timer(2, F7Timers.terminalsOpen(progress, tick), SystemColors.GREEN);
		timer(3, F7Timers.goldorDeath(progress, tick), SystemColors.RED);
		timer(4, F7Timers.necronDrop(progress, tick), SystemColors.RED);
	}

	/** One timer's text and color: a dash (color 0, drawn quiet) while it does not run, red when it is about to run out. */
	private void timer(int row, long ticks, int color) {
		if (ticks < 0) {
			values[row] = "—";
			colors[row] = 0;
			return;
		}
		values[row] = unit.get() == Unit.TICKS ? ticks + " t" : "in " + DungeonTime.seconds(ticks);
		colors[row] = ticks <= SOON_TICKS ? SystemColors.RED : color;
	}

	/**
	 * A config from before the Boss info module was split up: who had it on gets the Splits panel (with its boss phases) and keeps the Terminal
	 * status as it was; who had it off gets no Terminal status panel either, as before, when it only showed inside Boss info.
	 */
	@Override
	public void load(JsonObject json) {
		super.load(json);
		if (!json.has(SPLIT_UP) && json.has("enabled")) {
			BossInfoSplit.old(isEnabled());
		}
	}

	@Override
	public JsonObject save() {
		JsonObject json = super.save();
		json.addProperty(SPLIT_UP, true);
		return json;
	}
}
