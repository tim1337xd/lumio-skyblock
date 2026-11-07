package dev.tim1337.lumio.skyblock.slayer;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.api.experimental.util.Sight;
import dev.tim1337.lumio.api.experimental.draw.PanelLook;
import dev.tim1337.lumio.api.experimental.hud.StyledFeature;
import dev.tim1337.lumio.api.setting.AddonSetting;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.MultiToggle;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;
import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.misc.CocoonLook;
import dev.tim1337.lumio.skyblock.world.WorldLabel;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The phase right above the boss: a label just above the name tags of the player's own boss or the carried one, with the phase's kind, what is left, a thin
 * bar and the phases, as far as they are switched on, as a card, a compact line or just text. Only while the player sees the boss, checked a few times a second; it always faces the player, and has a fixed size in the world, as a name tag does.
 */
public class BossLabelFeature extends AddonFeature implements StyledFeature {
	private static final float LEAST_SIZE = 0.5f;
	private static final float MOST_SIZE = 2f;
	private static final int SIGHT_CHECK_TICKS = 5;
	/** How high above the top name tag the card stands by default, in blocks, how far that can be set, and the room left between them, in GUI pixels. */
	private static final float ABOVE_TOP_TAG = 0.5f;
	private static final float LEAST_ABOVE = 0f;
	private static final float MOST_ABOVE = 3f;
	private static final float ABOVE_TAG_PIXELS = 3f;
	// the label, in GUI pixels at full size
	private static final int LEAST_WIDTH = 104;
	private static final float LEAST_COMPACT_WIDTH = 60f;
	private static final float PAD = 8f;
	private static final float COMPACT_PAD = 6f;
	private static final float TOP = 5.5f;
	private static final float BOTTOM = 4f;
	private static final float VALUE_ROW = 13.5f;
	private static final float BAR_ROW = 7.5f;
	private static final float STEPS_ROW = 9f;
	private static final float LINE_ROW = 10f;
	private static final float VALUE_SIZE = 1.15f;
	private static final float SMALL_DROP = 1.5f;
	private static final float BAR_HEIGHT = 2.5f;
	private static final float STEP_WIDTH = 6f;
	private static final float STEP_HEIGHT = 2f;
	private static final float STEP_GAP = 2f;
	private static final float GAP = 4f;
	private static final float SMALL_GAP = 3f;
	private static final float PAST_STEP_OPACITY = 0.45f;
	private static final float NEXT_STEP_OPACITY = 0.14f;
	private static final float TRACK_OPACITY = 0.14f;
	private static final float SHOWN = 0.01f;
	private static final float SHADOW_OPACITY = 0.6f;
	private static final float SHADOW_OFFSET = 0.75f;
	private static final float SHADOW_REACH = 10f;
	private static final float SOFT_SHADOW_OPACITY = 0.28f;
	// the preview: a sample phase, its label's scale in the card, and the boss behind it
	private static final int PREVIEW_HEIGHT = 96;
	private static final BossPhase SAMPLE = new BossPhase(2, 6, true, "48", "60", 0.8f);
	private static final float PREVIEW_SCALE = 1.1f;
	private static final float PREVIEW_ABOVE_PIXELS = 4f;
	private static final int SAMPLE_WIDTH = 14;
	private static final int SAMPLE_HEIGHT = 34;
	private static final int SAMPLE_BOTTOM = 6;
	private static final int SAMPLE_COLOR = 0xFF1B1422;
	private static final int SAMPLE_EYES = 0xFFD070FF;

	private final Choice<CocoonLook.Style> style = add(make().choice("look", Component.literal("Style"), CocoonLook.Style.CARD, CocoonLook.Style::displayName), "Look");
	private final Choice<PanelLook> theme = add(make().choice("panel_theme", Component.literal("Theme"), PanelLook.HUD, PanelLook::displayName, Choice.Style.DROPDOWN), "Look");
	private final Slider sizeSetting = add(make().slider("size", Component.literal("Size"), 1f, LEAST_SIZE, MOST_SIZE, 2).percent(), "Look");
	private final Slider above = add(make().slider("height", Component.literal("Height"), ABOVE_TOP_TAG, LEAST_ABOVE, MOST_ABOVE, 1), "Look");
	private final Toggle showCount = make().toggle("show_count", Component.literal("Phase count"), true, Toggle.Style.CHECKBOX);
	private final Toggle showKind = make().toggle("show_kind", Component.literal("Kind"), true, Toggle.Style.CHECKBOX);
	private final Toggle showValue = make().toggle("show_value", Component.literal("Value left"), true, Toggle.Style.CHECKBOX);
	private final Toggle showBar = make().toggle("show_bar", Component.literal("Bar"), true, Toggle.Style.CHECKBOX);
	private final Toggle showSteps = make().toggle("show_steps", Component.literal("Phase steps"), true, Toggle.Style.CHECKBOX);
	private final MultiToggle shows = add(make().multi("shows", Component.literal("Shows"), List.of(showCount, showKind, showValue, showBar, showSteps)).asChips(), "Shows");
	private final AddonSetting<Void> previewRow = add(HudTheme.preview("preview", Component.literal("Preview"), PREVIEW_HEIGHT, this::drawPreview), "Look");
	private int shownBefore = BossLabelLook.ALL;
	private final float[] previewPlace = new float[WorldLabel.PLACE_VALUES];
	private final Spring amount = Spring.smooth(Spring.GENTLE);
	private final Spring bar = Spring.smooth(Spring.DEFAULT);
	private final float[] screen = new float[WorldLabel.PLACE_VALUES];
	private boolean sees;
	private int tick;
	private long lastFrame;

	public BossLabelFeature() {
		super("boss_label", "Phase at the boss", "A small card right above your own boss, or the one you carry, with its phase, what is left and a thin bar, while you can see it.", true);
		iconRgb(SystemColors.PURPLE_RGB);
		iconName("sword");
		summary("The phase right above your boss");
	}

	@Override
	public PanelLook look() {
		return theme.get();
	}

	@Override
	public void takeLook(PanelLook look) {
		theme.set(look);
	}

	/** The cocoon's label is the same kind of label: it gets the style and the size too, so the two match. */
	@Override
	public void styleCopied() {
		SkyBlockFeatures.COCOONS.takeStyle(style.get(), sizeSetting.get());
	}

	/** Takes the style and the size of the other label, whose style is copied. */
	public void takeStyle(CocoonLook.Style otherStyle, float size) {
		style.set(otherStyle);
		sizeSetting.set(size);
	}

	/** @return the parts it shows, keeping at least one on: switching the last one off does nothing */
	private int parts() {
		int now = (showCount.get() ? BossLabelLook.COUNT : 0) | (showKind.get() ? BossLabelLook.KIND : 0) | (showValue.get() ? BossLabelLook.VALUE : 0)
			| (showBar.get() ? BossLabelLook.BAR : 0) | (showSteps.get() ? BossLabelLook.STEPS : 0);
		int kept = CocoonLook.kept(shownBefore, now);
		if (kept != now) {
			showCount.set((kept & BossLabelLook.COUNT) != 0);
			showKind.set((kept & BossLabelLook.KIND) != 0);
			showValue.set((kept & BossLabelLook.VALUE) != 0);
			showBar.set((kept & BossLabelLook.BAR) != 0);
			showSteps.set((kept & BossLabelLook.STEPS) != 0);
		}
		shownBefore = kept;
		return kept;
	}

	/** @return its size on top of the distance */
	public float labelSize() {
		return sizeSetting.get();
	}

	public void register() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	private void tick(Minecraft client) {
		if (++tick % SIGHT_CHECK_TICKS != 0) {
			return;
		}
		Entity tag = SlayerBosses.watchedTag();
		Entity body = SlayerBosses.watchedBody();
		sees = isEnabled() && SkyBlock.allowed() && client.player != null && tag != null && Sight.visible(client, body != null ? body : tag);
	}

	public void render(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		long now = System.nanoTime();
		float seconds = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;
		SlayerBoss boss = SlayerBosses.shown();
		Entity tag = SlayerBosses.watchedTag();
		float shown = amount.follow(sees && boss != null && tag != null ? 1f : 0f, seconds);
		Minecraft client = Minecraft.getInstance();
		if (shown < SHOWN || boss == null || tag == null || client.player == null) {
			return;
		}
		float partial = tracker.getGameTimeDeltaPartialTick(false);
		Vec3 at = tag.getPosition(partial);
		double y = at.y + SlayerBosses.watchedTopAbove() + above.get();
		if (!WorldLabel.project(client, at.x, y, at.z, sizeSetting.get(), screen)) {
			return;
		}
		BossPhase phase = boss.phases() != null ? BossPhase.ofShields(boss.phases(), boss.health(), boss.maxHealth()) : BossPhase.ofHealth(boss.health(), boss.maxHealth(), boss.thirds());
		float barNow = bar.follow(phase.bar(), seconds);
		Canvas ui = Draw.hudCanvas(graphics, 0xFF000000 | MainSettings.accentRgb(), seconds, true);
		label(graphics, ui, screen, phase, barNow, shown);
	}

	/** Draws the label on a place, in its style and theme: the slayer panel's colors for the kind and the bar. */
	private void label(GuiGraphicsExtractor graphics, Canvas ui, float[] place, BossPhase phase, float barNow, float fade) {
		int parts = parts();
		CocoonLook.Style chosen = style.get();
		boolean card = chosen == CocoonLook.Style.CARD;
		boolean filled = chosen != CocoonLook.Style.MINIMAL;
		if (filled) {
			graphics.nextStratum();
		}
		float pad = card ? PAD : COMPACT_PAD;
		float width = Math.max(card ? LEAST_WIDTH : LEAST_COMPACT_WIDTH, contentWidth(ui, phase, parts, card) + 2f * pad);
		float height = BossLabelLook.height(parts, card, TOP, VALUE_ROW, BAR_ROW, STEPS_ROW, LINE_ROW, BOTTOM);
		HudTheme.withLook(theme.get(), () -> WorldLabel.draw(graphics, place, width, height + ABOVE_TAG_PIXELS, fade, () -> {
			if (filled) {
				HudTheme.panel(ui, 0, 0, Math.round(width), Math.round(height), card ? Radius.LG : Radius.MD, HudTheme.opacity());
			} else {
				ui.softLight(width / 2f, height / 2f, width / 2f + SHADOW_REACH, height / 2f + SHADOW_REACH, Draw.withOpacity(0xFF000000, SOFT_SHADOW_OPACITY));
			}
			draw(ui, phase, barNow, width, pad, parts, card, filled);
		}));
	}

	/** The widest row of the label. */
	private static float contentWidth(Canvas ui, BossPhase phase, int parts, boolean card) {
		float first = 0f;
		if ((parts & BossLabelLook.KIND) != 0) {
			first += ui.boldWidthScaled(kind(phase), TextScale.SMALL) + GAP;
		}
		if ((parts & BossLabelLook.VALUE) != 0) {
			first += valueWidth(ui, phase) + GAP;
		}
		float last = 0f;
		if ((parts & BossLabelLook.COUNT) != 0) {
			last += ui.widthScaled(phase.count(), TextScale.SMALL) + GAP;
		}
		if ((parts & BossLabelLook.STEPS) != 0) {
			last += stepsWidth(phase) + GAP;
		}
		// the compact styles have it all on one line; each row ends in a gap that the last part does not need
		return (card ? Math.max(first, last) : first + last) - GAP;
	}

	private static float valueWidth(Canvas ui, BossPhase phase) {
		return ui.boldWidthScaled(phase.value(), VALUE_SIZE) + SMALL_GAP + ui.widthScaled("/ " + phase.total(), TextScale.SMALL);
	}

	private static float stepsWidth(BossPhase phase) {
		return phase.steps() * STEP_WIDTH + (phase.steps() - 1) * STEP_GAP;
	}

	private static String kind(BossPhase phase) {
		return phase.hits() ? "HITS" : "DAMAGE";
	}

	private static void draw(Canvas ui, BossPhase phase, float barNow, float width, float pad, int parts, boolean card, boolean filled) {
		int color = phase.hits() ? SlayerPhaseHudFeature.SHIELD : SlayerPhaseHudFeature.BOSS_RED;
		int textColor = filled ? HudTheme.text() : 0xFFFFFFFF;
		int quiet = filled ? HudTheme.secondary() : 0xFFE6E6E6;
		float y = TOP;
		boolean first = (parts & (BossLabelLook.KIND | BossLabelLook.VALUE)) != 0;
		boolean last = (parts & (BossLabelLook.COUNT | BossLabelLook.STEPS)) != 0;
		boolean oneLine = !card && (first || last);
		if (first) {
			float x = pad;
			if ((parts & BossLabelLook.KIND) != 0) {
				String kind = kind(phase);
				text(ui, kind, x, y + SMALL_DROP, TextScale.SMALL, true, filled, filled ? HudTheme.readable(color) : color);
				x += ui.boldWidthScaled(kind, TextScale.SMALL) + GAP;
			}
			if ((parts & BossLabelLook.VALUE) != 0) {
				text(ui, phase.value(), x, y, VALUE_SIZE, true, filled, textColor);
				x += ui.boldWidthScaled(phase.value(), VALUE_SIZE) + SMALL_GAP;
				String total = "/ " + phase.total();
				text(ui, total, x, y + SMALL_DROP, TextScale.SMALL, false, filled, quiet);
				x += ui.widthScaled(total, TextScale.SMALL) + GAP;
			}
			if (oneLine) {
				lastRow(ui, phase, x, y, width, pad, parts, color, quiet, filled);
			}
			y += card ? VALUE_ROW : LINE_ROW;
		} else if (oneLine) {
			lastRow(ui, phase, pad, y, width, pad, parts, color, quiet, filled);
			y += LINE_ROW;
		}
		if ((parts & BossLabelLook.BAR) != 0) {
			float inner = width - 2f * pad;
			ui.roundedSmooth(pad, y, inner, BAR_HEIGHT, BAR_HEIGHT / 2f, Draw.withOpacity(filled ? HudTheme.text() : 0xFFFFFFFF, TRACK_OPACITY));
			ui.roundedSmooth(pad, y, Math.max(BAR_HEIGHT, inner * barNow), BAR_HEIGHT, BAR_HEIGHT / 2f, color);
			y += BAR_ROW;
		}
		if (card && last) {
			lastRow(ui, phase, pad, y, width, pad, parts, color, quiet, filled);
		}
	}

	/** The phase count and its steps, which go to the right end of the row. */
	private static void lastRow(Canvas ui, BossPhase phase, float x, float y, float width, float pad, int parts, int color, int quiet, boolean filled) {
		if ((parts & BossLabelLook.COUNT) != 0) {
			text(ui, phase.count(), x, y + (STEP_HEIGHT - SMALL_GAP) / 2f + SMALL_DROP, TextScale.SMALL, false, filled, quiet);
		}
		if ((parts & BossLabelLook.STEPS) != 0) {
			float stepX = width - pad - stepsWidth(phase);
			float stepY = y + SMALL_GAP;
			for (int i = 1; i <= phase.steps(); i++) {
				int stepColor = i == phase.step() ? color : Draw.withOpacity(filled ? HudTheme.text() : 0xFFFFFFFF, i < phase.step() ? PAST_STEP_OPACITY : NEXT_STEP_OPACITY);
				ui.roundedSmooth(stepX, stepY, STEP_WIDTH, STEP_HEIGHT, STEP_HEIGHT / 2f, stepColor);
				stepX += STEP_WIDTH + STEP_GAP;
			}
		}
	}

	/** A text; without a panel behind it, with a shadow so it reads over the world. */
	private static void text(Canvas ui, String text, float x, float y, float scale, boolean bold, boolean filled, int color) {
		if (!filled) {
			int shadow = Draw.withOpacity(0xFF000000, SHADOW_OPACITY);
			if (bold) {
				ui.textAtScaledBold(text, x + SHADOW_OFFSET, y + SHADOW_OFFSET, scale, shadow);
			} else {
				ui.textAtScaled(text, x + SHADOW_OFFSET, y + SHADOW_OFFSET, scale, shadow);
			}
		}
		if (bold) {
			ui.textAtScaledBold(text, x, y, scale, color);
		} else {
			ui.textAtScaled(text, x, y, scale, color);
		}
	}

	/** The label as it is set, over a sample boss. */
	private void drawPreview(GuiGraphicsExtractor graphics, Canvas ui, int x, int y, int width, int height) {
		int bossX = x + (width - SAMPLE_WIDTH) / 2;
		int bossY = y + height - SAMPLE_BOTTOM - SAMPLE_HEIGHT;
		ui.rounded(bossX, bossY, SAMPLE_WIDTH, SAMPLE_HEIGHT, Radius.XS, SAMPLE_COLOR);
		ui.fill(bossX + 2, bossY + 4, SAMPLE_WIDTH - 4, 2, SAMPLE_EYES);
		previewPlace[WorldLabel.X] = x + width / 2f;
		previewPlace[WorldLabel.Y] = bossY - PREVIEW_ABOVE_PIXELS - above.get() * 2f;
		previewPlace[WorldLabel.SIZE] = sizeSetting.get() * PREVIEW_SCALE;
		previewPlace[WorldLabel.FADE] = 1f;
		label(graphics, ui, previewPlace, SAMPLE, SAMPLE.bar(), 1f);
	}
}
