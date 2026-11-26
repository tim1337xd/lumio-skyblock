package dev.tim1337.lumio.skyblock.fishing;

import java.util.ArrayList;
import java.util.List;

/**
 * One use of the Hotspot Radar: the flame trail the game sends after it, and where the trail comes to rest. As recorded in the game, the
 * trail is a cubic curve in the flame's number: it starts one block ahead of the eyes, leaves them in the look's direction across the ground,
 * bends toward the hotspot and slows down until it stops on the hotspot's middle, beyond the last flame the game sends. So one use gives the place:
 * with the start and its direction known, three flames fix the curve once it bends; four always do. Each flame refines the estimate. Plain logic,
 * tested with the recorded trails.
 */
final class RadarTrail {
	/** The first flame lies this near one block ahead of the eyes (it is exactly there; the margin covers the player moving meanwhile). */
	static final double START_NEAR = 2.5;
	/** Flames of the trail lie at most this far apart (the recorded steps grow to 2.7 blocks). */
	static final double STEP_MOST = 4.0;
	/** Fewer flames than this fix no curve. */
	static final int LEAST_POINTS = 3;
	/** An estimate is given only when every curve that fits about as well ends within this many blocks of it. */
	static final double MOST_SPREAD = 8.0;
	/** "About as well": a sum of squares this much above the best, far above the rounding of the server's coordinates. */
	private static final double FIT_MARGIN = 1e-7;
	/** A trail that fits no such curve this well per flame (squared blocks) is not the radar's. */
	private static final double LEAST_FIT = 1e-3;
	/** Where the curve may stop, in flames after the first: searched in these steps up to the most, then refined. */
	private static final double END_STEP = 0.05;
	private static final double END_MOST = 300;
	private static final int MOST_POINTS = 64;

	/** Where the trail stops: the hotspot's middle, how far curves that fit about as well end from it, and from how many flames. */
	record Estimate(double x, double y, double z, double spread, int points) {
	}

	private final double startX;
	private final double startY;
	private final double startZ;
	/** The look's direction across the ground, unit length; both 0 when the player looked straight up or down. */
	private final double lookX;
	private final double lookZ;
	private final List<double[]> points = new ArrayList<>();

	/** A use: the player's eyes and look (unit length) at the right-click. */
	RadarTrail(double eyeX, double eyeY, double eyeZ, double lookX, double lookY, double lookZ) {
		startX = eyeX + lookX;
		startY = eyeY + lookY;
		startZ = eyeZ + lookZ;
		double flat = Math.hypot(lookX, lookZ);
		this.lookX = flat < 0.05 ? 0 : lookX / flat;
		this.lookZ = flat < 0.05 ? 0 : lookZ / flat;
	}

	/** @return whether a flame is the trail's next point (and was taken) */
	boolean add(double x, double y, double z) {
		if (points.size() >= MOST_POINTS) {
			return false;
		}
		double[] from = points.isEmpty() ? new double[] {startX, startY, startZ} : points.getLast();
		double most = points.isEmpty() ? START_NEAR : STEP_MOST;
		double dx = x - from[0];
		double dy = y - from[1];
		double dz = z - from[2];
		if (dx * dx + dy * dy + dz * dz > most * most) {
			return false;
		}
		points.add(new double[] {x, y, z});
		return true;
	}

	int size() {
		return points.size();
	}

	/**
	 * Fits the curve: the first flame is its start, across the ground it leaves in the look's direction, and at the flame number {@code end} it stops.
	 * Per axis across the ground, {@code p(t) - p(0) = b (t - t^2 / 2N) + d (t^3 - 3/2 N t^2)}, whose speed is 0 at N; b is the look's direction times
	 * one shared factor. For each N tried this is a small least squares; the best N wins.
	 *
	 * @return where the trail comes to rest, or null while the flames do not fix it yet
	 */
	Estimate estimate() {
		int n = points.size();
		if (n < LEAST_POINTS) {
			return null;
		}
		boolean looked = lookX != 0 || lookZ != 0;
		if (!looked && n < LEAST_POINTS + 1) {
			return null;
		}
		double first = n - 1 + END_STEP;
		double bestEnd = Double.NaN;
		double bestSquares = Double.MAX_VALUE;
		double[] fit = new double[5];
		for (double end = first; end <= END_MOST; end += END_STEP) {
			double squares = fit(end, looked, fit);
			if (squares < bestSquares) {
				bestSquares = squares;
				bestEnd = end;
			}
		}
		if (Double.isNaN(bestEnd) || bestEnd >= END_MOST - END_STEP || bestSquares > LEAST_FIT * n) {
			return null;
		}
		// refined between the neighbouring steps
		double low = Math.max(first, bestEnd - END_STEP);
		double high = Math.min(END_MOST, bestEnd + END_STEP);
		for (int i = 0; i < 40; i++) {
			double a = low + (high - low) / 3;
			double b = high - (high - low) / 3;
			if (fit(a, looked, fit) <= fit(b, looked, fit)) {
				high = b;
			} else {
				low = a;
			}
		}
		double end = (low + high) / 2;
		bestSquares = Math.min(bestSquares, fit(end, looked, fit));
		double x = endX(end, fit);
		double z = endZ(end, fit);
		// how far other curves that fit about as well end: a trail that has not bent yet fits curves of any length
		double spread = 0;
		double[] other = new double[5];
		for (double tried = first; tried <= END_MOST; tried += END_STEP) {
			if (fit(tried, looked, other) <= bestSquares + FIT_MARGIN) {
				spread = Math.max(spread, Math.hypot(endX(tried, other) - x, endZ(tried, other) - z));
				if (spread > MOST_SPREAD) {
					return null;
				}
			}
		}
		return new Estimate(x, endY(end), z, spread, n);
	}

	/**
	 * The least squares for one end N.
	 *
	 * @param into gets bx, dx, bz, dz
	 * @return the sum of squares left, or the most double where it cannot be solved
	 */
	private double fit(double end, boolean looked, double[] into) {
		double[] p0 = points.getFirst();
		int n = points.size();
		// unknowns: looked {k, dx, dz} with bx = k lookX, bz = k lookZ; else {bx, dx, bz, dz}
		int size = looked ? 3 : 4;
		double[][] normal = new double[size][size + 1];
		double[] row = new double[size];
		for (int i = 1; i < n; i++) {
			double t = i;
			double f = t - t * t / (2 * end);
			double g = t * t * t - 1.5 * end * t * t;
			for (int axis = 0; axis < 2; axis++) {
				double value = points.get(i)[axis == 0 ? 0 : 2] - p0[axis == 0 ? 0 : 2];
				java.util.Arrays.fill(row, 0);
				if (looked) {
					row[0] = f * (axis == 0 ? lookX : lookZ);
					row[axis == 0 ? 1 : 2] = g;
				} else {
					row[axis * 2] = f;
					row[axis * 2 + 1] = g;
				}
				for (int r = 0; r < size; r++) {
					for (int c = 0; c < size; c++) {
						normal[r][c] += row[r] * row[c];
					}
					normal[r][size] += row[r] * value;
				}
			}
		}
		double[] solved = solve(normal, size);
		if (solved == null) {
			return Double.MAX_VALUE;
		}
		if (looked) {
			into[0] = solved[0] * lookX;
			into[1] = solved[1];
			into[2] = solved[0] * lookZ;
			into[3] = solved[2];
		} else {
			System.arraycopy(solved, 0, into, 0, 4);
		}
		double squares = 0;
		for (int i = 1; i < n; i++) {
			double t = i;
			double f = t - t * t / (2 * end);
			double g = t * t * t - 1.5 * end * t * t;
			double ex = points.get(i)[0] - p0[0] - into[0] * f - into[1] * g;
			double ez = points.get(i)[2] - p0[2] - into[2] * f - into[3] * g;
			squares += ex * ex + ez * ez;
		}
		return squares;
	}

	private double endX(double end, double[] fit) {
		return points.getFirst()[0] + fit[0] * end / 2 - fit[1] * end * end * end / 2;
	}

	private double endZ(double end, double[] fit) {
		return points.getFirst()[2] + fit[2] * end / 2 - fit[3] * end * end * end / 2;
	}

	/**
	 * The height where the curve stops: unlike across the ground, the height is still falling there, so it is a plain cubic in the flame number
	 * by least squares, taken at the end. Three flames fix no cubic (a parabola falls far too low): until the fourth, the first flame's height.
	 */
	private double endY(double end) {
		int n = points.size();
		if (n < 4) {
			return points.getFirst()[1];
		}
		int size = 4;
		double[][] normal = new double[size][size + 1];
		for (int i = 0; i < n; i++) {
			double value = points.get(i)[1];
			for (int r = 0; r < size; r++) {
				double tr = Math.pow(i, r);
				for (int c = 0; c < size; c++) {
					normal[r][c] += tr * Math.pow(i, c);
				}
				normal[r][size] += tr * value;
			}
		}
		double[] solved = solve(normal, size);
		if (solved == null) {
			return points.getLast()[1];
		}
		double y = 0;
		for (int k = size - 1; k >= 0; k--) {
			y = y * end + solved[k];
		}
		return y;
	}

	/** Gaussian elimination with pivoting on an augmented matrix (changed in place); null when it is singular. */
	private static double[] solve(double[][] m, int size) {
		for (int c = 0; c < size; c++) {
			int pivot = c;
			for (int r = c + 1; r < size; r++) {
				if (Math.abs(m[r][c]) > Math.abs(m[pivot][c])) {
					pivot = r;
				}
			}
			if (Math.abs(m[pivot][c]) < 1e-12) {
				return null;
			}
			double[] swap = m[c];
			m[c] = m[pivot];
			m[pivot] = swap;
			for (int r = 0; r < size; r++) {
				if (r != c) {
					double factor = m[r][c] / m[c][c];
					for (int k = c; k <= size; k++) {
						m[r][k] -= factor * m[c][k];
					}
				}
			}
		}
		double[] out = new double[size];
		for (int r = 0; r < size; r++) {
			out[r] = m[r][size] / m[r][r];
		}
		return out;
	}
}
