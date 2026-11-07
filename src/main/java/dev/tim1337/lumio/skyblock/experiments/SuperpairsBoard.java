package dev.tim1337.lumio.skyblock.experiments;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the player has seen of a game of Superpairs: only cards the game has flipped in this game, never one it has not shown. A card that goes face down again is remembered; two remembered with one key are a known pair, with a
 * number; one alone was seen once; two face up stay so, as a collected pair. The payload is whatever draws the card. Plain state, so it is unit tested.
 */
public final class SuperpairsBoard<T> {
	/** What a slot is now, for its frame. */
	public enum Mark {
		NONE, SEEN_ONCE, KNOWN_PAIR, COLLECTED, POWERUP
	}

	/** A slot as the game shows it now. */
	public record Card<T>(int slot, boolean hidden, String key, boolean powerup, long xp, T payload) {
	}

	/** What is known of a slot. */
	public record Shown<T>(Mark mark, int pair, T payload, long xp, boolean faceDown) {
	}

	private final Map<Integer, Card<T>> remembered = new HashMap<>();
	private final Map<String, Integer> pairNumbers = new HashMap<>();
	private final Map<Integer, Shown<T>> shown = new LinkedHashMap<>();
	/** The slots whose card is face down in the last update. */
	private final Set<Integer> faceDownNow = new HashSet<>();
	private int collectedPairs;
	private long xpCollected;
	private boolean frozen;

	/** A new game starts. */
	public void clear() {
		remembered.clear();
		faceDownNow.clear();
		pairNumbers.clear();
		shown.clear();
		collectedPairs = 0;
		xpCollected = 0L;
		frozen = false;
	}

	/**
	 * Takes in the board as the game shows it.
	 *
	 * @param cards       the slots of the cards
	 * @param lastClicked the slot the player clicked last, or -1: a card just flipped is not collected for being the same as a collected pair
	 * @param clicksLeft  the clicks left, or -1. At the second look at none left the game shows the rest of the board, which is no longer the player's finding
	 */
	public void update(List<Card<T>> cards, int lastClicked, int clicksLeft) {
		boolean wasFrozen = frozen;
		frozen = clicksLeft == 0;
		if (frozen && wasFrozen) {
			return;
		}
		Map<String, List<Card<T>>> faceUp = new LinkedHashMap<>();
		List<Card<T>> hidden = new ArrayList<>();
		shown.clear();
		faceDownNow.clear();
		for (Card<T> card : cards) {
			if (card.hidden()) {
				hidden.add(card);
				faceDownNow.add(card.slot());
				continue;
			}
			remembered.put(card.slot(), card);
			if (card.powerup()) {
				shown.put(card.slot(), new Shown<>(Mark.POWERUP, 0, card.payload(), 0L, false));
			} else {
				faceUp.computeIfAbsent(card.key(), key -> new ArrayList<>()).add(card);
			}
		}
		collectedPairs = 0;
		xpCollected = 0L;
		List<Card<T>> open = new ArrayList<>();
		for (List<Card<T>> group : faceUp.values()) {
			Card<T> justFlipped = null;
			if (group.size() % 2 == 1) {
				justFlipped = group.stream().filter(card -> card.slot() == lastClicked).findFirst().orElse(group.getLast());
				open.add(justFlipped);
			}
			for (Card<T> card : group) {
				if (card != justFlipped) {
					shown.put(card.slot(), new Shown<>(Mark.COLLECTED, 0, card.payload(), card.xp(), false));
				}
			}
			int pairs = group.size() / 2;
			collectedPairs += pairs;
			xpCollected += pairs * group.getFirst().xp();
		}
		Map<String, List<Card<T>>> known = new LinkedHashMap<>();
		for (Card<T> open1 : open) {
			known.computeIfAbsent(open1.key(), key -> new ArrayList<>()).add(open1);
		}
		for (Card<T> card : hidden) {
			Card<T> seen = remembered.get(card.slot());
			if (seen != null && !seen.powerup()) {
				known.computeIfAbsent(seen.key(), key -> new ArrayList<>()).add(seen);
			}
		}
		for (Map.Entry<String, List<Card<T>>> group : known.entrySet()) {
			List<Card<T>> list = group.getValue();
			int number = list.size() >= 2 ? pairNumbers.computeIfAbsent(group.getKey(), key -> pairNumbers.size() + 1) : 0;
			for (int i = 0; i < list.size(); i++) {
				Card<T> card = list.get(i);
				boolean paired = i / 2 * 2 + 1 < list.size();
				boolean faceDown = !open.contains(card);
				if (paired) {
					shown.put(card.slot(), new Shown<>(Mark.KNOWN_PAIR, number, card.payload(), card.xp(), faceDown));
				} else {
					shown.put(card.slot(), new Shown<>(faceDown ? Mark.SEEN_ONCE : Mark.NONE, 0, card.payload(), card.xp(), faceDown));
				}
			}
		}
	}

	/** @return the card the game showed last on a slot that is face down now, or null: never one it has not shown */
	public Card<T> seenFaceDown(int slot) {
		return faceDownNow.contains(slot) ? remembered.get(slot) : null;
	}

	/** @return what draws the most valuable card seen and not collected yet, or null if there is none: what was never flipped cannot be known */
	public T bestStillOpen() {
		T best = null;
		long most = 0L;
		for (Shown<T> card : shown.values()) {
			if ((card.mark() == Mark.SEEN_ONCE || card.mark() == Mark.KNOWN_PAIR) && card.xp() > most) {
				most = card.xp();
				best = card.payload();
			}
		}
		return best;
	}

	/** @return what is known of a slot, or null if nothing is */
	public Shown<T> at(int slot) {
		return shown.get(slot);
	}

	public int collectedPairs() {
		return collectedPairs;
	}

	public long xpCollected() {
		return xpCollected;
	}
}
