package dev.tim1337.lumio.skyblock.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.List;

class RecentChatNamesTest {
	@Test
	void findsTheSender() {
		assertEquals("Steve123", RecentChatNames.senderOf("Steve123: hello"));
		assertEquals("Steve123", RecentChatNames.senderOf("[VIP] Steve123: hello"));
		assertEquals("Steve123", RecentChatNames.senderOf("[302] ✦ [MVP+] Steve123: hello"));
		assertEquals("Bob_1", RecentChatNames.senderOf("Party > [MVP+] Bob_1: hello"));
		assertEquals("Bob_1", RecentChatNames.senderOf("Guild > Bob_1 [OFF]: hello"));
		assertEquals("Eve", RecentChatNames.senderOf("From [VIP] Eve: hello"));
		assertEquals("Eve", RecentChatNames.senderOf("To Eve: hello"));
	}

	@Test
	void whatALineSaysIsNoName() {
		assertNull(RecentChatNames.senderOf("You placed a Summoning Eye!"));
		assertNull(RecentChatNames.senderOf("A long server message: with a colon"));
		assertEquals("Bob", RecentChatNames.senderOf("Bob: Eve: not her"), "only the one in front");
	}

	@Test
	void newestFirstWithoutThePlayerAndWithoutDoubles() {
		RecentChatNames names = new RecentChatNames();
		names.read("Bob: one");
		names.read("Eve: two");
		names.read("Steve123: mine");
		names.read("Bob: three");
		assertEquals(List.of("Bob", "Eve"), names.newestFirst("steve123"));
	}

	@Test
	void staysBounded() {
		RecentChatNames names = new RecentChatNames();
		for (int i = 0; i < 200; i++) {
			names.read("Player" + i + ": hi");
		}
		assertEquals(64, names.newestFirst("x").size());
		assertEquals("Player199", names.newestFirst("x").getFirst());
	}

	@Test
	void forgets() {
		RecentChatNames names = new RecentChatNames();
		names.read("Bob: one");
		names.clear();
		assertTrue(names.newestFirst("x").isEmpty());
	}
}
