package dev.tim1337.lumio.skyblock.slayer;

import dev.tim1337.lumio.api.experimental.draw.PanelKit;

/**
 * What the label at a boss shows: which phase of how many, its kind (hits or damage), the value left and the bar. Plain numbers, so it is unit tested.
 *
 * @param step  the phase, from 1
 * @param steps how many phases the boss has
 * @param hits  whether the phase is one of hits rather than damage
 * @param value what is left, short
 * @param total what it started from, short
 * @param bar   how much is left, from 0 to 1
 */
record BossPhase(int step, int steps, boolean hits, String value, String total, float bar) {
	private static final int THIRDS = 3;

	/** @return the phase of a boss with hit shields */
	static BossPhase ofShields(VoidgloomPhases.View view, double health, double maxHealth) {
		if (view.hits()) {
			return new BossPhase(view.step(), view.steps(), true, String.valueOf(view.hitsLeft()), String.valueOf(view.hitsTotal()), clamp(view.bar()));
		}
		return new BossPhase(view.step(), view.steps(), false, PanelKit.compact(health), PanelKit.compact(maxHealth), clamp(view.bar()));
	}

	/** @return the phase of a boss without hit shields: three thirds of its health, or just the one */
	static BossPhase ofHealth(double health, double maxHealth, boolean thirds) {
		float left = maxHealth > 0 ? clamp((float) (health / maxHealth)) : 0f;
		int steps = thirds ? THIRDS : 1;
		int step = thirds ? Math.min(THIRDS, (int) Math.floor((1f - left) * THIRDS) + 1) : 1;
		return new BossPhase(step, steps, false, PanelKit.compact(health), PanelKit.compact(maxHealth), left);
	}

	/** @return the count, as "Phase 1/6" */
	String count() {
		return "Phase " + step + "/" + steps;
	}

	private static float clamp(float value) {
		return Math.max(0f, Math.min(1f, value));
	}
}
