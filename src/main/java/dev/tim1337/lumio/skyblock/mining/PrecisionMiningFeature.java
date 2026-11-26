package dev.tim1337.lumio.skyblock.mining;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.util.ServerParticles;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.ColorChoice;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockAddon;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;
import dev.tim1337.lumio.skyblock.mixin.accessor.BreakingBlockAccess;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Precision Mining helper: the Precision Mining perk marks a spot on the ore or Dwarven metal being mined with small particles, and aiming at it
 * gives more mining speed. This draws a clear marker there, on the block's face (a dot, a ring or a small box with a soft glow), hidden by other
 * blocks like the block itself, green while the crosshair is on it. Only looks: it never aims, moves the camera or clicks.
 * <p>
 * The particles: the target is sent as crit particles, and while the player aims at it as happy villager particles (as other mods read them; with
 * Debug on, the first particle of each kind inside the mined block is logged, to check this on the server).
 */
public final class PrecisionMiningFeature extends AddonFeature {
	/** How the marker looks. */
	public enum Style {
		DOT("Dot"), RING("Ring"), BOX("Small box");

		final String label;

		Style(String label) {
			this.label = label;
		}
	}

	/** The marker's radius at size 1, in blocks; a ring's hole; how far the glow reaches beyond it, and how strong it starts. */
	private static final float RADIUS = 0.085f;
	private static final float RING_HOLE = 0.62f;
	private static final float GLOW_REACH = 2.2f;
	private static final float GLOW_OPACITY = 0.35f;
	private static final float MARKER_OPACITY = 0.95f;
	/** How far in front of the face the marker floats, against flicker with the block. */
	private static final float FACE_GAP = 0.004f;
	/** The segments of a round marker. */
	private static final int SEGMENTS = 24;
	/** The crosshair made green: half its length and its thickness, in GUI pixels, as the game's. */
	private static final int CROSSHAIR_HALF = 4;
	private static final int MOST_LOGGED_TYPES = 32;

	private final Choice<Style> style = add(make().choice("style", Component.literal("Style"), Style.RING, value -> Component.literal(value.label)),
		"General");
	private final Toggle accent = add(make().toggle("use_accent", Component.literal("Accent color"), true), "General");
	private final ColorChoice color = add(make().color("color", Component.literal("Color"), SystemColors.BLUE_RGB), "General");
	private final Slider size = add(make().slider("size", Component.literal("Size"), 1f, 0.5f, 2f, 1), "General");
	private final Toggle feedback = add(make().toggle("feedback", Component.literal("Green while aimed at"), true), "General",
		"Green while you aim at the target");
	private final Toggle crosshair = add(make().toggle("crosshair", Component.literal("Green crosshair too"), false), "General");

	private final PrecisionTarget target = new PrecisionTarget();
	private final Set<ParticleType<?>> logged = new HashSet<>();
	private final SubmitNodeCollector.CustomGeometryRenderer drawMarker = this::drawMarker;
	/** What the frame draws, set before the marker is submitted. */
	private Vec3 camera = Vec3.ZERO;
	private float shownAlpha;
	private boolean aimedNow;
	/** The marker's spot and block while it is drawn. Reused. */
	private final double[] spot = new double[PrecisionTarget.AXES];
	private final int[] cell = new int[PrecisionTarget.AXES];

	public PrecisionMiningFeature() {
		super("precision_mining", "Precision Mining helper",
			"A marker on the Precision Mining target.", false);
		pageGroup("While mining");
		iconRgb(SystemColors.BLUE_RGB);
		iconName("target");
		summary("A marker on the mining target");
		color.visibleWhen(() -> !accent.get());
		crosshair.visibleWhen(feedback::get);
	}

	/** Starts listening and drawing; once, while the add-on starts. */
	public void register() {
		ServerParticles.listen(this::particle);
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(this::draw);
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, Identifier.fromNamespaceAndPath(SkyBlockAddon.ID, "precision_crosshair"),
			this::drawCrosshair);
	}

	private void tick(Minecraft client) {
		if (!isEnabled() || client.gameMode == null || client.level == null || !client.gameMode.isDestroying() || !SkyBlock.inSkyBlock()
			|| !SkyBlockArea.inMines()) {
			target.stopped();
			return;
		}
		BlockPos pos = ((BreakingBlockAccess) client.gameMode).evskyblock$destroyPos();
		if (pos == null || !worksOn(client.level.getBlockState(pos))) {
			target.stopped();
			return;
		}
		target.mining(pos.getX(), pos.getY(), pos.getZ());
	}

	/** @return whether the perk works on a block: an ore or a Dwarven metal */
	private static boolean worksOn(BlockState state) {
		Block block = state.getBlock();
		return block == Blocks.COAL_ORE || block == Blocks.IRON_ORE || block == Blocks.GOLD_ORE || block == Blocks.LAPIS_ORE || block == Blocks.REDSTONE_ORE
			|| block == Blocks.DIAMOND_ORE || block == Blocks.EMERALD_ORE || block == Blocks.NETHER_QUARTZ_ORE || TickGlideFeature.dwarvenMetal(state);
	}

	private void particle(ParticleOptions particle, double x, double y, double z, int count) {
		if (!isEnabled()) {
			return;
		}
		ParticleType<?> type = particle.getType();
		long now = System.currentTimeMillis();
		if (DebugHooks.ACTIVE) {
			logOnce(type, x, y, z, count);
		}
		if (type == ParticleTypes.CRIT) {
			target.particle(PrecisionTarget.Kind.TARGET, x, y, z, now);
		} else if (type == ParticleTypes.HAPPY_VILLAGER) {
			target.particle(PrecisionTarget.Kind.AIMED, x, y, z, now);
		}
	}

	/** With Debug on: the first particle of each kind inside the block being mined, to see what the perk sends. */
	private void logOnce(ParticleType<?> type, double x, double y, double z, int count) {
		Minecraft client = Minecraft.getInstance();
		if (client.gameMode == null || !client.gameMode.isDestroying() || logged.size() >= MOST_LOGGED_TYPES) {
			return;
		}
		BlockPos pos = ((BreakingBlockAccess) client.gameMode).evskyblock$destroyPos();
		if (pos != null && PrecisionTarget.inside(x, y, z, pos.getX(), pos.getY(), pos.getZ()) && logged.add(type)) {
			// relative to the block's middle, so no world position is logged
			if (DebugHooks.ACTIVE) {
				DebugHooks.log(SkyBlockDebug.MINING, "Precision Mining: particle {} at {} {} {} from the mined block's middle, count {}", net.minecraft.core.registries.BuiltInRegistries
					.PARTICLE_TYPE.getKey(type), round(x - pos.getX() - 0.5), round(y - pos.getY() - 0.5), round(z - pos.getZ() - 0.5), count);
			}
		}
	}

	private static double round(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

	/** @return the marker's radius in blocks */
	private float radius() {
		return RADIUS * size.get();
	}

	/** @return whether the crosshair is on the target: the server says so, or the crosshair's point on the face is within the marker */
	private boolean aimed(Minecraft client, long now) {
		if (target.aimedByServer(now)) {
			return true;
		}
		HitResult hit = client.hitResult;
		if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
			return false;
		}
		BlockPos pos = block.getBlockPos();
		if (pos.getX() != target.blockX() || pos.getY() != target.blockY() || pos.getZ() != target.blockZ()) {
			return false;
		}
		Vec3 at = hit.getLocation();
		int face = PrecisionTarget.face(target.x(), target.y(), target.z(), target.blockX(), target.blockY(), target.blockZ());
		return PrecisionTarget.face(at.x, at.y, at.z, pos.getX(), pos.getY(), pos.getZ()) == face
			&& PrecisionTarget.onTarget(at.x, at.y, at.z, target.x(), target.y(), target.z(), face, radius());
	}

	private void draw(LevelRenderContext context) {
		long now = System.currentTimeMillis();
		shownAlpha = isEnabled() ? target.alpha(now) : 0f;
		if (shownAlpha <= 0f) {
			aimedNow = false;
			return;
		}
		Minecraft client = Minecraft.getInstance();
		aimedNow = feedback.get() && aimed(client, now);
		camera = client.gameRenderer.mainCamera().position();
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), drawMarker);
	}

	/** Draws the marker flat on the block's face at the target, with its soft glow; the world's depth hides it behind other blocks. */
	private void drawMarker(PoseStack.Pose pose, VertexConsumer consumer) {
		int face = PrecisionTarget.face(target.x(), target.y(), target.z(), target.blockX(), target.blockY(), target.blockZ());
		int axis = face / 2;
		float r = radius();
		// on the face's plane, the marker kept wholly on the block
		double[] at = spot;
		at[0] = target.x();
		at[1] = target.y();
		at[2] = target.z();
		int[] block = cell;
		block[0] = target.blockX();
		block[1] = target.blockY();
		block[2] = target.blockZ();
		for (int i = 0; i < PrecisionTarget.AXES; i++) {
			at[i] = i == axis ? block[i] + (face % 2 == 1 ? 1 + FACE_GAP : -FACE_GAP) : Math.clamp(at[i], block[i] + r, block[i] + 1 - r);
		}
		float cx = (float) (at[0] - camera.x);
		float cy = (float) (at[1] - camera.y);
		float cz = (float) (at[2] - camera.z);
		int rgb = aimedNow ? SystemColors.GREEN_RGB : (accent.get() ? MainSettings.accentRgb() : color.argb()) & 0xFFFFFF;
		int glow = alpha(GLOW_OPACITY) | rgb;
		int clear = rgb;
		int solid = alpha(MARKER_OPACITY) | rgb;
		// the soft glow: from the marker's edge out to nothing
		ring(pose, consumer, axis, cx, cy, cz, r, r * GLOW_REACH, glow, clear);
		switch (style.get()) {
			case DOT -> ring(pose, consumer, axis, cx, cy, cz, 0f, r, solid, solid);
			case RING -> ring(pose, consumer, axis, cx, cy, cz, r * RING_HOLE, r, solid, solid);
			case BOX -> square(pose, consumer, axis, cx, cy, cz, r * 0.8f, solid);
		}
	}

	private int alpha(float opacity) {
		return Math.round(opacity * shownAlpha * 255f) << 24;
	}

	/** A flat ring (a disc with no hole) on a face's plane, from an inner to an outer radius, each edge in its own color. */
	private static void ring(PoseStack.Pose pose, VertexConsumer consumer, int axis, float cx, float cy, float cz, float inner, float outer, int innerArgb,
							 int outerArgb) {
		for (int i = 0; i < SEGMENTS; i++) {
			double a0 = 2.0 * Math.PI * i / SEGMENTS;
			double a1 = 2.0 * Math.PI * (i + 1) / SEGMENTS;
			float u0 = (float) Math.cos(a0);
			float v0 = (float) Math.sin(a0);
			float u1 = (float) Math.cos(a1);
			float v1 = (float) Math.sin(a1);
			vertex(pose, consumer, axis, cx, cy, cz, u0 * inner, v0 * inner, innerArgb);
			vertex(pose, consumer, axis, cx, cy, cz, u0 * outer, v0 * outer, outerArgb);
			vertex(pose, consumer, axis, cx, cy, cz, u1 * outer, v1 * outer, outerArgb);
			vertex(pose, consumer, axis, cx, cy, cz, u1 * inner, v1 * inner, innerArgb);
		}
	}

	private static void square(PoseStack.Pose pose, VertexConsumer consumer, int axis, float cx, float cy, float cz, float half, int argb) {
		vertex(pose, consumer, axis, cx, cy, cz, -half, -half, argb);
		vertex(pose, consumer, axis, cx, cy, cz, half, -half, argb);
		vertex(pose, consumer, axis, cx, cy, cz, half, half, argb);
		vertex(pose, consumer, axis, cx, cy, cz, -half, half, argb);
	}

	/** A point on a face's plane: u and v along the face's two other axes. */
	private static void vertex(PoseStack.Pose pose, VertexConsumer consumer, int axis, float cx, float cy, float cz, float u, float v, int argb) {
		float x = cx + (axis == 0 ? 0f : u);
		float y = cy + (axis == 0 ? u : axis == 1 ? 0f : v);
		float z = cz + (axis == 2 ? 0f : v);
		consumer.addVertex(pose, x, y, z).setColor(argb);
	}

	/** The crosshair in green while it is on the target, if wanted: drawn over the game's own. */
	private void drawCrosshair(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		if (!aimedNow || !crosshair.get() || shownAlpha <= 0f || !Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
			return;
		}
		int x = graphics.guiWidth() / 2;
		int y = graphics.guiHeight() / 2;
		int green = alpha(1f) | SystemColors.GREEN_RGB;
		graphics.fill(x - CROSSHAIR_HALF, y, x + CROSSHAIR_HALF + 1, y + 1, green);
		graphics.fill(x, y - CROSSHAIR_HALF, x + 1, y + CROSSHAIR_HALF + 1, green);
	}
}
