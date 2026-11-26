package dev.tim1337.lumio.skyblock.dungeon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import it.unimi.dsi.fastutil.ints.IntSet;

import java.util.List;

/** The blood camp: the Watcher's lines, which heads count, where and when a mob lands, and the Watcher's time per floor, learned from runs. */
class BloodCampTest {
	/** A blood mob's texture hash from the list, and one that is not on it (as the player's own Soulweaver skulls are not). */
	private static final String MOB = "3260325171a7ba8460830c0eea515c757a665e5b16a14207ba1a3182752bee87";
	// TODO the real hash of the Soulweaver gloves' skulls, from /lumio debug skulls; until then a made-up one
	private static final String NOT_LISTED = "0f3a6e8d2c1b4a59e7d6c5b4a3928170f6e5d4c3b2a1908f7e6d5c4b3a291807";

	@Test
	void theWatchersLines() {
		assertTrue(DungeonParse.BLOOD_OPEN.matcher("[BOSS] The Watcher: Congratulations, you made it through the Entrance.").matches());
		assertTrue(DungeonParse.BLOOD_OPEN.matcher("[BOSS] The Watcher: Oh.. hello?").matches());
		assertTrue(DungeonParse.WATCHER_MOVES.matcher("[BOSS] The Watcher: Let's see how you can handle this.").matches());
		assertTrue(DungeonParse.WATCHER_ENOUGH.matcher("[BOSS] The Watcher: That will be enough for now.").matches());
		assertTrue(DungeonParse.WATCHER_DONE.matcher("[BOSS] The Watcher: You have proven yourself. You may pass.").matches());
		assertFalse(DungeonParse.WATCHER_MOVES.matcher("Ash: [BOSS] The Watcher: Let's see how you can handle this.").matches());
	}

	@Test
	void headsOfTheListOrThrownByTheWatcherNearHimWhileHeSends() {
		assertTrue(BloodMobs.bloodMob(MOB, 100, true, false, false), "on the list, wherever it was first seen");
		assertFalse(BloodMobs.bloodMob(NOT_LISTED, 100, true, false, false), "a skull not on the list that stood somewhere in the room");
		assertFalse(BloodMobs.bloodMob("", 100, true, false, false));
		assertFalse(BloodMobs.bloodMob(MOB, 21 * 21, true, false, false), "too far from the Watcher");
		assertFalse(BloodMobs.bloodMob(MOB, 100, false, false, false), "outside the Watcher's lines");
		assertFalse(BloodMobs.bloodMob(MOB, 100, true, true, false), "in the boss room");
		assertTrue(BloodMobs.watcher("2739d7f4e66a7db2ea6cd414e4c4ba41df7a92455c9fc42caab014665c367ad5"));
		assertFalse(BloodMobs.watcher(MOB));
	}

	@Test
	void aSkullNotOnTheListIsFollowedWhenItCameFromTheWatcher() {
		// the case of the run of 2026-11-14: the mobs' textures were not on the list, and all but two were left out
		String fromTheRun = "a2dd1389a97fd4c86b11a1a2abad61389404d837d2caf097c91d27768363dc5a";
		assertFalse(BloodMobs.mob(fromTheRun));
		assertTrue(BloodMobs.bloodMob(fromTheRun, 9, true, false, true), "first seen right at the Watcher: one of his mobs");
		assertFalse(BloodMobs.bloodMob(fromTheRun, 9, true, false, false), "first seen elsewhere: a head of the room");
		assertFalse(BloodMobs.bloodMob(fromTheRun, 9, false, false, true), "the Watcher sends nothing");
		// the Watcher's own head is never one of his mobs, also right where he is
		assertFalse(BloodMobs.bloodMob("2739d7f4e66a7db2ea6cd414e4c4ba41df7a92455c9fc42caab014665c367ad5", 0, true, false, true));
		// and it is followed like any other: it lands where and when the rule says
		BloodCamp camp = new BloodCamp();
		camp.opened(0);
		camp.seen(7, 0, 70, 0, 10);
		camp.seen(7, 1, 70, 0, 11);
		assertEquals(1, camp.landings(12, BloodCamp.Settings.DEFAULT).size());
	}

	@Test
	void aSoulweaverSkullNearTheWatcherIsNotOneOfHisMobs() {
		// a skull of the player's Soulweaver Gloves, not on the list, flying to a mob beside the Watcher
		String soulweaver = DungeonVisualRules.SOUL_WEAVER;
		assertFalse(BloodMobs.mob(soulweaver));
		// it starts three blocks from him and flies toward him: never farther from him than where it started
		assertFalse(BloodMobs.thrownOut(3.0, 1.5));
		assertTrue(BloodMobs.notThrown(3.0, 1.5, 1.5), "it moved, and not away from him: dropped");
		// one that flies past him sideways
		assertTrue(BloodMobs.notThrown(3.0, 3.1, 2.0));
		// one that starts where the player stands, across the room
		assertFalse(BloodMobs.thrownOut(9.0, 14.0));
		assertTrue(BloodMobs.notThrown(9.0, 14.0, 5.0));
		// the Watcher's own mob: first seen at him, then farther with every move
		assertFalse(BloodMobs.notThrown(0.4, 0.4, 0.0), "not moved yet: still open");
		assertFalse(BloodMobs.thrownOut(0.4, 0.6), "not far enough yet");
		assertTrue(BloodMobs.thrownOut(0.4, 1.2));
		assertFalse(BloodMobs.notThrown(0.4, 1.2, 0.8));

		// in the tracker: a head not confirmed has no box; once it is confirmed its way counts from its first place
		BloodCamp camp = new BloodCamp();
		camp.opened(0);
		camp.seen(9, 0, 70, 0, 10, false);
		camp.seen(9, 0.3, 70, 0, 11, false);
		assertTrue(camp.landings(12, BloodCamp.Settings.DEFAULT).isEmpty(), "no box before it is known to be his");
		assertEquals(0, camp.tracked());
		camp.seen(9, 0.6, 70, 0, 12, true);
		assertEquals(1, camp.landings(13, BloodCamp.Settings.DEFAULT).size());
		assertEquals(BloodCamp.LATER_FLIGHT > 0 ? BloodCamp.FIRST_WAVE_FLIGHT : 0, camp.landings(13, BloodCamp.Settings.DEFAULT).get(0).x(), 1e-6,
			"the landing is counted from where it was first seen");
		// a Soulweaver skull is dropped and never comes back
		camp.seen(11, 3, 70, 0, 12, false);
		camp.notHis(11);
		camp.seen(11, 2, 70, 0, 13, true);
		assertEquals(1, camp.landings(14, BloodCamp.Settings.DEFAULT).size());
	}

	@Test
	void whyNothingIsFollowedIsSaid() {
		assertTrue(BloodMobs.whyNothing(false, 0, 0, 0).contains("Watcher was not found"));
		assertTrue(BloodMobs.whyNothing(true, 0, 0, 0).contains("no armor stand"));
		assertTrue(BloodMobs.whyNothing(true, 4, 0, 0).contains("none wears a player head"));
		assertTrue(BloodMobs.whyNothing(true, 9, 8, 8).contains("none on the list"));
		assertTrue(BloodMobs.whyNothing(true, 9, 8, 6).contains("2 of his skulls"));
	}

	@Test
	void whereAndWhenAMobLands() {
		BloodCamp camp = new BloodCamp();
		camp.opened(1000);
		camp.seen(7, 0, 70, 0, 1000);
		camp.seen(7, 0.5, 70, 0, 1001);
		camp.seen(7, 1.0, 70, 0, 1002);
		List<BloodCamp.Landing> first = camp.landings(1002, new BloodCamp.Settings(38, 0));
		assertEquals(1, first.size());
		assertEquals(16.1, first.getFirst().x(), 1e-9);
		// first wave: 38 + 40 ticks after its first move (tick 1001), now 1002
		assertEquals((1001 + 38 + 40 - 1002) * 50L, first.getFirst().millisLeft());
		assertEquals((1001 + 38 + 40 - 1002) * 50L + 40, camp.landings(1002, BloodCamp.Settings.DEFAULT).getFirst().millisLeft());
		// after "Let's see how you can handle this.": shorter and sooner
		camp.wavesStarted();
		camp.seen(8, 10, 70, 10, 1100);
		camp.seen(8, 10, 70, 10.4, 1101);
		BloodCamp.Landing later = camp.landings(1101, new BloodCamp.Settings(38, 0)).stream().filter(each -> each.id() == 8).findFirst().orElseThrow();
		assertEquals(10 + 11.9, later.z(), 1e-9);
		assertEquals((1101 + 38 - 1101) * 50L, later.millisLeft());
		// a mob that has not moved is not shown; the end of the Watcher's lines forgets everything
		camp.seen(9, 5, 70, 5, 1101);
		assertEquals(2, camp.landings(1101, BloodCamp.Settings.DEFAULT).size());
		camp.ended();
		assertTrue(camp.landings(1102, BloodCamp.Settings.DEFAULT).isEmpty());
		camp.seen(10, 0, 70, 0, 1103);
		assertEquals(0, camp.tracked(), "no mobs after the Watcher's last line");
	}

	@Test
	void aMobThatLandedOrDiedIsNoLongerFollowed() {
		BloodCamp camp = new BloodCamp();
		camp.opened(1000);
		camp.wavesStarted();
		BloodCamp.Settings settings = new BloodCamp.Settings(38, 0);
		// two mobs fly: 1 starts moving at 1001 and lands at 1039, 2 is killed in flight
		camp.seen(1, 0, 70, 0, 1000);
		camp.seen(1, 0.4, 70, 0, 1001);
		camp.seen(2, 5, 70, 5, 1000);
		camp.seen(2, 5, 70, 5.4, 1001);
		camp.settle(1001, settings);
		assertEquals(2, camp.tracked());
		camp.died(2);
		assertEquals(1, camp.tracked(), "killed: gone");
		camp.settle(1038, settings);
		assertEquals(1, camp.tracked(), "still in flight a tick before");
		camp.settle(1039, settings);
		assertEquals(0, camp.tracked(), "landed at its spawn tick: gone");
		assertTrue(camp.landings(1039, settings).isEmpty(), "no box for a landed mob");
		// the landed and the dead mob are still near the Watcher: never followed again
		camp.seen(1, 0.4, 70, 0, 1040);
		camp.seen(2, 5, 70, 5.4, 1040);
		assertEquals(0, camp.tracked());
	}

	@Test
	void theFirstWaveWaitsLongerAndFliesFurtherThanLaterWaves() {
		BloodCamp.Settings settings = new BloodCamp.Settings(38, 0);
		BloodCamp camp = new BloodCamp();
		camp.opened(500);
		camp.seen(1, 0, 70, 0, 500);
		camp.seen(1, 0, 70, 0.3, 501);
		BloodCamp.Landing first = camp.landings(501, settings).getFirst();
		assertEquals(16.1, first.z(), 1e-9);
		assertEquals((38 + 40) * 50L, first.millisLeft(), "first wave: 38 ticks and the 40 before the Watcher moves");
		camp.wavesStarted();
		camp.seen(2, 0, 70, 0, 600);
		camp.seen(2, 0, 70, 0.3, 601);
		BloodCamp.Landing later = camp.landings(601, settings).stream().filter(each -> each.id() == 2).findFirst().orElseThrow();
		assertEquals(11.9, later.z(), 1e-9);
		assertEquals(38 * 50L, later.millisLeft());
		// the mob of the first wave keeps its own way and time after the Watcher's line
		BloodCamp.Landing still = camp.landings(601, settings).stream().filter(each -> each.id() == 1).findFirst().orElseThrow();
		assertEquals(16.1, still.z(), 1e-9);
		// the spawn tick is the player's: 35 lands three ticks sooner
		assertEquals(35 * 50L, camp.landings(601, new BloodCamp.Settings(35, 0)).stream().filter(each -> each.id() == 2).findFirst().orElseThrow().millisLeft());
	}

	@Test
	void theDirectionComesFromTheWholeWayNotTheLastStep() {
		BloodCamp camp = new BloodCamp();
		camp.opened(1000);
		camp.wavesStarted();
		camp.seen(4, 0, 70, 0, 1000);
		// a skull flying along +x, each step off to one side and then the other
		double[] sideways = {0.2, -0.2, 0.15, -0.15, 0.1, -0.1, 0.2, -0.2};
		double x = 0;
		double z = 0;
		for (int step = 0; step < sideways.length; step++) {
			x += 0.3;
			z += sideways[step];
			camp.seen(4, x, 70, z, 1001 + step);
		}
		BloodCamp.Landing landing = camp.landings(1001 + sideways.length, new BloodCamp.Settings(38, 0)).getFirst();
		assertEquals(11.9, landing.x(), 1e-6, "the sideways steps cancel out");
		assertEquals(0, landing.z(), 1e-6);
		// one step alone would point well off
		assertTrue(Math.abs(landing.stepZ()) > 0.1);
	}

	@Test
	void theCountdownRunsOnServerTicksOnly() {
		BloodCamp camp = new BloodCamp();
		camp.opened(1000);
		camp.wavesStarted();
		camp.seen(5, 0, 70, 0, 1000);
		camp.seen(5, 0.3, 70, 0, 1001);
		BloodCamp.Settings settings = new BloodCamp.Settings(38, 0);
		long before = camp.landings(1010, settings).getFirst().millisLeft();
		// asked again on the same server tick (many frames, or a lag spike): the same time
		assertEquals(before, camp.landings(1010, settings).getFirst().millisLeft());
		assertEquals(before - 50, camp.landings(1011, settings).getFirst().millisLeft(), "one server tick: 50 ms");
		// a position without a new tick does not restart the clock
		camp.seen(5, 0.6, 70, 0, 1011);
		assertEquals(before - 50, camp.landings(1011, settings).getFirst().millisLeft());
		// the offset and the ping shift what is shown, not the tick it lands on
		assertEquals(before + 40 - 120, camp.landings(1010, new BloodCamp.Settings(38, 40, 120)).getFirst().millisLeft());
		camp.settle(1038, new BloodCamp.Settings(38, 40, 120));
		assertEquals(1, camp.tracked());
		camp.settle(1039, new BloodCamp.Settings(38, 40, 120));
		assertEquals(0, camp.tracked());
	}

	@Test
	void theCountdownsWordsAndColors() {
		assertEquals("1.4s", BloodCamp.countdownText(1400));
		assertEquals("1.4s", BloodCamp.countdownText(1449));
		assertEquals("0.0s", BloodCamp.countdownText(-80));
		assertEquals("3.9s", BloodCamp.countdownText(3900));
		assertEquals(BloodCamp.Urgency.CALM, BloodCamp.urgency(1501));
		assertEquals(BloodCamp.Urgency.SOON, BloodCamp.urgency(1500));
		assertEquals(BloodCamp.Urgency.SOON, BloodCamp.urgency(500));
		assertEquals(BloodCamp.Urgency.NOW, BloodCamp.urgency(499));
		assertEquals(Long.MAX_VALUE, BloodCamp.soonest(List.of()), "no mob in flight: nothing to warn about");
	}

	@Test
	void theKillWarningGoesByTheFirstMobsOwnCountdown() {
		BloodCamp camp = new BloodCamp();
		camp.opened(1000);
		BloodCamp.Settings settings = new BloodCamp.Settings(38, 0);
		// the room is open for a long time: without a flying mob there is nothing to go by
		assertEquals(Long.MAX_VALUE, BloodCamp.soonest(camp.landings(1400, settings)));
		camp.seen(1, 0, 70, 0, 1400);
		camp.seen(1, 0.3, 70, 0, 1401);
		camp.seen(2, 5, 70, 0, 1410);
		camp.seen(2, 5.3, 70, 0, 1411);
		assertEquals((1401 + 78 - 1411) * 50L, BloodCamp.soonest(camp.landings(1411, settings)), "the mob that started first");
	}

	@Test
	void aMobIsLetGoWhenItsSkullIsGoneOrItSpawned() {
		BloodCamp camp = new BloodCamp();
		camp.opened(1000);
		camp.wavesStarted();
		BloodCamp.Settings settings = new BloodCamp.Settings(38, 0);
		camp.seen(1, 0, 70, 0, 1000);
		camp.seen(1, 0.3, 70, 0, 1001);
		camp.seen(2, 5, 70, 0, 1000);
		camp.seen(2, 5.3, 70, 0, 1001);
		// 2's skull is gone in flight: its box goes at once
		camp.keepOnly(IntSet.of(1));
		assertEquals(1, camp.tracked());
		List<BloodCamp.Report> gone = camp.drainReports();
		assertEquals(1, gone.size());
		assertEquals(2, gone.getFirst().id());
		assertEquals(1001, gone.getFirst().startTick());
		assertEquals(1039, gone.getFirst().predictedTick());
		assertTrue(camp.drainReports().isEmpty(), "told once");
		// 1 reaches its spawn tick: no box, but its skull is followed to where the mob really appears
		for (long tick = 1002; tick <= 1041; tick++) {
			camp.seen(1, 0.3 * (tick - 1000), 70, 0, tick);
			camp.settle(tick, settings);
		}
		assertEquals(0, camp.tracked());
		assertTrue(camp.landings(1041, settings).isEmpty());
		assertTrue(camp.drainReports().isEmpty(), "not told before the skull is gone");
		camp.keepOnly(IntSet.of());
		BloodCamp.Report spawned = camp.drainReports().getFirst();
		assertEquals(1, spawned.id());
		assertFalse(spawned.firstWave());
		assertEquals(1039, spawned.predictedTick());
		assertEquals(11.9, spawned.predictedX(), 1e-6);
		assertEquals(1041, spawned.endTick());
		assertEquals(2, spawned.ticksOff());
		assertEquals(0.3 * 41 - 11.9, spawned.blocksOff(), 1e-6);
		// a skull that stays past its spawn tick is dropped after a while, and never followed again
		camp.seen(3, 0, 70, 0, 2000);
		camp.seen(3, 0.3, 70, 0, 2001);
		camp.settle(2039, settings);
		camp.settle(2039 + BloodCamp.LINGER_TICKS + 1, settings);
		assertEquals("skull still there", camp.drainReports().getFirst().how());
		camp.seen(3, 0.6, 70, 0, 2200);
		assertEquals(0, camp.tracked());
		// a new world: nothing left, and nothing told
		camp.seen(6, 0, 70, 0, 2300);
		camp.seen(6, 0.3, 70, 0, 2301);
		camp.died(6);
		camp.reset();
		assertEquals(0, camp.tracked());
		assertTrue(camp.drainReports().isEmpty());
	}

	@Test
	void aHeadThatNeverMovesIsLetGo() {
		BloodCamp camp = new BloodCamp();
		camp.opened(1000);
		camp.seen(3, 0, 70, 0, 1000);
		camp.settle(1000 + BloodCamp.STILL_TICKS, BloodCamp.Settings.DEFAULT);
		assertEquals(1, camp.tracked());
		camp.settle(1001 + BloodCamp.STILL_TICKS, BloodCamp.Settings.DEFAULT);
		assertEquals(0, camp.tracked(), "a head standing still is no flying mob");
	}

	@Test
	void emptyOnceBloodIsDone() {
		BloodCamp camp = new BloodCamp();
		camp.opened(1000);
		for (int id = 0; id < 20; id++) {
			camp.seen(id, id, 70, 0, 1000);
		}
		assertEquals(20, camp.tracked());
		camp.ended();
		assertEquals(0, camp.tracked(), "blood done: nothing followed");
	}

	@Test
	void resetOnWorldChange() {
		BloodCamp camp = new BloodCamp();
		camp.opened(10);
		camp.seen(1, 0, 70, 0, 10);
		camp.reset();
		assertEquals(-1, camp.openedAt());
		assertFalse(camp.active());
		assertEquals(0, camp.tracked());
		camp.keepOnly(IntSet.of());
	}

	@Test
	void theWatchersTimePerFloor() {
		assertEquals(24, WatcherTimes.startValue(DungeonFloor.E));
		assertEquals(30, WatcherTimes.startValue(DungeonFloor.F1));
		assertEquals(25, WatcherTimes.startValue(DungeonFloor.F3));
		assertEquals(23.5, WatcherTimes.startValue(DungeonFloor.F6));
		assertEquals(21, WatcherTimes.startValue(DungeonFloor.F7));
		assertEquals(30, WatcherTimes.startValue(DungeonFloor.M1));
		assertEquals(23.5, WatcherTimes.startValue(DungeonFloor.M5));
		assertEquals(21, WatcherTimes.startValue(DungeonFloor.M7));
	}

	@Test
	void learnedMedian() {
		WatcherTimes times = new WatcherTimes();
		times.measured(DungeonFloor.F7, 22.0);
		times.measured(DungeonFloor.F7, 24.0);
		assertEquals(21, times.seconds(DungeonFloor.F7), "two runs are not enough");
		times.measured(DungeonFloor.F7, 23.0);
		assertEquals(23.0, times.seconds(DungeonFloor.F7));
		assertEquals(3, times.learnedFrom(DungeonFloor.F7));
		times.measured(DungeonFloor.F7, 2.0);
		assertEquals(3, times.learnedFrom(DungeonFloor.F7), "a time that cannot be real is left out");
		for (int i = 0; i < 12; i++) {
			times.measured(DungeonFloor.F7, 30.0);
		}
		assertEquals(30.0, times.seconds(DungeonFloor.F7), "only the last ten count");
		assertEquals(10, times.learnedFrom(DungeonFloor.F7));
		assertEquals(25, times.seconds(DungeonFloor.F4), "other floors keep their estimate");
	}
}
