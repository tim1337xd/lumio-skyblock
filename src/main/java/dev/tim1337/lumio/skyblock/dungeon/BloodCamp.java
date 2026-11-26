package dev.tim1337.lumio.skyblock.dungeon;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The blood room's mobs on their way: the Watcher throws each one out in a straight line, and it lands a fixed distance away a fixed number of
 * server ticks after its first move, farther and later in the first wave (before "Let's see how you can handle this.") than after it. Each mob is
 * followed by the positions the server sends, never the client's smoothed one: its way is the sum of all its moves since it appeared, so the landing
 * gets more exact with every move. Only what the server sends of a mob's flight is used. Plain logic, so it is tested without the game.
 */
public final class BloodCamp {
	/** How far a mob flies before it lands, in blocks: in the first wave and later. */
	static final double FIRST_WAVE_FLIGHT = 16.1;
	static final double LATER_FLIGHT = 11.9;
	/** The first wave waits this much longer, in server ticks. */
	static final int FIRST_WAVE_WAIT_TICKS = 40;
	/** The spawn tick, counted from a mob's first move: the default and the range the setting allows. */
	public static final int SPAWN_TICK = 38;
	public static final int LEAST_SPAWN_TICK = 35;
	public static final int MOST_SPAWN_TICK = 41;
	/** A move shorter than this (in blocks, across) is not a move: the server sends the same place again. */
	private static final double STILL = 1e-4;
	private static final int MOST_MOBS = 64;
	/** A head that has not moved this many server ticks after it was first seen is no flying mob (one that landed before, or stands still). */
	static final int STILL_TICKS = 60;
	private static final long MILLIS_PER_TICK = 50;

	/**
	 * The player's adjustments: the spawn tick, an offset of the shown time in milliseconds, and the player's ping in milliseconds where the
	 * countdown is shifted by it (the server is that much ahead of what the player sees), 0 otherwise.
	 */
	public record Settings(int spawnTick, int offsetMillis, int pingMillis) {
		public static final Settings DEFAULT = new Settings(SPAWN_TICK, 40);

		public Settings(int spawnTick, int offsetMillis) {
			this(spawnTick, offsetMillis, 0);
		}
	}

	/**
	 * What became of a mob, for the debug probe: the tick its skull started to move, the spawn tick and point predicted for it, and the tick and
	 * place where its skull was last seen (where the mob really appeared), with how its flight ended.
	 */
	public record Report(int id, boolean firstWave, long startTick, long predictedTick, double predictedX, double predictedY, double predictedZ,
		long endTick, double endX, double endY, double endZ, String how) {
		/** @return how many ticks the skull was there past the predicted spawn tick (below 0: gone before it) */
		public long ticksOff() {
			return endTick - predictedTick;
		}

		/** @return how far the skull's last place was from the predicted spawn point, across, in blocks */
		public double blocksOff() {
			return Math.hypot(endX - predictedX, endZ - predictedZ);
		}
	}

	/**
	 * Where a mob lands and when: the landing, the time left in milliseconds (below 0 when it should have landed), the mob's own place now (as the
	 * server last sent it) and how far it moved in its last tick, for drawing it a little ahead.
	 */
	public record Landing(int id, double x, double y, double z, long millisLeft, double nowX, double nowY, double nowZ, double stepX, double stepY,
		double stepZ) {
	}

	private static final class Flight {
		final double startX;
		final double startY;
		final double startZ;
		final boolean firstWave;
		final long firstSeenTick;
		long firstMoveTick = -1;
		/** Whether its spawn tick has come: it has no box any more, and is only followed to see where its skull really ends. */
		boolean landed;
		/** Whether it is known to be one of the Watcher's mobs: on the list, or seen to fly away from him. Until then it is followed without a box. */
		boolean confirmed;
		long predictedTick;
		double predictedX;
		double predictedY;
		double predictedZ;
		long lastSeenTick;
		double x;
		double y;
		double z;
		double stepX;
		double stepY;
		double stepZ;

		Flight(double x, double y, double z, boolean firstWave, long firstSeenTick) {
			this.firstSeenTick = firstSeenTick;
			startX = x;
			startY = y;
			startZ = z;
			this.x = x;
			this.y = y;
			this.z = z;
			this.firstWave = firstWave;
		}
	}

	private final Map<Integer, Flight> flights = new HashMap<>();
	/** Mobs that landed or died: never followed again, also while their head is still near the Watcher. */
	private final IntOpenHashSet done = new IntOpenHashSet();
	/** What became of the mobs whose flight ended since the last {@link #drainReports}. */
	private final List<Report> reports = new ArrayList<>();
	/** A skull still there this many ticks after its spawn tick is let go. */
	static final int LINGER_TICKS = 100;
	private static final int MOST_REPORTS = 64;
	private boolean laterWaves;
	private boolean active;
	private long openedAt = -1;

	/** The first Watcher line of the run: mobs are watched, and his move is counted from now. */
	public void opened(long tick) {
		if (openedAt < 0) {
			openedAt = tick;
			active = true;
		}
	}

	/** "Let's see how you can handle this.": mobs from now on fly the shorter way. */
	public void wavesStarted() {
		laterWaves = true;
	}

	/** "That will be enough for now." or "You have proven yourself.": no more mobs come. */
	public void ended() {
		active = false;
		flights.clear();
		done.clear();
		reports.clear();
	}

	/** @return whether mobs are watched: between the first Watcher line and his last */
	public boolean active() {
		return active;
	}

	/** @return the server tick of the first Watcher line, or -1 */
	public long openedAt() {
		return openedAt;
	}

	public boolean laterWaves() {
		return laterWaves;
	}

	/** A blood mob at the place the server last sent for it; one seen for the first time starts its flight there. */
	public void seen(int id, double x, double y, double z, long tick) {
		seen(id, x, y, z, tick, true);
	}

	/**
	 * The same for a head that may be one of his mobs: it is followed from its first place on, so its way is whole, but has no box and no
	 * countdown until it is confirmed (see {@link BloodMobs#thrownOut}).
	 */
	public void seen(int id, double x, double y, double z, long tick, boolean confirmed) {
		Flight flight = flights.get(id);
		if (flight == null) {
			if (done.contains(id)) {
				return;
			}
			if (active && flights.size() < MOST_MOBS) {
				Flight fresh = new Flight(x, y, z, !laterWaves, tick);
				fresh.confirmed = confirmed;
				flights.put(id, fresh);
			}
			return;
		}
		flight.confirmed |= confirmed;
		double dx = x - flight.x;
		double dz = z - flight.z;
		if (dx * dx + dz * dz > STILL * STILL) {
			if (flight.firstMoveTick < 0) {
				flight.firstMoveTick = tick;
			}
			flight.stepX = dx;
			flight.stepY = y - flight.y;
			flight.stepZ = dz;
		}
		flight.x = x;
		flight.y = y;
		flight.z = z;
		flight.lastSeenTick = tick;
	}

	/** Forgets the mobs whose skull is no longer there: the mob has appeared in its place (or the skull left the Watcher's reach). */
	public void keepOnly(IntSet there) {
		for (Iterator<Map.Entry<Integer, Flight>> it = flights.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Flight> entry = it.next();
			if (!there.contains(entry.getKey())) {
				report(entry.getKey(), entry.getValue(), entry.getValue().landed ? "skull gone, mob spawned" : "skull gone before its spawn tick");
				it.remove();
				if (entry.getValue().landed) {
					finished(entry.getKey());
				}
			}
		}
	}

	/** Notes what became of a mob that had started to fly, for the debug probe. */
	private void report(int id, Flight flight, String how) {
		if (flight.firstMoveTick < 0 || !flight.confirmed || reports.size() >= MOST_REPORTS) {
			return;
		}
		if (!flight.landed) {
			predict(flight, SPAWN_TICK);
		}
		reports.add(new Report(id, flight.firstWave, flight.firstMoveTick, flight.predictedTick, flight.predictedX, flight.predictedY, flight.predictedZ,
			flight.lastSeenTick, flight.x, flight.y, flight.z, how));
	}

	/** Works out a mob's spawn tick and point from its way so far. */
	private static void predict(Flight flight, int spawnTick) {
		double dx = flight.x - flight.startX;
		double dy = flight.y - flight.startY;
		double dz = flight.z - flight.startZ;
		double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
		double reach = length < STILL ? 0 : (flight.firstWave ? FIRST_WAVE_FLIGHT : LATER_FLIGHT) / length;
		flight.predictedTick = flight.firstMoveTick + spawnTick + (flight.firstWave ? FIRST_WAVE_WAIT_TICKS : 0);
		flight.predictedX = flight.startX + dx * reach;
		flight.predictedY = flight.startY + dy * reach;
		flight.predictedZ = flight.startZ + dz * reach;
	}

	/** @return what became of the mobs whose flight ended since the last call, for the debug probe; the list is emptied */
	public List<Report> drainReports() {
		if (reports.isEmpty()) {
			return List.of();
		}
		List<Report> out = List.copyOf(reports);
		reports.clear();
		return out;
	}

	/** @return where the flying mobs land and when; a mob that has not moved yet is left out, as its way is not known */
	public List<Landing> landings(long tick, Settings settings) {
		List<Landing> landings = new ArrayList<>(flights.size());
		for (Map.Entry<Integer, Flight> entry : flights.entrySet()) {
			Flight flight = entry.getValue();
			if (flight.firstMoveTick < 0 || flight.landed || !flight.confirmed) {
				continue;
			}
			// the way: every move since it appeared, added up, which is where it is now from where it started
			double dx = flight.x - flight.startX;
			double dy = flight.y - flight.startY;
			double dz = flight.z - flight.startZ;
			double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
			if (length < STILL) {
				continue;
			}
			double reach = (flight.firstWave ? FIRST_WAVE_FLIGHT : LATER_FLIGHT) / length;
			long millisLeft = (landsAt(flight, settings) - tick) * MILLIS_PER_TICK + settings.offsetMillis() - settings.pingMillis();
			landings.add(new Landing(entry.getKey(), flight.startX + dx * reach, flight.startY + dy * reach, flight.startZ + dz * reach, millisLeft,
				flight.x, flight.y, flight.z, flight.stepX, flight.stepY, flight.stepZ));
		}
		return landings;
	}

	/** How close a mob's spawn is: more than 1.5 s away, 0.5 s to 1.5 s, or less than 0.5 s. */
	public enum Urgency { CALM, SOON, NOW }

	private static final long CALM_ABOVE_MILLIS = 1500;
	private static final long NOW_BELOW_MILLIS = 500;
	private static final long MILLIS_PER_SECOND = 1000;
	private static final long MILLIS_PER_TENTH = 100;
	private static final long TENTHS = 10;

	/** @return how close a spawn is by the time left on its countdown */
	public static Urgency urgency(long millisLeft) {
		return millisLeft > CALM_ABOVE_MILLIS ? Urgency.CALM : millisLeft >= NOW_BELOW_MILLIS ? Urgency.SOON : Urgency.NOW;
	}

	/** @return a mob's countdown as shown: "1.4s"; never below "0.0s" */
	public static String countdownText(long millisLeft) {
		long shown = Math.max(0, millisLeft);
		return shown / MILLIS_PER_SECOND + "." + shown / MILLIS_PER_TENTH % TENTHS + "s";
	}

	/** @return the shortest countdown of the mobs in flight, or {@link Long#MAX_VALUE} without one: what the "Kill mobs" title goes by */
	public static long soonest(List<Landing> landings) {
		long soonest = Long.MAX_VALUE;
		for (Landing each : landings) {
			soonest = Math.min(soonest, each.millisLeft());
		}
		return soonest;
	}

	/** A head turned out not to be one of the Watcher's mobs (it did not fly away from him): it is dropped and never followed again. */
	public void notHis(int id) {
		flights.remove(id);
		finished(id);
	}

	/** A mob died: it is not followed any more. */
	public void died(int id) {
		Flight flight = flights.remove(id);
		if (flight != null) {
			report(id, flight, "died");
		}
		finished(id);
	}

	private void finished(int id) {
		if (done.size() < MOST_MOBS * 4) {
			done.add(id);
		}
	}

	/**
	 * Lets go of the mobs whose flight is over: those that reached their spawn tick (they have landed), and heads that never moved since they
	 * were first seen. Called every tick before {@link #landings}.
	 */
	public void settle(long tick, Settings settings) {
		for (Iterator<Map.Entry<Integer, Flight>> it = flights.entrySet().iterator(); it.hasNext();) {
			Map.Entry<Integer, Flight> entry = it.next();
			Flight flight = entry.getValue();
			boolean landed = flight.firstMoveTick >= 0 && tick >= landsAt(flight, settings);
			boolean still = flight.firstMoveTick < 0 && tick - flight.firstSeenTick > STILL_TICKS;
			if (landed && !flight.landed) {
				// its spawn tick: the box goes; the skull is followed on only to note where the mob really appears
				predict(flight, settings.spawnTick());
				flight.landed = true;
				finished(entry.getKey());
			} else if (flight.landed && tick - flight.predictedTick > LINGER_TICKS) {
				report(entry.getKey(), flight, "skull still there");
				it.remove();
			} else if (still) {
				it.remove();
				finished(entry.getKey());
			}
		}
	}

	private static long landsAt(Flight flight, Settings settings) {
		return flight.firstMoveTick + settings.spawnTick() + (flight.firstWave ? FIRST_WAVE_WAIT_TICKS : 0);
	}

	/** @return how many mobs are followed now: in flight, not landed or dead */
	public int tracked() {
		int flying = 0;
		for (Flight flight : flights.values()) {
			flying += flight.landed || !flight.confirmed ? 0 : 1;
		}
		return flying;
	}

	/** The run ended or the world changed: nothing is known any more. */
	public void reset() {
		flights.clear();
		done.clear();
		reports.clear();
		laterWaves = false;
		active = false;
		openedAt = -1;
	}
}
