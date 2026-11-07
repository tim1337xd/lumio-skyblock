package dev.tim1337.lumio.skyblock.profit;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.skyblock.data.ProfitHistory;

import net.minecraft.util.Mth;

/**
 * The profit panel's chart: the total over the time spent as a gold line with a soft area under it, a dot at its end, and how long
 * ago it starts. The line's height at each column is worked out only when the history or the width changes.
 */
final class ProfitChart {
	/** The chart's height and the time line under it. */
	static final int HEIGHT = 54;
	static final int AXIS = 9;
	private static final int MOST_COLUMNS = 512;
	/** Room kept at the top and bottom, so the line's end and its dot are not cut. */
	private static final float EDGE = 3f;

	private final float[] columns = new float[MOST_COLUMNS];
	private ProfitHistory drawnFor;
	private int version = -1;
	private int width = -1;
	private String ago = "";

	/** Draws the chart of a history at a place, as wide as given. */
	void draw(Canvas ui, float left, float top, int wanted, ProfitHistory history) {
		int chartWidth = Math.min(MOST_COLUMNS, wanted);
		if (history.count() < 2 || chartWidth < 2) {
			return;
		}
		if (history != drawnFor || history.version() != version || chartWidth != width) {
			drawnFor = history;
			version = history.version();
			width = chartWidth;
			columns(history, chartWidth);
		}
		int gold = PanelKit.readable(PanelKit.GOLD);
		int area = Draw.withOpacity(gold, 0.14f);
		float bottom = top + HEIGHT;
		for (int x = 0; x < chartWidth; x++) {
			float lineY = top + columns[x];
			ui.fill(Math.round(left + x), Math.round(lineY), 1, Math.max(0, Math.round(bottom - lineY)), area);
			// the line: from this column's height to the next one's, at least a pixel and a half thick
			float next = x + 1 < chartWidth ? top + columns[x + 1] : lineY;
			float from = Math.min(lineY, next);
			float to = Math.max(lineY, next);
			ui.roundedSmooth(left + x, from - 0.75f, 1.2f, Math.max(1.5f, to - from + 1.5f), 0.4f, gold);
		}
		float endX = left + chartWidth - 1f;
		float endY = top + columns[chartWidth - 1];
		ui.softLight(endX, endY, 6f, Draw.withOpacity(gold, 0.35f));
		ui.discAt(endX, endY, 3.5f, Draw.mix(gold, 0xFFFFFFFF, 0.4f));
		int quiet = Draw.withOpacity(HudTheme.secondary(), 0.75f);
		ui.textAtScaled(ago, left, bottom + 2.5f, PanelKit.SMALL, quiet);
		float nowWidth = ui.widthScaled("now", PanelKit.SMALL);
		ui.textAtScaled("now", left + chartWidth - nowWidth, bottom + 2.5f, PanelKit.SMALL, quiet);
	}

	/** Works out the line's height at each column from the samples, from the lowest total (or zero) to the highest. */
	private void columns(ProfitHistory history, int chartWidth) {
		int count = history.count();
		long first = history.time(0);
		long last = Math.max(first + 1, history.time(count - 1));
		// scaled to the range of the data, so growth shows also when the session started high (a total carried over, a cost first)
		double low = Double.MAX_VALUE;
		double high = -Double.MAX_VALUE;
		for (int i = 0; i < count; i++) {
			low = Math.min(low, history.total(i));
			high = Math.max(high, history.total(i));
		}
		double span = Math.max(1, high - low);
		int sample = 0;
		for (int x = 0; x < chartWidth; x++) {
			double at = first + (last - first) * (double) x / (chartWidth - 1);
			while (sample < count - 2 && history.time(sample + 1) < at) {
				sample++;
			}
			int next = Math.min(count - 1, sample + 1);
			long t0 = history.time(sample);
			long t1 = history.time(next);
			double share = t1 <= t0 ? 0 : Mth.clamp((at - t0) / (t1 - t0), 0, 1);
			double value = history.total(sample) + (history.total(next) - history.total(sample)) * share;
			columns[x] = (float) (HEIGHT - EDGE - (value - low) / span * (HEIGHT - 2 * EDGE));
		}
		long minutes = Math.max(1, Math.round((last - first) / 60_000.0));
		ago = minutes < 60 ? minutes + " min ago" : minutes % 60 == 0 ? minutes / 60 + " h ago" : minutes / 60 + " h " + minutes % 60 + " min ago";
	}
}
