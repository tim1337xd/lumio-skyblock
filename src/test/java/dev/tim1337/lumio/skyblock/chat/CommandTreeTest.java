package dev.tim1337.lumio.skyblock.chat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The commands' data file and the tree made of it: subcommand, then argument, then names. */
class CommandTreeTest {
	private static CommandTree tree() throws IOException {
		try (InputStream in = CommandTreeTest.class.getResourceAsStream("/assets/lumio-skyblock/skyblock/commands.json")) {
			assertNotNull(in);
			return CommandTree.of(JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
		}
	}

	private static boolean has(CommandTree.Completion found, String text) {
		return found.fixed().stream().anyMatch(entry -> entry.text().equals(text));
	}

	@Test
	void theCommandsAskedForAreAllThere() throws IOException {
		CommandTree tree = tree();
		for (String command : List.of("party", "p", "guild", "g", "friend", "f", "msg", "w", "r", "ignore", "chat", "pc", "gc", "oc", "ac", "visit", "trade", "pv", "ah", "bz", "viewrecipe",
			"recipes", "craft", "storage", "ec", "bp", "pets", "wardrobe", "equipment", "hotm", "skills", "collection", "sbmenu", "warp", "joininstance", "play", "lobby", "tip", "boop", "report")) {
			assertNotNull(tree.rootOf(command), command);
		}
	}

	@Test
	void subcommandsComeWithWhatTheyTake() throws IOException {
		CommandTree.Completion party = tree().find("/party ");
		assertTrue(has(party, "kick"));
		assertEquals(7, party.start());
		assertEquals("", party.typed());
		assertEquals("<player>", party.fixed().stream().filter(entry -> entry.text().equals("kick")).findFirst().orElseThrow().hint());
	}

	@Test
	void aliasesAndCaseAreTheSame() throws IOException {
		CommandTree tree = tree();
		assertEquals("party", tree.rootOf("P"));
		assertTrue(has(tree.find("/P "), "invite"));
		assertEquals(CommandCompletion.Source.PLAYERS, tree.find("/g KICK ").names());
	}

	@Test
	void theNamesFollowTheContext() throws IOException {
		CommandTree tree = tree();
		assertEquals(CommandCompletion.Source.PLAYERS, tree.find("/party kick Ti").names());
		assertEquals("Ti", tree.find("/party kick Ti").typed());
		assertEquals(CommandCompletion.Source.PLAYERS, tree.find("/p promote ").names());
		assertEquals(CommandCompletion.Source.PLAYERS, tree.find("/p invite ").names());
		assertEquals(CommandCompletion.Source.PLAYERS, tree.find("/g demote ").names());
		assertEquals(CommandCompletion.Source.PLAYERS, tree.find("/g invite ").names());
		assertEquals(CommandCompletion.Source.PLAYERS, tree.find("/f remove ").names());
		assertEquals(CommandCompletion.Source.PLAYERS, tree.find("/visit ").names());
		assertEquals(CommandCompletion.Source.WARPS, tree.find("/warp hu").names());
		assertEquals(CommandCompletion.Source.RECIPES, tree.find("/viewrecipe ench").names());
		assertEquals(CommandCompletion.Source.SACKS, tree.find("/gfs DIA").names());
	}

	@Test
	void fixedValuesAreOffered() throws IOException {
		assertTrue(has(tree().find("/chat "), "guild"));
	}

	@Test
	void whatIsNotACommandIsLeftAlone() throws IOException {
		CommandTree tree = tree();
		assertNull(tree.find("/warp"), "still the command's name");
		assertNull(tree.find("/warp hub "), "past what is known");
		assertNull(tree.find("/gamemode cr"), "not a known command");
		assertNull(tree.find("warp hub"), "a chat message");
	}

	@Test
	void theHintShowsWhileNothingIsTyped() throws IOException {
		CommandTree tree = tree();
		assertEquals("<player>", tree.usage("/party kick "));
		assertNull(tree.usage("/party kick Ti"));
		assertNull(tree.usage("/party list x"));
	}

	@Test
	void whatTheGameShowsIsAddedOnce() throws IOException {
		CommandTree tree = tree();
		assertFalse(tree.learn("p", "kick", "<player>"), "it is known");
		assertTrue(tree.learn("p", "newthing", "<player>"));
		assertFalse(tree.learn("party", "newthing", ""));
		assertTrue(has(tree.find("/party "), "newthing"));
		assertEquals(CommandCompletion.Source.PLAYERS, tree.find("/party newthing ").names());
		assertFalse(tree.learn("unknowncommand", "x", ""));
	}

	@Test
	void aStrangeFileGivesAnEmptyTree() {
		JsonObject odd = JsonParser.parseString("{\"commands\":[1,{\"noname\":true}]}").getAsJsonObject();
		assertNull(CommandTree.of(odd).find("/party "));
		assertNull(CommandTree.of(new JsonObject()).find("/party "));
	}

	@Test
	void linesOfHelpAreRead() {
		CommandLearner.Learned help = CommandLearner.read("/party kick <player> - Removes the player from your party");
		assertEquals("party", help.command());
		assertEquals("kick", help.sub());
		assertEquals("<player>", help.args());
		assertEquals("list", CommandLearner.read("/p list - Lists the members").sub());
		assertEquals("add", CommandLearner.read("Invalid usage! Usage: /ignore add <player>").sub());
		assertNull(CommandLearner.read("You are not in a party"));
		assertNull(CommandLearner.read("/party"));
	}

	/** @return the subcommands of what is typed that match the word, in the order the list shows them */
	private static List<String> subsFor(CommandTree tree, String typed) {
		CommandTree.Completion found = tree.find(typed);
		List<String> fixed = new java.util.ArrayList<>();
		for (CommandTree.Entry entry : found.fixed()) {
			fixed.add(entry.text());
		}
		return CommandCompletion.order(found.typed(), List.of(fixed), 100);
	}

	@Test
	void theWordBeingTypedFiltersTheSubcommands() throws IOException {
		CommandTree tree = tree();
		assertEquals(List.of("accept"), subsFor(tree, "/party acc"));
		assertEquals(List.of("accept"), subsFor(tree, "/party accept"));
	}

	@Test
	void afterASubcommandItsArgumentIsSuggested() throws IOException {
		CommandTree.Completion found = tree().find("/party accept ");
		assertTrue(found.fixed().isEmpty(), "no subcommands after one");
		assertEquals(CommandCompletion.Source.PLAYERS, found.names());
	}

	@Test
	void aliasesAreTheirCommands() throws IOException {
		CommandTree tree = tree();
		String[][] pairs = {{"p", "party"}, {"f", "friend"}, {"g", "guild"}, {"msg", "message"}, {"w", "message"}, {"tell", "message"}, {"r", "reply"}};
		for (String[] pair : pairs) {
			assertEquals(pair[1], tree.rootOf(pair[0]), pair[0]);
		}
		assertEquals(subsFor(tree, "/party "), subsFor(tree, "/p "));
		assertEquals(subsFor(tree, "/friend a"), subsFor(tree, "/f a"));
		assertEquals(tree.find("/party accept ").names(), tree.find("/p accept ").names());
	}

	@Test
	void partyAndFriendTakeANameDirectly() throws IOException {
		CommandTree tree = tree();
		for (String command : List.of("/p ", "/party ", "/f ", "/friend ")) {
			CommandTree.Completion found = tree.find(command);
			assertFalse(found.fixed().isEmpty(), command + " has its subcommands");
			assertEquals(CommandCompletion.Source.PLAYERS, found.names(), command + " takes a name as well");
		}
		// a name that matches no subcommand: only names are left
		assertTrue(subsFor(tree, "/p xyzname").isEmpty());
		List<String> both = CommandCompletion.merge("in", subsFor(tree, "/p in"), List.of(), List.of("Indigo", "Bob"));
		assertEquals("invite", both.get(0), "subcommands first");
		assertTrue(both.contains("Indigo") && !both.contains("Bob"));
	}

	@Test
	void visitTakesAName() throws IOException {
		assertEquals(CommandCompletion.Source.PLAYERS, tree().find("/visit na").names());
	}
}
