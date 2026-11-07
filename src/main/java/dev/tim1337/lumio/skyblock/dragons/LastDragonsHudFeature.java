package dev.tim1337.lumio.skyblock.dragons;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.skyblock.data.ProfitActivity;
import dev.tim1337.lumio.skyblock.data.SkyBlock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Collection;

/** The last three dragons as small cards beside the dragon's profit: kind in its color, eyes placed, place, weight and what dropped. A dragon the player placed no eyes for is faint. */
public class LastDragonsHudFeature extends AddonPanel {
	private static final int WIDTH = 190;
	private static final int CARD_HEIGHT = 42;
	private static final int CARD_GAP = 4;
	private static final int HEAD = 13;
	private static final int DOT = 5;
	private static final float FAINT = 0.5f;
	private static final String UNCOUNTED = "Not placed for · not counted";
	private static final String NOTHING = "Nothing dropped";

	private final DragonProfit dragons;

	public LastDragonsHudFeature(DragonProfit dragons) {
		super("last_dragons", "Last dragons", "Your last three dragons: kind, eyes, place, weight and what dropped.", true, Anchor.LEFT, false, 216f, 220f);
		this.dragons = dragons;
		iconRgb(SystemColors.ORANGE_RGB);
		iconName("swoosh");
		summary("Your last three dragons");
		offerNoBackground();
	}

	private Collection<DragonRecord> shownRecords() {
		return simulated() && dragons.last().isEmpty() ? dragons.sampleRecords() : dragons.last();
	}

	@Override
	public boolean shown(Minecraft client) {
		return dragons.showsLast() && SkyBlock.allowed() && ProfitActivity.DRAGONS.engaged() && !dragons.last().isEmpty();
	}

	@Override
	public int panelWidth() {
		return WIDTH;
	}

	@Override
	public int panelHeight() {
		int count = Math.max(1, shownRecords().size());
		return HEAD + count * (CARD_HEIGHT + CARD_GAP);
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		ui.textAtScaled("LAST DRAGONS", 2f, 2f, TextScale.SMALL, HudTheme.secondary());
		int y = HEAD;
		float before = Draw.alpha();
		for (DragonRecord record : shownRecords()) {
			Draw.setAlpha(before * (record.counted ? 1f : FAINT));
			drawCard(ui, record, y);
			y += CARD_HEIGHT + CARD_GAP;
		}
		Draw.setAlpha(before);
	}

	private void drawCard(Canvas ui, DragonRecord record, int y) {
		drawGlassCard(ui, y);
		float x = PanelKit.PAD;
		ui.rounded(Math.round(x), y + 7, DOT, DOT, Radius.XS, 0xFF000000 | DragonFormats.rgb(record.kind));
		ui.textAtScaledBold(record.kind + " Dragon", x + DOT + 4f, y + 5f, TextScale.BODY, HudTheme.text());
		String eyes = record.eyes == 1 ? "1 eye" : record.eyes + " eyes";
		ui.textAtScaled(eyes, WIDTH - PanelKit.PAD - ui.widthScaled(eyes, TextScale.SMALL), y + 6f, TextScale.SMALL, HudTheme.secondary());
		String standing = "Place " + (record.place > 0 ? "#" + record.place : "–") + "   Weight " + (record.counted && record.weight > 0 ? record.weight : "–");
		ui.textAtScaled(standing, x, y + 18f, TextScale.SMALL, HudTheme.secondary());
		String drops = record.counted ? record.drops.isEmpty() ? NOTHING : String.join(" · ", record.drops) : UNCOUNTED;
		ui.textAtScaled(ui.fitScaled(drops, WIDTH - 2f * PanelKit.PAD, TextScale.SMALL), x, y + 29f, TextScale.SMALL, HudTheme.text());
	}

	private void drawGlassCard(Canvas ui, int y) {
		var pose = ui.graphics().pose();
		pose.pushMatrix();
		pose.translate(0f, y);
		drawGlass(ui, WIDTH, CARD_HEIGHT, PanelKit.RADIUS);
		pose.popMatrix();
	}
}
