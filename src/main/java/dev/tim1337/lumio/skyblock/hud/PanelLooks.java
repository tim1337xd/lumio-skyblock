package dev.tim1337.lumio.skyblock.hud;

import net.minecraft.network.chat.Component;

/**
 * The looks of the SkyBlock panels that have more than one (a rich mode and a simple one), chosen with picture tiles on their settings cards
 * and in their header in the HUD editor. Their saved names never change: the settings keep them.
 */
public final class PanelLooks {
	private PanelLooks() {
	}

	/** The slayer panel: the boss card, or one simple row. */
	public enum Slayer {
		CARD("Card"),
		SIMPLE("Simple");

		private final Component displayName;

		Slayer(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** The abilities panel: a ring per ability, or one simple row each. */
	public enum Abilities {
		RINGS("Rings"),
		SIMPLE("Simple");

		private final Component displayName;

		Abilities(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** The deployable panel: a ring with the deployable's buffs, or one simple row. */
	public enum Deployable {
		RING("Ring"),
		SIMPLE("Simple");

		private final Component displayName;

		Deployable(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** The profit panel: the total with its chart and top drops, or the share of each kind of drop as a ring. */
	public enum Profit {
		BREAKDOWN("Full"),
		CHART("Compact");

		private final Component displayName;

		Profit(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** The pest panel: the Garden's plot map, a ring with the next pest's time, or simple rows. */
	public enum Pests {
		MAP("Map"),
		RING("Ring"),
		SIMPLE("Simple");

		private final Component displayName;

		Pests(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}
}
