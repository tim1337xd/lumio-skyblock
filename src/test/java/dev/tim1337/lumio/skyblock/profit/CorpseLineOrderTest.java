package dev.tim1337.lumio.skyblock.profit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.skyblock.chat.AfterBlock;
import dev.tim1337.lumio.skyblock.data.ItemGains;

import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Lumio's corpse line comes right after the loot list's closing bar, as a line of its own: the chat is played as the game handles it, every line read
 * before it is added, and Lumio's waiting lines added at the end of the tick. A list whose closing bar never comes is complete a few ticks after its
 * last line.
 */
class CorpseLineOrderTest {
	private static final String BAR = "§a§l" + "▬".repeat(64);
	private static final String LUMIO = "[Lumio] Corpse profit";
	private static final AfterBlock<String> AFTER = new AfterBlock<>();
	private static final List<String> CHAT = new ArrayList<>();

	@BeforeAll
	static void listen() {
		ItemGains.listenCorpses(corpse -> AFTER.add(LUMIO));
	}

	/** The game handles a line: the readers first, then the line goes into the chat. */
	private static void receive(String line) {
		ItemGains.onMessage(Component.literal(line));
		CHAT.add(line);
	}

	private static void endOfTick() {
		ItemGains.tickCorpse();
		AFTER.flush(CHAT::add);
	}

	@Test
	void afterTheClosingBar() {
		CHAT.clear();
		for (String line : List.of(BAR, "  §r§b§l§r§9§lUMBER §r§b§lCORPSE LOOT!", "", "  §r§a§lREWARDS", "    §r§fUmber §r§8x200",
			"    §r§bGlacite Powder §r§8x1,450", BAR)) {
			receive(line);
		}
		endOfTick();
		assertEquals(BAR, CHAT.get(CHAT.size() - 2));
		assertEquals(LUMIO, CHAT.getLast());
		assertEquals(1, CHAT.stream().filter(LUMIO::equals).count());
	}

	@Test
	void afterTheLastRewardWithoutABar() throws InterruptedException {
		CHAT.clear();
		for (String line : List.of(BAR, "  §r§b§l§r§9§lUMBER §r§b§lCORPSE LOOT!", "", "  §r§a§lREWARDS", "    §r§fUmber §r§8x200")) {
			receive(line);
		}
		endOfTick();
		assertTrue(CHAT.stream().noneMatch(LUMIO::equals), "not while the bar may still come");
		Thread.sleep(400);
		endOfTick();
		assertEquals(LUMIO, CHAT.getLast());
	}
}
