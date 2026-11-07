package dev.tim1337.lumio.skyblock.slayer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which name tags are slayer minibosses, and whose quest one belongs to: recorded name tags (colors already removed) and made-up positions. */
class MinibossesTest {
	private static final List<Minibosses.Player> ME_AND_OTHER = List.of(new Minibosses.Player("Me", 0, 64, 0), new Minibosses.Player("Other", 20, 64, 0));

	@Test
	void minibossNameTagsAreFound() {
		assertEquals("Voidling Devotee", Minibosses.named("☠ Voidling Devotee 12M❤"));
		assertEquals("Revenant Sycophant", Minibosses.named("[Lv310] Revenant Sycophant 24,000❤"));
		assertEquals("Pack Enforcer", Minibosses.named("☠ Pack Enforcer 45k/45k❤"));
		assertEquals("Flare Demon", Minibosses.named("☠ Flare Demon 120M❤"));
	}

	@Test
	void otherMobsWithSimilarNamesAreNot() {
		assertNull(Minibosses.named("☠ Voidling Extremist 8M❤"), "an End mob, not a miniboss");
		assertNull(Minibosses.named("☠ Revenant Horror IV 400k❤"), "the slayer boss");
		assertNull(Minibosses.named("☠ Tarantula Broodfather III 960k❤"));
		assertNull(Minibosses.named("Voidling Devotee"), "the name alone, without health, is no mob tag (a chat line, a sign)");
		assertNull(Minibosses.named("☠ Voidling Devotees 1M❤"), "only the whole name");
	}

	@Test
	void theOwnMinibossIsWatched() {
		String owner = Minibosses.ownerOf(null, 2, 64, 1, ME_AND_OTHER);
		assertEquals("Me", owner);
		assertTrue(Minibosses.watched(owner, "me", true, ""));
		assertFalse(Minibosses.watched(owner, "Me", false, ""), "not during a quest of the own");
	}

	@Test
	void anotherPlayersMinibossNearbyIsIgnored() {
		String owner = Minibosses.ownerOf(null, 18, 64, 0, ME_AND_OTHER);
		assertEquals("Other", owner);
		assertFalse(Minibosses.watched(owner, "Me", true, ""));
		// a Spawned by tag says it outright, even right next to the player
		assertFalse(Minibosses.watched(Minibosses.ownerOf("Other", 1, 64, 0, ME_AND_OTHER), "Me", true, ""));
		// between two players it can't be told: no alert
		assertNull(Minibosses.ownerOf(null, 10, 64, 0, ME_AND_OTHER));
	}

	@Test
	void theCarriedPlayersMinibossIsWatched() {
		List<Minibosses.Player> players = List.of(new Minibosses.Player("Me", 0, 64, 0), new Minibosses.Player("Friend", 6, 64, 0));
		String owner = Minibosses.ownerOf(null, 7, 64, 0, players);
		assertEquals("Friend", owner);
		assertTrue(Minibosses.watched(owner, "Me", false, "friend"));
		assertFalse(Minibosses.watched(owner, "Me", true, ""), "without carry mode it is someone else's");
	}
}
