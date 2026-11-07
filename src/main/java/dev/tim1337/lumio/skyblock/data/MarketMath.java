package dev.tim1337.lumio.skyblock.data;

import java.util.Arrays;

/**
 * Numbers from price history, as Coflnet gives it: an average price and a sale count per stretch of time. Its only real median covers two days, so the median of any
 * other span is an estimate from those averages. Plain numbers, so it is unit tested.
 */
public final class MarketMath {
	/** A trend compares the first and the last of this many equal parts of the span: a week's first day with its last. */
	private static final int TREND_PARTS = 7;
	private static final double PERCENT = 100d;

	private MarketMath() {
	}

	/** @return the middle price by sales: half of what was sold went below it. NaN without sales */
	public static double weightedMedian(double[] prices, double[] sales) {
		int count = Math.min(prices.length, sales.length);
		Integer[] order = new Integer[count];
		double total = 0d;
		for (int i = 0; i < count; i++) {
			order[i] = i;
			if (usable(prices[i], sales[i])) {
				total += sales[i];
			}
		}
		if (total <= 0d) {
			return Double.NaN;
		}
		Arrays.sort(order, (a, b) -> Double.compare(prices[a], prices[b]));
		double half = total / 2d;
		double passed = 0d;
		for (Integer index : order) {
			if (usable(prices[index], sales[index])) {
				passed += sales[index];
				if (passed >= half) {
					return prices[index];
				}
			}
		}
		return Double.NaN;
	}

	/** @return how many sold per day over the days the history spans; NaN without days */
	public static double perDay(double[] sales, int days) {
		if (days <= 0 || sales.length == 0) {
			return Double.NaN;
		}
		double total = 0d;
		for (double sale : sales) {
			if (sale > 0d && !Double.isInfinite(sale)) {
				total += sale;
			}
		}
		return total / days;
	}

	/**
	 * @param prices the prices, oldest first
	 * @param sales  what each price weighs; null for all the same, as a bazaar's prices
	 * @return the change in percent from the first part of the span to the last; NaN with too little history
	 */
	public static double trendPercent(double[] prices, double[] sales) {
		int part = prices.length / TREND_PARTS;
		if (part < 1) {
			return Double.NaN;
		}
		double first = mean(prices, sales, 0, part);
		double last = mean(prices, sales, prices.length - part, prices.length);
		if (Double.isNaN(first) || Double.isNaN(last) || first <= 0d) {
			return Double.NaN;
		}
		return (last - first) / first * PERCENT;
	}

	private static double mean(double[] prices, double[] sales, int from, int to) {
		double sum = 0d;
		double weight = 0d;
		for (int i = from; i < to; i++) {
			double each = sales == null ? 1d : sales[i];
			if (usable(prices[i], each)) {
				sum += prices[i] * each;
				weight += each;
			}
		}
		return weight <= 0d ? Double.NaN : sum / weight;
	}

	private static boolean usable(double price, double sales) {
		return price > 0d && sales > 0d && !Double.isInfinite(price) && !Double.isInfinite(sales);
	}
}
