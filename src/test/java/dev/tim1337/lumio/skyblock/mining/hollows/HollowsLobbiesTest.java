package dev.tim1337.lumio.skyblock.mining.hollows;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Places per lobby: kept apart, own visits over chat, expiry, clearing, and a round trip through the file. */
class HollowsLobbiesTest {
	private static final long NOW = 1_800_000_000_000L;

	@Test
	void eachLobbyKeepsItsOwnPlaces() {
		HollowsLobbies lobbies = new HollowsLobbies();
		assertTrue(lobbies.put("m14AB", HollowsStructure.MINES_OF_DIVAN, 700, 80, 330, true, NOW));
		assertEquals(1, lobbies.spots("m14AB").size());
		assertTrue(lobbies.spots("m99ZZ").isEmpty(), "never shown in another lobby");
		assertTrue(lobbies.spots("").isEmpty());
	}

	@Test
	void ownVisitsWinOverChat() {
		HollowsLobbies lobbies = new HollowsLobbies();
		lobbies.put("m1", HollowsStructure.JUNGLE_TEMPLE, 300, 100, 300, false, NOW);
		assertFalse(lobbies.put("m1", HollowsStructure.JUNGLE_TEMPLE, 310, 100, 310, false, NOW), "the first chat place stays");
		assertTrue(lobbies.put("m1", HollowsStructure.JUNGLE_TEMPLE, 330, 110, 360, true, NOW), "the player's own visit replaces it");
		assertFalse(lobbies.put("m1", HollowsStructure.JUNGLE_TEMPLE, 300, 100, 300, false, NOW), "chat never moves it again");
		assertEquals(330, lobbies.spots("m1").get(HollowsStructure.JUNGLE_TEMPLE).x());
	}

	@Test
	void placesOutsideAreRefused() {
		HollowsLobbies lobbies = new HollowsLobbies();
		assertFalse(lobbies.put("m1", HollowsStructure.ODAWA, 0, 100, 0, true, NOW));
		assertFalse(lobbies.put("", HollowsStructure.ODAWA, 400, 100, 400, true, NOW));
	}

	@Test
	void oldLobbiesExpire() {
		HollowsLobbies lobbies = new HollowsLobbies();
		lobbies.put("old", HollowsStructure.ODAWA, 400, 100, 400, true, NOW);
		lobbies.put("new", HollowsStructure.ODAWA, 400, 100, 400, true, NOW + HollowsLobbies.KEEP_MILLIS);
		assertFalse(lobbies.expire(NOW + HollowsLobbies.KEEP_MILLIS), "not yet");
		lobbies.seen("new", NOW + 2 * HollowsLobbies.KEEP_MILLIS);
		assertTrue(lobbies.expire(NOW + HollowsLobbies.KEEP_MILLIS + 1));
		assertTrue(lobbies.spots("old").isEmpty());
		assertEquals(1, lobbies.spots("new").size());
	}

	@Test
	void clearingALobby() {
		HollowsLobbies lobbies = new HollowsLobbies();
		lobbies.put("m1", HollowsStructure.ODAWA, 400, 100, 400, true, NOW);
		lobbies.put("m2", HollowsStructure.ODAWA, 400, 100, 400, true, NOW);
		assertTrue(lobbies.clear("m1"));
		assertTrue(lobbies.spots("m1").isEmpty());
		assertEquals(1, lobbies.spots("m2").size());
	}

	@Test
	void roundTripThroughTheFile() {
		HollowsLobbies lobbies = new HollowsLobbies();
		lobbies.put("m14AB", HollowsStructure.KHAZAD_DUM, 540, 50, 540, false, NOW);
		lobbies.put("m14AB", HollowsStructure.FAIRY_GROTTO, 450, 90, 600, true, NOW);
		lobbies.put("stale", HollowsStructure.ODAWA, 400, 100, 400, true, NOW - 2 * HollowsLobbies.KEEP_MILLIS);
		HollowsLobbies read = new HollowsLobbies();
		read.load(lobbies.toJson(), NOW);
		assertEquals(lobbies.spots("m14AB"), read.spots("m14AB"));
		assertTrue(read.spots("stale").isEmpty(), "dropped when read");
	}

	@Test
	void brokenFilesAreLeftOut() {
		HollowsLobbies read = new HollowsLobbies();
		read.load("not json {", NOW);
		assertEquals(0, read.lobbyCount());
		read.load("{\"m1\": {\"seen\": " + NOW + ", \"spots\": {\"nowhere\": [1,2,3,1], \"odawa\": [400,100,400,1], \"divan\": [1,2]}}}", NOW);
		assertEquals(1, read.spots("m1").size());
	}

	@Test
	void theNumberOfLobbiesIsBounded() {
		HollowsLobbies lobbies = new HollowsLobbies();
		for (int i = 0; i < HollowsLobbies.MOST_LOBBIES + 5; i++) {
			lobbies.put("m" + i, HollowsStructure.ODAWA, 400, 100, 400, true, NOW + i);
		}
		assertEquals(HollowsLobbies.MOST_LOBBIES, lobbies.lobbyCount());
		assertTrue(lobbies.spots("m0").isEmpty(), "the oldest went first");
	}
}
