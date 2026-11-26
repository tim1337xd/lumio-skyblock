package dev.tim1337.lumio.skyblock.dungeon.waypoints;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.LumioChat;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.api.experimental.util.WorldLabels;
import dev.tim1337.lumio.api.experimental.util.ModFiles;
import dev.tim1337.lumio.api.experimental.waypoint.WaypointCards;
import dev.tim1337.lumio.api.experimental.waypoint.WaypointLook;
import dev.tim1337.lumio.api.experimental.waypoint.WaypointStyle;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.Action;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.dungeon.CurrentRoom;
import dev.tim1337.lumio.skyblock.dungeon.Dungeon;
import dev.tim1337.lumio.skyblock.dungeon.DungeonKeys;
import dev.tim1337.lumio.skyblock.dungeon.DungeonParse;
import dev.tim1337.lumio.api.feature.KeyPlace;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import dev.tim1337.lumio.api.setting.MultiToggle;
import dev.tim1337.lumio.api.setting.AddonSetting;
import dev.tim1337.lumio.api.experimental.ui.SettingRows;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

/**
 * Waypoints of the dungeon rooms, like Odin's: points kept relative to a room, so they stand in the same place in every run however the room is
 * turned. They are the player's own or imported route data for the fixed room layouts; whether a point shows through walls is the point's own
 * setting. Inside a room the place key adds a point there; a secret point hides once that secret was found. Nothing runs outside a dungeon.
 */
public class DungeonWaypointsFeature extends AddonFeature {
	/** Which points of the room show. */
	public enum Show {
		ALL("All"), SECRETS("Secrets"), NEXT("Next");

		final String label;

		Show(String label) {
			this.label = label;
		}
	}

	/** Which format the import reads first and the export writes. */
	public enum Format {
		ODIN("Odin"), LUMIO("Lumio");

		final String label;

		Format(String label) {
			this.label = label;
		}
	}

	private static final String FILE = "dungeon_waypoints.json";
	private static final RenderType SEE_THROUGH = RenderTypes.textSeeThrough(Identifier.fromNamespaceAndPath("lumio", "textures/white.png"));
	private static final float FILL_OPACITY = 0.28f;
	private static final float FILLED_OPACITY = 0.6f;
	private static final float THROUGH_WALLS_FILL = 0.5f;
	private static final float OUTLINE_OPACITY = 0.9f;
	private static final float REFERENCE_LINES = 1080f;
	/** A secret counts as found when the room's count rises with the player this close to its point, in blocks. */
	private static final double SECRET_NEAR_BLOCKS = 5.0;

	/** The sheet's groups, as in the mockup: the rooms on top, then what shows, placing, the lists, and Delete apart from the rest. */
	private static final String ROOMS = "Rooms";
	private static final String SHOW = "Show";
	private static final String PLACE = "Place in the room";
	private static final String LISTS = "Import and export";
	private static final String DELETE = "Delete";
	/** How long Undo stays after Delete all points. */
	private static final long UNDO_MILLIS = 15_000L;
	private static final String[] MERGE_OR_REPLACE = {"Merge", "Replace"};

	private final AddonSetting<Void> roomsRow = add(SettingRows.buttons("rooms_row", Component.literal("Rooms and points"), this::roomsText, true, null,
		() -> List.of(new SettingRows.Button("Manage…", SettingRows.Kind.PRIMARY, this::openManager))), ROOMS);
	private final Choice<DungeonPointLabel.Mode> label = add(make().choice("label", Component.literal("Label"), DungeonPointLabel.Mode.TITLE,
		value -> Component.literal(value.label()), Choice.Style.SEGMENTED), SHOW, "What shows above each point");
	private final DungeonLabelLayer labels = new DungeonLabelLayer();
	private final List<Toggle> typeSwitches = typeSwitches();
	private final MultiToggle types = add(make().multi("types", Component.literal("Types"), typeSwitches).asChips(), SHOW);
	private final Toggle hideFound = add(make().toggle("hide_found", Component.literal("Hide secrets once found"), true), SHOW);
	private final Choice<Show> show = add(make().choice("show", Component.literal("Points"), Show.ALL, value -> Component.literal(value.label)), SHOW);
	private final Toggle throughWalls = add(make().toggle("through_walls", Component.literal("Through walls"), true), SHOW);
	/** The look: the general one of all waypoints, or the dungeon waypoints' own. */
	private final DungeonLook look = addLook();
	private final Choice<DungeonWaypoint.Type> newType = add(make().choice("new_type", Component.literal("New points are"), DungeonWaypoint.Type.SECRET,
		value -> Component.literal(value.label())), PLACE);
	private final Toggle newFilled = add(make().toggle("new_filled", Component.literal("New points filled"), false), PLACE);
	private final AddonSetting<Void> importRow = add(SettingRows.buttons("import_row", Component.literal("Import"),
		() -> "Copy an Odin or Lumio list; it is recognized by itself, and checked before anything changes", false, null,
		() -> List.of(new SettingRows.Button("From clipboard", SettingRows.Kind.NORMAL, this::importClipboard))), LISTS);
	private final Choice<Format> format = make().choice("format", Component.literal("Export as"), Format.ODIN, value -> Component.literal(value.label),
		Choice.Style.SEGMENTED);
	private final AddonSetting<Void> exportRow = add(SettingRows.buttons("export_row", Component.literal("Export"), this::exportText, false, format,
		() -> List.of(new SettingRows.Button("Copy", SettingRows.Kind.NORMAL, this::export))),
		LISTS);
	private final AddonSetting<Void> deleteRow = add(SettingRows.buttons("delete_row", Component.literal("Delete all points"), this::deleteText, false, null,
		this::deleteButtons), DELETE);

	/** The points before Delete all points, while Undo is offered, and until when. */
	private Map<String, List<DungeonWaypoint>> deleted;
	private long deletedAt;
	/** The import waiting for the player's answer. */
	private DungeonWaypointFormat.ImportSummary pending;

	/** The points by room name. Changed on the game's thread only. */
	private final Map<String, List<DungeonWaypoint>> rooms = new LinkedHashMap<>();
	/** The rooms whose points are switched off. */
	private final java.util.Set<String> offRooms = new java.util.TreeSet<>();
	/** The rooms of this run, in the order they were entered. */
	private final java.util.LinkedHashSet<String> runRooms = new java.util.LinkedHashSet<>();
	/** How many of each room's points came from an Odin import. */
	private final Map<String, Integer> fromOdin = new java.util.TreeMap<>();
	private static final double PICK_BLOCKS = 6.0;
	/** Secrets found this run, by their place in the world. */
	private final Set<Long> found = new HashSet<>();
	private int lastSecretCount = -1;
	private boolean loaded;
	private KeyMapping removeKey;

	public DungeonWaypointsFeature() {
		super("dungeon_waypoints", "Dungeon waypoints", "Points per room, imported or placed.", false);
		iconRgb(SystemColors.PURPLE_RGB);
		iconName("map");
		summary("Per room, turned with it");
		// the format is chosen in the Export row; kept as a setting of its own so its value stays saved
		add(format, LISTS).visibleWhen(() -> false);
		SettingRows.colors(types, typeColors());
	}

	private DungeonLook addLook() {
		DungeonLook made = new DungeonLook();
		for (AddonSetting<?> setting : made.settings()) {
			add(setting, DungeonLook.GROUP);
		}
		return made;
	}

	private static List<Toggle> typeSwitches() {
		List<Toggle> switches = new ArrayList<>();
		for (DungeonWaypoint.Type type : DungeonWaypoint.Type.values()) {
			switches.add(make().toggle("type_" + type.name().toLowerCase(java.util.Locale.ROOT), Component.literal(type.label()), true, Toggle.Style.CHECKBOX));
		}
		return switches;
	}

	private static int[] typeColors() {
		DungeonWaypoint.Type[] all = DungeonWaypoint.Type.values();
		int[] colors = new int[all.length];
		for (int i = 0; i < all.length; i++) {
			colors[i] = all[i].rgb();
		}
		return colors;
	}

	/** @return whether points of a type are drawn: an off type is not */
	private boolean typeOn(DungeonWaypoint.Type type) {
		return typeSwitches.get(type.ordinal()).get();
	}

	private String roomsText() {
		int points = 0;
		for (List<DungeonWaypoint> room : rooms.values()) {
			points += room.size();
		}
		return String.format(java.util.Locale.ROOT, "%,d rooms · %,d points", rooms.size(), points);
	}

	private String exportText() {
		return format.get() == Format.ODIN ? "To the clipboard, to share with Odin players" : "To the clipboard, Lumio's own with titles and colors";
	}

	private String deleteText() {
		return undoOpen() ? "Every point is gone. Undo brings them back." : "Removes every point in every room. Asks first.";
	}

	private boolean undoOpen() {
		return deleted != null && System.currentTimeMillis() - deletedAt < UNDO_MILLIS;
	}

	private List<SettingRows.Button> deleteButtons() {
		if (undoOpen()) {
			return List.of(new SettingRows.Button("Undo", SettingRows.Kind.NORMAL, this::undoDelete));
		}
		return List.of(new SettingRows.Button("Delete all points", SettingRows.Kind.DANGER, this::askDelete));
	}

	/** Starts with the add-on: loads the points, and takes the place key inside a known room. */
	public void register() {
		Thread loader = new Thread(this::load, "Lumio dungeon waypoints");
		loader.setDaemon(true);
		loader.start();
		WaypointCards.claim(this::place);
		WorldLabels.add((graphics, tracker) -> labels.draw(graphics));
		// Lumio's place key places and edits the points of a room too: listed here, in "Place in the room"
		KeyMapping placeKey = WaypointCards.placeKey();
		if (placeKey != null) {
			addKey(placeKey, KeyPlace.WORLD);
		}
		removeKey = DungeonKeys.register("dungeon_waypoint_remove");
		addKey(removeKey, KeyPlace.WORLD);
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (DungeonKeys.presses(removeKey) > 0) {
				removeLookedAt(client);
			}
			if (isEnabled() && Dungeon.inClear()) {
				look.refresh();
				labels.checkSight(client);
			}
		});
		Dungeon.listen(new Dungeon.Listener() {
			@Override
			public void left() {
				found.clear();
				runRooms.clear();
				lastSecretCount = -1;
				cachedRoom = "";
			}
		});
	}

	public Show show() {
		return show.get();
	}

	/** @return how many points a room has */
	public int count(String room) {
		List<DungeonWaypoint> points = rooms.get(room);
		return points == null ? 0 : points.size();
	}

	// ------------------------------------------------------------------------------------------------------ placing

	/** The place key's tap on a block inside a known room: a new point there, or the card of the one already there. */
	private boolean place(int x, int y, int z) {
		if (!isEnabled() || !Dungeon.in()) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		if (CurrentRoom.data() == null) {
			// in a dungeon, but the room is not known: the card says so and keeps nothing
			client.gui.setScreen(new DungeonPointCard(null, "", "points cannot be kept here yet", null, new int[DungeonWaypoint.Type.values().length],
				newType.get(), 0, null));
			return true;
		}
		String room = CurrentRoom.name();
		int[] at = CurrentRoom.toModel(x, y, z);
		DungeonWaypoint existing = at(room, at);
		List<DungeonWaypoint> roomPoints = rooms.getOrDefault(room, List.of());
		int[] counts = new int[DungeonWaypoint.Type.values().length];
		int foundSecrets = 0;
		for (DungeonWaypoint point : roomPoints) {
			counts[point.type().ordinal()]++;
			int[] world = CurrentRoom.modelToWorld(point.x(), point.y(), point.z());
			if (point.secret() && found.contains(BlockPos.asLong(world[0], world[1], world[2]))) {
				foundSecrets++;
			}
		}
		String badge = roomPoints.size() + (roomPoints.size() == 1 ? " point · " : " points · ") + foundSecrets + " secrets found"
			+ (existing != null ? " · edit mode" : "");
		client.gui.setScreen(new DungeonPointCard(room, at[0] + " · " + at[1] + " · " + at[2], badge, existing, counts, newType.get(), existing != null ? roomPoints.indexOf(existing) + 1 : roomPoints.size() + 1,
			new DungeonPointCard.Done() {
				@Override
				public void save(String title, DungeonWaypoint.Type type) {
					List<DungeonWaypoint> points = rooms.computeIfAbsent(room, key -> new ArrayList<>());
					DungeonWaypoint point = existing != null ? existing.withTitle(title).withType(type)
						: DungeonWaypoint.block(at[0], at[1], at[2], title, type).withFilled(newFilled.get());
					int index = existing == null ? -1 : points.indexOf(existing);
					if (index >= 0) {
						points.set(index, point);
					} else {
						points.add(point);
					}
					saveSoon();
				}

				@Override
				public void delete() {
					List<DungeonWaypoint> points = rooms.get(room);
					if (points != null && existing != null) {
						points.remove(existing);
						saveSoon();
					}
				}
			}));
		return true;
	}

	/** Removes the point the player looks at, in the room they are in. */
	public void removeLookedAt(Minecraft client) {
		if (!isEnabled() || client.player == null || CurrentRoom.data() == null) {
			return;
		}
		HitResult hit = client.player.pick(6.0, 1f, false);
		if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
			return;
		}
		BlockPos pos = block.getBlockPos();
		String room = CurrentRoom.name();
		DungeonWaypoint point = at(room, CurrentRoom.toModel(pos.getX(), pos.getY(), pos.getZ()));
		if (point != null) {
			rooms.get(room).remove(point);
			saveSoon();
			LumioChat.info(Component.literal("Removed the point" + (point.title().isEmpty() ? "" : " \"" + point.title() + "\"") + " from " + room + "."));
		}
	}

	private DungeonWaypoint at(String room, int[] at) {
		List<DungeonWaypoint> points = rooms.get(room);
		if (points != null) {
			for (DungeonWaypoint point : points) {
				if (point.x() == at[0] && point.y() == at[1] && point.z() == at[2]) {
					return point;
				}
			}
		}
		return null;
	}

	// ------------------------------------------------------------------------------------------------------ secrets

	/** A block the player used (a chest, a lever, a skull): a secret point there counts as found. */
	public void used(BlockPos pos) {
		if (isEnabled() && Dungeon.in()) {
			found.add(pos.asLong());
		}
	}

	/** The action bar: when the room's secret count rises, the nearest secret point counts as found. */
	public void actionBar(String text) {
		if (!isEnabled() || !Dungeon.in()) {
			return;
		}
		Matcher matcher = DungeonParse.ROOM_SECRETS.matcher(text);
		if (!matcher.find()) {
			return;
		}
		int count;
		try {
			count = Integer.parseInt(matcher.group(1));
		} catch (NumberFormatException e) {
			return;
		}
		if (lastSecretCount >= 0 && count > lastSecretCount) {
			markNearestSecret(Minecraft.getInstance());
		}
		lastSecretCount = count;
	}

	private void markNearestSecret(Minecraft client) {
		List<DungeonWaypoint> points = rooms.get(CurrentRoom.name());
		if (points == null || client.player == null || CurrentRoom.data() == null) {
			return;
		}
		long nearest = 0;
		double nearestSq = SECRET_NEAR_BLOCKS * SECRET_NEAR_BLOCKS;
		for (DungeonWaypoint point : points) {
			if (!point.secret()) {
				continue;
			}
			int[] at = CurrentRoom.modelToWorld(point.x(), point.y(), point.z());
			double distanceSq = client.player.distanceToSqr(at[0] + 0.5, at[1] + 0.5, at[2] + 0.5);
			if (distanceSq < nearestSq && !found.contains(BlockPos.asLong(at[0], at[1], at[2]))) {
				nearestSq = distanceSq;
				nearest = BlockPos.asLong(at[0], at[1], at[2]);
			}
		}
		if (nearestSq < SECRET_NEAR_BLOCKS * SECRET_NEAR_BLOCKS) {
			found.add(nearest);
		}
	}

	// ------------------------------------------------------------------------------------------------------ drawing

	/** Draws the points of the room the player is in, with the world. */
	public void draw(LevelRenderContext context) {
		if (!isEnabled() || !Dungeon.inClear() || CurrentRoom.data() == null) {
			return;
		}
		String here = CurrentRoom.name();
		runRooms.add(here);
		if (offRooms.contains(here)) {
			return;
		}
		List<DungeonWaypoint> points = rooms.get(CurrentRoom.name());
		Minecraft client = Minecraft.getInstance();
		if (points == null || points.isEmpty() || client.player == null || !cache(points)) {
			return;
		}
		Vec3 cam = client.gameRenderer.mainCamera().position();
		pixelAngle = (float) (2.0 * Math.tan(Math.toRadians(client.options.fov().get()) / 2.0)) / REFERENCE_LINES;
		next = show.get() == Show.NEXT ? nearestOpen(points.size(), client.player.position()) : -1;
		boolean anyThrough = false;
		camera = cam;
		labels.points(points, worldKeys, label.get());
		labels.look(look.style(), look.distance());
		boolean anyDepth = false;
		for (int i = 0; i < points.size(); i++) {
			boolean pointShown = shown(points.get(i), i);
			boolean through = throughWalls.get() && points.get(i).throughWalls();
			labels.mark(i, pointShown, through);
			if (pointShown) {
				anyThrough |= through;
				anyDepth |= !through;
			}
		}
		drawn = points;
		labels.frameDrawn();
		PoseStack pose = context.poseStack();
		if (anyDepth) {
			context.submitNodeCollector().submitCustomGeometry(pose, RenderTypes.debugQuads(), drawDepth);
		}
		if (anyThrough) {
			context.submitNodeCollector().submitCustomGeometry(pose, SEE_THROUGH, drawThrough);
		}
	}

	/** What the frame draws, set before its geometry is asked for, so the drawing makes nothing new per frame. */
	private Vec3 camera = Vec3.ZERO;
	private List<DungeonWaypoint> drawn = List.of();
	private int next = -1;
	private float pixelAngle;
	private final net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer drawDepth = (last, consumer) -> drawAll(last, consumer, false);
	private final net.minecraft.client.renderer.SubmitNodeCollector.CustomGeometryRenderer drawThrough = (last, consumer) -> drawAll(last, consumer, true);

	/** The current room's points in the world: their boxes, six numbers each, and their blocks; made again when the room, its turn or its points change. */
	private String cachedRoom = "";
	private CurrentRoom.Rotation cachedRotation;
	private int cachedVersion = -1;
	private int version;
	private float[] worldBoxes = new float[0];
	private long[] worldKeys = new long[0];

	/** @return whether the room's points are in the world, worked out again only when something changed */
	private boolean cache(List<DungeonWaypoint> points) {
		CurrentRoom.Rotation rotation = CurrentRoom.rotation();
		if (rotation == null) {
			return false;
		}
		String room = CurrentRoom.name();
		if (room.equals(cachedRoom) && rotation == cachedRotation && version == cachedVersion && worldKeys.length == points.size()) {
			return true;
		}
		cachedRoom = room;
		cachedRotation = rotation;
		cachedVersion = version;
		worldBoxes = new float[points.size() * 6];
		worldKeys = new long[points.size()];
		for (int i = 0; i < points.size(); i++) {
			DungeonWaypoint point = points.get(i);
			int[] at = CurrentRoom.modelToWorld(point.x(), point.y(), point.z());
			float[] box = turnedBox(point, rotation);
			for (int k = 0; k < 6; k++) {
				worldBoxes[i * 6 + k] = box[k] + at[k % 3];
			}
			worldKeys[i] = BlockPos.asLong(at[0], at[1], at[2]);
		}
		return true;
	}

	private boolean shown(DungeonWaypoint point, int index) {
		if (!typeOn(point.type())) {
			return false;
		}
		if (hideFound.get() && point.hideFound() && point.secret() && found.contains(worldKeys[index])) {
			return false;
		}
		WaypointStyle style = look.style();
		if (style.maxDistance() > 0) {
			long key = worldKeys[index];
			double distance = Math.sqrt(camera.distanceToSqr(net.minecraft.core.BlockPos.getX(key) + 0.5, net.minecraft.core.BlockPos.getY(key) + 0.5,
				net.minecraft.core.BlockPos.getZ(key) + 0.5));
			if (!style.inReach(distance)) {
				return false;
			}
		}
		return switch (show.get()) {
			case ALL -> true;
			case SECRETS -> point.secret();
			case NEXT -> index == next;
		};
	}

	/** @return the nearest point not found yet, or -1 */
	private int nearestOpen(int count, Vec3 player) {
		int nearest = -1;
		double nearestSq = Double.MAX_VALUE;
		for (int i = 0; i < count; i++) {
			if (found.contains(worldKeys[i])) {
				continue;
			}
			double dx = worldBoxes[i * 6] + 0.5 - player.x;
			double dy = worldBoxes[i * 6 + 1] + 0.5 - player.y;
			double dz = worldBoxes[i * 6 + 2] + 0.5 - player.z;
			double distanceSq = dx * dx + dy * dy + dz * dz;
			if (distanceSq < nearestSq) {
				nearestSq = distanceSq;
				nearest = i;
			}
		}
		return nearest;
	}

	private void drawAll(PoseStack.Pose pose, VertexConsumer consumer, boolean through) {
		List<DungeonWaypoint> points = drawn;
		Vec3 cam = camera;
		WaypointStyle style = look.style();
		if (worldKeys.length != points.size()) {
			return;
		}
		for (int i = 0; i < points.size(); i++) {
			DungeonWaypoint point = points.get(i);
			if (!shown(point, i) || (throughWalls.get() && point.throughWalls()) != through) {
				continue;
			}
			int at = i * 6;
			float x0 = (float) (worldBoxes[at] - cam.x);
			float y0 = (float) (worldBoxes[at + 1] - cam.y);
			float z0 = (float) (worldBoxes[at + 2] - cam.z);
			float x1 = (float) (worldBoxes[at + 3] - cam.x);
			float y1 = (float) (worldBoxes[at + 4] - cam.y);
			float z1 = (float) (worldBoxes[at + 5] - cam.z);
			int rgb = point.argb() & 0xFFFFFF;
			float alpha = (point.argb() >>> 24) / 255f;
			if (style.boxed()) {
				float fill = style.fillOpacity((point.filled() ? FILLED_OPACITY : FILL_OPACITY) * alpha * (through ? THROUGH_WALLS_FILL : 1f));
				if (fill > 0f) {
					WaypointLook.boxAt(pose, consumer, x0, y0, z0, x1, y1, z1, rgb, fill);
				}
				WaypointLook.outlineAt(pose, consumer, x0, y0, z0, x1, y1, z1, rgb, OUTLINE_OPACITY * alpha, pixelAngle, style.lineWidth());
			}
			// dungeon points have no beam of their own: one stands on them only with the beam marker
			if (style.beamOn(false)) {
				WaypointLook.beam(pose, consumer, (x0 + x1) / 2f, y0, (z0 + z1) / 2f, rgb, alpha * style.beamStrength(), style.beamHeight());
			}
		}
	}

	/** @return the point's box within its block, turned with the room */
	static float[] turnedBox(DungeonWaypoint point, CurrentRoom.Rotation rotation) {
		float[] a = rotation.intoWorld((float) point.minX() - 0.5f, (float) point.minZ() - 0.5f);
		float[] b = rotation.intoWorld((float) point.maxX() - 0.5f, (float) point.maxZ() - 0.5f);
		return new float[] {Math.min(a[0], b[0]) + 0.5f, (float) point.minY(), Math.min(a[1], b[1]) + 0.5f, Math.max(a[0], b[0]) + 0.5f, (float) point.maxY(),
			Math.max(a[1], b[1]) + 0.5f};
	}

	// ------------------------------------------------------------------------------------------------------ the manager

	/** Opens the manager over the screen now open. */
	public void openManager() {
		Minecraft client = Minecraft.getInstance();
		client.gui.setScreen(new DungeonWaypointsScreen(client.gui.screen(), this));
	}

	/** @return a room's points, in their order; empty for a room without any */
	public List<DungeonWaypoint> points(String room) {
		List<DungeonWaypoint> points = rooms.get(room);
		return points == null ? List.of() : List.copyOf(points);
	}

	/** Replaces a room's points, as the manager edited them, and saves. */
	public void setPoints(String room, List<DungeonWaypoint> points) {
		if (points.isEmpty()) {
			rooms.remove(room);
			fromOdin.remove(room);
		} else {
			rooms.put(room, new ArrayList<>(points));
		}
		saveSoon();
	}

	/** @return whether a room's points show */
	public boolean roomOn(String room) {
		return !offRooms.contains(room);
	}

	public void setRoomOn(String room, boolean on) {
		if (on) {
			offRooms.remove(room);
		} else {
			offRooms.add(room);
		}
		saveSoon();
	}

	/** @return the rooms of this run, in the order they were entered */
	public List<String> runRooms() {
		return List.copyOf(runRooms);
	}

	/** @return the room the player is in, or "" */
	public String roomHere() {
		return Dungeon.in() && CurrentRoom.data() != null ? CurrentRoom.name() : "";
	}

	/** @return how many of a room's points came from an Odin import */
	public int fromOdin(String room) {
		return Math.min(fromOdin.getOrDefault(room, 0), points(room).size());
	}

	/** @return whether a secret point was found this run (only known in the room the player is in) */
	public boolean found(String room, DungeonWaypoint point) {
		if (!room.equals(roomHere())) {
			return false;
		}
		int[] world = CurrentRoom.modelToWorld(point.x(), point.y(), point.z());
		return found.contains(BlockPos.asLong(world[0], world[1], world[2]));
	}

	/** @return the block the player looks at, in the room's model, while the player is in that room; or null */
	public int[] lookedAt(String room) {
		Minecraft client = Minecraft.getInstance();
		if (!room.equals(roomHere()) || client.player == null) {
			return null;
		}
		HitResult hit = client.player.pick(PICK_BLOCKS, 1f, false);
		if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
			return null;
		}
		BlockPos pos = block.getBlockPos();
		return CurrentRoom.toModel(pos.getX(), pos.getY(), pos.getZ());
	}

	/** @return a list read from text: Lumio's own, or Odin's converted once */
	public Map<String, List<DungeonWaypoint>> readList(String text) {
		return DungeonWaypointFormat.read(text, CurrentRoom::shapeOf);
	}

	/** @return whether a text is Odin's list rather than Lumio's */
	public static boolean isOdin(String text) {
		return text != null && !text.contains(DungeonWaypointFormat.MARK);
	}

	/**
	 * Takes in an import: each room's points replace the room's own, or are added to them where the room is merged (a point on a block the room
	 * already has replaces that one).
	 */
	public void apply(Map<String, List<DungeonWaypoint>> read, java.util.Set<String> merged, boolean odin) {
		for (Map.Entry<String, List<DungeonWaypoint>> room : read.entrySet()) {
			boolean merge = merged.contains(room.getKey());
			List<DungeonWaypoint> points = merge ? new ArrayList<>(points(room.getKey())) : new ArrayList<>();
			for (DungeonWaypoint point : room.getValue()) {
				points.removeIf(old -> old.x() == point.x() && old.y() == point.y() && old.z() == point.z());
				points.add(point);
			}
			rooms.put(room.getKey(), points);
			int before = merge ? fromOdin.getOrDefault(room.getKey(), 0) : 0;
			if (odin) {
				fromOdin.put(room.getKey(), before + room.getValue().size());
			} else if (!merge) {
				fromOdin.remove(room.getKey());
			}
		}
		saveSoon();
	}

	/** @return rooms written in Odin's format or Lumio's own */
	public String export(Map<String, List<DungeonWaypoint>> chosen, boolean odin) {
		return odin ? OdinWaypoints.write(chosen, CurrentRoom::shapeOf) : DungeonWaypointFormat.write(chosen);
	}

	/** @return every room with points, in name order */
	public Map<String, List<DungeonWaypoint>> allPoints() {
		return new java.util.TreeMap<>(rooms);
	}

	// ------------------------------------------------------------------------------------------------------ lists

	private void importClipboard() {
		String text = Minecraft.getInstance().keyboardHandler.getClipboard();
		askImport(DungeonWaypointFormat.summarize(text, CurrentRoom::shapeOf), "the clipboard");
	}

	/** Asks before an import changes anything: what was found, Merge or Replace, Cancel or Import. */
	private void askImport(DungeonWaypointFormat.ImportSummary summary, String from) {
		if (summary == null || summary.rooms().isEmpty()) {
			LumioChat.alert(Component.literal("No dungeon waypoints in " + from + " (Odin's list or Lumio's)."));
			return;
		}
		pending = summary;
		String title = summary.format() + (summary.packed() ? " list (packed)" : " list") + " found";
		SettingRows.ask(new SettingRows.Question(title, summary.line(), List.of(MERGE_OR_REPLACE), 0,
			"Import", option -> take(option == 1)));
	}

	/** Takes in the waiting import: merged (an imported room replaces that room), or in place of every room. */
	private void take(boolean replace) {
		DungeonWaypointFormat.ImportSummary summary = pending;
		pending = null;
		if (summary == null) {
			return;
		}
		if (replace) {
			rooms.clear();
		}
		for (Map.Entry<String, List<DungeonWaypoint>> room : summary.rooms().entrySet()) {
			rooms.put(room.getKey(), new ArrayList<>(room.getValue()));
		}
		saveSoon();
		LumioChat.info(Component.literal("Imported " + summary.points() + " points in " + summary.rooms().size() + " rooms."));
	}

	private String exported() {
		return format.get() == Format.ODIN ? OdinWaypoints.write(rooms, CurrentRoom::shapeOf) : DungeonWaypointFormat.write(rooms);
	}

	private void export() {
		Minecraft.getInstance().keyboardHandler.setClipboard(exported());
		LumioChat.info(Component.literal("Copied " + rooms.size() + " rooms in " + format.get().label + "'s format."));
	}

	/** Asks before every point goes. */
	private void askDelete() {
		SettingRows.ask(new SettingRows.Question("Delete every point?", roomsText() + " · you can undo it for a moment", List.of(), 0,
			"Delete", option -> clearAll()));
	}

	private void clearAll() {
		deleted = new LinkedHashMap<>(rooms);
		deletedAt = System.currentTimeMillis();
		rooms.clear();
		saveSoon();
	}

	private void undoDelete() {
		if (deleted == null) {
			return;
		}
		deleted.forEach(rooms::putIfAbsent);
		deleted = null;
		saveSoon();
	}

	private void load() {
		Path file = ModFiles.file(FILE);
		try {
			if (Files.exists(file) && Files.size(file) <= DungeonWaypointFormat.MOST_TEXT_BYTES) {
				String text = Files.readString(file, StandardCharsets.UTF_8);
				Map<String, List<DungeonWaypoint>> read = DungeonWaypointFormat.read(text, CurrentRoom::shapeOf);
				java.util.Set<String> off = DungeonWaypointFormat.offRooms(text);
				Map<String, Integer> odinCounts = DungeonWaypointFormat.fromOdin(text);
				Minecraft.getInstance().execute(() -> {
					offRooms.addAll(off);
					fromOdin.putAll(odinCounts);
					read.forEach((room, points) -> rooms.putIfAbsent(room, new ArrayList<>(points)));
					version++;
					loaded = true;
				});
				return;
			}
		} catch (IOException e) {
			Logs.warn("Could not read the dungeon waypoints", e);
		}
		Minecraft.getInstance().execute(() -> loaded = true);
	}

	/** Writes the points in the background, through a temporary file. */
	private void saveSoon() {
		version++;
		if (!loaded) {
			return;
		}
		String text = DungeonWaypointFormat.write(rooms, offRooms, fromOdin);
		Thread.ofVirtual().name("Lumio dungeon waypoints save").start(() -> {
			try {
				ModFiles.writeAtomically(ModFiles.file(FILE), text);
			} catch (IOException e) {
				Logs.warn("Could not save the dungeon waypoints", e);
			}
		});
	}
}
