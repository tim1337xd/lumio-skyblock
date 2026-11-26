package dev.tim1337.lumio.skyblock.data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Tells what really came in from outside from what only changed its form or place among the player's own items. Every change of the inventory and every line
 * of the sack summaries passes here: what left recently is kept for a while, and what then arrives is first matched against it, as the same item (moved between
 * the inventory and the sacks, dropped and picked up) or by a crafting recipe (crafted, compacted or taken apart). Only the rest is a gain. Where a recipe's
 * ingredients were only partly there, the missing ones are what was really gained: a Personal Compactor turns the 80th Rough gem just picked up into a Flawed one
 * before it is ever seen. Sack summaries come up to 30 seconds late, so what they say was taken out can also take back a gain already counted. Plain logic,
 * tested without the game.
 */
final class ItemFlow {
	/** How long what left can still explain what arrives: a sack summary covers 30 seconds and comes a little late. */
	static final long WINDOW_MILLIS = 40_000L;
	/** A recipe counts as what happened only if at least this share of its ingredients really left. */
	private static final double LEAST_COVERED = 0.5;
	/** How deep ingredients are followed: Rough to Flawed to Fine in one go. */
	private static final int MOST_DEPTH = 4;
	/** The most crafts looked at for one arrival. */
	private static final int MOST_CRAFTS = 512;

	/** An amount of an item in a recipe. */
	record Part(String id, long count) {
	}

	/** A way to craft an item: how many one craft makes, of what. */
	record Way(long makes, List<Part> parts) {
	}

	/** Knows the crafting recipes. */
	interface Recipes {
		List<Way> of(String id);
	}

	/** Receives what the flow finds. */
	interface Out {
		void gained(String id, long count, boolean sacks);

		/** A gain counted before turned out to be none. */
		void taken(String id, long count);
	}

	/** An amount of what left, or of a gain lately counted, and when. */
	private static final class Recent {
		final String id;
		long count;
		long at;

		Recent(String id, long count, long at) {
			this.id = id;
			this.count = count;
			this.at = at;
		}
	}

	private final Recipes recipes;
	private final Out out;
	private final Map<String, Recent> gone = new HashMap<>();
	private final Deque<Recent> counted = new ArrayDeque<>();

	ItemFlow(Recipes recipes, Out out) {
		this.recipes = recipes;
		this.out = out;
	}

	/** Items left the inventory. */
	void left(String id, long count, long now) {
		leave(id, count, now);
	}

	/**
	 * Items a Personal Deletor took out of the inventory: they leave the profit again only as far as they were counted lately; what was never
	 * counted takes nothing. They are gone for good, so they explain nothing that arrives later. Not called yet: which items a deletor deletes
	 * is read from its item data once that data is known from the game.
	 */
	void deleted(String id, long count, long now) {
		forget(now);
		takeBack(id, count);
	}

	/** Items landed in the inventory. Call after {@link #left} for the same look, so a craft within it is seen whole. */
	void landed(String id, long count, long now) {
		forget(now);
		long rest = arrive(id, count, now);
		if (rest > 0) {
			count(id, rest, false, now);
		}
	}

	/** One sack summary: what went in (positive) and what was taken out (negative), by item. */
	void sacks(Map<String, Long> changes, long now) {
		forget(now);
		for (Map.Entry<String, Long> change : changes.entrySet()) {
			if (change.getValue() < 0) {
				// taken out of the sacks: what landed in the inventory lately was this, no gain
				long rest = takeBack(change.getKey(), -change.getValue());
				leave(change.getKey(), rest, now);
			}
		}
		rethinkCounted(now);
		for (Map.Entry<String, Long> change : changes.entrySet()) {
			if (change.getValue() > 0) {
				long rest = arrive(change.getKey(), change.getValue(), now);
				if (rest > 0) {
					out.gained(change.getKey(), rest, true);
				}
			}
		}
	}

	/** Counts a gain from the inventory, and keeps it for a while so a later sack summary or deletion can take it back. */
	private void count(String id, long count, boolean sacks, long now) {
		counted.addLast(new Recent(id, count, now));
		out.gained(id, count, sacks);
	}

	private void leave(String id, long count, long now) {
		if (count > 0) {
			Recent known = gone.computeIfAbsent(id, key -> new Recent(key, 0L, now));
			known.count += count;
			known.at = now;
		}
	}

	private void forget(long now) {
		gone.values().removeIf(recent -> recent.count <= 0 || now - recent.at > WINDOW_MILLIS);
		counted.removeIf(recent -> recent.count <= 0 || now - recent.at > WINDOW_MILLIS);
	}

	private long takeBack(String id, long count) {
		long rest = count;
		for (Iterator<Recent> it = counted.descendingIterator(); it.hasNext() && rest > 0; ) {
			Recent gain = it.next();
			if (gain.id.equals(id)) {
				long taken = Math.min(rest, gain.count);
				gain.count -= taken;
				rest -= taken;
				out.taken(id, taken);
			}
		}
		return rest;
	}

	/** Ingredients the sacks gave up may have made what landed before the summary came. */
	private void rethinkCounted(long now) {
		if (gone.isEmpty()) {
			return;
		}
		// a copy: what a craft finds missing is counted, and kept, as it goes
		for (Recent gain : new ArrayList<>(counted)) {
			long rest = crafted(gain.id, gain.count, false, now);
			if (rest < gain.count) {
				out.taken(gain.id, gain.count - rest);
				gain.count = rest;
			}
		}
	}

	/** @return how much of what arrived is new */
	private long arrive(String id, long count, long now) {
		if (gone.isEmpty()) {
			return count;
		}
		long rest = count - take(gone, id, count);
		rest = rest > 0 ? crafted(id, rest, true, now) : 0;
		return rest > 0 ? takenApart(id, rest) : 0;
	}

	private static long take(Map<String, Recent> from, String id, long count) {
		Recent known = from.get(id);
		if (known == null) {
			return 0;
		}
		long taken = Math.min(count, known.count);
		known.count -= taken;
		return taken;
	}

	/** @return how much of an item is left once what its recipes made of what left is taken away */
	private long crafted(String id, long count, boolean sacksOfMissing, long now) {
		long rest = count;
		for (Way way : recipes.of(id)) {
			int crafts = 0;
			while (rest > 0 && crafts++ < MOST_CRAFTS) {
				Map<String, Long> used = new HashMap<>();
				Map<String, Long> missing = new HashMap<>();
				for (Part part : way.parts()) {
					need(part.id(), part.count(), used, missing, 0);
				}
				long had = sum(used);
				long lacking = sum(missing);
				if (had == 0 || had < LEAST_COVERED * (had + lacking)) {
					break;
				}
				used.forEach((item, amount) -> take(gone, item, amount));
				rest -= Math.min(rest, Math.max(1L, way.makes()));
				// what was not there before came from outside, and went straight into the craft
				missing.forEach((item, amount) -> count(item, amount, sacksOfMissing, now));
			}
		}
		return rest;
	}

	/** Finds an amount among what left, by this frame's plan: from itself, else from what it is crafted from. */
	private void need(String id, long count, Map<String, Long> used, Map<String, Long> missing, int depth) {
		Recent known = gone.get(id);
		long free = known == null ? 0 : known.count - used.getOrDefault(id, 0L);
		long have = Math.clamp(free, 0L, count);
		if (have > 0) {
			used.merge(id, have, Long::sum);
		}
		long rest = count - have;
		if (rest <= 0) {
			return;
		}
		List<Way> ways = depth < MOST_DEPTH ? recipes.of(id) : List.of();
		if (ways.isEmpty() || !anyLeft(ways.getFirst(), depth + 1)) {
			missing.merge(id, rest, Long::sum);
			return;
		}
		Way way = ways.getFirst();
		long crafts = (rest + way.makes() - 1) / Math.max(1L, way.makes());
		for (Part part : way.parts()) {
			need(part.id(), part.count() * crafts, used, missing, depth + 1);
		}
	}

	private boolean anyLeft(Way way, int depth) {
		for (Part part : way.parts()) {
			Recent known = gone.get(part.id());
			if (known != null && known.count > 0) {
				return true;
			}
			if (depth < MOST_DEPTH) {
				for (Way deeper : recipes.of(part.id())) {
					if (anyLeft(deeper, depth + 1)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** @return how much of an item is left once what came of taking apart something that left is taken away */
	private long takenApart(String id, long count) {
		long rest = count;
		for (Recent left : new ArrayList<>(gone.values())) {
			for (Way way : recipes.of(left.id)) {
				long each = 0;
				for (Part part : way.parts()) {
					each += part.id().equals(id) ? part.count() : 0;
				}
				long makes = Math.max(1L, way.makes());
				if (each > 0 && rest > 0 && left.count >= makes) {
					long units = Math.min((rest + each - 1) / each, left.count / makes);
					left.count -= units * makes;
					rest -= Math.min(rest, units * each);
				}
			}
		}
		return rest;
	}

	private static long sum(Map<String, Long> amounts) {
		long total = 0;
		for (long amount : amounts.values()) {
			total += amount;
		}
		return total;
	}
}
