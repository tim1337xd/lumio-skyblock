package dev.tim1337.lumio.skyblock.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** Who leads the party and who left last, from the server's party lines. */
class PartyListTest {
	@Test
	void followsTheLeader() {
		PartyList party = new PartyList();
		party.read("You have joined [MVP+] Kiwi's party!");
		assertEquals("Kiwi", party.leader());
		party.read("The party was transferred to Rae by Kiwi");
		assertEquals("Rae", party.leader());
		party.read("[VIP] Rae has promoted Nox to Party Leader");
		assertEquals("Nox", party.leader());
		party.read("Kiwi has left the party.");
		assertEquals("Kiwi", party.lastLeft());
		party.read("Kicked Sol because they were offline.");
		assertEquals("Sol", party.lastLeft());
		party.read("The party was disbanded because all invites expired and the party was empty.");
		assertEquals("", party.leader());
	}
}
