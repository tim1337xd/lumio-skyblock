package dev.tim1337.lumio.skyblock.dungeon;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.hud.HudEditor;
import dev.tim1337.lumio.api.experimental.util.ServerTicks;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.setting.Action;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;
import dev.tim1337.lumio.skyblock.dungeon.f7.BossInfoSplit;
import dev.tim1337.lumio.skyblock.dungeon.f7.F7Progress;
import dev.tim1337.lumio.skyblock.dungeon.f7.F7State;
import dev.tim1337.lumio.skyblock.chat.AfterBlock;

import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The splits of a dungeon run, like NoammAddons' and Odin's: every phase with its real time and its server ticks, the current one marked and the
 * total at the bottom, compared with the best (as it was before the run) or the last run of the floor: green with a minus for faster, red with a plus for slower, a grey dash without a best. A HUD panel, placed in the HUD editor.
 */
public class DungeonSplitsFeature extends AddonPanel {
	/** What each row shows. */
	public enum Show {
		TIME("Time"), TIME_TICKS("+ Tick time");

		final String label;

		Show(String label) {
			this.label = label;
		}
	}

	/** Which time of a split is compared: the real time, or the tick time (server ticks, which a lagging server does not stretch). */
	public enum CompareWith {
		REAL("Real time"), TICKS("Tick time");

		final String label;

		CompareWith(String label) {
			this.label = label;
		}
	}

	/** What the phases are compared with. */
	public enum Compare {
		OFF("Off"), BEST("Best"), LAST("Last");

		final String label;

		Compare(String label) {
			this.label = label;
		}
	}

	private static final int ROW = 13;
	private static final int WIDTH = 210;
	private static final int PAD = PanelKit.PAD;
	private static final int HEADER_ABOVE = 6;
	/** The small row of column names over the values. */
	private static final int COLUMNS_ROW = 10;
	private static final String TIME_COLUMN = "Time";
	private static final String TICKS_COLUMN = "Ticks";
	private static final String VERSUS_BEST = "vs. best";
	private static final String VERSUS_LAST = "vs. last";
	/** The widest time and difference a row shows, for the widths of their columns. */
	private static final String CLOCK_TEMPLATE = "00:00.00";
	private static final String DELTA_TEMPLATE = "+00.0";
	private static final float COLUMN_GAP = 8f;
	private static final long MILLIS_PER_TICK = SplitBests.MILLIS_PER_TICK;
	/** How light the "now" mark of the current split is, and how the debug log groups a split's lines (once per this many ticks). */
	private static final float NOW_OPACITY = 0.85f;
	private static final long LOG_BUCKET_TICKS = 200L;
	private static final int MOST_LOGGED = 64;
	private final java.util.Set<String> loggedTicks = new java.util.HashSet<>();
	/** How long the splits stay after the run, in milliseconds. */
	private static final long AFTER_RUN_MILLIS = 60_000;
	/** The floor number of F7 and M7, whose "Terminals" split has sections; and the moment the example run is shown at. */
	private static final int F7 = 7;
	private static final long SAMPLE_NOW_MILLIS = 497_000L;

	private final Choice<Show> show = add(make().choice("show", Component.literal("Show"), Show.TIME_TICKS, value -> Component.literal(value.label)), "General");
	private final Choice<Compare> compare = add(make().choice("compare", Component.literal("Compare"), Compare.BEST,
		value -> Component.literal(value.label)), "General", "Against your best or last run");
	private final Choice<CompareWith> compareWith = add(make().choice("compare_with", Component.literal("Compare with"), CompareWith.REAL,
		value -> Component.literal(value.label), Choice.Style.SEGMENTED), "General", "The same for every row and the total");
	private final Action resetBests = add(make().action("reset_bests", Component.literal("Bests of this floor"), Component.literal("Reset"), true,
		this::resetBests), "General", "This floor, or the last one run");
	private final Toggle summary = add(make().toggle("summary", Component.literal("Splits in chat"), false), "General");
	private final Toggle bossPhases = add(make().toggle("boss_phases", Component.literal("Boss phases"), true), "Boss",
		"After the clear. F7/M7: in testing");
	private final Toggle terminalSections = add(make().toggle("terminal_sections", Component.literal("Terminals as sections"), false), "Boss",
		"F7/M7: rows S1 to S4. Still being tested");

	/** The bests and the last run of each floor, and what the run going on is compared with: the bests as stored before it started. */
	private final SplitBests bests = new SplitBests();
	/** The same for the four sections of Goldor's phase, stored beside the splits whether they show or not. */
	private final SplitBests sectionBests = new SplitBests("section_");
	private final SplitRows.Sections sections = new SplitRows.Sections();
	private static final SplitRows.Sections SAMPLE_SECTIONS = sampleSections();
	/** Where the best of each row shown is stored (see {@link SplitRows.Shown}). */
	private int[] slots = new int[0];
	/** The floor of the run going on or of the last one, for "Reset bests". */
	private DungeonFloor lastFloor;
	private DungeonSplits splits;
	private DungeonFloor floor;
	private long overAt;
	private List<DungeonSplits.Row> rows = List.of();
	private static final DungeonSplits SAMPLE = sample();

	public DungeonSplitsFeature() {
		super("dungeon_splits", "Splits", "Every phase of a run, timed and compared.", false, Anchor.RIGHT,
			false, 8f, 8f);
		iconRgb(SystemColors.BLUE_RGB);
		iconName("clock");
		summary("Every phase, time and ticks");
		compareWith.visibleWhen(() -> compare.get() != Compare.OFF);
		terminalSections.visibleWhen(bossPhases::get);
		offerHeader();
		offerNoBackground();
		Dungeon.listen(new Dungeon.Listener() {
			@Override
			public void runStarted(DungeonFloor started) {
				floor = started;
				lastFloor = started;
				// before anything of this run is stored: what it is compared with
				bests.runStarted(started);
				sectionBests.runStarted(started);
				sections.reset();
				splits = new DungeonSplits(started, Dungeon.startedAt(), Dungeon.startedTick(), Dungeon.startedGameTime());
				overAt = 0;
			}

			@Override
			public void serverLine(String line) {
				if (splits != null && !splits.over()) {
					int finished = splits.line(line, System.currentTimeMillis(), DungeonTicks.count(), ServerTicks.serverGameTime());
					if (finished > 0 && DebugHooks.ACTIVE) {
						logChecks(finished);
					}
					if (splits.over()) {
						finished();
					}
				}
			}

			@Override
			public void left() {
				splits = null;
				floor = null;
				bests.runLeft();
				sectionBests.runLeft();
				sections.reset();
				overAt = 0;
				rows = List.of();
			}
		});
	}

	private static DungeonSplits sample() {
		DungeonSplits sample = new DungeonSplits(DungeonFloor.F7, 0, 0);
		String[] lines = {"The BLOOD DOOR has been opened!", "[BOSS] The Watcher: You have proven yourself. You may pass.",
			"[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!", "[BOSS] Storm: Pathetic Maxor, just like expected.",
			"[BOSS] Goldor: Who dares trespass into my domain?", "The Core entrance is opening!",
			"[BOSS] Necron: You went further than any human before, congratulations."};
		long[] at = {31_400, 103_450, 282_050, 323_250, 385_800, 467_600, 485_950};
		for (int i = 0; i < lines.length; i++) {
			sample.line(lines[i], at[i], at[i] / 50);
		}
		return sample;
	}

	/** The run is over: its phases become the last run, and the best where faster. The panel goes on comparing with the bests from before the run. */
	private void finished() {
		overAt = System.currentTimeMillis();
		List<DungeonSplits.Row> done = splits.rows(overAt, DungeonTicks.count());
		long[] times = new long[done.size()];
		long[] tickTimes = new long[done.size()];
		for (int i = 0; i < done.size(); i++) {
			times[i] = done.get(i).millis();
			tickTimes[i] = done.get(i).ticks();
		}
		if (floor != null) {
			bests.runFinished(floor, times, tickTimes);
			if (sections.over()) {
				sectionBests.runFinished(floor, sections.spent(false), sections.spent(true));
			}
			LumioApi.saveSoon();
		}
		if (summary.get()) {
			StringBuilder text = new StringBuilder("Splits");
			for (DungeonSplits.Row row : done) {
				text.append(" · ").append(row.name()).append(' ').append(DungeonSplits.time(row.millis()));
			}
			AfterBlock.info(Component.literal(text.toString()));
		}
	}

	/** Forgets the bests of the floor of the run going on, or of the last one. */
	private void resetBests() {
		DungeonFloor which = floor != null ? floor : lastFloor;
		if (which != null) {
			bests.reset(which);
			sectionBests.reset(which);
			LumioApi.saveSoon();
		}
	}

	private boolean live() {
		return splits != null && Dungeon.in() && (overAt == 0 || System.currentTimeMillis() - overAt < AFTER_RUN_MILLIS);
	}

	/** What the panel's rows come from: the run, the example run, or nothing. */
	enum Source {
		RUN, SAMPLE, NONE
	}

	/**
	 * @param live       whether a run of the Catacombs is going on (or just ended) here
	 * @param simulated  whether the HUD editor asks for its example (Simulate, All panels)
	 * @param editorOpen whether the HUD editor is open: picked from its list, the panel is drawn with the example to place it
	 */
	static Source source(boolean live, boolean simulated, boolean editorOpen) {
		if (live) {
			return Source.RUN;
		}
		return simulated || editorOpen ? Source.SAMPLE : Source.NONE;
	}

	/** @return whether the panel shows: only during a run in the Catacombs, or in the HUD editor when its example is asked for; never elsewhere */
	static boolean shownFor(boolean live, boolean simulated) {
		return live || simulated;
	}

	@Override
	public void prepare(Minecraft client) {
		Source source = source(live(), simulated(), HudEditor.isOpen());
		long nowMillis = source == Source.SAMPLE ? SAMPLE_NOW_MILLIS : System.currentTimeMillis();
		long nowTick = source == Source.SAMPLE ? SAMPLE_NOW_MILLIS / MILLIS_PER_TICK : DungeonTicks.count();
		if (source == Source.RUN && floor != null && floor.number() == F7) {
			// the sections of Goldor's phase, noted as the fight goes on, whether they show or not: their bests are kept either way
			F7Progress fight = F7State.progress();
			sections.see(fight.section(), fight.phase().ordinal() > F7Progress.Phase.GOLDOR.ordinal(), nowMillis, nowTick);
		}
		List<DungeonSplits.Row> all = switch (source) {
			case RUN -> splits.rows(nowMillis, nowTick);
			// the example run is only drawn when asked for: the editor's All panels or Simulate, or picked from its list
			case SAMPLE -> SAMPLE.rows(nowMillis, nowTick);
			case NONE -> List.of();
		};
		if (all.isEmpty()) {
			rows = all;
			slots = new int[0];
			return;
		}
		boolean seven = source == Source.SAMPLE || floor != null && floor.number() == F7;
		SplitRows.Shown shown = SplitRows.shown(all, bossPhases.get(), terminalSections.get() && seven ? source == Source.SAMPLE ? SAMPLE_SECTIONS : sections : null,
			nowMillis, nowTick);
		rows = shown.rows();
		slots = shown.slots();
	}

	/** The example run's sections of Goldor's phase, inside its "Terminals" split. */
	private static SplitRows.Sections sampleSections() {
		SplitRows.Sections sample = new SplitRows.Sections();
		long[] at = {323_250, 338_050, 352_500, 369_100, 385_800};
		for (int i = 0; i < at.length; i++) {
			sample.see(Math.min(i + 1, SplitRows.SECTIONS), i == SplitRows.SECTIONS, at[i], at[i] / MILLIS_PER_TICK);
		}
		return sample;
	}

	/** @return what a row shown is compared with, in milliseconds; 0 for none */
	private long against(int row, boolean last, boolean tickTime) {
		int slot = row < slots.length ? slots[row] : row;
		return slot < 0 ? sectionBests.against(-slot - 1, last, tickTime) : bests.against(slot, last, tickTime);
	}

	@Override
	public boolean shown(Minecraft client) {
		// the example rows alone never show the panel: outside the Catacombs it is gone, also in the editor unless asked for
		return shownFor(live(), simulated()) && !rows.isEmpty();
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
		return contentTop() + COLUMNS_ROW + Math.max(1, rows.size()) * ROW + 10 + PAD;
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		drawGlass(ui, WIDTH, panelHeight(), PanelKit.RADIUS);
		DungeonFloor shownFloor = live() ? floor : DungeonFloor.F7;
		familyHeader(ui, WIDTH, "clock", PanelKit.TILE_COMMISSIONS, "Splits" + (shownFloor == null ? "" : " · " + shownFloor.name()), "", 0, false);
		int text = HudTheme.text();
		int secondary = HudTheme.secondary();
		int dim = Draw.withOpacity(secondary, 0.7f);
		boolean ticks = show.get() == Show.TIME_TICKS;
		// fixed columns, each right-aligned and as wide as its longest value, digits in cells of one width: nothing moves sideways
		// the last column is as wide as its name, which is wider than its values
		float deltaWidth = Math.max(TabularText.width(ui, DELTA_TEMPLATE, TextScale.SMALL, true), ui.widthScaled(VERSUS_BEST, TextScale.SMALL));
		float clockWidth = TabularText.width(ui, CLOCK_TEMPLATE, TextScale.SMALL, true);
		float deltaRight = WIDTH - PAD;
		float tickRight = deltaRight - deltaWidth - COLUMN_GAP;
		float timeRight = ticks ? tickRight - clockWidth - COLUMN_GAP : tickRight;
		boolean comparing = compare.get() != Compare.OFF;
		boolean againstLast = compare.get() == Compare.LAST;
		boolean tickCompared = compareWith.get() == CompareWith.TICKS;
		boolean sample = !live();
		int y = contentTop() - HEADER_ABOVE + 4;
		// the columns' names, right-aligned over their values
		drawColumnName(ui, TIME_COLUMN, timeRight, y, dim);
		if (ticks) {
			drawColumnName(ui, TICKS_COLUMN, tickRight, y, dim);
		}
		if (comparing) {
			drawColumnName(ui, againstLast ? VERSUS_LAST : VERSUS_BEST, deltaRight, y, dim);
		}
		y += COLUMNS_ROW;
		for (int i = 0; i < rows.size(); i++) {
			DungeonSplits.Row row = rows.get(i);
			boolean total = i == rows.size() - 1;
			if (total) {
				PanelKit.divider(ui, WIDTH, y + 2);
				y += 8;
			}
			if (row.current()) {
				ui.roundedSmooth(PAD - 3, y - 2, WIDTH - 2 * PAD + 6, ROW, Radius.SM, Draw.withOpacity(accentArgb(), 0.18f));
				ui.fill(PAD - 3, y - 1, 2, ROW - 2, accentArgb());
			}
			boolean started = row.done() || row.current() || total;
			int nameColor = started ? text : Draw.withOpacity(secondary, 0.6f);
			ui.textAtScaled(row.name(), PAD + 2, y + 1, total ? TextScale.BODY : TextScale.SMALL, nameColor);
			String time = row.millis() > 0 || row.current() ? DungeonSplits.time(row.millis()) : "—";
			TabularText.drawRight(ui, time, timeRight, y + 1, TextScale.SMALL, true, row.millis() > 0 ? text : secondary);
			if (ticks && started && row.ticks() > 0) {
				// the server's ticks as time: ticks ÷ 20, in the same form as the real time, dimmed; with Debug on, a tick time above the real time is logged
				if (!DungeonSplits.ticksPlausible(row.ticks(), row.millis(), MILLIS_PER_TICK)) {
					tooManyTicks(row);
				}
				String tickTime = DungeonSplits.time(row.ticks() * MILLIS_PER_TICK);
				TabularText.drawRight(ui, tickTime, tickRight, y + 1, TextScale.SMALL, false, dim);
			}
			if (comparing) {
				// against the best (or the last run) as stored before this run started; the same time, real or ticks, for every row
				long value = tickCompared ? row.ticks() * MILLIS_PER_TICK : row.millis();
				long against = sample ? sampleBest(i, value) : against(i, againstLast, tickCompared);
				SplitBests.Cell cell = SplitBests.cell(value, against, started, row.done());
				int color = switch (cell.kind()) {
					case FASTER -> SystemColors.GREEN;
					case SLOWER -> SystemColors.RED;
					default -> secondary;
				};
				if (!cell.text().isEmpty()) {
					TabularText.drawRight(ui, cell.text(), deltaRight, y + 1, TextScale.SMALL, true, color);
				} else if (row.current() && !total) {
					// light on the row's accent tint, so it reads on dark backgrounds
					ui.textAtScaledBold("now", deltaRight - ui.boldWidthScaled("now", TextScale.SMALL), y + 1, TextScale.SMALL, Draw.withOpacity(0xFFFFFFFF, NOW_OPACITY));
				}
			} else if (row.current() && !total) {
				ui.textAtScaledBold("now", deltaRight - ui.boldWidthScaled("now", TextScale.SMALL), y + 1, TextScale.SMALL, Draw.withOpacity(0xFFFFFFFF, NOW_OPACITY));
			}
			y += ROW;
		}
	}

	private static void drawColumnName(Canvas ui, String name, float right, int y, int color) {
		ui.textAtScaled(name, right - ui.widthScaled(name, TextScale.SMALL), y, TextScale.SMALL, color);
	}

	/**
	 * The example run's bests in the HUD editor, made from its own times so every case shows: a split faster than its best, slower ones, and one
	 * without a best yet.
	 */
	static long sampleBest(int split, long value) {
		return switch (split % 4) {
			case 0 -> value + 1_300;
			case 1 -> Math.max(1, value - 2_400);
			case 2 -> 0;
			default -> Math.max(1, value - 700);
		};
	}

	/** With Debug on: each split just finished, with its tick count, its real time and the server's game time over it as a cross-check. */
	private void logChecks(int finished) {
		for (int phase = splits.finished() - finished; phase < splits.finished(); phase++) {
			DungeonSplits.Check check = splits.check(phase);
			if (DebugHooks.ACTIVE) {
				DebugHooks.log(SkyBlockDebug.DUNGEON, "Splits: {} took {} ticks by ping ({} ms), {} ms real, {} ticks by game time", check.name(), check.ticks(),
					check.ticks() * MILLIS_PER_TICK, check.millis(), check.gameTicks() < 0 ? "unknown" : check.gameTicks());
			}
		}
	}

	/** With Debug on: a split whose tick time came out above its real time, with its raw counts, once per split and run. */
	private void tooManyTicks(DungeonSplits.Row row) {
		if (loggedTicks.size() > MOST_LOGGED) {
			loggedTicks.clear();
		}
		if (DebugHooks.ACTIVE && loggedTicks.add(row.name() + row.millis() / MILLIS_PER_TICK / LOG_BUCKET_TICKS)) {
			DebugHooks.log(SkyBlockDebug.DUNGEON, "Splits: {} has {} ticks ({} ms) in {} ms of real time", row.name(), row.ticks(),
				row.ticks() * MILLIS_PER_TICK, row.millis());
		}
	}

	@Override
	public JsonObject save() {
		JsonObject json = super.save();
		bests.save(json);
		sectionBests.save(json);
		return json;
	}

	@Override
	public void load(JsonObject json) {
		super.load(json);
		bests.load(json);
		sectionBests.load(json);
		// a config from before the Boss info module was split up: who had it on has the phases here now
		BossInfoSplit.apply();
	}

}
