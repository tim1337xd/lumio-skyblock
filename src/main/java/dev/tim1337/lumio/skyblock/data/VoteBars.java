package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.TabCard;
import dev.tim1337.lumio.api.experimental.util.ServerText;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The election's votes in the player list come as text blocks ("Diana ||||||||| 34%"): this reads them as a row with a real bar in the candidate's color, and the percent. */
final class VoteBars {
	/** How long a run of bars has to be. */
	private static final int LEAST_BARS = 3;
	private static final char BAR = '|';
	private static final Pattern PERCENT = Pattern.compile("(\\d{1,3}(?:\\.\\d{1,2})?)%");
	private static final float HUNDRED = 100f;

	private VoteBars() {
	}

	/** @return where the run of bars in a line starts, or -1 */
	static int barsStart(String text) {
		int first = text.indexOf(BAR);
		if (first < 0) {
			return -1;
		}
		int at = first;
		while (at < text.length() && (text.charAt(at) == BAR || text.charAt(at) == '§' || at > first && text.charAt(at - 1) == '§')) {
			at++;
		}
		int bars = 0;
		for (int i = first; i < at; i++) {
			if (text.charAt(i) == BAR) {
				bars++;
			}
		}
		return bars >= LEAST_BARS ? first : -1;
	}

	/** @return the share of the votes: the percent in the text, otherwise how many bars have the first color */
	static float share(String afterBars, int colored, int bars) {
		Matcher percent = PERCENT.matcher(afterBars);
		if (percent.find()) {
			return Math.clamp(Float.parseFloat(percent.group(1)) / HUNDRED, 0f, 1f);
		}
		return bars <= 0 ? 0f : Math.clamp(colored / (float) bars, 0f, 1f);
	}

	/** @return a line with a run of bars as a row with a bar, or null for any other line */
	static TabCard.Row row(Component line, String raw) {
		int start = barsStart(raw);
		if (start <= 0) {
			return null;
		}
		int[] counts = new int[2];
		int[] color = {-1};
		line.visit((Style style, String part) -> {
			for (int i = 0; i < part.length(); i++) {
				if (part.charAt(i) == BAR) {
					TextColor shown = style.getColor();
					int rgb = shown == null ? -1 : shown.getValue();
					if (color[0] < 0) {
						color[0] = rgb;
					}
					counts[0]++;
					if (rgb == color[0]) {
						counts[1]++;
					}
				}
			}
			return Optional.empty();
		}, Style.EMPTY);
		float share = share(raw.substring(start), counts[1], counts[0]);
		String label = raw.substring(0, start).strip();
		int labelEnd = label.endsWith(":") ? raw.substring(0, start).lastIndexOf(':') : start;
		Component name = ServerText.slice(line, raw.length() - raw.stripLeading().length(), labelEnd);
		String shownPercent = Math.round(share * HUNDRED) + "%";
		int barColor = color[0] >= 0 ? color[0] : -1;
		Component value = barColor >= 0 ? Component.literal(shownPercent).withStyle(style -> style.withColor(barColor)) : Component.literal(shownPercent);
		return new TabCard.Row(name, value, share, barColor);
	}
}
