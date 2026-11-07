package dev.tim1337.lumio.skyblock.slayer;

import dev.tim1337.lumio.api.experimental.util.Debug;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.hud.HudEditor;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.feature.SettingGroup;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.TextInput;
import dev.tim1337.lumio.api.setting.Toggle;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.motion.Easing;
import dev.tim1337.lumio.skyblock.hud.PanelMoment;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;
import dev.tim1337.lumio.skyblock.hud.PanelLooks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * The slayer panel: the boss being fought (its name and tier, timer, health, phase and hits left) and the player's slayer stats. The bosses are
 * read by {@link SlayerBosses}. In carry mode the carried player's boss comes first, and the player's own stats are left out while it is shown.
 */
public class SlayerPhaseHudFeature extends AddonPanel {
	private final Choice<PanelLooks.Slayer> look = add(make().choice("look", Component.literal("Style"), PanelLooks.Slayer.CARD,
		PanelLooks.Slayer::displayName, Choice.Style.SEGMENTED), "Phase display");
	private final Toggle showHealth = add(make().toggle("show_health", Component.literal("Boss health"), true), "Phase display");
	private final Toggle showPhase = add(make().toggle("show_phase", Component.literal("Phase"), true), "Phase display");
	private final Toggle showTimer = add(make().toggle("show_timer", Component.literal("Timer"), true), "Phase display");
	private final Toggle showBossesRate = add(make().toggle("show_bosses_rate", Component.literal("Bosses/h"), true), "Phase display");
	private final Toggle showAvgKill = add(make().toggle("show_avg_kill", Component.literal("Avg kill"), true), "Phase display");
	private final Toggle showCoinsRate = add(make().toggle("show_coins_rate", Component.literal("Coins/h"), false), "Phase display");
	private final Toggle showNextBoss = add(make().toggle("show_next_boss", Component.literal("Next boss"), true), "Phase display");
	private final Toggle showSessionTotal = add(make().toggle("show_session_total", Component.literal("Session total"), false), "Phase display");
	/** The stat tiles in their order. */
	private final List<Toggle> tiles = List.of(showBossesRate, showAvgKill, showCoinsRate, showSessionTotal);
	private final Toggle carryMode = add(make().toggle("carry_mode", Component.literal("Carry mode"), false), "Carry",
		"Shows the boss of the player you carry");
	private final TextInput carryName = add(make().text("carry_name", Component.literal("Player"), "", false).cleaned(SlayerOwners::clean),
		"Carry");

	{
		carryName.under(carryMode);
	}

	@Override
	public boolean ownWindow() {
		return true;
	}

	@Override
	public boolean alwaysTabbed() {
		return false;
	}

	private final Spring shownAmount = Spring.smooth(Spring.DEFAULT);
	private final Spring shownHealth = Spring.smooth(Spring.SNAPPY);

	public SlayerPhaseHudFeature() {
		super("slayer_phase", "Slayer HUD", "The slayer panel: your boss with its phase, health and timer, and your slayer stats.", false, Anchor.LEFT, false,
			200f, 120f);
		offerCardSize();
		iconRgb(SystemColors.RED_RGB);
		iconName("sword");
		summary("Your boss phase, health and slayer stats");
		offerNoBackground();
		offerModes(look);
		offerHeader();
	}

	/** @return the carried player's name while carry mode is on, else "" */
	public String carriedName() {
		return carryMode.get() ? carryName.get() : "";
	}

	/** @return whether the panel shows a carried player's boss now */
	private static boolean carrying() {
		return SlayerBosses.carried() != null;
	}

	/** Counts the player's own kills for the average kill; the bosses themselves are read by {@link SlayerBosses}. */
	public void onMessage(Component message) {
		if (message.getString().contains("SLAYER QUEST COMPLETE!")) {
			killed();
		}
	}

	/** The header's status while carrying, "Carry · Name" with the timer, made again only when it changes. */
	private String carryMeta = "";
	private String carryMetaFor = "";
	private String carryMetaTimer = "";

	private String carryMeta(String name, String timer) {
		if (!name.equals(carryMetaFor) || !timer.equals(carryMetaTimer)) {
			carryMetaFor = name;
			carryMetaTimer = timer;
			carryMeta = "Carry · " + name + (timer.isEmpty() ? "" : "  " + timer);
		}
		return carryMeta;
	}

	// ------------------------------------------------------------------------------------------------------- the panel

	/** The boss's title size, the health bar's height, the gap between tiles, and how far a phase marker reaches past the bar. */
	private static final float BOSS = 1.1f;
	private static final float HEALTH_BAR = 6f;
	private static final float TILE_GAP = 4f;
	private static final float MARKER = 1.5f;
	static final int BOSS_RED = 0xFFFF6257;
	/** The start of a plain health phase's name, "Phase 2 of 3". */
	private static final String PHASE_PREFIX = "Phase ";
	/** The boss's block: its title, the bar and the health line under it; the quest's line before the boss spawns. */
	private static final int BOSS_BLOCK = 30;
	private static final int QUEST_BLOCK = 10;
	/** The next boss's block under a line: its label row and the bar. */
	private static final int NEXT_BLOCK = 20;
	/** The boss spawning: the panel turns to the boss, a thin red border fades in and settles, the health fills up from zero. */
	private static final long SPAWN_MILLIS = 1200L;
	private static final float BORDER_IN = 100f;
	private static final float BORDER_FULL = 600f;
	private static final float FILL_END = 600f;
	private final PanelMoment spawnMoment = new PanelMoment(SPAWN_MILLIS);
	/** The kills of this session: when the fight in progress started, and the time all kills took, for the average kill. */
	private long fightStart;
	private long killMillis;
	private int kills;
	private boolean bossBefore;

	private SlayerBoss shownBoss() {
		SlayerBoss boss = SlayerBosses.shown();
		// a sample only while the editor simulates slayer: otherwise the editor shows what the game shows now
		if (simulated() && boss == null) {
			return quest() ? null : sampleBoss();
		}
		return boss;
	}

	/** @return whether a slayer quest runs now, with its boss not spawned yet */
	private static boolean quest() {
		return SkyBlockArea.slayerQuest() && !SkyBlockArea.slayerBossUp() && SlayerBosses.showsHere();
	}

	// ------------------------------------------------------------------------------------------------------- sample bosses

	/** The boss shown in the HUD editor without a real one; made on first use, since items cannot be made while the mod loads. */
	private static SlayerBoss sample;
	private static SlayerBoss sampleBoss() {
		if (sample == null) {
			sample = new SlayerBoss("Voidgloom Seraph IV", 30e6, 50e6, "Hits", SystemColors.PURPLE, "", "3:42", new ItemStack(Items.ENDER_PEARL), true,
				new VoidgloomPhases.View(3, 6, true, 15, 100, 0.15f));
		}
		return sample;
	}

	@Override
	public void prepare(Minecraft client) {
		SlayerBoss shown = shownBoss();
		boolean questOn = shown == null && (quest() && SkyBlock.allowed());
		shownAmount.follow(shown != null || questOn ? 1f : 0f, frameSeconds());
		// only the player's own boss counts toward the average kill, never a carried one
		boolean bossNow = SlayerBosses.own() != null && !SlayerBosses.ownCocooned();
		if (bossNow && !bossBefore) {
			fightStart = System.currentTimeMillis();
			spawnMoment.start();
		}
		bossBefore = bossNow;
		float health = shown != null && shown.maxHealth() > 0 ? (float) Mth.clamp(shown.health() / shown.maxHealth(), 0, 1) : 0f;
		shownHealth.follow(health, frameSeconds());
		if (shown != null && shown.phases() != null) {
			// the phase's bar fills smoothly to its new value, and its color crosses over between the shield's purple and the boss red
			phaseBar.follow(shown.phases().bar(), frameSeconds());
			shieldBlend.follow(shown.phases().hits() ? 1f : 0f, frameSeconds());
		}
		if (shown != null) {
			last = shown;
		}
		questView = shown == null && questOn;
	}

	/** Plays the boss spawning once, for the HUD editor's simulation of the moments. */
	public void playSpawn() {
		spawnMoment.start();
	}

	/** Called when the quest is turned in: the fight in progress counts toward the average kill. */
	private void killed() {
		if (fightStart > 0) {
			long took = System.currentTimeMillis() - fightStart;
			if (took > 0 && took < MOST_KILL_MILLIS) {
				killMillis += took;
				kills++;
			}
			fightStart = 0;
		}
	}

	/** A fight longer than this was not watched from its start (the game was restarted, or the player left and came back). */
	private static final long MOST_KILL_MILLIS = 10 * 60_000L;

	/** The last shown boss, which fades out when it is gone, and whether the quest is shown instead (its boss not spawned yet). */
	private SlayerBoss last;
	private boolean questView;

	@Override
	public boolean shown(Minecraft client) {
		return shownAmount.value() > 0.01f && (last != null || questView);
	}

	@Override
	public boolean inEditorWhenHidden() {
		return false;
	}

	@Override
	public int panelWidth() {
		return cardWidth();
	}

	@Override
	public int panelHeight() {
		return simple() ? PanelKit.SIMPLE_ROW : Math.round(layout(null, false));
	}

	private boolean simple() {
		return look.get() == PanelLooks.Slayer.SIMPLE;
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		float amount = Mth.clamp(shownAmount.value(), 0f, 1f);
		float alphaBefore = Draw.alpha();
		Draw.setAlpha(alphaBefore * amount);
		try {
			if (simple()) {
				drawSimple(ui);
				return;
			}
			int width = cardWidth();
			int height = panelHeight();
			drawGlass(ui, width, height, PanelKit.RADIUS);
			float millis = spawnMoment.millis();
			if (millis >= 0f && !questView) {
				// the thin red border of the boss spawning: it fades in, then settles away
				float border = millis < BORDER_FULL ? PanelMoment.stage(millis, BORDER_IN, BORDER_FULL) : 1f - PanelMoment.stage(millis, BORDER_FULL, SPAWN_MILLIS);
				ui.ring(0, 0, width, height, PanelKit.RADIUS, 1, Draw.withOpacity(BOSS_RED, 0.85f * border));
			}
			layout(ui, true);
		} finally {
			Draw.setAlpha(alphaBefore);
		}
	}

	/**
	 * Lays the card out from the top and draws it when asked; measuring and drawing take the same steps, so the height always fits.
	 *
	 * @return the card's height
	 */
	private float layout(Canvas ui, boolean draw) {
		SlayerBoss shown = questView ? null : last;
		boolean editing = HudEditor.isOpen();
		if (draw) {
			String meta = shown != null && (showTimer.get() || editing) && !shown.timer().isEmpty() ? shown.timer() : null;
			if (carrying()) {
				meta = carryMeta(carriedName(), meta == null ? "" : meta);
			}
			float before = Draw.alpha();
			familyHeader(ui, cardWidth(), "skull", PanelKit.TILE_SLAYER, "Slayer", meta, BOSS_RED, false);
			if (meta != null) {
				part("Timer", showTimer, cardWidth() - PanelKit.PAD - ui.widthScaled(meta, PanelKit.META), PanelKit.TOP, ui.widthScaled(meta, PanelKit.META),
					PanelKit.TILE);
			}
			Draw.setAlpha(before);
		}
		float y = contentTop();
		if (shown != null) {
			if (draw) {
				if (shown.phases() != null) {
					drawPhasedBoss(ui, shown, y);
				} else {
					drawBoss(ui, shown, y);
				}
			}
			y += shown.phases() != null ? PHASED_BLOCK : BOSS_BLOCK;
		} else {
			if (draw) {
				drawQuest(ui, y);
			}
			y += QUEST_BLOCK;
		}
		int tiles = tileCount();
		if (tiles > 0) {
			y += PanelKit.GAP;
			if (draw) {
				drawTiles(ui, y, tiles);
			}
			y += PanelKit.STAT;
		}
		if (nextBossShown()) {
			y += PanelKit.GAP;
			if (draw) {
				float before = Draw.alpha();
				Draw.setAlpha(before * HudEditor.partAlpha(showNextBoss.get()));
				drawNextBoss(ui, y);
				Draw.setAlpha(before);
				part("Next boss", showNextBoss, PanelKit.PAD, y, cardWidth() - 2 * PanelKit.PAD, NEXT_BLOCK);
			}
			y += NEXT_BLOCK;
		}
		return y + PanelKit.BOTTOM;
	}

	/** The boss: its name and tier in red with its phase, the health bar with the phase markers, and its health with the next phase. */
	private void drawBoss(Canvas ui, SlayerBoss shown, float top) {
		int width = cardWidth();
		float right = width - PanelKit.PAD;
		boolean editing = HudEditor.isOpen();
		float before = Draw.alpha();
		int phases = shown.thirds() ? 3 : 1;
		float health = Mth.clamp(shownHealth.value(), 0f, 1f);
		float millis = spawnMoment.millis();
		if (millis >= 0f) {
			// the health fills up from zero as the boss appears
			health *= Easing.easeOut(PanelMoment.stage(millis, 0f, FILL_END));
		}
		// a phase that asks for something (the beacon, the hits shield, demons, egg sacs, an attunement) shows instead of the health third
		boolean special = shown.phaseColor() != SystemColors.GREEN && !shown.phase().startsWith(PHASE_PREFIX);
		boolean thirds = phases > 1 && !special;
		String phaseText = thirds ? PHASE_PREFIX + phaseOf(health) + " of " + phases : shown.phase();
		float phaseWidth = 0f;
		if (showPhase.get() || editing) {
			Draw.setAlpha(before * HudEditor.partAlpha(showPhase.get()));
			phaseWidth = ui.widthScaled(phaseText, PanelKit.SMALL);
			ui.textAtScaled(phaseText, right - phaseWidth, top + 2f, PanelKit.SMALL, thirds ? HudTheme.secondary() : PanelKit.readable(shown.phaseColor()));
			Draw.setAlpha(before);
			part("Phase", showPhase, right - phaseWidth, top, phaseWidth, 10f);
		}
		ui.textAtScaledBold(ui.fit(shown.name(), Math.round((right - phaseWidth - 6f - PanelKit.PAD) / BOSS)), PanelKit.PAD, top, BOSS,
			PanelKit.readable(BOSS_RED));
		if (!showHealth.get() && !editing) {
			return;
		}
		Draw.setAlpha(before * HudEditor.partAlpha(showHealth.get()));
		float barY = top + 12f;
		float barWidth = right - PanelKit.PAD;
		ui.roundedSmooth(PanelKit.PAD, barY, barWidth, HEALTH_BAR, HEALTH_BAR / 2f, Draw.withOpacity(HudTheme.text(), 0.08f));
		if (health > 0.005f) {
			ui.softLight(PanelKit.PAD + barWidth * health / 2f, barY + HEALTH_BAR / 2f, barWidth * health / 2f + 4f, HEALTH_BAR, Draw.withOpacity(PanelKit.RED, 0.3f));
			ui.roundedSmooth(PanelKit.PAD, barY, Math.max(HEALTH_BAR, barWidth * health), HEALTH_BAR, HEALTH_BAR / 2f, PanelKit.RED);
		}
		if (phases > 1) {
			for (int i = 1; i < phases; i++) {
				float markerX = PanelKit.PAD + barWidth * i / phases;
				ui.roundedSmooth(markerX - 0.5f, barY - MARKER, 1f, HEALTH_BAR + 2 * MARKER, 0.5f, Draw.withOpacity(0xFFFFFFFF, 0.75f));
			}
		}
		if (shown.health() >= 0) {
			String now = PanelKit.compact(shown.health());
			ui.textAtScaledBold(now, PanelKit.PAD, barY + HEALTH_BAR + 3f, PanelKit.SMALL, HudTheme.text());
			String of = shown.maxHealth() > 0 ? " / " + PanelKit.compact(shown.maxHealth()) + " health" : " health";
			ui.textAtScaled(of, PanelKit.PAD + ui.boldWidthScaled(now, PanelKit.SMALL), barY + HEALTH_BAR + 3f, PanelKit.SMALL, HudTheme.secondary());
		}
		String next = thirds && phaseOf(health) < phases ? "next phase at " + Math.round(100f * (phases - phaseOf(health)) / phases) + " %"
			: shown.detail();
		if (!next.isEmpty()) {
			float nextWidth = ui.widthScaled(next, PanelKit.SMALL);
			ui.textAtScaled(next, right - nextWidth, barY + HEALTH_BAR + 3f, PanelKit.SMALL, HudTheme.secondary());
		}
		Draw.setAlpha(before);
		part("SlayerBoss health", showHealth, PanelKit.PAD, barY - 2f, barWidth, BOSS_BLOCK - 10f);
	}

	/** The soft purple of a hit shield: its phase's name, its pills and its bar. */
	static final int SHIELD = 0xFFC48CFF;
	/** A boss with phases has a row more under its title: the phase's name and the step pills. */
	private static final int PHASED_BLOCK = 38;
	private static final float PHASE_ROW = 11f;
	private static final float PHASED_BAR = 20f;
	/** The step pills: one per phase, the ones done full, the current one a little larger, the ones to come faint. */
	private static final float PILL_WIDTH = 9f;
	private static final float PILL_HEIGHT = 3f;
	private static final float PILL_GAP = 2f;
	private static final float PILL_DONE = 0.8f;
	private static final float PILL_TO_COME = 0.22f;
	private final Spring phaseBar = Spring.smooth(Spring.DEFAULT);
	private final Spring shieldBlend = Spring.smooth(Spring.DEFAULT);
	/** The phase's texts, made again only when the phase or the hits change. */
	private String stepText = "";
	private int stepFor = -1;
	private String simpleText = "";
	private int simpleFor = -1;
	private String hitsOf = "";
	private String hitsLeftText = "";
	private int hitsOfFor = -1;

	/**
	 * A boss with hit shields (the Voidgloom Seraph): its name with "Phase 3 of 6", under it "Hits" or "Damage" with a pill per phase (shields in
	 * the soft purple, damage in the boss red), and the bar of the phase: the shield's hits left, or this damage phase's health. The bar is always
	 * drawn; between phases it crosses over in color and fills to its new value.
	 */
	private void drawPhasedBoss(Canvas ui, SlayerBoss shown, float top) {
		VoidgloomPhases.View phases = shown.phases();
		int width = cardWidth();
		float right = width - PanelKit.PAD;
		boolean editing = HudEditor.isOpen();
		float before = Draw.alpha();
		int key = phases.step() * 1000 + phases.steps();
		if (key != stepFor) {
			stepFor = key;
			stepText = phases.steps() > 0 ? PHASE_PREFIX + phases.step() + " of " + phases.steps() : PHASE_PREFIX + phases.step();
		}
		float stepWidth = 0f;
		boolean phaseShown = showPhase.get() || editing;
		if (phaseShown) {
			Draw.setAlpha(before * HudEditor.partAlpha(showPhase.get()));
			stepWidth = ui.widthScaled(stepText, PanelKit.SMALL);
			ui.textAtScaled(stepText, right - stepWidth, top + 2f, PanelKit.SMALL, HudTheme.secondary());
			// under the name: the phase's kind (or the beacon alert), and the pills of all phases on the right
			float rowY = top + PHASE_ROW;
			int kindColor = shown.phaseColor() == SystemColors.RED ? PanelKit.readable(SystemColors.RED) : phases.hits() ? SHIELD : BOSS_RED;
			ui.textAtScaled(shown.phase(), PanelKit.PAD, rowY, PanelKit.SMALL, kindColor);
			int count = phases.steps() > 0 ? phases.steps() : phases.step();
			float x = right - count * PILL_WIDTH - (count - 1) * PILL_GAP;
			for (int i = 1; i <= count; i++) {
				// the odd phases are the shields, the even ones the damage between them
				int color = i % 2 == 1 ? SHIELD : BOSS_RED;
				float y = rowY + 2f;
				if (i == phases.step()) {
					ui.roundedSmooth(x - 0.5f, y - 0.5f, PILL_WIDTH + 1f, PILL_HEIGHT + 1f, (PILL_HEIGHT + 1f) / 2f, color);
				} else {
					ui.roundedSmooth(x, y, PILL_WIDTH, PILL_HEIGHT, PILL_HEIGHT / 2f, Draw.withOpacity(color, i < phases.step() ? PILL_DONE : PILL_TO_COME));
				}
				x += PILL_WIDTH + PILL_GAP;
			}
			Draw.setAlpha(before);
			part("Phase", showPhase, PanelKit.PAD, top, right - PanelKit.PAD, PHASE_ROW + PILL_HEIGHT + 4f);
		}
		ui.textAtScaledBold(ui.fit(shown.name(), Math.round((right - stepWidth - 6f - PanelKit.PAD) / BOSS)), PanelKit.PAD, top, BOSS,
			PanelKit.readable(BOSS_RED));
		if (!showHealth.get() && !editing) {
			return;
		}
		Draw.setAlpha(before * HudEditor.partAlpha(showHealth.get()));
		float barY = top + PHASED_BAR;
		float barWidth = right - PanelKit.PAD;
		float fill = Mth.clamp(phaseBar.value(), 0f, 1f);
		float millis = spawnMoment.millis();
		if (millis >= 0f) {
			fill *= Easing.easeOut(PanelMoment.stage(millis, 0f, FILL_END));
		}
		int color = Draw.mix(PanelKit.RED, SHIELD, Mth.clamp(shieldBlend.value(), 0f, 1f));
		ui.roundedSmooth(PanelKit.PAD, barY, barWidth, HEALTH_BAR, HEALTH_BAR / 2f, Draw.withOpacity(HudTheme.text(), 0.08f));
		if (fill > 0.005f) {
			ui.softLight(PanelKit.PAD + barWidth * fill / 2f, barY + HEALTH_BAR / 2f, barWidth * fill / 2f + 4f, HEALTH_BAR, Draw.withOpacity(color, 0.3f));
			ui.roundedSmooth(PanelKit.PAD, barY, Math.max(HEALTH_BAR, barWidth * fill), HEALTH_BAR, HEALTH_BAR / 2f, color);
		}
		float textY = barY + HEALTH_BAR + 3f;
		if (phases.hits()) {
			int hitsKey = phases.hitsLeft() * 10_000 + phases.hitsTotal();
			if (hitsKey != hitsOfFor) {
				hitsOfFor = hitsKey;
				hitsLeftText = Integer.toString(phases.hitsLeft());
				hitsOf = " / " + phases.hitsTotal() + " hits";
			}
			ui.textAtScaledBold(hitsLeftText, PanelKit.PAD, textY, PanelKit.SMALL, HudTheme.text());
			ui.textAtScaled(hitsOf, PanelKit.PAD + ui.boldWidthScaled(hitsLeftText, PanelKit.SMALL), textY, PanelKit.SMALL, HudTheme.secondary());
		} else if (shown.health() >= 0) {
			String now = PanelKit.compact(shown.health());
			ui.textAtScaledBold(now, PanelKit.PAD, textY, PanelKit.SMALL, HudTheme.text());
			String of = shown.maxHealth() > 0 ? " / " + PanelKit.compact(shown.maxHealth()) + " health" : " health";
			ui.textAtScaled(of, PanelKit.PAD + ui.boldWidthScaled(now, PanelKit.SMALL), textY, PanelKit.SMALL, HudTheme.secondary());
		}
		if (!shown.detail().isEmpty()) {
			ui.textAtScaled(shown.detail(), right - ui.widthScaled(shown.detail(), PanelKit.SMALL), textY, PanelKit.SMALL, HudTheme.secondary());
		}
		Draw.setAlpha(before);
		part("SlayerBoss health", showHealth, PanelKit.PAD, barY - 2f, barWidth, PHASED_BLOCK - PHASED_BAR);
	}

	/** @return the simple row's value for a boss with phases: "3/6 Hits", or "3 Damage" while the count is not known */
	private String simpleValue(SlayerBoss shown) {
		VoidgloomPhases.View phases = shown.phases();
		int key = (phases.step() * 1000 + phases.steps()) * 2 + (phases.hits() ? 1 : 0);
		if (key != simpleFor) {
			simpleFor = key;
			simpleText = (phases.steps() > 0 ? phases.step() + "/" + phases.steps() : Integer.toString(phases.step())) + " " + (phases.hits() ? "Hits" : "Damage");
		}
		return simpleText;
	}

	/** @return the boss phase by its health: the first while above two thirds, then the second, then the third */
	private static int phaseOf(float health) {
		return health > 2f / 3f ? 1 : health > 1f / 3f ? 2 : 3;
	}

	/** Before the boss spawns: the quest's boss, quietly. */
	private void drawQuest(Canvas ui, float top) {
		String boss = SkyBlockArea.slayerBoss();
		ui.textAtScaled("Quest", PanelKit.PAD, top + 1f, PanelKit.META, HudTheme.secondary());
		float x = PanelKit.PAD + ui.widthScaled("Quest ", PanelKit.META);
		ui.textAtScaledBold(ui.fit(boss.isEmpty() ? "Slayer quest" : boss, Math.round((cardWidth() - PanelKit.PAD - x) / PanelKit.META)), x, top + 1f,
			PanelKit.META, PanelKit.readable(BOSS_RED));
	}

	/** @return how many stat tiles are shown: the chosen ones, and in the HUD editor all of them (faded when off) */
	private int tileCount() {
		// the player's own stats are left out while a carried boss is shown
		if (carrying()) {
			return 0;
		}
		boolean editing = HudEditor.isOpen();
		int count = 0;
		for (Toggle tile : tileSettings()) {
			if (tile.get() || editing) {
				count++;
			}
		}
		return count;
	}

	private List<Toggle> tileSettings() {
		return tiles;
	}

	/** A tile with nothing to show yet, in its usual brightness: the tile is not off, there just is no number. */
	private static final String NO_DATA = "–";

	/** Draws the stat tiles side by side: bosses per hour, the average kill, coins per hour and the session's total. */
	private void drawTiles(Canvas ui, float top, int count) {
		var profit = SkyBlockFeatures.SLAYER_PROFIT;
		var bucket = profit == null ? null : profit.slayerSession();
		boolean sampleShown = HudEditor.isOpen() && SlayerBosses.shown() == null;
		float width = (cardWidth() - 2f * PanelKit.PAD - (count - 1) * TILE_GAP) / count;
		float x = PanelKit.PAD;
		boolean editing = HudEditor.isOpen();
		float before = Draw.alpha();
		for (Toggle tile : tiles) {
			if (!tile.get() && !editing) {
				continue;
			}
			String value;
			String label;
			if (tile == showBossesRate) {
				label = "Bosses/h";
				value = sampleShown ? "18.4" : bucket == null || bucket.events == 0 || bucket.activeMillis < 60_000L ? NO_DATA
					: PanelKit.decimals(bucket.eventsPerHour(), 1);
			} else if (tile == showAvgKill) {
				label = "Avg kill";
				value = sampleShown ? "0:41" : kills == 0 ? NO_DATA : PanelKit.clock(Math.round(killMillis / (double) kills / 1000.0));
			} else if (tile == showCoinsRate) {
				label = "Coins/h";
				value = sampleShown ? "5.33M" : bucket == null || bucket.activeMillis < 10_000L ? NO_DATA : PanelKit.compact(bucket.perHour(profit.priceMode()));
			} else {
				label = "Session";
				value = sampleShown ? "4.82M" : bucket == null ? NO_DATA : PanelKit.compact(bucket.total(profit.priceMode()));
			}
			Draw.setAlpha(before * HudEditor.partAlpha(tile.get()));
			PanelKit.stat(ui, x, top, width, label, value);
			Draw.setAlpha(before);
			part(label, tile, x, top, width, PanelKit.STAT);
			x += width + TILE_GAP;
		}
	}

	/** @return the quest's progress toward the boss as read from the scoreboard; while the editor shows a sample without a quest, a sample */
	private SlayerProgress questProgress() {
		if (HudEditor.isOpen() && !SkyBlockArea.slayerQuest()) {
			return SAMPLE_PROGRESS;
		}
		String line = SkyBlockArea.slayerProgress();
		if (!line.equals(progressRead)) {
			progressRead = line;
			progress = SlayerProgress.of(line);
			if (progress.state() == SlayerProgress.State.UNKNOWN && !line.equals(unknownNoted)) {
				unknownNoted = line;
				Debug.log("A slayer quest line that is not known: {}", line);
			}
		}
		return progress;
	}

	private static final SlayerProgress SAMPLE_PROGRESS = SlayerProgress.of("(1,840/2,400) Combat XP");
	private String progressRead = "";
	private SlayerProgress progress = SlayerProgress.of("");
	private String unknownNoted = "";

	private boolean nextBossShown() {
		return !carrying() && (showNextBoss.get() || HudEditor.isOpen()) && !questProgress().text().isEmpty();
	}

	/** The next boss: the quest's progress toward spawning it, in the unit the scoreboard counts it in, or that the boss is up or slain, under a thin line. */
	private void drawNextBoss(Canvas ui, float top) {
		SlayerProgress now = questProgress();
		PanelKit.divider(ui, cardWidth(), top);
		float y = top + 5f;
		ui.textAtScaled("Next boss", PanelKit.PAD, y, PanelKit.SMALL, HudTheme.secondary());
		String text = now.text();
		float width = ui.widthScaled(text, PanelKit.SMALL);
		ui.textAtScaled(text, cardWidth() - PanelKit.PAD - width, y, PanelKit.SMALL, HudTheme.secondary());
		PanelKit.bar(ui, PanelKit.PAD, y + 9f, cardWidth() - 2f * PanelKit.PAD, PanelKit.BAR, now.counting() ? now.share() : 1f, PanelKit.ORANGE);
	}

	/** One simple row: the boss with its health and time left, or the quest with its progress. */
	private void drawSimple(Canvas ui) {
		SlayerBoss shown = questView ? null : last;
		if (shown != null && shown.phases() != null) {
			// the current phase's bar in its color, with "3/6 Hits"
			int color = Draw.mix(PanelKit.RED, SHIELD, Mth.clamp(shieldBlend.value(), 0f, 1f));
			PanelKit.simpleRow(ui, cardWidth(), 0f, hasBackground(), "skull", PanelKit.TILE_SLAYER, shown.name(),
				showHealth.get() ? Mth.clamp(phaseBar.value(), 0f, 1f) : -1f, color, simpleValue(shown), shown.phases().hits() ? SHIELD : BOSS_RED);
			return;
		}
		if (shown != null) {
			float health = Mth.clamp(shownHealth.value(), 0f, 1f);
			String value = showTimer.get() && !shown.timer().isEmpty() ? shown.timer() : Math.round(health * 100f) + "%";
			PanelKit.simpleRow(ui, cardWidth(), 0f, hasBackground(), "skull", PanelKit.TILE_SLAYER, shown.name(), showHealth.get() ? health : -1f,
				PanelKit.RED, value, BOSS_RED);
			return;
		}
		SlayerProgress now = questProgress();
		PanelKit.simpleRow(ui, cardWidth(), 0f, hasBackground(), "skull", PanelKit.TILE_SLAYER, "Next boss", now.counting() ? now.share() : -1f,
			PanelKit.ORANGE, now.text(), 0xFFFFFFFF);
	}
}
