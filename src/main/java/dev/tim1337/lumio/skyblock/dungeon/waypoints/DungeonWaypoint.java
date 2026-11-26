package dev.tim1337.lumio.skyblock.dungeon.waypoints;

import java.util.Locale;

/**
 * A point of a dungeon room in Lumio's room model ({@link dev.tim1337.lumio.skyblock.dungeon.RoomModel}: from the middle of the room, turned with
 * it), so it is in the same place in every run however the room lies: its block, color with alpha, whether it is filled, whether it shows through
 * walls, the box within the block (in the room's turn), a title, a type, and whether it hides once found.
 */
public record DungeonWaypoint(int x, int y, int z, int argb, boolean filled, boolean throughWalls, double minX, double minY, double minZ, double maxX,
	double maxY, double maxZ, String title, Type type, boolean hideFound) {

	/** What a point marks, with its default color and icon. */
	public enum Type {
		SECRET("Secret", 0xBF5AF2, "chest"), ETHERWARP("Etherwarp", 0x5AC8FA, "star"), LEVER("Lever", 0xFFD60A, "power"), BAT("Bat", 0xFF6EC7, "bug"),
		NORMAL("Note", 0x8E8E93, "page");

		private final String label;
		private final int rgb;
		private final String icon;

		Type(String label, int rgb, String icon) {
			this.label = label;
			this.rgb = rgb;
			this.icon = icon;
		}

		public String label() {
			return label;
		}

		/** @return the type's color as 0xRRGGBB */
		public int rgb() {
			return rgb;
		}

		/** @return the name of the type's icon */
		public String icon() {
			return icon;
		}

		/** @return the type of a name in a list ("SECRET", "secret"), NORMAL for "NONE", "NOTE" and anything not known */
		static Type of(String name) {
			String upper = name.strip().toUpperCase(Locale.ROOT);
			for (Type type : values()) {
				if (type.name().equals(upper)) {
					return type;
				}
			}
			return NORMAL;
		}
	}

	/** The longest title kept. */
	public static final int MAX_TITLE = 48;
	/** The box of a small point, inside its block. */
	public static final double SMALL_FROM = 0.25;
	public static final double SMALL_TO = 0.75;

	public DungeonWaypoint {
		title = title == null ? "" : title.length() > MAX_TITLE ? title.substring(0, MAX_TITLE) : title;
		type = type == null ? Type.NORMAL : type;
	}

	/** @return a point of the whole block in its type's color, not filled, hidden by walls, hiding once found */
	public static DungeonWaypoint block(int x, int y, int z, String title, Type type) {
		Type kind = type == null ? Type.NORMAL : type;
		return new DungeonWaypoint(x, y, z, 0xFF000000 | kind.rgb(), false, false, 0, 0, 0, 1, 1, 1, title, kind, true);
	}

	public boolean secret() {
		return type == Type.SECRET;
	}

	/** @return whether the box is the whole block */
	public boolean fullBlock() {
		return minX == 0 && minY == 0 && minZ == 0 && maxX == 1 && maxY == 1 && maxZ == 1;
	}

	/** @return whether the box is the small one in the middle of the block */
	public boolean smallBox() {
		return minX == SMALL_FROM && minY == SMALL_FROM && minZ == SMALL_FROM && maxX == SMALL_TO && maxY == SMALL_TO && maxZ == SMALL_TO;
	}

	public DungeonWaypoint withTitle(String value) {
		return new DungeonWaypoint(x, y, z, argb, filled, throughWalls, minX, minY, minZ, maxX, maxY, maxZ, value, type, hideFound);
	}

	/** @return the point of another type, in that type's color where it had the old type's color */
	public DungeonWaypoint withType(Type value) {
		int color = (argb & 0xFFFFFF) == type.rgb() ? (argb & 0xFF000000) | value.rgb() : argb;
		return new DungeonWaypoint(x, y, z, color, filled, throughWalls, minX, minY, minZ, maxX, maxY, maxZ, title, value, hideFound);
	}

	public DungeonWaypoint withColor(int value) {
		return new DungeonWaypoint(x, y, z, value, filled, throughWalls, minX, minY, minZ, maxX, maxY, maxZ, title, type, hideFound);
	}

	public DungeonWaypoint withFilled(boolean value) {
		return new DungeonWaypoint(x, y, z, argb, value, throughWalls, minX, minY, minZ, maxX, maxY, maxZ, title, type, hideFound);
	}

	public DungeonWaypoint withThroughWalls(boolean value) {
		return new DungeonWaypoint(x, y, z, argb, filled, value, minX, minY, minZ, maxX, maxY, maxZ, title, type, hideFound);
	}

	public DungeonWaypoint withHideFound(boolean value) {
		return new DungeonWaypoint(x, y, z, argb, filled, throughWalls, minX, minY, minZ, maxX, maxY, maxZ, title, type, value);
	}

	public DungeonWaypoint withPlace(int atX, int atY, int atZ) {
		return new DungeonWaypoint(atX, atY, atZ, argb, filled, throughWalls, minX, minY, minZ, maxX, maxY, maxZ, title, type, hideFound);
	}

	/** @return the point with the whole block, the small box, or its own box as it is */
	public DungeonWaypoint withBox(boolean full, boolean small) {
		if (full) {
			return new DungeonWaypoint(x, y, z, argb, filled, throughWalls, 0, 0, 0, 1, 1, 1, title, type, hideFound);
		}
		if (small) {
			return new DungeonWaypoint(x, y, z, argb, filled, throughWalls, SMALL_FROM, SMALL_FROM, SMALL_FROM, SMALL_TO, SMALL_TO, SMALL_TO, title, type,
				hideFound);
		}
		return this;
	}
}
