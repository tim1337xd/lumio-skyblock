package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.api.HudSimulator;
import dev.tim1337.lumio.api.Simulation;
import dev.tim1337.lumio.api.experimental.hud.HudEditor;
import dev.tim1337.lumio.api.feature.FeatureView;
import dev.tim1337.lumio.skyblock.data.Activity;
import dev.tim1337.lumio.skyblock.hud.RareDropToast;

import java.util.ArrayList;
import java.util.List;

/**
 * The SkyBlock activities the HUD editor can simulate (one entry for all slayers), and the moments of the SkyBlock panels. The panels read the
 * simulated activity here to show their sample content; it only exists while the editor is open.
 */
public final class SkyBlockSimulation implements HudSimulator {
	static final SkyBlockSimulation INSTANCE = new SkyBlockSimulation();
	private static final List<String> MOMENTS = List.of("Rare drop", "Pet level up", "Ability ready", "Slayer boss");

	private static Activity activity;
	private static int option;
	private final List<Simulation> simulations;

	private SkyBlockSimulation() {
		List<Simulation> found = new ArrayList<>();
		for (Activity candidate : Activity.values()) {
			if (candidate.simulatable()) {
				found.add(new Simulation(candidate.name(), candidate.displayName(), candidate.group(), candidate.options()));
			}
		}
		simulations = List.copyOf(found);
	}

	/** Starts simulating an activity in the HUD editor, as the Simulate menu does (also for the game tests). */
	public static void begin(Activity simulated, int chosen) {
		HudEditor.simulate(INSTANCE, simulated.name(), chosen);
	}

	/** @return whether an activity is simulated right now, which is only while the editor is open */
	public static boolean active() {
		return activity != null && HudEditor.isOpen();
	}

	/** @return the simulated activity, also while the editor is closed, or null */
	public static Activity activity() {
		return activity;
	}

	public static int option() {
		return option;
	}

	@Override
	public List<Simulation> simulations() {
		return simulations;
	}

	@Override
	public List<String> moments() {
		return MOMENTS;
	}

	@Override
	public void playMoment(int index) {
		switch (index) {
			case 0 -> RareDropToast.simulate();
			case 1 -> SkyBlockFeatures.PET.playLevelUp();
			case 2 -> SkyBlockFeatures.MINING_ABILITY.playReady();
			case 3 -> SkyBlockFeatures.SLAYER_PHASE.playSpawn();
			default -> {
			}
		}
	}

	@Override
	public void start(String id, int chosen) {
		activity = Activity.valueOf(id);
		option = chosen;
	}

	@Override
	public void stop() {
		activity = null;
		option = 0;
	}

	@Override
	public boolean shows(FeatureView feature) {
		return Activity.belongs(feature, activity);
	}

	@Override
	public boolean simulates(FeatureView feature) {
		return Activity.belongs(feature, activity) && !Activity.of(feature).contains(Activity.ALWAYS);
	}
}
