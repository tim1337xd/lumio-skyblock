package dev.tim1337.lumio.skyblock.dungeon.waypoints;

import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.WorldLabel;
import dev.tim1337.lumio.api.experimental.waypoint.WaypointLook;
import dev.tim1337.lumio.api.experimental.waypoint.WaypointStyle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The labels above the current room's dungeon points, drawn with the HUD in the look of Lumio's waypoint labels. A label shows only while its
 * point does: the world pass marks which points it drew this frame, and a point that walls hide (one not drawn through walls) loses its label
 * too, checked a few points a tick against the blocks between the eye and it.
 */
final class DungeonLabelLayer {
	/** The label stands this far above the top of the point's block, in blocks, so it never lies over the box. */
	private static final double LABEL_ABOVE_BLOCKS = 0.45;
	private static final float SHOWN_OPACITY = 0.01f;
	private static final int SIGHT_CHECKS_PER_TICK = 6;
	/** A wall hit closer than this to the label's place (squared, in blocks) is the point's own block, which does not hide it. */
	private static final double OWN_BLOCK_SQ = 2.25;

	private final float[] place = new float[WorldLabel.PLACE_VALUES];
	private long[] keys = new long[0];
	private String[] texts = new String[0];
	private String[] icons = new String[0];
	private int[] colors = new int[0];
	/** The distance each label last showed, in whole blocks, and its text: made again only when the blocks change. */
	private long[] shownMeters = new long[0];
	private String[] distances = new String[0];
	private WaypointStyle style = WaypointStyle.DEFAULT;
	private boolean showDistance;
	private boolean[] shown = new boolean[0];
	private boolean[] through = new boolean[0];
	private boolean[] blocked = new boolean[0];
	private DungeonPointLabel.Mode mode;
	private int sightTurn;
	/** Whether every label still waits for its first check against walls. */
	private boolean fresh;
	/** Whether this frame's world pass drew the points; the HUD draws the labels only then, once. */
	private boolean drawnThisFrame;

	/** Takes the room's points in the world (their blocks in {@code worldKeys}); remade only when they or the label setting changed. */
	void points(List<DungeonWaypoint> points, long[] worldKeys, DungeonPointLabel.Mode labelMode) {
		if (worldKeys == keys && labelMode == mode) {
			return;
		}
		boolean samePlaces = worldKeys == keys;
		keys = worldKeys;
		mode = labelMode;
		int count = worldKeys.length;
		texts = new String[count];
		icons = new String[count];
		colors = new int[count];
		shownMeters = new long[count];
		distances = new String[count];
		for (int i = 0; i < count; i++) {
			texts[i] = DungeonPointLabel.text(labelMode, points.get(i).title(), i + 1);
			icons[i] = points.get(i).type().icon();
			colors[i] = points.get(i).argb() & 0xFFFFFF;
			shownMeters[i] = -1;
			distances[i] = "";
		}
		if (!samePlaces) {
			shown = new boolean[count];
			through = new boolean[count];
			blocked = new boolean[count];
			fresh = true;
		}
	}

	/** The look the labels draw by this frame, and whether they have the distance. */
	void look(WaypointStyle look, boolean distance) {
		style = look;
		showDistance = distance;
	}

	/** @return whether labels show at all: where they say something, or mark the point by their icon */
	private boolean labelled() {
		return mode != DungeonPointLabel.Mode.OFF || style.iconLabel();
	}

	/** @return what the labels say now, in the room's order */
	List<String> texts() {
		return List.of(texts);
	}

	/** The world pass of this frame: whether a point shows, and whether it shows through walls. */
	void mark(int index, boolean pointShown, boolean throughWalls) {
		if (index < shown.length) {
			shown[index] = pointShown;
			through[index] = throughWalls;
		}
	}

	void frameDrawn() {
		drawnThisFrame = true;
	}

	/** Checks a few labels against walls, in turn (all of them once after the points changed). */
	void checkSight(Minecraft client) {
		int count = keys.length;
		if (count == 0 || !labelled() || client.player == null || client.level == null) {
			return;
		}
		Vec3 eye = client.player.getEyePosition();
		int checks = fresh ? count : Math.min(SIGHT_CHECKS_PER_TICK, count);
		fresh = false;
		for (int n = 0; n < checks; n++) {
			sightTurn = (sightTurn + 1) % count;
			if (through[sightTurn] || !shown[sightTurn]) {
				blocked[sightTurn] = false;
				continue;
			}
			long key = keys[sightTurn];
			Vec3 target = new Vec3(BlockPos.getX(key) + 0.5, BlockPos.getY(key) + 1.0 + LABEL_ABOVE_BLOCKS / 2, BlockPos.getZ(key) + 0.5);
			HitResult hit = client.level.clip(new ClipContext(eye, target, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, client.player));
			blocked[sightTurn] = hit.getType() == HitResult.Type.BLOCK && hit.getLocation().distanceToSqr(target) > OWN_BLOCK_SQ;
		}
	}

	/** Draws the labels of the points this frame's world pass drew; a HUD layer that stands under every panel. */
	void draw(GuiGraphicsExtractor graphics) {
		if (!drawnThisFrame) {
			return;
		}
		drawnThisFrame = false;
		if (!labelled()) {
			return;
		}
		WaypointStyle look = style;
		boolean iconLabel = look.iconLabel();
		Minecraft client = Minecraft.getInstance();
		Vec3 eye = client.gameRenderer.mainCamera().position();
		Canvas ui = null;
		for (int i = 0; i < keys.length; i++) {
			String text = look.showName() ? texts[i] : "";
			if (!shown[i] || blocked[i] || (text.isEmpty() && !showDistance && !iconLabel)) {
				continue;
			}
			double x = BlockPos.getX(keys[i]) + 0.5;
			double y = BlockPos.getY(keys[i]) + 1.0 + LABEL_ABOVE_BLOCKS;
			double z = BlockPos.getZ(keys[i]) + 0.5;
			if (WorldLabel.place(client, x, y, z, place) < WorldLabel.NEAR) {
				continue;
			}
			double distance = Math.sqrt(eye.distanceToSqr(x, y, z));
			float opacity = look.nearOpacity(distance);
			if (opacity <= SHOWN_OPACITY) {
				continue;
			}
			if (ui == null) {
				ui = Draw.hudCanvas(graphics, 0xFF000000 | MainSettings.accentRgb(), 0f, false);
			}
			place[WorldLabel.SIZE] = look.labelScale(distance);
			place[WorldLabel.FADE] = 1f;
			String far = showDistance ? distanceText(i, distance) : "";
			Canvas canvas = ui;
			if (iconLabel) {
				String icon = icons[i];
				int rgb = colors[i];
				WorldLabel.draw(graphics, place, WaypointLook.iconLabelWidth(ui, text, far, look.iconSize()), WaypointLook.iconLabelHeight(look.iconSize()), opacity,
					() -> WaypointLook.iconLabel(canvas, icon, text, far, rgb, WaypointLook.State.SHOWN, look.textShadow(), look.iconSize()));
			} else if (look.textBackground()) {
				int number = i + 1;
				int rgb = colors[i];
				WorldLabel.draw(graphics, place, WaypointLook.labelWidth(ui, text, far), WaypointLook.labelHeight(), opacity,
					() -> WaypointLook.label(canvas, number, text, far, rgb, WaypointLook.State.SHOWN));
			} else {
				WorldLabel.draw(graphics, place, WaypointLook.plainLabelWidth(ui, text, far), WaypointLook.plainLabelHeight(), opacity,
					() -> WaypointLook.plainLabel(canvas, text, far, WaypointLook.State.SHOWN, look.textShadow()));
			}
		}
	}

	/** @return a label's distance as it shows, made again only when the whole blocks change */
	private String distanceText(int index, double distance) {
		long meters = Math.round(distance);
		if (meters != shownMeters[index]) {
			shownMeters[index] = meters;
			distances[index] = WaypointLook.distance(distance);
		}
		return distances[index];
	}
}
