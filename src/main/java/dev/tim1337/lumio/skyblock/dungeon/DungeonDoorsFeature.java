package dev.tim1337.lumio.skyblock.dungeon;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.waypoint.WaypointLook;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.ColorChoice;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The wither and blood doors of the Catacombs: one flat rectangle on the doorway of each door of the room the player is in, on the side facing that
 * room, green while the party has the door's key (a wither key, the blood key) and red while it has none, without text. The doors are found where the dungeon has them (between two tiles, at a fixed height) a few times a second, only in
 * a dungeon, only the doorways of the current room ({@link CurrentRoom#ownsDoor}), and only those the game's own map shows: their outlines may show
 * through walls (a documented exception to the project rules: the doors of the current room, never those of other rooms).
 */
public class DungeonDoorsFeature extends AddonFeature {
	/** How the doors are marked. */
	public enum Look {
		BOX("Box"), GLOW("Glow");

		final String label;

		Look(String label) {
			this.label = label;
		}
	}

	private static final int SCAN_TICKS = 10;
	/** Doors this far from the player are looked at, in tiles. */
	private static final int NEAR_TILES = 2;
	private static final int DOOR_BOTTOM = 69;
	private static final int DOOR_HEIGHT = 4;
	private static final int MOST_DOORS = 16;
	/** How far the doorway's rectangle stands in front of the door, in blocks. */
	private static final float FACE_GAP = 0.02f;
	private static final float REFERENCE_LINES = 1080f;
	private static final RenderType SEE_THROUGH = RenderTypes.textSeeThrough(Identifier.fromNamespaceAndPath("lumio", "textures/white.png"));

	private final Choice<Look> look = add(make().choice("look", Component.literal("Look"), Look.BOX, value -> Component.literal(value.label)), "General", "A box, or a box with a soft glow");
	private final ColorChoice keyReady = add(make().color("key_ready", Component.literal("Key ready"), SystemColors.GREEN_RGB).ownColorOnly(), "General",
		"The party has the key for this door");
	private final ColorChoice noKey = add(make().color("no_key", Component.literal("No key"), SystemColors.RED_RGB).ownColorOnly(), "General");

	/** The doors found: their middle columns and whether each is the blood door. */
	private final int[] doorX = new int[MOST_DOORS];
	private final int[] doorZ = new int[MOST_DOORS];
	private final boolean[] blood = new boolean[MOST_DOORS];
	/** Whether a door is the Fairy room's open doorway on the route to Blood: shown like a wither door whose key is ready, as it needs none. */
	private final boolean[] fairy = new boolean[MOST_DOORS];
	/** Whether a door stands between two tiles east and west of it (its doorway faces east or west), else north and south. */
	private final boolean[] acrossX = new boolean[MOST_DOORS];
	private double playerX;
	private double playerZ;
	private int doorCount;
	private int ticks;
	private final BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
	private Vec3 camera = Vec3.ZERO;
	private float pixelAngle;
	private final SubmitNodeCollector.CustomGeometryRenderer drawDoors = this::drawAll;

	public DungeonDoorsFeature() {
		super("dungeon_doors", "Doors", "Doors of your room, also through walls.", false);
		iconRgb(SystemColors.RED_RGB);
		iconName("lock");
		summary("Wither and blood doors");
	}

	/** Looks for the doors around the player a few times a second. */
	public void tick(Minecraft client) {
		if (++ticks % SCAN_TICKS != 0) {
			return;
		}
		doorCount = 0;
		if (!isEnabled() || !Dungeon.inClear() || client.level == null || client.player == null) {
			return;
		}
		int tileX = (client.player.getBlockX() + 201) >> 5;
		int tileZ = (client.player.getBlockZ() + 201) >> 5;
		for (int tx = tileX - NEAR_TILES; tx <= tileX + NEAR_TILES; tx++) {
			for (int tz = tileZ - NEAR_TILES; tz <= tileZ + NEAR_TILES; tz++) {
				int middleX = CurrentRoom.TILE_ORIGIN + tx * CurrentRoom.TILE;
				int middleZ = CurrentRoom.TILE_ORIGIN + tz * CurrentRoom.TILE;
				// the doors east and south of a tile, half a tile from its middle
				check(client, middleX + CurrentRoom.TILE / 2, middleZ, true);
				check(client, middleX, middleZ + CurrentRoom.TILE / 2, false);
			}
		}
		checkFairy(client);
	}

	private void check(Minecraft client, int x, int z, boolean betweenX) {
		if (doorCount >= MOST_DOORS) {
			return;
		}
		at.set(x, DOOR_BOTTOM, z);
		if (!client.level.isLoaded(at)) {
			return;
		}
		Block block = client.level.getBlockState(at).getBlock();
		boolean wither = block == Blocks.COAL_BLOCK;
		boolean bloodDoor = block == Blocks.DYED_TERRACOTTA.pick(DyeColor.RED);
		// only the doors of the room the player is in: never one of the next rooms, even if the map knows it
		if ((wither || bloodDoor) && CurrentRoom.ownsDoor(x, z) && SkyBlockFeatures.DUNGEON_MAP.doorOnMap(x, z)) {
			doorX[doorCount] = x;
			doorZ[doorCount] = z;
			blood[doorCount] = bloodDoor;
			fairy[doorCount] = false;
			acrossX[doorCount] = betweenX;
			doorCount++;
		}
	}

	/**
	 * The Fairy room's open doorway on the route to Blood, as the dungeon map's data gives it ({@link FairyRoute}): one doorway of the room the
	 * player is in, or none where the route is not known. It joins the doors drawn and looks like a wither door with its key ready.
	 */
	private void checkFairy(Minecraft client) {
		if (doorCount >= MOST_DOORS) {
			return;
		}
		FairyRoute.Doorway door = SkyBlockFeatures.DUNGEON_MAP.fairyDoorway(client.player.getX(), client.player.getZ());
		if (door == null) {
			noteFairy(SkyBlockFeatures.DUNGEON_MAP.fairyWhy(client.player.getX(), client.player.getZ()));
			return;
		}
		int middleX = CurrentRoom.middleOf(door.tile() % DungeonMapData.GRID);
		int middleZ = CurrentRoom.middleOf(door.tile() / DungeonMapData.GRID);
		int x = door.east() ? middleX + CurrentRoom.TILE / 2 : middleX;
		int z = door.east() ? middleZ : middleZ + CurrentRoom.TILE / 2;
		// only a doorway of the room the player is in, as with the doors
		if (!CurrentRoom.ownsDoor(x, z)) {
			noteFairy("the doorway at " + x + " " + z + " is not one of the room the player is in");
			return;
		}
		doorX[doorCount] = x;
		doorZ[doorCount] = z;
		blood[doorCount] = false;
		fairy[doorCount] = true;
		acrossX[doorCount] = door.east();
		noteFairy("outlined at " + x + " " + z);
		doorCount++;
	}

	/** What the debug log last said about the Fairy doorway: said again only when it changes. */
	private String fairyNoted = "";

	/** For the debug log: whether the Fairy doorway is outlined, or why not, once per change. */
	private void noteFairy(String what) {
		if (!what.equals(fairyNoted)) {
			fairyNoted = what;
			if (DebugHooks.ACTIVE) {
				DebugHooks.log(SkyBlockDebug.DUNGEON, "Doors: Fairy doorway: {}", what);
			}
		}
	}

	/** Draws the outlines with the world, also through walls: the map shows these doors anyway. */
	public void draw(LevelRenderContext context) {
		if (doorCount == 0 || !isEnabled()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		camera = client.gameRenderer.mainCamera().position();
		if (client.player != null) {
			playerX = client.player.getX();
			playerZ = client.player.getZ();
		}
		pixelAngle = (float) (2.0 * Math.tan(Math.toRadians(client.options.fov().get()) / 2.0)) / REFERENCE_LINES;
		context.submitNodeCollector().submitCustomGeometry(context.poseStack(), SEE_THROUGH, drawDoors);
	}

	private void drawAll(PoseStack.Pose pose, VertexConsumer consumer) {
		Vec3 cam = camera;
		boolean glow = look.get() == Look.GLOW;
		for (int i = 0; i < doorCount; i++) {
			float x0 = (float) (doorX[i] - 1 - cam.x);
			float y0 = (float) (DOOR_BOTTOM - cam.y);
			float z0 = (float) (doorZ[i] - 1 - cam.z);
			float x1 = x0 + 3f;
			float y1 = y0 + DOOR_HEIGHT;
			float z1 = z0 + 3f;
			// only the doorway's face toward the player's room: a flat rectangle just in front of it, not the door's whole box
			if (acrossX[i]) {
				float face = playerX < doorX[i] + 0.5 ? x0 - FACE_GAP : x1 + FACE_GAP;
				x0 = face - FACE_GAP / 2f;
				x1 = face + FACE_GAP / 2f;
			} else {
				float face = playerZ < doorZ[i] + 0.5 ? z0 - FACE_GAP : z1 + FACE_GAP;
				z0 = face - FACE_GAP / 2f;
				z1 = face + FACE_GAP / 2f;
			}
			int line = fairy[i] ? keyReady.get() & 0xFFFFFF
				: doorColor(blood[i], Dungeon.hasWitherKey(), Dungeon.hasBloodKey(), keyReady.get() & 0xFFFFFF, noKey.get() & 0xFFFFFF);
			int fill = line;
			WaypointLook.boxAt(pose, consumer, x0, y0, z0, x1, y1, z1, fill, glow ? 0.12f : 0.08f);
			if (glow) {
				WaypointLook.outlineAt(pose, consumer, x0, y0, z0, x1, y1, z1, line, 0.18f, pixelAngle, SkyBlockFeatures.DUNGEON_HIGHLIGHTS.glowPixels());
			}
			WaypointLook.outlineAt(pose, consumer, x0, y0, z0, x1, y1, z1, line, 0.95f, pixelAngle, SkyBlockFeatures.DUNGEON_HIGHLIGHTS.lineWidth());
		}
	}

	/** @return a door's color: the key-ready color while the party has its key (the blood key for the blood door, a wither key for a wither door), else the no-key color */
	static int doorColor(boolean bloodDoor, boolean witherKey, boolean bloodKey, int ready, int none) {
		return (bloodDoor ? bloodKey : witherKey) ? ready : none;
	}

	/** @return how many doors are near, and where: for the map */
	public int doors() {
		return doorCount;
	}
}
