package dev.tim1337.lumio.skyblock.fishing;

/**
 * When the radar's waypoint has done its job: the player is inside the hotspot or near its middle (fishing into it from the shore counts), or the
 * player's own bobber landed in it. Across the ground only. Plain logic, tested without the game.
 */
final class RadarArrival {
	/** The player counts as there this near the middle, or anywhere inside a larger hotspot. */
	static final double NEAR_BLOCKS = 8.0;
	/** A hotspot's radius while the game shows none at the place (they measure about 3 blocks). */
	static final double USUAL_RADIUS = 3.0;
	/** A hotspot the game shows is the one the waypoint points at when its middle lies this much beyond its radius from the waypoint, at most. */
	static final double MATCH_MARGIN = 3.0;

	private RadarArrival() {
	}

	/**
	 * @param middleX    the hotspot's middle: the shown hotspot's, or the waypoint's where none shows
	 * @param radius     the shown hotspot's radius, or NaN where none shows
	 * @param hookLanded whether the player's bobber lies in water or lava now (its place is then hookX, hookZ)
	 * @return whether the waypoint can go
	 */
	static boolean reached(double middleX, double middleZ, double radius, double playerX, double playerZ, boolean hookLanded, double hookX, double hookZ) {
		double size = Double.isNaN(radius) ? USUAL_RADIUS : radius;
		if (Math.hypot(playerX - middleX, playerZ - middleZ) <= Math.max(NEAR_BLOCKS, size)) {
			return true;
		}
		return hookLanded && Math.hypot(hookX - middleX, hookZ - middleZ) <= size;
	}

	/** @return whether a shown hotspot is the one at the waypoint */
	static boolean matches(double spotX, double spotZ, double spotRadius, double waypointX, double waypointZ) {
		return Math.hypot(spotX - waypointX, spotZ - waypointZ) <= spotRadius + MATCH_MARGIN;
	}
}
