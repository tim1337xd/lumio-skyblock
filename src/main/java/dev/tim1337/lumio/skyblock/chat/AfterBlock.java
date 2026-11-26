package dev.tim1337.lumio.skyblock.chat;

import dev.tim1337.lumio.api.LumioChat;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Lumio's lines that sum up a block of Hypixel's (a corpse's loot, a run's end): they are read while the game is still handling the block's last
 * line, before it is in the chat, so they wait until the end of the tick and come right after the whole block, as lines of their own. The block
 * itself stays as Hypixel sent it.
 */
public final class AfterBlock<T> {
	/** The lines for the chat, sent at the end of each client tick. */
	public static final AfterBlock<Runnable> CHAT = new AfterBlock<>();
	/** Lines of one block hardly reach this; a bound so nothing piles up. */
	private static final int MOST_WAITING = 16;

	private final List<T> waiting = new ArrayList<>();

	/** Adds a line to come after the block now being read. */
	public void add(T line) {
		if (waiting.size() < MOST_WAITING) {
			waiting.add(line);
		}
	}

	/** Hands out the waiting lines in their order. */
	public void flush(Consumer<T> out) {
		if (waiting.isEmpty()) {
			return;
		}
		List<T> lines = List.copyOf(waiting);
		waiting.clear();
		lines.forEach(out);
	}

	/** A Lumio line after the block now being read. */
	public static void line(Component line) {
		CHAT.add(() -> LumioChat.line(line));
	}

	/** A Lumio info line ("[Lumio] ...") after the block now being read. */
	public static void info(Component line) {
		CHAT.add(() -> LumioChat.info(line));
	}

	/** At the end of a client tick: the waiting lines go to the chat. */
	public static void tick() {
		CHAT.flush(Runnable::run);
	}
}
