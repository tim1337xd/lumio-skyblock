package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.motion.Easing;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;
import dev.tim1337.lumio.skyblock.data.DropMessage;
import dev.tim1337.lumio.skyblock.data.DropPriceIds;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockPrices;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * The drop toast: a note at the top of the screen when the server announces a valuable rare drop ("VERY RARE DROP! (Mana Steal I) (+437% ✯ Magic
 * Find)", read by {@link DropMessage}). The item in its rarity color, the magic find under it and the value on the right; every kind of drop looks
 * the same. It is as wide as its longest line, up to half the screen; beyond that the name wraps to a second line. It slides down with a soft spring, a
 * border in the rarity color turns in and one light sweeps across, the value counts up, and after about four seconds it floats up and fades. Drops
 * in a row are queued, one toast at a time. From what value on drops get one is chosen in {@link DropToastFeature}; with "Animations" off it shows still and
 * leaves at once. Only the server's message is read (through the chat guard), and the value is the item's known price; nothing is sent.
 */
public final class RareDropToast {
	/** The most drops waiting, and how long one may wait before it is no longer worth showing. */
	private static final int MOST_WAITING = 4;
	private static final long STALE_MILLIS = 20_000L;
	/** The toast's life: the light sweeps from when it has landed, the value counts up, and it leaves after its time. */
	private static final float SWEEP_START = 250f;
	private static final float SWEEP_END = 850f;
	private static final float COUNT_END = 1000f;
	private static final float STAY = 4000f;
	private static final float LEAVE = 450f;
	/** The toast's least width, its share of the screen at most, and its height with one line of name; a second name line adds a row. */
	private static final int LEAST_WIDTH = 150;
	private static final float MOST_SCREEN_SHARE = 0.5f;
	private static final int HEIGHT = 28;
	private static final int NAME_ROW = 9;
	/** The icon tile, the room between it and the text and between the text and the value, the gap below the info bar, and the motion. */
	private static final int TILE = 20;
	private static final float TEXT_GAP = 8f;
	private static final float VALUE_GAP = 8f;
	private static final int GAP = 6;
	private static final float SLIDE = 36f;
	private static final float FLOAT_UP = 14f;
	/** The size of the line under the name and of the value, and the room the line under the name takes. */
	private static final float DETAIL = 0.7f;
	private static final float DETAIL_ROW = 9.5f;
	private static final float VALUE = 1.15f;
	/** The rare drop pink, for a name without a color. */
	private static final int RARE_PINK = 0xFFFF55FF;
	/** The kind of an enchanted book, shown under its name. */
	private static final String BOOK_TYPE = "Enchanted book";

	/**
	 * A drop to show: the item, its rarity color, the line under the name, its value, and when it was announced.
	 */
	private record Drop(String item, int color, String detail, double value, long at) {
	}

	/** Recorded drop messages for the HUD editor's Simulate, shown one after the other, with a value each. */
	private static final List<String> SAMPLES = List.of(
		"§6§lVERY RARE DROP! §r§7(§9Mana Steal I§7) §b(+437% ✯ Magic Find)",
		"§9§lRARE DROP! §r§5Shard of the Shredded §r§b(+312% ✯ Magic Find)",
		"§d§lCRAZY RARE DROP! §r§62x Judgement Core",
		"§5§lPET DROP! §r§6Baby Yeti §b(+200% ✯ Magic Find)");
	private static final double[] SAMPLE_VALUES = {3_150_000, 2_400_000, 1_160_000_000, 41_000_000};
	private static final int[] SAMPLE_COLORS = {0xFF5555FF, 0xFFAA00AA, 0xFFFFAA00, 0xFFFFAA00};
	private static int nextSample;

	private static final Deque<Drop> WAITING = new ArrayDeque<>();
	private static Drop shown;
	private static long shownSince;
	private static final Spring SLIDE_IN = Spring.bouncy(Spring.DEFAULT);
	/** The toast's layout for the drop shown, worked out on its first frame: its size, the name's lines and the value's final width. */
	private static Drop laidOut;
	private static int laidWidth;
	private static int width;
	private static int height;
	private static String[] nameLines = new String[0];
	private static float valueWidth;

	private RareDropToast() {
	}

	/** Reads a server chat message: a rare drop worth a toast is queued. */
	public static void onMessage(Component message) {
		var toasts = SkyBlockFeatures.DROP_TOASTS;
		if (toasts == null || !toasts.isEnabled() || !SkyBlock.allowed()) {
			return;
		}
		DropMessage drop = DropMessage.parse(message.getString());
		if (drop == null) {
			return;
		}
		int color = colorOf(message, drop.name());
		receive(drop, priceOf(drop, color) * drop.amount(), color);
	}

	/** @return a drop's price by the first of its ids with a known one (a pet by its type and rarity, a book by its enchantment), or 0 */
	private static double priceOf(DropMessage drop, int color) {
		for (String id : DropPriceIds.ids(drop, color, SkyBlockPrices::idForName)) {
			double price = SkyBlockPrices.price(id);
			if (price > 0) {
				return price;
			}
		}
		return 0;
	}

	/** Queues a drop if the drop toasts want it: its value is known and at least the chosen amount. */
	public static void receive(DropMessage drop, double value, int color) {
		var toasts = SkyBlockFeatures.DROP_TOASTS;
		if (toasts != null && toasts.wants(value)) {
			queue(drop, value, color);
		}
	}

	/** Shows the next recorded drop, for the HUD editor's simulation of the moments; it shows whatever the chosen amount. */
	public static void simulate() {
		int index = nextSample;
		nextSample = (nextSample + 1) % SAMPLES.size();
		DropMessage drop = DropMessage.parse(SAMPLES.get(index));
		if (drop != null) {
			queue(drop, SAMPLE_VALUES[index], SAMPLE_COLORS[index]);
		}
	}

	private static void queue(DropMessage drop, double value, int color) {
		if (WAITING.size() >= MOST_WAITING) {
			WAITING.removeFirst();
		}
		String item = drop.amount() > 1 ? drop.name() + " x" + drop.amount() : drop.name();
		String magicFind = drop.magicFind() >= 0 ? "Magic find " + drop.magicFind() : "";
		String detail = drop.book() ? (magicFind.isEmpty() ? BOOK_TYPE : BOOK_TYPE + " · " + magicFind) : magicFind;
		WAITING.addLast(new Drop(item, color, detail, value, System.currentTimeMillis()));
	}

	/** @return the color of the item's name in the message: its rarity color, or the rare drop pink when it has none */
	private static int colorOf(Component message, String item) {
		String start = item.length() > 3 ? item.substring(0, 3) : item;
		Optional<TextColor> found = message.visit((Style style, String part) -> style.getColor() != null && part.contains(start)
			? Optional.of(style.getColor()) : Optional.empty(), Style.EMPTY);
		return found.map(color -> 0xFF000000 | color.getValue()).orElse(RARE_PINK);
	}

	/** Draws the toast in the HUD, centered under the info bar when it sits at the top. */
	public static void render(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		Minecraft client = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		if (shown == null || now - shownSince > STAY + LEAVE) {
			shown = null;
			// the next one that is still worth showing
			while (!WAITING.isEmpty() && shown == null) {
				Drop next = WAITING.removeFirst();
				if (now - next.at() <= STALE_MILLIS) {
					start(next, now);
				}
			}
		}
		if (shown == null || client.level == null) {
			return;
		}
		boolean animated = MainSettings.animations();
		float millis = now - shownSince;
		if (!animated && millis > STAY) {
			shown = null;
			return;
		}
		Canvas ui = Draw.hudCanvas(graphics, shown.color(), 0f, false);
		layOut(ui, graphics.guiWidth());
		float left = Math.round((graphics.guiWidth() - width) / 2f);
		float top = top(graphics);
		float slide = animated ? SLIDE_IN.follow(1f) : 1f;
		float leave = animated ? PanelMoment.stage(millis, STAY, STAY + LEAVE) : 0f;
		float alpha = Math.min(1f, Math.max(0f, slide)) * (1f - leave);
		var pose = graphics.pose();
		pose.pushMatrix();
		float before = Draw.alpha();
		try {
			graphics.nextStratum();
			pose.translate(left, top - SLIDE * (1f - slide) - FLOAT_UP * leave);
			Draw.setAlpha(before * alpha);
			draw(ui, animated ? millis : COUNT_END);
		} finally {
			Draw.setAlpha(before);
			pose.popMatrix();
		}
	}

	private static void start(Drop drop, long now) {
		shown = drop;
		shownSince = now;
		SLIDE_IN.snap(0f);
		var toasts = SkyBlockFeatures.DROP_TOASTS;
		if (toasts != null) {
			toasts.playSound();
		}
	}

	/**
	 * Works out the toast's size for the drop shown (once per drop and screen width): as wide as its longest line plus the value, between its least
	 * width and half the screen. A name too long for that wraps to a second line between words; the value always keeps its full room.
	 */
	private static void layOut(Canvas ui, int screenWidth) {
		if (laidOut == shown && laidWidth == screenWidth) {
			return;
		}
		laidOut = shown;
		laidWidth = screenWidth;
		valueWidth = shown.value() > 0 ? ui.boldWidthScaled(valueText(shown.value()), VALUE) : 0f;
		float textLeft = PanelKit.PAD + TILE + TEXT_GAP;
		float valueRoom = valueWidth > 0 ? VALUE_GAP + valueWidth : 0f;
		float longest = Math.max(ui.boldWidthScaled(shown.item(), PanelKit.META), ui.widthScaled(shown.detail(), DETAIL));
		int most = Math.max(LEAST_WIDTH, Math.round(screenWidth * MOST_SCREEN_SHARE));
		width = Math.clamp(Math.round(textLeft + longest + valueRoom + PanelKit.PAD + 1f), LEAST_WIDTH, most);
		float room = width - textLeft - valueRoom - PanelKit.PAD;
		nameLines = wrap(ui, shown.item(), room);
		height = HEIGHT + (nameLines.length - 1) * NAME_ROW;
	}

	/** @return the name in one line, or two lines broken between words when it is too long; only a single word longer than a line is shortened */
	private static String[] wrap(Canvas ui, String name, float room) {
		if (ui.boldWidthScaled(name, PanelKit.META) <= room) {
			return new String[]{name};
		}
		String[] words = name.split(" ");
		StringBuilder first = new StringBuilder();
		int used = 0;
		while (used < words.length) {
			String next = first.isEmpty() ? words[used] : first + " " + words[used];
			if (!first.isEmpty() && ui.boldWidthScaled(next, PanelKit.META) > room) {
				break;
			}
			first.setLength(0);
			first.append(next);
			used++;
		}
		String second = String.join(" ", Arrays.copyOfRange(words, used, words.length));
		int roomUnits = Math.round(room / PanelKit.META);
		String firstLine = ui.boldWidthScaled(first.toString(), PanelKit.META) > room ? ui.fit(first.toString(), roomUnits) : first.toString();
		if (second.isEmpty()) {
			return new String[]{firstLine};
		}
		return new String[]{firstLine, ui.boldWidthScaled(second, PanelKit.META) > room ? ui.fit(second, roomUnits) : second};
	}

	private static String valueText(double value) {
		return "+" + PanelKit.compact(value);
	}

	/** @return the toast's top: under the info bar when it is at the top of the screen, otherwise near the top */
	private static float top(GuiGraphicsExtractor graphics) {
		int[] box = MainSettings.infoBarBounds();
		if (box != null) {
			if (box[1] < graphics.guiHeight() / 4) {
				return box[1] + box[3] + GAP;
			}
		}
		return GAP;
	}

	private static void draw(Canvas ui, float millis) {
		Drop drop = shown;
		int color = drop.color();
		HudTheme.panel(ui, 0, 0, width, height, PanelKit.RADIUS, HudTheme.opacity());
		// the border in the rarity color: it turns in as the toast lands, then stays as a soft frame
		float border = PanelMoment.stage(millis, 0f, SWEEP_START);
		ui.ring(0, 0, width, height, PanelKit.RADIUS, 1, Draw.withOpacity(color, 0.35f + 0.5f * border));
		// one light sweeping across
		if (millis > SWEEP_START && millis < SWEEP_END) {
			float sweep = PanelMoment.stage(millis, SWEEP_START, SWEEP_END);
			ui.softLight(-20f + (width + 40f) * sweep, height / 2f, 26f, height, Draw.withOpacity(0xFFFFFFFF, 0.16f * (float) Math.sin(sweep * Math.PI)));
		}
		float tileY = (height - TILE) / 2f;
		ui.roundedSmooth(PanelKit.PAD, tileY, TILE, TILE, Radius.SM, Draw.withOpacity(color, 0.22f));
		ui.ring(PanelKit.PAD, Math.round(tileY), TILE, TILE, Radius.SM, 1, Draw.withOpacity(color, 0.45f));
		ui.icon("gem", PanelKit.PAD + 5, Math.round(tileY) + 5, TILE - 10, color);
		float x = PanelKit.PAD + TILE + TEXT_GAP;
		// the name and the line under it, together in the middle of the toast
		boolean detail = !drop.detail().isEmpty();
		float block = nameLines.length * NAME_ROW + (detail ? DETAIL_ROW : 0f);
		float textTop = (height - block) / 2f + 1f;
		for (int i = 0; i < nameLines.length; i++) {
			ui.textAtScaledBold(nameLines[i], x, textTop + i * NAME_ROW, PanelKit.META, PanelKit.readable(color));
		}
		if (detail) {
			ui.textAtScaled(drop.detail(), x, textTop + nameLines.length * NAME_ROW + 1f, DETAIL, HudTheme.secondary());
		}
		if (drop.value() > 0) {
			// the value counts up from zero; its place is kept for the full value, so it never moves or gets cut
			String value = valueText(drop.value() * Easing.easeOut(PanelMoment.stage(millis, SWEEP_START, COUNT_END)));
			float shownWidth = ui.boldWidthScaled(value, VALUE);
			PanelKit.hero(ui, value, width - PanelKit.PAD - shownWidth, height / 2f - 4.5f, VALUE, PanelKit.GOLD, false);
		}
	}
}
