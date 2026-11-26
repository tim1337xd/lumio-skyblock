package dev.tim1337.lumio.skyblock.data;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Drops the server announces in chat (a rare drop, an RNG meter drop, a loot share) matched with the item that arrives, so each counts once and in
 * the activity it belongs to, also when the kill was not the player's. The line and the item come within moments of each other, in either order:
 * an item landing in the inventory waits a moment for its line, and a line waits a moment for its item. A drop whose item never shows (a full
 * inventory sends it to the stash) counts from the line alone; a sack summary that later reports the same item counts nothing more. An item that
 * no line announced goes on as before (the activity's own gains, crafting and moving told apart there). "LOOT SHARE You received loot …" names
 * nothing: what lands in the inventory right after it is that loot. What else changed meanwhile (items that left, a sack summary) goes on through
 * {@link #inOrder} only after the items held before it, so the next step sees everything in the order it happened: a compactor that turns held items into
 * their enchanted form is seen as the items arriving, then leaving, then the enchanted one arriving. Plain logic, tested without the game.
 */
final class AnnouncedDrops {
	/** How long an item landing in the inventory waits for a line that announces it. */
	static final long HOLD_MILLIS = 1_500L;
	/** How long an announced drop waits for its item before it counts from the line alone. */
	static final long ARRIVE_MILLIS = 4_000L;
	/** How long after counting from the line a sack summary may still report the same item: sacks report every 30 seconds, a little late. */
	static final long SACK_MILLIS = 45_000L;
	/** How long after "You received loot …" what lands in the inventory is that loot. */
	static final long SHARE_MILLIS = 3_000L;
	/** Two lines for the same item this close are one drop told twice (a Garden drop comes as two lines in the same moment). */
	static final long SAME_DROP_MILLIS = 300L;

	/** Receives what is counted. */
	interface Out {
		/** An item no line announced: it goes on as any gain. */
		void arrived(String key, long count);

		/**
		 * An announced drop, or loot of a loot share.
		 *
		 * @param name     its name from the line, or null to use the item's own
		 * @param fromChat whether its item never showed and it counts from the line alone
		 */
		void dropped(String key, String name, long count, boolean fromChat);
	}

	private static final class Pending {
		final String key;
		final String name;
		long count;
		final long at;
		/** What happened after this item landed and waits for it: it goes on right after the item does, or is dropped. */
		List<Runnable> after;

		Pending(String key, String name, long count, long at) {
			this.key = key;
			this.name = name;
			this.count = count;
			this.at = at;
		}
	}

	private final Out out;
	private final List<Pending> announced = new ArrayList<>();
	private final List<Pending> held = new ArrayList<>();
	/** Drops counted from the line whose item a sack summary may still report. */
	private final List<Pending> countedFromChat = new ArrayList<>();
	private long shareUntil;

	AnnouncedDrops(Out out) {
		this.out = out;
	}

	/** A line announced a drop: it claims an item that just landed, or waits for one. */
	void announce(String key, String name, long count, long now) {
		if (key == null || count <= 0) {
			return;
		}
		for (Pending line : announced) {
			if (line.key.equals(key) && now - line.at < SAME_DROP_MILLIS) {
				return;
			}
		}
		long rest = count;
		for (int i = 0; i < held.size() && rest > 0; i++) {
			Pending item = held.get(i);
			if (item.key.equals(key)) {
				long taken = Math.min(rest, item.count);
				item.count -= taken;
				rest -= taken;
				out.dropped(key, name, taken, false);
				if (item.count <= 0) {
					release(i--);
				}
			}
		}
		announced.add(new Pending(key, name, rest, now));
	}

	/** "LOOT SHARE You received loot …": what lands in the inventory right after it is that loot. */
	void lootShare(long now) {
		shareUntil = now + SHARE_MILLIS;
		// what landed a moment before the line is that loot too
		while (!held.isEmpty()) {
			Pending item = held.getFirst();
			out.dropped(item.key, null, item.count, false);
			release(0);
		}
	}

	/**
	 * Runs a step of what happened (items that left, a sack summary) once every item that landed before it has gone on: right away when nothing is
	 * held. Seen out of order, items that land and are compacted within a moment would look moved, and their enchanted form new.
	 */
	void inOrder(Runnable step) {
		if (held.isEmpty()) {
			step.run();
			return;
		}
		Pending last = held.getLast();
		if (last.after == null) {
			last.after = new ArrayList<>(2);
		}
		last.after.add(step);
	}

	/** Takes a held item out (gone on, or counted as a drop): what waited for it waits for the item before it, or runs when none is left. */
	private void release(int index) {
		Pending item = held.remove(index);
		if (item.after == null) {
			return;
		}
		if (index > 0) {
			Pending before = held.get(index - 1);
			if (before.after == null) {
				before.after = new ArrayList<>(item.after.size());
			}
			before.after.addAll(item.after);
			return;
		}
		for (Runnable step : item.after) {
			step.run();
		}
	}

	/** An item landed in the inventory. */
	void landed(String key, long count, long now) {
		long rest = claim(key, count);
		if (rest <= 0) {
			return;
		}
		if (now < shareUntil) {
			out.dropped(key, null, rest, false);
			return;
		}
		held.add(new Pending(key, null, rest, now));
	}

	/** @return how much of what a sack summary reports went in as no announced drop: the rest was one, counted here or already from its line */
	long intoSacks(String key, long count) {
		long rest = claim(key, count);
		for (Iterator<Pending> it = countedFromChat.iterator(); it.hasNext() && rest > 0; ) {
			Pending drop = it.next();
			if (drop.key.equals(key)) {
				long taken = Math.min(rest, drop.count);
				drop.count -= taken;
				rest -= taken;
				if (drop.count <= 0) {
					it.remove();
				}
			}
		}
		return rest;
	}

	/** Every tick: items no line claimed go on, lines whose item never came count from the line. */
	void tick(long now) {
		// in the order they landed, each followed by what happened after it
		while (!held.isEmpty() && now - held.getFirst().at >= HOLD_MILLIS) {
			Pending item = held.getFirst();
			out.arrived(item.key, item.count);
			release(0);
		}
		for (Iterator<Pending> it = announced.iterator(); it.hasNext(); ) {
			Pending line = it.next();
			if (line.count <= 0) {
				if (now - line.at >= SAME_DROP_MILLIS) {
					it.remove();
				}
			} else if (now - line.at >= ARRIVE_MILLIS) {
				it.remove();
				out.dropped(line.key, line.name, line.count, true);
				countedFromChat.add(new Pending(line.key, line.name, line.count, now));
			}
		}
		countedFromChat.removeIf(drop -> drop.count <= 0 || now - drop.at > SACK_MILLIS);
	}

	/** @return how much of an arrival is not an announced drop; the announced part is counted as one */
	private long claim(String key, long count) {
		long rest = count;
		for (Pending line : announced) {
			if (rest <= 0) {
				break;
			}
			if (line.key.equals(key) && line.count > 0) {
				long taken = Math.min(rest, line.count);
				line.count -= taken;
				rest -= taken;
				out.dropped(key, line.name, taken, false);
			}
		}
		return rest;
	}
}
