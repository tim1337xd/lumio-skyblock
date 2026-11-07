package dev.tim1337.lumio.skyblock.slayer;

import dev.tim1337.lumio.api.experimental.draw.BlockStyle;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.WorldDraw;
import dev.tim1337.lumio.api.experimental.ui.Toasts;
import dev.tim1337.lumio.api.experimental.util.Sight;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.ColorChoice;
import dev.tim1337.lumio.api.setting.Toggle;
import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * Helpers for the Voidgloom Seraph (the Enderman slayer): an alert and a highlight when the player's own boss, or the carried player's, throws its beacon. Others'
 * beacons nearby stay plain ({@link BeaconOwners}). The beacon flies as an entity, which is highlighted only while it is in sight (it is worn by an invisible stand, so an entity outline is not the way), and then is a
 * real block, drawn against the world's depth so a wall hides it, until it is destroyed ({@link BeaconWatch}).
 */
public class VoidgloomHelperFeature extends AddonFeature {
	private static final float LINE_WIDTH = 2.5f;
	private static final float FILL_OPACITY = 0.3f;
	private static final float ANIMATION_SPEED = 1f;
	private static final float ALERT_SECONDS = 3f;
	private static final float ALERT_VOLUME = 0.7f;
	private static final int SIGHT_CHECK_TICKS = 2;
	private static final int NONE = Integer.MIN_VALUE;
	// how the block's fill breathes
	private static final float LEAST_PULSE = 0.45f;
	private static final float PULSE_SECONDS = 2.4f;
	private static final float NANOS_PER_SECOND = 1_000_000_000f;
	// where the thrown beacon is: worn on a stand's head as a small block, or a falling block
	private static final double WORN_CENTER = 1.72;
	private static final float WORN_HALF = 0.32f;
	private static final double FALLING_CENTER = 0.5;
	private static final float FALLING_HALF = 0.5f;

	private final Toggle beaconAlert = add(make().toggle("beacon_alert", Component.literal("Beacon alert"), true), "Beacon",
		"A short note, and in the Slayer HUD, for your boss and the one you carry");
	private final Toggle beaconHighlight = add(make().toggle("beacon_highlight", Component.literal("Beacon highlight"), true), "Beacon",
		"While you can see it, until it is destroyed");
	private final ColorChoice beaconColor = add(make().color("beacon_color", Component.literal("Color"), SystemColors.RED_RGB), "Beacon");
	private int thrownInSight = NONE;
	private int tick;

	{
		beaconColor.under(beaconHighlight);
	}

	public VoidgloomHelperFeature() {
		super("voidgloom_helper", "Voidgloom helper", "Helpers for the Voidgloom Seraph: an alert and a highlight when your boss throws its beacon.", false);
		iconRgb(SystemColors.PURPLE_RGB);
		iconName("orb");
		summary("Beacon alert and highlight for your own boss");
	}

	public void register() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(this::drawBeacon);
	}

	private void tick(Minecraft client) {
		if (++tick % SIGHT_CHECK_TICKS != 0) {
			return;
		}
		Entity thrown = highlightOn() ? SlayerBosses.thrownBeacon() : null;
		thrownInSight = thrown != null && client.player != null && Sight.visible(client, thrown) ? thrown.getId() : NONE;
	}

	/** The thrown beacon where it flies, while in sight; then its block with an outline and a soft fill that pulses slowly, which the world's depth hides behind a wall. */
	private void drawBeacon(LevelRenderContext context) {
		if (!highlightOn()) {
			return;
		}
		float pulse = LEAST_PULSE + (1f - LEAST_PULSE) * (0.5f + 0.5f * Mth.sin(System.nanoTime() / NANOS_PER_SECOND * Mth.TWO_PI / PULSE_SECONDS));
		BlockPos block = SlayerBosses.beaconBlock();
		if (block != null) {
			WorldDraw.highlight(context, block, Shapes.block(), BlockStyle.GLOW, beaconColor(), LINE_WIDTH, FILL_OPACITY * pulse, ANIMATION_SPEED);
			return;
		}
		Entity thrown = SlayerBosses.thrownBeacon();
		if (thrown != null && thrown.getId() == thrownInSight) {
			Vec3 at = thrown.getPosition(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false));
			double centerY = at.y + (thrown instanceof ArmorStand ? WORN_CENTER : FALLING_CENTER);
			float half = thrown instanceof ArmorStand ? WORN_HALF : FALLING_HALF;
			BlockPos near = BlockPos.containing(at.x, centerY, at.z);
			double cx = at.x - near.getX();
			double cy = centerY - near.getY();
			double cz = at.z - near.getZ();
			WorldDraw.highlight(context, near, Shapes.box(cx - half, cy - half, cz - half, cx + half, cy + half, cz + half), BlockStyle.GLOW, beaconColor(), LINE_WIDTH, FILL_OPACITY, ANIMATION_SPEED);
		}
	}

	private boolean highlightOn() {
		return isEnabled() && beaconHighlight.get();
	}

	/** A watched boss just threw its beacon. */
	void beaconThrown() {
		if (beaconAlert()) {
			Toasts.brief("orb", "Beacon!", "Your boss threw it: break it", ALERT_SECONDS);
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, ALERT_VOLUME));
		}
	}

	/** @return whether a watched boss's beacon is shown as an alert */
	public boolean beaconAlert() {
		return isEnabled() && beaconAlert.get();
	}

	/** @return whether the beacons have to be told apart at all */
	public boolean beaconWanted() {
		return beaconAlert() || highlightOn();
	}

	/** @return the highlight's color as 0xRRGGBB */
	public int beaconColor() {
		return beaconColor.get() & 0xFFFFFF;
	}
}
