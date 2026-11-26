package dev.tim1337.lumio.skyblock.fishing;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.data.SkyBlock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.FishingHook;

/**
 * Bite timer (Fishing): the countdown the game shows next to the player's own bobber until a fish bites, and its "!!!" when one does, large on the
 * screen. Only that text of the game, read from the name tag at the player's own bobber while it lies in the water; nothing is predicted. The bite
 * changes its color and pops once; a sound on the bite and hiding the text at the bobber are the player's choice.
 */
public class BiteTimerFeature extends AddonPanel {
	/** The name tag is looked for this far around the bobber (across, and up from it), in blocks. */
	private static final double ACROSS = 1.5;
	private static final double UP = 3.0;
	/** What the game shows when a fish bites. */
	private static final String BITE = "!!!";
	/** The pop of a bite: how long, and how much larger at its start. */
	private static final long POP_MILLIS = 250L;
	private static final float POP = 0.35f;
	private static final float PITCH = 1.6f;
	private static final float VOLUME = 0.8f;
	private static final int HEIGHT_PAD = 6;
	private static final String SAMPLE = "1.5";

	/** How large the text is. */
	public enum Size {
		SMALL("Small", 1.5f), NORMAL("Normal", 2f), LARGE("Large", 3f), HUGE("Huge", 4.5f);

		final String label;
		final float scale;

		Size(String label, float scale) {
			this.label = label;
			this.scale = scale;
		}
	}

	private final Choice<Size> size = add(make().choice("size", Component.literal("Size"), Size.NORMAL, value -> Component.literal(value.label), Choice.Style.SEGMENTED),
		"General");
	private final Toggle sound = add(make().toggle("sound", Component.literal("Sound on bite"), false), "General");
	private final Toggle hideAtBobber = add(make().toggle("hide_at_bobber", Component.literal("Hide the text at the bobber"), false), "General");

	/** The game's text at the bobber now, or null; its name tag; and when the last bite began. */
	private String text;
	private int tagId = -1;
	private boolean bitten;
	private long bitAt;

	public BiteTimerFeature() {
		super("bite_timer", "Bite timer", "The bite countdown, large on screen.", false, Anchor.CENTER, false, 0f, 40f);
		iconRgb(SystemColors.BLUE_RGB);
		iconName("timer");
		summary("The bite countdown, large");
		offerNoBackground();
	}

	/** Called every tick: the name tag at the player's own bobber, while it lies in the water. */
	public void tick(Minecraft client) {
		String found = null;
		int foundId = -1;
		FishingHook hook = client.player == null || !isEnabled() || !SkyBlock.allowed() ? null : client.player.fishing;
		if (hook != null && hook.isInWater() && client.level != null) {
			double best = Double.MAX_VALUE;
			for (Entity entity : client.level.getEntities(hook, hook.getBoundingBox().inflate(ACROSS, UP, ACROSS))) {
				if (entity instanceof ArmorStand stand && stand.hasCustomName()) {
					String name = TextColors.noColors(stand.getCustomName().getString()).strip();
					double distance = stand.distanceToSqr(hook);
					if (!name.isEmpty() && distance < best) {
						best = distance;
						found = name;
						foundId = stand.getId();
					}
				}
			}
		}
		boolean bite = found != null && found.contains(BITE);
		if (bite && !bitten) {
			bitAt = System.currentTimeMillis();
			if (sound.get()) {
				client.getSoundManager().play(SimpleSoundInstance.forUI(FishingAlertsFeature.Sound.PLING.event(), PITCH, VOLUME));
			}
		}
		bitten = bite;
		text = found;
		tagId = foundId;
	}

	/** @return whether the game's text at the bobber is hidden: the player chose so, and the timer shows it */
	public boolean hidesTag(Entity stand) {
		return hideAtBobber.get() && tagId >= 0 && stand.getId() == tagId && isEnabled();
	}

	@Override
	public boolean shown(Minecraft client) {
		return text != null;
	}

	@Override
	public int panelWidth() {
		Minecraft client = Minecraft.getInstance();
		return Math.round(client.font.width(text == null ? SAMPLE : text) * size.get().scale) + 2 * PanelKit.PAD;
	}

	@Override
	public int panelHeight() {
		return Math.round(Minecraft.getInstance().font.lineHeight * size.get().scale) + HEIGHT_PAD;
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		String shown = text == null ? SAMPLE : text;
		float scale = size.get().scale;
		int color = HudTheme.text();
		if (bitten) {
			// the bite: its own color, and one short pop
			float t = Math.min(1f, (System.currentTimeMillis() - bitAt) / (float) POP_MILLIS);
			scale *= 1f + POP * (1f - t) * (1f - t);
			color = 0xFF000000 | SystemColors.RED_RGB;
		}
		drawGlass(ui, panelWidth(), panelHeight(), PanelKit.RADIUS);
		float width = client.font.width(shown) * scale;
		float height = client.font.lineHeight * scale;
		ui.textAtScaledBold(shown, (panelWidth() - width) / 2f, (panelHeight() - height) / 2f + 1f, scale, color);
	}
}
