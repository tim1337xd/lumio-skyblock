package dev.tim1337.lumio.skyblock.farming;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.hud.HudEditor;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.feature.SettingGroup;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.api.experimental.ui.Toasts;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;
import dev.tim1337.lumio.skyblock.data.ProfitTracker;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;
import dev.tim1337.lumio.skyblock.hud.PanelLooks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Garden pests for Hypixel SkyBlock: how many are alive and on which plots, and when the next ones can spawn, like SkyHanni. The cooldown is read
 * from the "Pests" player list widget (enabled via /widgets), or a custom cooldown is used, since the widget is wrong for a while after changing
 * armor. Shortly before it ends, a notification and/or a sound is played, as configured. Every line can be hidden, and the whole panel can be a
 * slim cooldown bar instead.
 */
public class PestHudFeature extends AddonPanel {
	// pests widget lines, parsed like SkyHanni
	private static final Pattern ALIVE = Pattern.compile("^Alive:\\s*(\\d+)");
	private static final Pattern COOLDOWN = Pattern.compile("^Cooldown:\\s*(?:(\\d{1,2})m)?\\s*(?:(\\d{1,2})s)?\\s*(READY|MAX PESTS)?", Pattern.CASE_INSENSITIVE);
	private static final Pattern PLOTS = Pattern.compile("^(?:Infested )?Plots?:\\s*(.+)$");
	// pest spawn chat messages, matched after stripping color codes
	private static final Pattern ONE_PEST = Pattern.compile("A Pest has appeared in Plot - ?(\\S+?)!?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern MANY_PESTS = Pattern.compile("(\\d+) Pests have spawned in Plot - ?(\\S+?)!?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern OFFLINE_PESTS = Pattern.compile("While you were offline, (\\d+) Pests? spawned", Pattern.CASE_INSENSITIVE);
	private static final int MAX_PESTS = 8;

	private final Choice<PanelLooks.Pests> look = add(make().choice("look", Component.literal("Style"), PanelLooks.Pests.MAP, PanelLooks.Pests::displayName,
		Choice.Style.SEGMENTED), "General");
	private final Toggle showPlots = add(make().toggle("show_plots", Component.literal("Plots"), true), "General");
	private final Toggle showCooldown = add(make().toggle("show_cooldown", Component.literal("Next pest"), true), "General");
	private final Toggle showSpray = add(make().toggle("show_spray", Component.literal("Spray"), true), "General");
	private final Toggle showLast = add(make().toggle("show_last", Component.literal("Last spawn"), false), "General");
	private final Toggle showVisitors = add(make().toggle("show_visitors", Component.literal("Visitors"), true), "General");
	private final Slider customCooldown = add(make().slider("custom_cooldown", Component.literal("Custom cooldown"), 0f, 0f, 900f, 0), "Alerts",
		"In seconds. 0 reads it from the tab list");
	private final Toggle alert = add(make().toggle("alert", Component.literal("Cooldown alert"), true), "Alerts",
		"Before pests can spawn");
	private final Toggle spawnAlert = add(make().toggle("spawn_alert", Component.literal("Spawn alert"), true), "Alerts",
		"With the plot and how many");
	private final Slider alertSeconds = add(make().slider("alert_seconds", Component.literal("Alert time"), 5f, 0f, 30f, 0), "Alerts");
	private final Toggle alertSound = add(make().toggle("alert_sound", Component.literal("Sound"), true), "Alerts");
	private final Slider alertVolume = add(make().slider("alert_volume", Component.literal("Volume"), 0.8f, 0.1f, 1f, 2).percent(), "Alerts");

	private final Spring shownAmount = Spring.bouncy(Spring.GENTLE);
	private final Spring ringFill = Spring.smooth(Spring.GENTLE);
	private boolean widgetSeen;
	private long widgetSeenAt;
	private int alive;
	private String plots = "";
	private boolean ready;
	private boolean maxed;
	/** When the cooldown ends, or 0 if unknown. */
	private long cooldownEnd;
	/** The longest cooldown seen since the last spawn, at which the ring is full. */
	private long cooldownLength;
	private long lastSpawn;
	private final Deque<Long> spawnGaps = new ArrayDeque<>();
	private boolean alerted;
	/** The cooldown the player list showed last, to detect it restarting. */
	private long lastListed = -1;
	/** The alive count at the last player list read, to detect new spawns; -1 if unknown. */
	private int lastAliveCount = -1;

	public PestHudFeature() {
		super("pest_hud", "Pests", "The pests of the Garden: how many are alive, on which plots, and when the next ones can come, with a notice before.", true,
			Anchor.LEFT, false, 200f, 120f);
		offerCardSize();
		iconRgb(SystemColors.GREEN_RGB);
		iconName("bug");
		summary("Garden pests: how many, where, and when the next come");
		offerNoBackground();
		alertSeconds.under(alert);
		alertSound.under(alert);
		alertVolume.visibleWhen(() -> alert.get() && alertSound.get());
		offerModes(look);
		offerHeader();
	}

	private boolean allowedHere() {
		return SkyBlock.allowed();
	}

	/** @return the custom cooldown in milliseconds, or 0 when the player list's cooldown is used */
	private long ownCooldown() {
		return Math.round(customCooldown.get()) * 1000L;
	}

	/** When the spawn notification was last shown: the chat message and the player list both report the same pests, which is one notification. */
	private long spawnNoticeAt;

	/** Shows the spawn notification with count and plot when known, once per spawn. */
	private void spawned(String count, String plot) {
		long now = System.currentTimeMillis();
		if (!spawnAlert.get() || now - spawnNoticeAt < 10_000L || !SkyBlockArea.inGarden()) {
			return;
		}
		spawnNoticeAt = now;
		boolean one = count.equals("1");
		String what = count.isEmpty() ? "Pests have spawned" : one ? "1 pest has spawned" : count + " pests have spawned";
		Toasts.info("Pests", what + (plot.isEmpty() ? "" : " in plot " + plot));
	}

	/** Handles a chat message about spawned pests. The server embeds color codes in the text, which are stripped first. */
	public void onMessage(Component message) {
		String text = TextColors.noColors(message.getString()).strip();
		Matcher one = ONE_PEST.matcher(text);
		Matcher many = MANY_PESTS.matcher(text);
		Matcher offline = OFFLINE_PESTS.matcher(text);
		if (!one.find() && !many.find() && !offline.find()) {
			return;
		}
		long now = System.currentTimeMillis();
		if (!offline.find(0)) {
			// count and plot, as stated in the message
			boolean single = one.find(0);
			String count = single ? "1" : many.find(0) ? many.group(1) : "";
			String plot = single ? one.group(1) : many.find(0) ? many.group(2) : "";
			spawned(count, plot);
		}
		if (lastSpawn != 0 && now - lastSpawn < 30 * 60_000L) {
			spawnGaps.addLast(now - lastSpawn);
			while (spawnGaps.size() > 10) {
				spawnGaps.removeFirst();
			}
		}
		lastSpawn = now;
		alerted = false;
		cooldownLength = 0;
		long own = ownCooldown();
		if (own > 0) {
			// the custom cooldown starts when pests spawn
			cooldownEnd = now + own;
			cooldownLength = own;
			ready = false;
		}
	}

	/** The player list read last; the same list is not parsed again (it refreshes once per second, while this runs every frame). */
	private List<String> readTab;

	/** Parses the pests widget; the time is only adopted when it differs from the counted one, so it does not jump every second. */
	private void readWidget() {
		List<String> tab = SkyBlockArea.tab();
		if (tab == readTab) {
			return;
		}
		readTab = tab;
		boolean inBox = false;
		boolean found = false;
		boolean plotsFound = false;
		long now = System.currentTimeMillis();
		long own = ownCooldown();
		for (String line : tab) {
			String plain = line.strip();
			if (plain.equalsIgnoreCase("Pests:")) {
				inBox = true;
				found = true;
				continue;
			}
			if (!inBox) {
				continue;
			}
			if (plain.isEmpty() || plain.endsWith(":") && !plain.startsWith("Cooldown")) {
				inBox = false;
				continue;
			}
			Matcher matcher = ALIVE.matcher(plain);
			if (matcher.find()) {
				alive = Integer.parseInt(matcher.group(1));
				maxed = alive >= MAX_PESTS;
				// more alive than before means pests spawned, even if the chat message was missed: the custom cooldown starts here
				if (lastAliveCount >= 0 && alive > lastAliveCount) {
					if (now - lastSpawn > 5000L) {
						lastSpawn = now;
						alerted = false;
					}
					// the notification, in case the chat message was missed (shown once per spawn)
					spawned(Integer.toString(alive - lastAliveCount), "");
				}
				lastAliveCount = alive;
				continue;
			}
			matcher = COOLDOWN.matcher(plain);
			if (matcher.find()) {
				// a cooldown restarting in the player list (much higher than before) means pests spawned, even if the message was missed
				if (matcher.group(3) == null && (matcher.group(1) != null || matcher.group(2) != null)) {
					long listed = ((matcher.group(1) == null ? 0 : Long.parseLong(matcher.group(1))) * 60 + (matcher.group(2) == null ? 0 : Long.parseLong(matcher.group(2)))) * 1000L;
					if (lastListed >= 0 && listed > lastListed + 30_000L && now - lastSpawn > 20_000L) {
						lastSpawn = now;
						alerted = false;
					}
					lastListed = listed;
				}
				if (own > 0) {
					// the custom cooldown from the last spawn: recomputed every time so setting changes apply immediately. The player list time is
					// never used then; until pests have spawned once, it is unknown
					if (lastSpawn != 0) {
						cooldownEnd = lastSpawn + own;
						cooldownLength = own;
						ready = now >= cooldownEnd;
					} else {
						cooldownEnd = 0;
						ready = matcher.group(3) != null && matcher.group(3).equalsIgnoreCase("READY");
					}
					continue;
				}
				String state = matcher.group(3);
				if (state != null) {
					ready = state.equalsIgnoreCase("READY");
					maxed = maxed || state.toUpperCase(Locale.ROOT).startsWith("MAX");
					cooldownEnd = 0;
				} else if (matcher.group(1) != null || matcher.group(2) != null) {
					ready = false;
					// the list truncates seconds: "10s" means anything up to 11 seconds, so add the missing second, as SkyHanni does
					long left = ((matcher.group(1) == null ? 0 : Long.parseLong(matcher.group(1))) * 60 + (matcher.group(2) == null ? 0 : Long.parseLong(matcher.group(2)))) * 1000L + 999L;
					long counted = cooldownEnd == 0 ? -1 : cooldownEnd - now;
					// The list only refreshes every few seconds, so it always shows slightly more than what is left: keep the counted time and only adopt
					// the list's value when it is clearly lower (the cooldown got shorter) or much higher (it got longer).
					if (counted < 0 || left < counted - 1500 || left > counted + 8000) {
						cooldownEnd = now + left;
						if (left > cooldownLength) {
							cooldownLength = left;
						}
						if (left > alertSeconds.get() * 1000f + 1000) {
							alerted = false;
						}
					}
				}
				continue;
			}
			matcher = PLOTS.matcher(plain);
			if (matcher.find()) {
				plots = matcher.group(1).strip();
				plotsFound = true;
			}
		}
		if (found) {
			widgetSeen = true;
			widgetSeenAt = now;
			// the widget lists no plots once the pests are gone: forget the previous plots
			if (!plotsFound || alive == 0) {
				plots = "";
			}
		} else if (now - widgetSeenAt > 5000) {
			alive = 0;
			plots = "";
		}
		// the custom cooldown counts from the last spawn, also without the widget
		if (own > 0 && lastSpawn != 0) {
			cooldownEnd = lastSpawn + own;
			cooldownLength = own;
			ready = now >= cooldownEnd;
		}
	}

	/** Called every tick: parses the widget and shows the notification when due. */
	public void tick(Minecraft client) {
		if (!isEnabled() || client.level == null || client.player == null || !allowedHere() || !SkyBlockArea.inGarden()) {
			return;
		}
		readWidget();
		if (!alert.get() || alerted || cooldownEnd == 0 || maxed) {
			return;
		}
		long left = cooldownEnd - System.currentTimeMillis();
		if (left <= alertSeconds.get() * 1000f && left > -2000) {
			alerted = true;
			int seconds = Math.max(0, (int) Math.ceil(left / 1000.0));
			Toasts.info("Pests", seconds <= 0 ? "Pests can spawn now" : "Pests can spawn in " + seconds + (seconds == 1 ? " second" : " seconds"));
			if (alertSound.get()) {
				client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, alertVolume.get()));
				client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.2f, alertVolume.get() * 0.7f));
			}
		}
	}

	private boolean wanted() {
		if (!allowedHere()) {
			return false;
		}
		// auto-detected: in the Garden, where pests spawn whether the player farms or not
		return SkyBlockArea.inGarden();
	}

	@Override
	public void prepare(Minecraft client) {
		shownAmount.follow(simulated() || wanted() ? 1f : 0f);
		long left = cooldownEnd == 0 ? 0 : Math.max(0, cooldownEnd - System.currentTimeMillis());
		// the slim bar stays full while there are pests to kill; the cooldown starting at the spawn is only shown once they are gone
		ringFill.follow(cooldownLength <= 0 || cooldownEnd == 0 ? (ready ? 1f : 0f) : 1f - left / (float) cooldownLength);
	}

	@Override
	public boolean shown(Minecraft client) {
		return shownAmount.value() > 0.01f;
	}

	@Override
	public boolean inEditorWhenHidden() {
		return false;
	}

	@Override
	public int panelWidth() {
		return cardWidth();
	}

	/** The plot map: its size and the gap between plots; the ring of the ring look. */
	private static final int MAP = 84;
	private static final float CELL_GAP = 2f;
	private static final int RING = 46;
	private static final float RING_THICKNESS = 3f;
	/** The Garden's plots on the map, row by row, as the Garden lays them out around the barn (0). */
	private static final int[] PLOT_GRID = {21, 13, 9, 14, 22, 15, 5, 1, 6, 16, 10, 2, 0, 3, 11, 17, 7, 4, 8, 18, 23, 19, 12, 20, 24};
	private static final int PLOT_COUNT = 24;
	/** The plot the player stands in, as the scoreboard names it: "Plot - 14". */
	private static final Pattern IN_PLOT = Pattern.compile("Plot\\s*-\\s*(\\d{1,2})");
	private static final Pattern NUMBER = Pattern.compile("\\d{1,2}");
	/** A spray lasts at most an hour: its bar is full then. */
	private static final long SPRAY_MILLIS = 60 * 60_000L;
	private static final int SPRAY_COLOR = 0xFF7FE3FF;

	private PanelLooks.Pests mode() {
		return look.get();
	}

	/** @return how many simple rows are shown: the pests, and the cooldown and spray when chosen */
	private int simpleRows() {
		return 1 + (showCooldown.get() ? 1 : 0) + (sprayShown() ? 1 : 0);
	}

	@Override
	public int panelHeight() {
		return switch (mode()) {
			case MAP -> contentTop() + MAP + PanelKit.BOTTOM;
			case RING -> contentTop() + RING + PanelKit.BOTTOM;
			case SIMPLE -> PanelKit.simpleHeight(simpleRows()) + (showPlots.get() && !plots.isEmpty() ? PLOTS_LINE : 0);
		};
	}

	/** The line of plots under the pests row of the simple look. */
	private static final int PLOTS_LINE = 10;

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		float amount = Mth.clamp(shownAmount.value(), 0f, 1f);
		float alphaBefore = Draw.alpha();
		Draw.setAlpha(alphaBefore * amount);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(0f, (1f - amount) * -8f);
		pose.translate(panelWidth() / 2f, 0f);
		pose.scale(0.94f + 0.06f * amount, 0.94f + 0.06f * amount);
		pose.translate(-panelWidth() / 2f, 0f);
		try {
			readPlots();
			if (mode() == PanelLooks.Pests.SIMPLE) {
				drawSimple(ui);
				return;
			}
			int width = cardWidth();
			drawGlass(ui, width, panelHeight(), PanelKit.RADIUS);
			familyHeader(ui, width, "bug", PanelKit.TILE_GARDEN, "Pests", null, 0, false);
			drawSprayTag(ui, width);
			if (!widgetSeen && ownCooldown() <= 0 && !sampleShown()) {
				ui.textAtScaledBold("Pests widget missing", PanelKit.PAD, contentTop(), PanelKit.META, PanelKit.readable(PanelKit.RED_TEXT));
				ui.textAtScaled("Turn it on with /widgets", PanelKit.PAD, contentTop() + 11f, PanelKit.SMALL, HudTheme.secondary());
				return;
			}
			if (mode() == PanelLooks.Pests.MAP) {
				drawMap(ui, PanelKit.PAD, contentTop());
				drawFacts(ui, PanelKit.PAD + MAP + 10f, contentTop(), MAP);
			} else {
				drawRing(ui, graphics, contentTop());
				drawFacts(ui, PanelKit.PAD + RING + 10f, contentTop(), RING);
			}
		} finally {
			pose.popMatrix();
			Draw.setAlpha(alphaBefore);
		}
	}

	// ------------------------------------------------------------------------------------------------------- state

	/** @return the cooldown formatted like SkyHanni: "1m 7s", and "45s" in the last minute. Counted down locally, so it runs smoothly between list updates. */
	private static String clock(long millis) {
		// truncate to whole seconds like the list and SkyHanni; the counted time includes the second the list cuts off
		long seconds = Math.max(0L, millis) / 1000L;
		long minutes = seconds / 60L;
		return minutes > 0 ? minutes + "m " + seconds % 60L + "s" : seconds + "s";
	}

	/** @return the cooldown color: red when there are many pests, green when they can spawn, yellow just before, the accent while running */
	private int stateColor(boolean canCome, boolean soon) {
		return maxed ? SystemColors.RED_TEXT : canCome ? SystemColors.GREEN : soon ? SystemColors.YELLOW : accentArgb();
	}

	/** @return the cooldown's state as a short text: "Max", "Ready", the time left, or "--:--" when not known */
	private String cooldownText(long now) {
		if (sampleShown()) {
			return SAMPLE_COOLDOWN;
		}
		long left = cooldownEnd == 0 ? 0 : cooldownEnd - now;
		boolean canCome = ready || (cooldownEnd != 0 && left <= 0);
		return maxed ? "Max pests" : canCome ? "Ready" : cooldownEnd == 0 ? "--:--" : clock(left);
	}

	/** The plots read from the widget as a set, and the plot the player stands in; read again only when their texts change. */
	private final boolean[] infested = new boolean[PLOT_COUNT + 1];
	private int infestedCount;
	private String plotsRead = "";
	private String plotsLine = "";
	private int ownPlot;
	private String zoneRead = "";

	private void readPlots() {
		String shownPlots = sampleShown() ? SAMPLE_PLOTS : plots;
		if (!shownPlots.equals(plotsRead)) {
			plotsRead = shownPlots;
			Arrays.fill(infested, false);
			infestedCount = 0;
			StringBuilder line = new StringBuilder();
			Matcher number = NUMBER.matcher(shownPlots);
			while (number.find()) {
				int plot = Integer.parseInt(number.group());
				if (plot >= 1 && plot <= PLOT_COUNT && !infested[plot]) {
					infested[plot] = true;
					infestedCount++;
					line.append(line.isEmpty() ? "" : " · ").append(plot);
				}
			}
			plotsLine = line.toString();
		}
		String zone = SkyBlockArea.zone();
		if (!zone.equals(zoneRead)) {
			zoneRead = zone;
			Matcher in = IN_PLOT.matcher(zone);
			ownPlot = in.find() ? Integer.parseInt(in.group(1)) : 0;
		}
	}

	/** The sample in the HUD editor while the Garden is simulated and no widget was read. */
	private static final String SAMPLE_PLOTS = "2, 6, 14, 19";
	private static final int SAMPLE_ALIVE = 7;
	private static final String SAMPLE_COOLDOWN = "1m 12s";
	private static final float SAMPLE_FILL = 0.6f;

	/** @return how far the cooldown has run, from 0 to 1 (1: pests can come) */
	private float fill() {
		return sampleShown() ? SAMPLE_FILL : Mth.clamp(ringFill.value(), 0f, 1f);
	}

	private boolean sampleShown() {
		return simulated() && !widgetSeen;
	}

	private int aliveShown() {
		return sampleShown() ? SAMPLE_ALIVE : alive;
	}

	/** @return the plot spray's time left in milliseconds, or -1 when not known (the farming panel reads it while it is on) */
	private long sprayLeft() {
		if (sampleShown()) {
			return 12 * 60_000L + 40_000L;
		}
		var farming = SkyBlockFeatures.FARMING_HUD;
		long end = farming == null || !farming.isEnabled() ? 0 : farming.sprayEnd();
		return end == 0 ? -1 : Math.max(0, end - System.currentTimeMillis());
	}

	private boolean sprayShown() {
		return (showSpray.get() || HudEditor.isOpen()) && sprayLeft() >= 0;
	}

	/** The spray's time and the visitors, made again only when they change. */
	private long sprayShownSeconds = -1;
	private String sprayTime = "";
	private String sprayTag = "";

	private String sprayTime(long left) {
		long seconds = left / 1000L;
		if (seconds != sprayShownSeconds) {
			sprayShownSeconds = seconds;
			sprayTime = PanelKit.clock(seconds);
			sprayTag = "Spray " + sprayTime;
		}
		return sprayTime;
	}

	/** The spray as a tag at the right of the header; in the HUD editor the selected panel shows its modes there instead. */
	private void drawSprayTag(Canvas ui, int width) {
		if (selectedInEditor() || !sprayShown()) {
			return;
		}
		sprayTime(sprayLeft());
		float before = Draw.alpha();
		Draw.setAlpha(before * HudEditor.partAlpha(showSpray.get()));
		float tagWidth = PanelKit.tagWidth(ui, sprayTag);
		float x = width - PanelKit.PAD - tagWidth;
		PanelKit.tag(ui, sprayTag, x, PanelKit.TOP + (PanelKit.TILE - PanelKit.TAG) / 2f, SPRAY_COLOR);
		Draw.setAlpha(before);
		part("Spray", showSpray, x, PanelKit.TOP, tagWidth, PanelKit.TILE);
	}

	// ------------------------------------------------------------------------------------------------------- the looks

	/** Draws the Garden's plots around the barn: infested ones in red, the one the player stands in framed in the accent. */
	private void drawMap(Canvas ui, float left, float top) {
		float cell = (MAP - 4 * CELL_GAP) / 5f;
		for (int i = 0; i < PLOT_GRID.length; i++) {
			int plot = PLOT_GRID[i];
			float x = left + (i % 5) * (cell + CELL_GAP);
			float y = top + (i / 5) * (cell + CELL_GAP);
			if (plot == 0) {
				ui.roundedGradient(Math.round(x), Math.round(y), Math.round(cell), Math.round(cell), 3f, 0xFF6B4A2A, 0xFF4A321C);
				float barnWidth = ui.widthScaled("BARN", BARN_TEXT);
				ui.textAtScaledBold("BARN", x + (cell - barnWidth) / 2f, y + cell / 2f - 2.2f, BARN_TEXT, 0xB3FFFFFF);
				continue;
			}
			boolean pests = infested[plot];
			boolean own = plot == ownPlot;
			if (pests) {
				ui.softLight(x + cell / 2f, y + cell / 2f, cell * 0.75f, Draw.withOpacity(PanelKit.RED, 0.35f));
				ui.roundedSmooth(x, y, cell, cell, 3f, Draw.withOpacity(PanelKit.RED, 0.22f));
				ui.ring(Math.round(x), Math.round(y), Math.round(cell), Math.round(cell), 3f, 1, Draw.withOpacity(0xFFFF5A4E, 0.85f));
			} else {
				ui.roundedSmooth(x, y, cell, cell, 3f, own ? Draw.withOpacity(PanelKit.GREEN, 0.2f) : 0x2162BE5A);
				ui.ring(Math.round(x), Math.round(y), Math.round(cell), Math.round(cell), 3f, 1, own ? accentArgb() : Draw.withOpacity(HudTheme.text(), 0.06f));
			}
		}
	}

	private static final float BARN_TEXT = 0.55f;

	/** Draws the ring: the cooldown filling up until pests can come, with the pest count in its middle. */
	private void drawRing(Canvas ui, GuiGraphicsExtractor graphics, float top) {
		long now = System.currentTimeMillis();
		long left = cooldownEnd == 0 ? 0 : cooldownEnd - now;
		boolean soon = cooldownEnd != 0 && left <= alertSeconds.get() * 1000f;
		boolean canCome = ready || (cooldownEnd != 0 && left <= 0);
		// near the end the ring pulses, so it catches the eye
		float pulse = soon && !canCome ? 0.75f + 0.25f * (float) Math.sin(now / 160.0) : 1f;
		float centerX = PanelKit.PAD + RING / 2f;
		float centerY = top + RING / 2f;
		PanelKit.ring(graphics, ui, centerX, centerY, RING, RING_THICKNESS, fill(),
			Draw.withOpacity(stateColor(canCome, soon), pulse), 0f);
		String count = Integer.toString(aliveShown());
		float countWidth = ui.boldWidthScaled(count, PanelKit.HERO_MEDIUM);
		ui.textAtScaledBold(count, centerX - countWidth / 2f, centerY - 9f, PanelKit.HERO_MEDIUM, aliveShown() > 0 ? PanelKit.readable(PanelKit.RED_TEXT)
			: HudTheme.text());
		float wordWidth = ui.widthScaled("pests", PanelKit.SMALL);
		ui.textAtScaled("pests", centerX - wordWidth / 2f, centerY + 4f, PanelKit.SMALL, HudTheme.secondary());
	}

	/**
	 * The facts beside the map or ring, spread over its height: on the map the pest count as the hero; the next pest's time, the plots, the
	 * last spawn and the visitors, as chosen.
	 */
	private void drawFacts(Canvas ui, float x, float top, float height) {
		float room = cardWidth() - PanelKit.PAD - x;
		float y = top;
		boolean editing = HudEditor.isOpen();
		float before = Draw.alpha();
		if (mode() == PanelLooks.Pests.MAP) {
			ui.textAtScaled("Pests", x, y, PanelKit.SMALL, HudTheme.secondary());
			String count = Integer.toString(aliveShown());
			ui.textAtScaledBold(count, x, y + 8f, PanelKit.HERO_MEDIUM, aliveShown() > 0 ? PanelKit.readable(PanelKit.RED_TEXT) : HudTheme.text());
			if (infestedCount > 0) {
				String in = "in " + infestedCount + (infestedCount == 1 ? " plot" : " plots");
				ui.textAtScaled(in, x, y + 21f, PanelKit.SMALL, HudTheme.secondary());
			}
			y += 32f;
		}
		if (showCooldown.get() || editing) {
			Draw.setAlpha(before * HudEditor.partAlpha(showCooldown.get()));
			ui.textAtScaled("Next pest can spawn in", x, y, PanelKit.SMALL, HudTheme.secondary());
			ui.textAtScaledBold(cooldownText(System.currentTimeMillis()), x, y + 8f, TextScale.BODY, maxed ? PanelKit.readable(PanelKit.RED_TEXT)
				: HudTheme.text());
			Draw.setAlpha(before);
			part("Cooldown", showCooldown, x, y, room, 18f);
			y += 21f;
		}
		if ((showPlots.get() || editing) && !plotsLine.isEmpty() && mode() == PanelLooks.Pests.RING) {
			Draw.setAlpha(before * HudEditor.partAlpha(showPlots.get()));
			ui.textAtScaled("Plots", x, y, PanelKit.SMALL, HudTheme.secondary());
			ui.textAtScaledBold(ui.fit(plotsLine, Math.round((room - 22f) / PanelKit.SMALL)), x + 22f, y, PanelKit.SMALL, HudTheme.text());
			Draw.setAlpha(before);
			part("Plots", showPlots, x, y - 1f, room, 9f);
			y += 11f;
		}
		if ((showLast.get() || editing) && y + 8f <= top + height) {
			Draw.setAlpha(before * HudEditor.partAlpha(showLast.get()));
			ui.textAtScaled(ui.fit(lastLine(), Math.round(room / PanelKit.SMALL)), x, y, PanelKit.SMALL, HudTheme.secondary());
			Draw.setAlpha(before);
			part("Last spawn", showLast, x, y - 1f, room, 9f);
			y += 11f;
		}
		int visitors = visitorCount();
		if ((showVisitors.get() || editing) && visitors >= 0 && y + 8f <= top + height) {
			Draw.setAlpha(before * HudEditor.partAlpha(showVisitors.get()));
			ui.textAtScaled("Visitors " + visitors, x, y, PanelKit.SMALL, HudTheme.secondary());
			Draw.setAlpha(before);
			part("Visitors", showVisitors, x, y - 1f, room, 9f);
		}
	}

	/** @return the Garden's visitors from the visitor panel, or -1 when it does not know them */
	private int visitorCount() {
		if (sampleShown()) {
			return 3;
		}
		var visitors = SkyBlockFeatures.VISITORS;
		return visitors == null || !visitors.isEnabled() ? -1 : visitors.visitorCount();
	}

	/** @return when pests last spawned and how often they do: "Last 2m ago · every 4m" */
	private String lastLine() {
		long now = System.currentTimeMillis();
		if (lastSpawn == 0) {
			return "Last not seen yet";
		}
		String last = "Last " + ProfitTracker.time(now - lastSpawn) + " ago";
		if (!spawnGaps.isEmpty()) {
			long sum = 0;
			for (long gap : spawnGaps) {
				sum += gap;
			}
			last += " · every " + ProfitTracker.time(sum / spawnGaps.size());
		}
		return last;
	}

	/** The simple look: a row for the pests with the plots under it, the next pest's time and the spray, as chosen. */
	private void drawSimple(Canvas ui) {
		int width = cardWidth();
		boolean glass = hasBackground();
		float y = 0f;
		int count = aliveShown();
		PanelKit.simpleRow(ui, width, y, glass, "bug", PanelKit.TILE_SLAYER, "Pests", count / (float) MAX_PESTS, PanelKit.RED, Integer.toString(count),
			count > 0 ? PanelKit.RED_TEXT : 0xFFFFFFFF);
		y += PanelKit.SIMPLE_ROW + PanelKit.SIMPLE_GAP;
		if (showPlots.get() && !plotsLine.isEmpty()) {
			ui.textAtScaled(ui.fit("Plots " + plotsLine, Math.round((width - 8f) / PanelKit.SMALL)), 4f, y, PanelKit.SMALL, HudTheme.text());
			y += PLOTS_LINE;
		}
		if (showCooldown.get()) {
			long now = System.currentTimeMillis();
			long left = cooldownEnd == 0 ? 0 : cooldownEnd - now;
			boolean soon = cooldownEnd != 0 && left <= alertSeconds.get() * 1000f;
			boolean canCome = ready || (cooldownEnd != 0 && left <= 0);
			PanelKit.simpleRow(ui, width, y, glass, "timer", PanelKit.TILE_GARDEN, "Next pest", fill(), stateColor(canCome, soon),
				cooldownText(now), canCome ? PanelKit.GREEN_TEXT : 0xFFFFFFFF);
			y += PanelKit.SIMPLE_ROW + PanelKit.SIMPLE_GAP;
		}
		if (sprayShown()) {
			long left = sprayLeft();
			PanelKit.simpleRow(ui, width, y, glass, "drop", PanelKit.TILE_COMMISSIONS, "Spray", left / (float) SPRAY_MILLIS, SPRAY_COLOR, sprayTime(left),
				0xFFFFFFFF);
		}
	}
}
