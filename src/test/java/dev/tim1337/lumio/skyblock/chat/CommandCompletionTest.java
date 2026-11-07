package dev.tim1337.lumio.skyblock.chat;

import dev.tim1337.lumio.skyblock.data.PartyList;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The SkyBlock command suggestions: which argument is typed, how entries match and are ordered, the merge with the server's, and the party. */
class CommandCompletionTest {
	@Test
	void prefixesFirstThenWordStartsThenAnywhere() {
		List<String> ids = List.of("DIAMOND_SWORD", "ENCHANTED_DIAMOND", "REFINED_DIAMOND", "DIAMOND", "SUPERDIAMONDS", "GOLD_INGOT");
		assertEquals(List.of("DIAMOND_SWORD", "DIAMOND", "ENCHANTED_DIAMOND", "REFINED_DIAMOND", "SUPERDIAMONDS"),
			CommandCompletion.order("diamond", List.of(ids), 100));
		assertEquals(0, CommandCompletion.rank("Tester1", "tes"), "ignoring case");
		assertEquals(CommandCompletion.NO_MATCH, CommandCompletion.rank("GOLD_INGOT", "diamond"));
		assertEquals(1, CommandCompletion.rank("ENCHANTED_DIAMOND_BLOCK", "ench_dia"), "the typed parts start its words, in a row");
		assertEquals(CommandCompletion.NO_MATCH, CommandCompletion.rank("ENCHANTED_GOLD", "ench_dia"));
		assertEquals(6, CommandCompletion.order("", List.of(ids), 100).size(), "nothing typed: everything");
		assertEquals(2, CommandCompletion.order("", List.of(ids), 2).size(), "at most as many as asked");
	}

	@Test
	void partyMembersComeBeforeTheOtherPlayers() {
		List<String> party = List.of("Zed", "Tom");
		List<String> island = List.of("Tester1", "Anna");
		List<String> tab = List.of("anna", "Tobi");
		assertEquals(List.of("Tom", "Tester1", "Tobi"), CommandCompletion.order("t", List.of(party, island, tab), 100));
		assertEquals(List.of("Zed", "Tom", "Tester1", "Anna", "Tobi"), CommandCompletion.order("", List.of(party, island, tab), 100),
			"each name once, ignoring case");
	}

	@Test
	void theServersSuggestionsAreMergedWithoutDoubles() {
		List<String> server = List.of("hub", "garden", "Island");
		List<String> ours = List.of("hub", "home", "island", "howl");
		assertEquals(List.of("hub", "home", "howl"), CommandCompletion.merge("h", List.of("hub"), List.of("hub", "home", "howl")));
		List<String> merged = CommandCompletion.merge("", server, ours);
		assertEquals(List.of("hub", "garden", "Island", "home", "howl"), merged, "the server's first, then the mod's new ones");
		assertEquals(List.of("garden", "hub"), CommandCompletion.merge("ga", List.of("garden", "hub"), List.of()),
			"a server entry that does not match stays, at the end");
	}

	@Test
	void thePartyIsLearnedFromTheServersMessages() {
		PartyList party = new PartyList();
		party.read("[MVP+] Friend joined the party.");
		party.read("Other joined the party.");
		assertEquals(List.of("Friend", "Other"), party.members());
		party.read("[VIP] Other has left the party.");
		assertEquals(List.of("Friend"), party.members());
		party.read("Kicked [MVP] Friend because they were offline.");
		assertEquals(List.of(), party.members());
		party.read("You have joined [MVP++] Leader's party!");
		party.read("You'll be partying with: [VIP] A_1, [MVP+] B2");
		assertEquals(List.of("Leader", "A_1", "B2"), party.members());
		party.read("[MVP++] Leader has disbanded the party!");
		assertEquals(List.of(), party.members(), "disbanded");
		// the list from /p list replaces what was known
		party.read("Old joined the party.");
		party.read("Party Members (3)");
		party.read("Party Leader: [MVP+] Tester1 ●");
		party.read("Party Members: [VIP] A ● B ●");
		assertEquals(List.of("Tester1", "A", "B"), party.members());
		party.read("The party was disbanded because all invites expired and the party was empty.");
		assertEquals(List.of(), party.members());
		party.read("Guild > [VIP] Someone joined.");
		assertEquals(List.of(), party.members(), "other messages change nothing");
	}

	@Test
	void subcommandsComeBeforeTheServersAndTheNames() {
		assertEquals(List.of("kick", "Kim", "Kevin"), CommandCompletion.merge("k", List.of("kick"), List.of("Kim", "KICK"), List.of("Kevin")));
	}
}
