package dev.tim1337.lumio.skyblock.fishing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.api.experimental.util.ChatCoords;
import dev.tim1337.lumio.skyblock.fishing.SeaCreatures.Creature;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** The fishing alerts: catch lines, the list's defaults, the party message, shares from chat, name tags nearby, and one alert per creature. */
class FishingAlertsTest {
	private static final Predicate<Creature> RARE = SeaCreatures::rare;

	@Test
	void catchLinesNameTheirCreature() {
		assertEquals("Thunder", SeaCreatures.caughtIn("You hear a massive rumble as Thunder emerges.").name());
		assertEquals("Lord Jawbus", SeaCreatures.caughtIn("You have angered a legendary creature... Lord Jawbus has arrived.").name());
		assertEquals("Squid", SeaCreatures.caughtIn("A Squid appeared.").name());
		// creatures whose line does not say their name
		assertEquals("Grim Reaper", SeaCreatures.caughtIn("This can't be! The manifestation of death himself!").name());
		assertEquals("Yeti", SeaCreatures.caughtIn("What is this creature!?").name());
		assertEquals("Great White Shark", SeaCreatures.caughtIn("Hide no longer, a Great White Shark has tracked your scent and thirsts for your blood!").name());
		assertNull(SeaCreatures.caughtIn("GOOD CATCH! You found a Fairy Soul."));
		// the line before a double hook's catch is no catch itself
		assertNull(SeaCreatures.caughtIn("It's a Double Hook!"));
		assertTrue("It's a Double Hook! Woot woot!".contains(SeaCreatures.DOUBLE_HOOK));
	}

	@Test
	void theRareOnesAreOnTheListAtFirst() {
		List<String> rare = new ArrayList<>();
		for (Creature creature : SeaCreatures.all()) {
			if (SeaCreatures.rare(creature)) {
				rare.add(creature.name());
			}
		}
		for (String name : new String[] {"Thunder", "Lord Jawbus", "Great White Shark", "Grim Reaper", "Yeti", "Reindrake", "Phantom Fisher", "The Loch Emperor",
			"Abyssal Miner", "Water Hydra", "Plhlegblast", "Fiery Scuttler", "Ragnarok", "Titanoboa", "Wiki Tiki"}) {
			assertTrue(rare.contains(name), name);
			assertNotNull(SeaCreatures.named(name), name);
		}
		// the name a creature had before still finds it, so what was counted under it is not lost
		assertEquals("The Loch Emperor", SeaCreatures.named("Sea Emperor").name());
		assertEquals("The Loch Emperor", SeaCreatures.nameNow("Sea Emperor"));
		assertEquals("Squid", SeaCreatures.nameNow("Squid"));
		assertEquals("Something Else", SeaCreatures.nameNow("Something Else"), "a name the list never had stays as saved");
		assertEquals(java.util.Map.of("Phlhlegblast", "Plhlegblast", "Sea Emperor", "The Loch Emperor", "The Sea Emperor", "The Loch Emperor"), SeaCreatures.renamed());
		// creatures the old list lacked are in the data
		for (String name : new String[] {"Bogged", "Wetwing", "Tadgang", "Inkling", "Jumpin' Jack", "Snapping Turtle", "Ent", "Manta Ray", "Nessie"}) {
			assertNotNull(SeaCreatures.named(name), name);
		}
		assertEquals("Tadgang", SeaCreatures.caughtIn("A gang of Liltads!").name());
		assertTrue(SeaCreatures.all().size() >= 89, "the whole data file is read");
		assertFalse(rare.contains("Squid"));
		assertFalse(rare.contains("Sea Leech"));
		// every creature has a place and a key of its own for its switch
		List<String> keys = new ArrayList<>();
		for (Creature creature : SeaCreatures.all()) {
			assertNotNull(creature.place());
			assertFalse(keys.contains(creature.key()), creature.key());
			keys.add(creature.key());
		}
		assertEquals("lord_jawbus", SeaCreatures.named("Lord Jawbus").key());
	}

	@Test
	void thePartyMessage() {
		String coords = FishingAlerts.coords(12, 70, -3);
		assertEquals("Thunder caught! x: 12, y: 70, z: -3", FishingAlerts.message(FishingAlerts.DEFAULT_MESSAGE, "Thunder", coords, false));
		assertEquals("Double Hook! Thunder caught! x: 12, y: 70, z: -3", FishingAlerts.message(FishingAlerts.DEFAULT_MESSAGE, "Thunder", coords, true));
		// without the coordinates nothing is left of their place
		assertEquals("Thunder caught!", FishingAlerts.message(FishingAlerts.DEFAULT_MESSAGE, "Thunder", "", false));
		assertEquals("Got a Yeti at x: 12, y: 70, z: -3", FishingAlerts.message("Got a {creature} at {coords}", "Yeti", coords, false));
		// an empty text is the shipped one; a long one is cut
		assertEquals("Thunder caught!", FishingAlerts.message(" ", "Thunder", "", false));
		assertTrue(FishingAlerts.message("{creature} " + "!".repeat(300), "Thunder", "", false).length() <= FishingAlerts.MOST_MESSAGE);
		// the place is in a form the chat waypoints read
		ChatCoords.Found found = ChatCoords.inChat(FishingAlerts.message(FishingAlerts.DEFAULT_MESSAGE, "Thunder", coords, false));
		assertNotNull(found);
		assertEquals(12, found.x());
		assertEquals(70, found.y());
		assertEquals(-3, found.z());
	}

	@Test
	void sharesFromChat() {
		FishingAlerts.Share ours = FishingAlerts.share("Party > [MVP+] Kiwi: Double Hook! Thunder caught! x: 12, y: 70, z: -3", RARE);
		assertEquals(FishingAlerts.Chat.PARTY, ours.chat());
		assertEquals("Kiwi", ours.player());
		assertEquals("Thunder", ours.creature().name());
		assertTrue(ours.doubleHook());
		assertEquals(12, ours.at().x());
		// without coordinates, and other usual forms
		FishingAlerts.Share plain = FishingAlerts.share("Party > Ash: Lord Jawbus caught!", RARE);
		assertEquals("Lord Jawbus", plain.creature().name());
		assertNull(plain.at());
		assertFalse(plain.doubleHook());
		assertEquals("Yeti", FishingAlerts.share("Co-op > [VIP] Rae: I caught a yeti at x: 5, y: 76, z: 40", RARE).creature().name());
		assertEquals(FishingAlerts.Chat.COOP, FishingAlerts.share("Co-op > [VIP] Rae: I caught a yeti at x: 5, y: 76, z: 40", RARE).chat());
		assertEquals("Thunder", FishingAlerts.share("Party > Ash: x: -360, y: 80, z: -700 | Thunder", RARE).creature().name());
		assertEquals(FishingAlerts.Chat.GUILD, FishingAlerts.share("Guild > [MVP++] Nox [Officer]: Reindrake spawned", RARE).chat());
		assertEquals(FishingAlerts.Chat.PUBLIC, FishingAlerts.share("[312] [MVP+] Sol: grim reaper caught, come", RARE).chat());
		// the form SkyHanni sends
		FishingAlerts.Share skyHanni = FishingAlerts.share("Party > [MVP+] Kiwi: DOUBLE HOOK: I caught a Thunder!", RARE);
		assertEquals("Thunder", skyHanni.creature().name());
		assertTrue(skyHanni.doubleHook());
		assertEquals("Abyssal Miner", FishingAlerts.share("Party > Ash: I caught an Abyssal Miner!", RARE).creature().name());
		// the longest name wins
		assertEquals("Great White Shark", FishingAlerts.share("Party > Ash: Great White Shark caught!", RARE).creature().name());
	}

	@Test
	void whatIsNoShare() {
		// talk about a creature, a creature that is not on the list, a name inside another word, and lines of the game
		assertNull(FishingAlerts.share("Party > Ash: anyone need thunder kills?", RARE));
		assertNull(FishingAlerts.share("Party > Ash: Squid caught!", RARE));
		assertNull(FishingAlerts.share("Party > Ash: yetis caught!", RARE));
		assertNull(FishingAlerts.share("You hear a massive rumble as Thunder emerges.", RARE));
		assertNull(FishingAlerts.share("Party > Ash: Thunder caught!", creature -> false));
		assertEquals("Squid", FishingAlerts.share("Party > Ash: Squid caught!", creature -> true).creature().name());
	}

	@Test
	void nameTagsNearby() {
		assertEquals("Thunder", FishingAlerts.inTag("[Lv400] Thunder 35M/35M❤", RARE).name());
		assertEquals("Lord Jawbus", FishingAlerts.inTag("﴾ [Lv600] Lord Jawbus 100M/100M❤ ﴿", RARE).name());
		// a tag without a level is a player or something else
		assertNull(FishingAlerts.inTag("Thunder", RARE));
		assertNull(FishingAlerts.inTag("[MVP+] ThunderFan", RARE));
		assertNull(FishingAlerts.inTag("[Lv20] Squid 2k/2k❤", RARE));
		assertTrue(FishingAlerts.within(30, 5, 40, 60));
		assertFalse(FishingAlerts.within(50, 5, 40, 60));
		assertTrue(FishingAlerts.within(0, 0, 60, 60));
	}

	@Test
	void oneAlertPerCreature() {
		Creature thunder = SeaCreatures.named("Thunder");
		Creature yeti = SeaCreatures.named("Yeti");
		FishingAlerts.Seen seen = new FishingAlerts.Seen();
		// the player's own catch: the creature nearby right after is that one
		seen.ownCatch(thunder, 1_000);
		assertFalse(seen.nearby(7, thunder, 3_000));
		// another one, later, is someone else's; each entity once
		assertTrue(seen.nearby(8, thunder, 1_000 + FishingAlerts.Seen.OWN_MILLIS + 1));
		assertFalse(seen.nearby(8, thunder, 60_000));
		// shared in chat first: its name tag is no second alert
		assertTrue(seen.chat("Kiwi", yeti, 100_000));
		assertFalse(seen.nearby(9, yeti, 105_000));
		// the same share again is one alert; another player's is its own
		assertFalse(seen.chat("kiwi", yeti, 110_000));
		assertTrue(seen.chat("Ash", yeti, 110_000));
		assertTrue(seen.chat("Kiwi", yeti, 100_000 + FishingAlerts.Seen.CHAT_MILLIS));
		// seen nearby first, then shared: the share is still told (it has the player's name)
		assertTrue(seen.nearby(10, SeaCreatures.named("Reindrake"), 200_000));
		seen.clear();
		assertTrue(seen.nearby(7, thunder, 3_000));
	}

	@Test
	void severalAlertsAreShownOneAfterTheOther() {
		FishingAlerts.Queue queue = new FishingAlerts.Queue();
		queue.add(new FishingAlerts.Alert("Thunder", "", true));
		queue.add(new FishingAlerts.Alert("Kiwi: Yeti", "", false));
		assertEquals("Thunder", queue.poll(10_000).title());
		assertNull(queue.poll(10_500));
		assertEquals("Kiwi: Yeti", queue.poll(10_000 + FishingAlerts.Queue.EACH_MILLIS).title());
		assertNull(queue.poll(99_000));
		for (int i = 0; i < 20; i++) {
			queue.add(new FishingAlerts.Alert("x" + i, "", false));
		}
		int shown = 0;
		for (long now = 200_000; queue.poll(now) != null; now += FishingAlerts.Queue.EACH_MILLIS) {
			shown++;
		}
		assertEquals(FishingAlerts.Queue.MOST_WAITING, shown);
	}
}
