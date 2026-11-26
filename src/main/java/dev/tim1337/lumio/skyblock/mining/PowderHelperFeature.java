package dev.tim1337.lumio.skyblock.mining;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.util.ServerTicks;
import dev.tim1337.lumio.api.experimental.util.ShortNumber;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.setting.Action;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.ColorChoice;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;
import dev.tim1337.lumio.skyblock.data.ProfitData;
import dev.tim1337.lumio.skyblock.data.ProfitStore;
import dev.tim1337.lumio.skyblock.data.ProfitTracker;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;
import dev.tim1337.lumio.skyblock.dungeon.DungeonAlert;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.text.NumberFormat;
import java.util.Locale;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

/**
 * Powder Helper: one of the player's powders (Mithril, Gemstone or Glacite; by itself the one gained last) from the tab list as a small HUD
 * element with what was made of it (gained, per hour, the time farmed, the chests; for the session, today or all time), and in the Crystal Hollows the
 * treasure chests the player uncovers while mining: a box around each, the seconds it has left above it, and an optional line to the newest one.
 * Display only: it never helps with the lock picking and never sends anything. Chests are learned only from block changes near the player (a
 * chest block appearing where stone or air was); the server's "You uncovered a treasure chest!" line confirms them. Nothing is scanned.
 */
public class PowderHelperFeature extends AddonPanel {
	/** How a chest's box looks. */
	public enum Fill {
		OUTLINE("Outline"), FILLED("Filled");

		final String label;

		Fill(String label) {
			this.label = label;
		}
	}

	private static final String UNCOVERED = "You uncovered a treasure chest!";
	private static final String PICKED = "You have successfully picked the lock on this chest!";
	/** A chest block counts this close to the player, in blocks (squared): the chests appear near the block mined, which can be some way off. */
	private static final double CHEST_RANGE_SQ = 16.0 * 16.0;
	/** Chest blocks appearing up to this far away are still noted in the debug log, so a real session shows what is not counted. */
	private static final double LOG_RANGE_SQ = 48.0 * 48.0;
	/** The tab list is read this often, in client ticks; it is refreshed once a second anyway. */
	private static final int READ_TICKS = 10;
	private static final int PAD = 4;
	private static final int LINE = 10;
	private static final int NAME_GAP = 6;
	/** The widest amounts the element makes room for, so it keeps its width while the digits change. */
	private static final String WIDEST_FULL = "88,888,888";
	private static final String WIDEST_COMPACT = "888.8M";
	private static final long[] SAMPLE = {1_234_567, 845_210, 92_480};

	/** Which powder the panel shows: the one gained last (or the one of the place), or always the same. */
	public enum Shown {
		MITHRIL("Mithril", PowderTab.Powder.MITHRIL), GEMSTONE("Gemstone", PowderTab.Powder.GEMSTONE), GLACITE("Glacite", PowderTab.Powder.GLACITE), AUTO("Auto", null);

		final String label;
		final PowderTab.Powder powder;

		Shown(String label, PowderTab.Powder powder) {
			this.label = label;
			this.powder = powder;
		}
	}

	private final Choice<Shown> shownPowder = add(make().choice("powder", Component.literal("Powder"), Shown.AUTO, s -> Component.literal(s.label), Choice.Style.SEGMENTED), "HUD",
		"Auto: the powder you gained last");
	// the three switches of before, kept out of sight: a settings file with one of them ticked becomes that powder, once
	private final Toggle showMithril = add(make().toggle("show_mithril", Component.literal("Mithril"), true), "HUD");
	private final Toggle showGemstone = add(make().toggle("show_gemstone", Component.literal("Gemstone"), true), "HUD");
	private final Toggle showGlacite = add(make().toggle("show_glacite", Component.literal("Glacite"), true), "HUD");
	private final Toggle powderChosen = add(make().toggle("powder_chosen", Component.literal("Powder chosen"), false), "HUD");
	private final Toggle compact = add(make().toggle("compact", Component.literal("Compact numbers"), false), "HUD", "1.23M instead of 1,234,567");
	private final Toggle background = add(make().toggle("background", Component.literal("Background"), false), "HUD");
	private final Toggle onlyMining = add(make().toggle("only_mining", Component.literal("Only on mining islands"), true), "HUD");

	private final Toggle tracker = add(make().toggle("tracker", Component.literal("What you made"), true), "Tracker", "Gained, per hour and the time farmed");
	private final Choice<ProfitTracker.Range> range = add(make().choice("range", Component.literal("Time range"), ProfitTracker.Range.SESSION, ProfitTracker.Range::displayName,
		Choice.Style.SEGMENTED), "Tracker", "Stops after a minute without powder");
	private final Toggle showChests = add(make().toggle("chests_opened", Component.literal("Chests opened"), false), "Tracker");
	private final Toggle showChestRate = add(make().toggle("chests_per_hour", Component.literal("Chests per hour"), false), "Tracker");
	private final Toggle showPerChest = add(make().toggle("powder_per_chest", Component.literal("Powder per chest"), false), "Tracker", "The average of the chests you opened");
	private final Toggle showDouble = add(make().toggle("double_powder", Component.literal("2\u00D7 Powder"), false), "Tracker", "Marked while the event is on");
	private final Action resetSession = add(make().action("reset_session", Component.literal("This session"), Component.literal("Start over"), true, () -> reset(ProfitTracker.Range.SESSION)), "Reset");
	private final Action resetToday = add(make().action("reset_today", Component.literal("Today"), Component.literal("Start over"), true, () -> reset(ProfitTracker.Range.TODAY)), "Reset");
	private final Action resetAll = add(make().action("reset_lifetime", Component.literal("All time"), Component.literal("Start over"), true, () -> reset(ProfitTracker.Range.LIFETIME)), "Reset");

	private final Toggle highlight = add(make().toggle("chest_highlight", Component.literal("Highlight"), true), "Chests");
	private final ColorChoice chestColor = add(make().color("chest_color", Component.literal("Color"), 0xFFD60A), "Chests");
	private final Slider lineWidth = add(make().slider("line_width", Component.literal("Line width"), 2f, 1f, 5f, 0), "Chests");
	private final Choice<Fill> fill = add(make().choice("chest_fill", Component.literal("Box"), Fill.OUTLINE, f -> Component.literal(f.label)), "Chests");
	private final Toggle timer = add(make().toggle("despawn_timer", Component.literal("Despawn timer"), true), "Chests");
	private final Toggle alert = add(make().toggle("chest_alert", Component.literal("Alert"), false), "Chests", "A title and sound on each one");

	private final Toggle tracer = add(make().toggle("tracer", Component.literal("Tracer"), false), "Tracer", "A line to the chest you uncovered last");
	private final ColorChoice tracerColor = add(make().color("tracer_color", Component.literal("Color"), 0x5EE0D0), "Tracer");

	private final PowderTab tab = new PowderTab();
	private final PowderChests chests = new PowderChests();
	private final PowderChestDrawing drawing = new PowderChestDrawing(this, chests);
	private final PowderGains gains = new PowderGains();
	/** The rows of each powder as texts (label, value, the value's color), made a few times a second. */
	private static final int MOST_ROWS = 8;
	private final String[][] labels = new String[PowderTab.Powder.values().length][MOST_ROWS];
	private final String[][] values = new String[PowderTab.Powder.values().length][MOST_ROWS];
	private final int[][] colors = new int[PowderTab.Powder.values().length][MOST_ROWS];
	private final int[] rowCounts = new int[PowderTab.Powder.values().length];
	/** The powder shown, the one shown before it, and when it changed: the two cross-fade in place. */
	private PowderTab.Powder shown = PowderTab.Powder.MITHRIL;
	private PowderTab.Powder before;
	private long changedAt;
	private boolean shownOnce;
	private static final long FADE_MS = 180L;
	/** The powder of the place, kept when the place has none of its own. */
	private PowderTab.Powder placePowder = PowderTab.Powder.MITHRIL;
	/** The 2x Powder event: said in chat until this time, or shown in the tab list at the last read. */
	private long doubleUntil;
	private boolean doubleInTab;
	private static final long DOUBLE_LONGEST_MS = 30 * 60_000L;
	private static final String DOUBLE_ENDED = "2X POWDER ENDED!";
	private static final String DOUBLE_RUNNING = "2X POWDER";
	private int savedChanges;
	private long lastSaveAsk;
	private final ProfitStore.Part stored = new ProfitStore.Part() {
		@Override
		public com.google.gson.JsonObject save() {
			return gains.save();
		}

		@Override
		public void load(com.google.gson.JsonObject json) {
			gains.load(json);
		}

		@Override
		public void merge(com.google.gson.JsonObject json) {
			gains.merge(json);
		}
	};
	private Object level;
	private int tickCount;
	/** Whether block changes are watched now: on, in the Crystal Hollows. Asked by the mixin for every block the server changes. */
	private boolean watching;
	private boolean inHollows;

	public PowderHelperFeature() {
		super("powder_helper", "Powder Helper", "Your powders and Hollows treasure chests.",
			false, Anchor.LEFT, false, 200f, 330f);
		pageGroup("Mineshafts and Hollows");
		iconRgb(SystemColors.GREEN_RGB);
		iconName("gem");
		summary("Your powders and Hollows chests");
		withoutHeader();
		for (Toggle old : new Toggle[] {showMithril, showGemstone, showGlacite, powderChosen}) {
			old.visibleWhen(() -> false);
		}
		for (Toggle row : new Toggle[] {showChests, showChestRate, showPerChest, showDouble}) {
			row.visibleWhen(tracker::get);
		}
		range.visibleWhen(tracker::get);
		// the alert's title has the chests' color, and the tracer the boxes' line width
		chestColor.visibleWhen(() -> highlight.get() || alert.get());
		lineWidth.visibleWhen(() -> highlight.get() || tracer.get());
		fill.visibleWhen(highlight::get);
		tracerColor.visibleWhen(tracer::get);
	}

	/** @return the tracker's values for the profit store, registered when the add-on starts */
	public ProfitStore.Part stored() {
		return stored;
	}

	private void reset(ProfitTracker.Range which) {
		gains.reset(which);
		ProfitData.changed();
		LumioApi.saveSoon();
	}

	public void register() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(drawing::draw);
		dev.tim1337.lumio.api.experimental.util.WorldLabels.add((graphics, tracker) -> drawing.drawTimers(graphics));
	}

	// ------------------------------------------------------------------------------------------------------------ state

	private void tick(Minecraft client) {
		if (client.level != level) {
			level = client.level;
			if (!chests.isEmpty() && DebugHooks.ACTIVE) {
				DebugHooks.log(SkyBlockDebug.MINING, "Powder chests: {} cleared on world change", chests.size());
			}
			chests.clear();
		}
		if (!isEnabled() || client.level == null || !SkyBlock.inSkyBlock()) {
			watching = false;
			return;
		}
		if (!powderChosen.get()) {
			// a settings file of before: exactly one powder ticked is that powder, anything else Auto
			powderChosen.set(true);
			int ticked = (showMithril.get() ? 1 : 0) + (showGemstone.get() ? 1 : 0) + (showGlacite.get() ? 1 : 0);
			if (ticked == 1) {
				shownPowder.set(showMithril.get() ? Shown.MITHRIL : showGemstone.get() ? Shown.GEMSTONE : Shown.GLACITE);
			}
			LumioApi.saveSoon();
		}
		if (++tickCount % READ_TICKS == 0) {
			inHollows = "Crystal Hollows".equals(SkyBlockArea.island());
			java.util.List<String> lines = SkyBlockArea.tab();
			tab.read(lines);
			long nowMs = System.currentTimeMillis();
			doubleInTab = false;
			for (String line : lines) {
				doubleInTab |= line.toUpperCase(Locale.ROOT).contains(DOUBLE_RUNNING);
			}
			for (PowderTab.Powder powder : PowderTab.Powder.values()) {
				long counted = gains.fromTab(powder, tab.amount(powder), nowMs);
				if (counted > 0 && DebugHooks.ACTIVE) {
					DebugHooks.log(SkyBlockDebug.MINING, "Powder tracker: +{} {} from the tab list", counted, powder.label);
				}
			}
			placePowder = PowderPlace.of(SkyBlockArea.island(), SkyBlockArea.zone(), placePowder);
			makeRows();
			if (gains.changes() != savedChanges && nowMs - lastSaveAsk > 15_000L) {
				savedChanges = gains.changes();
				lastSaveAsk = nowMs;
				ProfitData.changed();
				LumioApi.saveSoon();
			}
		}
		watching = inHollows;
		if (!inHollows) {
			chests.clear();
			return;
		}
		long now = ServerTicks.count();
		for (Long block : chests.expire(now)) {
			if (DebugHooks.ACTIVE) {
				DebugHooks.log(SkyBlockDebug.MINING, "Powder chest {} timed out at tick {}", BlockPos.of(block).toShortString(), now);
			}
		}
		drawing.checkSight(client);
	}

	/** A block the server changed; called by the block mixin for every change, so it leaves after one boolean outside the Crystal Hollows. */
	public void blockChanged(BlockPos pos, BlockState before, BlockState after) {
		if (!watching) {
			return;
		}
		boolean wasChest = before.is(Blocks.CHEST);
		boolean isChest = after.is(Blocks.CHEST);
		if (wasChest == isChest) {
			return;
		}
		long block = pos.asLong();
		long now = ServerTicks.count();
		if (wasChest) {
			long appeared = chests.appearedAt(block);
			if (chests.remove(block) && DebugHooks.ACTIVE) {
				DebugHooks.log(SkyBlockDebug.MINING, "Powder chest {} disappeared at tick {}, {} ticks after it appeared", pos.toShortString(), now,
					now - appeared);
			}
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		double distanceSq = client.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		// a treasure chest takes the place of stone or air the player could have just mined, never of another thing with contents
		boolean mined = before.isAir() || !before.hasBlockEntity();
		boolean counts = distanceSq <= CHEST_RANGE_SQ && mined;
		boolean fresh = counts && chests.chestAppeared(block, now);
		if (DebugHooks.ACTIVE && distanceSq <= LOG_RANGE_SQ) {
			DebugHooks.log(SkyBlockDebug.MINING, "Powder chest block appeared at {} (tick {}): {} blocks away, in place of {}, chat line before it: {}, highlighted: {}{}",
				pos.toShortString(), now, Math.round(Math.sqrt(distanceSq) * 10.0) / 10.0, before.isAir() ? "air" : before.getBlock().getName().getString(),
				fresh && chests.confirmed(block) ? "yes" : "no", fresh ? "yes" : "no",
				fresh ? "" : !mined ? " (not in place of stone or air)" : distanceSq > CHEST_RANGE_SQ ? " (too far away)" : " (already a chest)");
		}
		if (fresh) {
			uncovered(pos, now);
		}
	}

	/** A line of the server's (never a player's): a chest uncovered or opened. */
	public void onServerLine(String line) {
		if (!isEnabled() || !SkyBlock.inSkyBlock()) {
			return;
		}
		PowderGains.Line gain = PowderGains.parse(line);
		if (gain != null) {
			gains.fromChat(gain.powder(), gain.amount(), System.currentTimeMillis());
			if (DebugHooks.ACTIVE) {
				DebugHooks.log(SkyBlockDebug.MINING, "Powder tracker: +{} {} from chat", gain.amount(), gain.powder().label);
			}
			return;
		}
		if (line.contains(DOUBLE_RUNNING)) {
			doubleUntil = line.contains(DOUBLE_ENDED) ? 0L : System.currentTimeMillis() + DOUBLE_LONGEST_MS;
		} else if (DebugHooks.ACTIVE && line.contains(" Powder")) {
			// a line about powder in a form not known here: logged, so a real session shows what is not counted
			DebugHooks.log(SkyBlockDebug.MINING, "Powder tracker: a powder line not counted, {} characters", line.length());
		}
		if (!watching) {
			return;
		}
		if (line.equals(UNCOVERED)) {
			long now = ServerTicks.count();
			if (DebugHooks.ACTIVE) {
				DebugHooks.log(SkyBlockDebug.MINING, "Powder chest: uncover line at tick {}", now);
			}
			long block = chests.uncovered(now);
			if (DebugHooks.ACTIVE) {
				DebugHooks.log(SkyBlockDebug.MINING, block == PowderChests.NONE ? "Powder chest: the uncover line matched no chest yet, it waits for the next one"
					: "Powder chest " + BlockPos.of(block).toShortString() + ": confirmed by the uncover line, " + (now - chests.appearedAt(block)) + " ticks after it appeared");
			}
		} else if (line.equals(PICKED)) {
			gains.chestOpened(System.currentTimeMillis());
			long block = openedChest(Minecraft.getInstance());
			if (block != PowderChests.NONE && chests.remove(block) && DebugHooks.ACTIVE) {
				DebugHooks.log(SkyBlockDebug.MINING, "Powder chest {} opened at tick {}", BlockPos.of(block).toShortString(), ServerTicks.count());
			}
		}
	}

	private void uncovered(BlockPos pos, long now) {
		if (DebugHooks.ACTIVE) {
			DebugHooks.log(SkyBlockDebug.MINING, "Powder chest {} uncovered, appeared at tick {}", pos.toShortString(), chests.appearedAt(pos.asLong()));
		}
		if (alert.get()) {
			DungeonAlert.show(DungeonAlert.Style.TITLE, "Treasure chest", "", 0xFF000000 | chestColor.get());
		}
	}

	/** @return the chest the player looks at, or else the nearest active one: the one just picked */
	private long openedChest(Minecraft client) {
		if (client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			long looked = hit.getBlockPos().asLong();
			if (chests.contains(looked)) {
				return looked;
			}
		}
		if (client.player == null) {
			return PowderChests.NONE;
		}
		long nearest = PowderChests.NONE;
		double nearestSq = CHEST_RANGE_SQ;
		for (Long block : chests.blocks()) {
			double distance = client.player.distanceToSqr(BlockPos.getX(block) + 0.5, BlockPos.getY(block) + 0.5, BlockPos.getZ(block) + 0.5);
			if (distance <= nearestSq) {
				nearestSq = distance;
				nearest = block;
			}
		}
		return nearest;
	}

	// ------------------------------------------------------------------------------------------------- what the drawing reads

	boolean highlightOn() {
		return isEnabled() && highlight.get();
	}

	boolean filled() {
		return fill.get() == Fill.FILLED;
	}

	int chestRgb() {
		return chestColor.get() & 0xFFFFFF;
	}

	float lineWidthPixels() {
		return lineWidth.get();
	}

	boolean timerOn() {
		return isEnabled() && timer.get();
	}

	boolean tracerOn() {
		return isEnabled() && tracer.get();
	}

	int tracerRgb() {
		return tracerColor.get() & 0xFFFFFF;
	}

	// -------------------------------------------------------------------------------------------------------------- HUD

	/** @return the powder the panel shows now: the chosen one, or by itself the one gained last, before any gain the one of the place */
	private PowderTab.Powder powderNow() {
		Shown chosen = shownPowder.get();
		if (chosen.powder != null) {
			return chosen.powder;
		}
		PowderTab.Powder last = gains.lastGained();
		return last != null ? last : placePowder;
	}

	private boolean doubleNow() {
		return doubleInTab || System.currentTimeMillis() < doubleUntil;
	}

	private String number(long amount, NumberFormat format) {
		String full = format.format(amount);
		return compact.get() ? ShortNumber.of(full) : full;
	}

	/** Makes the rows of every powder: its amount, then what was made of it in the chosen time range, then the rows switched on. */
	private void makeRows() {
		NumberFormat format = NumberFormat.getIntegerInstance(Locale.US);
		boolean sample = simulated() && !tab.anyKnown();
		ProfitTracker.Range of = range.get();
		long nowMs = System.currentTimeMillis();
		for (PowderTab.Powder powder : PowderTab.Powder.values()) {
			int i = powder.ordinal();
			int n = 0;
			long amount = sample ? SAMPLE[i] : tab.amount(powder);
			n = row(i, n, powder.label + ":", amount < 0 ? "-" : number(amount, format), 0xFFFFFFFF);
			if (tracker.get()) {
				long gained = sample ? SAMPLE[i] / 12 : gains.gained(powder, of);
				long millis = sample ? 42 * 60_000L : gains.timeMillis(powder, of);
				double rate = sample ? gained / (millis / 3_600_000.0) : gains.gainedPerHour(powder, of);
				boolean paused = !sample && of == ProfitTracker.Range.SESSION && gains.paused(powder, nowMs);
				n = row(i, n, "Gained", "+" + number(gained, format), GAIN);
				n = row(i, n, "Per hour", rate <= 0 ? "-" : number(Math.round(rate), format) + "/h", 0xFFFFFFFF);
				n = row(i, n, "Time", ProfitTracker.time(millis), paused ? DIM : 0xFFFFFFFF);
				long chests = sample ? 18 : gains.chests(powder, of);
				if (showChests.get()) {
					n = row(i, n, "Chests", number(chests, format), 0xFFFFFFFF);
				}
				if (showChestRate.get()) {
					double perHour = sample ? chests / (millis / 3_600_000.0) : gains.chestsPerHour(powder, of);
					n = row(i, n, "Chests/h", perHour <= 0 ? "-" : String.format(Locale.ROOT, "%.1f", perHour), 0xFFFFFFFF);
				}
				if (showPerChest.get()) {
					double each = sample ? gained * 0.6 / chests : gains.perChest(powder, of);
					n = row(i, n, "Per chest", each <= 0 ? "-" : number(Math.round(each), format), 0xFFFFFFFF);
				}
				if (showDouble.get()) {
					boolean on = sample || doubleNow();
					n = row(i, n, "2\u00D7 Powder", on ? "Active" : "Off", on ? DOUBLE : DIM);
				}
			}
			rowCounts[i] = n;
		}
	}

	private static final int GAIN = 0xFF7BE08A;
	private static final int DIM = 0xFF9A9AA0;
	private static final int DOUBLE = 0xFFFFD60A;

	private int row(int powder, int n, String label, String value, int color) {
		labels[powder][n] = label;
		values[powder][n] = value;
		colors[powder][n] = color;
		return n + 1;
	}

	@Override
	public void prepare(Minecraft client) {
		// in the editor's sample the rows are made each frame: it shows only there, and its values are not the read ones
		if (simulated() || rowCounts[0] == 0) {
			makeRows();
		}
		PowderTab.Powder now = powderNow();
		if (!shownOnce) {
			shownOnce = true;
			shown = now;
		} else if (now != shown) {
			before = shown;
			shown = now;
			changedAt = System.currentTimeMillis();
		}
	}

	@Override
	public boolean shown(Minecraft client) {
		if (simulated()) {
			return true;
		}
		return isEnabled() && SkyBlock.inSkyBlock() && tab.anyKnown() && !(onlyMining.get() && !SkyBlockArea.inMines());
	}

	@Override
	public boolean inEditorWhenHidden() {
		return false;
	}

	/** @return how many rows the panel has: the same for every powder, so it keeps its size when the powder changes */
	private int rows() {
		return Math.max(1, rowCounts[shown.ordinal()]);
	}

	@Override
	public int panelWidth() {
		Minecraft client = Minecraft.getInstance();
		int names = client.font.width(PowderTab.Powder.GEMSTONE.label + ":");
		if (tracker.get()) {
			names = Math.max(names, client.font.width(showDouble.get() ? "2\u00D7 Powder" : "Per chest"));
		}
		int amounts = client.font.width((compact.get() ? WIDEST_COMPACT : WIDEST_FULL) + (tracker.get() ? "/h" : ""));
		return 2 * PAD + names + NAME_GAP + amounts;
	}

	@Override
	public int panelHeight() {
		return 2 * PAD + rows() * LINE - 1;
	}

	@Override
	public float hudRadius() {
		return Radius.SM * hudScale();
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		int width = panelWidth();
		if (background.get()) {
			drawGlass(ui, width, panelHeight(), Radius.SM);
		}
		// the powder shown and, for a moment after a change, the one before it: they cross-fade in place
		float in = before == null ? 1f : Math.min(1f, (System.currentTimeMillis() - changedAt) / (float) FADE_MS);
		if (in >= 1f) {
			before = null;
		} else {
			drawRows(ui, before, width, 1f - in);
		}
		drawRows(ui, shown, width, in);
	}

	private void drawRows(Canvas ui, PowderTab.Powder powder, int width, float alpha) {
		int a = Math.round(alpha * 255f);
		// the game draws text below a few percent of opacity fully opaque: left out
		if (a < 8) {
			return;
		}
		int i = powder.ordinal();
		int y = PAD;
		for (int n = 0; n < rowCounts[i]; n++) {
			int labelColor = n == 0 ? powder.rgb : 0xC8C8D0;
			// through the panel's own text drawing: in the chosen font, on whole screen pixels at every HUD size
			ui.textAt(labels[i][n], PAD, y, a << 24 | labelColor & 0xFFFFFF);
			String value = values[i][n];
			ui.textAt(value, width - PAD - ui.width(value), y, a << 24 | colors[i][n] & 0xFFFFFF);
			y += LINE;
		}
	}
}
