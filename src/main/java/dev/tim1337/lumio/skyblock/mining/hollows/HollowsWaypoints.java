package dev.tim1337.lumio.skyblock.mining.hollows;

import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.WorldLabel;
import dev.tim1337.lumio.api.experimental.waypoint.WaypointLook;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/**
 * The places of the Crystal Hollows map as waypoints in the world, in the look of Lumio's waypoints: a beam and a box in the place's color, with
 * the depth test, and its name and distance as a plain label.
 */
final class HollowsWaypoints {
	private static final float BEAM_OPACITY = 0.8f;
	private static final float BOX_OPACITY = 0.3f;
	/** The label stands this far above the block, in blocks. */
	private static final double LABEL_ABOVE_BLOCKS = 1.45;
	/** A label fades out closer than this, in blocks, so it never fills the screen when the player stands on the place. */
	private static final double FADE_NEAR_BLOCKS = 4.0;
	private static final float SHOWN_OPACITY = 0.01f;

	private final HollowsMapFeature feature;
	private final float[] place = new float[WorldLabel.PLACE_VALUES];
	private Map<HollowsStructure, HollowsLobbies.Spot> drawn = Map.of();
	private Vec3 camera = Vec3.ZERO;
	private final SubmitNodeCollector.CustomGeometryRenderer drawGeometry = this::drawAll;

	HollowsWaypoints(HollowsMapFeature feature) {
		this.feature = feature;
	}

	void draw(LevelRenderContext context) {
		drawn = feature.waypointSpots();
		if (drawn.isEmpty()) {
			return;
		}
		camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
		PoseStack pose = context.poseStack();
		context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugQuads(), drawGeometry);
	}

	private void drawAll(PoseStack.Pose pose, VertexConsumer consumer) {
		Vec3 cam = camera;
		for (Map.Entry<HollowsStructure, HollowsLobbies.Spot> entry : drawn.entrySet()) {
			HollowsLobbies.Spot spot = entry.getValue();
			int rgb = feature.colorOf(entry.getKey());
			float x = (float) (spot.x() + 0.5 - cam.x);
			float y = (float) (spot.y() - cam.y);
			float z = (float) (spot.z() + 0.5 - cam.z);
			WaypointLook.beam(pose, consumer, x, y, z, rgb, BEAM_OPACITY);
			WaypointLook.box(pose, consumer, x, y, z, rgb, BOX_OPACITY);
		}
	}

	/** The labels, with the HUD, for the places the world pass drew. */
	void drawLabels(GuiGraphicsExtractor graphics) {
		if (drawn.isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		Vec3 eye = client.gameRenderer.mainCamera().position();
		Canvas ui = null;
		for (Map.Entry<HollowsStructure, HollowsLobbies.Spot> entry : drawn.entrySet()) {
			HollowsLobbies.Spot spot = entry.getValue();
			double x = spot.x() + 0.5;
			double y = spot.y() + LABEL_ABOVE_BLOCKS;
			double z = spot.z() + 0.5;
			if (WorldLabel.place(client, x, y, z, place) < WorldLabel.NEAR) {
				continue;
			}
			double distance = Math.sqrt(eye.distanceToSqr(x, y, z));
			float opacity = (float) Math.clamp((distance - 1.0) / (FADE_NEAR_BLOCKS - 1.0), 0.0, 1.0);
			if (opacity <= SHOWN_OPACITY) {
				continue;
			}
			if (ui == null) {
				ui = Draw.hudCanvas(graphics, 0xFF000000 | MainSettings.accentRgb(), 0f, false);
			}
			place[WorldLabel.SIZE] = WaypointLook.labelSize(distance);
			place[WorldLabel.FADE] = 1f;
			String name = entry.getKey().title;
			String far = WaypointLook.distance(distance);
			Canvas canvas = ui;
			WorldLabel.draw(graphics, place, WaypointLook.plainLabelWidth(ui, name, far), WaypointLook.plainLabelHeight(), opacity,
				() -> WaypointLook.plainLabel(canvas, name, far, WaypointLook.State.SHOWN));
		}
	}
}
