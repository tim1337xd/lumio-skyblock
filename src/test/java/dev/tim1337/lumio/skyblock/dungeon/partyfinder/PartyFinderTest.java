package dev.tim1337.lumio.skyblock.dungeon.partyfinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.skyblock.dungeon.DungeonClass;
import dev.tim1337.lumio.skyblock.dungeon.partyfinder.PartyFinderLore.Line;
import dev.tim1337.lumio.skyblock.dungeon.partyfinder.PartyFinderLore.Party;
import dev.tim1337.lumio.skyblock.dungeon.partyfinder.PartyFinderVerdict.Mark;
import dev.tim1337.lumio.skyblock.dungeon.partyfinder.PartyFinderVerdict.Me;
import dev.tim1337.lumio.skyblock.dungeon.partyfinder.PartyFinderVerdict.Taken;
import dev.tim1337.lumio.skyblock.dungeon.partyfinder.PartyFinderVerdict.Verdict;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/** The Party Finder Helper: a party read from sample lore, and green, yellow or red for each case. */
class PartyFinderTest {
	private static final Me MAGE_30 = new Me("Plum", DungeonClass.MAGE, 30, 28);

	private static List<Line> lore(String... lines) {
		List<Line> lore = new ArrayList<>();
		for (String line : lines) {
			boolean red = line.startsWith("!");
			lore.add(new Line(red ? line.substring(1) : line, red));
		}
		return lore;
	}

	/** A party as the menu shows it: three members, no mage. */
	private static Party f7() {
		return PartyFinderLore.read("Ash's Party", lore("Dungeon: The Catacombs", "Floor: Floor VII", "", "Note: s+ only, 2 tank", "Class Level Required: 25",
			"Dungeon Level Required: 30", "", "Members: ", " Ash: Archer (41)", " [MVP+] Kiwi: Berserk (38)", " Rae: Healer (33)", " Empty", " Empty", "", "Click to join!"));
	}

	@Test
	void readsTheDungeonFloorNoteLevelsAndMembers() {
		Party party = f7();
		assertEquals("Ash", party.leader());
		assertFalse(party.master());
		assertEquals(7, party.floor());
		assertEquals("F7", party.floorLabel());
		assertEquals("s+ only, 2 tank", party.note());
		assertEquals(25, party.classLevel());
		assertEquals(30, party.dungeonLevel());
		assertEquals(3, party.members().size());
		assertEquals("Kiwi", party.members().get(1).name());
		assertEquals(DungeonClass.BERSERK, party.members().get(1).dungeonClass());
		assertEquals(38, party.members().get(1).level());
		assertEquals(List.of(DungeonClass.MAGE, DungeonClass.TANK), party.missing());
		assertFalse(party.full());
		assertEquals("", party.refusal());
	}

	@Test
	void masterModeAndTheFloors() {
		Party master = PartyFinderLore.read("Nox's Party", lore("Dungeon: Master Mode The Catacombs", "Floor: Floor V", "Members: ", " Nox: Mage (50)"));
		assertTrue(master.master());
		assertEquals("M5", master.floorLabel());
		assertEquals(0, master.dungeonLevel());
		assertEquals("", master.note());
		assertEquals("E", PartyFinderLore.read("Nox's Party", lore("Dungeon: The Catacombs", "Floor: Entrance", " Nox: Mage (3)")).floorLabel());
		assertEquals("F3", PartyFinderLore.read("Nox's Party", lore("Dungeon: The Catacombs", "Floor: Floor III", " Nox: Mage (3)")).floorLabel());
		// a floor that was not read gives no label, and the party is still read
		assertEquals("", PartyFinderLore.read("Nox's Party", lore("Dungeon: The Catacombs", " Nox: Mage (3)")).floorLabel());
	}

	@Test
	void whatIsNoPartyIsNotRead() {
		assertNull(PartyFinderLore.read("Go Back", lore("To SkyBlock Menu")));
		assertNull(PartyFinderLore.read("Ash's Party", lore("Dungeon: The Catacombs", "Floor: Floor VII")));
		assertNull(PartyFinderLore.read("Ash's Party", lore(" Ash: Archer (41)")));
		// a class nobody has is no member line
		assertNull(PartyFinderLore.read("Ash's Party", lore("Dungeon: The Catacombs", " Ash: Wizard (41)")));
	}

	@Test
	void theSelectedClassComesFromTheClassItem() {
		assertEquals(DungeonClass.MAGE, PartyFinderLore.selectedClass(lore("View and select a dungeon class.", "", "Currently Selected: Mage", "", "Click to open!")));
		assertEquals(DungeonClass.NONE, PartyFinderLore.selectedClass(lore("View and select a dungeon class.")));
	}

	@Test
	void greenWhenThePlayerCanJoinAndTheClassIsFree() {
		Verdict verdict = PartyFinderVerdict.of(f7(), MAGE_30, Taken.YELLOW);
		assertEquals(Mark.GREEN, verdict.mark());
		assertEquals("", verdict.takenBy());
	}

	@Test
	void yellowRedOrIgnoredWhenTheClassIsTaken() {
		Me archer = new Me("Plum", DungeonClass.ARCHER, 40, 40);
		Verdict yellow = PartyFinderVerdict.of(f7(), archer, Taken.YELLOW);
		assertEquals(Mark.YELLOW, yellow.mark());
		assertEquals("Ash", yellow.takenBy());
		Verdict red = PartyFinderVerdict.of(f7(), archer, Taken.RED);
		assertEquals(Mark.RED, red.mark());
		assertEquals("Archer is taken by Ash", red.reason());
		assertEquals(Mark.GREEN, PartyFinderVerdict.of(f7(), archer, Taken.IGNORE).mark());
	}

	@Test
	void redWhenALevelIsTooLow() {
		Verdict cata = PartyFinderVerdict.of(f7(), new Me("Plum", DungeonClass.MAGE, 27, 40), Taken.YELLOW);
		assertEquals(Mark.RED, cata.mark());
		assertEquals("Needs Catacombs 30 (you: 27)", cata.reason());
		Verdict classLevel = PartyFinderVerdict.of(f7(), new Me("Plum", DungeonClass.MAGE, 35, 20), Taken.YELLOW);
		assertEquals(Mark.RED, classLevel.mark());
		assertEquals("Needs class level 25 (you: 20)", classLevel.reason());
	}

	@Test
	void anUnknownLevelNeverMakesAPartyRed() {
		assertEquals(Mark.GREEN, PartyFinderVerdict.of(f7(), new Me("Plum", DungeonClass.MAGE, -1, -1), Taken.YELLOW).mark());
		// and without a known class the party is green, with nobody named as taking it
		Verdict noClass = PartyFinderVerdict.of(f7(), new Me("Plum", DungeonClass.NONE, -1, -1), Taken.RED);
		assertEquals(Mark.GREEN, noClass.mark());
		assertEquals("", noClass.takenBy());
	}

	@Test
	void theGamesOwnRedLineWinsOverTheLevelsWeKnow() {
		Party party = PartyFinderLore.read("Ash's Party", lore("Dungeon: The Catacombs", "Floor: Floor VII", "Dungeon Level Required: 30", " Ash: Archer (41)",
			"!Requires Catacombs Level 30!"));
		Verdict verdict = PartyFinderVerdict.of(party, new Me("Plum", DungeonClass.MAGE, 45, 45), Taken.YELLOW);
		assertEquals(Mark.RED, verdict.mark());
		assertEquals("Requires Catacombs Level 30!", verdict.reason());
	}

	@Test
	void redWhenThePartyIsFull() {
		Party full = PartyFinderLore.read("Ash's Party", lore("Dungeon: The Catacombs", "Floor: Floor VII", " Ash: Archer (41)", " Kiwi: Berserk (38)", " Rae: Healer (33)",
			" Nox: Tank (30)", " Sol: Mage (36)"));
		assertTrue(full.full());
		Verdict verdict = PartyFinderVerdict.of(full, new Me("Plum", DungeonClass.NONE, -1, -1), Taken.YELLOW);
		assertEquals(Mark.RED, verdict.mark());
		assertEquals("The party is full", verdict.reason());
	}

	@Test
	void noColorForTheOwnPartyOrAHeadThatCouldNotBeRead() {
		assertEquals(Mark.NONE, PartyFinderVerdict.of(f7(), new Me("kiwi", DungeonClass.MAGE, 30, 30), Taken.YELLOW).mark());
		assertEquals(Mark.NONE, PartyFinderVerdict.of(f7(), new Me("Ash", DungeonClass.MAGE, 30, 30), Taken.YELLOW).mark());
		assertEquals(Mark.NONE, PartyFinderVerdict.of(null, MAGE_30, Taken.YELLOW).mark());
	}

	@Test
	void theOwnLevelsComeFromThePlayerListsDungeonsCardAndAreKeptPerProfile() {
		DungeonLevels levels = new DungeonLevels();
		assertEquals(DungeonLevels.UNKNOWN, levels.of("Peach").catacombs());
		assertTrue(levels.read("Peach", List.of("Area: Dungeon Hub", "", "Dungeons:", " Catacombs 27: 61.5%", " Mage 33", " Tank 50: MAX", "", "Pet:", " Tank 99")));
		assertEquals(27, levels.of("Peach").catacombs());
		assertEquals(33, levels.of("Peach").of(DungeonClass.MAGE));
		assertEquals(50, levels.of("Peach").of(DungeonClass.TANK));
		// what stands under another card is not a level, and a class never listed stays unknown
		assertEquals(DungeonLevels.UNKNOWN, levels.of("Peach").of(DungeonClass.HEALER));
		// the same again changes nothing; another profile has its own
		assertFalse(levels.read("Peach", List.of("Dungeons:", " Catacombs 27: 61.5%")));
		assertEquals(DungeonLevels.UNKNOWN, levels.of("Apple").catacombs());
		// without the card nothing is forgotten
		assertFalse(levels.read("Peach", List.of("Area: Hub", "Pet:", " [Lvl 100] Bat")));
		assertEquals(27, levels.of("Peach").catacombs());
		// remembered: written and read back
		DungeonLevels read = new DungeonLevels();
		read.load(levels.save());
		assertEquals(27, read.of("Peach").catacombs());
		assertEquals(50, read.of("Peach").of(DungeonClass.TANK));
		// and used for the red decision: Catacombs 27 against a party that needs 30
		Verdict verdict = PartyFinderVerdict.of(f7(), new Me("Plum", DungeonClass.MAGE, read.of("Peach").catacombs(), read.of("Peach").of(DungeonClass.MAGE)), Taken.YELLOW);
		assertEquals(Mark.RED, verdict.mark());
		assertEquals("Needs Catacombs 30 (you: 27)", verdict.reason());
		// nothing known: as before, never red for a level
		assertEquals(Mark.GREEN, PartyFinderVerdict.of(f7(), new Me("Plum", DungeonClass.MAGE, read.of("Apple").catacombs(), read.of("Apple").of(DungeonClass.MAGE)),
			Taken.YELLOW).mark());
	}

	@Test
	void theNotesFilter() {
		assertTrue(PartyFinderLore.noteHas("Carry 2m per run", "carry, selling"));
		assertTrue(PartyFinderLore.noteHas("SELLING f7 completions", "carry, selling"));
		assertFalse(PartyFinderLore.noteHas("s+ only", "carry, selling"));
		// shipped empty: nothing is dimmed
		assertFalse(PartyFinderLore.noteHas("Carry 2m per run", ""));
		assertFalse(PartyFinderLore.noteHas("Carry 2m per run", " , "));
		assertFalse(PartyFinderLore.noteHas("", "carry"));
	}
}
