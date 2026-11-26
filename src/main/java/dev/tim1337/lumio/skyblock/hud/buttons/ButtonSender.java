package dev.tim1337.lumio.skyblock.hud.buttons;

import dev.tim1337.lumio.skyblock.data.CommandSender;
import dev.tim1337.lumio.skyblock.dungeon.AnnouncementQueue;

import java.util.function.Consumer;

/**
 * What a button's click sends: its one command, exactly as typed, through the shared send queue ({@link CommandSender}). One click is one command;
 * a button never sends more, and nothing is sent without a click.
 */
final class ButtonSender {
	private final CommandSender sender;

	ButtonSender(AnnouncementQueue queue) {
		this.sender = new CommandSender(queue);
	}

	/**
	 * A click on a button.
	 *
	 * @return whether its command was taken (a valid command, not already waiting)
	 */
	boolean click(InventoryButton button) {
		String command = button == null ? null : InventoryButton.clean(button.command());
		return command != null && sender.send(command);
	}

	void poll(Consumer<String> send) {
		sender.poll(send);
	}

	void clear() {
		sender.clear();
	}

	int waiting() {
		return sender.waiting();
	}
}
