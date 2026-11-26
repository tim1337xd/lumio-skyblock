package dev.tim1337.lumio.skyblock.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;
import dev.tim1337.lumio.skyblock.fishing.SeaCreatures.Creature;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The fishing alerts' module: its list as shipped, and that a catch is shared only in a party and only with the switch on. */
class FishingAlertsShareTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ModFeatures.createAll();
	}

	@Test
	void theListAsShippedIsTheRareOnes() {
		FishingAlertsFeature alerts = SkyBlockFeatures.FISHING_ALERTS;
		assertFalse(alerts.isEnabled(), "off by default");
		for (Creature creature : SeaCreatures.all()) {
			assertEquals(SeaCreatures.rare(creature), alerts.listed(creature), creature.name());
		}
	}

	@Test
	void aCatchIsSharedOnlyInAPartyAndOnlyWithTheSwitchOn() {
		FishingAlertsFeature alerts = SkyBlockFeatures.FISHING_ALERTS;
		Creature thunder = SeaCreatures.named("Thunder");
		BlockPos at = new BlockPos(12, 70, -3);
		assertEquals("Thunder caught! x: 12, y: 70, z: -3", alerts.partyMessage(thunder, false, true, true, at));
		assertEquals("Double Hook! Thunder caught! x: 12, y: 70, z: -3", alerts.partyMessage(thunder, true, true, true, at));
		// not in a party: nothing; the switch off: nothing
		assertNull(alerts.partyMessage(thunder, false, true, false, at));
		assertNull(alerts.partyMessage(thunder, false, false, true, at));
		// a place that is not known is left out
		assertTrue(alerts.partyMessage(thunder, false, true, true, null).equals("Thunder caught!"));
		assertEquals(0, alerts.catchesShared());
	}
}
