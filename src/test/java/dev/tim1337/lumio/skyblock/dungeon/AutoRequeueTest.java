package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.skyblock.chat.PartyCommands;

import org.junit.jupiter.api.Test;

/** Auto Requeue: every condition, the party's words, the ways to cancel, and that one command at most goes out per run. */
class AutoRequeueTest {
	private static final AutoRequeue.Settings SETTINGS = AutoRequeue.Settings.DEFAULT;
	/** A completed run in a full party the player leads. */
	private static final AutoRequeue.Facts GOOD = new AutoRequeue.Facts(true, 5, true);
	private static final long END = 100_000;

	/** @return how many times "send" was said from the run's end until well after the delay, a tick every 50 ms */
	private static int sends(AutoRequeue requeue, AutoRequeue.Settings settings, AutoRequeue.Facts facts) {
		int sends = 0;
		for (long now = END; now <= END + 40_000; now += 50) {
			sends += requeue.tick(now, settings, facts) ? 1 : 0;
		}
		return sends;
	}

	private static AutoRequeue ended() {
		AutoRequeue requeue = new AutoRequeue();
		requeue.runStarted();
		requeue.ended(END);
		return requeue;
	}

	@Test
	void oneCommandAfterTheDelay() {
		AutoRequeue requeue = ended();
		assertFalse(requeue.tick(END, SETTINGS, GOOD), "the end screen's lines are waited for");
		assertEquals(AutoRequeue.State.SETTLING, requeue.state());
		assertFalse(requeue.tick(END + AutoRequeue.SETTLE_MILLIS, SETTINGS, GOOD));
		assertEquals(AutoRequeue.State.COUNTING, requeue.state());
		assertEquals(5000 - AutoRequeue.SETTLE_MILLIS, requeue.millisLeft(END + AutoRequeue.SETTLE_MILLIS), "counted from the run's end");
		assertFalse(requeue.tick(END + 4999, SETTINGS, GOOD));
		assertTrue(requeue.tick(END + 5000, SETTINGS, GOOD));
		assertEquals(AutoRequeue.State.SENT, requeue.state());
		assertFalse(requeue.tick(END + 5050, SETTINGS, GOOD), "never twice");
		// the delay is the player's
		assertEquals(1, sends(ended(), new AutoRequeue.Settings(30, false, false, 5, true, false), GOOD));
		AutoRequeue slow = ended();
		assertFalse(slow.tick(END + 29_999, new AutoRequeue.Settings(30, false, false, 5, true, false), GOOD));
	}

	@Test
	void neverMoreThanOncePerRun() {
		AutoRequeue requeue = ended();
		assertEquals(1, sends(requeue, SETTINGS, GOOD));
		// the end screen coming again, or anything else, never brings a second one
		requeue.ended(END + 50_000);
		assertEquals(0, sends(requeue, SETTINGS, GOOD));
		assertFalse(requeue.requestNow(GOOD));
		// only a new run makes it ready again
		requeue.runStarted();
		assertEquals(0, sends(requeue, SETTINGS, GOOD), "no run ended yet");
		requeue.ended(END);
		assertEquals(1, sends(requeue, SETTINGS, GOOD));
	}

	@Test
	void nothingWithoutARunsEnd() {
		AutoRequeue requeue = new AutoRequeue();
		assertEquals(0, sends(requeue, SETTINGS, GOOD), "off, or no run: nothing");
		requeue.runStarted();
		assertEquals(0, sends(requeue, SETTINGS, GOOD));
		assertFalse(requeue.requestNow(GOOD), "a request during the run does nothing");
	}

	@Test
	void theLeaderOnlyUnlessAllowed() {
		AutoRequeue.Facts member = new AutoRequeue.Facts(true, 5, false);
		AutoRequeue requeue = ended();
		assertEquals(0, sends(requeue, SETTINGS, member));
		assertEquals(AutoRequeue.Reason.NOT_LEADER, requeue.reason());
		assertEquals("you are not the party leader", requeue.reasonText());
		assertEquals(1, sends(ended(), new AutoRequeue.Settings(5, true, false, 5, true, false), member));
	}

	@Test
	void soloAndThePartysSize() {
		AutoRequeue.Facts alone = new AutoRequeue.Facts(true, 1, false);
		AutoRequeue requeue = ended();
		assertEquals(0, sends(requeue, SETTINGS, alone));
		assertEquals(AutoRequeue.Reason.SOLO, requeue.reason());
		assertEquals(1, sends(ended(), new AutoRequeue.Settings(5, false, true, 5, true, false), alone), "solo allowed: no party size asked");
		AutoRequeue small = ended();
		assertEquals(0, sends(small, SETTINGS, new AutoRequeue.Facts(true, 4, true)));
		assertEquals(AutoRequeue.Reason.PARTY_TOO_SMALL, small.reason());
		assertEquals(1, sends(ended(), new AutoRequeue.Settings(5, false, false, 4, true, false), new AutoRequeue.Facts(true, 4, true)));
		assertEquals(1, sends(ended(), SETTINGS, new AutoRequeue.Facts(true, 5, true)));
	}

	@Test
	void downtimeHoldsItUntilTakenBack() {
		AutoRequeue requeue = new AutoRequeue();
		requeue.runStarted();
		requeue.downtime("Kiwi", "food");
		requeue.downtime("Rae", "");
		requeue.ended(END);
		assertEquals(0, sends(requeue, SETTINGS, GOOD));
		assertEquals(AutoRequeue.Reason.DOWNTIME, requeue.reason());
		assertEquals("kiwi and 1 more asked for downtime (food)", requeue.reasonText());
		requeue.undowntime("KIWI");
		assertEquals(0, sends(requeue, SETTINGS, GOOD), "Rae still wants it");
		requeue.undowntime("Rae");
		// taken back: a full countdown from then, never at once
		assertFalse(requeue.tick(END + 50_000, SETTINGS, GOOD));
		assertEquals(AutoRequeue.State.COUNTING, requeue.state());
		assertEquals(5000, requeue.millisLeft(END + 50_000));
		assertFalse(requeue.tick(END + 54_999, SETTINGS, GOOD));
		assertTrue(requeue.tick(END + 55_000, SETTINGS, GOOD));
		// asked during the countdown: stops it
		AutoRequeue counting = ended();
		assertFalse(counting.tick(END + 1000, SETTINGS, GOOD));
		counting.downtime("Kiwi", "");
		assertEquals(0, sends(counting, SETTINGS, GOOD));
		assertEquals("kiwi asked for downtime", counting.reasonText());
		// downtime of the last run does not hold the next
		counting.runStarted();
		counting.ended(END);
		assertEquals(1, sends(counting, SETTINGS, GOOD));
	}

	@Test
	void someoneLeaving() {
		AutoRequeue requeue = new AutoRequeue();
		requeue.runStarted();
		requeue.memberLeft("Rae");
		requeue.ended(END);
		assertEquals(0, sends(requeue, SETTINGS, GOOD));
		assertEquals(AutoRequeue.Reason.SOMEONE_LEFT, requeue.reason());
		assertEquals("rae left the party", requeue.reasonText());
		// with the switch off it does not matter
		AutoRequeue allowed = new AutoRequeue();
		allowed.runStarted();
		allowed.memberLeft("Rae");
		allowed.ended(END);
		assertEquals(1, sends(allowed, new AutoRequeue.Settings(5, false, false, 5, false, false), GOOD));
		// leaving during the countdown stops it too
		AutoRequeue counting = ended();
		assertFalse(counting.tick(END + 2000, SETTINGS, GOOD));
		counting.memberLeft("Kiwi");
		assertEquals(0, sends(counting, SETTINGS, GOOD));
	}

	@Test
	void aFailedRun() {
		AutoRequeue.Facts failed = new AutoRequeue.Facts(false, 5, true);
		AutoRequeue requeue = ended();
		assertEquals(0, sends(requeue, SETTINGS, failed));
		assertEquals(AutoRequeue.Reason.FAILED_RUN, requeue.reason());
		assertEquals(1, sends(ended(), new AutoRequeue.Settings(5, false, false, 5, true, true), failed));
	}

	@Test
	void thePlayerLeavingOrRequeuingByHand() {
		AutoRequeue left = ended();
		assertFalse(left.tick(END + 1000, SETTINGS, GOOD));
		left.left();
		assertEquals(0, sends(left, SETTINGS, GOOD), "another world: nothing goes out");
		assertEquals(AutoRequeue.Reason.LEFT, left.reason());
		left.ended(END + 60_000);
		assertEquals(0, sends(left, SETTINGS, GOOD));
		AutoRequeue byHand = ended();
		assertFalse(byHand.tick(END + 1000, SETTINGS, GOOD));
		byHand.selfRequeued();
		assertEquals(0, sends(byHand, SETTINGS, GOOD), "the player's own requeue is the run's one");
		assertEquals(AutoRequeue.Reason.ALREADY_SENT, byHand.reason());
		// by hand before the run's end, too
		AutoRequeue early = new AutoRequeue();
		early.runStarted();
		early.selfRequeued();
		early.ended(END);
		assertEquals(0, sends(early, SETTINGS, GOOD));
	}

	@Test
	void cancelAndStop() {
		AutoRequeue cancelled = ended();
		assertFalse(cancelled.tick(END + 1000, SETTINGS, GOOD));
		cancelled.cancel();
		assertEquals(0, sends(cancelled, SETTINGS, GOOD));
		assertEquals(AutoRequeue.Reason.CANCELLED, cancelled.reason());
		assertFalse(cancelled.requestNow(GOOD), "cancelled stays cancelled for this run");
		// a cancel with nothing counting does nothing to the next run
		AutoRequeue idle = new AutoRequeue();
		idle.cancel();
		idle.runStarted();
		idle.ended(END);
		assertEquals(1, sends(idle, SETTINGS, GOOD));
		// "!stop" from the party, during the run or the countdown
		AutoRequeue stopped = new AutoRequeue();
		stopped.runStarted();
		stopped.stop();
		stopped.ended(END);
		assertEquals(0, sends(stopped, SETTINGS, GOOD));
		assertEquals(AutoRequeue.Reason.STOPPED, stopped.reason());
		AutoRequeue late = ended();
		assertFalse(late.tick(END + 4000, SETTINGS, GOOD));
		late.stop();
		assertEquals(0, sends(late, SETTINGS, GOOD));
	}

	@Test
	void aRequestToRequeueNow() {
		// the leader's client only, after the run's end, once
		AutoRequeue requeue = ended();
		assertFalse(requeue.requestNow(new AutoRequeue.Facts(true, 5, false)), "not the leader");
		assertTrue(requeue.requestNow(GOOD));
		assertFalse(requeue.requestNow(GOOD));
		assertEquals(0, sends(requeue, SETTINGS, GOOD), "the countdown's command does not follow");
		// it passes a downtime and a small party, as the party asked for it
		AutoRequeue held = new AutoRequeue();
		held.runStarted();
		held.downtime("Kiwi", "");
		held.ended(END);
		assertEquals(0, sends(held, SETTINGS, new AutoRequeue.Facts(true, 3, true)));
		assertTrue(held.requestNow(new AutoRequeue.Facts(true, 3, true)));
	}

	@Test
	void thePartysWords() {
		PartyCommands.Said downtime = PartyCommands.said("Party > [MVP+] Kiwi: !dt food and water", "!");
		assertNotNull(downtime);
		assertEquals(PartyCommands.Source.PARTY, downtime.source());
		assertEquals("Kiwi", downtime.sender());
		assertEquals("dt", downtime.word());
		assertEquals("food and water", downtime.argument());
		assertEquals("downtime", PartyCommands.said("Party > Kiwi: !DOWNTIME", "!").word());
		assertEquals("undt", PartyCommands.said("Party > Kiwi: !undt", "!").word());
		assertEquals("rq", PartyCommands.said("Party > Kiwi: !rq", "!").word());
		assertEquals("requeue", PartyCommands.said("Party > Kiwi: !requeue", "!").word());
		assertEquals("stop", PartyCommands.said("Party > Kiwi: !stop", "!").word());
		assertNull(PartyCommands.said("Party > Kiwi: stop", "!"), "without the prefix");
		assertNull(PartyCommands.said("Kiwi: !stop", "!"), "not party chat");
		assertNull(PartyCommands.said("Party > Kiwi: !", "!"));
		assertEquals(PartyCommands.Source.GUILD, PartyCommands.said("Guild > Kiwi: !stop", "!").source());
		// the Party commands module knows undt beside dt; rq and stop are no commands of its own
		assertEquals(PartyCommands.Command.UNDOWNTIME, PartyCommands.read("Party > Kiwi: !undt", "!").command());
		assertEquals(PartyCommands.Command.DOWNTIME, PartyCommands.read("Party > Kiwi: !dt food", "!").command());
		assertNull(PartyCommands.read("Party > Kiwi: !rq", "!"));
		assertNull(PartyCommands.read("Party > Kiwi: !stop", "!"));
	}
}
