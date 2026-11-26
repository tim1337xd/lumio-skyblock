package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The dungeon's Visual Filter rules match their skulls by texture and nothing else, and leave the Watcher and the blood mobs alone. */
class DungeonVisualRulesTest {
	/** The Watcher's skull, from the blood camp's list. */
	private static final String WATCHER = "2739d7f4e66a7db2ea6cd414e4c4ba41df7a92455c9fc42caab014665c367ad5";

	@Test
	void eachRuleMatchesItsSkullOnly() {
		assertTrue(DungeonVisualRules.matches(DungeonVisualRules.SOUL_WEAVER, DungeonVisualRules.SOUL_WEAVER));
		assertTrue(DungeonVisualRules.matches(DungeonVisualRules.TENTACLE, DungeonVisualRules.TENTACLE));
		assertTrue(DungeonVisualRules.matches(DungeonVisualRules.HEALER_FAIRY, DungeonVisualRules.HEALER_FAIRY));
		assertFalse(DungeonVisualRules.matches(DungeonVisualRules.TENTACLE, DungeonVisualRules.SOUL_WEAVER));
		assertFalse(DungeonVisualRules.matches(WATCHER, DungeonVisualRules.SOUL_WEAVER));
		assertFalse(DungeonVisualRules.matches("", DungeonVisualRules.TENTACLE));
	}

	@Test
	void theGlovesRuleWaitsForItsSkull() {
		assertFalse(DungeonVisualRules.matches("", DungeonVisualRules.SOULWEAVER_GLOVES));
		assertFalse(DungeonVisualRules.matches(DungeonVisualRules.SOUL_WEAVER, DungeonVisualRules.SOULWEAVER_GLOVES));
	}

	@Test
	void theWatcherIsNeverOneOfThem() {
		assertTrue(BloodMobs.watcher(WATCHER));
		for (String hash : new String[]{DungeonVisualRules.SOUL_WEAVER, DungeonVisualRules.TENTACLE, DungeonVisualRules.HEALER_FAIRY}) {
			assertFalse(BloodMobs.watcher(hash));
		}
	}
}
