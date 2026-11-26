package dev.tim1337.lumio.skyblock.mining;

import dev.tim1337.lumio.skyblock.mining.PowderTab.Powder;

/** The powder that fits a place: what the panel shows by itself before any powder was gained. Plain text, so it is unit tested. */
final class PowderPlace {
	private PowderPlace() {
	}

	/**
	 * @param island the island's name, as the tab list gives it
	 * @param zone   the zone on it, as the scoreboard gives it
	 * @param other  the powder for a place that has none of its own
	 * @return Gemstone in the Crystal Hollows, Glacite in the Glacite Tunnels and the Mineshafts, Mithril elsewhere in the Dwarven Mines
	 */
	static Powder of(String island, String zone, Powder other) {
		if (island == null) {
			return other;
		}
		if (island.equals("Crystal Hollows")) {
			return Powder.GEMSTONE;
		}
		if (island.equals("Mineshaft") || island.contains("Glacite")) {
			return Powder.GLACITE;
		}
		if (island.equals("Dwarven Mines")) {
			return zone != null && (zone.contains("Glacite") || zone.contains("Base Camp") || zone.contains("Fossil Research")) ? Powder.GLACITE : Powder.MITHRIL;
		}
		return other;
	}
}
