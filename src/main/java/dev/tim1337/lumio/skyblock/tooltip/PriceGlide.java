package dev.tim1337.lumio.skyblock.tooltip;

/** A shown price that goes smoothly to a better one when it arrives, instead of jumping. A new item starts at its number at once. Plain numbers, so it is unit tested. */
final class PriceGlide {
	static final long GLIDE_MILLIS = 250L;

	private double from = Double.NaN;
	private double to = Double.NaN;
	private long startedAt;

	/** Forgets what was shown: the next number is simply there. */
	void reset() {
		from = Double.NaN;
		to = Double.NaN;
	}

	/** @return what to show now of a number: only a known number that replaces another known one glides */
	double follow(double target, long now) {
		boolean known = !Double.isNaN(target);
		if (!known) {
			reset();
			return target;
		}
		if (Double.isNaN(to)) {
			from = target;
			to = target;
			startedAt = now;
			return target;
		}
		if (target != to) {
			from = shown(now);
			to = target;
			startedAt = now;
		}
		return shown(now);
	}

	private double shown(long now) {
		float share = Math.clamp((now - startedAt) / (float) GLIDE_MILLIS, 0f, 1f);
		float rest = 1f - share;
		return from + (to - from) * (1f - rest * rest * rest);
	}
}
