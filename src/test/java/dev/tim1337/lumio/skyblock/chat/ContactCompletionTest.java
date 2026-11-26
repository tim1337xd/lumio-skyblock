package dev.tim1337.lumio.skyblock.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** /call takes Abiphone contacts: matched by prefix, then a word's start, then anywhere, the player's own first among equals, never more than eight. */
class ContactCompletionTest {
	private static final List<ContactCompletion.Contact> CONTACTS = List.of(
		contact("Kat"), contact("Kat's Assistant"), contact("Marco"), contact("Elizabeth", "community_shop", "elizabeth"), contact("Maddox the Slayer"),
		contact("Mad Scientist"), contact("Crag", "kuudra"), contact("Captain Ahone", "ahone"), contact("Jacob"), contact("Anita"), contact("Malik"),
		contact("Marigold"), contact("Maxwell"), contact("Moby"), contact("Mort"), contact("Miria"), contact("Rosemary"));

	private static ContactCompletion.Contact contact(String name, String... calls) {
		return new ContactCompletion.Contact(name, List.of(calls));
	}

	private static List<String> texts(String typed, Set<String> own) {
		List<String> out = new ArrayList<>();
		for (ContactCompletion.Contact contact : ContactCompletion.order(typed, CONTACTS, own)) {
			out.add(contact.callText());
		}
		return out;
	}

	@Test
	void namesThatStartWithTheTextComeFirst() {
		assertEquals(List.of("Kat", "Kat's Assistant"), texts("ka", Set.of()));
		assertEquals(List.of("Marco", "Marigold", "Rosemary"), texts("MAR", Set.of()));
	}

	@Test
	void theMiddleOfANameAndItsWords() {
		// "liz" is inside Elizabeth; "slay" starts a word of Maddox the Slayer; "sci" one of Mad Scientist
		assertEquals(List.of("Elizabeth"), texts("liz", Set.of()));
		assertEquals(List.of("Maddox the Slayer"), texts("slay", Set.of()));
		assertEquals(List.of("Maddox the Slayer", "Mad Scientist"), texts("mad", Set.of()));
		assertEquals(List.of("Mad Scientist"), texts("mad sci", Set.of()));
	}

	@Test
	void theNameCallTakesIsInserted() {
		// Crag is called as "kuudra", Captain Ahone as "ahone"; Elizabeth keeps her name, which is one of her call names
		assertEquals(List.of("kuudra"), texts("crag", Set.of()));
		assertEquals(List.of("kuudra"), texts("kuu", Set.of()));
		assertEquals(List.of("ahone"), texts("capt", Set.of()));
		assertEquals(List.of("Elizabeth"), texts("community", Set.of()));
	}

	@Test
	void ownContactsComeFirstAmongEquals() {
		assertEquals(List.of("Marigold", "Marco", "Rosemary"), texts("mar", Set.of("Marigold")));
		// but a better match stays ahead: these start with "ma", Rosemary only holds it
		assertEquals(List.of("Marco", "Maddox the Slayer", "Mad Scientist", "Malik", "Marigold", "Maxwell", "Rosemary"), texts("ma", Set.of("Rosemary")));
	}

	@Test
	void unknownTextGivesNothingAndTheListIsShort() {
		assertTrue(texts("zzz", Set.of()).isEmpty());
		assertTrue(texts("xq", Set.of("Kat")).isEmpty());
		assertEquals(ContactCompletion.MOST, texts("m", Set.of()).size());
	}
}
