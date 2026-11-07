package dev.tim1337.lumio.skyblock.dragons;

import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.experimental.util.Debug;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.data.ProfitActivity;
import dev.tim1337.lumio.skyblock.data.ProfitTracker;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;
import dev.tim1337.lumio.skyblock.data.SkyBlockPrices;
import dev.tim1337.lumio.skyblock.profit.ProfitHudFeature;

import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * What a dragon brings in, for its profit panel: what lands in the inventory during the fight and shortly after it, and the Summoning Eyes the player placed as a cost. A dragon the player placed no eye for does not count, unless asked. It only
 * reads: the chat and the inventory.
 */
public final class DragonProfit {
	public static final String EYES = "eyes";
	public static final String BEST_PLACE = "best_place";
	private static final String EYE_ID = "SUMMONING_EYE";
	private static final long AFTER_FIGHT_MILLIS = 60_000L;
	/** The summary follows the "down" line at once; a fight with no line about the player's place ends after this. */
	private static final long SUMMARY_MILLIS = 5_000L;
	private static final int KEPT = 3;

	private final ProfitHudFeature panel;
	private final Toggle eyesAsCost;
	private final Toggle countLeeched;
	private final Toggle showLast;
	private final Deque<DragonRecord> last = new ArrayDeque<>();
	private final DragonFight fight = new DragonFight();
	private String kind;
	private long downAt;
	private double topDamage;
	private DragonRecord collecting;
	private long collectingUntil;

	public DragonProfit(ProfitHudFeature panel) {
		this.panel = panel;
		eyesAsCost = panel.ownToggle("eyes_cost", "Eyes as a cost", true, "Summoning Eyes you placed, at their current price");
		countLeeched = panel.ownToggle("count_leeched", "Count dragons I didn't place eyes for", false, "On: if you were in the Nest and the fight lists you");
		showLast = panel.ownToggle("show_last", "Show last dragons", true, "Kind, place, weight and what dropped");
		ItemGains.listen(this::gained);
	}

	/** @return the last dragons, the newest first */
	Deque<DragonRecord> last() {
		return last;
	}

	/** @return whether the cards of the last dragons are wanted */
	boolean showsLast() {
		return showLast.get();
	}

	/** Called every tick: tells the panel about the fight, and ends one whose summary said nothing about the player. */
	public void tick() {
		if (fight.fighting()) {
			fight.seeArea(SkyBlockArea.zone());
			// someone who has nothing to do with the fight sees nothing of it
			if (collecting == null && fight.involved() && panel.isEnabled()) {
				startCollecting();
			}
		}
		ProfitActivity.dragonFight(fight.fighting() && fight.involved() && panel.isEnabled());
		if (downAt != 0L && System.currentTimeMillis() - downAt > SUMMARY_MILLIS) {
			finish(0, 0.0);
		}
	}

	/** A message from the server, about the fight or not. */
	public void onMessage(Component message) {
		if (!panel.isEnabled() || !SkyBlock.allowed()) {
			return;
		}
		String line = TextColors.noColors(message.getString());
		if (DragonFormats.eyePlaced(line)) {
			fight.eyePlaced();
			return;
		}
		if (DragonFormats.eyeRecovered(line)) {
			fight.eyeRecovered();
			return;
		}
		String spawned = DragonFormats.spawned(line);
		if (spawned != null) {
			kind = spawned;
			fight.spawned();
			fight.seeArea(SkyBlockArea.zone());
			collecting = null;
			if (fight.involved()) {
				startCollecting();
			}
			return;
		}
		String down = DragonFormats.down(line);
		if (down != null) {
			kind = down;
			fight.seeArea(SkyBlockArea.zone());
			fight.down();
			downAt = System.currentTimeMillis();
			collectingUntil = downAt + AFTER_FIGHT_MILLIS;
			topDamage = 0.0;
			return;
		}
		if (downAt != 0L) {
			double top = DragonFormats.topDamage(line);
			if (top >= 0.0) {
				topDamage = top;
				return;
			}
			DragonFormats.Standing standing = DragonFormats.standing(line);
			if (standing != null) {
				fight.listed();
				finish(standing.place(), standing.damage());
				return;
			}
		}
		if (line.startsWith(DragonFormats.SIGN) && !line.contains("placed a Summoning Eye") && !line.contains("Dragon Egg")) {
			Debug.log("Dragons: a line not understood: {}", line);
		}
	}

	private void startCollecting() {
		collecting = new DragonRecord(kind, fight.eyes(), fight.counts(countLeeched.get()));
		collectingUntil = Long.MAX_VALUE;
		last.addFirst(collecting);
		while (last.size() > KEPT) {
			last.removeLast();
		}
	}

	/** The summary said where the player came, or said nothing of the player. A fight the player had nothing to do with leaves nothing behind. */
	private void finish(int place, double damage) {
		downAt = 0L;
		if (!fight.involved()) {
			fight.finished();
			collecting = null;
			return;
		}
		if (collecting == null) {
			startCollecting();
			collectingUntil = System.currentTimeMillis() + AFTER_FIGHT_MILLIS;
		}
		int eyes = fight.eyes();
		collecting.counted = fight.counts(countLeeched.get());
		collecting.place = place;
		collecting.weight = DragonFormats.weight(eyes, place, topDamage, damage);
		if (collecting.counted) {
			ProfitTracker tracker = panel.tracker();
			tracker.addEvent();
			if (place > 0) {
				tracker.least(BEST_PLACE, place);
			}
			if (eyes > 0) {
				tracker.count(EYES, eyes);
				if (eyesAsCost.get()) {
					tracker.addCost(SkyBlockPrices.price(EYE_ID, panel.priceMode()) * eyes);
				}
			}
			LumioApi.saveSoon();
		}
		fight.finished();
	}

	private void gained(String id, String name, long count, boolean sacks) {
		if (sacks || collecting == null || System.currentTimeMillis() > collectingUntil || !panel.isEnabled() || EYE_ID.equals(id)) {
			return;
		}
		collecting.drops.add(count > 1 ? name + " ×" + count : name);
		if (collecting.counted) {
			panel.tracker().add(id, name, count);
		}
	}

	/** @return the tiles under the panel's list, from a time range's numbers */
	public static String[][] tiles(ProfitTracker.Bucket bucket, SkyBlockPrices.Mode mode) {
		Long best = bucket.counters.get(BEST_PLACE);
		return new String[][]{{"Dragons", ProfitTracker.count(bucket.events)}, {"Best place", best == null ? "–" : "#" + best},
			{"Per dragon", bucket.events > 0 ? ProfitTracker.coins(bucket.total(mode) / bucket.events) : "–"}};
	}

	/** @return some dragons, for the HUD editor */
	List<DragonRecord> sampleRecords() {
		DragonRecord first = new DragonRecord("Superior", 3, true);
		first.place = 1;
		first.weight = 512;
		first.drops.addAll(List.of("Aspect of the Dragons", "Superior Dragon Fragment ×3"));
		DragonRecord second = new DragonRecord("Strong", 2, true);
		second.place = 3;
		second.weight = 341;
		second.drops.add("Strong Dragon Fragment ×2");
		DragonRecord third = new DragonRecord("Young", 0, false);
		third.place = 6;
		return List.of(first, second, third);
	}
}
