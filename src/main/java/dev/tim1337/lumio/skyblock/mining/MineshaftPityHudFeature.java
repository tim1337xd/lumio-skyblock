package dev.tim1337.lumio.skyblock.mining;

import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.setting.Toggle;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.skyblock.data.ProfitActivity;
import dev.tim1337.lumio.skyblock.data.ProfitTracker;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;

import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Progress towards the next guaranteed Glacite Mineshaft, like SkyHanni: SkyBlock counts blocks mined in the Glacite Tunnels towards 2,000, and a
 * mineshaft is guaranteed at 2,000. Block types count differently (mithril 2, glacite, tungsten and umber 4, titanium and low tier gemstones 8,
 * high tier gemstones 10), so the remainder is shown as the number of each block type still needed. The count is read from the "Glacite
 * Mineshafts" player list widget (enabled via /tab); found mineshafts are counted from the chat message.
 */
public class MineshaftPityHudFeature extends AddonPanel {
	private static final int MAX = 2000;
	private static final Pattern WIDGET = Pattern.compile("Glacite Mineshafts:\\s*([\\d,]+)\\s*/\\s*2,?000");
	private static final int PAD = PanelKit.PAD;
	/** How far the header reaches above the content, which starts 6 below it. */
	private static final int HEADER_ABOVE = 6;
	private static final int RING = 40;

	/** A block type with its pity weight and display icon. */
	private record Kind(String name, int weight, ItemStack icon) {
	}

	private static Kind[] kinds;

	private static Kind[] kinds() {
		if (kinds == null) {
			kinds = new Kind[]{
				new Kind("Mithril", 2, new ItemStack(Items.WOOL.lightBlue())),
				new Kind("Glacite, Tungsten, Umber", 4, new ItemStack(Items.PACKED_ICE)),
				new Kind("Titanium, low gemstones", 8, new ItemStack(Items.POLISHED_DIORITE)),
				new Kind("High gemstones", 10, new ItemStack(Items.STAINED_GLASS.blue()))};
		}
		return kinds;
	}

	private final Toggle showNeeded = add(make().toggle("show_needed", Component.literal("Blocks it still takes"), true), "General");
	private final Toggle showStats = add(make().toggle("show_stats", Component.literal("Mineshaft stats"), true), "General");

	private final Spring shownAmount = Spring.bouncy(Spring.GENTLE);
	private final Spring fill = Spring.smooth(Spring.GENTLE);
	/** The widget's count, or -1 if it has not been seen. */
	private int pity = -1;
	private long pitySeen;
	private boolean widgetEverSeen;
	private long lastMineshaft;
	private int total;
	private int session;
	private boolean showingSample;

	public MineshaftPityHudFeature() {
		super("mineshaft_pity", "Mineshaft pity", "How near the next Glacite Mineshaft is by pity, and the blocks of each kind it still takes.", true, Anchor.LEFT, false, 200f, 380f);
		offerCardSize();
		iconRgb(SystemColors.TEAL_RGB);
		iconName("gem");
		summary("How near the next Glacite Mineshaft is, by pity points");
		offerHeader();
		offerNoBackground();
	}

	/** Handles a chat message: the one announcing a found mineshaft. */
	public void onMessage(Component message) {
		String text = message.getString();
		if (text.contains("You found a Glacite Mineshaft portal")) {
			lastMineshaft = System.currentTimeMillis();
			total++;
			session++;
			LumioApi.saveSoon();
		}
	}

	/** The player list read last; the same list is not parsed again (it refreshes once per second, while this runs every frame). */
	private List<String> readTab;

	private void readWidget() {
		List<String> tab = SkyBlockArea.tab();
		if (tab == readTab) {
			return;
		}
		readTab = tab;
		for (String line : tab) {
			Matcher matcher = WIDGET.matcher(line);
			if (matcher.find()) {
				try {
					int read = Mth.clamp(Integer.parseInt(matcher.group(1).replace(",", "")), 0, MAX);
					// the count only rises while mining in the tunnels: the most reliable sign of mining there
					if (pity >= 0 && read > pity) {
						pityRoseAt = System.currentTimeMillis();
					}
					pity = read;
					pitySeen = System.currentTimeMillis();
					if (!widgetEverSeen) {
						widgetEverSeen = true;
						LumioApi.saveSoon();
					}
				} catch (NumberFormatException ignored) {
				}
				return;
			}
		}
	}

	private boolean allowedHere() {
		return SkyBlock.allowed();
	}

	/** @return whether the player is in the Glacite Tunnels or at the Great Glacite Lake (including base camp and fossil research center), not in a mineshaft */
	private boolean inTunnels() {
		String zone = SkyBlockArea.zone().toLowerCase(Locale.ROOT);
		// by the zone name, or by the pity widget in the player list, which only exists in the mines: the zone name was not always read, and the
		// panel stayed hidden while mining in the tunnels
		boolean place = zone.contains("glacite") || zone.contains("base camp") || zone.contains("fossil") || System.currentTimeMillis() - pitySeen < 30_000L;
		return !inMineshaft() && place;
	}

	/** When the pity count last increased, i.e. a block was mined in the tunnels. */
	private long pityRoseAt;

	/** @return whether the player is inside a Glacite Mineshaft, where the pity is irrelevant since the mineshaft was already found */
	private static boolean inMineshaft() {
		return SkyBlockArea.island().toLowerCase(Locale.ROOT).contains("mineshaft") || SkyBlockArea.zone().toLowerCase(Locale.ROOT).contains("mineshaft");
	}

	private boolean wanted() {
		// while changing worlds (entering a mineshaft, warping) the location is briefly unknown: keep the previous visibility
		if (ProfitActivity.changingWorld()) {
			return wasWanted;
		}
		return wasWanted = wantedNow();
	}

	/** The last visibility, kept while changing worlds. */
	private boolean wasWanted;

	private boolean wantedNow() {
		if (!allowedHere()) {
			return false;
		}
		// never inside a mineshaft: the pity is for finding one
		if (inMineshaft()) {
			return false;
		}
		// auto-detected: mining in the tunnels or at the lake, and only there; mining is detected by the SkyBlock activity tracker, or by the pity
		// count having increased in the last minutes
		return inTunnels() && (ProfitActivity.MINING.engaged() || System.currentTimeMillis() - pityRoseAt < 5 * 60_000L);
	}

	@Override
	public void prepare(Minecraft client) {
		if (allowedHere()) {
			readWidget();
		}
		showingSample = false;
		shownAmount.follow(simulated() || wanted() ? 1f : 0f);
		fill.follow(current() / (float) MAX);
	}

	private int current() {
		return showingSample ? 1340 : Math.max(0, pity);
	}

	@Override
	public boolean shown(Minecraft client) {
		return shownAmount.value() > 0.01f;
	}

	@Override
	public boolean inEditorWhenHidden() {
		return false;
	}

	private boolean missing() {
		return !showingSample && pity < 0;
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
		int h = header() + 4 + RING + 6;
		if (missing()) {
			return h + 4;
		}
		if (showNeeded.get()) {
			h += 6 + 13 + kinds().length * 15;
		}
		if (showStats.get()) {
			h += 6 + 2 * 13;
		}
		return h + PAD - 4;
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		float amount = Mth.clamp(shownAmount.value(), 0f, 1f);
		float alphaBefore = Draw.alpha();
		Draw.setAlpha(alphaBefore * amount);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(0f, (1f - amount) * -8f);
		pose.translate(cardWidth() / 2f, 0f);
		pose.scale(0.94f + 0.06f * amount, 0.94f + 0.06f * amount);
		pose.translate(-cardWidth() / 2f, 0f);
		try {
			drawCard(ui, graphics);
		} finally {
			pose.popMatrix();
			Draw.setAlpha(alphaBefore);
		}
	}

	private void separator(Canvas ui, int y) {
		PanelKit.divider(ui, cardWidth(), y);
	}

	private void drawCard(Canvas ui, GuiGraphicsExtractor graphics) {
		drawGlass(ui, cardWidth(), panelHeight(), PanelKit.RADIUS);
		int text = HudTheme.text();
		int secondary = HudTheme.secondary();
		int accent = accentArgb();
		familyHeader(ui, cardWidth(), "gem", PanelKit.TILE_COMMISSIONS, "Mineshaft pity", null, 0, false);

		int top = header() + 5;
		int count = current();
		float fraction = Mth.clamp(fill.value(), 0f, 1f);
		// the ring: progress towards 2,000, from the top, clockwise
		int cx = PAD + RING / 2;
		int cy = top + RING / 2;
		float radius = RING / 2f - 1.5f;
		boolean near = count >= MAX - 100;
		int ringColor = near ? SystemColors.GREEN : accent;
		if (ui.arcsAvailable()) {
			// a faint full track with one smooth round-capped arc on top
			ui.arc(cx, cy, RING / 2f, 3.5f, 0f, 1f, Draw.faded(Draw.withOpacity(0xFFFFFFFF, 0.1f)));
			float caps = 1.75f / (float) (Math.PI * 2.0 * (RING / 2f - 1.75f));
			if (count >= MAX || fraction >= 1f - caps) {
				// full: a closed ring. The round caps are kept within the arc's angle, so a nearly full arc left a gap at the top, and the fill spring
				// only approaches 1
				ui.arc(cx, cy, RING / 2f, 3.5f, 0f, 1f, Draw.faded(ringColor));
			} else if (fraction > 0.002f) {
				ui.arc(cx, cy, RING / 2f, 3.5f, caps, Math.max(caps + 0.001f, fraction - caps), Draw.faded(ringColor));
			}
		} else {
			ui.ring(PAD, top, RING, RING, RING / 2f, 3, Draw.withOpacity(0xFFFFFFFF, 0.12f));
			int dots = 110;
			int lit = Math.round(dots * fraction);
			for (int i = 0; i < lit; i++) {
				double angle = -Math.PI / 2 + i * Math.PI * 2 / dots;
				ui.roundedSmooth(cx + (float) Math.cos(angle) * radius - 1.5f, cy + (float) Math.sin(angle) * radius - 1.5f, 3f, 3f, Radius.pill(3f), ringColor);
			}
		}
		String percent = missing() ? "?" : Math.round(fraction * 100f) + "%";
		float pw = ui.boldWidthScaled(percent, TextScale.SMALL);
		ui.textAtScaledBold(percent, cx - pw / 2f, cy - 3.5f, TextScale.SMALL, text);

		int textX = PAD + RING + 10;
		if (missing()) {
			ui.textAt("Pity widget missing", textX, top + 6f, SystemColors.RED_TEXT);
			ui.textAtScaled("Turn it on with /tab", textX, top + 20f, TextScale.SMALL, secondary);
			return;
		}
		String number = PanelKit.grouped(count);
		ui.textAtScaledBold(number, textX, top + 3f, TextScale.HEADING, text);
		float numberWidth = ui.boldWidthScaled(number, TextScale.HEADING);
		ui.textAtScaled("/ 2,000", textX + numberWidth + 4f, Draw.besideTop(top + 3f, TextScale.HEADING, TextScale.SMALL), TextScale.SMALL, secondary);
		int left = MAX - count;
		ui.textAtScaled(left <= 0 ? "A mineshaft is due" : PanelKit.grouped(left) + " to go", textX, top + 28f, TextScale.SMALL, near ? SystemColors.GREEN : secondary);

		int y = top + RING + 6;
		if (showNeeded.get()) {
			separator(ui, y);
			y += 6;
			ui.textAtScaled("Blocks it still takes", PAD + 1, y + 1f, TextScale.SMALL, secondary);
			y += 13;
			for (Kind kind : kinds()) {
				var pose = graphics.pose();
				pose.pushMatrix();
				pose.translate(PAD, y);
				pose.scale(0.75f, 0.75f);
				graphics.item(kind.icon(), 0, 0);
				pose.popMatrix();
				ui.textAt(ui.fit(kind.name(), cardWidth() - PAD * 2 - 60), PAD + 17, y + 2f, text);
				String blocks = PanelKit.grouped((long) Math.ceil(Math.max(0, left) / (double) kind.weight()));
				ui.textAt(blocks, cardWidth() - PAD - ui.width(blocks), y + 2f, text);
				y += 15;
			}
		}
		if (showStats.get()) {
			separator(ui, y);
			y += 6;
			String last = lastMineshaft == 0 ? "not yet" : ProfitTracker.time(System.currentTimeMillis() - lastMineshaft) + " ago";
			statLine(ui, y, "Last mineshaft", last);
			y += 13;
			statLine(ui, y, "Found", session + " now  ·  " + total + " in all");
		}
	}

	private void statLine(Canvas ui, int y, String label, String value) {
		ui.textAtScaled(label, PAD + 1, y + 1f, TextScale.SMALL, HudTheme.secondary());
		float w = ui.widthScaled(value, TextScale.SMALL);
		ui.textAtScaled(value, cardWidth() - PAD - w, y + 1f, TextScale.SMALL, HudTheme.text());
	}

	@Override
	public JsonObject save() {
		JsonObject json = super.save();
		json.addProperty("last_mineshaft", lastMineshaft);
		json.addProperty("total", total);
		json.addProperty("widget_seen", widgetEverSeen);
		return json;
	}

	@Override
	public void load(JsonObject json) {
		super.load(json);
		try {
			lastMineshaft = json.has("last_mineshaft") ? json.get("last_mineshaft").getAsLong() : 0;
			total = json.has("total") ? json.get("total").getAsInt() : 0;
			widgetEverSeen = json.has("widget_seen") && json.get("widget_seen").getAsBoolean();
		} catch (RuntimeException ignored) {
		}
	}
}
