package dev.tim1337.lumio.skyblock.farming;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.hud.HudEditor;
import dev.tim1337.lumio.api.feature.AddonPanel;
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

	private final Choice<PanelLooks.Pests> look = add(make().choice("look", Component.literal("Style"), PanelLooks.Pests.RING, PanelLooks.Pests::displayName,
		Choice.Style.SEGMENTED), "General");
	private final Toggle showPlots = add(make().toggle("show_plots", Component.literal("Plots"), true), "General");
	private final Toggle showCooldown = add(make().toggle("show_cooldown", Component.literal("Next pest"), true), "General");
	private final Toggle showLast = add(make().toggle("show_last", Component.literal("Last spawn"), false), "General", "Since the last pest, and the usual gap");
	private final Slider customCooldown = add(make().slider("custom_cooldown", Component.literal("Custom cooldown"), 0f, 0f, 900f, 0).unit(" s").offAtZero("Auto"), "Alerts",
		"Auto reads it from the tab list");
	private final Toggle alert = add(make().toggle("alert", Component.literal("Cooldown alert"), true), "Alerts",
		"Before pests can spawn");
	private final Toggle spawnAlert = add(make().toggle("spawn_alert", Component.literal("Spawn alert"), true), "Alerts",
		"With the plot and how many");
	private final Slider alertSeconds = add(make().slider("alert_seconds", Component.literal("Duration"), 5f, 0f, 30f, 0).unit(" s"), "Alerts");
	private final Toggle alertSound = add(make().toggle("alert_sound", Component.literal("Sound"), true), "Alerts");
	private final Slider alertVolume = add(make().slider("alert_volume", Component.literal("Volume"), 0.8f, 0.05f, 1f, 2).percent(), "Alerts");

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
		super("pest_hud", "Pests", "Garden pests: how many, where, when next.", true,
			Anchor.LEFT, false, 200f, 120f);
		offerCardSize();
		iconRgb(SystemColors.GREEN_RGB);
		iconName("bug");
		summary("Garden pests: how many, where, when next");
		offerNoBackground();
		alertSeconds.under(alert);
		alertSound.under(alert);
		alertVolume.visibleWhen(() -> alert.get() && alertSound.get());
		showLast.visibleWhen(() -> look.get() == PanelLooks.Pests.RING);
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

	/** The ring of the ring look. */
	private static final int RING = 46;
	private static final float RING_THICKNESS = 3f;
	private static final int PLOT_COUNT = 24;
	private static final Pattern NUMBER = Pattern.compile("\\d{1,2}");

	private PanelLooks.Pests mode() {
		return look.get();
	}


	@Override
	public int panelHeight() {
		return switch (mode()) {
			case RING -> contentTop() + RING + PanelKit.BOTTOM;
			case SIMPLE -> PanelKit.simpleHeight(1);
		};
	}

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
			if (!widgetSeen && ownCooldown() <= 0 && !sampleShown()) {
				ui.textAtScaledBold("Pests widget missing", PanelKit.PAD, contentTop(), PanelKit.META, PanelKit.readable(PanelKit.RED_TEXT));
				ui.textAtScaled("Turn it on with /widgets", PanelKit.PAD, contentTop() + 11f, PanelKit.SMALL, HudTheme.secondary());
				return;
			}
			drawRing(ui, graphics, contentTop());
			drawFacts(ui, PanelKit.PAD + RING + 10f, contentTop(), RING);
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

	/** The plots read from the widget, as a set and as a line; read again only when their text changes. */
	private final boolean[] infested = new boolean[PLOT_COUNT + 1];
	private String plotsRead = "";
	private String plotsLine = "";

	private void readPlots() {
		String shownPlots = sampleShown() ? SAMPLE_PLOTS : plots;
		if (!shownPlots.equals(plotsRead)) {
			plotsRead = shownPlots;
			Arrays.fill(infested, false);
			StringBuilder line = new StringBuilder();
			Matcher number = NUMBER.matcher(shownPlots);
			while (number.find()) {
				int plot = Integer.parseInt(number.group());
				if (plot >= 1 && plot <= PLOT_COUNT && !infested[plot]) {
					infested[plot] = true;
					line.append(line.isEmpty() ? "" : " · ").append(plot);
				}
			}
			plotsLine = line.toString();
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

	// ------------------------------------------------------------------------------------------------------- the looks

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

	/** The facts beside the ring, spread over its height: the next pest's time, the plots, the last spawn, as chosen. */
	private void drawFacts(Canvas ui, float x, float top, float height) {
		float room = cardWidth() - PanelKit.PAD - x;
		float y = top;
		boolean editing = HudEditor.isOpen();
		float before = Draw.alpha();
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

	/** The simple look's one row changes between its states with a cross-fade this long, in place. */
	private static final float ROW_FADE_SECONDS = 0.2f;
	/** In the HUD editor's example the row shows each of its two states for this long. */
	private static final long SAMPLE_STATE_MILLIS = 3000L;
	private static final float PLOTS_ROOM = 70f;
	/** The state the row shows, the one it showed before while that fades out, and how far the change is. */
	private PestRow rowNow;
	private PestRow rowBefore;
	private float rowChanged = 1f;
	private long rowFrame;

	/**
	 * The simple look: one row, the pests while some are alive (with their plots after the number, as chosen), else the time until the next
	 * pest, or that it is ready. The row keeps its size and place whatever it shows.
	 */
	private void drawSimple(Canvas ui) {
		int width = cardWidth();
		boolean glass = hasBackground();
		long now = System.currentTimeMillis();
		long left = cooldownEnd == 0 ? 0 : cooldownEnd - now;
		boolean canCome = ready || (cooldownEnd != 0 && left <= 0);
		int count = aliveShown();
		// the example in the editor shows both states in turn
		boolean sample = sampleShown();
		if (sample && now / SAMPLE_STATE_MILLIS % 2 == 1) {
			count = 0;
		}
		PestRow state = PestRow.of(count, showCooldown.get(), !sample && canCome);
		long frame = System.nanoTime();
		float seconds = rowFrame == 0L ? 0f : Math.min(0.1f, (frame - rowFrame) / 1_000_000_000f);
		rowFrame = frame;
		if (state != rowNow) {
			rowBefore = rowNow;
			rowNow = state;
			rowChanged = rowBefore == null ? 1f : 0f;
		}
		rowChanged = Math.min(1f, rowChanged + seconds / ROW_FADE_SECONDS);
		float before = Draw.alpha();
		if (rowChanged < 1f && rowBefore != null) {
			Draw.setAlpha(before * (1f - rowChanged));
			drawRow(ui, rowBefore, width, glass, count, now, canCome);
			Draw.setAlpha(before * rowChanged);
		}
		drawRow(ui, rowNow, width, glass, count, now, canCome);
		Draw.setAlpha(before);
	}

	/** One state of the simple look's row, at the row's place. */
	private void drawRow(Canvas ui, PestRow state, int width, boolean glass, int count, long now, boolean canCome) {
		switch (state) {
			case PESTS -> {
				String plots = ui.fit(plotsLine, Math.round(PLOTS_ROOM / PanelKit.SMALL));
				PanelKit.simpleRow(ui, width, 0f, glass, "bug", PanelKit.TILE_SLAYER, "Pests", count / (float) MAX_PESTS, PanelKit.RED,
					PestRow.countText(count, showPlots.get(), plots), count > 0 ? PanelKit.RED_TEXT : 0xFFFFFFFF);
			}
			case READY -> PanelKit.simpleRow(ui, width, 0f, glass, "timer", PanelKit.TILE_GARDEN, "Next pest · ready", 1f, PanelKit.GREEN, "", PanelKit.GREEN_TEXT);
			case COOLDOWN -> {
				boolean soon = cooldownEnd != 0 && cooldownEnd - now <= alertSeconds.get() * 1000f;
				PanelKit.simpleRow(ui, width, 0f, glass, "timer", PanelKit.TILE_GARDEN, "Next pest", fill(), stateColor(canCome, soon), cooldownText(now), 0xFFFFFFFF);
			}
		}
	}
}
