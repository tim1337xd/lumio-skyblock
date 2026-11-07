package dev.tim1337.lumio.skyblock.slayer;

import dev.tim1337.lumio.skyblock.SkyBlockFeatures;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.util.Debug;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;
import dev.tim1337.lumio.skyblock.profit.Slayer;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads one player's slayer boss from the name tags above it, which everyone can see: the boss ("☠ Voidgloom Seraph IV 45M❤", or "15 Hits" while
 * its shield is up), "Spawned by: <name>" below it, and for the Inferno Demonlord its attunement ("ASHEN ♨12"). It keeps the state of that fight
 * (the phase reached, the Tarantula's webs and egg sacs, the steady health), so the player's own boss and a carried player's boss each have one.
 */
final class BossReader {
	/** Health and hits in a boss name, as {@link BossNameText} reads them. */
	private static final Pattern HEALTH = BossNameText.HEALTH;
	private static final Pattern HITS = BossNameText.HITS;
	private static final Pattern ATTUNEMENT = Pattern.compile("(ASHEN|SPIRIT|CRYSTAL|AURIC)\\s*♨?\\s*(\\d+)?");
	/** Name tags are parsed every tick, so the patterns are compiled once. */
	private static final Pattern TIMER = Pattern.compile("^\\d{1,2}:\\d{2}$");
	/** A health value and everything after it, at the end of a name tag. */
	private static final Pattern HEALTH_TAIL = Pattern.compile("[\\d.,]+[kKmMbB]?❤.*$");
	private static final Pattern WEB_TIERS = Pattern.compile("\\b(iii|iv|v)\\b");
	private static final Pattern LAST_TIER = Pattern.compile("\\bv\\b");
	/** Parts of a boss name that are not its title: the skull, the level, and the health or hit count after it. */
	private static final Pattern TITLE_JUNK = Pattern.compile("☠|\\[Lv\\s*\\d+\\]\\s*|[\\d.,]+[kKmMbB]?(/[\\d.,]+[kKmMbB]?)?❤.*$|(?i)\\d{1,4}[^\\p{Alnum}]{0,3}hits?\\b.*$");

	/** Boss icons, created once. */
	private static ItemStack[] icons;

	/** @return a boss icon: 0 Voidgloom, 1 Inferno, 2 Tarantula, 3 Sven, 4 Revenant, 5 any other */
	private static ItemStack icon(int which) {
		if (icons == null) {
			icons = new ItemStack[]{new ItemStack(Items.ENDER_PEARL), new ItemStack(Items.BLAZE_ROD), new ItemStack(Items.STRING), new ItemStack(Items.BONE),
				new ItemStack(Items.ROTTEN_FLESH), new ItemStack(Items.IRON_SWORD)};
		}
		return icons[which];
	}

	/** The entity id of the boss whose phase is tracked, and the furthest phase it reached. */
	private int phaseBoss = -1;
	private int phaseReached = 1;
	/** Number of Tarantula webs started in the current fight. */
	private int websStarted;
	/** When a web was last seen in progress: its egg sacs, or the hatchlings message. */
	private long webAt;
	/** How long after its last sign a web still counts as in progress. */
	private static final long WEB_HOLD_MILLIS = 1500L;
	/** The highest health seen for the boss, which the remaining health is shown against. */
	private double maxSeen;
	private String maxFor = "";

	/** When the Tarantula last announced that its hatchlings must be killed first; it cannot be damaged until they are. */
	private long hatchlingsAt;
	/** How long after the hatchlings message the Tarantula counts as invulnerable. */
	private static final long HATCHLINGS_MILLIS = 3000L;
	/** Since when the Tarantula's health has been below the floor of its phase; 0 if it is not. */
	private long belowFloorSince;
	/** Whether the Tarantula took damage since its last web (or since spawning); only then can a new web start. */
	private boolean damagedSinceWeb;
	/** Whether this fight's Conjoined Brood was seen with health: at 0 afterwards it is slain, at 0 before that it is still fusing. */
	private boolean broodAlive;
	/** When the quest was turned in, i.e. its boss died; 0 if a new quest started since. */
	private long questDoneAt;
	/** How long after turning in the quest the boss is shown as dying. */
	private static final long DYING_MILLIS = 10_000L;
	/** The tick counter for the egg sac scan, which runs every few ticks, and whether the sacs were scanned (or kept) in this tick. */
	private int eggTick;
	private boolean eggsLooked;

	/** Resets the state of the previous fight: its webs and which boss it was. */
	void newFight() {
		voidgloom.clear();
		fightStartedAt = System.currentTimeMillis();
		phaseBoss = -1;
		websStarted = 0;
		webAt = 0;
		belowFloorSince = 0;
		hatchlingsAt = 0;
		damagedSinceWeb = false;
		broodAlive = false;
	}

	private SlayerBoss readAt(Minecraft client, List<SlayerTag> tags, Entity spawnedBy, double beaconBlocks, boolean own) {
		// the boss's name tags form a column: the boss right above "Spawned by", with the timer and attunement nearby
		// a fast boss's stands (the Tarantula jumps) are not always in an exact column, so the ones closest to "Spawned by" are taken
		String bossName = null;
		double bossDistance = Double.MAX_VALUE;
		Entity bossEntity = null;
		String timer = "";
		String attunement = null;
		// the hits of a shield on a line of their own, below or above the boss's name, when the server writes them apart
		String hitsLine = null;
		double hitsDistance = Double.MAX_VALUE;
		StringBuilder debugLines = Debug.on() ? new StringBuilder() : null;
		for (SlayerTag tag : tags) {
			// a boss that hatched from a cocoon has its tags spaced slightly further apart
			double distance = tag.entity().distanceTo(spawnedBy);
			if (distance > 6) {
				continue;
			}
			String text = SkyBlockArea.plainLine(tag.text());
			if (text.contains("Spawned") || text.contains("SLAYER BOSS")) {
				continue;
			}
			boolean hitsOnly = !text.contains("☠") && !text.contains("❤") && Slayer.of(text) == null && HITS.matcher(text).find();
			if (debugLines != null && (hitsOnly || text.contains("☠") || text.contains("❤") || TIMER.matcher(text.strip()).matches()
				|| ATTUNEMENT.matcher(text).find())) {
				// only the boss's own lines (its name, health or hits, timer, attunement): never a "Spawned by" line or anything with a player's name
				debugLines.append(debugLines.isEmpty() ? "" : " | ").append(tag.text());
			}
			if (hitsOnly) {
				if (distance < hitsDistance) {
					hitsLine = text;
					hitsDistance = distance;
				}
			} else if (text.contains("☠") || text.contains("❤") || HITS.matcher(text).find()) {
				// prefer the slayer boss over other mobs nearby (area mobs also show health in their names); of two, the closer one
				boolean slayerName = Slayer.of(text) != null;
				boolean wasSlayer = bossName != null && Slayer.of(bossName) != null;
				// Of two slayer boss names, take the one with less health: a tag that was not updated yet (rendered a moment after the other) still shows
				// more, and the panel showed 3.8M while the boss had 3.6M. Otherwise the closer one.
				double tagHealth = healthOf(text);
				double bestHealth = bossName == null ? -1 : healthOf(bossName);
				boolean better;
				if (bossName == null) {
					better = true;
				} else if (slayerName != wasSlayer) {
					better = slayerName;
				} else if (slayerName && tagHealth >= 0 && bestHealth >= 0 && tagHealth != bestHealth && bossEntity != null
					&& tag.entity().distanceTo(bossEntity) < 2f) {
					// two names in the same column belong to the same boss (another player's boss a few blocks away is not compared this way)
					better = tagHealth < bestHealth;
				} else {
					better = distance < bossDistance;
				}
				if (better) {
					bossName = text;
					bossDistance = distance;
					bossEntity = tag.entity();
				}
			} else if (TIMER.matcher(text.strip()).matches()) {
				timer = text.strip();
			}
			Matcher tune = ATTUNEMENT.matcher(text);
			if (tune.find() && !text.contains("☠")) {
				attunement = tune.group(1) + (tune.group(2) == null ? "" : " · " + tune.group(2) + " hits");
			}
		}
		if (bossName == null) {
			return null;
		}
		if (debugLines != null) {
			logTags(debugLines.toString());
		}
		String lower = bossName.toLowerCase(Locale.ROOT);
		// slayer bosses only: an event mob spawned for the player too (a Primal Fear) is not one
		if (Slayer.of(lower) == null && !lower.contains("conjoined")) {
			return null;
		}
		// "[Lv396] Tarantula Broodfather V 3.6M/10M❤": the level in front, current and max health after it
		String title = TITLE_JUNK.matcher(bossName).replaceAll("").strip();
		double health = -1;
		double most = -1;
		Matcher hp = HEALTH.matcher(bossName);
		if (hp.find()) {
			health = BossNameText.number(hp.group(1), hp.group(2));
			if (hp.group(3) != null) {
				most = BossNameText.number(hp.group(3), hp.group(4));
			}
		}
		health = steady(spawnedBy.getId() + "|" + title, health);
		if (!title.equals(maxFor)) {
			maxFor = title;
			maxSeen = 0;
		}
		maxSeen = most > 0 ? most : Math.max(maxSeen, health);
		double fraction = maxSeen > 0 && health >= 0 ? health / maxSeen : 1;
		// the health third it is in: most bosses change behavior at two thirds and one third
		// reset for a new boss, or one at full health again (a boss hatched from a cocoon can reappear on the same stands)
		if (spawnedBy.getId() != phaseBoss || websStarted > 0 && fraction > 0.9) {
			newFight();
			phaseBoss = spawnedBy.getId();
		}
		// From tier III the Tarantula does not skip a phase: a hit that takes it below two thirds (or one third) shows that briefly, then the web
		// starts and its health is reset to the threshold. So its phase is the number of webs started, and its health never below that phase's end.
		boolean webbed = (lower.contains("tarantula") || lower.contains("broodfather")) && WEB_TIERS.matcher(lower).find();
		if (webbed) {
			// The egg sacs appear with a web, before the server message: they are scanned for once the health approaches the next web threshold,
			// and a web starts when they are seen or when the message arrives, whichever comes first.
			long now = System.currentTimeMillis();
			findEggSacs(client, spawnedBy, websStarted > 0 || fraction <= 2 / 3.0 + 0.02);
			double top = (3 - Math.min(2, websStarted)) / 3.0;
			// egg sacs and shield messages keep the web active; once they stop, health tracking resumes after a short delay
			boolean held = websStarted > 0 && now - webAt < WEB_HOLD_MILLIS;
			// A web only starts after the boss took damage since the previous one: a hit dropping it far (10M to 2M at once) is still one web, as in
			// the game, which resets its health to the threshold. The sacs of one web (shot one by one) and the messages belong to the same web.
			if (!held && fraction < top - 0.02) {
				damagedSinceWeb = true;
			}
			// the shield message only starts a web near the next threshold: at full health (tier V spawns shielded) it does not
			boolean shieldMessage = now - hatchlingsAt < 1000L;
			double nextLine = (2 - Math.min(2, websStarted)) / 3.0;
			boolean webNow = !eggSacs.isEmpty() || shieldMessage && fraction <= nextLine + 0.03;
			if (webNow && damagedSinceWeb && websStarted < 2) {
				websStarted++;
				damagedSinceWeb = false;
				webAt = now;
			} else if (!eggSacs.isEmpty() || shieldMessage && websStarted > 0) {
				webAt = now;
			}
			top = (3 - Math.min(2, websStarted)) / 3.0;
			double floor = websStarted >= 2 ? 0 : top - 1 / 3.0;
			held = websStarted > 0 && now - webAt < WEB_HOLD_MILLIS;
			// Only if the health stays below the floor for a long time without a web was a web missed: the dip before a web resets it lasts a few
			// seconds at most.
			if (fraction < floor - 0.01 && !held && eggSacs.isEmpty()) {
				if (belowFloorSince == 0) {
					belowFloorSince = now;
				} else if (now - belowFloorSince > 10_000L && websStarted < 2) {
					websStarted++;
					damagedSinceWeb = false;
					belowFloorSince = 0;
					top = (3 - Math.min(2, websStarted)) / 3.0;
					floor = websStarted >= 2 ? 0 : top - 1 / 3.0;
				}
			} else {
				belowFloorSince = 0;
			}
			// between webs the health stays within the phase; during a web the boss is held at its threshold
			fraction = held ? top : Mth.clamp(fraction, floor, websStarted == 0 ? 1.0 : top);
		}
		int third = fraction > 2 / 3.0 ? 1 : fraction > 1 / 3.0 ? 2 : 3;
		if (webbed) {
			third = Math.min(third, websStarted + 1);
		}
		phaseReached = third;
		Matcher hits = HITS.matcher(bossName);
		String phase;
		int color;
		String detail = "";
		ItemStack icon;
		VoidgloomPhases.View phases = null;
		if (lower.contains("voidgloom")) {
			icon = icon(0);
			// every hit shield is a phase of its own, and every part between them: its hits from the name, or from a line of their own
			int hitsLeft = BossNameText.hits(bossName);
			if (hitsLeft < 0 && hitsLine != null) {
				hitsLeft = BossNameText.hits(hitsLine);
			}
			voidgloom.tier(BossNameText.tier(title));
			voidgloom.read(hitsLeft, health >= 0 && maxSeen > 0 ? health / maxSeen : -1);
			phases = voidgloom.view();
			if (beaconBlocks >= 0) {
				phase = "Beacon! Break it";
				color = SystemColors.RED;
				detail = Math.round(beaconBlocks) + " blocks away";
			} else if (phases.hits()) {
				phase = "Hits";
				color = SystemColors.PURPLE;
			} else {
				phase = "Damage";
				color = SystemColors.GREEN;
			}
		} else if (lower.contains("inferno") || lower.contains("demonlord")) {
			icon = icon(1);
			SlayerTag demon = SlayerTag.near(tags, spawnedBy, 20, text -> text.contains("Quazii") || text.contains("Typhoeus"));
			if (demon != null) {
				phase = "Demons: kill them";
				color = SystemColors.ORANGE;
				detail = SkyBlockArea.plainLine(demon.text()).replace("☠", "");
				detail = HEALTH_TAIL.matcher(detail).replaceAll("").strip();
			} else if (attunement != null) {
				phase = attunement.split(" ")[0];
				color = switch (phase) {
					case "ASHEN" -> SystemColors.GRAY;
					case "SPIRIT" -> 0xFFFFFFFF;
					case "CRYSTAL" -> SystemColors.TEAL;
					default -> SystemColors.YELLOW;
				};
				detail = attunement.contains("·") ? attunement.substring(attunement.indexOf('·') + 2) + "  ·  phase " + third + " of 3" : "Phase " + third + " of 3";
			} else {
				phase = "Phase " + third + " of 3";
				color = SystemColors.ORANGE;
			}
		} else if (lower.contains("conjoined")) {
			// Tier V, after the Broodfather dies: it fuses with the Primordial Broodmother into the Conjoined Brood, with 20M health of its own. While
			// fusing its health reads 0.
			icon = icon(2);
			// at 0 health before it ever had health it is still fusing; after that it is slain
			if (health > 0) {
				broodAlive = true;
			}
			if (System.currentTimeMillis() - questDoneAt < DYING_MILLIS || health <= 0 && broodAlive) {
				phase = "Slain";
				color = SystemColors.GRAY;
			} else if (health <= 0) {
				phase = "Shielded";
				color = SystemColors.ORANGE;
			} else {
				phase = "Damage phase";
				color = SystemColors.GREEN;
			}

		} else if (lower.contains("tarantula") || lower.contains("broodfather")) {
			icon = icon(2);
			// Only three phases: egg sacs above the boss that must be shot before they hatch (Web of Lies, from tier III); shielded, while it cannot be
			// damaged until its hatchlings are dead; otherwise the boss can be hit.
			// At 0 health it is dying: hits then report it cannot be damaged, which is not a shield. The scoreboard's "Boss slain!" is not used, since
			// it can appear while the boss is still fighting.
			// Tier V is not slain at 0 health: it turns into the Conjoined Brood, its final stage (the same boss), and cannot be hit meanwhile
			boolean lastTier = LAST_TIER.matcher(lower).find();
			boolean dying = System.currentTimeMillis() - questDoneAt < DYING_MILLIS || !lastTier && (health == 0
				|| System.currentTimeMillis() - hatchlingsAt < HATCHLINGS_MILLIS && maxSeen > 0 && health >= 0 && health / maxSeen < 0.03);
			if (dying) {
				phase = "Slain";
				color = SystemColors.GRAY;
			} else if (lastTier && health == 0) {
				phase = "Shielded";
				color = SystemColors.ORANGE;
			} else if (!eggSacs.isEmpty()) {
				phase = "Shoot the eggs!";
				color = SystemColors.RED;
				detail = eggSacs.size() + " left";
			} else if (System.currentTimeMillis() - hatchlingsAt < HATCHLINGS_MILLIS) {
				// the server reports the boss cannot be damaged right now
				phase = "Shielded";
				color = SystemColors.ORANGE;
			} else {
				phase = "Damage phase";
				color = SystemColors.GREEN;
				detail = hits.find() ? hits.group(1) + " hits left" : "";
			}
		} else if (lower.contains("sven")) {
			icon = icon(3);
			boolean pups = SlayerTag.near(tags, spawnedBy, 15, text -> text.contains("Pup")) != null;
			phase = pups ? "Pups: kill them" : fraction <= 0.5 ? "Second half" : "First half";
			color = pups ? SystemColors.ORANGE : fraction <= 0.5 ? SystemColors.RED : SystemColors.GREEN;
		} else if (lower.contains("revenant") || lower.contains("atoned")) {
			icon = icon(4);
			phase = fraction <= 0.5 ? "Enraged" : "Phase 1 of 2";
			color = fraction <= 0.5 ? SystemColors.RED : SystemColors.GREEN;
		} else {
			icon = icon(5);
			phase = hits.find() ? hits.group(1) + " hits" : "Phase " + third + " of 3";
			color = SystemColors.GREEN;
		}
		// the bar shows the health as the phase allows, not the dip of a hit that the web resets
		// the Conjoined Brood is the Tarantula's final stage, not a separate boss: it keeps the name of the slayer being done
		String shownTitle = title;
		if (lower.contains("conjoined")) {
			String quest = own ? SkyBlockArea.slayerBoss() : null;
			shownTitle = quest != null && quest.toLowerCase(Locale.ROOT).contains("tarantula") ? quest.strip() : "Tarantula Broodfather V";
		}
		lastBoss = new SlayerBoss(shownTitle, maxSeen > 0 && health >= 0 ? fraction * maxSeen : health, maxSeen, phase, color, detail, timer, icon,
			!lower.contains("conjoined"), phases);
		lastBossAt = System.currentTimeMillis();
		return lastBoss;
	}

	/** The fight's Voidgloom phases, and the last boss read with when, kept for a moment while its name tag is not seen. */
	private final VoidgloomPhases voidgloom = new VoidgloomPhases();
	private SlayerBoss lastBoss;
	private long lastBossAt;
	/** How long a boss with phases stays as last read while its name tag is not seen (it turned away, or the tag is updated). */
	private static final long TAG_HOLD_MILLIS = 1500L;
	/** The boss's lines as last written to the debug log, and when the fight started, for the log's times. */
	private String loggedTags = "";
	private long fightStartedAt;

	/** With the debug log on: the boss's name tag lines, each time they change, with the time since the fight started. */
	private void logTags(String lines) {
		if (!lines.equals(loggedTags)) {
			loggedTags = lines;
			long since = fightStartedAt == 0 ? 0 : System.currentTimeMillis() - fightStartedAt;
			Debug.log("Slayer boss tags +{} ms: {}", since, lines);
		}
	}

	/** The Tarantula egg sacs currently present, to display and highlight. */
	private final List<Entity> eggSacs = new ArrayList<>();
	/** Every armor stand rendering part of a sac; all of them are outlined. */
	private final List<Entity> eggParts = new ArrayList<>();

	/** @return whether a stand is a part of an egg sac of this boss */
	boolean isEggPart(Entity entity) {
		return eggParts.contains(entity) || eggSacs.contains(entity);
	}

	/** An egg sac's timer below its "SHOOT ME!": "11s 7/9", seconds until it hatches and hits taken. */
	private static final Pattern SAC_TIMER = Pattern.compile("^\\d+s \\d+/\\d+$");

	/**
	 * Finds the Web of Lies egg sacs, like SkyHanni: a sac is a stand named with its timer ("11s 7/9") with a "SHOOT ME!" nearby, and the unnamed
	 * stands right at it are the sac's other parts. The timer stand is a part too.
	 */
	private void findEggSacs(Minecraft client, Entity spawnedBy, boolean webs) {
		eggsLooked = true;
		// every fourth tick (SkyHanni uses every fifth): sacs last seconds, and the entity query around the boss is not needed every tick. In
		// between the found sacs are kept; a sac that was shot leaves the outline once it is gone from the world.
		if (eggTick % 4 != 0) {
			eggSacs.removeIf(Entity::isRemoved);
			eggParts.removeIf(Entity::isRemoved);
			return;
		}
		eggSacs.clear();
		eggParts.clear();
		AABB area = spawnedBy.getBoundingBox().inflate(20, 12, 20);
		List<Entity> near = client.level.getEntities((Entity) null, area, entity -> entity != client.player);
		List<ArmorStand> shootMe = new ArrayList<>();
		List<ArmorStand> timers = new ArrayList<>();
		for (Entity entity : near) {
			if (entity instanceof ArmorStand stand && stand.hasCustomName()) {
				String name = SkyBlockArea.plainLine(stand.getCustomName().getString()).strip();
				if (name.equalsIgnoreCase("SHOOT ME!")) {
					shootMe.add(stand);
				} else if (SAC_TIMER.matcher(name).matches()) {
					timers.add(stand);
				}
			}
		}
		for (ArmorStand timer : timers) {
			if (shootMe.stream().noneMatch(label -> label.distanceTo(timer) < 2.5f)) {
				continue;
			}
			eggSacs.add(timer);
			eggParts.add(timer);
			for (Entity entity : near) {
				if (entity instanceof ArmorStand part && !part.hasCustomName() && part.distanceTo(timer) < 1.2f && !eggParts.contains(part)) {
					eggParts.add(part);
				}
			}
		}

		// Fallback: anything wearing a head (as sacs are rendered) slightly above the boss, but only once the boss reached its webs: before that,
		// other things with heads were mistaken for sacs
		if (eggSacs.isEmpty() && webs) {
			for (Entity entity : near) {
				if (entity instanceof ArmorStand stand && !stand.hasCustomName() && stand.getItemBySlot(EquipmentSlot.HEAD).is(Items.PLAYER_HEAD) && !SkyBlockFeatures.COCOONS.claims(stand)
					&& stand.getY() > spawnedBy.getY() + 0.5 && Math.abs(stand.getX() - spawnedBy.getX()) < 5 && Math.abs(stand.getZ() - spawnedBy.getZ()) < 5
					// a sac consists of several stands at one spot: stands where a sac is already taken belong to that sac
					&& eggSacs.stream().noneMatch(other -> other.distanceToSqr(stand) < 1.2 * 1.2)) {
					eggSacs.add(stand);
				}
			}
		}
		// fallback sacs are rendered by several stacked stands: outline all of them, not only the counted one
		if (eggParts.isEmpty()) {
			eggParts.addAll(eggSacs);
			for (Entity entity : near) {
				if (entity instanceof ArmorStand stand && !eggParts.contains(stand) && !SkyBlockFeatures.COCOONS.claims(stand)
					&& (!stand.getItemBySlot(EquipmentSlot.HEAD).isEmpty() || !stand.getMainHandItem().isEmpty() || !stand.getOffhandItem().isEmpty())
					&& eggSacs.stream().anyMatch(sac -> Math.abs(sac.getX() - stand.getX()) < 1.6 && Math.abs(sac.getZ() - stand.getZ()) < 1.6
						&& Math.abs(sac.getY() - stand.getY()) < 4.0)) {
					eggParts.add(stand);
				}
			}
		}
	}

	/** @return the current health in a name ("3.6M/10M❤" gives 3.6M), or -1 */
	private static double healthOf(String name) {
		return BossNameText.health(name);
	}

	/** The displayed health, which only increases if the boss really has more for a while: for which boss, and since when a higher value is read. */
	private double steadyHealth;
	private String steadyFor = "";
	private long higherSince;

	/**
	 * @return the health to use: a boss's health only drops during a fight. A briefly higher reading is a stale name tag and is ignored; one that
	 *         stays higher for two seconds is real (it was healed, or a web reset it).
	 */
	private double steady(String key, double health) {
		if (health < 0) {
			return health;
		}
		long now = System.currentTimeMillis();
		if (!key.equals(steadyFor) || health <= steadyHealth) {
			steadyFor = key;
			steadyHealth = health;
			higherSince = 0;
			return health;
		}
		if (higherSince == 0) {
			higherSince = now;
		} else if (now - higherSince >= 2000L) {
			steadyHealth = health;
			higherSince = 0;
			return health;
		}
		return steadyHealth;
	}

	/** Starts a tick: the egg sacs are kept only if this tick looks for them. */
	void beginTick(int tick) {
		eggTick = tick;
		eggsLooked = false;
	}

	/** Ends a tick: egg sacs only exist while fighting a Tarantula, so without a look for them in this tick they are cleared. */
	void endTick() {
		if (!eggsLooked) {
			eggSacs.clear();
			eggParts.clear();
		}
	}

	/** Forgets the fight entirely, for a new world. */
	void clear() {
		newFight();
		lastBoss = null;
		questDoneAt = 0;
		eggSacs.clear();
		eggParts.clear();
	}

	/** The server said the Tarantula's hatchlings must be killed first: it cannot be damaged until they are. */
	void hatchlings() {
		hatchlingsAt = System.currentTimeMillis();
	}

	/** The quest was turned in: the boss is dead, even while its name tag lingers for a moment. */
	void questDone() {
		questDoneAt = System.currentTimeMillis();
	}

	/** A new quest started: what follows is a new fight. */
	void questStarted() {
		questDoneAt = 0;
		lastBoss = null;
		newFight();
	}

	/**
	 * Reads the boss spawned by a player, from the name tags near it.
	 *
	 * @param owner  the player's name
	 * @param beaconBlocks how far the beacon of a watched Voidgloom is, in blocks; below zero while there is none
	 * @param own    whether it is the player's own boss (whose quest the scoreboard shows)
	 * @return the boss, or null while none of that player's bosses is near
	 */
	SlayerBoss read(Minecraft client, List<SlayerTag> tags, String owner, double beaconBlocks, boolean own) {
		// the player can have several spawned entities (a Primal Fear, a miniboss): each is tried, and the slayer boss is taken
		List<Entity> spawned = new ArrayList<>();
		for (SlayerTag tag : tags) {
			if (SlayerOwners.ownedBy(tag.text(), owner) && !spawned.contains(tag.entity())) {
				spawned.add(tag.entity());
			}
		}
		for (Entity spawnedBy : spawned) {
			SlayerBoss found = readAt(client, tags, spawnedBy, beaconBlocks, own);
			if (found != null) {
				tag = spawnedBy;
				return found;
			}
		}
		// a boss with phases keeps its last reading for a moment while its name tag is not seen, so its bar never disappears; not once it died
		if (lastBoss != null && lastBoss.phases() != null && questDoneAt == 0 && System.currentTimeMillis() - lastBossAt < TAG_HOLD_MILLIS) {
			return lastBoss;
		}
		tag = null;
		return null;
	}

	/** The "Spawned by" tag of the boss read last; null while none is up. */
	private Entity tag;

	Entity tag() {
		return tag;
	}

	/** Adds the entities that are part of an egg sac of this boss's current web. */
	void addEggParts(List<Entity> into) {
		into.addAll(eggParts);
	}

	/** @return whether egg sacs of this boss are up now */
	boolean anyEggs() {
		return !eggSacs.isEmpty();
	}
}
