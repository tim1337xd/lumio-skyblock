package dev.tim1337.lumio.skyblock.dungeon;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.skyblock.hud.MapMarker;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * The dungeon map as a HUD panel, without a header: the map is the panel. It shows what the game's own map item shows (the rooms found so far, their
 * kinds, doors and checkmarks), each room as one connected shape with soft corners, the names of rooms the player was in, their secrets, and the
 * players. It never reads the world to show more than the map: unopened rooms are dimmed with a "?", and space not on the map stays empty.
 */
public class DungeonMapFeature extends AddonPanel {
	/** Which rooms are named. */
	public enum RoomNames {
		OFF("Off"), PUZZLES("Puzzles"), ALL("All");

		final String label;

		RoomNames(String label) {
			this.label = label;
		}
	}

	/** When the players have a name tag: only while the leap menu is open (to pick whom to leap to), always, or never. */
	public enum PlayerNames {
		LEAP("Only in leap menu"), ALWAYS("Always"), OFF("Off");

		final String label;

		PlayerNames(String label) {
			this.label = label;
		}
	}

	/** How the teammates are shown: their own face, or an arrow in their class color. */
	public enum Teammates {
		HEADS("Heads"), ARROWS("Arrows");

		final String label;

		Teammates(String label) {
			this.label = label;
		}
	}

	/** How rooms seen on the map but not entered look. */
	public enum Unopened {
		DIM("Dim + ?"), HIDDEN("Hidden");

		final String label;

		Unopened(String label) {
			this.label = label;
		}
	}

	private static final int PAD = PanelKit.PAD;
	private static final int READ_TICKS = 5;
	private static final int HOTBAR_MAP_SLOT = 8;
	/** GUI pixels per map pixel at size 1. */
	private static final float BASE_SCALE = 1.25f;
	/** The map's gap between two rooms, in map pixels. */
	private static final int GAP = 4;
	/** A room's corner radius, as a share of a tile. */
	private static final float CORNER = 0.16f;
	/** The checkmark's size, as a share of a tile. */
	private static final float CHECK = 0.62f;
	/** How tall a bold "?" is at text scale 1, the drop of the marks' soft shadow (a share of their size), and its color. */
	private static final float QUESTION_HEIGHT = 7f;
	private static final float SHADOW_DROP = 0.07f;
	private static final int MARK_SHADOW = 0x59000000;
	/** Below these tile sizes in GUI pixels, names, secret chips and name tags leave on their own. */
	private static final float NAMES_FROM = 18f;
	private static final float CHIPS_FROM = 15f;
	private static final float TAGS_FROM = 12f;
	/** A room name's text scale, and the smallest it may shrink to before it wraps. */
	private static final float NAME_SCALE = TextScale.SMALL * 0.8f;
	private static final float NAME_SMALLEST = TextScale.SMALL * 0.6f;
	private static final float CHIP_SCALE = TextScale.SMALL * 0.62f;
	/** The secrets count follows the map's size: its scale at this tile size, how far it may grow or shrink, and the smallest still readable. */
	private static final float CHIP_TILE = 20f;
	private static final float CHIP_MOST = 1.6f;
	private static final float CHIP_LEAST = 0.8f;
	/** The mark and the count in one tile: the margin to its edges, the gap between them, and the smallest the mark shrinks to (a share of the tile). */
	private static final float STACK_MARGIN = 1f;
	private static final float STACK_GAP = 0.5f;
	private static final float CHECK_LEAST = 0.4f;
	private static final float TAG_SCALE = TextScale.SMALL * 0.62f;
	/** The player names of configs from before: Team and All are shown always now. */
	private static final String NAMES_KEY = "player_names";
	private static final java.util.Set<String> OLD_ALWAYS = java.util.Set.of("TEAM", "ALL");
	/** A rotated map is drawn smaller, so its corners stay inside the panel. */
	private static final float ROTATED_FIT = 0.7f;
	private static final float DEGREES_PER_ROTATION_STEP = 22.5f;
	private static final int WITHER_DOOR = 0xFF0E0E10;
	private static final int WITHER_EDGE = 0xFF5A5A60;
	private static final int TAG_BACKGROUND = 0xD0141416;
	/** The secrets count's outline: its color, and the offsets it is drawn at around the text (x, y pairs, in GUI pixels). */
	private static final int SECRETS_OUTLINE = 0xD0101012;
	private static final float[] OUTLINE_STEPS = {-0.6f, 0f, 0.6f, 0f, 0f, -0.6f, 0f, 0.6f};
	// the sample map: where the game starts its grid, a room, the gap between rooms, and a door across it
	private static final int SAMPLE_START = 5;
	private static final int SAMPLE_ROOM = 16;
	private static final int SAMPLE_GAP = 4;
	private static final int SAMPLE_STEP = SAMPLE_ROOM + SAMPLE_GAP;
	private static final int SAMPLE_DOOR = 4;
	private static final int SAMPLE_DOOR_FROM = 6;

	/** Its own range, narrower than the panels' (0.5 to 2): a map smaller than this has unreadable rooms, a larger one covers the view. */
	private final Slider size = add(make().slider("size", Component.literal("Size"), 1f, 0.6f, 1.6f, 2).percent(), "General");
	private final Choice<RoomNames> roomNames = add(make().choice("room_name_show", Component.literal("Room names"), RoomNames.PUZZLES,
		value -> Component.literal(value.label), Choice.Style.SEGMENTED), "General");
	private final Choice<Teammates> teammates = add(make().choice("teammates", Component.literal("Teammates"), Teammates.HEADS,
		value -> Component.literal(value.label), Choice.Style.SEGMENTED), "General");
	private final Choice<PlayerNames> playerNames = add(make().choice("player_names", Component.literal("Player names"), PlayerNames.LEAP,
		value -> Component.literal(value.label), Choice.Style.SEGMENTED), "General");
	private final Toggle secrets = add(make().toggle("room_secrets", Component.literal("Secrets per room"), true), "General",
		"Found / total in rooms you were in");
	private final Toggle countOverCheck = add(make().toggle("count_over_check", Component.literal("Count in small rooms"), false),
		"General", "Where a room is too small for both");
	private final Choice<Unopened> unopened = add(make().choice("unopened", Component.literal("Unopened rooms"), Unopened.DIM,
		value -> Component.literal(value.label), Choice.Style.SEGMENTED), "General", "Shown on the map, not entered yet");
	private final Toggle background = add(make().toggle("background", Component.literal("Background"), true), "Look");
	private final Toggle rotate = add(make().toggle("rotate", Component.literal("Rotate with player"), false), "Look",
		"Turns the map so the way you look is up");

	private final DungeonMapData map = new DungeonMapData();
	/** The names of the rooms the player was in this run, by tile, and whether each is a puzzle. */
	private final String[] tileNames = new String[DungeonMapData.GRID * DungeonMapData.GRID];
	private final boolean[] tilePuzzle = new boolean[DungeonMapData.GRID * DungeonMapData.GRID];
	/** The secrets found and in all of each room the player was in, by tile; -1 while not known. */
	private final int[] secretsFound = new int[DungeonMapData.GRID * DungeonMapData.GRID];
	private final int[] secretsTotal = new int[DungeonMapData.GRID * DungeonMapData.GRID];
	private final String[] secretTexts = new String[DungeonMapData.GRID * DungeonMapData.GRID];
	/** The other players' places on the map, in map pixels, their facing in degrees, and who they are; filled from the map's markers. */
	private final float[] markerX = new float[Dungeon.MOST_PLAYERS];
	private final float[] markerY = new float[Dungeon.MOST_PLAYERS];
	private final float[] markerYaw = new float[Dungeon.MOST_PLAYERS];
	private final DungeonPlayer[] markerPlayer = new DungeonPlayer[Dungeon.MOST_PLAYERS];
	private final net.minecraft.world.entity.player.PlayerSkin[] markerSkin = new net.minecraft.world.entity.player.PlayerSkin[Dungeon.MOST_PLAYERS];
	private int markerCount;
	private int ticks;
	/** For the debug log only: how long a room takes to show after it was entered, and the game's map item as last seen. */
	private final MapDelay delay = new MapDelay();
	private int mapItemHash;
	/** The tile the player stood on at the last read of the map. */
	private int readForTile = -1;
	/** What stands for the secrets found in a room the game has not told the count of yet: "?/6". */
	private static final String UNKNOWN_FOUND = "?/";
	private final RoomNameFit nameFit = new RoomNameFit();
	/** The rectangle a room's mark is centered on (see {@link MapMarks}). Reused. */
	private final int[] markRect = new int[4];
	private static final DungeonMapData SAMPLE = sample();
	/** The sample's names, secrets and players, as in the mockup. */
	private static final Map<Integer, String> SAMPLE_NAMES = Map.of(20, "Water Board", 33, "Ice Path", 26, "Blue Skulls");
	private static final Map<Integer, int[]> SAMPLE_SECRETS = Map.of(0, new int[] {4, 4}, 7, new int[] {5, 7}, 10, new int[] {1, 3}, 16,
		new int[] {4, 6}, 18, new int[] {3, 5}, 25, new int[] {1, 2}, 27, new int[] {2, 2}, 30, new int[] {2, 2});
	private static final float[][] SAMPLE_PLAYERS = {{38, 33, 210}, {96, 28, 160}, {98, 68, 300}, {14, 92, 20}};
	private static final String[] SAMPLE_PLAYER_NAMES = {"", "Kiwi", "Rae", "Nox"};
	private static final DungeonClass[] SAMPLE_CLASSES = {DungeonClass.NONE, DungeonClass.MAGE, DungeonClass.BERSERK, DungeonClass.TANK};

	public DungeonMapFeature() {
		super("dungeon_map", "Map", "The game's map, drawn clean. Nothing more.", false, Anchor.LEFT, false, 8f, 8f);
		iconRgb(SystemColors.TEAL_RGB);
		iconName("map");
		summary("Rooms the game map shows");
		countOverCheck.visibleWhen(secrets::get);
		offerNoBackground();
		java.util.Arrays.fill(secretsFound, -1);
		java.util.Arrays.fill(secretsTotal, -1);
		Dungeon.listen(new Dungeon.Listener() {
			@Override
			public void left() {
				map.reset();
				delay.reset();
				java.util.Arrays.fill(tileNames, null);
				java.util.Arrays.fill(tilePuzzle, false);
				java.util.Arrays.fill(secretsFound, -1);
				java.util.Arrays.fill(secretsTotal, -1);
				java.util.Arrays.fill(secretTexts, null);
				markerCount = 0;
				markerShown.clear();
				mapItemHash = 0;
				readForTile = -1;
			}
		});
	}

	/**
	 * The sample in the HUD editor, as in the mockup: a whole floor drawn the way the game's map draws it, with rooms of every shape joined across
	 * their gaps, doors (a wither door and the blood door among them), white and green checks and unopened rooms, read like a real map.
	 */
	private static DungeonMapData sample() {
		String[] layout = {"aobbpr", "accydd", "acckef", "ggmnef", "ghhqe.", "ijlu.."};
		Map<Character, Integer> colors = Map.ofEntries(Map.entry('a', 63), Map.entry('o', 62), Map.entry('b', 85), Map.entry('p', 66),
			Map.entry('r', 18), Map.entry('c', 63), Map.entry('y', 74), Map.entry('d', 63), Map.entry('k', 82), Map.entry('e', 63), Map.entry('f', 85),
			Map.entry('g', 63), Map.entry('m', 66), Map.entry('n', 63), Map.entry('h', 63), Map.entry('q', 63), Map.entry('i', 63), Map.entry('j', 30),
			Map.entry('l', 63), Map.entry('u', 66));
		String green = "akqi";
		String unopened = "bfpr";
		byte[] map = new byte[DungeonMapData.SIZE * DungeonMapData.SIZE];
		java.util.Set<Character> checked = new java.util.HashSet<>();
		for (int row = 0; row < DungeonMapData.GRID; row++) {
			for (int column = 0; column < DungeonMapData.GRID; column++) {
				char room = layout[row].charAt(column);
				if (room == '.') {
					continue;
				}
				byte color = (byte) (int) colors.get(room);
				int x = SAMPLE_START + column * SAMPLE_STEP;
				int y = SAMPLE_START + row * SAMPLE_STEP;
				boolean east = column + 1 < DungeonMapData.GRID && layout[row].charAt(column + 1) == room;
				boolean south = row + 1 < DungeonMapData.GRID && layout[row + 1].charAt(column) == room;
				boolean corner = east && south && layout[row + 1].charAt(column + 1) == room;
				fill(map, x, y, SAMPLE_ROOM + (east ? SAMPLE_GAP : 0), SAMPLE_ROOM + (south ? SAMPLE_GAP : 0), color);
				if (corner) {
					fill(map, x + SAMPLE_ROOM, y + SAMPLE_ROOM, SAMPLE_GAP, SAMPLE_GAP, color);
				}
				// a door to each different room east and south: the blood door red, one wither door, the rest in the room's color
				if (column + 1 < DungeonMapData.GRID && !east && layout[row].charAt(column + 1) != '.') {
					fill(map, x + SAMPLE_ROOM, y + SAMPLE_DOOR_FROM, SAMPLE_GAP, SAMPLE_DOOR, doorColor(room, layout[row].charAt(column + 1)));
				}
				if (row + 1 < DungeonMapData.GRID && !south && layout[row + 1].charAt(column) != '.') {
					fill(map, x + SAMPLE_DOOR_FROM, y + SAMPLE_ROOM, SAMPLE_DOOR, SAMPLE_GAP, doorColor(room, layout[row + 1].charAt(column)));
				}
				if (checked.add(room)) {
					byte check = unopened.indexOf(room) >= 0 ? (byte) 119 : green.indexOf(room) >= 0 && room != 'j' ? (byte) 30 : (byte) 34;
					map[x + SAMPLE_ROOM / 2 + (y + SAMPLE_ROOM / 2) * DungeonMapData.SIZE] = check;
				}
			}
		}
		DungeonMapData sample = new DungeonMapData();
		sample.read(map);
		return sample;
	}

	private static byte doorColor(char room, char other) {
		if (room == 'r' || other == 'r') {
			return 18;
		}
		return room == 'c' && other == 'y' ? (byte) 119 : (byte) 63;
	}

	private static void fill(byte[] map, int x, int y, int width, int height, byte color) {
		for (int row = y; row < y + height; row++) {
			java.util.Arrays.fill(map, x + row * DungeonMapData.SIZE, x + width + row * DungeonMapData.SIZE, color);
		}
	}


	@Override
	public void prepare(Minecraft client) {
		boolean inClear = isEnabled() && Dungeon.inClear() && client.player != null && client.level != null;
		boolean timing = inClear && DebugHooks.ACTIVE;
		if (timing) {
			// for the debug log: when the player walks onto a tile, and how long its room takes to show here
			int tile = playerTile(client);
			String line = delay.frame(tile, shownOnMap(tile), System.currentTimeMillis());
			if (line != null && DebugHooks.ACTIVE) {
				DebugHooks.log(SkyBlockDebug.DUNGEON, line);
			}
		}
		if (!inClear) {
			return;
		}
		ticks++;
		int tile = playerTile(client);
		// walking into a room discovers it at once, without waiting for the game's map
		DungeonScan.walkedInto(tile);
		ItemStack stack = client.player.getInventory().getItem(HOTBAR_MAP_SLOT);
		MapItemSavedData data = stack.getItem() instanceof MapItem ? MapItem.getSavedData(stack, client.level) : null;
		if (data == null) {
			return;
		}
		// the game's map item is taken in the frame it changed (a hash of its colors tells, a few microseconds), not on a timer
		int hash = java.util.Arrays.hashCode(data.colors);
		boolean changed = hash != mapItemHash || !map.known();
		if (changed) {
			mapItemHash = hash;
			if (timing) {
				delay.mapItemChanged(System.currentTimeMillis());
			}
		}
		readMarkers(client, data);
		// read anew when the map changed, when the player stepped onto another tile, and a few times a second for what the scan told since
		if (!changed && tile == readForTile && ticks % READ_TICKS != 0) {
			return;
		}
		readForTile = tile;
		map.read(data.colors);
		reconcile();
		fillFallbacks(Dungeon.stats());
		CurrentRoom.RoomData room = CurrentRoom.data();
		if (room != null && tile >= 0) {
			tileNames[tile] = room.name();
			tilePuzzle[tile] = "PUZZLE".equals(room.type());
		}
		if (timing) {
			long now = System.currentTimeMillis();
			if (room != null || tile >= 0 && tileNames[tile] != null) {
				delay.named(now);
			}
			String line = delay.read(shownOnMap(tile), now, 1);
			if (line != null && DebugHooks.ACTIVE) {
				DebugHooks.log(SkyBlockDebug.DUNGEON, line);
			}
		}
	}

	/**
	 * Brings the game's map and the background scan together. The map wins for what a room's state is (discovered, its checkmark, its doors);
	 * the scan gives what does not change (the name, the kind, the secrets), and only for rooms that count as discovered: those the map shows,
	 * and those the player walked into, which are drawn here before the map has them. A room the map marks as unexplored stays unexplored.
	 */
	private void reconcile() {
		boolean revealed = false;
		for (int t = 0; t < tileNames.length; t++) {
			boolean onMap = shownOnMap(t);
			if (onMap) {
				DungeonScan.mapShows(t);
			}
			CurrentRoom.RoomData room = DungeonScan.room(t);
			if (room == null) {
				continue;
			}
			tileNames[t] = room.name();
			tilePuzzle[t] = "PUZZLE".equals(room.type());
			if (secretsTotal[t] < 0 && room.secrets() > 0) {
				// how many it has is known from the room data; how many were found only once the game says so
				secretsTotal[t] = room.secrets();
				secretTexts[t] = UNKNOWN_FOUND + room.secrets();
			}
			if (!onMap && DungeonScan.walked(t)) {
				revealed |= map.reveal(t, DungeonMapData.kindOf(room.type()));
			}
		}
		if (!revealed) {
			return;
		}
		// the tiles of one room the player walked through lie together as one shape
		for (int t = 0; t < tileNames.length; t++) {
			if (map.kinds[t] == null || tileNames[t] == null) {
				continue;
			}
			int east = t % DungeonMapData.GRID < DungeonMapData.GRID - 1 ? t + 1 : -1;
			int south = t + DungeonMapData.GRID < tileNames.length ? t + DungeonMapData.GRID : -1;
			if (east >= 0 && map.kinds[east] != null && tileNames[t].equals(tileNames[east])) {
				map.joinRevealed(t, true);
			}
			if (south >= 0 && map.kinds[south] != null && tileNames[t].equals(tileNames[south])) {
				map.joinRevealed(t, false);
			}
		}
		map.regroup();
	}

	/** @return whether a tile's room shows on the map as read: a room the game's map has, and not one it still marks as unexplored */
	private boolean shownOnMap(int tile) {
		return tile >= 0 && map.kinds[tile] != null && map.kinds[tile] != DungeonMapData.Kind.UNEXPLORED;
	}

	/**
	 * Gives the score what the map knows while the game has not told the totals yet: the rooms on the map, and the secrets of the rooms whose
	 * names are known from the room data.
	 */
	private void fillFallbacks(DungeonStats stats) {
		int rooms = 0;
		for (int t = 0; t < DungeonMapData.GRID * DungeonMapData.GRID; t++) {
			if (map.roomOf[t] == t) {
				rooms++;
			}
		}
		stats.mapRooms = rooms;
		int secrets = 0;
		java.util.Set<String> counted = new java.util.HashSet<>();
		for (String name : tileNames) {
			if (name != null && counted.add(name)) {
				for (CurrentRoom.RoomData room : CurrentRoom.allRooms()) {
					if (room.name().equals(name)) {
						secrets += room.secrets();
						break;
					}
				}
			}
		}
		// the whole dungeon's secrets from the background scan, once every tile was told: a total the game shows anyway, and all that is used
		// of rooms that are not discovered yet
		int exact = DungeonScan.totalSecrets();
		stats.mapSecrets = exact >= 0 ? exact : secrets;
	}

	/** @return the tile the player stands on, or -1 outside the grid */
	private static int playerTile(Minecraft client) {
		int tileX = CurrentRoom.tileOf(client.player.getBlockX());
		int tileZ = CurrentRoom.tileOf(client.player.getBlockZ());
		return tileX < 0 || tileZ < 0 || tileX >= DungeonMapData.GRID || tileZ >= DungeonMapData.GRID ? -1 : tileX + tileZ * DungeonMapData.GRID;
	}

	/** The action bar: the secrets of the room the player is in, kept for its tile. */
	public void actionBar(String text) {
		Minecraft client = Minecraft.getInstance();
		if (!isEnabled() || !Dungeon.inClear() || client.player == null) {
			return;
		}
		Matcher matcher = DungeonParse.ROOM_SECRETS.matcher(text);
		int tile = playerTile(client);
		if (tile < 0 || !matcher.find()) {
			return;
		}
		try {
			int found = Integer.parseInt(matcher.group(1));
			int total = Integer.parseInt(matcher.group(2));
			if (found != secretsFound[tile] || total != secretsTotal[tile]) {
				secretsFound[tile] = found;
				secretsTotal[tile] = total;
				secretTexts[tile] = found + "/" + total;
			}
		} catch (NumberFormatException e) {
			// a number too long for an int: not a room's count
		}
	}

	/** The other players' markers on the map, given to the run's living players in their order; the player's own marker is left out. */
	private void readMarkers(Minecraft client, MapItemSavedData data) {
		markerCount = 0;
		List<DungeonPlayer> party = Dungeon.party();
		int next = 0;
		String self = client.player.getName().getString();
		for (MapDecoration decoration : data.getDecorations()) {
			if (decoration.type().is(MapDecorationTypes.FRAME) || markerCount >= markerX.length) {
				continue;
			}
			while (next < party.size() && (party.get(next).dead() || party.get(next).name().equals(self))) {
				next++;
			}
			if (next >= party.size()) {
				break;
			}
			markerX[markerCount] = (decoration.x() + DungeonMapData.SIZE) / 2f;
			markerY[markerCount] = (decoration.y() + DungeonMapData.SIZE) / 2f;
			markerYaw[markerCount] = decoration.rot() * DEGREES_PER_ROTATION_STEP;
			markerPlayer[markerCount] = party.get(next++);
			// the face from the player's own skin, looked up here a few times a second, not while drawing
			var info = client.getConnection() == null ? null : client.getConnection().getPlayerInfo(markerPlayer[markerCount].name());
			markerSkin[markerCount] = info == null ? null : info.getSkin();
			markerCount++;
		}
		// a teammate the client has loaded stands where the game shows them, every frame; the others where the map's markers are, moved there softly
		for (int i = 0; i < markerCount; i++) {
			String name = markerPlayer[i].name();
			net.minecraft.world.entity.player.Player teammate = map.known() ? loadedPlayer(client, name) : null;
			float[] at = markerShown.get(name);
			if (at == null) {
				// first seen: it starts where the map has it
				at = new float[] {markerX[i], markerY[i]};
				markerShown.put(name, at);
			}
			if (teammate != null) {
				markerX[i] = map.worldToMap(teammate.getX(), false);
				markerY[i] = map.worldToMap(teammate.getZ(), true);
				markerYaw[i] = teammate.getYRot();
			} else {
				markerX[i] = at[0] + (markerX[i] - at[0]) * MARKER_FOLLOW;
				markerY[i] = at[1] + (markerY[i] - at[1]) * MARKER_FOLLOW;
			}
			at[0] = markerX[i];
			at[1] = markerY[i];
		}
	}

	/** How much of the way to the map's marker a teammate's arrow moves per frame while the teammate is not loaded. */
	private static final float MARKER_FOLLOW = 0.25f;
	/** Where each teammate's arrow was drawn last, in map pixels, by name. */
	private final Map<String, float[]> markerShown = new java.util.HashMap<>();

	/** @return a teammate by name among the players the client has loaded, or null */
	private static net.minecraft.world.entity.player.Player loadedPlayer(Minecraft client, String name) {
		for (net.minecraft.world.entity.player.Player player : client.level.players()) {
			if (player != client.player && player.getGameProfile().name().equals(name)) {
				return player;
			}
		}
		return null;
	}

	/**
	 * @return whether the game's map shows a door at a door block (between two tiles, as the doors feature finds them): only those may be outlined
	 *         through walls
	 */
	public boolean doorOnMap(int x, int z) {
		if (!map.known()) {
			return false;
		}
		boolean east = Math.floorMod(x - CurrentRoom.TILE_ORIGIN, CurrentRoom.TILE) == CurrentRoom.TILE / 2;
		int tileX = CurrentRoom.tileOf(east ? x - CurrentRoom.TILE / 2 : x);
		int tileZ = CurrentRoom.tileOf(east ? z : z - CurrentRoom.TILE / 2);
		if (tileX < 0 || tileZ < 0 || tileX >= DungeonMapData.GRID || tileZ >= DungeonMapData.GRID) {
			return false;
		}
		int tile = tileX + tileZ * DungeonMapData.GRID;
		return (east ? map.doorEast[tile] : map.doorSouth[tile]) != DungeonMapData.Door.NONE;
	}

	/** @return why no Fairy doorway is picked for the room at a place, for the debug log; "" when one is */
	String fairyWhy(double x, double z) {
		int tileX = CurrentRoom.tileOf((int) Math.floor(x));
		int tileZ = CurrentRoom.tileOf((int) Math.floor(z));
		if (tileX < 0 || tileZ < 0 || tileX >= DungeonMapData.GRID || tileZ >= DungeonMapData.GRID) {
			return "the player is outside the dungeon's grid";
		}
		return FairyRoute.why(map, tileX + tileZ * DungeonMapData.GRID);
	}

	/** @return the Fairy room's open doorway on the route to Blood for the room at a place, from the map's data alone; null for none or not known */
	FairyRoute.Doorway fairyDoorway(double x, double z) {
		int tileX = CurrentRoom.tileOf((int) Math.floor(x));
		int tileZ = CurrentRoom.tileOf((int) Math.floor(z));
		if (!map.known() || tileX < 0 || tileZ < 0 || tileX >= DungeonMapData.GRID || tileZ >= DungeonMapData.GRID) {
			return null;
		}
		return FairyRoute.doorway(map, tileX + tileZ * DungeonMapData.GRID);
	}

	/** @return the main tile on the game's map of the room at a place, or -1 where the map shows no room there */
	public int roomTileAt(double x, double z) {
		int tileX = CurrentRoom.tileOf((int) Math.floor(x));
		int tileZ = CurrentRoom.tileOf((int) Math.floor(z));
		if (!map.known() || tileX < 0 || tileZ < 0 || tileX >= DungeonMapData.GRID || tileZ >= DungeonMapData.GRID) {
			return -1;
		}
		int room = map.roomOf[tileX + tileZ * DungeonMapData.GRID];
		return room < 0 ? -1 : map.mainTile[room];
	}

	/** @return a room's kind as the game's map shows it, by its main tile */
	public DungeonMapData.Kind kindOf(int mainTile) {
		return map.kinds[mainTile];
	}

	/** @return a room's checkmark as the game's map shows it, by its main tile */
	public DungeonMapData.Check checkOf(int mainTile) {
		return map.checks[mainTile];
	}

	/** @return the name of the room at a place, where the player was in that room this run, or "" */
	public String roomAt(double x, double z) {
		int tileX = CurrentRoom.tileOf((int) Math.floor(x));
		int tileZ = CurrentRoom.tileOf((int) Math.floor(z));
		if (tileX < 0 || tileZ < 0 || tileX >= DungeonMapData.GRID || tileZ >= DungeonMapData.GRID) {
			return "";
		}
		String name = tileNames[tileX + tileZ * DungeonMapData.GRID];
		return name == null ? "" : name;
	}

	private boolean live() {
		return Dungeon.in() && map.known();
	}

	private DungeonMapData shownMap() {
		return live() ? map : SAMPLE;
	}

	@Override
	public boolean shown(Minecraft client) {
		return Dungeon.inClear() && map.known() || simulated();
	}

	@Override
	public boolean inEditorWhenHidden() {
		return true;
	}

	private float scale() {
		return BASE_SCALE * size.get();
	}

	/** @return the width of the grid in map pixels: six rooms and the gaps between them */
	private static int span(DungeonMapData shown) {
		return DungeonMapData.GRID * shown.roomSize() + (DungeonMapData.GRID - 1) * GAP;
	}

	@Override
	public int panelWidth() {
		return Math.round(span(shownMap()) * scale()) + 2 * PAD;
	}

	@Override
	public int panelHeight() {
		return panelWidth();
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		int width = panelWidth();
		if (background.get()) {
			drawGlass(ui, width, width, PanelKit.RADIUS);
		}
		boolean live = live();
		boolean turned = rotate.get() && live && client.player != null;
		var pose = graphics.pose();
		if (turned) {
			// the way the player looks is up: north is up when they look north (yaw 180)
			pose.pushMatrix();
			pose.translate(width / 2f, width / 2f);
			pose.rotate((float) Math.toRadians(180f - client.player.getYRot()));
			pose.scale(ROTATED_FIT, ROTATED_FIT);
			pose.translate(-width / 2f, -width / 2f);
		}
		try {
			drawMap(ui, graphics, client, live);
		} finally {
			if (turned) {
				pose.popMatrix();
			}
		}
	}

	private void drawMap(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client, boolean live) {
		DungeonMapData shown = live ? map : SAMPLE;
		float s = scale();
		float tile = shown.roomSize() * s;
		float radius = tile * CORNER;
		float gap = GAP * s;
		float originX = PAD - shown.tileX(0) * s;
		float originY = PAD - shown.tileY(0) * s;
		boolean hideUnopened = unopened.get() == Unopened.HIDDEN;
		// the rooms: each tile reaches across its joins, past the next tile's rounded corners, so a room is one shape
		for (int t = 0; t < DungeonMapData.GRID * DungeonMapData.GRID; t++) {
			DungeonMapData.Kind kind = shown.kinds[t];
			if (kind == null || hideUnopened && unopened(shown, t)) {
				continue;
			}
			float x = originX + shown.tileX(t) * s;
			float y = originY + shown.tileY(t) * s;
			int color = roomColor(shown, t);
			ui.roundedSmooth(x, y, tile + (shown.joinedEast[t] ? gap + 2 * radius : 0f), tile, radius, color);
			if (shown.joinedSouth[t]) {
				ui.roundedSmooth(x, y, tile, tile + gap + 2 * radius, radius, color);
			}
			if (shown.joinedEast[t] && shown.joinedSouth[t] && t + 1 < shown.joinedSouth.length && shown.joinedSouth[t + 1]) {
				ui.roundedSmooth(x + tile - radius, y + tile - radius, gap + 2 * radius, gap + 2 * radius, 0f, color);
			}
		}
		for (int t = 0; t < DungeonMapData.GRID * DungeonMapData.GRID; t++) {
			if (shown.kinds[t] == null) {
				continue;
			}
			float x = originX + shown.tileX(t) * s;
			float y = originY + shown.tileY(t) * s;
			drawDoor(ui, shown, t, shown.doorEast[t], x + tile, y + tile / 2f - 2 * s, gap, 4 * s);
			drawDoor(ui, shown, t, shown.doorSouth[t], x + tile / 2f - 2 * s, y + tile, 4 * s, gap);
		}
		for (int t = 0; t < DungeonMapData.GRID * DungeonMapData.GRID; t++) {
			if (shown.roomOf[t] == t && !(hideUnopened && unopened(shown, t))) {
				drawRoomMarks(ui, shown, t, originX, originY, s, tile, live);
			}
		}
		drawPlayers(ui, graphics, client, shown, originX, originY, s, tile, live);
	}

	/** @return whether a room was seen on the map but nobody entered it yet */
	private static boolean unopened(DungeonMapData shown, int t) {
		int room = shown.roomOf[t] < 0 ? t : shown.roomOf[t];
		return shown.kinds[t] == DungeonMapData.Kind.UNEXPLORED || shown.checks[shown.mainTile[room]] == DungeonMapData.Check.QUESTION;
	}

	private static int roomColor(DungeonMapData shown, int t) {
		DungeonMapData.Kind kind = shown.kinds[t];
		int color = 0xFF000000 | color(kind == DungeonMapData.Kind.UNEXPLORED ? DungeonMapData.Kind.NORMAL : kind);
		return unopened(shown, t) ? mix(color, 0xFF26262A, 0.55f) : color;
	}

	/**
	 * The checkmark (or "?"), the room's name and its secrets, inside the room. The mark and the secrets count share the room's main tile: the mark about two
	 * thirds of a tile (a little less where the count stands under it), the count centered under it, both inside that tile, so the count never
	 * hangs over a door or a neighbouring room. The name goes above or below the mark in the room's rectangle.
	 */
	private void drawRoomMarks(Canvas ui, DungeonMapData shown, int room, float originX, float originY, float s, float tile, boolean live) {
		if (!MapMarks.markRect(shown.roomOf, room, DungeonMapData.GRID, markRect)) {
			return;
		}
		float boxX = originX + shown.tileX(markRect[MapMarks.LEFT] + markRect[MapMarks.TOP] * DungeonMapData.GRID) * s;
		float boxY = originY + shown.tileY(markRect[MapMarks.LEFT] + markRect[MapMarks.TOP] * DungeonMapData.GRID) * s;
		float boxWidth = originX + shown.tileX(markRect[MapMarks.RIGHT]) * s + tile - boxX;
		float boxHeight = originY + shown.tileY(markRect[MapMarks.BOTTOM] * DungeonMapData.GRID) * s + tile - boxY;
		DungeonMapData.Check check = shown.checks[shown.mainTile[room]];
		boolean questionMark = unopened(shown, room);
		boolean marked = questionMark || check != DungeonMapData.Check.NONE;
		// the mark and the secrets count share one tile, the room's main tile: the count under the mark, both inside that tile
		int anchor = MapMarks.anchorTile(shown.roomOf, shown.mainTile, room);
		if (anchor < 0) {
			return;
		}
		float anchorX = originX + shown.tileX(anchor) * s;
		float anchorY = originY + shown.tileY(anchor) * s;
		String count = secrets.get() && tile >= CHIPS_FROM ? secretText(shown, room, live) : null;
		float wanted = CHIP_SCALE * Math.clamp(tile / CHIP_TILE, CHIP_LEAST, CHIP_MOST);
		MapMarks.Stack stack = MapMarks.stack(tile, marked, count != null, CHECK, CHECK_LEAST, count == null ? 0f : ui.boldWidthScaled(count, 1f), wanted,
			CHIP_SCALE * CHIP_LEAST, STACK_MARGIN, STACK_GAP, countOverCheck.get());
		marked = stack.mark();
		float markSize = stack.markSize();
		float markX = anchorX + tile / 2f - markSize / 2f;
		float markY = anchorY + stack.markTop();
		boolean question = questionMark && marked;
		if (question) {
			// the "?" as tall as a check, with the same soft shadow
			float scale = markSize / QUESTION_HEIGHT;
			float x = anchorX + tile / 2f - ui.boldWidthScaled("?", scale) / 2f;
			float y = markY + markSize / 2f - QUESTION_HEIGHT * scale / 2f;
			ui.textAtScaledBold("?", x, y + Math.max(1f, markSize * SHADOW_DROP), scale, MARK_SHADOW);
			ui.textAtScaledBold("?", x, y, scale, 0xE6FFFFFF);
		} else if (marked && check != DungeonMapData.Check.NONE) {
			String icon = check == DungeonMapData.Check.FAILED ? "close" : "check";
			int color = check == DungeonMapData.Check.GREEN ? SystemColors.GREEN : check == DungeonMapData.Check.FAILED ? SystemColors.RED : 0xFFFFFFFF;
			int size = Math.round(markSize);
			ui.icon(icon, Math.round(markX), Math.round(markY + Math.max(1f, markSize * SHADOW_DROP)), size, MARK_SHADOW);
			ui.icon(icon, Math.round(markX), Math.round(markY), size, color);
		}
		String name = nameOf(shown, room, live);
		if (name != null && tile >= NAMES_FROM) {
			// without a mark the name takes the middle; with one, the room above it (or below, where there is more)
			float bandTop = boxY + 1;
			// the mark is in the room's main tile: where that lies outside the name's rectangle (the odd tile of an L), the name has the rectangle to itself
			boolean markInBox = marked && markX >= boxX - 0.5f && markX + markSize <= boxX + boxWidth + 0.5f && markY >= boxY - 0.5f && markY + markSize <= boxY + boxHeight + 0.5f;
			float bandHeight = boxHeight - 2;
			if (markInBox) {
				float above = markY - boxY - 2;
				float below = boxY + boxHeight - (markY + markSize) - 2;
				bandTop = above >= below ? boxY + 1 : markY + markSize + 1;
				bandHeight = Math.max(above, below);
			}
			RoomNameFit.Fit fit = bandHeight <= 0 ? null : nameFit.fit(ui, room, name, boxWidth - 4, bandHeight, NAME_SCALE, NAME_SMALLEST);
			if (fit == null && markInBox) {
				// no room beside the mark (a one-tile room): a small label on the room's top edge, half over the gap, never over the mark
				drawNameLabel(ui, name, boxX + boxWidth / 2f, boxY, boxWidth + GAP * s);
			} else if (fit != null) {
				float lineHeight = 9f * fit.scale();
				float textTop = bandTop + bandHeight / 2f - fit.lines().length * lineHeight / 2f + 0.5f;
				for (int i = 0; i < fit.lines().length; i++) {
					String line = fit.lines()[i];
					ui.textAtScaledBold(line, boxX + boxWidth / 2f - ui.boldWidthScaled(line, fit.scale()) / 2f, textTop + i * lineHeight, fit.scale(),
						0xFFFFFFFF);
				}
			}
		}
		if (stack.count()) {
			float scale = stack.countScale();
			drawSecretCount(ui, count, secretsComplete(shown, room, live), anchorX + tile / 2f - ui.boldWidthScaled(count, scale) / 2f,
				anchorY + stack.countTop(), scale);
		}
	}

	/** A room's name as one small label centered on a room's top edge, shortened to the room's width where it is longer. */
	private static void drawNameLabel(Canvas ui, String name, float centerX, float edgeY, float most) {
		String shown = name;
		while (shown.length() > 1 && ui.boldWidthScaled(shown, TAG_SCALE) + 4 > most) {
			shown = shown.substring(0, shown.length() - 1);
		}
		if (!shown.equals(name)) {
			shown = shown.length() > 1 ? shown.substring(0, shown.length() - 1) + "…" : shown;
		}
		float width = ui.boldWidthScaled(shown, TAG_SCALE) + 4;
		float height = 9f * TAG_SCALE + 2;
		float top = edgeY - height / 2f;
		ui.roundedSmooth(centerX - width / 2f, top, width, height, Radius.box(height), TAG_BACKGROUND);
		ui.textAtScaledBold(shown, centerX - width / 2f + 2, top + 1.5f, TAG_SCALE, 0xFFFFFFFF);
	}

	/** @return the name shown for a room, by the room-names setting, or null */
	private String nameOf(DungeonMapData shown, int room, boolean live) {
		if (roomNames.get() == RoomNames.OFF) {
			return null;
		}
		for (int t = 0; t < DungeonMapData.GRID * DungeonMapData.GRID; t++) {
			if (shown.roomOf[t] != room) {
				continue;
			}
			String name = live ? tileNames[t] : SAMPLE_NAMES.get(t);
			boolean puzzle = live ? tilePuzzle[t] : shown.kinds[t] == DungeonMapData.Kind.PUZZLE;
			if (name != null && (roomNames.get() == RoomNames.ALL || puzzle)) {
				return name;
			}
		}
		return null;
	}

	/** @return a room's secrets as the map shows them, "3/5", or null while they are not known */
	private String secretText(DungeonMapData shown, int room, boolean live) {
		for (int t = 0; t < DungeonMapData.GRID * DungeonMapData.GRID; t++) {
			if (shown.roomOf[t] != room) {
				continue;
			}
			if (live && secretTexts[t] != null) {
				return secretTexts[t];
			}
			if (!live && SAMPLE_SECRETS.containsKey(t)) {
				int[] sample = SAMPLE_SECRETS.get(t);
				return sample[0] + "/" + sample[1];
			}
		}
		return null;
	}

	/** @return whether every secret of a room is found */
	private boolean secretsComplete(DungeonMapData shown, int room, boolean live) {
		for (int t = 0; t < DungeonMapData.GRID * DungeonMapData.GRID; t++) {
			if (shown.roomOf[t] != room) {
				continue;
			}
			if (live && secretTexts[t] != null) {
				return secretsFound[t] >= secretsTotal[t];
			}
			if (!live && SAMPLE_SECRETS.containsKey(t)) {
				int[] sample = SAMPLE_SECRETS.get(t);
				return sample[0] >= sample[1];
			}
		}
		return false;
	}

	/**
	 * The secrets count at a place, without a box: the text on the room, with a thin dark outline so it reads on every room color. Its place and
	 * scale come from {@link MapMarks#stack}: under the mark, inside the room's main tile.
	 */
	private static void drawSecretCount(Canvas ui, String text, boolean complete, float textX, float textY, float scale) {
		for (int i = 0; i < OUTLINE_STEPS.length; i += 2) {
			ui.textAtScaledBold(text, textX + OUTLINE_STEPS[i], textY + OUTLINE_STEPS[i + 1], scale, SECRETS_OUTLINE);
		}
		ui.textAtScaledBold(text, textX, textY, scale, complete ? SystemColors.GREEN : 0xFFFFFFFF);
	}

	/** A door between two rooms: a corridor in a darker tone of the room, a wither door black with a thin edge, the blood door red. */
	private static void drawDoor(Canvas ui, DungeonMapData shown, int t, DungeonMapData.Door door, float x, float y, float width, float height) {
		if (door == DungeonMapData.Door.NONE) {
			return;
		}
		switch (door) {
			case WITHER -> {
				ui.roundedSmooth(x - 0.5f, y - 0.5f, width + 1, height + 1, 1.5f, WITHER_EDGE);
				ui.roundedSmooth(x, y, width, height, 1f, WITHER_DOOR);
			}
			case BLOOD -> ui.roundedSmooth(x, y, width, height, 1f, 0xFF000000 | color(DungeonMapData.Kind.BLOOD));
			case FAIRY -> ui.roundedSmooth(x, y, width, height, 1f, 0xFF000000 | color(DungeonMapData.Kind.FAIRY));
			default -> ui.roundedSmooth(x, y, width, height, 1f, mix(roomColor(shown, t), 0xFF000000, 0.35f));
		}
	}

	// ------------------------------------------------------------------------------------------------------ players

	/** @return whether the players' name tags show now: always, never, or only while the leap menu is open (to pick whom to leap to) */
	private boolean tagsNow(Minecraft client, boolean live) {
		return switch (playerNames.get()) {
			case OFF -> false;
			case ALWAYS -> true;
			case LEAP -> !live || DungeonLeapFeature.leapMenu(client.gui.screen());
		};
	}

	private void drawPlayers(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client, DungeonMapData shown, float originX, float originY, float s,
		float tile, boolean live) {
		boolean tags = tile >= TAGS_FROM && tagsNow(client, live);
		boolean heads = teammates.get() == Teammates.HEADS;
		if (!live) {
			for (int i = 1; i < SAMPLE_PLAYERS.length; i++) {
				float x = originX + SAMPLE_PLAYERS[i][0] * s;
				float y = originY + SAMPLE_PLAYERS[i][1] * s;
				int color = 0xFF000000 | SAMPLE_CLASSES[i].rgb();
				if (heads) {
					// the sample shows the player's own face, as a real run shows each teammate's
					MapMarker.head(ui, graphics, client.player == null ? null : client.player.getSkin(), x, y, color);
				} else {
					MapMarker.arrow(ui, graphics, x, y, SAMPLE_PLAYERS[i][2], color, 1f);
				}
				if (tags) {
					drawTag(ui, shown, SAMPLE_PLAYER_NAMES[i], color, x, y, originX, originY, s, tile);
				}
			}
			MapMarker.arrow(ui, graphics, originX + SAMPLE_PLAYERS[0][0] * s, originY + SAMPLE_PLAYERS[0][1] * s, SAMPLE_PLAYERS[0][2], 0xFFFFFFFF, 1f);
			return;
		}
		for (int i = 0; i < markerCount; i++) {
			int color = 0xFF000000 | markerPlayer[i].dungeonClass().rgb();
			float x = originX + markerX[i] * s;
			float y = originY + markerY[i] * s;
			if (heads) {
				MapMarker.head(ui, graphics, markerSkin[i], x, y, color);
			} else {
				MapMarker.arrow(ui, graphics, x, y, markerYaw[i], color, 1f);
			}
			if (tags) {
				drawTag(ui, shown, markerPlayer[i].name(), color, x, y, originX, originY, s, tile);
			}
		}
		if (client.player != null) {
			float mapX = map.worldToMap(client.player.getX(), false);
			float mapY = map.worldToMap(client.player.getZ(), true);
			if (mapX >= 0 && mapY >= 0) {
				// the player's own arrow last, on top of the others
				MapMarker.arrow(ui, graphics, originX + mapX * s, originY + mapY * s, client.player.getYRot(), 0xFFFFFFFF, 1f);
			}
		}
	}

	/** A small name tag above a marker, or under it where it would cover a room's checkmark or name. */
	private static void drawTag(Canvas ui, DungeonMapData shown, String name, int color, float x, float y, float originX, float originY, float s,
		float tile) {
		float width = ui.boldWidthScaled(name, TAG_SCALE) + 4;
		float height = 9f * TAG_SCALE + 2;
		float tagY = y - MapMarker.ARROW * 0.7f - height;
		// the middle of the room under the tag is where its checkmark and name sit: keep clear of it
		int column = Math.round((x - originX) / s - shown.tileX(0)) / (shown.roomSize() + GAP);
		int row = Math.round((tagY + height / 2f - originY) / s - shown.tileY(0)) / (shown.roomSize() + GAP);
		if (column >= 0 && row >= 0 && column < DungeonMapData.GRID && row < DungeonMapData.GRID) {
			float middleY = originY + shown.tileY(column + row * DungeonMapData.GRID) * s + tile / 2f;
			if (Math.abs(tagY + height / 2f - middleY) < tile * CHECK / 2f + height / 2f) {
				tagY = y + MapMarker.ARROW * 0.7f;
			}
		}
		ui.roundedSmooth(x - width / 2f, tagY, width, height, Radius.box(height), TAG_BACKGROUND);
		ui.textAtScaledBold(name, x - width / 2f + 2, tagY + 1.5f, TAG_SCALE, color);
	}

	/** Player names of configs from before: Team and All become Always, Off stays Off. */
	@Override
	public void load(com.google.gson.JsonObject json) {
		migrateNames(json);
		super.load(json);
	}

	/** Turns the old values of the player names setting into the new ones, in place. */
	static void migrateNames(com.google.gson.JsonObject json) {
		var value = json.get(NAMES_KEY);
		if (value != null && value.isJsonPrimitive() && OLD_ALWAYS.contains(value.getAsString())) {
			json.addProperty(NAMES_KEY, PlayerNames.ALWAYS.name());
		}
	}

	/** @return two colors mixed, a share of the second */
	private static int mix(int first, int second, float share) {
		int a = Math.round(((first >>> 24) & 0xFF) * (1 - share) + ((second >>> 24) & 0xFF) * share);
		int r = Math.round(((first >> 16) & 0xFF) * (1 - share) + ((second >> 16) & 0xFF) * share);
		int g = Math.round(((first >> 8) & 0xFF) * (1 - share) + ((second >> 8) & 0xFF) * share);
		int b = Math.round((first & 0xFF) * (1 - share) + (second & 0xFF) * share);
		return a << 24 | r << 16 | g << 8 | b;
	}

	/** @return a room kind's color, as the legend has them */
	static int color(DungeonMapData.Kind kind) {
		return switch (kind) {
			case ENTRANCE -> 0x34C759;
			case FAIRY -> 0xFF6EC7;
			case NORMAL, UNEXPLORED -> 0x8B6A47;
			case BLOOD -> 0xFF453A;
			case MINIBOSS -> 0xFFD60A;
			case PUZZLE -> 0xAF52DE;
			case TRAP -> 0xFF9F0A;
		};
	}
}
