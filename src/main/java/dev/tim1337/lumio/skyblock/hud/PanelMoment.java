package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;

/**
 * A short moment on a SkyBlock panel, such as a pet leveling up or an ability getting ready: it runs once over a fixed time on real time, and a
 * new one replaces the one running, so moments never stack up. With "Celebrations" off it never starts; with "Animations" off it is over at
 * once, so the panel shows its end state right away.
 */
public final class PanelMoment {
	private final long lengthNanos;
	private long startNanos;
	private boolean running;

	/** @param lengthMillis how long the moment lasts */
	public PanelMoment(long lengthMillis) {
		this.lengthNanos = lengthMillis * 1_000_000L;
	}

	/** @return whether the panels show their moments */
	public static boolean wanted() {
		return SkyBlockFeatures.CELEBRATIONS.isEnabled();
	}

	/** Starts the moment, replacing one still running. */
	public void start() {
		if (!wanted() || !MainSettings.animations()) {
			running = false;
			return;
		}
		startNanos = System.nanoTime();
		running = true;
	}

	/** Ends the moment at once. */
	public void stop() {
		running = false;
	}

	/** @return how long the moment has run, in milliseconds, or -1 when it is not running (never started, or over) */
	public float millis() {
		if (!running) {
			return -1f;
		}
		long elapsed = System.nanoTime() - startNanos;
		if (elapsed >= lengthNanos) {
			running = false;
			return -1f;
		}
		return elapsed / 1_000_000f;
	}

	/** @return how far a stage of the moment has come, from 0 before it to 1 after it */
	public static float stage(float millis, float from, float to) {
		if (millis < 0f) {
			return 1f;
		}
		return Math.max(0f, Math.min(1f, (millis - from) / (to - from)));
	}
}
