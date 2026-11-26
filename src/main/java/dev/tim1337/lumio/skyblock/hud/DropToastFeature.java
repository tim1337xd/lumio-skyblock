package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.MultiToggle;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;


import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * The drop toasts: a short note at the top of the screen when a valuable rare drop is announced ({@link RareDropToast} draws it). Every kind of
 * drop counts the same; only its value decides, from the amount chosen here on. Drops below it are still counted by the profit trackers as
 * before; only the toast is left out.
 */
public class DropToastFeature extends AddonFeature {
	/** The least value of a drop that gets a toast: the slider's range, its default, and the values its knob rests on. */
	private static final float LEAST_COINS = 100_000f;
	private static final float MOST_COINS = 100_000_000f;
	private static final float DEFAULT_COINS = 1_000_000f;
	private static final float[] SNAP_COINS = {500_000f, 1_000_000f, 5_000_000f, 10_000_000f, 50_000_000f};
	private static final String SHOW_FROM_KEY = "show_from";

	private final Slider showFrom = add(make().slider(SHOW_FROM_KEY, Component.literal("Show from"), DEFAULT_COINS, LEAST_COINS, MOST_COINS, 0)
		.logarithmic().snapTo(SNAP_COINS).coins(), "Drops");
	/** How a drop shows: the pop-up as before, the game's title, a chat line, or several of them. */
	private final ShowAs showAs = new ShowAs("show_as", true, false, false);
	private final MultiToggle showAsSetting = add(showAs.setting(), "Drops");
	private final Slider titleFrom = add(make().slider("title_from", Component.literal("Title from"), DEFAULT_COINS, LEAST_COINS, MOST_COINS, 0)
		.logarithmic().snapTo(SNAP_COINS).coins(), "Drops", "Cheaper drops: pop-up or chat only");
	private final Slider titleSeconds = add(make().slider("title_seconds", Component.literal("Duration"), 1.5f, 1f, 5f, 1).unit(" s"), "Drops");
	private final Toggle sound = add(make().toggle("sound", Component.literal("Sound"), true), "Sound");
	private final Slider volume = add(make().slider("volume", Component.literal("Volume"), 0.6f, 0.05f, 1f, 2).percent(), "Sound");

	{
		volume.under(sound);
		titleFrom.visibleWhen(showAs::title);
		titleSeconds.visibleWhen(showAs::title);
	}

	private static final float MILLIS_PER_SECOND = 1000f;

	/** @return how a drop of this value shows: a title only from "Title from", and never nowhere */
	public ShowAs.Chosen display(double value) {
		return showAs.chosen().forValue(value, titleFrom.get());
	}

	/** @return how long a drop's title shows */
	public long titleMillis() {
		return Math.round(titleSeconds.get() * MILLIS_PER_SECOND);
	}

	/** @return the line under a drop's title: its value, then what the pop-up says under the name (the magic find) */
	static String titleDetail(String value, String detail) {
		return detail.isEmpty() ? value : value + " · " + detail;
	}

	public DropToastFeature() {
		super("drop_toasts", "Drop alerts", "A note when you get a valuable drop.", true);
		pageGroup("Helpers");
		searchWords("toast toasts drop toasts");
		iconRgb(SystemColors.PURPLE_RGB);
		iconName("gem");
		summary("A note when you get a valuable drop");
	}

	/** @return whether a drop of this value gets a toast */
	public boolean wants(double value) {
		return isEnabled() && worthAToast(value, showFrom.get());
	}

	/**
	 * @return whether a drop of this value gets a toast with this "Show from": only a known value (above 0) of at least that much. A drop without
	 *         a known price never gets one, so the toasts stay the valuable drops; the profit trackers count it as before.
	 */
	static boolean worthAToast(double value, double showFrom) {
		return value > 0 && value >= showFrom;
	}

	/** Plays the toast's sound, if chosen. */
	public void playSound() {
		if (sound.get()) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1f, volume.get()));
		}
	}
}
