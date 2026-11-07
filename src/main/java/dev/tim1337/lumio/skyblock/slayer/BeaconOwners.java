package dev.tim1337.lumio.skyblock.slayer;

import java.util.List;
import java.util.function.Predicate;

/**
 * Which Voidgloom Seraph threw a beacon, so the beacon alert only fires for the player's own boss (or the carried one): the boss that held a
 * beacon right before it appeared, otherwise the nearest Voidgloom at that moment within a short distance. When it can't be told for sure (no
 * boss close enough, or two equally close), the owner is unknown and nothing alerts. Plain numbers, so it is unit tested.
 */
final class BeaconOwners {
	/** How long after a boss was last seen holding a beacon it still counts as the one that threw it. */
	static final long HELD_MILLIS = 2000L;
	/** How far from the boss holding a beacon a thrown one can appear, in blocks. */
	static final double HOLDER_RANGE = 12.0;
	/** How far from the nearest Voidgloom a beacon may appear to count as its own when no boss was seen holding one, in blocks. */
	static final double NEAR_RANGE = 6.0;
	/** How far from the only Voidgloom around a beacon may appear to be its own: with one boss there is nothing to tell apart. */
	static final double LONE_RANGE = 16.0;
	/** How much closer one boss has to be than the next to tell them apart, in blocks. */
	static final double TIE_BLOCKS = 1.0;

	/**
	 * A Voidgloom Seraph as seen when a beacon appears.
	 *
	 * @param owner  the name from its "Spawned by" tag, or null if unknown
	 * @param heldAt when it was last seen holding a beacon (System millis), or 0 if never
	 */
	record Boss(String owner, double x, double y, double z, long heldAt) {
		double distanceTo(double px, double py, double pz) {
			double dx = x - px;
			double dy = y - py;
			double dz = z - pz;
			return Math.sqrt(dx * dx + dy * dy + dz * dz);
		}
	}

	private BeaconOwners() {
	}

	/** @return the name of the player whose boss threw a beacon that just appeared at a spot, or null when it can't be told for sure */
	static String ownerOf(double x, double y, double z, long now, List<Boss> bosses) {
		Boss holder = nearest(x, y, z, bosses, HOLDER_RANGE, boss -> boss.heldAt() > 0 && now - boss.heldAt() <= HELD_MILLIS);
		if (holder != null) {
			return holder.owner();
		}
		if (anyHolderClose(x, y, z, now, bosses)) {
			// several held one: too close to tell apart
			return null;
		}
		if (bosses.size() == 1 && bosses.get(0).distanceTo(x, y, z) <= LONE_RANGE) {
			return bosses.get(0).owner();
		}
		Boss nearest = nearest(x, y, z, bosses, NEAR_RANGE, boss -> true);
		return nearest == null ? null : nearest.owner();
	}

	private static boolean anyHolderClose(double x, double y, double z, long now, List<Boss> bosses) {
		for (Boss boss : bosses) {
			if (boss.heldAt() > 0 && now - boss.heldAt() <= HELD_MILLIS && boss.distanceTo(x, y, z) <= HOLDER_RANGE) {
				return true;
			}
		}
		return false;
	}

	/** @return the one boss within range that is clearly the nearest of those passing the test, or null (none, or two about as near) */
	private static Boss nearest(double x, double y, double z, List<Boss> bosses, double range, Predicate<Boss> test) {
		Boss best = null;
		double bestDistance = Double.MAX_VALUE;
		double secondDistance = Double.MAX_VALUE;
		for (Boss boss : bosses) {
			if (!test.test(boss)) {
				continue;
			}
			double distance = boss.distanceTo(x, y, z);
			if (distance > range) {
				continue;
			}
			if (distance < bestDistance) {
				secondDistance = bestDistance;
				bestDistance = distance;
				best = boss;
			} else if (distance < secondDistance) {
				secondDistance = distance;
			}
		}
		if (best == null || secondDistance - bestDistance < TIE_BLOCKS) {
			return null;
		}
		return best;
	}
}
