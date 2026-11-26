package dev.tim1337.lumio.skyblock.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Set;

/** Party commands: which lines are requests, their names, what follows, and the one command each leads to. */
class PartyCommandsTest {
	private static PartyCommands.Context context(boolean leads) {
		return new PartyCommands.Context() {
			public String self() {
				return "Ash";
			}

			public boolean selfLeads() {
				return leads;
			}

			public String lastLeft() {
				return "Nox";
			}

			public String coords() {
				return "-12, 70, 340";
			}

			public int ping() {
				return 42;
			}

			public String tps() {
				return "19.8";
			}

			public int fps() {
				return 240;
			}

			public String time() {
				return "21:04";
			}

			public int roll(int most) {
				return 1;
			}
		};
	}

	/** The same game, with no ping measured yet. */
	private static PartyCommands.Context withoutPing() {
		PartyCommands.Context known = context(false);
		return new PartyCommands.Context() {
			public String self() {
				return known.self();
			}

			public boolean selfLeads() {
				return false;
			}

			public String lastLeft() {
				return known.lastLeft();
			}

			public String coords() {
				return known.coords();
			}

			public int ping() {
				return -1;
			}

			public String tps() {
				return known.tps();
			}

			public int fps() {
				return known.fps();
			}

			public String time() {
				return known.time();
			}

			public int roll(int most) {
				return 1;
			}
		};
	}

	@Test
	void pingAndFpsAnswerInPartyChatAlsoToThePlayersOwnLine() {
		assertEquals("pc Ping: 42 ms", answer("Party > Kiwi: !ping", false));
		assertEquals("pc FPS: 240", answer("Party > [MVP+] Kiwi: !fps", false));
		// the player's own line: only these two count
		assertEquals("pc Ping: 42 ms", answer("Party > Ash: !ping", false));
		assertEquals("pc FPS: 240", answer("Party > Ash: !fps", false));
		assertNull(answer("Party > Ash: !coords", false));
		assertNull(answer("Party > Ash: !warp", true));
	}

	@Test
	void pingAndFpsNeverAnswerInOtherChats() {
		assertNull(answer("Guild > Kiwi: !ping", false));
		assertNull(answer("From Kiwi: !fps", false));
		// the other info commands still do, where the player allows those chats
		assertEquals("gc Coords: -12, 70, 340", answer("Guild > Kiwi: !coords", false));
	}

	@Test
	void onlyTheExactCommandCounts() {
		assertNull(answer("Party > Kiwi: ping", false));
		assertNull(answer("Party > Kiwi: !pingg", false));
		assertNull(answer("Party > Kiwi: what is your !ping", false));
		assertNull(PartyCommands.read("Party > Kiwi: !fpss", "!"));
	}

	@Test
	void withoutAMeasuredPingNothingIsSaidYet() {
		PartyCommands.Request request = PartyCommands.read("Party > Kiwi: !ping", "!");
		assertNull(PartyCommands.answer(request, withoutPing(), command -> true));
		// the answer once the measurement is there
		assertEquals("pc Ping: 147 ms", PartyCommands.pingReply(147));
	}

	@Test
	void switchedOffTheyDoNotAnswer() {
		PartyCommands.Request request = PartyCommands.read("Party > Kiwi: !ping", "!");
		assertNull(PartyCommands.answer(request, context(false), command -> command != PartyCommands.Command.PING));
		PartyCommands.Request fps = PartyCommands.read("Party > Kiwi: !fps", "!");
		assertNull(PartyCommands.answer(fps, context(false), command -> command != PartyCommands.Command.FPS));
	}

	@Test
	void eachOfTheTwoIsAnsweredOncePerCooldownWhoeverAsks() {
		PartyCommands.Gate gate = new PartyCommands.Gate();
		long cooldown = 5_000;
		// the key the module uses for a command answered once per cooldown
		assertTrue(gate.take("!ping", 1_000, cooldown, Set.of()));
		// both asked close together: each once
		assertTrue(gate.take("!fps", 1_200, cooldown, Set.of()));
		// the same command again within the cooldown, from anyone: ignored
		assertFalse(gate.take("!ping", 3_000, cooldown, Set.of()));
		assertFalse(gate.take("!fps", 5_900, cooldown, Set.of()));
		assertTrue(gate.take("!ping", 6_100, cooldown, Set.of()));
		assertTrue(PartyCommands.ownNumbers(PartyCommands.Command.PING));
		assertTrue(PartyCommands.ownNumbers(PartyCommands.Command.FPS));
		assertFalse(PartyCommands.ownNumbers(PartyCommands.Command.COORDS));
	}

	private static String answer(String line, boolean leads) {
		PartyCommands.Request request = PartyCommands.read(line, "!");
		return request == null ? null : PartyCommands.answer(request, context(leads), command -> command != PartyCommands.Command.DICE);
	}

	@Test
	void readsPartyGuildAndDirectLines() {
		PartyCommands.Request party = PartyCommands.read("Party > [MVP+] Kiwi: !warp", "!");
		assertEquals(PartyCommands.Source.PARTY, party.source());
		assertEquals("Kiwi", party.sender());
		assertEquals(PartyCommands.Command.WARP, party.command());
		assertEquals(PartyCommands.Source.GUILD, PartyCommands.read("Guild > [VIP] Rae [Pro]: !ping", "!").source());
		assertEquals(PartyCommands.Source.DIRECT, PartyCommands.read("From [MVP+] Sol: !coords", "!").source());
		assertNull(PartyCommands.read("Kiwi: !warp", "!"));
		assertNull(PartyCommands.read("Party > Kiwi: warp", "!"));
		assertNull(PartyCommands.read("Party > Kiwi: !explode", "!"));
	}

	@Test
	void aliasesAndArguments() {
		assertEquals(PartyCommands.Command.WARP, PartyCommands.read("Party > Kiwi: !w", "!").command());
		assertEquals(PartyCommands.Command.KICK_OFFLINE, PartyCommands.read("Party > Kiwi: !KO", "!").command());
		PartyCommands.Request kick = PartyCommands.read("Party > Kiwi: !k  Steve  ", "!");
		assertEquals("Steve", kick.argument());
		assertEquals(PartyCommands.Command.FLOOR, PartyCommands.read("Party > Kiwi: !m7", "!").command());
		assertEquals(PartyCommands.Command.DOWNTIME, PartyCommands.read("Party > Kiwi: .dt", ".").command());
	}

	@Test
	void leaderCommandsOnlyWhileLeading() {
		assertEquals("p warp", answer("Party > Kiwi: !warp", true));
		assertNull(answer("Party > Kiwi: !warp", false));
		assertEquals("p transfer Kiwi", answer("Party > Kiwi: !ptme", true));
		assertEquals("p transfer Rae", answer("Party > Kiwi: !pt Rae", true));
		assertEquals("p kick Steve", answer("Party > Kiwi: !kick Steve", true));
		assertNull(answer("Party > Kiwi: !kick", true));
		assertEquals("p invite Nox", answer("Party > Kiwi: !reinv", true));
		assertEquals("joininstance MASTER_CATACOMBS_FLOOR_SEVEN", answer("Party > Kiwi: !m7", true));
		assertEquals("joininstance CATACOMBS_FLOOR_ONE", answer("Party > Kiwi: !f1", true));
		// never from the guild or a message, and never a name that is not one
		assertNull(answer("Guild > Kiwi: !warp", true));
		assertNull(answer("Party > Kiwi: !kick St;eve", true));
	}

	@Test
	void infoReplies() {
		assertEquals("pc Ping: 42 ms", answer("Party > Kiwi: !ping", false));
		assertEquals("pc Coords: -12, 70, 340", answer("Party > Kiwi: !co", false));
		assertEquals("gc TPS: 19.8", answer("Guild > Kiwi: !tps", false));
		assertEquals("msg Sol Time: 21:04", answer("From Sol: !time", false));
		assertEquals("pc Downtime noted for Kiwi: food.", answer("Party > Kiwi: !dt food", false));
		assertTrue(answer("Party > Kiwi: !help", false).startsWith("pc Commands: warp"));
		// switched off, and never the player's own request (but for the ping and the frame rate, see below)
		assertNull(answer("Party > Kiwi: !dice", false));
		assertNull(answer("Party > Ash: !time", false));
	}

	@Test
	void cooldownAndBlockedPlayers() {
		PartyCommands.Gate gate = new PartyCommands.Gate();
		assertTrue(gate.take("Kiwi", 0, 5_000, Set.of()));
		assertFalse(gate.take("kiwi", 4_000, 5_000, Set.of()));
		assertTrue(gate.take("Kiwi", 5_000, 5_000, Set.of()));
		assertTrue(gate.take("Rae", 4_000, 5_000, Set.of()));
		assertFalse(gate.take("Nox", 0, 5_000, Set.of("nox")));
	}
}
