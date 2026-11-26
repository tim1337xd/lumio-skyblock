package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** The dungeon alerts: keys from Hypixel's lines (me and a teammate), and "Room cleared" once per room from the map's changes. */
class DungeonAlertsTest {
	private static final DungeonMapData.Check NONE = DungeonMapData.Check.NONE;
	private static final DungeonMapData.Check WHITE = DungeonMapData.Check.WHITE;
	private static final DungeonMapData.Check GREEN = DungeonMapData.Check.GREEN;
	private static final DungeonMapData.Kind NORMAL = DungeonMapData.Kind.NORMAL;

	@Test
	void keysFromTheGamesLines() {
		assertArrayEquals(new String[] {"Wither", "Kiwi"}, DungeonAlertsFeature.keyOf("[MAGE] Kiwi has obtained Wither Key!"));
		assertArrayEquals(new String[] {"Blood", "Rae"}, DungeonAlertsFeature.keyOf("Rae has obtained Blood Key!"));
		assertArrayEquals(new String[] {"Wither", ""}, DungeonAlertsFeature.keyOf("A Wither Key was picked up!"));
		assertArrayEquals(new String[] {"Blood", ""}, DungeonAlertsFeature.keyOf("A Blood Key was picked up!"));
		assertNull(DungeonAlertsFeature.keyOf("Kiwi: has obtained Wither Key!"), "a player writing it is no key");
	}

	@Test
	void roomClearedOncePerRoom() {
		RoomAlerts alerts = new RoomAlerts();
		assertEquals(RoomAlerts.NOTHING, alerts.update(7, NORMAL, NONE, false, false), "entered, not cleared");
		assertEquals(RoomAlerts.CLEARED, alerts.update(7, NORMAL, WHITE, false, false), "its check appeared");
		assertEquals(RoomAlerts.NOTHING, alerts.update(7, NORMAL, WHITE, false, false));
		assertEquals(RoomAlerts.NOTHING, alerts.update(7, NORMAL, GREEN, false, false), "green without the secrets alert: no second one");
		assertEquals(RoomAlerts.NOTHING, alerts.update(9, NORMAL, WHITE, false, false), "a room entered already cleared says nothing");
	}

	@Test
	void theAllSecretsAlert() {
		RoomAlerts alerts = new RoomAlerts();
		alerts.update(3, NORMAL, NONE, false, true);
		assertEquals(RoomAlerts.CLEARED, alerts.update(3, NORMAL, WHITE, false, true));
		assertEquals(RoomAlerts.ALL_SECRETS, alerts.update(3, NORMAL, GREEN, false, true));
		assertEquals(RoomAlerts.NOTHING, alerts.update(3, NORMAL, GREEN, false, true));
		alerts.update(4, NORMAL, NONE, false, true);
		assertEquals(RoomAlerts.CLEARED | RoomAlerts.ALL_SECRETS, alerts.update(4, NORMAL, GREEN, false, true), "straight to green: both");
	}

	@Test
	void theEntranceFairyAndBloodRoomsOnlyWhenWanted() {
		for (DungeonMapData.Kind kind : new DungeonMapData.Kind[] {DungeonMapData.Kind.ENTRANCE, DungeonMapData.Kind.FAIRY, DungeonMapData.Kind.BLOOD}) {
			RoomAlerts alerts = new RoomAlerts();
			alerts.update(1, kind, NONE, false, false);
			assertEquals(RoomAlerts.NOTHING, alerts.update(1, kind, WHITE, false, false), kind + " left out");
			RoomAlerts wanted = new RoomAlerts();
			wanted.update(1, kind, NONE, true, false);
			assertEquals(RoomAlerts.CLEARED, wanted.update(1, kind, WHITE, true, false), kind + " when wanted");
		}
	}

	@Test
	void aNewRunStartsAgain() {
		RoomAlerts alerts = new RoomAlerts();
		alerts.update(7, NORMAL, NONE, false, false);
		alerts.update(7, NORMAL, WHITE, false, false);
		alerts.reset();
		alerts.update(7, NORMAL, NONE, false, false);
		assertEquals(RoomAlerts.CLEARED, alerts.update(7, NORMAL, WHITE, false, false));
	}
}
