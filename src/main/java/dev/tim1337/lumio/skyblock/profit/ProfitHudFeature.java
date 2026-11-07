package dev.tim1337.lumio.skyblock.profit;

import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.hud.HudEditor;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.feature.SettingGroup;
import dev.tim1337.lumio.api.setting.Action;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Toggle;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SlideText;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.api.experimental.ui.UiSounds;
import dev.tim1337.lumio.skyblock.data.FishingRods;
import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.data.Mayor;
import dev.tim1337.lumio.skyblock.data.ProfitActivity;
import dev.tim1337.lumio.skyblock.data.ProfitHistory;
import dev.tim1337.lumio.skyblock.data.ProfitTracker;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;
import dev.tim1337.lumio.skyblock.data.SkyBlockPrices;
import dev.tim1337.lumio.skyblock.hud.InventoryHover;
import dev.tim1337.lumio.skyblock.hud.PanelLooks;
import dev.tim1337.lumio.skyblock.slayer.SlayerBosses;

import com.google.gson.JsonObject;

import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.awt.Color;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Profit tracker for one Hypixel SkyBlock activity (mining, farming, slayer, fishing, corpses): the value of everything gained during the activity,
 * coins per hour, a ring chart of the most valuable items, and the items themselves. It appears when the activity starts and disappears a while
 * after it stops. There is one per activity, each with its own totals for the session, today and all time.
 */
public class ProfitHudFeature extends AddonPanel {
	private static final String COINS_KEY = "\u0000coins";

	private final ProfitActivity activity;
	private final ProfitTracker tracker = new ProfitTracker();
	/** The Gummy Polar Bear reminder of the slayer tracker. */
	private final GummyReminder gummy = new GummyReminder(this);

	private static final Pattern TIER = Pattern.compile("\\b(I|II|III|IV|V)\\s*$");

	/** @return the boss tier as the scoreboard names it ("Tarantula Broodfather IV" gives 4), or 0 */
	private static int tier(String boss) {
		Matcher matcher = TIER.matcher(boss == null ? "" : boss.strip());
		if (!matcher.find()) {
			return 0;
		}
		return switch (matcher.group(1)) {
			case "I" -> 1;
			case "II" -> 2;
			case "III" -> 3;
			case "IV" -> 4;
			default -> 5;
		};
	}

	/** When a paid quest was started whose cost has not been deducted yet (the scoreboard names its boss shortly after); 0 if none. */
	private long questStartedAt;
	/** Whether the boss was cocooned: the next quest starts with that boss and costs nothing. */
	private boolean cocooned;

	/** Deducts the started quest's cost (by the scoreboard boss) from its slayer's profit; half while the mayor perk is active. */
	private void payForQuest() {
		long now = System.currentTimeMillis();
		if (questStartedAt == 0 || now - questStartedAt < 1500L) {
			return;
		}
		String boss = SkyBlockArea.slayerBoss();
		Slayer kind = Slayer.of(boss);
		int tier = tier(boss);
		if (kind == null || tier == 0) {
			// the scoreboard does not show it yet: wait a while, otherwise the cost is unknown
			if (now - questStartedAt > 15_000L) {
				questStartedAt = 0;
			}
			return;
		}
		questStartedAt = 0;
		double cost = kind.cost(tier) * (Mayor.hasPerk("Slashed Pricing") ? 0.5 : 1.0);
		slayers.computeIfAbsent(kind, key -> new ProfitTracker()).addCost(cost);
		LumioApi.saveSoon();
	}

	/** For the slayer tracker: a separate tracker per slayer, and the slayer currently (or last) being done. */
	private final Map<Slayer, ProfitTracker> slayers = new EnumMap<>(Slayer.class);
	private Slayer slayer;

	/** @return the tracker currently receiving and displaying gains: the active slayer's for the slayer tracker, otherwise the activity's */
	private ProfitTracker current() {
		if (activity != ProfitActivity.SLAYER || slayer == null) {
			return tracker;
		}
		return slayers.computeIfAbsent(slayer, key -> new ProfitTracker());
	}

	/** Idle time after which the tracker pauses and its timer stops. */
	private static final long PAUSE_AFTER = 60_000L;
	/** Duration of an ongoing break, which counts once the activity resumes, but not if it turns into a pause. */
	private long idleMillis;

	/** @return whether the timer is stopped because the activity was idle for a minute */
	private boolean paused() {
		return !activity.within(PAUSE_AFTER) || inactiveNow();
	}

	/** Fishing: when a fish last bit (Hypixel shows "!!!" above the bobber), and whether the line was cast in the previous tick. */
	private long bitAt;
	private boolean lineOut;
	private ProfitTracker sample;

	private final Choice<ProfitTracker.Range> range = add(make().choice("range", Component.literal("Time range"), ProfitTracker.Range.SESSION, ProfitTracker.Range::displayName, Choice.Style.SEGMENTED), "General");
	private final Choice<SkyBlockPrices.Mode> priceMode = add(make().choice("price_mode", Component.literal("Price type"), SkyBlockPrices.Mode.BUY_ORDER, SkyBlockPrices.Mode::displayName, Choice.Style.SEGMENTED), "General",
		"Click the panel in an inventory to switch");
	/** How many rows the chart's top drops list: the most valuable items, coins counted as one. */
	public enum TopDrops {
		THREE("3", 3),
		FIVE("5", 5);

		private final String title;
		private final int rows;

		TopDrops(String title, int rows) {
			this.title = title;
			this.rows = rows;
		}

		public Component displayName() {
			return Component.literal(title);
		}
	}

	private final Choice<TopDrops> topDrops = add(make().choice("top_drops", Component.literal("Top drops"), TopDrops.THREE, TopDrops::displayName,
		Choice.Style.SEGMENTED), "General");
	private static final String LOOK_KEY = "look";
	private final Choice<PanelLooks.Profit> look = add(make().choice(LOOK_KEY, Component.literal("Style"), PanelLooks.Profit.BREAKDOWN,
		PanelLooks.Profit::displayName, Choice.Style.SEGMENTED), "General");
	/** One row for whether the top drops show and how many, which are kept apart since a click on the panel in the HUD editor switches them on and off. */
	public enum TopDropsRow {
		OFF("Off"),
		THREE("3"),
		FIVE("5");

		private final String title;

		TopDropsRow(String title) {
			this.title = title;
		}

		public Component displayName() {
			return Component.literal(title);
		}
	}

	private final Toggle showChart = add(make().toggle("show_chart", Component.literal("Chart"), true), "General");
	private final Toggle showDrops = add(make().toggle("show_drops", Component.literal("Top drops"), true), "General");
	private final Choice<TopDropsRow> topDropsRow = add(make().derivedChoice("top_drops_row", Component.literal("Top drops"), TopDropsRow.THREE, TopDropsRow::displayName, Choice.Style.SEGMENTED,
		() -> !showDrops.get() ? TopDropsRow.OFF : topDrops.get() == TopDrops.FIVE ? TopDropsRow.FIVE : TopDropsRow.THREE,
		value -> {
			showDrops.set(value != TopDropsRow.OFF);
			if (value != TopDropsRow.OFF) {
				topDrops.set(value == TopDropsRow.FIVE ? TopDrops.FIVE : TopDrops.THREE);
			}
		}), "General");
	private final Toggle showPerHour = add(make().toggle("show_per_hour", Component.literal("Per hour"), true), "General",
		"Time away does not count");
	private final Toggle showCounts = add(make().toggle("show_counts", Component.literal("Item counts"), true), "General");
	/**
	 * After how long without progress the tracker counts as inactive: its timer pauses and its stats turn into a quiet warning (for slayer, when
	 * the quest is active but no quest mob was killed for that long, like SkyHanni). There is no sound.
	 */
	private static final long INACTIVE_AFTER = 60_000L;
	private final Action resetSession = add(make().action("reset_session", Component.literal("The profit of this session"), Component.literal("Start over"), true, () -> reset(ProfitTracker.Range.SESSION)), "Reset");
	private final Action resetToday = add(make().action("reset_today", Component.literal("The profit of today"), Component.literal("Start over"), true, () -> reset(ProfitTracker.Range.TODAY)), "Reset");
	private final Action resetAll = add(make().action("reset_lifetime", Component.literal("The profit of all time"), Component.literal("Start over"), true, () -> reset(ProfitTracker.Range.LIFETIME)), "Reset");

	// animation state
	private final Spring shownAmount = Spring.bouncy(Spring.GENTLE);
	private final Spring total = Spring.smooth(Spring.GENTLE);
	private final Spring perHour = Spring.smooth(Spring.GENTLE);
	/** Per listed item: its ring share, its value, and its entrance progress. */
	private final Map<String, Spring[]> rows = new HashMap<>();
	private List<ProfitTracker.Entry> listed = List.of();
	private ProfitTracker shownTracker;
	private int savedChanges = -1;
	private long lastSaveAsk;
	private long lastPurse = -1;
	private long lastTick;

	public ProfitHudFeature(ProfitActivity activity, String id, String description, float defaultY) {
		super(id, activity.title() + " profit", description, true, Anchor.LEFT, false, 12f, defaultY);
		offerCardSize();
		this.activity = activity;
		iconRgb(switch (activity) {
			case MINING -> SystemColors.TEAL_RGB;
			case FARMING -> SystemColors.GREEN_RGB;
			case SLAYER -> SystemColors.PINK_RGB;
			case FISHING -> SystemColors.BLUE_RGB;
			case CORPSES -> SystemColors.PURPLE_RGB;
			case EXPERIMENTS -> SystemColors.PURPLE_RGB;
			case DRAGONS -> SystemColors.ORANGE_RGB;
		});
		iconName(switch (activity) {
			case MINING -> "pickaxe_coin";
			case FARMING -> "wheat_coin";
			case SLAYER -> "sword_coin";
			case FISHING -> "hook_coin";
			case CORPSES -> "coins";
			case EXPERIMENTS -> "gem";
			case DRAGONS -> "swoosh";
		});
		summary(switch (activity) {
			case MINING -> "What mining brings in, as coins";
			case FARMING -> "What farming brings in, as coins";
			case SLAYER -> "What your slayers bring in, as coins";
			case FISHING -> "What fishing brings in, as coins";
			case CORPSES -> "What corpse loot is worth, less the keys";
			case EXPERIMENTS -> "What the table brings in, less the bottles";
			case DRAGONS -> "What your dragons drop, less the eyes";
		});
		// one row stands for these two
		topDrops.visibleWhen(() -> false);
		showDrops.visibleWhen(() -> false);
		offerNoBackground();
		offerModes(look);
		offerHeader();
		if (activity == ProfitActivity.CORPSES) {
			// no time-based numbers for corpses: their line is the count and the value per corpse, always shown
			showPerHour.visibleWhen(() -> false);
			// corpse loot comes from the loot list in chat, with the key it consumed; nothing entering the inventory is attributed to it
			ItemGains.listenCorpses(this::corpseLooted);
		} else if (activity != ProfitActivity.EXPERIMENTS && activity != ProfitActivity.DRAGONS) {
			// the table's rewards are read from its menu, and what a dragon drops goes through its tracker, which knows the fight
			ItemGains.listen(this::gained);
		}
		if (activity == ProfitActivity.SLAYER) {
			AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
				gummy.onAttack(player);
				return InteractionResult.PASS;
			});
		}
	}

	/** Adds a switch of this panel's tracker to its settings. */
	public Toggle ownToggle(String key, String name, boolean on, String description) {
		return add(make().toggle(key, Component.literal(name), on), "General", description);
	}

	/** What a panel shows in a row of tiles under its list, from a time range's numbers: labels and values. */
	public interface Tiles {
		/** @return as many pairs of a label and a value as there are tiles */
		String[][] of(ProfitTracker.Bucket bucket, SkyBlockPrices.Mode mode);
	}

	private Tiles tiles;

	public void showTiles(Tiles shown) {
		tiles = shown;
	}

	private void drawTiles(Canvas ui, ProfitTracker.Bucket bucket, float y) {
		String[][] row = tiles.of(bucket, mode());
		float room = cardWidth() - 2f * PanelKit.PAD;
		float each = (room - (row.length - 1) * TILE_GAP) / row.length;
		for (int i = 0; i < row.length; i++) {
			PanelKit.stat(ui, PanelKit.PAD + i * (each + TILE_GAP), y, each, row[i][0], row[i][1]);
		}
	}

	private static final float TILE_GAP = 4f;
	private static final int TILES_ROW = 26;

	/** @return the profit of the slayer being done in this session, for the slayer panel's chips */
	public ProfitTracker.Bucket slayerSession() {
		return current().bucket(ProfitTracker.Range.SESSION);
	}

	/** @return the price type the panel values items at, so other panels show the same numbers */
	public SkyBlockPrices.Mode priceMode() {
		return mode();
	}

	public ProfitActivity activity() {
		return activity;
	}

	public ProfitTracker tracker() {
		return current();
	}

	private void reset(ProfitTracker.Range which) {
		current().reset(which);
		LumioApi.saveSoon();
	}

	/** Fishing works only on the islands made for it: elsewhere the tracker is hidden and counts nothing, and keeps its session. */
	private boolean allowedHere() {
		return SkyBlock.allowed() && (activity != ProfitActivity.FISHING || FishingRods.onFishingIsland());
	}

	/** An item was gained: it belongs to this activity if it is active, or was a moment ago (drops arrive after the blow). */
	private void gained(String id, String name, long count, boolean sacks) {
		// The server reports sacks every 30 seconds, for the preceding 30 seconds: what was mined right before stopping (walking into a mineshaft, a
		// warp) arrives long after the last blow and still belongs to this activity. Items landing in the inventory arrive immediately.
		// Only the most recent activity gets it: mined items do not go to a slayer done shortly before.
		if (!isEnabled() || !allowedHere() || !activity.within(sacks ? 45_000 : 8000) || ProfitActivity.latest() != activity) {
			return;
		}
		current().add(id, name, count);
	}

	/** @return the key a corpse type consumes; Lapis corpses need none */
	private static String corpseKey(String kind) {
		return switch (kind) {
			case "TUNGSTEN" -> "TUNGSTEN_KEY";
			case "UMBER" -> "UMBER_KEY";
			case "VANGUARD" -> "SKELETON_KEY";
			default -> null;
		};
	}

	/**
	 * A corpse was opened, counted like SkyHanni's corpse tracker: its loot (powder is not an item and is excluded), one more corpse, and the
	 * consumed key as a cost, unless the Resourceful perk saved it.
	 */
	private void corpseLooted(ItemGains.Corpse corpse) {
		if (!isEnabled() || !allowedHere()) {
			return;
		}
		ProfitTracker into = current();
		for (ItemGains.Drop drop : corpse.drops()) {
			if (!drop.name().endsWith("Powder")) {
				into.add(drop.id(), drop.name(), drop.count());
			}
		}
		into.addEvent();
		String key = corpseKey(corpse.kind());
		if (key != null && corpse.keyUsed()) {
			into.addCost(SkyBlockPrices.price(key, mode()));
		}
		LumioApi.saveSoon();
	}

	/**
	 * Counts a fishing catch: Hypixel shows "!!!" above the bobber on a bite, and reeling in shortly after catches it. Reeling in without a bite is
	 * not a catch.
	 */
	private void countCatch(Minecraft client, long now) {
		var hook = FishingRods.holding(client.player.getMainHandItem()) ? client.player.fishing : null;
		if (hook != null) {
			for (var stand : client.level.getEntitiesOfClass(ArmorStand.class, hook.getBoundingBox().inflate(2.5, 3.0, 2.5))) {
				if (stand.hasCustomName() && stand.getCustomName().getString().contains("!!!")) {
					bitAt = now;
					break;
				}
			}
		} else if (lineOut && now - bitAt < 2500L) {
			current().addEvent();
			bitAt = 0;
		}
		lineOut = hook != null;
	}

	/** Handles a chat message: a slain slayer boss is one slayer round. */
	public void onMessage(Component message) {
		if (activity != ProfitActivity.SLAYER) {
			return;
		}
		String text = message.getString();
		// the Gummy Polar Bear is tracked even while the tracker is off, since its reminder can be a notification
		gummy.onMessage(text);
		if (!isEnabled()) {
			return;
		}
		if (text.contains("SLAYER QUEST COMPLETE!")) {
			current().addEvent();
		}
		// a started quest is paid for, except the one after a cocooned boss, which starts with that boss
		if (text.contains("YOU COCOONED YOUR SLAYER BOSS")) {
			cocooned = true;
		} else if (text.contains("SLAYER QUEST STARTED!")) {
			if (cocooned) {
				cocooned = false;
			} else {
				questStartedAt = System.currentTimeMillis();
			}
		}
	}

	/** Called every tick: activity time, coins gained passively, and saving. */
	public void tick(Minecraft client) {
		long now = System.currentTimeMillis();
		long dt = lastTick == 0 ? 0 : Math.min(1000, now - lastTick);
		lastTick = now;
		if (!isEnabled() || client.level == null || client.player == null || !allowedHere()) {
			lastPurse = -1;
			return;
		}
		// prices are only fetched while a tracker is enabled on SkyBlock: they are irrelevant otherwise, and fetching is a download
		if (SkyBlock.inSkyBlock()) {
			SkyBlockPrices.refreshIfOld();
		}
		if (activity == ProfitActivity.SLAYER) {
			// the active slayer receives and shows its own profit until another one is started
			Slayer now2 = Slayer.of(SkyBlockArea.slayerBoss());
			if (now2 == null) {
				// fall back to the boss name tag when the scoreboard does not show the quest
				now2 = Slayer.of(SlayerBosses.ownBossName());
			}
			if (now2 != null && now2 != slayer) {
				slayer = now2;
				LumioApi.saveSoon();
			}
			payForQuest();
		}
		if (activity == ProfitActivity.FISHING) {
			countCatch(client, now);
		}
		// Activity time: a short break (checking the inventory, walking to the next spot) still counts once the activity resumes; a minute without
		// activity is a pause and that minute does not count. Once the tracker is inactive its timer stops, also for slayer, whose active quest
		// keeps it busy while nothing is killed.
		if (inactiveNow()) {
			idleMillis = 0;
		} else if (activity.now()) {
			current().addTime(dt + idleMillis);
			idleMillis = 0;
		} else if (activity.within(PAUSE_AFTER)) {
			idleMillis += dt;
		} else {
			idleMillis = 0;
		}
		// the session's profit over time, for the chart: recorded whatever the panel shows, even while it is hidden
		current().recordHistory(mode());
		if (activity == ProfitActivity.SLAYER) {
			// mob coins: the purse rises a little while the quest is active and no menu is open (buying or selling does not count)
			long purse = SkyBlockArea.purse();
			boolean menuOpen = client.gui.screen() != null;
			if (purse >= 0 && lastPurse >= 0 && !menuOpen && activity.within(8000)) {
				long gained = purse - lastPurse;
				if (gained > 0 && gained <= 50_000L) {
					current().addCoins(gained);
				}
			}
			lastPurse = menuOpen ? -1 : purse;
		}
		int changesNow = tracker.changes();
		for (ProfitTracker each : slayers.values()) {
			changesNow = changesNow * 31 + each.changes();
		}
		if (changesNow != savedChanges && now - lastSaveAsk > 15_000L) {
			savedChanges = changesNow;
			lastSaveAsk = now;
			LumioApi.saveSoon();
		}
	}

	// ------------------------------------------------------------------------------------------------ display

	private boolean wanted() {
		// while changing worlds (entering a mineshaft, warping) the location is briefly unknown: keep the previous visibility
		if (ProfitActivity.changingWorld()) {
			return wasWanted;
		}
		return wasWanted = wantedNow();
	}

	/** How long the experiments' panel stays at the table after the last of its menus. */
	private static final long EXPERIMENTS_LINGER_MILLIS = 30_000L;

	/** The last visibility, kept while changing worlds. */
	private boolean wasWanted;

	private boolean wantedNow() {
		if (!allowedHere()) {
			return false;
		}
		// auto-detected, no setting: during the activity and afterwards as long as the player stays where it happened, the same for every panel of the activity
		if (activity == ProfitActivity.EXPERIMENTS) {
			// the table's panel only while a table menu or the rewards are open, or near the table for a while after: walking off or waiting hides it
			return activity.engaged() && (activity.now() || activity.within(EXPERIMENTS_LINGER_MILLIS) && dev.tim1337.lumio.skyblock.experiments.ExperimentProfit.nearTable(net.minecraft.client.Minecraft.getInstance()));
		}
		return activity.engaged();
	}

	private ProfitTracker sample() {
		if (sample == null) {
			sample = new ProfitTracker();
			String[][] items = switch (activity) {
				case MINING -> new String[][]{{"MITHRIL_ORE", "Mithril", "1240", "260"}, {"TITANIUM_ORE", "Titanium", "31", "6800"}, {"FINE_JADE_GEM", "Fine Jade Gemstone", "4", "41000"},
					{"ROUGH_JADE_GEM", "Rough Jade Gemstone", "380", "140"}, {"GLACITE", "Glacite", "120", "320"}};
				case FARMING -> new String[][]{{"ENCHANTED_CARROT", "Enchanted Carrot", "812", "640"}, {"ENCHANTED_GOLDEN_CARROT", "Enchanted Golden Carrot", "5", "41000"},
					{"CARROT_ITEM", "Carrot", "2900", "4"}, {"ENCHANTED_SEEDS", "Enchanted Seeds", "9", "900"}};
				case SLAYER -> new String[][]{{"REVENANT_FLESH", "Revenant Flesh", "214", "1100"}, {"FOUL_FLESH", "Foul Flesh", "6", "32000"},
					{"REVENANT_VISCERA", "Revenant Viscera", "1", "140000"}, {"ROTTEN_FLESH", "Rotten Flesh", "640", "3"}};
				case FISHING -> new String[][]{{"ENCHANTED_RAW_FISH", "Enchanted Raw Fish", "96", "900"}, {"MAGMA_FISH", "Magmafish", "410", "110"},
					{"SHARK_FIN", "Shark Fin", "12", "9000"}, {"ENCHANTED_SPONGE", "Enchanted Sponge", "18", "2100"}};
				case EXPERIMENTS -> new String[][]{{"ENCHANTMENT_GROWTH_6", "Growth VI", "2", "410000"}, {"ENCHANTMENT_ULTIMATE_WISE_1", "Ultimate Wise I", "1", "690000"},
					{"TITANIC_EXP_BOTTLE", "Titanic Experience Bottle", "2", "200000"}, {"METAPHYSICAL_SERUM", "Metaphysical Serum", "1", "229000"}};
				case DRAGONS -> new String[][]{{"ASPECT_OF_THE_DRAGON", "Aspect of the Dragons", "1", "2820000"}, {"SUPERIOR_FRAGMENT", "Superior Dragon Fragment", "6", "275000"},
					{"DRAGON_CLAW", "Dragon Claw", "2", "367000"}, {"ENCHANTED_ENDER_PEARL", "Enchanted Ender Pearl", "32", "17000"}};
				case CORPSES -> new String[][]{{"FINE_ONYX_GEM", "Fine Onyx Gemstone", "14", "52000"}, {"SHATTERED_PENDANT", "Shattered Locket", "1", "410000"},
					{"BEJEWELED_COLLAR", "Bejeweled Collar", "2", "95000"}, {"TUNGSTEN_PLATE", "Tungsten Plate", "6", "12000"}, {"UMBER_PLATE", "Umber Plate", "5", "11000"}};
			};
			for (String[] item : items) {
				ProfitTracker.Entry entry = sample.bucket(ProfitTracker.Range.SESSION).items.computeIfAbsent(item[0], key -> new ProfitTracker.Entry(key, item[1]));
				entry.count = Long.parseLong(item[2]);
				entry.lastPrice = Double.parseDouble(item[3]);
			}
			if (activity == ProfitActivity.SLAYER) {
				sample.bucket(ProfitTracker.Range.SESSION).coins = 38_400;
				sample.bucket(ProfitTracker.Range.SESSION).events = 22;
				sample.bucket(ProfitTracker.Range.SESSION).spent = 150_000;
			}
			if (activity == ProfitActivity.CORPSES) {
				sample.bucket(ProfitTracker.Range.SESSION).spent = 186_000;
				sample.bucket(ProfitTracker.Range.SESSION).events = 9;
			}
			if (activity == ProfitActivity.EXPERIMENTS || activity == ProfitActivity.DRAGONS) {
				ProfitTracker.Bucket shown = sample.bucket(ProfitTracker.Range.SESSION);
				shown.spent = activity == ProfitActivity.DRAGONS ? 2_400_000 : 312_000;
				shown.events = activity == ProfitActivity.DRAGONS ? 7 : 3;
				shown.counters.put(dev.tim1337.lumio.skyblock.experiments.ExperimentProfit.XP, 600_000L);
				shown.counters.put(dev.tim1337.lumio.skyblock.experiments.ExperimentProfit.ULTRA_RARE, 1L);
				shown.counters.put(dev.tim1337.lumio.skyblock.experiments.ExperimentProfit.BOTTLES, 2L);
				shown.counters.put(dev.tim1337.lumio.skyblock.dragons.DragonProfit.EYES, 12L);
				shown.counters.put(dev.tim1337.lumio.skyblock.dragons.DragonProfit.BEST_PLACE, 1L);
			}
			sample.bucket(ProfitTracker.Range.SESSION).activeMillis = 72 * 60_000L;
		}
		return sample;
	}

	private SkyBlockPrices.Mode mode() {
		return priceMode.get();
	}

	// ------------------------------------------------------------------------------------------------ inventory interaction

	/** Positions of the two header labels in panel pixels: the time range and the price mode. */
	private final float[] rangeLabel = new float[2];
	private final float[] modeLabel = new float[2];
	/** Mouse position in panel pixels while an inventory is open; far away otherwise. */
	private float mousePanelX = -1000f;
	private float mousePanelY = -1000f;

	/** Draws the panel again on top of an open inventory, so its labels can be clicked as in SkyHanni. */
	public void renderInInventory(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!isEnabled() || !shownNow()) {
			return;
		}
		int[] box = hudBounds();
		// like the other panels it stays under the inventory dimming, and only comes up bright (to be clicked) while hovered
		float lit = inventoryHover.lit(box, mouseX, mouseY);
		if (!InventoryHover.drawn(lit)) {
			return;
		}
		float s = box[2] / (float) Math.max(1, panelWidth());
		mousePanelX = (mouseX - box[0]) / Math.max(0.01f, s);
		mousePanelY = (mouseY - box[1]) / Math.max(0.01f, s);
		float alphaBefore = Draw.alpha();
		Draw.setAlpha(alphaBefore * lit);
		try {
			render(graphics, null);
		} finally {
			Draw.setAlpha(alphaBefore);
			mousePanelX = -1000f;
			mousePanelY = -1000f;
		}
	}

	private final InventoryHover inventoryHover = new InventoryHover();

	/**
	 * @return how long the activity has been idle: for slayer, how long the quest has made no progress (active, boss not spawned, and no quest mob
	 *         killed); for the others, since the activity was last done. -1 while active or when unknown.
	 */
	private long idleAge() {
		if (activity == ProfitActivity.SLAYER) {
			if (!SkyBlockArea.slayerQuest() || SkyBlockArea.slayerBossUp() || SkyBlockArea.bossSlain()) {
				return -1;
			}
			return SkyBlockArea.slayerProgressAge();
		}
		if (activity == ProfitActivity.CORPSES || activity.now() || activity.lastSeen() == 0) {
			return -1;
		}
		return System.currentTimeMillis() - activity.lastSeen();
	}

	/** @return whether the activity has been idle for a minute: the tracker is then inactive and its timer stops */
	private boolean inactiveNow() {
		long age = idleAge();
		return age >= 0 && age >= INACTIVE_AFTER;
	}

	/** @return how long the activity has been idle once it counts as inactive, the same for every tracker; -1 while active */
	private long inactiveFor() {
		return inactiveNow() ? idleAge() : -1;
	}

	private boolean shownNow() {
		return shownAmount.value() > 0.5f;
	}

	/** @return whether the click hit the panel: on the time label it cycles the time range, anywhere else the price mode */
	public boolean clickInInventory(double mouseX, double mouseY, int button) {
		if (!isEnabled() || !shownNow() || button != 0 && button != 1) {
			return false;
		}
		int[] box = hudBounds();
		if (mouseX < box[0] || mouseY < box[1] || mouseX >= box[0] + box[2] || mouseY >= box[1] + box[3]) {
			return false;
		}
		float s = box[2] / (float) Math.max(1, panelWidth());
		float px = (float) (mouseX - box[0]) / Math.max(0.01f, s);
		float py = (float) (mouseY - box[1]) / Math.max(0.01f, s);
		boolean onRange = py >= labelsTop - 2 && py < labelsTop + SECTION_ROW && px >= rangeLabel[0] - 3 && px < rangeLabel[1] + 3;
		int step = button == 1 ? -1 : 1;
		if (onRange) {
			ProfitTracker.Range[] all = ProfitTracker.Range.values();
			range.set(all[Math.floorMod(range.get().ordinal() + step, all.length)]);
		} else {
			SkyBlockPrices.Mode[] all = SkyBlockPrices.Mode.values();
			priceMode.set(all[Math.floorMod(priceMode.get().ordinal() + step, all.length)]);
		}
		UiSounds.click();
		LumioApi.saveSoon();
		return true;
	}

	private boolean hovered(float[] label, float top, float bottom) {
		return mousePanelX >= label[0] - 3 && mousePanelX < label[1] + 3 && mousePanelY >= top && mousePanelY < bottom;
	}

	private ProfitTracker.Range shownRange() {
		return shownTracker == sample ? ProfitTracker.Range.SESSION : range.get();
	}

	@Override
	public void prepare(Minecraft client) {
		// only during the activity: in the editor too, where it then shows its data
		// the sample while the HUD editor simulates the activity and nothing was tracked yet
		ProfitTracker real = current();
		ProfitTracker.Bucket realBucket = real.bucket(range.get());
		shownTracker = simulated() && realBucket.items.isEmpty() && realBucket.coins == 0 ? sample() : real;
		shownAmount.follow(simulated() || wanted() ? 1f : 0f);
		ProfitTracker.Bucket bucket = shownTracker.bucket(shownRange());
		workOut(bucket);
		gummyNow = activity == ProfitActivity.SLAYER && gummy.missing(shownTracker == sample);
		gummyShown.follow(gummyNow ? 1f : 0f);
		// ring shares are of the gross gain; the total is the profit after spawn costs
		double sum = frameGross;
		shownHeight.follow(targetHeight());
		total.follow((float) frameSum);
		perHour.follow((float) framePerHour);
		// every listed item has its springs; unlisted ones are dropped
		for (int i = 0; i < listed.size(); i++) {
			ProfitTracker.Entry entry = listed.get(i);
			Spring[] springs = rows.computeIfAbsent(entry.id, key -> new Spring[]{Spring.smooth(Spring.GENTLE), Spring.smooth(Spring.GENTLE), Spring.bouncy(Spring.GENTLE)});
			springs[0].follow(sum <= 0 ? 0f : (float) (listedValues[i] / sum));
			springs[1].follow((float) listedValues[i]);
			springs[2].follow(1f);
		}
		otherSpring.follow((float) otherValue);
		if (bucket.coins > 0) {
			Spring[] springs = rows.computeIfAbsent(COINS_KEY, key -> new Spring[]{Spring.smooth(Spring.GENTLE), Spring.smooth(Spring.GENTLE), Spring.bouncy(Spring.GENTLE)});
			springs[0].follow(sum <= 0 ? 0f : (float) (bucket.coins / sum));
			springs[1].follow((float) bucket.coins);
			springs[2].follow(1f);
		}
		Iterator<Map.Entry<String, Spring[]>> iterator = rows.entrySet().iterator();
		while (iterator.hasNext()) {
			Map.Entry<String, Spring[]> row = iterator.next();
			boolean kept = row.getKey().equals(COINS_KEY) ? bucket.coins > 0 : listed.stream().anyMatch(entry -> entry.id.equals(row.getKey()));
			if (!kept) {
				row.getValue()[2].follow(0f);
				if (row.getValue()[2].value() < 0.02f) {
					iterator.remove();
				}
			}
		}
	}

	/** The panel figures, recomputed only when something was gained, the view changed, or some time passed (prices change). */
	private double frameSum;
	/** The gross gain before spawn costs: the base of the ring shares. */
	private double frameGross;
	private double framePerHour;
	private double[] listedValues = new double[0];
	/** What the list was last worked out for, compared part by part, so the check makes nothing new each frame. */
	private ProfitTracker workedOutTracker;
	private int workedOutChanges = -1;
	private ProfitTracker.Range workedOutRange;
	private SkyBlockPrices.Mode workedOutMode;
	private TopDrops workedOutTop;
	private boolean workedOutBreakdown;
	private boolean workedOutPrices;
	private long workedOutAt;

	/**
	 * Computes what is listed and its value: sorting and pricing every item each frame is too expensive (and the panel is drawn twice over an
	 * inventory), so it runs when something changed and otherwise twice per second, still more often than prices change.
	 */
	private void workOut(ProfitTracker.Bucket bucket) {
		long now = System.currentTimeMillis();
		ProfitTracker.Range range = shownRange();
		SkyBlockPrices.Mode priced = mode();
		TopDrops top = topDrops.get();
		boolean split = breakdown();
		boolean prices = SkyBlockPrices.ready();
		if (shownTracker == workedOutTracker && shownTracker.changes() == workedOutChanges && range == workedOutRange && priced == workedOutMode
			&& top == workedOutTop && split == workedOutBreakdown && prices == workedOutPrices && now - workedOutAt < 500L) {
			return;
		}
		workedOutTracker = shownTracker;
		workedOutChanges = shownTracker.changes();
		workedOutRange = range;
		workedOutMode = priced;
		workedOutTop = top;
		workedOutBreakdown = split;
		workedOutPrices = prices;
		workedOutAt = now;
		listed = shownTracker.top(shownRange(), listedCount(bucket), mode());
		listedValues = new double[listed.size()];
		double listedSum = 0;
		for (int i = 0; i < listed.size(); i++) {
			listedValues[i] = listed.get(i).value(mode());
			listedSum += listedValues[i];
		}
		frameSum = bucket.total(mode());
		frameGross = bucket.gross(mode());
		framePerHour = bucket.perHour(mode());
		// everything not listed: the gray part of the ring, which gets its own row so it is clear what it is
		otherValue = Math.max(0, frameGross - listedSum - bucket.coins);
		otherCount = Math.max(0, bucket.items.size() - listed.size());
		long pieces = 0;
		for (ProfitTracker.Entry entry : bucket.items.values()) {
			pieces += entry.count;
		}
		for (ProfitTracker.Entry entry : listed) {
			pieces -= entry.count;
		}
		otherPieces = Math.max(0, pieces);
		// "Other" is a part of the breakdown's donut and legend; the chart lists its top drops only
		otherShown = breakdown() && otherCount > 0 && frameGross > 0 && otherValue / frameGross > 0.005;
	}

	@Override
	public boolean shown(Minecraft client) {
		return shownAmount.value() > 0.01f;
	}

	@Override
	public boolean inEditorWhenHidden() {
		return false;
	}

	/** Value of all unlisted items, how many kinds they are, and whether they have a row. */
	private double otherValue;
	private int otherCount;
	/** Total item count of all unlisted items, for the count column of their row. */
	private long otherPieces;
	private boolean otherShown;
	private final Spring otherSpring = Spring.smooth(Spring.GENTLE);

	/**
	 * @return how many items are listed: the chart's top drops (coins taking one of their rows), or the breakdown's legend of at most five rows
	 *         (coins one, and one kept for the rest as "Other")
	 */
	private int listedCount(ProfitTracker.Bucket bucket) {
		int rows = breakdown() ? LEGEND_ROWS : topDrops.get().rows;
		int room = rows - (bucket.coins > 0 ? 1 : 0);
		if (breakdown() && bucket.items.size() > room) {
			room--;
		}
		return Math.max(1, room);
	}

	/** The most rows of the breakdown's legend. */
	private static final int LEGEND_ROWS = 5;

	private int rowCount() {
		int count = listed.size();
		if (shownTracker != null && shownTracker.bucket(shownRange()).coins > 0) {
			count++;
		}
		if (otherShown) {
			count++;
		}
		return Math.max(1, count);
	}

	@Override
	public int panelWidth() {
		return cardWidth();
	}

	@Override
	public int panelHeight() {
		// the panel height animates to its rows: a new item makes room for itself instead of making the panel jump
		return Math.round(shownHeight.value() > 0f ? shownHeight.value() : targetHeight());
	}

	/** Displayed height, following the rows' height with a slight bounce. */
	private final Spring shownHeight = Spring.bouncy(Spring.GENTLE);

	/** The coins per hour, which update every few seconds with the changed characters sliding. */
	private final SlideText underSlide = new SlideText();
	/** The price mode and time range the line was built for. */
	private String underFor = "";

	// the layout, in panel pixels
	/** The hero's height (the total at its largest), the per hour line, a top drop's row and its tile, and a section label's row. */
	private static final int HERO_ROW = 17;
	private static final int RATE_ROW = 9;
	private static final int DROP_ROW = 20;
	private static final int DROP_TILE = 16;
	/** The room between the hero, the chart and the top drops. */
	private static final int BLOCK_GAP = 13;
	private static final int SECTION_ROW = 11;
	/** The breakdown: the small ring, its thickness, the room to the total beside it, the total's largest size, and a category row of the list below. */
	private static final int DONUT = 58;
	private static final float DONUT_THICKNESS = 8f;
	private static final float DONUT_GAP = 10f;
	private static final float TOTAL_SIZE = 1.6f;
	private static final int CATEGORY_ROW = 14;
	/** Where the total's three lines stand beside the ring: the total's top and, from the ring's middle, the per hour line and the labels. */
	private static final float TOTAL_ABOVE_MIDDLE = 19f;
	private static final float RATE_BELOW_MIDDLE = 2f;
	private static final float LABELS_BELOW_MIDDLE = 13f;
	/** Height of the Gummy Polar Bear reminder: one quiet line at the bottom. */
	private static final int GUMMY_ROW = 12;
	/** Space above the spawn costs line, and its height including the separator. */
	private static final int COSTS_GAP = 4;
	private static final int COSTS_HEIGHT = 15;

	private boolean breakdown() {
		return look.get() == PanelLooks.Profit.BREAKDOWN;
	}

	/** @return the height of the panel for what it shows, as {@link #layout} lays it out */
	private int targetHeight() {
		return Math.round(layout(null, false));
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		float amount = Mth.clamp(shownAmount.value(), 0f, 1f);
		float alphaBefore = Draw.alpha();
		Draw.setAlpha(alphaBefore * amount);
		var pose = graphics.pose();
		pose.pushMatrix();
		// enters from slightly above and grows a little, like a card being laid down
		pose.translate(0f, (1f - amount) * -8f);
		pose.translate(cardWidth() / 2f, 0f);
		pose.scale(0.94f + 0.06f * amount, 0.94f + 0.06f * amount);
		pose.translate(-cardWidth() / 2f, 0f);
		try {
			int height = panelHeight();
			drawGlass(ui, cardWidth(), height, PanelKit.RADIUS);
			// clip what does not fit yet while the panel grows, so a new row emerges from under the bottom edge
			graphics.enableScissor(0, 0, cardWidth(), height - 2);
			try {
				layout(ui, true);
			} finally {
				graphics.disableScissor();
			}
		} finally {
			pose.popMatrix();
			Draw.setAlpha(alphaBefore);
		}
	}

	/**
	 * Lays the panel out from the top and draws it when asked; measuring and drawing use the same steps, so the height always fits what is drawn.
	 *
	 * @return the panel's height
	 */
	private float layout(Canvas ui, boolean draw) {
		ProfitTracker.Bucket bucket = shownTracker == null ? current().bucket(range.get()) : shownTracker.bucket(shownRange());
		if (draw) {
			drawHeader(ui, bucket);
		}
		float y = contentTop();
		if (breakdown()) {
			int categories = Math.min(MOST_CATEGORIES, rowCount());
			if (draw) {
				drawBreakdown(ui, y);
			}
			y += DONUT + PanelKit.GAP + categories * CATEGORY_ROW;
		} else {
			if (draw) {
				drawHero(ui, bucket, y);
			}
			y += HERO_ROW + 3;
			if (activity == ProfitActivity.CORPSES || showPerHour.get() || events() != null || HudEditor.isOpen()) {
				if (draw) {
					drawRate(ui, bucket, y);
				}
				y += RATE_ROW;
			}
			if (chartShown()) {
				y += BLOCK_GAP;
				if (draw) {
					float before = Draw.alpha();
					Draw.setAlpha(before * HudEditor.partAlpha(showChart.get()));
					chart.draw(ui, PanelKit.PAD, y, cardWidth() - 2 * PanelKit.PAD, shownTracker == sample ? sampleHistory() : shownTracker.history());
					Draw.setAlpha(before);
					part("Chart", showChart, PanelKit.PAD, y, cardWidth() - 2 * PanelKit.PAD, ProfitChart.HEIGHT + ProfitChart.AXIS);
				}
				y += ProfitChart.HEIGHT + ProfitChart.AXIS;
			}
			if (showDrops.get() || HudEditor.isOpen()) {
				y += BLOCK_GAP;
				if (draw) {
					float before = Draw.alpha();
					Draw.setAlpha(before * HudEditor.partAlpha(showDrops.get()));
					drawDrops(ui, bucket, y);
					Draw.setAlpha(before);
					part("Top drops", showDrops, PanelKit.PAD, y, cardWidth() - 2 * PanelKit.PAD, 6 + SECTION_ROW + rowCount() * line(DROP_ROW));
				}
				y += 6 + SECTION_ROW + rowCount() * line(DROP_ROW);
			} else {
				y += PanelKit.GAP;
				if (draw) {
					drawViewLabels(ui, y);
				}
				y += SECTION_ROW;
			}
		}
		if (tiles != null) {
			if (draw) {
				drawTiles(ui, bucket, y + 2f);
			}
			y += TILES_ROW;
		}
		// a note, such as the Gummy Polar Bear reminder: one quiet line at the bottom, so nothing above it moves when it comes and goes
		if (gummyWanted()) {
			if (draw) {
				drawGummy(ui, y + 2f, Mth.clamp(gummyShown.value(), 0f, 1f));
			}
			y += GUMMY_ROW;
		}
		if (bucket.spent > 0) {
			if (draw) {
				drawSpawnCosts(ui, Math.round(y + COSTS_GAP), bucket.spent);
			}
			y += COSTS_GAP + COSTS_HEIGHT;
		}
		return y + PanelKit.BOTTOM - 4;
	}

	/** The most categories the breakdown lists below its ring. */
	private static final int MOST_CATEGORIES = LEGEND_ROWS;

	/** @return the title: the slayer being done, or the activity's profit */
	private String title() {
		if (activity == ProfitActivity.EXPERIMENTS) {
			return activity.title();
		}
		return activity == ProfitActivity.SLAYER && slayer != null ? slayer.title : activity.title() + " profit";
	}

	/** The header: the tile, the title, and the time spent (with a green dot while running), or that the tracker is paused or inactive. */
	private void drawHeader(Canvas ui, ProfitTracker.Bucket bucket) {
		boolean sampleShown = shownTracker == sample;
		long idle = sampleShown ? -1 : inactiveFor();
		String meta;
		int metaColor = 0;
		boolean dot = false;
		if (idle >= 0) {
			meta = "Inactive " + ProfitTracker.time(idle);
			metaColor = SystemColors.ORANGE;
		} else if (!sampleShown && paused()) {
			meta = "Paused";
			metaColor = SystemColors.ORANGE;
		} else if (activity == ProfitActivity.CORPSES) {
			meta = "";
		} else {
			long seconds = bucket.activeMillis / 1000L;
			if (seconds != shownSeconds) {
				shownSeconds = seconds;
				timeText = PanelKit.clock(seconds);
			}
			meta = timeText;
			dot = true;
		}
		familyHeader(ui, cardWidth(), "coins", PanelKit.TILE_PROFIT, title(), meta, metaColor, dot);
	}

	/** The time spent as shown in the header, made again only when the second changes. */
	private long shownSeconds = -1;
	private String timeText = "";

	/** The total as the hero: large and gold, as large as fits. */
	private void drawHero(Canvas ui, ProfitTracker.Bucket bucket, float y) {
		double value = total.value();
		long whole = Math.round(value);
		if (whole != heroValue) {
			heroValue = whole;
			heroText = (whole >= 0 ? "+" : "") + PanelKit.grouped(whole);
		}
		float room = cardWidth() - 2f * PanelKit.PAD;
		float scale = Math.min(PanelKit.HERO, room / Math.max(1f, ui.boldWidthScaled(heroText, 1f)));
		boolean inactive = shownTracker != sample && inactiveFor() >= 0;
		float before = Draw.alpha();
		if (inactive) {
			Draw.setAlpha(before * INACTIVE_ALPHA);
		}
		PanelKit.hero(ui, heroText, PanelKit.PAD, y + HERO_ROW - 8f * scale, scale, value < 0 ? PanelKit.RED_TEXT : PanelKit.GOLD, value > 0);
		Draw.setAlpha(before);
	}

	/** The hero dims while the tracker is inactive. */
	private static final float INACTIVE_ALPHA = 0.6f;
	private long heroValue = Long.MIN_VALUE;
	private String heroText = "";

	/** @return the corpses opened and what one was worth on average, as "12 corpses \u00B7 59.1k per corpse"; made again only when they change */
	private String perCorpse(ProfitTracker.Bucket bucket) {
		double each = perCorpseValue(bucket);
		if (bucket.events != corpseCount || each != corpseEach) {
			corpseCount = bucket.events;
			corpseEach = each;
			corpseText = ProfitTracker.count(bucket.events) + (bucket.events == 1 ? " corpse" : " corpses")
				+ (bucket.events > 0 ? " \u00B7 " + PanelKit.compact(each) + " per corpse" : "");
		}
		return corpseText;
	}

	/** @return the value of the shown view per corpse: its total (after the keys) over the corpses, or 0 without one */
	private double perCorpseValue(ProfitTracker.Bucket bucket) {
		return bucket.events > 0 ? frameSum / bucket.events : 0.0;
	}

	private long corpseCount = -1;
	private double corpseEach = Double.NaN;
	private String corpseText = "";

	/** @return the round of the activity counted, with its rate ("12 bosses · 18/h"), or null for an activity without rounds */
	private String events() {
		if (activity == ProfitActivity.MINING || activity == ProfitActivity.FARMING || activity == ProfitActivity.CORPSES) {
			return null;
		}
		ProfitTracker.Bucket bucket = shownTracker == null ? current().bucket(range.get()) : shownTracker.bucket(shownRange());
		// the rate only once there is enough data: two bosses in the first minute are not 120 per hour
		boolean known = bucket.events > 0 && bucket.activeMillis >= 60_000L;
		String noun = switch (activity) {
			case SLAYER -> bucket.events == 1 ? " boss" : " bosses";
			case CORPSES -> bucket.events == 1 ? " corpse" : " corpses";
			case EXPERIMENTS -> bucket.events == 1 ? " table" : " tables";
			case DRAGONS -> bucket.events == 1 ? " dragon" : " dragons";
			default -> bucket.events == 1 ? " catch" : " catches";
		};
		long rate = known ? Math.round(bucket.eventsPerHour()) : -1;
		if (bucket.events != eventsCount || rate != eventsRate) {
			eventsCount = bucket.events;
			eventsRate = rate;
			eventsText = ProfitTracker.count(bucket.events) + noun + (rate >= 0 ? " · " + rate + "/h" : "");
		}
		return eventsText;
	}

	private long eventsCount = -1;
	private long eventsRate = -2;
	private String eventsText = "";

	/** The coins per hour under the total, with a small chip of how the rate changed, and the rounds of the activity at the right. */
	private void drawRate(Canvas ui, ProfitTracker.Bucket bucket, float y) {
		float x = PanelKit.PAD;
		if (activity == ProfitActivity.CORPSES) {
			ui.textAtScaled(perCorpse(bucket), x, y, PanelKit.META, HudTheme.text());
			return;
		}
		if (showPerHour.get() || HudEditor.isOpen()) {
			float before = Draw.alpha();
			Draw.setAlpha(before * HudEditor.partAlpha(showPerHour.get()));
			String rate = bucket.activeMillis < 10_000L ? "-" : PanelKit.compact(framePerHour);
			// the rate slides character by character when it changes; switching the view shows the new number immediately
			String view = shownRange() + "|" + mode();
			if (!view.equals(underFor)) {
				underFor = view;
				underSlide.set(rate);
			} else {
				underSlide.setEvery(rate, 5_000L);
			}
			String shownRate = underSlide.text();
			underSlide.draw(ui, x, y, PanelKit.META, HudTheme.text());
			float after = x + ui.widthScaled(shownRate, PanelKit.META) + 3f;
			ui.textAtScaled("per hour", after, y, PanelKit.META, HudTheme.secondary());
			float end = after + ui.widthScaled("per hour", PanelKit.META);
			double trend = shownTracker == sample ? SAMPLE_TREND : shownRange() == ProfitTracker.Range.SESSION ? shownTracker.history().trend(bucket.activeMillis, frameSum)
				: Double.NaN;
			if (!Double.isNaN(trend) && Math.abs(trend) >= 0.005) {
				long percent = Math.round(Math.abs(trend) * 100.0);
				if (percent != trendPercent || (trend > 0) != trendUp) {
					trendPercent = percent;
					trendUp = trend > 0;
					trendText = (trendUp ? "+" : "-") + percent + "%";
				}
				end += 5f + PanelKit.tag(ui, trendText, end + 5f, y - 1.5f, trendUp ? PanelKit.GREEN : PanelKit.RED);
			}
			Draw.setAlpha(before);
			part("Per hour", showPerHour, x, y - 1f, end - x, RATE_ROW);
		}
		String events = events();
		if (events != null) {
			float width = ui.widthScaled(events, PanelKit.SMALL);
			ui.textAtScaled(events, cardWidth() - PanelKit.PAD - width, y + 0.5f, PanelKit.SMALL, HudTheme.secondary());
		}
	}

	/** The trend shown on the sample in the HUD editor. */
	private static final double SAMPLE_TREND = 0.12;
	private long trendPercent = -1;
	private boolean trendUp;
	private String trendText = "";

	/** @return whether the chart is shown: when chosen (or in the HUD editor, faded) and there is some history to draw */
	private boolean chartShown() {
		return (showChart.get() || HudEditor.isOpen()) && (shownTracker == sample || shownTracker.history().count() >= 2);
	}

	private final ProfitChart chart = new ProfitChart();

	/** A rising history for the sample in the HUD editor. */
	private ProfitHistory sampleHistory;

	private ProfitHistory sampleHistory() {
		if (sampleHistory == null) {
			sampleHistory = new ProfitHistory();
			double sampleTotal = sample().bucket(ProfitTracker.Range.SESSION).total(mode());
			for (int i = 0; i <= 18; i++) {
				// a little uneven, as a real session is
				double share = i / 18.0;
				sampleHistory.record(i * 4 * 60_000L, sampleTotal * (share * share * 0.35 + share * 0.65));
			}
		}
		return sampleHistory;
	}

	/** The top drops: a section label (with the range and price type, which can be clicked over an inventory) and a row per drop. */
	private void drawDrops(Canvas ui, ProfitTracker.Bucket bucket, float top) {
		PanelKit.divider(ui, cardWidth(), top);
		float y = top + 6f;
		PanelKit.sectionLabel(ui, "TOP DROPS", PanelKit.PAD, y);
		// the range and price type only over an inventory, where they can be clicked to switch: less small text over the game
		if (Minecraft.getInstance().gui.screen() != null) {
			drawViewLabels(ui, y - 2f);
		}
		y += SECTION_ROW;
		if (listed.isEmpty() && bucket.coins <= 0 && bucket.spent <= 0) {
			ui.textAtScaled("Nothing yet", PanelKit.PAD, y + 4f, PanelKit.META, HudTheme.secondary());
			return;
		}
		int index = 0;
		for (ProfitTracker.Entry entry : listed) {
			Spring[] springs = rows.get(entry.id);
			ItemStack icon = ItemGains.iconOf(entry.id);
			drawDrop(ui, y, rarityColor(entry.id, icon, sliceColor(index)), icon, SkyBlockPrices.clean(entry.name), entry.count,
				springs == null ? (float) entry.value(mode()) : springs[1].value(), springs == null ? 1f : springs[2].value());
			y += line(DROP_ROW);
			index++;
		}
		if (otherShown) {
			if (otherCount != otherNameCount) {
				otherNameCount = otherCount;
				otherName = "Other (" + otherCount + (otherCount == 1 ? " kind)" : " kinds)");
			}
			drawDrop(ui, y, otherColor(), otherIcon(), otherName, otherPieces, otherSpring.value(), 1f);
			y += line(DROP_ROW);
		}
		if (bucket.coins > 0) {
			Spring[] springs = rows.get(COINS_KEY);
			drawDrop(ui, y, PanelKit.GOLD, coinsIcon(), "Coins", -1, springs == null ? (float) bucket.coins : springs[1].value(),
				springs == null ? 1f : springs[2].value());
		}
	}

	private int otherNameCount = -1;
	private String otherName = "";

	/** Positions of the two view labels in panel pixels (the time range and the price mode), and their row. */
	private float labelsTop;

	/** Draws "Buy order · Session" at the right of a row; over an inventory each is a label that can be clicked to switch it. */
	private void drawViewLabels(Canvas ui, float y) {
		drawViewLabels(ui, y, Float.NaN);
	}

	/** Same, starting at a place of its own when one is given. */
	private void drawViewLabels(Canvas ui, float y, float startX) {
		labelsTop = y;
		int text = HudTheme.text();
		int secondary = HudTheme.secondary();
		String rangeText = shownRange().title();
		String modeText = mode().title();
		float rangeWidth = ui.widthScaled(rangeText, PanelKit.SMALL);
		float modeWidth = ui.widthScaled(modeText, PanelKit.SMALL);
		float rangeX = Float.isNaN(startX) ? cardWidth() - PanelKit.PAD - rangeWidth : startX + modeWidth + 8f;
		float modeX = rangeX - 8f - modeWidth;
		rangeLabel[0] = rangeX;
		rangeLabel[1] = rangeX + rangeWidth;
		modeLabel[0] = modeX;
		modeLabel[1] = modeX + modeWidth;
		// over an inventory the hovered label is highlighted like a button; otherwise they look as over the game
		boolean overRange = mousePanelX > -999f && hovered(rangeLabel, y - 2f, y + SECTION_ROW);
		boolean overMode = mousePanelX > -999f && hovered(modeLabel, y - 2f, y + SECTION_ROW);
		if (overRange) {
			ui.roundedSmooth(rangeLabel[0] - 3f, y - 1.5f, rangeWidth + 6f, 10f, Radius.pill(10f), Draw.withOpacity(accentArgb(), 0.35f));
		}
		if (overMode) {
			ui.roundedSmooth(modeLabel[0] - 3f, y - 1.5f, modeWidth + 6f, 10f, Radius.pill(10f), Draw.withOpacity(accentArgb(), 0.35f));
		}
		ui.textAtScaled(modeText, modeX, y + 0.5f, PanelKit.SMALL, overMode ? text : secondary);
		ui.textAtScaled("·", rangeX - 5f, y + 0.5f, PanelKit.SMALL, secondary);
		ui.textAtScaled(rangeText, rangeX, y + 0.5f, PanelKit.SMALL, overRange ? text : secondary);
	}

	/** Draws a drop: a tile tinted in its rarity color with the item, its name in that color, the count and the value. */
	private void drawDrop(Canvas ui, float y, int color, ItemStack icon, String name, long count, float value, float in) {
		in = Mth.clamp(in, 0f, 1f);
		if (in <= 0.01f) {
			return;
		}
		float alphaBefore = Draw.alpha();
		Draw.setAlpha(alphaBefore * in);
		float slide = (1f - in) * 6f;
		try {
			float x = PanelKit.PAD + slide;
			float tileY = y + (DROP_ROW - DROP_TILE) / 2f;
			ui.roundedSmooth(x, tileY, DROP_TILE, DROP_TILE, 4f, Draw.withOpacity(color, 0.18f));
			ui.ring(Math.round(x), Math.round(tileY), DROP_TILE, DROP_TILE, 4f, 1, Draw.withOpacity(color, 0.35f));
			if (icon != null && !icon.isEmpty()) {
				var pose = ui.graphics().pose();
				pose.pushMatrix();
				pose.translate(x + (DROP_TILE - 16f * DROP_ITEM) / 2f, tileY + (DROP_TILE - 16f * DROP_ITEM) / 2f);
				pose.scale(DROP_ITEM, DROP_ITEM);
				ui.graphics().item(icon, 0, 0);
				pose.popMatrix();
			} else {
				// an item never in the inventory has no icon: a gem in its color, as the drops are drawn on the cards
				ui.icon("gem", Math.round(x + 4f), Math.round(tileY + 4f), DROP_TILE - 8, color);
			}
			float middle = y + DROP_ROW / 2f - 3.4f;
			String valueText = PanelKit.compact(value);
			float valueWidth = ui.boldWidthScaled(valueText, PanelKit.META);
			float right = cardWidth() - PanelKit.PAD;
			ui.textAtScaledBold(valueText, right - valueWidth, middle, PanelKit.META, HudTheme.text());
			right -= valueWidth + 8f;
			if (count >= 0 && (showCounts.get() || HudEditor.isOpen())) {
				String countText = "×" + ProfitTracker.count(count);
				float countWidth = ui.widthScaled(countText, PanelKit.SMALL);
				float before = Draw.alpha();
				Draw.setAlpha(before * HudEditor.partAlpha(showCounts.get()));
				ui.textAtScaled(countText, right - countWidth, middle + 0.5f, PanelKit.SMALL, HudTheme.secondary());
				Draw.setAlpha(before);
				part("Item counts", showCounts, right - countWidth, y, countWidth, DROP_ROW);
				right -= countWidth + 6f;
			}
			float nameX = x + DROP_TILE + 6f;
			ui.textAtScaledBold(PanelKit.fitWords(ui, name, right - nameX, PanelKit.META), nameX, middle, PanelKit.META, PanelKit.readable(color));
		} finally {
			Draw.setAlpha(alphaBefore);
		}
	}

	/** How large an item is drawn on its drop tile. */
	private static final float DROP_ITEM = 0.62f;

	/** The rarity colors of items by id, read once from the name color of the item as the game shows it. */
	private final Map<String, Integer> rarityColors = new HashMap<>();
	private static final int MOST_RARITY_COLORS = 512;

	/** @return the item's rarity color (the color of its name), or the fallback when its name has none */
	private int rarityColor(String id, ItemStack icon, int fallback) {
		Integer known = rarityColors.get(id);
		if (known != null) {
			return known == 0 ? fallback : known;
		}
		TextColor color = icon == null || icon.isEmpty() ? null : nameColor(icon.getHoverName());
		int found = color == null || (color.getValue() & 0xFFFFFF) == 0xFFFFFF ? 0 : 0xFF000000 | color.getValue();
		if (rarityColors.size() < MOST_RARITY_COLORS) {
			rarityColors.put(id, found);
		}
		return found == 0 ? fallback : found;
	}

	/** @return the first color in a name, its own or that of its first colored part */
	private static TextColor nameColor(Component name) {
		if (name.getStyle().getColor() != null) {
			return name.getStyle().getColor();
		}
		for (Component part : name.getSiblings()) {
			TextColor color = nameColor(part);
			if (color != null) {
				return color;
			}
		}
		return null;
	}

	/** The breakdown: the small ring of every kind's share, the total, per hour and the labels beside it, and a row per kind at full width below. */
	private void drawBreakdown(Canvas ui, float top) {
		float centerX = PanelKit.PAD + DONUT / 2f;
		float centerY = top + DONUT / 2f;
		drawArcs(ui, centerX, centerY);
		// the total takes the room beside the ring, and gets smaller rather than ever reaching over it or past the edge
		float textX = PanelKit.PAD + DONUT + DONUT_GAP;
		float textRoom = cardWidth() - PanelKit.PAD - textX;
		String totalText = ProfitText.total(total.value());
		float totalSize = Math.min(TOTAL_SIZE, textRoom / Math.max(1f, ui.boldWidthScaled(totalText, 1f)));
		ui.textAtScaledBold(totalText, textX, centerY - TOTAL_ABOVE_MIDDLE, totalSize, PanelKit.readable(PanelKit.GOLD));
		boolean corpses = activity == ProfitActivity.CORPSES;
		ProfitTracker.Bucket shownBucket = shownTracker.bucket(shownRange());
		String rate = corpses ? PanelKit.compact(perCorpseValue(shownBucket)) : PanelKit.compact(framePerHour);
		float rateY = centerY + RATE_BELOW_MIDDLE;
		ui.textAtScaledBold(rate, textX, rateY, PanelKit.SMALL, HudTheme.text());
		ui.textAtScaled(ui.fitScaled(corpses ? " per corpse" : " per hour", textRoom - ui.boldWidthScaled(rate, PanelKit.SMALL), PanelKit.SMALL), textX + ui.boldWidthScaled(rate, PanelKit.SMALL), rateY,
			PanelKit.SMALL, HudTheme.secondary());
		drawViewLabels(ui, centerY + LABELS_BELOW_MIDDLE, textX);
		float x = PanelKit.PAD;
		float right = cardWidth() - PanelKit.PAD;
		int categories = Math.min(MOST_CATEGORIES, rowCount());
		float y = top + DONUT + PanelKit.GAP;
		double all = Math.max(1e-9, frameGross);
		ProfitTracker.Bucket bucket = shownTracker == null ? current().bucket(range.get()) : shownTracker.bucket(shownRange());
		// two clean columns: the values right-aligned at the right edge, the shares right-aligned before them; each as wide as its widest row, so
		// the names keep as much room as they can
		valueColumn = 0f;
		percentColumn = 0f;
		int measured = 0;
		for (int i = 0; i < listed.size() && measured < categories; i++, measured++) {
			Spring[] springs = rows.get(listed.get(i).id);
			measureColumns(ui, springs == null ? listedValues[i] : springs[1].value(), all);
		}
		if (bucket.coins > 0 && measured < categories) {
			measureColumns(ui, bucket.coins, all);
			measured++;
		}
		if (otherShown && measured < categories) {
			measureColumns(ui, otherValue, all);
		}
		int shown = 0;
		for (int i = 0; i < listed.size() && shown < categories; i++, shown++) {
			Spring[] springs = rows.get(listed.get(i).id);
			double value = springs == null ? listedValues[i] : springs[1].value();
			category(ui, x, right, y, sliceColor(i), SkyBlockPrices.clean(listed.get(i).name), value / all, value);
			y += CATEGORY_ROW;
		}
		if (bucket.coins > 0 && shown < categories) {
			category(ui, x, right, y, sliceColor(listed.size()), "Coins", bucket.coins / all, bucket.coins);
			y += CATEGORY_ROW;
			shown++;
		}
		if (otherShown && shown < categories) {
			category(ui, x, right, y, otherColor(), "Other", otherValue / all, otherValue);
		}
	}

	/** The widths of the breakdown's two columns, measured on their widest rows each frame. */
	private float valueColumn;
	private float percentColumn;

	/** Widens the breakdown's columns to a row's value and share. */
	private void measureColumns(Canvas ui, double value, double all) {
		valueColumn = Math.max(valueColumn, ui.boldWidthScaled(PanelKit.compact(value), PanelKit.SMALL));
		percentColumn = Math.max(percentColumn, ui.widthScaled(Math.round(value / all * 100) + "%", PanelKit.SMALL));
	}
	/** The room between the name, the share and the value columns. */
	private static final float COLUMN_GAP = 5f;

	/**
	 * Draws a kind of the breakdown: its color mark, its full name, and its share and value in their columns.
	 */
	private void category(Canvas ui, float x, float right, float y, int color, String name, double share, double value) {
		float middle = y + CATEGORY_ROW / 2f;
		ui.roundedSmooth(x, middle - 2.5f, 5f, 5f, 1.5f, color);
		String valueText = PanelKit.compact(value);
		ui.textAtScaledBold(valueText, right - ui.boldWidthScaled(valueText, PanelKit.SMALL), middle - 3f, PanelKit.SMALL, HudTheme.text());
		String percent = Math.round(share * 100) + "%";
		float percentRight = right - valueColumn - COLUMN_GAP;
		ui.textAtScaled(percent, percentRight - ui.widthScaled(percent, PanelKit.SMALL), middle - 3f, PanelKit.SMALL, HudTheme.secondary());
		float nameX = x + 9f;
		float room = percentRight - percentColumn - COLUMN_GAP - nameX;
		ui.textAtScaledBold(PanelKit.fitWords(ui, name, room, PanelKit.SMALL), nameX, middle - 3f, PanelKit.SMALL, HudTheme.text());
	}

	/**
	 * Draws the donut as smooth arcs: a slice per listed item in its color, one for coins, and a muted one for the rest, with equal small gaps
	 * between them. A slice covering the whole ring is drawn as a full ring without a gap.
	 */
	private void drawArcs(Canvas ui, float centerX, float centerY) {
		var graphics = ui.graphics();
		float radius = DONUT / 2f;
		if (frameGross <= 0) {
			ui.arc(centerX, centerY, radius, DONUT_THICKNESS, 0f, 1f, Draw.faded(Draw.withOpacity(HudTheme.text(), 0.08f)));
			return;
		}
		int count = 0;
		int index = 0;
		for (ProfitTracker.Entry entry : listed) {
			Spring[] springs = rows.get(entry.id);
			float share = springs == null ? 0f : Mth.clamp(springs[0].value(), 0f, 1f);
			if (share > 0.002f && count < SLICES) {
				sliceShares[count] = share;
				sliceColors[count++] = sliceColor(index);
			}
			index++;
		}
		Spring[] coins = rows.get(COINS_KEY);
		if (coins != null && coins[0].value() > 0.002f && count < SLICES) {
			sliceShares[count] = Mth.clamp(coins[0].value(), 0f, 1f);
			sliceColors[count++] = sliceColor(index);
		}
		float listedShare = 0f;
		for (int i = 0; i < count; i++) {
			listedShare += sliceShares[i];
		}
		if (listedShare < 0.995f && count < SLICES) {
			sliceShares[count] = 1f - listedShare;
			sliceColors[count++] = otherColor();
		}
		float start = 0f;
		for (int i = 0; i < count; i++) {
			if (count == 1) {
				ui.arc(centerX, centerY, radius, DONUT_THICKNESS, 0f, 1f, Draw.faded(sliceColors[i]));
			} else if (sliceShares[i] > 0.002f) {
				ui.arcSegment(centerX, centerY, radius, DONUT_THICKNESS, start, start + sliceShares[i], Draw.faded(sliceColors[i]));
			}
			start += sliceShares[i];
		}
	}

	/** The donut's slices, reused every frame. */
	private static final int SLICES = 16;
	private final float[] sliceShares = new float[SLICES];
	private final int[] sliceColors = new int[SLICES];

	/**
	 * @return the color of the item at a list position, for its slice and dot
	 */
	private int sliceColor(int index) {
		// Clearly distinct colors, like Apple's charts: shades of one hue next to the accent were too similar to tell apart. The list starts with the
		// color closest to the accent, so the most valuable item keeps the panel color.
		int accent = accentArgb();
		if (accent != colorsFor) {
			colorsFor = accent;
			firstColor = nearestColor(accent);
		}
		return 0xFF000000 | CHART_COLORS[(firstColor + index) % CHART_COLORS.length];
	}

	/** The accent the starting color was computed for, and that color; recomputed only when the accent changes. */
	private int colorsFor;
	private int firstColor;

	private static int nearestColor(int accent) {
		float accentHue = Color.RGBtoHSB(accent >> 16 & 0xFF, accent >> 8 & 0xFF, accent & 0xFF, null)[0];
		int first = 0;
		float nearest = 2f;
		for (int i = 0; i < CHART_COLORS.length; i++) {
			int c = CHART_COLORS[i];
			float hue = Color.RGBtoHSB(c >> 16 & 0xFF, c >> 8 & 0xFF, c & 0xFF, null)[0];
			float distance = Math.min(Math.abs(hue - accentHue), 1f - Math.abs(hue - accentHue));
			if (distance < nearest) {
				nearest = distance;
				first = i;
			}
		}
		return first;
	}

	/** Blue, green, orange, pink, purple, yellow, cyan, red, indigo, mint: adjacent colors are always far apart. */
	private static final int[] CHART_COLORS = {SystemColors.BLUE_RGB, SystemColors.GREEN_RGB, SystemColors.ORANGE_RGB, SystemColors.PINK_RGB, SystemColors.PURPLE_RGB, SystemColors.YELLOW_RGB, SystemColors.TEAL_RGB, SystemColors.RED_RGB, SystemColors.INDIGO_RGB, 0x66D4CF};

	/** Visibility of the Gummy Polar Bear reminder from 0 to 1: its row opens and closes. */
	private final Spring gummyShown = Spring.smooth(Spring.GENTLE);

	/** Whether the Gummy Polar Bear reminder shows now, as worked out for this frame. */
	private boolean gummyNow;

	private boolean gummyWanted() {
		return gummyNow;
	}

	/** Draws the Gummy Polar Bear reminder as one quiet line: a small red dot and the note, fading in. */
	private void drawGummy(Canvas ui, float y, float shown) {
		float before = Draw.alpha();
		Draw.setAlpha(before * shown);
		float centerY = y + GUMMY_ROW / 2f - 1f;
		ui.discAt(PanelKit.PAD + 2.5f, centerY, 4f, SystemColors.RED_TEXT);
		ui.textAtScaled(GUMMY_NOTE, PanelKit.PAD + 8f, centerY - 3f, PanelKit.SMALL, HudTheme.secondary());
		Draw.setAlpha(before);
	}

	private static final String GUMMY_NOTE = "No Gummy Polar Bear active";

	/**
	 * Draws the quest costs deducted from the total: not a drop (it is neither a gain nor part of the donut) but a quiet line of its own below a
	 * line: the label in gray, the amount in a soft red.
	 */
	private void drawSpawnCosts(Canvas ui, int y, double spent) {
		PanelKit.divider(ui, cardWidth(), y);
		float textY = y + 5f;
		String label = switch (activity) {
			case CORPSES -> "Keys used";
			case EXPERIMENTS -> "XP bottles used";
			case DRAGONS -> "Summoning Eyes placed";
			default -> "Spawn costs";
		};
		ui.textAtScaled(label, PanelKit.PAD, textY, PanelKit.META, HudTheme.secondary());
		// with the mayor perk that halves them, a fainter note after the label
		if (activity == ProfitActivity.SLAYER && Mayor.hasPerk("Slashed Pricing")) {
			ui.textAtScaled("· halved", PanelKit.PAD + ui.widthScaled(label, PanelKit.META) + 4f, textY, PanelKit.META,
				Draw.withOpacity(HudTheme.secondary(), 0.6f));
		}
		String value = "-" + PanelKit.compact(spent);
		ui.textAtScaledBold(value, cardWidth() - PanelKit.PAD - ui.boldWidthScaled(value, PanelKit.META), textY, PanelKit.META,
			Draw.mix(HudTheme.text(), SystemColors.RED, 0.65f));
	}

	/** @return the color of the unlisted items, in the donut and in their row */
	private static int otherColor() {
		return Draw.withOpacity(HudTheme.secondary(), 0.55f);
	}

	private static ItemStack coinsIcon;

	private static ItemStack otherIcon;

	private static ItemStack otherIcon() {
		if (otherIcon == null) {
			otherIcon = new ItemStack(Items.BUNDLE);
		}
		return otherIcon;
	}

	private static ItemStack coinsIcon() {
		if (coinsIcon == null) {
			coinsIcon = new ItemStack(Items.GOLD_NUGGET);
		}
		return coinsIcon;
	}

	// ------------------------------------------------------------------------------------------------ persistence

	@Override
	public JsonObject save() {
		JsonObject json = super.save();
		json.add("profit", tracker.toJson());
		if (!slayers.isEmpty()) {
			JsonObject each = new JsonObject();
			slayers.forEach((kind, profit) -> each.add(kind.name(), profit.toJson()));
			json.add("slayers", each);
		}
		if (slayer != null) {
			json.addProperty("slayer", slayer.name());
		}
		if (gummy.activeUntil() > System.currentTimeMillis()) {
			json.addProperty("gummy_until", gummy.activeUntil());
		}
		return json;
	}

	@Override
	public void load(JsonObject json) {
		super.load(json);
		if (json.has("profit") && json.get("profit").isJsonObject()) {
			tracker.fromJson(json.getAsJsonObject("profit"));
			savedChanges = tracker.changes();
		}
		if (json.has("slayers") && json.get("slayers").isJsonObject()) {
			for (var entry : json.getAsJsonObject("slayers").entrySet()) {
				try {
					ProfitTracker profit = new ProfitTracker();
					profit.fromJson(entry.getValue().getAsJsonObject());
					slayers.put(Slayer.valueOf(entry.getKey()), profit);
				} catch (RuntimeException ignored) {
				}
			}
		}
		if (json.has("gummy_until")) {
			gummy.setActiveUntil(json.get("gummy_until").getAsLong());
		}
		if (json.has("slayer")) {
			try {
				slayer = Slayer.valueOf(json.get("slayer").getAsString());
			} catch (RuntimeException ignored) {
			}
		}
	}
}
