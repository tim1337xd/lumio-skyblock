package dev.tim1337.lumio.skyblock.data;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * The player's current Hypixel SkyBlock activity, inferred from what the game shows: location, held item, whether blocks are being broken or the attack
 * key is held, and the scoreboard's slayer quest. Nothing is requested from the server. Each activity tracks when it was last seen, so a panel can
 * linger after it stops and the time spent can be counted.
 */
public enum ProfitActivity {
	MINING("Mining"),
	FARMING("Farming"),
	SLAYER("Slayers"),
	FISHING("Fishing"),
	/** Glacite Mineshaft corpses: its time is time spent in a mineshaft, and its loot is reported in chat, so it takes nothing from the other activities. */
	CORPSES("Corpse"),
	/** The Experimentation Table: its time is time in its menus, and its rewards come from its summary menu. */
	EXPERIMENTS("Experiments"),
	/** Dragon fights: its time is the fight, and what is picked up during and just after one is its own. */
	DRAGONS("Dragon");

	/** Every activity, made once: {@link #values()} copies the array on every call, and the tick asks for it every tick. */
	private static final ProfitActivity[] ALL = values();

	private final String title;
	private long lastSeen;
	private boolean now;

	ProfitActivity(String title) {
		this.title = title;
	}

	public String title() {
		return title;
	}

	/** @return whether the activity is happening right now */
	public boolean now() {
		return now;
	}

	/** @return whether the activity happened within the last given milliseconds */
	public boolean within(long millis) {
		return now || System.currentTimeMillis() - lastSeen < millis;
	}

	public long lastSeen() {
		return lastSeen;
	}

	/** Area the activity was last done in: the mines count as one area (Dwarven Mines, tunnels, mineshafts, hollows), the Garden as one, otherwise the island. */
	private String workArea = "";

	private String areaHere() {
		return switch (this) {
			case MINING, CORPSES -> SkyBlockArea.inMines() ? "mines" : SkyBlockArea.island();
			case FARMING -> SkyBlockArea.inGarden() ? "garden" : SkyBlockArea.island();
			case SLAYER, FISHING, EXPERIMENTS, DRAGONS -> SkyBlockArea.island();
		};
	}

	/** @return how long the activity's panels stay while idle but still in the area: AFK in the mines still counts as mining */
	private long idleLimit() {
		return switch (this) {
			case MINING, FARMING, CORPSES -> 30 * 60_000L;
			case SLAYER, FISHING, EXPERIMENTS, DRAGONS -> 10 * 60_000L;
		};
	}

	/**
	 * @return whether this activity's panels should show: while it happens, and afterwards as long as the player stays in its area. Leaving the area
	 *         (warping to the hub) or the world ends it immediately, as does starting another activity; idling for a long time ends it too. So all
	 *         panels of one activity appear and disappear together, and none vanishes while the player stands still briefly in the mining lobby.
	 */
	public boolean engaged() {
		return engaged;
	}

	/** {@link #engaged()} as computed in the last tick: many panels query it every frame, and the area check is many string comparisons. */
	private boolean engaged;

	private boolean workOutEngaged() {
		if (now) {
			return true;
		}
		long since = System.currentTimeMillis() - lastSeen;
		if (lastSeen == 0 || since > idleLimit()) {
			return false;
		}
		if (!SkyBlock.inSkyBlock()) {
			return false;
		}
		String island = SkyBlockArea.island();
		// between two worlds the island is briefly unknown: that is not leaving
		if (island.isEmpty()) {
			return true;
		}
		// worked there earlier, by an older version or just before the island was known
		if ((this == MINING || this == CORPSES) && !SkyBlockIsland.miningHappensOn(island)) {
			return false;
		}
		if (!areaHere().equals(workArea)) {
			return false;
		}
		// another activity started here (fishing in the mines) takes over after a moment
		for (ProfitActivity other : ALL) {
			// corpses are looted while mining, in the same place: neither replaces the other
			if (other == CORPSES || other == EXPERIMENTS || other == DRAGONS || this == CORPSES && other == MINING) {
				continue;
			}
			if (other != this && other.now && (since > 60_000L || this == SLAYER)) {
				return false;
			}
		}
		return true;
	}

	// ------------------------------------------------------------------------------------------------ player input

	private static long lastSwing;
	private static long lastMove;
	private static double lastX;
	private static double lastY;
	private static double lastZ;
	private static float lastYaw;
	private static float lastPitch;

	/** @return whether the player did anything in the last two minutes; an AFK player does not count as active */
	public static boolean awake() {
		return System.currentTimeMillis() - lastMove < 120_000L;
	}

	private static boolean swingingRecently() {
		return System.currentTimeMillis() - lastSwing < 15_000L;
	}

	private static String heldId(Minecraft client) {
		ItemStack held = client.player.getMainHandItem();
		String id = ItemGains.idOf(held);
		if (id != null) {
			return id;
		}
		return held.isEmpty() ? "" : held.getHoverName().getString().toUpperCase(Locale.ROOT).replace(' ', '_');
	}

	private static boolean miningTool(String id) {
		return id.contains("PICKAXE") || id.contains("DRILL") || id.contains("GAUNTLET") || id.contains("PICKONIMBUS");
	}

	private static boolean farmingTool(String id) {
		return id.contains("HOE") || id.contains("DICER") || id.contains("KNIFE") || id.contains("FUNGI_CUTTER") || id.contains("CACTUS_KNIFE")
			|| id.contains("COCO_CHOPPER") || id.contains("AXE") && id.contains("ROOKIE");
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(ProfitActivity::tick);
	}

	/** The last level, and until when a level change is in progress. */
	private static Object lastLevel;
	private static long changingUntil;

	/**
	 * @return whether the world just changed (a warp, entering a mineshaft) and the location is not known yet: the scoreboard and player list are
	 *         briefly empty then. Panels keep their previous state meanwhile instead of disappearing for a second and coming back.
	 */
	public static boolean changingWorld() {
		return System.currentTimeMillis() < changingUntil && (SkyBlockArea.island().isEmpty() || !SkyBlock.inSkyBlock());
	}

	private static void tick(Minecraft client) {
		long now = System.currentTimeMillis();
		if (client.level != lastLevel) {
			lastLevel = client.level;
			changingUntil = now + 8000L;
		}
		// keep everything as it was until the new world is known
		if (client.level != null && changingWorld()) {
			return;
		}
		if (client.level == null || client.player == null || !SkyBlock.inSkyBlock()) {
			for (ProfitActivity activity : ALL) {
				activity.now = false;
				activity.engaged = false;
			}
			return;
		}
		try {
			see(client, now);
		} finally {
			for (ProfitActivity activity : ALL) {
				activity.engaged = activity.workOutEngaged();
			}
		}
	}

	private static void see(Minecraft client, long now) {
		if (client.options.keyAttack.isDown() || client.gameMode != null && client.gameMode.isDestroying()) {
			lastSwing = now;
		}
		double x = client.player.getX();
		double y = client.player.getY();
		double z = client.player.getZ();
		if (x != lastX || y != lastY || z != lastZ || client.player.getYRot() != lastYaw || client.player.getXRot() != lastPitch || now - lastSwing < 1000) {
			lastMove = now;
			lastX = x;
			lastY = y;
			lastZ = z;
			lastYaw = client.player.getYRot();
			lastPitch = client.player.getXRot();
		}
		String held = heldId(client);
		boolean swinging = swingingRecently();
		// pickaxe abilities (Pickobulus is thrown and breaks blocks without a swing) are a right click with the pickaxe
		if (miningTool(held) && client.options.keyUse.isDown()) {
			lastAbility = now;
		}
		// only while blocks are broken with the tool: holding it, or being on the island, is not mining
		// nothing is mined in the Garden or on the private island: swinging a pickaxe there is not mining
		boolean mining = miningTool(held) && (swinging || now - lastAbility < 15_000L) && !SkyBlockArea.inGarden() && SkyBlockIsland.miningHappensOn(SkyBlockArea.island());
		// farming only happens in the Garden: swinging a farming tool elsewhere (fighting with a dicer in hand) is not farming
		boolean farming = farmingTool(held) && swinging && SkyBlockArea.inGarden();
		// An active quest alone is not slaying: only a progressing quest (a quest mob killed in the last half minute), a spawned boss, or one just
		// slain. And not while breaking blocks with a mining or farming tool.
		boolean quest = SkyBlockArea.slayerQuest();
		// "Boss slain!" stays on the scoreboard until the quest is turned in: only its first minute counts as slaying, otherwise it would keep itself active forever
		boolean slain = SkyBlockArea.bossSlain();
		if (slain && slainAt == 0) {
			slainAt = now;
		} else if (!slain) {
			slainAt = 0;
		}
		boolean fighting = quest && (SkyBlockArea.slayerProgressWithin(30_000L) || SkyBlockArea.slayerBossUp())
			|| slain && now - slainAt < 60_000L;
		// an active quest while mining or farming (its boss is not being fought) is not slaying
		boolean slayer = fighting && !mining && !farming && !MINING.within(30_000L) && !FARMING.within(30_000L);
		// fishing: a line cast with a real rod, or one cast in the last half minute; the time between casts and the fight with the catch still count as fishing
		if (client.player.fishing != null && FishingRods.holding(client.player.getMainHandItem())) {
			lastCast = now;
		}
		boolean fishing = now - lastCast < 30_000L && !mining && !farming;
		set(MINING, mining && awake(), now);
		set(FARMING, farming && awake(), now);
		set(SLAYER, slayer && awake(), now);
		set(FISHING, fishing && awake(), now);
		set(CORPSES, SkyBlockArea.inMineshaft() && awake(), now);
		set(EXPERIMENTS, experimentsNow && awake(), now);
		set(DRAGONS, dragonFightNow, now);
	}

	/** Told by the trackers that see them: a menu of the table is open, a dragon fight is on. */
	private static boolean experimentsNow;
	private static boolean dragonFightNow;

	public static void experiments(boolean now) {
		experimentsNow = now;
	}

	public static void dragonFight(boolean now) {
		dragonFightNow = now;
	}

	private static long lastCast;
	/** When "Boss slain!" first appeared on the scoreboard; 0 while it is not shown. */
	private static long slainAt;

	/** @return the most recent activity: gains belong to it alone, not to every activity done shortly before */
	public static ProfitActivity latest() {
		ProfitActivity latest = null;
		for (ProfitActivity activity : ALL) {
			if (activity == CORPSES || activity == EXPERIMENTS || activity == DRAGONS) {
				continue; // their gains are their own, and never arrive as a gain of the latest activity
			}
			if (activity.lastSeen > 0 && (latest == null || activity.lastSeen > latest.lastSeen)) {
				latest = activity;
			}
		}
		return latest;
	}
	/** When a pickaxe ability was last used: a right click with the pickaxe, or the server's usage message. */
	private static long lastAbility;

	/** The server reported a pickaxe ability use ("You used your Pickobulus Pickaxe Ability!"): that counts as mining, like a swing. */
	public static void pickaxeAbilityUsed() {
		lastAbility = System.currentTimeMillis();
	}

	private static void set(ProfitActivity activity, boolean on, long now) {
		activity.now = on;
		if (on) {
			activity.lastSeen = now;
			String area = activity.areaHere();
			if (!area.isEmpty()) {
				activity.workArea = area;
			}
		}
	}
}
