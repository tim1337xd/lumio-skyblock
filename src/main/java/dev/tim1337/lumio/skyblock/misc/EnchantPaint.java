package dev.tim1337.lumio.skyblock.misc;

/** The moving colors of an enchant's letters: a calm rainbow, or the player's own colors flowing through them. Plain numbers, so it is unit tested. */
final class EnchantPaint {
	private static final float CHROMA_TURNS_PER_SECOND = 0.12f;
	private static final float CHROMA_PER_LETTER = 0.035f;
	private static final float CHROMA_SATURATION = 0.65f;
	private static final float FLOW_PER_LETTER = 0.06f;
	private static final float FLOW_TURNS_PER_SPEED = 0.25f;
	private static final int HUE_PARTS = 6;
	/** The contrast every default color keeps, as for text in the accessibility guidelines. */
	static final double LEAST_CONTRAST = 4.5;
	/** The lightest that can lie behind a tooltip's letters: the settings' Dark panel over a white world, lighter than the tooltip's own frame over one. */
	static final int LIGHTEST_BACKGROUND = 0x333334;
	/** How far apart the rainbow's colors are worked out, and how far a color at a time moves to white until it reads. */
	private static final int CHROMA_STEPS = 720;
	private static final float TO_WHITE_STEP = 0.005f;
	/** The rainbow, worked out once: its letters are asked for every frame. Its dark part, around blue, is lifted just as far as it needs to read; the rest is as it was. */
	private static final int[] CHROMA = chromaColors();

	private EnchantPaint() {
	}

	/** @return the color of a letter as 0xRRGGBB at a time */
	static int chroma(float seconds, int letter) {
		return CHROMA[(int) (fraction(seconds * CHROMA_TURNS_PER_SECOND - letter * CHROMA_PER_LETTER) * CHROMA_STEPS) % CHROMA_STEPS];
	}

	/** @return how far apart two colors are for reading, from 1 to 21, as in the accessibility guidelines */
	static double contrast(int first, int second) {
		double one = luminance(first);
		double other = luminance(second);
		return (Math.max(one, other) + 0.05) / (Math.min(one, other) + 0.05);
	}

	private static double luminance(int rgb) {
		return 0.2126 * linear(rgb >> 16 & 0xFF) + 0.7152 * linear(rgb >> 8 & 0xFF) + 0.0722 * linear(rgb & 0xFF);
	}

	private static double linear(int channel) {
		double value = channel / 255.0;
		return value <= 0.03928 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
	}

	private static int[] chromaColors() {
		int[] colors = new int[CHROMA_STEPS];
		for (int i = 0; i < CHROMA_STEPS; i++) {
			colors[i] = readable(hue((float) i / CHROMA_STEPS));
		}
		return colors;
	}

	/** @return a color moved toward white, which keeps its hue, as far as it takes to read */
	static int readable(int rgb) {
		int color = rgb;
		for (float share = TO_WHITE_STEP; contrast(color, LIGHTEST_BACKGROUND) < LEAST_CONTRAST && share < 1f; share += TO_WHITE_STEP) {
			color = mix(rgb, 0xFFFFFF, share);
		}
		return color;
	}

	/**
	 * @param colors the colors, as 0xRRGGBB
	 * @param count  how many of them are used
	 * @param speed  how fast they flow
	 * @return the color of a letter as 0xRRGGBB at a time
	 */
	static int flow(int[] colors, int count, float speed, float seconds, int letter) {
		float place = fraction(seconds * speed * FLOW_TURNS_PER_SPEED - letter * FLOW_PER_LETTER) * count;
		int from = (int) place % count;
		int to = (from + 1) % count;
		return mix(colors[from], colors[to], place - (int) place);
	}

	private static float fraction(float value) {
		return value - (float) Math.floor(value);
	}

	private static int hue(float hue) {
		float scaled = hue * HUE_PARTS;
		int part = (int) scaled % HUE_PARTS;
		float rest = scaled - (int) scaled;
		float low = 1f - CHROMA_SATURATION;
		float falling = 1f - CHROMA_SATURATION * rest;
		float rising = 1f - CHROMA_SATURATION * (1f - rest);
		return switch (part) {
			case 0 -> rgb(1f, rising, low);
			case 1 -> rgb(falling, 1f, low);
			case 2 -> rgb(low, 1f, rising);
			case 3 -> rgb(low, falling, 1f);
			case 4 -> rgb(rising, low, 1f);
			default -> rgb(1f, low, falling);
		};
	}

	private static int rgb(float red, float green, float blue) {
		return Math.round(red * 255f) << 16 | Math.round(green * 255f) << 8 | Math.round(blue * 255f);
	}

	private static int mix(int from, int to, float share) {
		return channel(from >> 16 & 0xFF, to >> 16 & 0xFF, share) << 16 | channel(from >> 8 & 0xFF, to >> 8 & 0xFF, share) << 8 | channel(from & 0xFF, to & 0xFF, share);
	}

	private static int channel(int from, int to, float share) {
		return Math.round(from + (to - from) * share);
	}
}
