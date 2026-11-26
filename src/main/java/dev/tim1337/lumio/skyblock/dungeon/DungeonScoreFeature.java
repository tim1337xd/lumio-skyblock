package dev.tim1337.lumio.skyblock.dungeon;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.LumioChat;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockSimulation;
import dev.tim1337.lumio.skyblock.data.Activity;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The run's score as Hypixel's Team Score will count it ({@link DungeonScore}), in one of two styles. Score bar: the score as a big number with
 * its rank in a colored box and "max N" on the right; a bar with the rank marks C, B, A, S and S+ on it, filled in the rank's color; four values
 * below (Secrets, Crypts, Puzzles, Deaths); and a last line with the next step in words ("30 secrets to S+", "S+ reached") and the bonus marks
 * written out (Mimic, Prince, Bat: grey not yet, green with a check counted). One line: the rank box, the score, Secrets, Crypts, Puzzles, and
 * the next step only while something is missing. Yellow is still missing, green is done. It shows only in the Catacombs, and after the run its
 * final score for a few seconds.
 */
public class DungeonScoreFeature extends AddonPanel {
	/** How the panel looks. The names are saved: the settings keep them. */
	public enum Style {
		SCORE_BAR("Score bar"), ONE_LINE("One line");

		final String label;

		Style(String label) {
			this.label = label;
		}
	}

	/** The rank the next-step line aims for. */
	public enum Aim {
		S_PLUS("S+", DungeonScore.S_PLUS_SCORE), S("S", DungeonScore.S_SCORE);

		final String label;
		final int score;

		Aim(String label, int score) {
			this.label = label;
			this.score = score;
		}
	}

	/** What the first death costs. */
	public enum FirstDeath {
		SPIRIT("Spirit pet (−1)"), ALWAYS("Always −2");

		final String label;

		FirstDeath(String label) {
			this.label = label;
		}
	}

	/** Whether Paul's EZPZ perk adds its ten points. */
	public enum Paul {
		ON("On"), OFF("Off"), AUTO("Auto");

		final String label;

		Paul(String label) {
			this.label = label;
		}
	}

	private static final int PAD = PanelKit.PAD;
	/** The crypts that count for the score. */
	private static final int CRYPTS_COUNTED = 5;
	// the score bar: its width, the score's row, the bar with its marks and their letters, the four values, and the last line
	private static final int BAR_WIDTH = 216;
	private static final int SCORE_ROW = 22;
	private static final int BAR_ROW = 22;
	private static final int BAR_HEIGHT = 3;
	private static final int MARK_HEIGHT = 7;
	private static final int VALUES_ROW = 24;
	private static final int LAST_ROW = 16;
	private static final float SCORE_SCALE = TextScale.HEADING * 1.35f;
	private static final float MARK_SCALE = TextScale.SMALL * 0.8f;
	/** What the bar's whole length stands for: a little past S+, so its mark is not at the very end. */
	private static final int BAR_MOST = 310;
	private static final int[] RANK_SCORES = {DungeonScore.C_SCORE, DungeonScore.B_SCORE, DungeonScore.A_SCORE, DungeonScore.S_SCORE, DungeonScore.S_PLUS_SCORE};
	private static final String[] RANKS = {"C", "B", "A", "S", "S+"};
	/** The rank's box: its height, the room beside its letter, and how strong its fill is. */
	private static final int RANK_BOX = 13;
	private static final float RANK_BOX_PAD = 4f;
	private static final float RANK_FILL = 0.16f;
	/** A bonus mark's chip: its height, its padding, the check's size, and the gap to the next. */
	private static final int CHIP_HEIGHT = 11;
	private static final float CHIP_PAD = 4f;
	private static final int CHECK = 5;
	private static final float CHIP_GAP = 3f;
	// the one line
	private static final int LINE_HEIGHT = 20;
	private static final float LINE_GAP = 6f;
	private static final int LINE_WIDTH_GUESS = 230;
	private static final int GOLD = 0xFFFFD60A;
	private static final int TITLE_FADE_IN_TICKS = 5;
	private static final int TITLE_STAY_TICKS = 40;
	private static final int TITLE_FADE_OUT_TICKS = 10;
	/** Paul with his EZPZ perk, as SkyBlock names the mayor or minister in the player list. */
	private static final Pattern PAUL = Pattern.compile("(?i)\\b(?:mayor|minister)\\b.*\\bpaul\\b|\\bEZPZ\\b");
	/** The style's key, and its names from before there were two styles. */
	static final String STYLE_KEY = "style";

	private final Choice<Style> style = add(make().choice(STYLE_KEY, Component.literal("Style"), Style.SCORE_BAR, value -> Component.literal(value.label),
		Choice.Style.SEGMENTED), "General");
	private final Toggle showMarks = add(make().toggle("show_marks", Component.literal("Show the bonus marks"), true), "General", "Mimic, Prince and Bat");
	private final Toggle showMax = add(make().toggle("show_max", Component.literal("Show \"max\""), true), "General", "The most the score can still reach");
	private final Toggle showStep = add(make().toggle("show_300", Component.literal("Show the next-step line"), true), "General",
		"What your rank still needs");
	private final Choice<Aim> aim = add(make().choice("aim", Component.literal("Rank to aim for"), Aim.S_PLUS, value -> Component.literal(value.label),
		Choice.Style.SEGMENTED), "General");
	private final Choice<FirstDeath> firstDeath = add(make().choice("first_death", Component.literal("First death"), FirstDeath.SPIRIT,
		value -> Component.literal(value.label), Choice.Style.SEGMENTED), "Score", "Spirit pet: first death costs 1");
	private final Choice<Paul> paul = add(make().choice("paul", Component.literal("Paul's EZPZ"), Paul.AUTO, value -> Component.literal(value.label),
		Choice.Style.SEGMENTED), "Score", "+10 with Paul's EZPZ; Auto reads the mayor");
	private final Toggle alert = add(make().toggle("alert", Component.literal("Alert at 270/300"), true), "Score");

	/** The choices last handed to the score, made again only when one changes. */
	private DungeonScore.Options options = DungeonScore.Options.DEFAULT;
	/** The player list the Paul check last read, and what it found. */
	private List<String> paulTab;
	private boolean paulSeen;
	/** The one line's width as last drawn: the panel is as wide as its content. */
	private int lineWidth = LINE_WIDTH_GUESS;

	public DungeonScoreFeature() {
		super("dungeon_score", "Score & info", "The live score, rank and next step.", false,
			Anchor.LEFT, false, 8f, 216f);
		iconRgb(SystemColors.YELLOW_RGB);
		iconName("star");
		summary("Live score, the next step");
		showMarks.visibleWhen(() -> style.get() != Style.ONE_LINE);
		showMax.visibleWhen(() -> style.get() != Style.ONE_LINE);
		aim.visibleWhen(showStep::get);
		offerNoBackground();
		Dungeon.listen(new Dungeon.Listener() {
			@Override
			public void scoreReached(int mark, int score) {
				if (isEnabled() && alert.get()) {
					Minecraft client = Minecraft.getInstance();
					dev.tim1337.lumio.skyblock.hud.TitleQueue.show(Component.literal(mark >= DungeonScore.S_PLUS_SCORE ? "§6300 score" : "§e270 score"),
						Component.empty(), TITLE_FADE_IN_TICKS, TITLE_STAY_TICKS, TITLE_FADE_OUT_TICKS, true);
					LumioChat.info(Component.literal(mark + " score reached (" + score + ")."));
				}
			}
		});
	}

	@Override
	public void load(JsonObject json) {
		migrateStyle(json);
		super.load(json);
	}

	/** The three styles of before become the two: Minimal was one line, Compact and Detailed become the score bar. */
	static void migrateStyle(JsonObject json) {
		if (json.get(STYLE_KEY) instanceof JsonPrimitive saved && saved.isString()) {
			String name = saved.getAsString();
			if (name.equals("MINIMAL")) {
				json.addProperty(STYLE_KEY, Style.ONE_LINE.name());
			} else if (name.equals("COMPACT") || name.equals("DETAILED")) {
				json.addProperty(STYLE_KEY, Style.SCORE_BAR.name());
			}
		}
	}

	@Override
	public void prepare(Minecraft client) {
		boolean spirit = firstDeath.get() == FirstDeath.SPIRIT;
		boolean paulNow = paulOn();
		if (options.spiritFirstDeath() != spirit || options.paul() != paulNow) {
			options = new DungeonScore.Options(spirit, paulNow);
		}
		Dungeon.scoreOptions(options);
	}

	/** @return whether Paul's perk counts: as set, or on Auto while the player list names Paul as mayor or minister */
	private boolean paulOn() {
		return switch (paul.get()) {
			case ON -> true;
			case OFF -> false;
			case AUTO -> {
				List<String> tab = SkyBlockArea.tab();
				if (tab != paulTab) {
					paulTab = tab;
					paulSeen = false;
					for (String line : tab) {
						if (PAUL.matcher(line).find()) {
							paulSeen = true;
							break;
						}
					}
				}
				yield paulSeen;
			}
		};
	}

	/** @return how visible the final score of a run that just ended still is, 0 when it is gone */
	private static float finalShare() {
		return Dungeon.finalScore() == null ? 0f : DungeonPresence.finalShare(Dungeon.endedAt(), System.currentTimeMillis());
	}

	@Override
	public boolean shown(Minecraft client) {
		boolean running = Dungeon.inClear() || Dungeon.inBoss() && Dungeon.endedAt() == 0;
		return running || Dungeon.in() && finalShare() > 0f || simulated();
	}

	@Override
	public boolean inEditorWhenHidden() {
		return true;
	}

	@Override
	public int panelWidth() {
		return style.get() == Style.ONE_LINE ? lineWidth : BAR_WIDTH;
	}

	@Override
	public int panelHeight() {
		if (style.get() == Style.ONE_LINE) {
			return LINE_HEIGHT;
		}
		return PAD + SCORE_ROW + BAR_ROW + VALUES_ROW + (lastLine() ? LAST_ROW : 0) + PAD - 2;
	}

	/** @return whether the score bar has its last line: the next step, the bonus marks, or both */
	private boolean lastLine() {
		return showStep.get() || showMarks.get();
	}

	/** What the panel shows this frame: the run's numbers, or the example's. */
	private record Shown(DungeonStats stats, DungeonFloor floor, DungeonScore.Result score, DungeonScore.Step step, boolean clearingOver) {
	}

	private Shown shownNow() {
		boolean live = Dungeon.in();
		if (!live) {
			// the example in the HUD editor: a run half way, or at its end while a boss fight is simulated
			boolean end = SkyBlockSimulation.active() && SkyBlockSimulation.activity() == Activity.DUNGEON_BOSS;
			DungeonStats stats = end ? sampleEnd() : sampleMidRun();
			DungeonScore.Result score = DungeonScore.of(stats, DungeonFloor.F7, end, DungeonScore.Options.DEFAULT);
			return new Shown(stats, DungeonFloor.F7, score, DungeonScore.step(stats, DungeonFloor.F7, end, DungeonScore.Options.DEFAULT, aim.get().score), end);
		}
		boolean ended = Dungeon.endedAt() != 0 && Dungeon.finalScore() != null;
		DungeonStats stats = Dungeon.stats();
		DungeonScore.Result score = ended ? Dungeon.finalScore() : Dungeon.score();
		// once the boss is on or the run ended, a bonus not got is lost
		boolean clearingOver = Dungeon.inBoss() || ended;
		DungeonScore.Step step = ended ? (score.total() >= aim.get().score ? DungeonScore.Step.REACHED : DungeonScore.Step.OUT_OF_REACH)
			: DungeonScore.step(stats, Dungeon.floor(), Dungeon.inBoss(), options, aim.get().score);
		return new Shown(stats, Dungeon.floor(), score, step, clearingOver);
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		Shown shown = shownNow();
		boolean fading = Dungeon.in() && Dungeon.endedAt() != 0 && Dungeon.finalScore() != null;
		float alphaBefore = Draw.alpha();
		if (fading) {
			Draw.setAlpha(alphaBefore * finalShare());
		}
		try {
			if (style.get() == Style.ONE_LINE) {
				drawOneLine(ui, shown);
			} else {
				drawScoreBar(ui, shown);
			}
		} finally {
			Draw.setAlpha(alphaBefore);
		}
	}

	// ------------------------------------------------------------------------------------------------ the score bar

	private void drawScoreBar(Canvas ui, Shown shown) {
		int width = BAR_WIDTH;
		DungeonScore.Result score = shown.score();
		DungeonStats stats = shown.stats();
		drawGlass(ui, width, panelHeight(), PanelKit.RADIUS);
		int text = HudTheme.text();
		int secondary = HudTheme.secondary();
		String rank = score.rank();
		int rankColor = rankColor(rank);

		// the score, its rank in a box, and "max N"
		String total = String.valueOf(score.total());
		TabularText.draw(ui, total, PAD, PAD, SCORE_SCALE, true, text);
		float numberWidth = TabularText.width(ui, total, SCORE_SCALE, true);
		rankBox(ui, rank, rankColor, PAD + numberWidth + 6, PAD + 2);
		if (showMax.get()) {
			TabularText.drawRight(ui, "max " + score.best(), width - PAD, PAD + 5, TextScale.SMALL, false, secondary);
		}

		// the bar with the rank marks on it, filled in the rank's color
		float barY = PAD + SCORE_ROW + 2;
		float barWidth = width - 2 * PAD;
		ui.roundedSmooth(PAD, barY, barWidth, BAR_HEIGHT, Radius.pill(BAR_HEIGHT), Draw.withOpacity(text, 0.12f));
		float share = Math.clamp(score.total() / (float) BAR_MOST, 0f, 1f);
		if (share > 0f) {
			ui.roundedSmooth(PAD, barY, Math.max(BAR_HEIGHT, barWidth * share), BAR_HEIGHT, Radius.pill(BAR_HEIGHT), rankColor);
		}
		for (int i = 0; i < RANK_SCORES.length; i++) {
			float at = PAD + barWidth * RANK_SCORES[i] / BAR_MOST;
			boolean reached = score.total() >= RANK_SCORES[i];
			ui.fill(Math.round(at), Math.round(barY - (MARK_HEIGHT - BAR_HEIGHT) / 2f), 1, MARK_HEIGHT, reached ? 0xFFFFFFFF : Draw.withOpacity(text, 0.35f));
			float letterWidth = ui.boldWidthScaled(RANKS[i], MARK_SCALE);
			// the last letter stays inside the panel
			float letterX = Math.min(at - letterWidth / 2f, width - PAD - letterWidth);
			ui.textAtScaledBold(RANKS[i], letterX, barY + BAR_HEIGHT + 4, MARK_SCALE, reached ? rankColor(RANKS[i]) : secondary);
		}

		// the four values
		float y = PAD + SCORE_ROW + BAR_ROW;
		float column = barWidth / 4f;
		int totalSecrets = score.totalSecrets();
		int puzzles = Math.max(stats.puzzleCount, stats.puzzles.size());
		value(ui, "Secrets", stats.secretsFound + "/" + (totalSecrets > 0 ? String.valueOf(totalSecrets) : "–"), PAD, y,
			progress(stats.secretsFound, score.secretsNeeded(), text), secondary);
		value(ui, "Crypts", Math.min(stats.crypts, 99) + "/" + CRYPTS_COUNTED, PAD + column, y, progress(stats.crypts, CRYPTS_COUNTED, text), secondary);
		value(ui, "Puzzles", stats.puzzlesDone() + "/" + (puzzles > 0 ? String.valueOf(puzzles) : "–"), PAD + 2 * column, y,
			stats.puzzlesFailed() > 0 ? SystemColors.RED : progress(stats.puzzlesDone(), puzzles, text), secondary);
		value(ui, "Deaths", String.valueOf(stats.deaths()), PAD + 3 * column, y, stats.deaths() > 0 ? SystemColors.RED : SystemColors.GREEN, secondary);

		// the last line: the next step in words, and the bonus marks written out
		if (!lastLine()) {
			return;
		}
		y += VALUES_ROW;
		PanelKit.divider(ui, width, Math.round(y - 3));
		float marksLeft = width - PAD;
		if (showMarks.get()) {
			marksLeft = drawMarks(ui, stats, shown.floor(), shown.clearingOver(), width - PAD, y + 2);
		}
		if (showStep.get()) {
			String words = stepWords(shown.step(), aim.get().label, false);
			if (!words.isEmpty()) {
				int color = shown.step().reached() ? SystemColors.GREEN : shown.step().outOfReach() ? secondary : SystemColors.YELLOW;
				ui.textAtScaledBold(ui.fitScaled(words, marksLeft - PAD - 4, TextScale.SMALL), PAD, y + 4, TextScale.SMALL, color);
			}
		}
	}

	/**
	 * @param brief whether only what is missing is said ("+30 secrets", for the one line), not the whole sentence
	 * @return the next step in words: "30 secrets to S+", "S+ reached", "S+ out of reach"; brief, "+30 secrets" or nothing
	 */
	static String stepWords(DungeonScore.Step step, String rank, boolean brief) {
		if (step.reached()) {
			return brief ? "" : rank + " reached";
		}
		if (step.outOfReach()) {
			return brief ? "" : rank + " out of reach";
		}
		return brief ? "+" + step.what() : step.what() + " to " + rank;
	}

	/** A value with its name above it. */
	private static void value(Canvas ui, String label, String text, float x, float y, int color, int secondary) {
		ui.textAtScaled(label, x, y, TextScale.SMALL, secondary);
		TabularText.draw(ui, text, x, y + 10, TextScale.BODY, true, color);
	}

	/** @return the color of a count against what it needs: green when there, yellow part way (still missing), the plain text color before the first */
	static int progress(int have, int need, int plain) {
		if (need > 0 && have >= need) {
			return SystemColors.GREEN;
		}
		return have > 0 ? SystemColors.YELLOW : plain;
	}

	/** The rank as a letter in a box of its color: a soft fill and a border. @return the box's width */
	private static float rankBox(Canvas ui, String rank, int color, float x, float y) {
		float width = ui.boldWidthScaled(rank, TextScale.BODY) + 2 * RANK_BOX_PAD;
		ui.roundedSmooth(x, y, width, RANK_BOX, Radius.SM, Draw.withOpacity(color, RANK_FILL));
		ui.ring(Math.round(x), Math.round(y), Math.round(width), RANK_BOX, Radius.SM, 1, color);
		ui.textAtScaledBold(rank, x + RANK_BOX_PAD, y + (RANK_BOX - 8) / 2f, TextScale.BODY, color);
		return width;
	}

	/**
	 * The bonus marks written out, right-aligned: Mimic (only where there is one), Prince, Bat (not in the Entrance). Grey means not yet, green
	 * with a check means counted; one that can no longer be got is fainter. @return where they start
	 */
	private static float drawMarks(Canvas ui, DungeonStats stats, DungeonFloor floor, boolean clearingOver, float right, float y) {
		float x = right;
		if (ScoreMarks.showsBat(floor)) {
			x = chip(ui, "Bat", ScoreMarks.state(stats.batBonus(), clearingOver), x, y) - CHIP_GAP;
		}
		if (ScoreMarks.showsPrince(floor)) {
			x = chip(ui, "Prince", ScoreMarks.state(stats.princeKilled, clearingOver), x, y) - CHIP_GAP;
		}
		if (ScoreMarks.showsMimic(floor)) {
			x = chip(ui, "Mimic", ScoreMarks.state(stats.mimicKilled, clearingOver), x, y) - CHIP_GAP;
		}
		return x;
	}

	/** A bonus mark's chip ending at a place. @return where it starts */
	private static float chip(Canvas ui, String name, ScoreMarks.State state, float right, float y) {
		boolean got = state == ScoreMarks.State.GOT;
		float textWidth = ui.widthScaled(name, TextScale.SMALL);
		float width = textWidth + 2 * CHIP_PAD + (got ? CHECK + 2 : 0);
		float x = right - width;
		int color = got ? SystemColors.GREEN : Draw.withOpacity(HudTheme.secondary(), state == ScoreMarks.State.LOST ? 0.55f : 1f);
		ui.roundedSmooth(x, y, width, CHIP_HEIGHT, Radius.XS, Draw.withOpacity(got ? SystemColors.GREEN : HudTheme.text(), got ? 0.16f : 0.08f));
		ui.textAtScaled(name, x + CHIP_PAD, y + (CHIP_HEIGHT - 7) / 2f, TextScale.SMALL, color);
		if (got) {
			ui.icon("check", Math.round(x + CHIP_PAD + textWidth + 2), Math.round(y + (CHIP_HEIGHT - CHECK) / 2f), CHECK, SystemColors.GREEN);
		}
		return x;
	}

	// ------------------------------------------------------------------------------------------------ the one line

	/** One line: the rank box, the score, Secrets, Crypts, Puzzles, and the next step only while something is missing. */
	private void drawOneLine(Canvas ui, Shown shown) {
		DungeonScore.Result score = shown.score();
		DungeonStats stats = shown.stats();
		int text = HudTheme.text();
		int secondary = HudTheme.secondary();
		drawGlass(ui, lineWidth, LINE_HEIGHT, Radius.SM);
		float y = (LINE_HEIGHT - 8) / 2f;
		float x = PAD - 2;
		x += rankBox(ui, score.rank(), rankColor(score.rank()), x, (LINE_HEIGHT - RANK_BOX) / 2f) + LINE_GAP;
		String total = String.valueOf(score.total());
		TabularText.draw(ui, total, x, y, TextScale.BODY, true, text);
		x += TabularText.width(ui, total, TextScale.BODY, true) + LINE_GAP;
		int totalSecrets = score.totalSecrets();
		int puzzles = Math.max(stats.puzzleCount, stats.puzzles.size());
		x = linePart(ui, "Secrets", stats.secretsFound + "/" + (totalSecrets > 0 ? String.valueOf(totalSecrets) : "–"), x, y,
			progress(stats.secretsFound, score.secretsNeeded(), text), secondary);
		x = linePart(ui, "Crypts", Math.min(stats.crypts, 99) + "/" + CRYPTS_COUNTED, x, y, progress(stats.crypts, CRYPTS_COUNTED, text), secondary);
		x = linePart(ui, "Puzzles", stats.puzzlesDone() + "/" + (puzzles > 0 ? String.valueOf(puzzles) : "–"), x, y,
			stats.puzzlesFailed() > 0 ? SystemColors.RED : progress(stats.puzzlesDone(), puzzles, text), secondary);
		String step = showStep.get() ? stepWords(shown.step(), aim.get().label, true) : "";
		if (!step.isEmpty()) {
			ui.fill(Math.round(x), Math.round(y - 1), 1, 10, Draw.withOpacity(text, 0.18f));
			x += LINE_GAP;
			ui.textAtScaledBold(step, x, y + 0.5f, TextScale.SMALL, SystemColors.YELLOW);
			x += ui.boldWidthScaled(step, TextScale.SMALL) + LINE_GAP;
		}
		// the panel is as wide as the line: known once drawn, for the next frame
		lineWidth = Math.round(x + PAD - 2 - LINE_GAP);
	}

	/** A part of the one line, after a thin separator: its name, quiet, and its value. @return where the next part starts */
	private static float linePart(Canvas ui, String label, String value, float x, float y, int color, int secondary) {
		ui.fill(Math.round(x), Math.round(y - 1), 1, 10, Draw.withOpacity(HudTheme.text(), 0.18f));
		x += LINE_GAP;
		ui.textAtScaled(label, x, y + 0.5f, TextScale.SMALL, secondary);
		x += ui.widthScaled(label + " ", TextScale.SMALL);
		TabularText.draw(ui, value, x, y + 0.5f, TextScale.SMALL, true, color);
		return x + TabularText.width(ui, value, TextScale.SMALL, true) + LINE_GAP;
	}

	static int rankColor(String rank) {
		return switch (rank) {
			case "S+", "S" -> GOLD;
			case "A" -> 0xFF34C759;
			case "B" -> 0xFF5AC8FA;
			case "C" -> 0xFFFF9F0A;
			default -> 0xFF8E8E93;
		};
	}

	// ------------------------------------------------------------------------------------------------ the example in the HUD editor

	/** A run half way, as in the mockup: a few secrets, no crypt, no puzzle yet, a bat. */
	static DungeonStats sampleMidRun() {
		DungeonStats stats = new DungeonStats();
		stats.secretsFound = 9;
		stats.secretsPercent = 23.1;
		stats.crypts = 0;
		stats.completedRooms = 12;
		stats.clearedPercent = 40;
		stats.puzzleCount = 4;
		stats.time = "4m 02s";
		stats.elapsedSeconds = 242;
		stats.batSlain = true;
		return stats;
	}

	/** The end of a run, as in the mockup: everything found and done. */
	static DungeonStats sampleEnd() {
		DungeonStats stats = new DungeonStats();
		stats.secretsFound = 39;
		stats.secretsPercent = 100;
		stats.crypts = 5;
		stats.completedRooms = 30;
		stats.clearedPercent = 100;
		stats.puzzleCount = 4;
		stats.time = "9m 41s";
		stats.elapsedSeconds = 581;
		stats.puzzles.put(DungeonPuzzle.WEIRDOS, DungeonPuzzle.Status.DONE);
		stats.puzzles.put(DungeonPuzzle.BOULDER, DungeonPuzzle.Status.DONE);
		stats.puzzles.put(DungeonPuzzle.QUIZ, DungeonPuzzle.Status.DONE);
		stats.puzzles.put(DungeonPuzzle.TIC_TAC_TOE, DungeonPuzzle.Status.DONE);
		stats.mimicKilled = true;
		stats.princeKilled = true;
		stats.batSlain = true;
		stats.bloodDone = true;
		return stats;
	}
}
