package dev.tim1337.lumio.skyblock.slayer;

import dev.tim1337.lumio.skyblock.data.DropMessage;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Decides whether an item on the ground is a drop: the server announced it in the chat, before or after it appeared, or it is a slayer drop that appeared where and when the boss died. An item is asked about
 * again whenever something changes, because on the server its real name comes after the item, and the chat line or the boss's death can come later still. Plain text and numbers, so it is unit tested.
 */
final class DropRecognizer {
	/** How far apart a chat line and the item it is about can be, either way round, in milliseconds. */
	static final long ANNOUNCED_MILLIS = 20_000L;
	private static final int MOST_ANNOUNCED = 32;
	private static final String BOOK_KEY = "enchanted book";

	/** What is decided about an item: whether it is a drop, and why. */
	enum Verdict {
		ANNOUNCED(true, "the chat announced it"),
		BOSS_DROP(true, "a slayer drop where the boss died"),
		NOT_A_DROP(false, "no chat line about it, and no slayer drop"),
		NO_BOSS_DIED(false, "a slayer drop's name, but no boss of yours died"),
		TOO_FAR(false, "a slayer drop's name, too far from where the boss died"),
		OTHER_TIME(false, "a slayer drop's name, not around the time the boss died");

		final boolean drop;
		final String why;

		Verdict(boolean drop, String why) {
			this.drop = drop;
			this.why = why;
		}
	}

	private record Announced(String key, String shortKey, boolean book, long millis) {
	}

	private final ArrayDeque<Announced> announced = new ArrayDeque<>();
	private int changes;

	/** Remembers a drop the chat announced. */
	void announce(DropMessage drop, long now) {
		if (announced.size() >= MOST_ANNOUNCED) {
			announced.removeFirst();
		}
		String key = BossDrops.nameKey(drop.name());
		announced.addLast(new Announced(key, BossDrops.withoutLevel(key), drop.book(), now));
		changes++;
	}

	/** @return a number that changes with every announcement: what was decided before may be decided differently now */
	int changes() {
		return changes;
	}

	void forget() {
		announced.clear();
		changes++;
	}

	/**
	 * @param name        the item's name as it shows, any format; empty while it has none
	 * @param id          its SkyBlock id, or null without one
	 * @param appeared    when the item appeared
	 * @param blocks      how far from where the boss died it appeared
	 * @param death       when a watched boss last died, or 0
	 */
	Verdict judge(String name, String id, long appeared, double blocks, long death) {
		String key = BossDrops.nameKey(name);
		String shortKey = BossDrops.withoutLevel(key);
		String idKey = id == null ? "" : id.toLowerCase(Locale.ROOT).replace('_', ' ');
		for (Announced said : announced) {
			if (Math.abs(said.millis() - appeared) <= ANNOUNCED_MILLIS && matches(said, key, shortKey, idKey)) {
				return Verdict.ANNOUNCED;
			}
		}
		if (!BossDrops.onList(name) && !BossDrops.onList(idKey)) {
			return Verdict.NOT_A_DROP;
		}
		if (death == 0L) {
			return Verdict.NO_BOSS_DIED;
		}
		if (blocks > BossDrops.NEAR_BLOCKS) {
			return Verdict.TOO_FAR;
		}
		return BossDrops.counts(blocks, appeared - death, true) ? Verdict.BOSS_DROP : Verdict.OTHER_TIME;
	}

	/** On the ground a book is called only "Enchanted Book", whatever is in it; a name may or may not have its level. */
	private static boolean matches(Announced said, String key, String shortKey, String idKey) {
		if (said.book() && (key.equals(BOOK_KEY) || idKey.equals(BOOK_KEY))) {
			return true;
		}
		return !key.isEmpty() && (key.equals(said.key()) || shortKey.equals(said.shortKey())) || !idKey.isEmpty() && (idKey.equals(said.key()) || idKey.equals(said.shortKey()));
	}
}
