package dev.tim1337.lumio.skyblock.mining;

import dev.tim1337.lumio.api.experimental.draw.WorldLabel;
import dev.tim1337.lumio.api.experimental.util.ServerTicks;
import dev.tim1337.lumio.api.experimental.waypoint.WaypointLook;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * What the Powder Helper draws in the world: the box around each active treasure chest, the line to the newest one, both with the depth test (never
 * through walls), and the seconds left above each chest as plain text with a shadow, only while the place above the chest is in sight.
 */
final class PowderChestDrawing {
	/** A chest's shape inside its block, in blocks. */
	private static final float CHEST_INSET = 1f / 16f;
	private static final float CHEST_HEIGHT = 14f / 16f;
	private static final float FILL_OPACITY = 0.25f;
	private static final float LINE_OPACITY = 0.95f;
	private static final float TRACER_OPACITY = 0.9f;
	/** The tracer starts this far in front of the eye, in blocks, so it leaves from the crosshair. */
	private static final float TRACER_START_BLOCKS = 1f;
	private static final float REFERENCE_LINES = 1080f;
	private static final float REFERENCE_GUI_SCALE = 2f;
	/** The timer stands this high over the chest's bottom, in blocks. */
	private static final double TIMER_HEIGHT_BLOCKS = 1.25;
	/** A wall hit closer than this to the timer's place (squared, in blocks) is the chest itself, which does not hide it. */
	private static final double OWN_BLOCK_SQ = 1.0;
	/** The texts of the timer, "0s" to the whole lifetime, made once. */
	private static final String[] SECONDS = seconds();

	private final PowderHelperFeature feature;
	private final PowderChests chests;
	private final float[] place = new float[WorldLabel.PLACE_VALUES];
	/** The chests whose timer place is hidden by a wall, checked each tick. */
	private final Set<Long> hidden = new HashSet<>();
	private Vec3 camera = Vec3.ZERO;
	private Vec3 tracerStart = Vec3.ZERO;
	private long tracerTo = PowderChests.NONE;
	private float pixelAngle;
	private float widthPixels;
	private final SubmitNodeCollector.CustomGeometryRenderer drawGeometry = this::drawAll;

	PowderChestDrawing(PowderHelperFeature feature, PowderChests chests) {
		this.feature = feature;
		this.chests = chests;
	}

	private static String[] seconds() {
		int most = (int) (PowderChests.LIFETIME_TICKS / 20) + 1;
		String[] texts = new String[most + 1];
		for (int i = 0; i <= most; i++) {
			texts[i] = i + "s";
		}
		return texts;
	}

	/** Checks each chest's timer place against the blocks between the eye and it; a few chests at most, once a tick. */
	void checkSight(Minecraft client) {
		hidden.clear();
		if (chests.isEmpty() || client.player == null || client.level == null) {
			return;
		}
		Vec3 eye = client.player.getEyePosition();
		for (Long block : chests.blocks()) {
			Vec3 target = new Vec3(BlockPos.getX(block) + 0.5, BlockPos.getY(block) + TIMER_HEIGHT_BLOCKS, BlockPos.getZ(block) + 0.5);
			HitResult hit = client.level.clip(new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, client.player));
			if (hit.getType() == HitResult.Type.BLOCK && hit.getLocation().distanceToSqr(target) > OWN_BLOCK_SQ) {
				hidden.add(block);
			}
		}
	}

	/** The world pass: boxes and the tracer, depth tested. */
	void draw(LevelRenderContext context) {
		boolean boxes = feature.highlightOn();
		tracerTo = feature.tracerOn() ? chests.mostRecent() : PowderChests.NONE;
		if (chests.isEmpty() || !boxes && tracerTo == PowderChests.NONE) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		camera = client.gameRenderer.mainCamera().position();
		float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 look = client.player.getViewVector(partial);
		tracerStart = look.scale(TRACER_START_BLOCKS);
		pixelAngle = (float) (2.0 * Math.tan(Math.toRadians(client.options.fov().get()) / 2.0)) / REFERENCE_LINES;
		widthPixels = feature.lineWidthPixels() * client.getWindow().getGuiScale() / REFERENCE_GUI_SCALE;
		PoseStack pose = context.poseStack();
		context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugQuads(), drawGeometry);
	}

	private void drawAll(PoseStack.Pose pose, VertexConsumer consumer) {
		Vec3 cam = camera;
		if (feature.highlightOn()) {
			int rgb = feature.chestRgb();
			boolean filled = feature.filled();
			for (Long block : chests.blocks()) {
				float x0 = (float) (BlockPos.getX(block) + CHEST_INSET - cam.x);
				float y0 = (float) (BlockPos.getY(block) - cam.y);
				float z0 = (float) (BlockPos.getZ(block) + CHEST_INSET - cam.z);
				float x1 = x0 + 1f - 2 * CHEST_INSET;
				float y1 = y0 + CHEST_HEIGHT;
				float z1 = z0 + 1f - 2 * CHEST_INSET;
				if (filled) {
					WaypointLook.boxAt(pose, consumer, x0, y0, z0, x1, y1, z1, rgb, FILL_OPACITY);
				}
				WaypointLook.outlineAt(pose, consumer, x0, y0, z0, x1, y1, z1, rgb, LINE_OPACITY, pixelAngle, widthPixels);
			}
		}
		long target = tracerTo;
		if (target != PowderChests.NONE) {
			float toX = (float) (BlockPos.getX(target) + 0.5 - cam.x);
			float toY = (float) (BlockPos.getY(target) + CHEST_HEIGHT / 2f - cam.y);
			float toZ = (float) (BlockPos.getZ(target) + 0.5 - cam.z);
			WaypointLook.line(pose, consumer, (float) tracerStart.x, (float) tracerStart.y, (float) tracerStart.z, toX, toY, toZ, feature.tracerRgb(),
				TRACER_OPACITY, pixelAngle, widthPixels);
		}
	}

	/** The timers, with the HUD: plain text with a shadow, no plate; white, orange under 20 s, red under 10 s. */
	void drawTimers(GuiGraphicsExtractor graphics) {
		if (chests.isEmpty() || !feature.timerOn()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		long now = ServerTicks.count();
		for (Long block : chests.blocks()) {
			if (hidden.contains(block)) {
				continue;
			}
			double x = BlockPos.getX(block) + 0.5;
			double y = BlockPos.getY(block) + TIMER_HEIGHT_BLOCKS;
			double z = BlockPos.getZ(block) + 0.5;
			if (WorldLabel.place(client, x, y, z, place) < WorldLabel.NEAR) {
				continue;
			}
			int left = Math.min(chests.secondsLeft(block, now), SECONDS.length - 1);
			if (left < 0) {
				continue;
			}
			String text = SECONDS[left];
			graphics.text(client.font, text, Math.round(place[WorldLabel.X] - client.font.width(text) / 2f), Math.round(place[WorldLabel.Y]) - 4,
				PowderChests.timerColor(left), true);
		}
	}
}
