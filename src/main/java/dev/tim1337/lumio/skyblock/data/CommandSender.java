package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.skyblock.dungeon.AnnouncementQueue;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Commands the player asked for with one click or one key press (an inventory button, opening an item in the market): each exactly as typed, one per
 * request, through the send queue shared with Lumio's other messages, so it goes out at once unless Lumio sent something a moment ago. Nothing is
 * sent without a request.
 */
public final class CommandSender {
	private final AnnouncementQueue queue;
	private int requests;

	/** A sender on the game's shared gap. */
	public CommandSender() {
		this(new AnnouncementQueue(System::currentTimeMillis, span -> ThreadLocalRandom.current().nextLong(span + 1), AnnouncementQueue.SHARED_GAP));
	}

	/** A sender on a queue of its own, for tests. */
	public CommandSender(AnnouncementQueue queue) {
		this.queue = queue;
	}

	/**
	 * One request: a command as the player would type it, with or without its slash.
	 *
	 * @return whether it was taken (one command, not already waiting)
	 */
	public boolean send(String command) {
		String text = command == null ? "" : command.strip();
		if (text.startsWith("/")) {
			text = text.substring(1);
		}
		if (text.isEmpty() || text.indexOf('\n') >= 0) {
			return false;
		}
		// each request is its own event; the queue still refuses the same command waiting twice
		return queue.add("command " + requests++, text);
	}

	/** Sends what may go out now, without its slash, as the game's own command sending takes it. */
	public void poll(Consumer<String> send) {
		if (queue.waiting() == 0) {
			return;
		}
		AnnouncementQueue.Waiting next = queue.poll();
		if (next != null) {
			send.accept(next.text());
		}
		if (queue.waiting() == 0) {
			queue.forgetEvents();
		}
	}

	/** Nothing may go out any more: the world changed or the feature was switched off. */
	public void clear() {
		queue.clear();
	}

	public int waiting() {
		return queue.waiting();
	}
}
