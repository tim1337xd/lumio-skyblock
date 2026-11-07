package dev.tim1337.lumio.skyblock.data;

/**
 * A profit tracker's total over its session, for the profit panel's chart and its trend. The tracker records it itself, whatever the panel shows:
 * a sample every few seconds of active time and on every gain (at most one a second), against the active time, so a pause does not stretch the
 * chart. It holds a fixed number of samples: when full, the older ones that change the curve's shape least are dropped (the newest quarter keeps
 * every sample), so a long session still fits and keeps its steps and bends.
 */
public final class ProfitHistory {
	/** The most samples kept. */
	public static final int MOST = 240;
	/** The steady time between two samples, and the least time between two when something was gained, in active milliseconds. */
	static final long STEP_MILLIS = 10_000L;
	static final long LEAST_GAP_MILLIS = 1_000L;
	/** When full, it is thinned to this many samples, never touching the newest {@link #KEPT_NEWEST}. */
	private static final int THINNED = MOST * 3 / 4;
	private static final int KEPT_NEWEST = MOST / 4;
	/** The trend compares the rate now with the rate this long ago, in active time; less history shows no trend. */
	public static final long TREND_MILLIS = 10 * 60_000L;

	private final long[] times = new long[MOST];
	private final double[] totals = new double[MOST];
	private int count;
	private int version;

	/** Starts over: the session was reset. */
	public void clear() {
		count = 0;
		version++;
	}

	/** @return whether a sample is due at this active time: the first one, the steady step, or a gain at least a second after the last */
	public boolean due(long activeMillis, boolean gained) {
		if (count == 0 || activeMillis < times[count - 1]) {
			return true;
		}
		long gap = activeMillis - times[count - 1];
		return gap >= STEP_MILLIS || gained && gap >= LEAST_GAP_MILLIS;
	}

	/**
	 * Takes a sample. A time or total going far back means the session was reset: it starts over.
	 *
	 * @param activeMillis the session's active time so far
	 * @param total        the profit so far
	 */
	public void record(long activeMillis, double total) {
		if (count > 0 && (activeMillis < times[count - 1] || total < totals[count - 1] - Math.abs(totals[count - 1]) * 0.5 - 1)) {
			clear();
		}
		if (count > 0 && activeMillis == times[count - 1]) {
			// the same moment: the newer total replaces it
			totals[count - 1] = total;
			version++;
			return;
		}
		if (count == MOST) {
			thin();
		}
		times[count] = activeMillis;
		totals[count] = total;
		count++;
		version++;
	}

	/**
	 * Drops the older samples that matter least to the curve, one at a time: the one whose triangle with its neighbors is smallest (the point that
	 * lies most nearly on the line between them), until a quarter is gone. The first sample and the newest quarter always stay.
	 */
	private void thin() {
		double timeScale = Math.max(1L, times[count - 1] - times[0]);
		double low = totals[0];
		double high = totals[0];
		for (int i = 1; i < count; i++) {
			low = Math.min(low, totals[i]);
			high = Math.max(high, totals[i]);
		}
		double totalScale = Math.max(1.0, high - low);
		while (count > THINNED) {
			int last = count - KEPT_NEWEST;
			int least = -1;
			double leastArea = Double.MAX_VALUE;
			for (int i = 1; i < last; i++) {
				double area = area(i, timeScale, totalScale);
				if (area < leastArea) {
					leastArea = area;
					least = i;
				}
			}
			if (least < 0) {
				return;
			}
			System.arraycopy(times, least + 1, times, least, count - least - 1);
			System.arraycopy(totals, least + 1, totals, least, count - least - 1);
			count--;
		}
	}

	/** @return twice the area of the triangle a sample makes with its neighbors, on axes scaled to the whole history */
	private double area(int i, double timeScale, double totalScale) {
		double t0 = times[i - 1] / timeScale;
		double t1 = times[i] / timeScale;
		double t2 = times[i + 1] / timeScale;
		double v0 = totals[i - 1] / totalScale;
		double v1 = totals[i] / totalScale;
		double v2 = totals[i + 1] / totalScale;
		return Math.abs((t1 - t0) * (v2 - v0) - (t2 - t0) * (v1 - v0));
	}

	public int count() {
		return count;
	}

	public long time(int index) {
		return times[index];
	}

	public double total(int index) {
		return totals[index];
	}

	/** @return a number that changes whenever a sample is added or the history starts over, so a drawing can be cached */
	public int version() {
		return version;
	}

	/**
	 * @return the change of the rate per hour against the rate {@link #TREND_MILLIS} ago, as a share (0.12 for 12 % more), or NaN without enough
	 *         history
	 */
	public double trend(long activeMillis, double total) {
		if (count < 2 || activeMillis - times[0] < TREND_MILLIS) {
			return Double.NaN;
		}
		long then = activeMillis - TREND_MILLIS;
		int at = 0;
		for (int i = 0; i < count; i++) {
			if (times[i] <= then) {
				at = i;
			}
		}
		if (times[at] <= 0) {
			return Double.NaN;
		}
		double rateThen = totals[at] / times[at];
		double rateNow = total / Math.max(1L, activeMillis);
		return rateThen <= 0 ? Double.NaN : (rateNow - rateThen) / rateThen;
	}
}
