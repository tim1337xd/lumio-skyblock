package dev.tim1337.lumio.skyblock.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.skyblock.farming.FarmingKeys.Action;
import dev.tim1337.lumio.skyblock.farming.FarmingKeys.Hold;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

/** The Farming Keys: a remap, one key for one action, held while it is held, and nothing else. */
class FarmingKeysTest {
	private final FarmingKeys keys = new FarmingKeys();
	private final boolean[] bound = new boolean[FarmingKeys.ACTIONS];
	private final boolean[] down = new boolean[FarmingKeys.ACTIONS];
	private final Hold[] out = new Hold[FarmingKeys.ACTIONS];

	private void bind(Action... actions) {
		Arrays.fill(bound, false);
		for (Action action : actions) {
			bound[action.ordinal()] = true;
		}
	}

	private void hold(Action... actions) {
		Arrays.fill(down, false);
		for (Action action : actions) {
			down[action.ordinal()] = true;
		}
	}

	private Hold of(Action action) {
		return out[action.ordinal()];
	}

	@Test
	void theyActWhileThePlayerWantsToFarmAndTheWindowHasTheKeyboard() {
		assertTrue(FarmingKeys.active(true, true));
		assertFalse(FarmingKeys.active(false, true), "not farming: another item, flying, the Barn, a screen, out of the Garden");
		assertFalse(FarmingKeys.active(true, false), "the window lost the keyboard");
	}

	@Test
	void everyActionFollowsItsOwnKey() {
		for (Action action : Action.values()) {
			FarmingKeys each = new FarmingKeys();
			bind(action);
			hold(action);
			each.tick(true, bound, down, true, out);
			for (Action other : Action.values()) {
				assertEquals(other == action ? Hold.DOWN : Hold.LEAVE, of(other), action + " held, " + other);
			}
			hold();
			each.tick(true, bound, down, true, out);
			assertEquals(Hold.UP, of(action), action + " let go");
		}
	}

	@Test
	void oneHeldKeyIsOneHeldActionAndNothingElse() {
		bind(Action.BREAK, Action.JUMP);
		hold(Action.BREAK);
		int held = 0;
		// a second of holding: the action is down in every tick, never let go in between, and no other action is touched
		for (int tick = 0; tick < 20; tick++) {
			keys.tick(true, bound, down, false, out);
			assertEquals(Hold.DOWN, of(Action.BREAK), "tick " + tick);
			for (Action other : Action.values()) {
				if (other != Action.BREAK) {
					assertEquals(Hold.LEAVE, of(other), "tick " + tick + " " + other);
				}
			}
			held += keys.holding(Action.BREAK) ? 1 : 0;
		}
		assertEquals(20, held);
		// let go: let go once, then left alone
		hold();
		keys.tick(true, bound, down, false, out);
		assertEquals(Hold.UP, of(Action.BREAK));
		keys.tick(true, bound, down, false, out);
		assertEquals(Hold.LEAVE, of(Action.BREAK), "nothing repeats");
	}

	@Test
	void anActionWithoutAKeyKeepsTheGamesKey() {
		bind(Action.BREAK);
		hold();
		keys.tick(true, bound, down, true, out);
		assertEquals(Hold.LEAVE, of(Action.JUMP));
		assertEquals(Hold.LEAVE, of(Action.FORWARD));
	}

	@Test
	void theNormalKeyIsOffOnlyForRemappedActionsWhileActive() {
		bind(Action.BREAK);
		hold();
		// on: the action is up while the farming key is not held, whatever the normal key does
		keys.tick(true, bound, down, true, out);
		assertEquals(Hold.UP, of(Action.BREAK));
		assertTrue(FarmingKeys.normalKeyOff(true, true, true));
		// off: both keys work, the game's key is left as the game has it
		keys.tick(true, bound, down, false, out);
		assertEquals(Hold.LEAVE, of(Action.BREAK));
		assertFalse(FarmingKeys.normalKeyOff(true, true, false));
		// not active, or no farming key: the normal key is the game's as always
		assertFalse(FarmingKeys.normalKeyOff(false, true, true));
		assertFalse(FarmingKeys.normalKeyOff(true, false, true));
		keys.tick(false, bound, down, true, out);
		assertEquals(Hold.LEAVE, of(Action.BREAK));
	}

	@Test
	void everythingHeldIsLetGoWhenTheyStopBeingActive() {
		bind(Action.BREAK, Action.FORWARD, Action.SNEAK);
		hold(Action.BREAK, Action.FORWARD);
		keys.tick(true, bound, down, true, out);
		assertEquals(Hold.DOWN, of(Action.BREAK));
		assertEquals(Hold.DOWN, of(Action.FORWARD));
		// a screen opens while the keys are still held
		keys.tick(false, bound, down, true, out);
		assertEquals(Hold.UP, of(Action.BREAK));
		assertEquals(Hold.UP, of(Action.FORWARD));
		assertEquals(Hold.LEAVE, of(Action.SNEAK), "it was not held by a farming key");
		assertFalse(keys.holding(Action.BREAK));
		// and stays out of the way afterwards
		keys.tick(false, bound, down, true, out);
		for (Action action : Action.values()) {
			assertEquals(Hold.LEAVE, of(action));
		}
	}

	@Test
	void aKeyTakenAwayWhileHeldLetsItsActionGo() {
		bind(Action.USE);
		hold(Action.USE);
		keys.tick(true, bound, down, false, out);
		bind();
		keys.tick(true, bound, down, false, out);
		assertEquals(Hold.UP, of(Action.USE));
	}
}
