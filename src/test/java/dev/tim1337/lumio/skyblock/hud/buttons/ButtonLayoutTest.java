package dev.tim1337.lumio.skyblock.hud.buttons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonParser;

import java.util.List;

/** Placing, moving, removing and duplicating buttons, Undo, saving and loading, the clipboard, per-profile layouts and the commands taken. */
class ButtonLayoutTest {
	private static final InventoryButton HUB = InventoryButton.of("/warp hub");
	private static final InventoryButton PETS = InventoryButton.of("/pets");

	@Test
	void knownCommandsGetTheirNameAndIcon() {
		assertEquals("Hub", HUB.name());
		assertEquals("Pets", PETS.name());
		assertEquals("Warp wizard", InventoryButton.of("/warp wizard").name());
		assertEquals(ButtonIcon.DEFAULT, InventoryButton.of("/warp wizard").icon());
	}

	@Test
	void placeMoveRemoveDuplicate() {
		ButtonLayout layout = new ButtonLayout();
		layout.set(0, HUB);
		layout.set(5, PETS);
		assertEquals(2, layout.used());
		assertEquals(1, layout.nextFree());
		layout.move(0, 5);
		assertEquals(PETS, layout.get(0), "they trade places");
		assertEquals(HUB, layout.get(5));
		assertEquals(1, layout.duplicate(5));
		assertEquals(HUB, layout.get(1));
		layout.set(1, null);
		assertNull(layout.get(1));
		assertNull(layout.get(99));
	}

	@Test
	void undoTakesBackStepByStep() {
		ButtonLayout layout = new ButtonLayout();
		assertFalse(layout.canUndo());
		layout.set(0, HUB);
		layout.move(0, 3);
		layout.clear();
		assertTrue(layout.isEmpty());
		layout.undo();
		assertEquals(HUB, layout.get(3));
		layout.undo();
		assertEquals(HUB, layout.get(0));
		layout.undo();
		assertTrue(layout.isEmpty());
		assertFalse(layout.canUndo());
	}

	@Test
	void undoKeepsItsLimit() {
		ButtonLayout layout = new ButtonLayout();
		for (int i = 0; i < ButtonLayout.MOST_UNDO + 10; i++) {
			layout.set(i % ButtonSpots.COUNT, (i + i / ButtonSpots.COUNT) % 2 == 0 ? HUB : PETS);
		}
		int steps = 0;
		while (layout.canUndo()) {
			layout.undo();
			steps++;
		}
		assertEquals(ButtonLayout.MOST_UNDO, steps);
	}

	@Test
	void saveAndLoad() {
		ButtonLayout layout = new ButtonLayout();
		layout.set(2, HUB.withShowIn(InventoryButton.ShowIn.CHESTS).withCloseAfter(true).withIcon(ButtonIcon.item("HYPERION")));
		layout.set(20, PETS.withOnlySkyBlock(false).withIcon(ButtonIcon.symbol("paw", 0x123456)));
		ButtonLayout read = new ButtonLayout();
		read.load(ButtonLayout.fromJson(JsonParser.parseString(layout.toJson().toString()).getAsJsonArray()));
		assertEquals(layout.placed(), read.placed());
		assertFalse(read.canUndo(), "loading is no step");
	}

	@Test
	void brokenSavedButtonsAreLeftOut() {
		String json = "[{\"spot\": 0, \"command\": \"/warp hub\"}, {\"spot\": 0, \"command\": \"/pets\"}, {\"spot\": 40, \"command\": \"/bz\"},"
			+ " {\"spot\": 1}, {\"spot\": 2, \"command\": \"\"}, {\"spot\": 3, \"command\": \"/a\\n/b\"}, {\"spot\": 4, \"command\": \"/ah\", \"icon\": \"symbol:nope:ffffff\"}]";
		List<ButtonLayout.Placed> read = ButtonLayout.fromJson(JsonParser.parseString(json).getAsJsonArray());
		assertEquals(2, read.size());
		assertEquals("/warp hub", read.get(0).button().command());
		assertEquals(ButtonIcon.DEFAULT, read.get(1).button().icon(), "an unknown icon falls back");
	}

	@Test
	void commandsAreCleaned() {
		assertEquals("/warp hub", InventoryButton.clean("  warp hub "));
		assertEquals("/pets", InventoryButton.clean("/ pets"));
		assertNull(InventoryButton.clean(""));
		assertNull(InventoryButton.clean("/"));
		assertNull(InventoryButton.clean("/pc hi\n/warp hub"), "never two commands");
		assertNull(InventoryButton.clean("/pc §chi"));
		assertNull(InventoryButton.clean("/" + "a".repeat(InventoryButton.MOST_COMMAND)));
	}

	@Test
	void clipboardRoundTrip() {
		ButtonLayout layout = new ButtonLayout();
		layout.set(0, HUB);
		layout.set(7, PETS.withIcon(ButtonIcon.item("minecraft:bone")));
		ButtonClipboard.Read read = ButtonClipboard.read(ButtonClipboard.write(layout));
		assertTrue(read.ok());
		assertEquals(layout.placed(), read.buttons());
	}

	@Test
	void brokenClipboardIsRefused() {
		assertFalse(ButtonClipboard.read(null).ok());
		assertFalse(ButtonClipboard.read("").ok());
		assertFalse(ButtonClipboard.read("[{\"spot\":0,\"command\":\"/warp hub\"}]").ok(), "no header");
		assertFalse(ButtonClipboard.read(ButtonClipboard.HEADER + "\n{nope").ok());
		assertFalse(ButtonClipboard.read(ButtonClipboard.HEADER + "\n{}").ok());
		assertFalse(ButtonClipboard.read(ButtonClipboard.HEADER + "\n[{\"spot\":0}]").ok(), "no valid button");
		assertFalse(ButtonClipboard.read(ButtonClipboard.HEADER + "\n" + "x".repeat(70_000)).ok());
	}

	@Test
	void pasteAddOrReplace() {
		ButtonLayout layout = new ButtonLayout();
		layout.set(0, PETS);
		List<ButtonLayout.Placed> pasted = List.of(new ButtonLayout.Placed(0, HUB), new ButtonLayout.Placed(1, HUB));
		assertEquals(2, layout.paste(pasted, false));
		assertEquals(PETS, layout.get(0), "add keeps what is there");
		assertEquals(HUB, layout.get(1));
		assertEquals(HUB, layout.get(2), "a taken spot moves to the next free one");
		layout.paste(pasted, true);
		assertEquals(2, layout.used());
		assertEquals(HUB, layout.get(0));
		layout.undo();
		assertEquals(3, layout.used(), "one step");
	}

	@Test
	void layoutsPerProfile() {
		ButtonLayouts layouts = new ButtonLayouts();
		layouts.current(false, "abc").set(0, HUB);
		assertEquals(HUB, layouts.current(false, "def").get(0), "one layout for all");
		assertTrue(layouts.current(true, "abc").isEmpty(), "a profile starts empty");
		layouts.current(true, "abc").set(1, PETS);
		assertTrue(layouts.current(true, "def").get(1) == null);
		assertEquals(HUB, layouts.current(true, null).get(0), "an unknown profile uses the global one");
		ButtonLayouts read = new ButtonLayouts();
		read.load(JsonParser.parseString(layouts.toJson().toString()));
		assertEquals(PETS, read.current(true, "abc").get(1));
		assertEquals(HUB, read.current(false, "abc").get(0));
		read.load(JsonParser.parseString("{\"profiles\": {\"../x\": []}}"));
		assertTrue(read.current(false, null).isEmpty());
	}

	@Test
	void oneClickSendsExactlyOneCommand() {
		long[] now = {10_000};
		ButtonSender sender = new ButtonSender(new dev.tim1337.lumio.skyblock.dungeon.AnnouncementQueue(() -> now[0], span -> 0,
			new dev.tim1337.lumio.skyblock.dungeon.AnnouncementQueue.Gap()));
		List<String> sent = new java.util.ArrayList<>();
		assertTrue(sender.click(HUB));
		sender.poll(sent::add);
		sender.poll(sent::add);
		now[0] += 60_000;
		sender.poll(sent::add);
		assertEquals(List.of("warp hub"), sent, "one command, without its slash, and nothing more");
		assertFalse(sender.click(new InventoryButton("/a\n/b", "x", ButtonIcon.DEFAULT, InventoryButton.ShowIn.BOTH, true, false)));
		assertEquals(0, sender.waiting());
	}

	@Test
	void twoClicksAreTwoCommandsWithTheGapBetween() {
		long[] now = {10_000};
		ButtonSender sender = new ButtonSender(new dev.tim1337.lumio.skyblock.dungeon.AnnouncementQueue(() -> now[0], span -> 0,
			new dev.tim1337.lumio.skyblock.dungeon.AnnouncementQueue.Gap()));
		List<String> sent = new java.util.ArrayList<>();
		sender.click(HUB);
		sender.poll(sent::add);
		sender.click(PETS);
		sender.poll(sent::add);
		assertEquals(1, sent.size(), "the second waits for the gap");
		now[0] += 1_000;
		sender.poll(sent::add);
		assertEquals(List.of("warp hub", "pets"), sent);
	}
}
