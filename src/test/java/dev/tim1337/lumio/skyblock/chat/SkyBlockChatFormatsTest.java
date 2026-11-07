package dev.tim1337.lumio.skyblock.chat;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which lines are the game's own, from the data file: never what a player writes, and a bad file does no harm. */
class SkyBlockChatFormatsTest {
	private static SkyBlockChatFormats builtIn() throws Exception {
		try (var stream = SkyBlockChatFormatsTest.class.getResourceAsStream("/assets/lumio-skyblock/skyblock/chat_formats.json")) {
			return SkyBlockChatFormats.read(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)));
		}
	}

	@Test
	void theGamesMessagesBelongToTheTab() throws Exception {
		SkyBlockChatFormats formats = builtIn();
		assertTrue(formats.matches("RARE DROP! Summoning Eye (+400% ✯ Magic Find)"));
		assertTrue(formats.matches("You received 1,200 coins from killing a Zealot."));
		assertTrue(formats.matches("  SKILL LEVEL UP Mining XX"));
		assertTrue(formats.matches("SLAYER QUEST STARTED!"));
		assertTrue(formats.matches("[Bazaar] Buy Order Setup! 64x Enchanted Diamond"));
		assertTrue(formats.matches("[BOSS] The Watcher: Oh? You found my lair."));
	}

	@Test
	void whatPlayersWriteNeverDoes() throws Exception {
		SkyBlockChatFormats formats = builtIn();
		assertFalse(formats.matches("[MVP+] Steve123: RARE DROP! trust me"));
		assertFalse(formats.matches("Steve123: You received 5 coins"));
		assertFalse(formats.matches("Party > [VIP] Bob: RARE DROP! Summoning Eye"));
		assertFalse(formats.matches("Guild > [MVP] Eve [Officer]: You earned it"));
		assertFalse(formats.matches("From [MVP+] Cara: You found it"));
		assertFalse(formats.matches("anyone selling a juju bow"));
	}

	@Test
	void aFileAddsFormatsAndOddEntriesAreLeftOut() {
		SkyBlockChatFormats formats = SkyBlockChatFormats.read(JsonParser.parseString("{\"starts\":[\"Hello there\",5,\"\",null],\"contains\":\"not a list\"}"), null, JsonParser.parseString("[1,2]"));
		assertTrue(formats.matches("Hello there, you"));
		assertFalse(formats.matches("5"));
		assertFalse(formats.matches(""));
	}
}
