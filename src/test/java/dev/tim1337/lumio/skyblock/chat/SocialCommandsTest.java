package dev.tim1337.lumio.skyblock.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** The subcommands of /friend, /party and /guild, from their help pages, for each command and its alias, with and without a typed prefix. */
class SocialCommandsTest {
	private static final List<String> FRIEND = List.of("accept", "add", "best", "deny", "help", "list", "nickname", "notifications", "remove", "removeall", "requests");
	private static final List<String> PARTY = List.of("accept", "chat", "demote", "disband", "invite", "kick", "kickoffline", "leave", "list", "mute", "poll", "private", "promote", "settings", "transfer", "warp");
	private static final List<String> GUILD = List.of("accept", "chat", "create", "demote", "disband", "discord", "help", "history", "info", "invite", "join", "kick", "leave", "log", "member", "members", "menu", "motd", "mute",
		"mypermissions", "notifications", "officerchat", "online", "promote", "quest", "rename", "setrank", "settings", "slow", "tag", "tagcolor", "toggle", "top", "transfer");
	private static final int MOST_DESCRIPTION = 44;

	private static CommandTree tree() throws IOException {
		try (InputStream in = SocialCommandsTest.class.getResourceAsStream("/assets/lumio-skyblock/skyblock/commands.json")) {
			assertNotNull(in);
			return CommandTree.of(JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
		}
	}

	private static List<String> texts(CommandTree.Completion found) {
		return found.fixed().stream().map(CommandTree.Entry::text).toList();
	}

	/** @return what the list shows for an input: what matches, the best first */
	private static List<String> shown(CommandTree tree, String input) {
		CommandTree.Completion found = tree.find(input);
		assertNotNull(found, input);
		return CommandCompletion.order(found.typed(), List.of(texts(found)), Integer.MAX_VALUE);
	}

	private static CommandTree.Entry entry(CommandTree tree, String input, String text) {
		return tree.find(input).fixed().stream().filter(candidate -> candidate.text().equals(text)).findFirst().orElseThrow();
	}

	@Test
	void everyCommandAndItsAliasHaveTheirSubcommands() throws IOException {
		CommandTree tree = tree();
		for (String command : List.of("/friend ", "/f ")) {
			assertEquals(FRIEND, shown(tree, command), command);
		}
		for (String command : List.of("/party ", "/p ")) {
			assertEquals(PARTY, shown(tree, command), command);
		}
		for (String command : List.of("/guild ", "/g ")) {
			assertEquals(GUILD, shown(tree, command), command);
		}
	}

	@Test
	void aTypedPrefixNarrowsThem() throws IOException {
		CommandTree tree = tree();
		for (String command : List.of("/friend re", "/f re")) {
			assertEquals(List.of("remove", "removeall", "requests"), shown(tree, command), command);
		}
		for (String command : List.of("/party wa", "/p wa")) {
			assertEquals(List.of("warp"), shown(tree, command), command);
		}
		for (String command : List.of("/party KI", "/p ki")) {
			assertEquals(List.of("kick", "kickoffline"), shown(tree, command), command);
		}
		for (String command : List.of("/guild mu", "/g mu")) {
			assertEquals(List.of("mute"), shown(tree, command), command);
		}
		for (String command : List.of("/guild ta", "/g ta")) {
			assertEquals(List.of("tag", "tagcolor"), shown(tree, command), command);
		}
		assertTrue(shown(tree, "/p zzz").isEmpty());
	}

	@Test
	void theyTakeWhatTheHelpPagesSay() throws IOException {
		CommandTree tree = tree();
		assertEquals("<player> <nickname>", entry(tree, "/f ", "nickname").hint());
		assertEquals("<page/best>", entry(tree, "/friend ", "list").hint());
		assertEquals("<page>", entry(tree, "/f ", "requests").hint());
		assertNull(entry(tree, "/f ", "removeall").hint());
		assertEquals("<players...>", entry(tree, "/party ", "invite").hint());
		assertEquals("<question/answer/answer/answer...>", entry(tree, "/p ", "poll").hint());
		assertEquals("<setting> <value>", entry(tree, "/p ", "settings").hint());
		assertNull(entry(tree, "/p ", "warp").hint());
		assertEquals("<chat message>", entry(tree, "/g ", "chat").hint());
		assertEquals("<player> <reason>", entry(tree, "/guild ", "kick").hint());
		assertEquals("<player/everyone> <time>", entry(tree, "/g ", "mute").hint());
		assertEquals("<player> <rank>", entry(tree, "/g ", "setrank").hint());
		assertNull(entry(tree, "/g ", "accept").hint());
	}

	@Test
	void everyOneHasAShortDescription() throws IOException {
		CommandTree tree = tree();
		for (String command : List.of("/f ", "/p ", "/g ")) {
			for (CommandTree.Entry entry : tree.find(command).fixed()) {
				assertNotNull(entry.description(), command + entry.text());
				assertFalse(entry.description().isBlank(), command + entry.text());
				assertTrue(entry.description().length() <= MOST_DESCRIPTION, command + entry.text() + ": " + entry.description());
			}
		}
		assertEquals("Warp the party to your server", entry(tree, "/party ", "warp").description());
		assertEquals("Remove all friends except best friends", entry(tree, "/friend ", "removeall").description());
	}

	@Test
	void theQuietTextHasBoth() throws IOException {
		CommandTree tree = tree();
		assertEquals("Warp the party to your server", entry(tree, "/p ", "warp").quiet());
		assertEquals("<player> · " + entry(tree, "/p ", "kick").description(), entry(tree, "/p ", "kick").quiet());
	}

	@Test
	void aPlayerArgumentTakesNames() throws IOException {
		CommandTree tree = tree();
		for (String input : List.of("/friend add ", "/f accept ", "/f best ", "/f deny ", "/f remove ", "/f nickname ", "/party accept ", "/p demote ", "/p kick ", "/p promote ", "/p transfer ", "/g invite ", "/g demote ", "/g kick ", "/g promote ",
			"/g transfer ", "/g setrank ", "/g mute ")) {
			assertEquals(CommandCompletion.Source.PLAYERS, tree.find(input).names(), input);
		}
		assertNull(tree.find("/p warp ").names());
		assertNull(tree.find("/g accept ").names());
	}

	@Test
	void manyPlayersGoOnTakingNames() throws IOException {
		CommandTree tree = tree();
		CommandTree.Completion second = tree.find("/party invite Steve123 ");
		assertEquals(CommandCompletion.Source.PLAYERS, second.names());
		assertEquals("", second.typed());
		assertEquals(CommandCompletion.Source.PLAYERS, tree.find("/p invite Steve123 Bob Ev").names());
		assertEquals("Ev", tree.find("/p invite Steve123 Bob Ev").typed());
		assertNull(tree.find("/p kick Steve123 "), "one player, and nothing known after it");
	}

	@Test
	void fixedValuesAreOffered() throws IOException {
		CommandTree tree = tree();
		assertEquals(List.of("everyone"), texts(tree.find("/g mute ")));
		assertEquals(List.of("best"), texts(tree.find("/f list ")));
	}

	@Test
	void theCommandItselfStillTakesAName() throws IOException {
		CommandTree tree = tree();
		assertEquals(CommandCompletion.Source.PLAYERS, tree.find("/p ").names(), "/p Steve123 invites");
		assertEquals(CommandCompletion.Source.PLAYERS, tree.find("/f ").names(), "/f Steve123 adds");
	}
}
