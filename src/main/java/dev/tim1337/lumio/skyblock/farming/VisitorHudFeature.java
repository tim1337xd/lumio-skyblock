package dev.tim1337.lumio.skyblock.farming;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.hud.HudEditor;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.feature.SettingGroup;
import dev.tim1337.lumio.api.setting.Toggle;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.api.experimental.ui.Toasts;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;
import dev.tim1337.lumio.skyblock.data.SkyBlockTime;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Garden visitors for Hypixel SkyBlock, like SkyHanni: who is there, how many of the maximum, and when the next one arrives. Everything is read
 * from the "Visitors" player list widget (enabled via /widgets): the title holds the count, the lines below the names, and the next visitor time
 * is a separate line. A notification can be shown when a new visitor arrives.
 */
public class VisitorHudFeature extends AddonPanel {
	/** The widget title with the count: "Visitors: (3)". */
	private static final Pattern TITLE = Pattern.compile("^Visitors:?\\s*\\((\\d+)\\)", Pattern.CASE_INSENSITIVE);
	private static final Pattern NEXT = Pattern.compile("Next Visitor:\\s*(.+)$", Pattern.CASE_INSENSITIVE);
	private static final int MOST = 5;

	private static final int PAD = PanelKit.PAD;
	/** How far the header reaches above the content, which starts 6 below it. */
	private static final int HEADER_ABOVE = 6;
	private static final int LINE_BASE = 13;

	private final Toggle showNames = add(make().toggle("show_names", Component.literal("Visitor names"), true), "General");
	private final Toggle showNext = add(make().toggle("show_next", Component.literal("Next visitor timer"), true), "General");
	private final Toggle notify = add(make().toggle("notify", Component.literal("Visitor alert"), false), "General");

	private final Spring shownAmount = Spring.bouncy(Spring.GENTLE);
	private final Spring height = Spring.smooth(Spring.DEFAULT);
	private List<String> visitors = List.of();
	private int count = -1;
	/** When the next visitor arrives, or 0 if unknown. */
	private long nextAt;
	private boolean seen;

	/** @return how many visitors are in the Garden, or -1 when not known (read while this panel is on) */
	public int visitorCount() {
		return count;
	}

	public VisitorHudFeature() {
		super("visitor_hud", "Visitors", "The visitors of the Garden: who is there, how many, and when the next one comes.", false, Anchor.LEFT, false, 200f, 230f);
		offerCardSize();
		iconRgb(SystemColors.GREEN_RGB);
		iconName("person");
		summary("Your Garden visitors and when the next one comes");
		offerHeader();
		offerNoBackground();
	}

	private boolean allowedHere() {
		return SkyBlock.allowed();
	}

	/** The player list read last; the same list is not parsed again (it refreshes once per second, while this runs every frame). */
	private List<String> readTab;

	private void readWidget() {
		List<String> tab = SkyBlockArea.tab();
		if (tab == readTab) {
			return;
		}
		readTab = tab;
		List<String> names = new ArrayList<>();
		boolean inBox = false;
		boolean found = false;
		int newCount = -1;
		for (String line : tab) {
			String plain = line.strip();
			Matcher next = NEXT.matcher(plain);
			if (next.find()) {
				long left = SkyBlockTime.parse(next.group(1));
				if (left >= 0) {
					long end = System.currentTimeMillis() + left;
					// whole seconds: keep the counted time while it agrees
					if (nextAt == 0 || Math.abs(end - nextAt) > 1500) {
						nextAt = end;
					}
				} else if (next.group(1).toLowerCase().contains("full") || next.group(1).toLowerCase().contains("queue")) {
					nextAt = 0;
				}
				continue;
			}
			Matcher title = TITLE.matcher(plain);
			if (title.find()) {
				inBox = true;
				found = true;
				newCount = Integer.parseInt(title.group(1));
				continue;
			}
			if (!inBox) {
				continue;
			}
			if (plain.isEmpty() || plain.endsWith(":")) {
				inBox = false;
				continue;
			}
			names.add(plain);
		}
		if (!found) {
			return;
		}
		if (notify.get() && seen && newCount > count && count >= 0) {
			Toasts.info("Visitor", newCount + (newCount == 1 ? " visitor is" : " visitors are") + " in the Garden");
		}
		seen = true;
		count = newCount;
		visitors = names;
	}

	@Override
	public void prepare(Minecraft client) {
		boolean here = allowedHere() && SkyBlockArea.inGarden();
		if (here) {
			readWidget();
		}
		// only where relevant, also in the HUD editor, where it then appears in its displayed position
		shownAmount.follow(simulated() || here ? 1f : 0f);
		height.follow(panelHeightNow());
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

	private List<String> shownNames() {
		if (HudEditor.isOpen() && !seen) {
			return List.of("Jacob", "Andrew", "Liam");
		}
		return visitors;
	}

	private int panelHeightNow() {
		int lines = (showNames.get() ? Math.max(1, shownNames().size()) : 0) + (showNext.get() ? 1 : 0);
		return header() + 6 + lines * line(LINE_BASE) + PAD - 3;
	}

	/** @return where the header row ends: 6 above the content, which starts under the header or at the top when it is hidden */
	private int header() {
		return contentTop() - HEADER_ABOVE;
	}

	@Override
	public int panelHeight() {
		return Math.max(header() + 8, Math.round(height.value()));
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
			int secondary = HudTheme.secondary();
			int shownCount = HudEditor.isOpen() && !seen ? 3 : Math.max(0, count);
			familyHeader(ui, cardWidth(), "people", PanelKit.TILE_GARDEN, "Visitors", shownCount + " / " + MOST, shownCount >= MOST ? SystemColors.ORANGE : 0, false);
			int y = header() + 6;
			if (showNames.get()) {
				List<String> names = shownNames();
				if (names.isEmpty()) {
					ui.textAtScaled(seen ? "No visitors" : "Visitors widget missing: /widgets", PAD + 1, y + 1f, TextScale.SMALL, secondary);
					y += line(LINE_BASE);
				}
				for (String name : names) {
					ui.roundedSmooth(PAD + 1, y + 4f, 4f, 4f, Radius.pill(4f), accentArgb());
					ui.textAtScaled(ui.fit(name, cardWidth() - PAD * 2 - 12), PAD + 10, y + 1f, TextScale.SMALL, text);
					y += line(LINE_BASE);
				}
			}
			if (showNext.get()) {
				long left = nextAt == 0 ? -1 : nextAt - System.currentTimeMillis();
				String value = shownCount >= MOST ? "full" : left < 0 ? "not known" : left == 0 ? "now" : SkyBlockTime.clock(left);
				ui.textAtScaled("Next visitor", PAD + 1, y + 1f, TextScale.SMALL, secondary);
				float vw = ui.widthScaled(value, TextScale.SMALL);
				ui.textAtScaled(value, cardWidth() - PAD - vw, y + 1f, TextScale.SMALL, text);
			}
			graphics.disableScissor();
		} finally {
			Draw.setAlpha(alphaBefore);
		}
	}
}
