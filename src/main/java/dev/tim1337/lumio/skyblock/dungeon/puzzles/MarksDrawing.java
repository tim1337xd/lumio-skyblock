package dev.tim1337.lumio.skyblock.dungeon.puzzles;

import dev.tim1337.lumio.api.experimental.draw.WorldLabel;
import dev.tim1337.lumio.api.experimental.waypoint.WaypointLook;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a set of {@link PuzzleMarks} in the world, depth tested (so never through walls): its boxes and lines with the level, its labels with the
 * HUD. Shared by the room puzzles and the Floor 7 devices; one per set of marks, so it keeps the camera of its frame without allocating.
 */
public final class MarksDrawing {
	private static final float REFERENCE_LINES = 1080f;
	private static final float OUTLINE_PIXELS = 2f;
	/** A weaker mark's outline: its width, and how faint it gets at weight 0. */
	private static final float WEAK_OUTLINE_PIXELS = 1f;
	private static final float WEAK_OPACITY = 0.5f;
	private static final float LINE_PIXELS = 3f;
	private static final float OPACITY = 0.9f;
	private static final float BEAM_OPACITY = 0.8f;

	private final PuzzleMarks marks;
	private final float[] place = new float[WorldLabel.PLACE_VALUES];
	private final SubmitNodeCollector.CustomGeometryRenderer drawBoxes = this::drawAll;
	private Vec3 camera = Vec3.ZERO;
	private float pixelAngle;
	private float fill;

	public MarksDrawing(PuzzleMarks marks) {
		this.marks = marks;
	}

	/** The boxes and lines, with the level. @param fill how strongly filled boxes are filled */
	public void draw(LevelRenderContext context, float fill) {
		if (marks.boxes().isEmpty() && marks.lines().isEmpty() && marks.beams().isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		this.fill = fill;
		camera = client.gameRenderer.mainCamera().position();
		pixelAngle = (float) (2.0 * Math.tan(Math.toRadians(client.options.fov().get()) / 2.0)) / REFERENCE_LINES;
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), RenderTypes.debugQuads(), drawBoxes);
	}

	private void drawAll(PoseStack.Pose pose, VertexConsumer consumer) {
		Vec3 cam = camera;
		for (PuzzleMarks.Box box : marks.boxes()) {
			float x0 = (float) (box.x0() - cam.x);
			float y0 = (float) (box.y0() - cam.y);
			float z0 = (float) (box.z0() - cam.z);
			float x1 = (float) (box.x1() - cam.x);
			float y1 = (float) (box.y1() - cam.y);
			float z1 = (float) (box.z1() - cam.z);
			float weight = box.weight();
			if (box.filled()) {
				WaypointLook.boxAt(pose, consumer, x0, y0, z0, x1, y1, z1, box.rgb(), fill * weight);
			}
			// a weaker mark: a thinner, fainter outline
			WaypointLook.outlineAt(pose, consumer, x0, y0, z0, x1, y1, z1, box.rgb(), OPACITY * (WEAK_OPACITY + (1f - WEAK_OPACITY) * weight), pixelAngle,
				weight < 1f ? WEAK_OUTLINE_PIXELS : OUTLINE_PIXELS);
		}
		for (PuzzleMarks.Beam beam : marks.beams()) {
			WaypointLook.beam(pose, consumer, (float) (beam.x() + 0.5 - cam.x), (float) (beam.y() - cam.y), (float) (beam.z() + 0.5 - cam.z), beam.rgb(),
				BEAM_OPACITY);
		}
		for (PuzzleMarks.Line line : marks.lines()) {
			WaypointLook.line(pose, consumer, (float) (line.x0() - cam.x), (float) (line.y0() - cam.y), (float) (line.z0() - cam.z), (float) (line.x1() - cam.x),
				(float) (line.y1() - cam.y), (float) (line.z1() - cam.z), line.rgb(), OPACITY, pixelAngle, LINE_PIXELS);
		}
	}

	/** The labels, with the HUD. */
	public void drawLabels(GuiGraphicsExtractor graphics) {
		if (marks.labels().isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		for (PuzzleMarks.Label label : marks.labels()) {
			if (WorldLabel.place(client, label.x(), label.y(), label.z(), place) >= WorldLabel.NEAR) {
				graphics.centeredText(client.font, label.text(), Math.round(place[WorldLabel.X]), Math.round(place[WorldLabel.Y]) - 4, 0xFF000000 | label.rgb());
			}
		}
	}
}
