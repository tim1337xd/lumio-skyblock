package dev.tim1337.lumio.skyblock.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Abiphone contacts /call takes, ranked for what was typed: a name that starts with it first, then one with a word that starts with it ("mad"
 * for "Maddox the Slayer"), then one that holds it ("liz" for "Elizabeth"); among equally good ones the player's own contacts come first. Each
 * contact is matched by its name and by the name /call knows it by ("kuudra" for Crag). Plain strings, so it is unit tested; the same matching as
 * SkyHanni's /call completion, which reads the same list of contacts.
 */
public final class ContactCompletion {
	/** The most contacts shown. */
	public static final int MOST = 8;

	/**
	 * A contact: its name as the Abiphone lists it, and the names /call knows it by in lower case (empty when that is its name).
	 */
	public record Contact(String name, List<String> callNames) {
		public Contact {
			callNames = List.copyOf(callNames);
		}

		/** @return what /call takes for this contact: its name as listed, or the call name the server knows when that differs from the name */
		public String callText() {
			String lower = name.toLowerCase(Locale.ROOT);
			if (callNames.isEmpty() || callNames.contains(lower)) {
				return name;
			}
			return callNames.getFirst();
		}

		/** @return how well the contact matches, by its name or a call name: 0 best, {@link CommandCompletion#NO_MATCH} when not at all */
		int rank(String typed) {
			int best = CommandCompletion.rank(name, typed);
			for (String call : callNames) {
				int rank = CommandCompletion.rank(call, typed);
				if (rank != CommandCompletion.NO_MATCH && (best == CommandCompletion.NO_MATCH || rank < best)) {
					best = rank;
				}
			}
			return best;
		}
	}

	/** A contact that matched, with how well and whether it is the player's own. */
	private record Match(Contact contact, int rank, boolean own, int order) {
	}

	private ContactCompletion() {
	}

	/**
	 * @param all the contacts there are, in the order to keep among equals
	 * @param own the names of the player's own contacts, as the Abiphone lists them; empty when not known
	 * @return the contacts that match the typed text, best first, at most {@link #MOST}; nothing when none matches
	 */
	public static List<Contact> order(String typed, List<Contact> all, Set<String> own) {
		Set<String> ownLower = new HashSet<>();
		for (String name : own) {
			ownLower.add(name.toLowerCase(Locale.ROOT));
		}
		String text = typed.strip();
		List<Match> matches = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < all.size(); i++) {
			Contact contact = all.get(i);
			int rank = contact.rank(text);
			if (rank != CommandCompletion.NO_MATCH && seen.add(contact.callText().toLowerCase(Locale.ROOT))) {
				matches.add(new Match(contact, rank, ownLower.contains(contact.name().toLowerCase(Locale.ROOT)), i));
			}
		}
		matches.sort(Comparator.comparingInt(Match::rank).thenComparing(match -> !match.own()).thenComparingInt(Match::order));
		List<Contact> out = new ArrayList<>();
		for (Match match : matches) {
			if (out.size() >= MOST) {
				break;
			}
			out.add(match.contact());
		}
		return out;
	}
}
