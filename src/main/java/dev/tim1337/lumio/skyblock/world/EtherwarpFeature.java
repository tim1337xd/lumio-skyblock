package dev.tim1337.lumio.skyblock.world;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.ColorChoice;
import dev.tim1337.lumio.api.setting.Toggle;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.api.experimental.draw.BlockPreview;
import dev.tim1337.lumio.api.experimental.draw.BlockStyle;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.WorldDraw;
import dev.tim1337.lumio.skyblock.data.SkyBlock;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Shows where an etherwarp lands: while holding an Aspect of the Void or End with Etherwarp and sneaking, or an Etherwarp Conduit, the target
 * block is drawn in a block overlay style, and in red where the warp would fail. It only marks a block the player already sees (the ray stops at
 * the first solid block and the box is depth tested) and sends nothing. The target is found by {@link EtherwarpTarget}.
 */
public class EtherwarpFeature extends AddonFeature {
	private static final float LINE_WIDTH = 2.5f;
	private static final float FILL_OPACITY = 0.3f;
	private static final float ANIMATION_SPEED = 1f;
	private static final long PREVIEW_MILLIS = 2000L;

	private final Choice<BlockStyle> mode = add(make().choice("mode", Component.literal("Style"), BlockStyle.GLOW, BlockStyle::displayName,
		Choice.Style.SEGMENTED), "General");
	private final ColorChoice color = add(make().color("color", Component.literal("Color"), SystemColors.GREEN_RGB).followsMenu(), "General");
	private final Toggle showFail = add(make().toggle("show_fail", Component.literal("Show when it fails"), true), "General");
	private final ColorChoice failColor = add(make().color("fail_color", Component.literal("Color when it fails"), SystemColors.RED_RGB),
		"General");
	private final Toggle fullBlock = add(make().toggle("full_block", Component.literal("Full block"), false), "General",
		"Off: only what is there, such as a slab or snow");

	private final EtherwarpTarget target = new EtherwarpTarget();

	// what the last ray was cast from, so it is only cast again when the view or the world (each tick) may have changed
	private double lastEyeX = Double.NaN;
	private double lastEyeY;
	private double lastEyeZ;
	private float lastYaw;
	private float lastPitch;
	private int lastRange;
	private long lastGameTime;

	public EtherwarpFeature() {
		super("etherwarp", "Etherwarp", "Where your etherwarp lands, and red where it would fail.", false);
		iconRgb(SystemColors.PURPLE_RGB);
		iconName("portal");
		summary("Where your etherwarp lands, red if it would fail");
		failColor.under(showFail);
	}

	@Override
	public boolean hasPreview() {
		return true;
	}

	/** The block in the chosen style; with "Show when it fails" every other moment as a warp that would fail, drawn as the world does. */
	@Override
	public void drawPreview(Canvas ui, int x, int y, int width, int height) {
		boolean fails = previewFails();
		BlockStyle shown = fails && mode.get() == BlockStyle.SHADER ? BlockStyle.GLOW : mode.get();
		BlockPreview.draw(ui, x, y, width, height, shown, (fails ? failColor.get() : color.get()) & 0xFFFFFF, LINE_WIDTH, shown == BlockStyle.OUTLINE ? 0f : FILL_OPACITY, ANIMATION_SPEED);
	}

	@Override
	public String previewNote() {
		return previewFails() ? "Red: here the warp would fail" : "Your target block";
	}

	private boolean previewFails() {
		return showFail.get() && System.currentTimeMillis() / PREVIEW_MILLIS % 2 == 1;
	}

	public void register() {
		LevelRenderEvents.COLLECT_SUBMITS.register(this::draw);
	}

	private void draw(LevelRenderContext context) {
		if (!isEnabled() || !SkyBlock.allowed()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (player == null || level == null || client.gui.screen() != null) {
			return;
		}
		int range = target.range(player.getMainHandItem());
		if (range == 0 || target.needsSneak() && !player.isShiftKeyDown()) {
			return;
		}
		cast(client, player, level, range);
		if (!target.found() || !target.valid() && !showFail.get()) {
			return;
		}
		BlockPos at = target.pos();
		VoxelShape shape = fullBlock.get() ? Shapes.block() : level.getBlockState(at).getShape(level, at);
		if (shape.isEmpty()) {
			shape = Shapes.block();
		}
		boolean valid = target.valid();
		// the gradient uses the menu colors, which would hide the red: a failing target glows in its color instead
		BlockStyle shown = !valid && mode.get() == BlockStyle.SHADER ? BlockStyle.GLOW : mode.get();
		int rgb = (valid ? color.get() : failColor.get()) & 0xFFFFFF;
		WorldDraw.highlight(context, at, shape, shown, rgb, LINE_WIDTH, FILL_OPACITY, ANIMATION_SPEED);
	}

	/** Casts the ray from the eyes along the view, unless the eyes, the view, the reach and the game tick are all unchanged. */
	private void cast(Minecraft client, LocalPlayer player, ClientLevel level, int range) {
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		double eyeX = Mth.lerp(partial, player.xo, player.getX());
		double eyeY = Mth.lerp(partial, player.yo, player.getY()) + player.getEyeHeight();
		double eyeZ = Mth.lerp(partial, player.zo, player.getZ());
		float yaw = player.getViewYRot(partial);
		float pitch = player.getViewXRot(partial);
		long gameTime = level.getGameTime();
		if (eyeX == lastEyeX && eyeY == lastEyeY && eyeZ == lastEyeZ && yaw == lastYaw && pitch == lastPitch && range == lastRange
			&& gameTime == lastGameTime) {
			return;
		}
		lastEyeX = eyeX;
		lastEyeY = eyeY;
		lastEyeZ = eyeZ;
		lastYaw = yaw;
		lastPitch = pitch;
		lastRange = range;
		lastGameTime = gameTime;
		// the view direction as the game computes it, scaled to the reach
		double pitchRadians = pitch * Mth.DEG_TO_RAD;
		double yawRadians = -yaw * Mth.DEG_TO_RAD;
		double horizontal = Math.cos(pitchRadians) * range;
		target.cast(level, eyeX, eyeY, eyeZ, Math.sin(yawRadians) * horizontal, -Math.sin(pitchRadians) * range, Math.cos(yawRadians) * horizontal);
	}
}
