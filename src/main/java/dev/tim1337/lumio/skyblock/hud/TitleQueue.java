package dev.tim1337.lumio.skyblock.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * The one place Lumio's on-screen titles go through (dungeon alerts, fishing alerts, drop alerts, timers), drawn by the game's own title
 * rendering. One title at a time, in the order of {@link TitleOrder}: a title never takes the place of another of ours that still shows,
 * except an urgent one. A title the server sends is the game's own and shows as the game decides.
 */
public final class TitleQueue {
	private static final int MILLIS_PER_TICK = 50;
	private static final int FADE_TICKS = 3;

	/** A title as the game shows it: the two lines and the fades around its stay. */
	private record Title(Component title, Component subtitle, int fadeIn, int fadeOut) {
	}

	private static final TitleOrder<Title> ORDER = new TitleOrder<>();

	private TitleQueue() {
	}

	/** A title with the usual short fades, for this long in all. */
	public static void show(Component title, Component subtitle, long millis, boolean urgent) {
		show(title, subtitle, FADE_TICKS, Math.max(1, Math.round(millis / (float) MILLIS_PER_TICK) - 2 * FADE_TICKS), FADE_TICKS, urgent);
	}

	/**
	 * A title with its own times, in ticks.
	 *
	 * @param urgent whether it is about this very moment and shows at once; otherwise it waits while another of Lumio's titles shows
	 */
	public static void show(Component title, Component subtitle, int fadeIn, int stay, int fadeOut, boolean urgent) {
		long millis = (long) (fadeIn + stay + fadeOut) * MILLIS_PER_TICK;
		put(ORDER.offer(new Title(title, subtitle, fadeIn, fadeOut), millis, urgent, System.currentTimeMillis()));
	}

	/** Once per client tick: the next waiting title, when its turn has come. */
	public static void tick(Minecraft client) {
		if (client.level == null) {
			ORDER.clear();
			return;
		}
		if (ORDER.waiting() > 0) {
			put(ORDER.next(System.currentTimeMillis()));
		}
	}

	private static void put(TitleOrder.Showing<Title> showing) {
		if (showing == null) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		Title title = showing.title();
		int stay = Math.max(1, Math.round(showing.millis() / (float) MILLIS_PER_TICK) - title.fadeIn() - title.fadeOut());
		client.gui.hud.setTimes(title.fadeIn(), stay, title.fadeOut());
		client.gui.hud.setTitle(title.title());
		client.gui.hud.setSubtitle(title.subtitle());
	}
}
