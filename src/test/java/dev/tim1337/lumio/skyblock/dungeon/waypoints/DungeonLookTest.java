package dev.tim1337.lumio.skyblock.dungeon.waypoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.api.experimental.waypoint.WaypointLook;
import dev.tim1337.lumio.api.experimental.waypoint.WaypointStyle;

import org.junit.jupiter.api.Test;

/** The dungeon waypoints' look: the general one unless the player set their own. */
class DungeonLookTest {
	private static final WaypointStyle OWN = new WaypointStyle(1.5f, WaypointStyle.TextScaling.SIGN, true, true, false, true, WaypointStyle.Marker.OUTLINE, 3f, 1f, true, 40f, 1f,
		false, 48, 1f);

	@Test
	void theGeneralLookUnlessTheSwitchIsOff() {
		WaypointStyle general = WaypointStyle.DEFAULT;
		assertSame(general, WaypointStyle.pick(true, general, OWN));
		assertSame(general, WaypointStyle.pick(true, general, null));
		assertSame(OWN, WaypointStyle.pick(false, general, OWN));
		// a changed general look reaches the dungeon waypoints while they use it
		WaypointStyle changed = new WaypointStyle(1f, WaypointStyle.TextScaling.SMALLER_FAR, true, true, true, false, WaypointStyle.Marker.BEAM, 2f, 1f, true, 80f, 1f, true, 0, 1f);
		assertSame(changed, WaypointStyle.pick(true, changed, OWN));
	}

	@Test
	void theDefaultsKeepTheDungeonPointsAsTheyWere() {
		WaypointStyle look = WaypointStyle.pick(true, WaypointStyle.DEFAULT, null);
		assertTrue(look.boxed());
		assertEquals(0.28f, look.fillOpacity(0.28f), "an open point's fill");
		assertEquals(0.6f, look.fillOpacity(0.6f), "a filled point's fill");
		assertEquals(2f, look.lineWidth());
		assertFalse(look.beamOn(false), "dungeon points have no beam");
		assertFalse(look.iconLabel());
		assertFalse(look.textBackground());
		assertTrue(look.textShadow());
		assertEquals(WaypointLook.labelSize(12), look.labelScale(12));
		assertTrue(look.inReach(400));
		assertFalse(DungeonLook.showsDistance(true, look), "no distance on dungeon labels, as before");
	}

	@Test
	void theirOwnLook() {
		assertTrue(DungeonLook.showsDistance(false, OWN));
		assertFalse(DungeonLook.showsDistance(true, OWN));
		assertEquals(0f, OWN.fillOpacity(0.6f), "the outline alone");
		assertFalse(OWN.inReach(49));
		assertEquals(1f, OWN.nearOpacity(0.2));
	}
}
