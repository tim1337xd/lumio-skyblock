package dev.tim1337.lumio.skyblock.fishing;

import dev.tim1337.lumio.api.experimental.util.TextColors;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.material.FluidState;

/**
 * The fishing hotspots the game shows: each is a name tag (an armor stand named "HOTSPOT") over an area of water or lava that the server marks
 * with particles. Found from the name tags once a second (only those the client has loaded, as the game shows them); each one's surface is the
 * fluid under its tag, and its radius is measured from its own particles: the kind most sent around it, by how far out they reach. Nothing is
 * guessed from outside what the game shows.
 */
final class Hotspots {
	/** A name tag with this word (any case, colors taken off) is a hotspot's. */
	private static final String WORD = "hotspot";
	/** How far under its name tag a hotspot's fluid surface is looked for, in blocks. */
	private static final int SURFACE_DEPTH = 8;
	/** Particles count as a hotspot's within this far from its middle across the ground and this far from its surface, in blocks. */
	private static final double REACH = 10;
	private static final double SURFACE_BAND = 2.5;
	/** Its radius before it has enough particles to measure, and the bounds of a measured one, in blocks. */
	static final float FIRST_RADIUS = 3f;
	private static final float LEAST_RADIUS = 1.5f;
	private static final float MOST_RADIUS = 8f;
	/** How many of its particles' distances are kept, how many it takes to measure, and the share of them the radius reaches. */
	private static final int SAMPLES = 64;
	private static final int ENOUGH = 16;
	private static final float REACHES = 0.9f;
	/**
	 * A measured hotspot ignores a particle this much farther out than its radius (and at least {@link #OUTLIER_BLOCKS} out): a burst of late
	 * particles after a lag, a neighbouring hotspot's or another player's are no reason for it to grow.
	 */
	private static final float OUTLIER_SHARE = 1.4f;
	private static final float OUTLIER_BLOCKS = 1.5f;
	/** This many outliers over twice the change time are no lag but a hotspot grown far beyond its old size. */
	private static final int LASTING_OUTLIERS = 24;
	/** A new size is taken only when it differs this much and stays so this long: the hotspot really changed. */
	private static final float CHANGE_SHARE = 0.12f;
	static final long CHANGE_MILLIS = 2_000L;
	/** How long the shown size takes to reach a new one. */
	static final long EASE_MILLIS = 400L;

	/** One hotspot. */
	static final class Spot {
		final int entityId;
		double x;
		double z;
		double surface;
		/** The size shown, easing toward {@link #size}. */
		float radius = FIRST_RADIUS;
		/** The size taken, and a measured one that differs from it, since when. */
		float size = FIRST_RADIUS;
		private float candidate = Float.NaN;
		/** Particles ignored as outliers lately, since when. */
		private int outliers;
		private long outliersSince;
		private long candidateSince;
		private long easedAt;
		boolean seen;
		/** The kind of particle that marks it, by a running majority vote (no counting per kind, so nothing is allocated per particle). */
		ParticleType<?> kind;
		int votes;
		private final float[] distances = new float[SAMPLES];
		private final float[] sorted = new float[SAMPLES];
		private int samples;
		private int next;

		Spot(int entityId) {
			this.entityId = entityId;
		}

		/** @return whether its particles are known well enough to measure it and to hide them */
		boolean measured() {
			return samples >= ENOUGH;
		}

		private void vote(ParticleType<?> type) {
			if (votes == 0) {
				kind = type;
				votes = 1;
			} else {
				votes += kind == type ? 1 : -1;
			}
		}

		void sample(float distance, long now) {
			if (measured() && distance > Math.max(size * OUTLIER_SHARE, size + OUTLIER_BLOCKS)) {
				// a moment of outliers (a lag) is ignored; outliers that go on for long are the hotspot grown far: it is measured afresh
				if (outliers++ == 0) {
					outliersSince = now;
				}
				if (outliers < LASTING_OUTLIERS || now - outliersSince < 2 * CHANGE_MILLIS) {
					return;
				}
				samples = 0;
				next = 0;
			}
			if (now - outliersSince > 2 * CHANGE_MILLIS) {
				outliers = 0;
			}
			distances[next] = distance;
			next = (next + 1) % SAMPLES;
			samples = Math.min(SAMPLES, samples + 1);
			if (samples < ENOUGH || next % 4 != 0) {
				return;
			}
			System.arraycopy(distances, 0, sorted, 0, samples);
			Arrays.sort(sorted, 0, samples);
			float measured = Math.clamp(sorted[Math.min(samples - 1, Math.round(samples * REACHES))], LEAST_RADIUS, MOST_RADIUS);
			if (samples == ENOUGH && size == FIRST_RADIUS) {
				// the first measurement replaces the guess before it at once
				size = measured;
				candidate = Float.NaN;
			} else if (Math.abs(measured - size) <= size * CHANGE_SHARE) {
				candidate = Float.NaN;
			} else if (Float.isNaN(candidate) || Math.abs(measured - candidate) > candidate * CHANGE_SHARE) {
				candidate = measured;
				candidateSince = now;
			} else if (now - candidateSince >= CHANGE_MILLIS) {
				size = measured;
				candidate = Float.NaN;
			}
		}

		/** Moves the shown size toward the one taken, with a short ease. */
		void ease(long now) {
			long elapsed = easedAt == 0L ? 0L : Math.max(0L, now - easedAt);
			easedAt = now;
			radius += (size - radius) * Math.min(1f, elapsed / (float) EASE_MILLIS);
		}
	}

	private final List<Spot> spots = new ArrayList<>();

	/** Looks for the name tags; once a second. */
	void scan(ClientLevel level) {
		for (Spot spot : spots) {
			spot.seen = false;
		}
		for (Entity entity : level.entitiesForRendering()) {
			if (!(entity instanceof ArmorStand) || !named(entity.getCustomName())) {
				continue;
			}
			Spot spot = find(entity.getId());
			if (spot == null) {
				double surface = surface(level, entity.getX(), entity.getY(), entity.getZ());
				if (Double.isNaN(surface)) {
					continue;
				}
				spot = new Spot(entity.getId());
				spot.surface = surface;
				spots.add(spot);
			}
			spot.x = entity.getX();
			spot.z = entity.getZ();
			spot.seen = true;
		}
		spots.removeIf(spot -> !spot.seen);
	}

	private static boolean named(Component name) {
		return name != null && TextColors.noColors(name.getString()).toLowerCase(Locale.ROOT).contains(WORD);
	}

	private Spot find(int entityId) {
		for (Spot spot : spots) {
			if (spot.entityId == entityId) {
				return spot;
			}
		}
		return null;
	}

	/** @return the top of the water or lava under a name tag, or NaN where there is none */
	private static double surface(ClientLevel level, double x, double y, double z) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int top = (int) Math.floor(y);
		for (int down = 0; down <= SURFACE_DEPTH; down++) {
			at.set(Math.floor(x), top - down, Math.floor(z));
			FluidState fluid = level.getFluidState(at);
			if (!fluid.isEmpty()) {
				return at.getY() + fluid.getHeight(level, at);
			}
		}
		return Double.NaN;
	}

	/** A particle from the server: counted for the hotspot it lies in, if any. The radar's trail (flames) never counts. */
	void particle(ParticleType<?> type, double x, double y, double z, long now) {
		if (type == ParticleTypes.FLAME) {
			return;
		}
		for (Spot spot : spots) {
			double across = Math.hypot(x - spot.x, z - spot.z);
			if (across <= REACH && Math.abs(y - spot.surface) <= SURFACE_BAND) {
				spot.vote(type);
				if (type == spot.kind) {
					spot.sample((float) across, now);
				}
				return;
			}
		}
	}

	/** Eases every hotspot's shown size toward the one taken; each frame. */
	void ease(long now) {
		for (Spot spot : spots) {
			spot.ease(now);
		}
	}

	/** @return whether a particle is one of a measured hotspot's own, inside its area: those the player may choose not to see */
	boolean marks(ParticleType<?> type, double x, double y, double z) {
		for (Spot spot : spots) {
			if (spot.measured() && type == spot.kind && Math.abs(y - spot.surface) <= SURFACE_BAND
				&& Math.hypot(x - spot.x, z - spot.z) <= spot.size + 1) {
				return true;
			}
		}
		return false;
	}

	List<Spot> spots() {
		return spots;
	}

	void clear() {
		spots.clear();
	}
}
