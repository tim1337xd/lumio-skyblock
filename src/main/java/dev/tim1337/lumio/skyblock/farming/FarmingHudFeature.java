package dev.tim1337.lumio.skyblock.farming;

import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.hud.HudEditor;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.setting.TextInput;
import dev.tim1337.lumio.api.setting.Toggle;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.data.ProfitActivity;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;
import dev.tim1337.lumio.skyblock.data.SkyBlockTime;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Garden farming info for Hypixel SkyBlock in one panel, each line individually toggleable: the vinyl the vacuum plays (which pest it attracts),
 * the remaining plot spray duration, and Pesthunter Phillip's farming fortune bonus with its duration.
 * <p>
 * The vinyl comes from the chat or the lore of the vacuum in the hotbar; the spray and Phillip's bonus from player list lines, and from chat for
 * the bonus, matched loosely since not all texts are known: a line containing "Spray" and a time, and one containing "Pesthunter" or "Phillip"
 * and a time.
 */
public class FarmingHudFeature extends AddonPanel {
	private static final Pattern VINYL = Pattern.compile("(?:Selected )?Vinyl:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern SPRAY = Pattern.compile("Spray(?:ed)?(?: with [^:]+)?:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
	private static final Pattern PHILLIP = Pattern.compile("(?:Pesthunter|Phillip)[^:]*:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
	/** Fortune in a text: "+60☘" or "60 Farming Fortune". */
	private static final Pattern FORTUNE = Pattern.compile("\\+?(\\d+)\\s*(?:☘|Farming Fortune)", Pattern.CASE_INSENSITIVE);

	private static final int PAD = PanelKit.PAD;
	/** How far the header reaches above the content, which starts 6 below it. */
	private static final int HEADER_ABOVE = 6;
	private static final int LINE_BASE = 14;

	private final Toggle showVinyl = add(make().toggle("show_vinyl", Component.literal("Vacuum vinyl"), true), "General");
	private final Toggle showSpray = add(make().toggle("show_spray", Component.literal("Pest spray"), true), "General");
	private final Toggle showPhillip = add(make().toggle("show_phillip", Component.literal("Pesthunter fortune"), true), "General");
	private final Toggle showBonus = add(make().toggle("show_bpc", Component.literal("Bonus Pest Chance"), true), "General",
		"From the stats in the tab list");
	private final Toggle showMantid = add(make().toggle("show_mantid", Component.literal("Mantid bonus"), true), "General",
		"From pests killed in the last 10 minutes");

	private final Spring shownAmount = Spring.bouncy(Spring.GENTLE);
	private final Spring height = Spring.smooth(Spring.DEFAULT);
	private String vinyl = "";
	private String sprayText = "";
	/** A timer read from the tab list only replaces the known end when it differs by more than this, so it does not jitter. */
	private static final long TIMER_TOLERANCE_MILLIS = 1500;
	private long sprayEnd;
	/** When a plot was sprayed; the player list is not trusted for a moment afterwards. */
	private long sprayedAt;
	/** The playing vinyl as announced in chat when it was changed; empty if not announced. Persisted, since the server only announces it on change. */
	private final TextInput chosenVinyl = add(make().text("chosen_vinyl", Component.literal("Vinyl"), "", false), "General");
	{
		chosenVinyl.visibleWhen(() -> false);
	}
	private static final Pattern PLAYING = Pattern.compile("You are now playing (.+?)!?$", Pattern.CASE_INSENSITIVE);
	private static final Pattern STOPPED = Pattern.compile("You (?:are no longer playing|stopped playing)", Pattern.CASE_INSENSITIVE);
	private static final Pattern SPRAYED = Pattern.compile("You sprayed Plot", Pattern.CASE_INSENSITIVE);
	private String fortune = "";
	private long phillipEnd;
	/** Bonus Pest Chance from the player list stats: "Bonus Pest Chance: 120". */
	private static final Pattern BONUS_PEST_CHANCE = Pattern.compile("Bonus Pest Chance:\\s*\\D{0,3}?\\s*([\\d,.]+)", Pattern.CASE_INSENSITIVE);
	private static final Pattern ALIVE = Pattern.compile("^Alive:\\s*(\\d+)");
	/** Mantid reforge: a little Bonus Pest Chance per pest killed in the last 10 minutes, per armor piece, up to a cap. */
	private static final float MANTID_PER_KILL = 0.25f;
	private static final float MANTID_MOST = 5f;
	private static final long MANTID_TIME = 10 * 60_000L;
	private String bonusPestChance = "";
	/** Pest kill times: the alive count in the pests widget went down. */
	private final Deque<Long> kills = new ArrayDeque<>();
	private int lastAlive = -1;
	private int mantidPieces;

	/** @return when the plot spray ends, in milliseconds since the epoch, or 0 when not known (read while this panel is on) */
	public long sprayEnd() {
		return sprayEnd;
	}

	public FarmingHudFeature() {
		super("farming_hud", "Farming", "The vinyl of the vacuum, the pest spray, the fortune of Pesthunter Phillip, the Bonus Pest Chance and the Mantid bonus, in one panel.", false, Anchor.LEFT, false, 200f, 340f);
		offerCardSize();
		iconRgb(SystemColors.GREEN_RGB);
		iconName("wheat");
		summary("Vinyl, spray and farming bonuses in one panel");
		offerHeader();
		offerNoBackground();
	}

	private boolean allowedHere() {
		return SkyBlock.allowed();
	}

	/** Handles a chat message: vinyl changes, plot sprays, and Phillip's bonus ("... +60☘ Farming Fortune for 30m"). */
	public void onMessage(Component message) {
		String text = TextColors.noColors(message.getString()).strip();
		// vinyl: the server announces which one plays when it is changed, and that is what the vacuum attracts
		Matcher playing = PLAYING.matcher(text);
		if (playing.find()) {
			chosenVinyl.set(playing.group(1).strip());
			LumioApi.saveSoon();
			return;
		}
		if (STOPPED.matcher(text).find()) {
			chosenVinyl.set("");
			LumioApi.saveSoon();
			return;
		}
		// spray: right after spraying, use the sprayonator's duration rather than the stale value the player list still shows briefly
		if (SPRAYED.matcher(text).find()) {
			long length = sprayLength(Minecraft.getInstance());
			sprayEnd = System.currentTimeMillis() + length;
			sprayedAt = System.currentTimeMillis();
			sprayText = SkyBlockTime.clock(length);
			return;
		}
		if (!text.contains("Phillip") && !text.contains("Pesthunter")) {
			return;
		}
		Matcher fortuneMatch = FORTUNE.matcher(text);
		long time = SkyBlockTime.parse(text);
		if (fortuneMatch.find()) {
			fortune = "+" + fortuneMatch.group(1) + " FF";
		}
		if (time > 0) {
			phillipEnd = System.currentTimeMillis() + time;
		}
	}

	private void read(Minecraft client) {
		long now = System.currentTimeMillis();
		boolean inPests = false;
		int alive = -1;
		for (String line : SkyBlockArea.tab()) {
			String plain = TextColors.noColors(line).strip();
			// the player list puts a stat symbol before the number: "Bonus Pest Chance: ൠ120"
			Matcher bonus = BONUS_PEST_CHANCE.matcher(plain);
			if (bonus.find()) {
				bonusPestChance = bonus.group(1);
			}
			if (plain.equalsIgnoreCase("Pests:")) {
				inPests = true;
				continue;
			}
			if (inPests) {
				Matcher aliveMatch = ALIVE.matcher(plain);
				if (aliveMatch.find()) {
					alive = Integer.parseInt(aliveMatch.group(1));
					inPests = false;
				} else if (plain.isEmpty() || plain.endsWith(":")) {
					inPests = false;
				}
			}
			Matcher spray = SPRAY.matcher(plain);
			if (spray.find()) {
				// right after a spray the list still shows the old time for a few seconds: keep the sprayonator's time then
				if (now - sprayedAt < 15_000L) {
					continue;
				}
				sprayText = spray.group(1).strip();
				long left = SkyBlockTime.parse(sprayText);
				if (left >= 0 && (sprayEnd == 0 || Math.abs(now + left - sprayEnd) > TIMER_TOLERANCE_MILLIS)) {
					sprayEnd = now + left;
				}
				if (left < 0) {
					sprayEnd = 0;
				}
				continue;
			}
			Matcher phillip = PHILLIP.matcher(plain);
			if (phillip.find()) {
				String value = phillip.group(1).strip();
				Matcher fortuneMatch = FORTUNE.matcher(value);
				if (fortuneMatch.find()) {
					fortune = "+" + fortuneMatch.group(1) + " FF";
				}
				long left = SkyBlockTime.parse(value);
				if (left >= 0 && (phillipEnd == 0 || Math.abs(now + left - phillipEnd) > TIMER_TOLERANCE_MILLIS)) {
					phillipEnd = now + left;
				}
			}
		}
		// the vinyl announced in chat is authoritative; the lore of a vacuum in the hotbar is used until such a message was seen
		String fromLore = vinylOf(client);
		vinyl = !chosenVinyl.get().isEmpty() ? chosenVinyl.get() : fromLore;

		// fewer pests alive than before means pests were killed, which the Mantid reforge counts
		if (alive >= 0) {
			if (lastAlive >= 0 && alive < lastAlive) {
				for (int i = 0; i < lastAlive - alive; i++) {
					kills.addLast(now);
				}
			}
			lastAlive = alive;
		}
		while (!kills.isEmpty() && now - kills.peekFirst() > MANTID_TIME) {
			kills.removeFirst();
		}
		mantidPieces = mantidPieces(client);
	}

	/** @return the number of worn armor pieces with the Mantid reforge (their names start with it) */
	private static int mantidPieces(Minecraft client) {
		int pieces = 0;
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD,
			EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
			ItemStack stack = client.player.getItemBySlot(slot);
			if (!stack.isEmpty() && TextColors.noColors(stack.getHoverName().getString()).strip().startsWith("Mantid ")) {
				pieces++;
			}
		}
		return pieces;
	}

	private static String trim(float value) {
		return value == Math.round(value) ? Integer.toString(Math.round(value)) : Draw.trimZeros(String.format(Locale.ROOT, "%.2f", value));
	}

	/** @return the spray duration of the held sprayonator from its lore ("Duration: 1h"), or 60 minutes (the best one) */
	private static long sprayLength(Minecraft client) {
		if (client.player != null) {
			ItemStack held = client.player.getMainHandItem();
			ItemLore lore = held.get(DataComponents.LORE);
			if (lore != null) {
				for (Component line : lore.lines()) {
					String plain = TextColors.noColors(line.getString());
					String lower = plain.toLowerCase(Locale.ROOT);
					if (lower.contains("duration") || lower.contains("lasts") || lower.contains("for ")) {
						long parsed = SkyBlockTime.parse(plain);
						if (parsed > 60_000L) {
							return parsed;
						}
					}
				}
			}
		}
		return 60 * 60_000L;
	}

	/** @return the pest a vinyl attracts, by vinyl name as in SkyHanni; unknown vinyls show their own name */
	private static String pestOf(String vinyl) {
		String key = vinyl.toLowerCase(Locale.ROOT).replace("'", "");
		return switch (key) {
			case "pretty fly" -> "Fly";
			case "cricket choir" -> "Cricket";
			case "cicada symphony" -> "Locust";
			case "rodent revolution" -> "Rat";
			case "buzzin beats" -> "Mosquito";
			case "earthworm ensemble" -> "Earthworm";
			case "dynamites" -> "Mite";
			case "wings of harmony" -> "Moth";
			case "slow and groovy" -> "Slug";
			case "not just a pest" -> "Beetle";
			default -> "";
		};
	}

	/** @return the vinyl of a vacuum in the hotbar, from its lore: "Selected Vinyl: Beetle" */
	private static String vinylOf(Minecraft client) {
		for (int i = 0; i < 9; i++) {
			ItemStack stack = client.player.getInventory().getItem(i);
			String id = ItemGains.idOf(stack);
			if (id == null || !id.toUpperCase(Locale.ROOT).contains("VACUUM")) {
				continue;
			}
			ItemLore lore = stack.get(DataComponents.LORE);
			if (lore == null) {
				continue;
			}
			for (Component line : lore.lines()) {
				Matcher matcher = VINYL.matcher(TextColors.noColors(line.getString()).strip());
				if (matcher.find()) {
					return matcher.group(1).strip();
				}
			}
			return "none";
		}
		return "";
	}

	/** When the panel last read the game state. */
	private long readAt;

	@Override
	public void prepare(Minecraft client) {
		boolean here = allowedHere() && SkyBlockArea.inGarden() && client.player != null;
		// armor, tool, vacuum and player list change a few times per second at most: read them four times per second rather than every frame (each
		// read strips color codes from names and lines)
		long now = System.currentTimeMillis();
		if (here && now - readAt >= 250L) {
			readAt = now;
			read(client);
		}
		// read anywhere in the Garden so it is accurate when shown; shown only while farming and for a while after, like the farming profit
		boolean farming = ProfitActivity.FARMING.engaged();
		// in the HUD editor too only while relevant, like the profit and pests panels
		shownAmount.follow(simulated() || here && farming ? 1f : 0f);
		height.follow(header() + 6 + Math.max(1, rows().size()) * line(LINE_BASE) + PAD - 3);
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

	/** @return where the header row ends: 6 above the content, which starts under the header or at the top when it is hidden */
	private int header() {
		return contentTop() - HEADER_ABOVE;
	}

	@Override
	public int panelHeight() {
		return Math.max(header() + 8, Math.round(height.value()));
	}

	private record Row(String label, String value, int color) {
	}

	/** @return a duration as displayed: whole minutes (the player list only updates every few seconds), and "<1m" for the last one */
	private static String minutes(long millis) {
		if (millis < 60_000L) {
			return "<1m";
		}
		// round up like the player list: while it says 33m, show 33m too, not 32m
		long minutes = (millis + 59_999L) / 60_000L;
		return minutes >= 60 ? minutes / 60 + "h " + minutes % 60 + "m" : minutes + "m";
	}

	private List<Row> rows() {
		long now = System.currentTimeMillis();
		boolean sample = HudEditor.isOpen() && vinyl.isEmpty() && sprayText.isEmpty() && fortune.isEmpty();
		List<Row> rows = new ArrayList<>();
		// every value in the same color and style, whatever it says
		int text = HudTheme.text();
		if (showVinyl.get()) {
			String pest = pestOf(vinyl);
			String value = sample ? "Slug" : vinyl.isEmpty() ? "None" : vinyl.equalsIgnoreCase("none") ? "None" : pest.isEmpty() ? vinyl : pest;
			rows.add(new Row("Vinyl", value, text));
		}
		if (showSpray.get()) {
			long left = sprayEnd == 0 ? -1 : sprayEnd - now;
			String value = sample ? "23m" : left > 0 ? minutes(left) : sprayText.isEmpty() || sprayText.equalsIgnoreCase("none") ? "None" : left == 0 ? "Ran out" : sprayText;
			rows.add(new Row("Pest spray", value, text));
		}
		if (showPhillip.get()) {
			long left = phillipEnd == 0 ? -1 : phillipEnd - now;
			String value = sample ? "+60 FF  ·  14m" : left > 0 ? (fortune.isEmpty() ? "" : fortune + "  ·  ") + minutes(left) : "None";
			rows.add(new Row("Phillip", value, text));
		}
		if (showBonus.get()) {
			rows.add(new Row("BPC", sample ? "120%" : bonusPestChance.isEmpty() ? "not seen yet" : bonusPestChance + "%",bonusPestChance.isEmpty() && !sample ? HudTheme.secondary() : text));
		}
		if (showMantid.get() && (mantidPieces > 0 || sample)) {
			int count = sample ? 12 : kills.size();
			int pieces = sample ? 4 : mantidPieces;
			float perPiece = Math.min(MANTID_MOST, count * MANTID_PER_KILL);
			rows.add(new Row("Mantid", "+" + trim(perPiece * pieces) + "% BPC  ·  " + count + (count == 1 ? " kill" : " kills"), text));
		}
		return rows;
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		float amount = Mth.clamp(shownAmount.value(), 0f, 1f);
		float alphaBefore = Draw.alpha();
		Draw.setAlpha(alphaBefore * amount);
		try {
			int h = panelHeight();
			drawGlass(ui, cardWidth(), h, PanelKit.RADIUS);
			graphics.enableScissor(0, 0, cardWidth(), h);
			int text = HudTheme.text();
			familyHeader(ui, cardWidth(), "wheat", PanelKit.TILE_GARDEN, "Farming", null, 0, false);
			int y = header() + 6;
			List<Row> rows = rows();
			if (rows.isEmpty()) {
				ui.textAtScaled("Nothing chosen to show", PAD + 1, y + 1f, TextScale.SMALL, HudTheme.secondary());
			}
			for (Row row : rows) {
				ui.textAtScaled(row.label(), PAD + 1, y + 1f, TextScale.SMALL, HudTheme.secondary());
				String fitted = ui.fit(row.value(), cardWidth() - PAD * 2 - 70);
				float vw = ui.widthScaled(fitted, TextScale.SMALL);
				ui.textAtScaled(fitted, cardWidth() - PAD - vw, y + 1f, TextScale.SMALL, row.color());
				y += line(LINE_BASE);
			}
			graphics.disableScissor();
		} finally {
			Draw.setAlpha(alphaBefore);
		}
	}
}
