package dev.tim1337.lumio.skyblock.mining;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.tim1337.lumio.skyblock.mining.PowderTab.Powder;

import org.junit.jupiter.api.Test;

/** The powder that fits a place, shown before any powder was gained. */
class PowderPlaceTest {
	@Test
	void everyMiningPlaceHasItsPowder() {
		assertEquals(Powder.GEMSTONE, PowderPlace.of("Crystal Hollows", "Precursor Remnants", Powder.MITHRIL));
		assertEquals(Powder.MITHRIL, PowderPlace.of("Dwarven Mines", "The Forge", Powder.GEMSTONE));
		assertEquals(Powder.GLACITE, PowderPlace.of("Dwarven Mines", "Glacite Tunnels", Powder.MITHRIL));
		assertEquals(Powder.GLACITE, PowderPlace.of("Dwarven Mines", "Dwarven Base Camp", Powder.MITHRIL));
		assertEquals(Powder.GLACITE, PowderPlace.of("Mineshaft", "Glacite Mineshafts", Powder.MITHRIL));
	}

	@Test
	void elsewhereThePowderOfBeforeStays() {
		assertEquals(Powder.GEMSTONE, PowderPlace.of("Hub", "Village", Powder.GEMSTONE));
		assertEquals(Powder.GLACITE, PowderPlace.of(null, null, Powder.GLACITE));
	}
}
