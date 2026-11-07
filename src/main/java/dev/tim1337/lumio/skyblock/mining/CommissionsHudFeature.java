package dev.tim1337.lumio.skyblock.mining;

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
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.skyblock.data.ProfitActivity;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;
import dev.tim1337.lumio.skyblock.mixin.accessor.ContainerScreenAccess;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The active Dwarven Mines commissions, each with a progress bar, highlighted when complete. They are read from the player list widget (enabled
 * via /widgets), the only place the game shows them. The panel appears while mining or in the mines, as configured.
 */
public class CommissionsHudFeature extends AddonPanel {
	/** Row height: the name, the bar under it and the gap to the next row; the last row has no gap after it. */
	private static final int ROW_BASE = 19;
	private static final int ROW_TAIL = 5;
	/** The commissions shown while the HUD editor simulates the mines: two under way and two done. */
	private static final List<SkyBlockArea.Commission> SAMPLE = List.of(new SkyBlockArea.Commission("Mithril Miner", 0.82f, false),
		new SkyBlockArea.Commission("Goblin Slayer", 0.35f, false), new SkyBlockArea.Commission("Titanium Miner", 1f, true),
		new SkyBlockArea.Commission("Lava Springs Mithril", 1f, true));
	private static final int DONE = SystemColors.GREEN;

	private final Toggle keepDone = add(make().toggle("keep_done", Component.literal("Finished ones"), true), "General");
	private final Toggle highlightMenu = add(make().toggle("highlight_menu", Component.literal("Highlight finished in menu"), true), "General",
		"In the Commissions menu");

	private final Spring shownAmount = Spring.bouncy(Spring.GENTLE);
	/** Per commission: bar progress, completion highlight, and entrance progress. */
	private final Map<String, Spring[]> rows = new HashMap<>();
	private final Map<String, Long> doneSince = new HashMap<>();
	private List<SkyBlockArea.Commission> listed = List.of();
	private long lastSeen;
	/** Last shown data and position of every row: a leaving row is still drawn there while it animates out. */
	private final Map<String, SkyBlockArea.Commission> lastData = new HashMap<>();
	private final Map<String, Integer> lastSlot = new HashMap<>();
	/** Displayed height, smoothly following the row count. */
	private final Spring shownHeight = Spring.bouncy(Spring.GENTLE);
	/** Whether rows were shown before: the first rows appear immediately, later ones animate in. */
	private boolean filled;

	public CommissionsHudFeature() {
		super("commissions_hud", "Commissions", "The commissions of the Dwarven Mines, with how far each is, lit when it is done.", true, Anchor.LEFT, false, 200f, 220f);
		offerCardSize();
		iconRgb(SystemColors.YELLOW_RGB);
		iconName("clipboard");
		summary("Your Dwarven Mines commissions and their progress");
		offerNoBackground();
		offerHeader();
	}

	private boolean allowedHere() {
		return SkyBlock.allowed();
	}

	/**
	 * In the Commissions menu (the King, or /commissions): a completed commission waiting to be claimed gets a slowly pulsing green light around its
	 * slot (soft glow, bright edge, faint fill), so it stands out among the others.
	 */
	public void highlightInMenu(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics) {
		if (!isEnabled() || !highlightMenu.get() || !allowedHere() || !screen.getTitle().getString().strip().equals("Commissions")) {
			return;
		}
		var access = (ContainerScreenAccess) screen;
		Draw.setZoom(1f);
		Canvas ui = Draw.hudCanvas(graphics, DONE, 0f, false);
		float breath = 0.5f + 0.5f * (float) Math.sin(System.nanoTime() / 1_000_000_000.0 * Math.PI * 1.4);
		for (Slot slot : screen.getMenu().slots) {
			// menu slots only, not the player inventory below
			if (slot.container instanceof Inventory || !slot.hasItem() || !finished(slot.getItem())) {
				continue;
			}
			int x = access.evskyblock$left() + slot.x - 1;
			int y = access.evskyblock$top() + slot.y - 1;
			ui.softLight(x + 9f, y + 9f, 15f + 3f * breath, 15f + 3f * breath, Draw.withOpacity(DONE, 0.22f + 0.18f * breath));
			ui.rounded(x, y, 18, 18, Radius.SM, Draw.withOpacity(DONE, 0.16f + 0.08f * breath));
			ui.ring(x, y, 18, 18, Radius.SM, 1, Draw.withOpacity(Draw.mix(DONE, 0xFFFFFFFF, 0.35f * breath), 0.85f + 0.15f * breath));
		}
	}

	/** @return whether a commission in the menu is complete: SkyBlock writes COMPLETED below it with claim instructions */
	private static boolean finished(ItemStack stack) {
		var lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return false;
		}
		for (Component line : lore.lines()) {
			if (line.getString().contains("COMPLETED")) {
				return true;
			}
		}
		return false;
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
		// auto-detected: while mining, and anywhere in the mines where commissions are done (including combat ones)
		return ProfitActivity.MINING.engaged() || SkyBlockArea.inDwarvenMines() || SkyBlockArea.inMines();
	}

	@Override
	public void prepare(Minecraft client) {
		boolean editing = HudEditor.isOpen();
		List<SkyBlockArea.Commission> found = allowedHere() ? SkyBlockArea.commissions() : List.of();
		if (found.isEmpty() && simulated()) {
			// the sample while the HUD editor simulates the mines and the player list shows none
			found = SAMPLE;
		}
		long now = System.currentTimeMillis();
		if (!found.isEmpty()) {
			lastSeen = now;
		}
		List<SkyBlockArea.Commission> shown = new ArrayList<>();
		for (SkyBlockArea.Commission commission : found) {
			if (commission.done() && !keepDone.get() && !editing) {
				continue;
			}
			shown.add(commission);
		}
		listed = shown;
		// the widget can briefly disappear while the server rewrites the list: keep the panel through that
		boolean has = !listed.isEmpty() || (now - lastSeen < 3000 && !rows.isEmpty());
		shownAmount.follow(simulated() || wanted() && has ? 1f : 0f);
		for (int i = 0; i < listed.size(); i++) {
			SkyBlockArea.Commission commission = listed.get(i);
			boolean isNew = !rows.containsKey(commission.name());
			Spring[] springs = rows.computeIfAbsent(commission.name(), key -> new Spring[]{Spring.smooth(Spring.GENTLE), Spring.bouncy(Spring.GENTLE), Spring.smooth(Spring.GENTLE)});
			if (isNew && filled) {
				// a new commission, such as the one following a claimed one: it slides in from the right and its bar fills from zero
				springs[0].snap(0f);
				springs[1].snap(0f);
				springs[2].snap(0f);
			}
			lastData.put(commission.name(), commission);
			lastSlot.put(commission.name(), i);
			springs[0].follow(commission.done() ? 1f : commission.progress());
			if (commission.done() && !doneSince.containsKey(commission.name())) {
				doneSince.put(commission.name(), now);
				springs[1].kick(6f); // small bump on completion
			}
			springs[1].follow(commission.done() ? 1f : 0f);
			springs[2].follow(1f);
		}
		Iterator<Map.Entry<String, Spring[]>> iterator = rows.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, Spring[]> row = iterator.next();
			if (listed.stream().noneMatch(commission -> commission.name().equals(row.getKey()))) {
				row.getValue()[2].follow(0f);
				if (row.getValue()[2].value() < 0.02f) {
					iterator.remove();
					doneSince.remove(row.getKey());
					lastData.remove(row.getKey());
					lastSlot.remove(row.getKey());
				}
			}
		}
		if (!listed.isEmpty()) {
			filled = true;
		} else if (rows.isEmpty()) {
			filled = false;
		}
		shownHeight.follow(targetHeight());
	}

	private int targetHeight() {
		return contentTop() + Math.max(1, listed.size()) * line(ROW_BASE) - ROW_TAIL + PanelKit.BOTTOM;
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

	@Override
	public int panelHeight() {
		return Math.round(shownHeight.value() > 0f ? shownHeight.value() : targetHeight());
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

	private void drawCard(Canvas ui, GuiGraphicsExtractor graphics) {
		int height = panelHeight();
		int width = cardWidth();
		drawGlass(ui, width, height, PanelKit.RADIUS);
		int done = 0;
		for (SkyBlockArea.Commission commission : listed) {
			if (commission.done()) {
				done++;
			}
		}
		if (doneCount != done || listedCount != listed.size()) {
			doneCount = done;
			listedCount = listed.size();
			doneText = done + " of " + listed.size() + " done";
		}
		boolean allDone = !listed.isEmpty() && done == listed.size();
		familyHeader(ui, width, "pickaxe", PanelKit.TILE_COMMISSIONS, "Commissions", listed.isEmpty() ? null : doneText,
			allDone ? PanelKit.GREEN_TEXT : 0, false);
		if (listed.isEmpty() && rows.isEmpty()) {
			ui.textAtScaled("None right now", PanelKit.PAD, contentTop(), PanelKit.META, HudTheme.secondary());
			return;
		}
		long now = System.currentTimeMillis();
		// while the panel grows or shrinks, clip what does not fit at the bottom
		graphics.enableScissor(0, 0, width, height - 2);
		try {
			// leaving rows below the arriving ones: a claimed commission slides out to the left while the new one comes in
			for (Map.Entry<String, Spring[]> row : rows.entrySet()) {
				String name = row.getKey();
				if (isListed(name)) {
					continue;
				}
				SkyBlockArea.Commission last = lastData.get(name);
				Integer slot = lastSlot.get(name);
				if (last == null || slot == null) {
					continue;
				}
				Spring[] springs = row.getValue();
				drawRow(ui, rowTop(slot), last, Mth.clamp(springs[0].value(), 0f, 1f), Mth.clamp(springs[1].value(), 0f, 1.3f),
					Mth.clamp(springs[2].value(), 0f, 1f), now, true);
			}
			for (int i = 0; i < listed.size(); i++) {
				SkyBlockArea.Commission commission = listed.get(i);
				Spring[] springs = rows.get(commission.name());
				float fraction = springs == null ? commission.progress() : Mth.clamp(springs[0].value(), 0f, 1f);
				float lit = springs == null ? (commission.done() ? 1f : 0f) : Mth.clamp(springs[1].value(), 0f, 1.3f);
				float in = springs == null ? 1f : Mth.clamp(springs[2].value(), 0f, 1f);
				drawRow(ui, rowTop(i), commission, fraction, lit, in, now, false);
			}
		} finally {
			graphics.disableScissor();
		}
	}

	/** @return whether a commission is in the list now, without allocating */
	private boolean isListed(String name) {
		for (SkyBlockArea.Commission commission : listed) {
			if (commission.name().equals(name)) {
				return true;
			}
		}
		return false;
	}

	private int rowTop(int slot) {
		return contentTop() + slot * line(ROW_BASE);
	}

	/** The header's "2 of 4 done", made again only when a count changes. */
	private int doneCount = -1;
	private int listedCount = -1;
	private String doneText = "";
	/** A row: its name line, the gap to its bar, and the check of a finished one. */
	private static final float NAME_LINE = 8f;
	private static final float BAR_GAP = 3f;
	private static final float CHECK = 8f;

	/** @param leaving whether the row is leaving: it slides out to the left while a new one slides in from the right */
	private void drawRow(Canvas ui, int y, SkyBlockArea.Commission commission, float fraction, float lit, float in, long now, boolean leaving) {
		if (in <= 0.01f) {
			return;
		}
		float alphaBefore = Draw.alpha();
		// smoothstep easing: accelerates as it leaves and decelerates as it arrives
		float eased = in * in * (3f - 2f * in);
		Draw.setAlpha(alphaBefore * eased);
		var pose = ui.graphics().pose();
		pose.pushMatrix();
		pose.translate((1f - eased) * (leaving ? -28f : 28f), 0f);
		try {
			int width = cardWidth();
			float right = width - PanelKit.PAD;
			float x = PanelKit.PAD;
			if (commission.done()) {
				// finished: a green check before the name, the name quiet, and "Done" in green
				float bump = Math.max(0f, lit - 1f);
				float centerY = y + NAME_LINE / 2f;
				if (bump > 0.01f) {
					ui.softLight(x + CHECK / 2f, centerY, CHECK + 4f, Draw.withOpacity(DONE, bump));
				}
				ui.discAt(x + CHECK / 2f, centerY, CHECK, Draw.withOpacity(DONE, Math.min(1f, Math.max(0.6f, lit))));
				ui.icon("check", Math.round(x + 1.5f), Math.round(centerY - 2.5f), 5, 0xFFFFFFFF);
				x += CHECK + 4f;
				float doneWidth = ui.boldWidthScaled("Done", PanelKit.META);
				ui.textAtScaledBold("Done", right - doneWidth, y, PanelKit.META, PanelKit.readable(PanelKit.GREEN_TEXT));
				ui.textAtScaled(ui.fit(commission.name(), Math.round((right - doneWidth - 6f - x) / PanelKit.META)), x, y, PanelKit.META, HudTheme.secondary());
				return;
			}
			String percent = Math.round(fraction * 100f) + "%";
			float percentWidth = ui.boldWidthScaled(percent, PanelKit.META);
			ui.textAtScaledBold(percent, right - percentWidth, y, PanelKit.META, HudTheme.text());
			ui.textAtScaled(ui.fit(commission.name(), Math.round((right - percentWidth - 6f - x) / PanelKit.META)), x, y, PanelKit.META, HudTheme.text());
			PanelKit.bar(ui, x, y + NAME_LINE + BAR_GAP, right - x, PanelKit.BAR, fraction, accentArgb());
		} finally {
			pose.popMatrix();
			Draw.setAlpha(alphaBefore);
		}
	}
}
