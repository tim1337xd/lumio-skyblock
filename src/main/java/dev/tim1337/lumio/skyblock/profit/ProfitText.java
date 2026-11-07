package dev.tim1337.lumio.skyblock.profit;

import dev.tim1337.lumio.api.experimental.draw.PanelKit;

/** Numbers of the profit trackers that have to fit a small place. Plain text, so it is unit tested. */
final class ProfitText {
	/** From here on a total has one decimal less: "128.6M", not "128.58M". */
	private static final double ONE_DECIMAL_MILLIONS = 100_000_000.0;
	private static final double ONE_DECIMAL_BILLIONS = 100_000_000_000.0;
	private static final double MILLION = 1_000_000.0;
	private static final double BILLION = 1_000_000_000.0;

	private ProfitText() {
	}

	/** @return a total, short enough for the large line beside the ring, with its sign */
	static String total(double value) {
		String sign = value >= 0 ? "+" : "-";
		double size = Math.abs(value);
		if (size >= ONE_DECIMAL_BILLIONS) {
			return sign + PanelKit.decimals(size / BILLION, 1) + "B";
		}
		if (size >= BILLION) {
			return sign + PanelKit.compact(size);
		}
		if (size >= ONE_DECIMAL_MILLIONS) {
			return sign + PanelKit.decimals(size / MILLION, 1) + "M";
		}
		return sign + PanelKit.compact(size);
	}
}
