package dev.tim1337.lumio.skyblock.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.List;

/** The lobby's server from the scoreboard's date line. */
class LobbyIdTest {
	@Test
	void readsTheServerAfterTheDate() {
		assertEquals("m14AB", LobbyId.of(List.of("SKYBLOCK", "11/12/26 m14AB", " Late Autumn 3rd")));
		assertEquals("mini22BC", LobbyId.of(List.of("11/12/26  mini22BC")));
	}

	@Test
	void nothingWithoutTheLine() {
		assertEquals("", LobbyId.of(List.of("SKYBLOCK", "Purse: 1,000")));
		assertEquals("", LobbyId.of(List.of()));
	}
}
