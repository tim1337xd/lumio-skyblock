package dev.tim1337.lumio.skyblock.slayer;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import net.minecraft.core.BlockPos;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The slayer bosses the player cares about, read once per tick for the slayer panel and the slayer helpers: the player's own boss and, in carry
 * mode, the boss of the player being carried. Everything comes from the name tags the server shows everyone ("Spawned by: <name>"). Voidgloom
 * beacons are told apart by the boss that threw them ({@link BeaconOwners}), so only a watched boss's beacon alerts.
 */
public final class SlayerBosses {
	/** How far around the player bosses and their name tags are read, in blocks. */
	private static final double READ_RANGE = 30.0;
	/** Ticks between boss searches while no boss is up yet; delays the panel by at most a quarter second after the spawn. */
	private static final int SEARCH_INTERVAL_TICKS = 5;
	/** How long a cocooned boss is shown as cocooned before giving up on the quest being turned in. */
	private static final long COCOON_MILLIS = 20_000L;
	/** How far from its "Spawned by" tag the Voidgloom itself (the enderman) stands, in blocks. */
	private static final double BOSS_BODY_RANGE = 4.0;
	/** How far from its "Spawned by" tag a boss's name line stands, in blocks. */
	private static final double BOSS_NAME_RANGE = 3.0;
	/** How far a boss's mob stands from its "Spawned by" tag, in blocks; how far across its own name tags are; and how little health a carried boss had when it counts as killed once it is gone. */
	private static final double BODY_RANGE = 4.0;
	private static final double TAG_COLUMN = 1.5;
	private static final double CARRIED_DEAD_SHARE = 0.15;
	/** The most beacons remembered with their owner; a fight has a few at a time. */
	private static final int MOST_BEACONS = 64;

	private static final BossReader ownReader = new BossReader();
	private static final BossReader carriedReader = new BossReader();
	/** The bosses read in the last tick; null while none is up. */
	private static SlayerBoss own;
	private static SlayerBoss carried;
	/** The last own boss read, shown as cocooned while it lies as a cocoon. */
	private static SlayerBoss lastOwn;
	/** When the own boss was cocooned; 0 if it is not lying as a cocoon. */
	private static long cocoonedAt;
	/** The carried player's name read in the last tick, "" while carry mode is off. */
	private static String carriedName = "";
	/** The thrown beacon of a watched boss, while it flies; null otherwise. */
	private static Entity thrownBeacon;
	/** That beacon through its whole life, also once it is a block. */
	private static final BeaconWatch beaconWatch = new BeaconWatch();
	/** The boss the labels and highlights are about, the carried one first: its "Spawned by" tag, its mob, and the top of its name tags. */
	private static Entity watchedTag;
	private static Entity watchedBody;
	/** The mob found to be the boss, kept through its phases, and which boss it is. */
	private static Entity linkedBody;
	private static String linkedName = "";
	private static boolean linkedCarried;
	private static int noTagTicks;
	private static double watchedTop;
	/** Where the own and the carried boss were last seen, and where and when one of them died; 0 while none has. */
	private static final double[] ownAt = new double[3];
	private static final double[] carriedAt = new double[3];
	private static final double[] deathAt = new double[3];
	private static long deathMillis;
	private static SlayerBoss carriedBefore;
	/** Every beacon seen with the owner of the boss that threw it ("" when that can't be told), by entity id, until it is gone. */
	private static final Map<Integer, String> beaconOwners = new HashMap<>();
	/** When each Voidgloom (by its enderman's entity id) was last seen holding a beacon. */
	private static final Map<Integer, Long> heldAt = new HashMap<>();
	private static Object level;
	private static int tick;

	private SlayerBosses() {
	}

	/** @return whether any feature needs the bosses read */
	private static boolean wanted() {
		return on(SkyBlockFeatures.SLAYER_PHASE) || on(SkyBlockFeatures.VOIDGLOOM_HELPER) || on(SkyBlockFeatures.TARANTULA_HELPER) || on(SkyBlockFeatures.BOSS_LABEL)
			|| SkyBlockFeatures.MINIBOSSES != null && SkyBlockFeatures.MINIBOSSES.wantsBosses();
	}

	private static boolean on(AddonFeature feature) {
		return feature != null && feature.isEnabled();
	}

	/** Reads the bosses: one entity query around the player per tick, shared by the own boss, the carried one and the beacons. */
	public static void tick(Minecraft client) {
		tick++;
		if (client.level != level) {
			level = client.level;
			forget();
		}
		ownReader.beginTick(tick);
		carriedReader.beginTick(tick);
		read(client);
		ownReader.endTick();
		carriedReader.endTick();
	}

	private static void read(Minecraft client) {
		if (cocoonedAt > 0 && System.currentTimeMillis() - cocoonedAt > COCOON_MILLIS) {
			cocoonedAt = 0;
		}
		carriedName = SkyBlockFeatures.SLAYER_PHASE == null ? "" : SkyBlockFeatures.SLAYER_PHASE.carriedName();
		if (!wanted() || !SkyBlock.allowed() || client.player == null || client.level == null) {
			own = null;
			carried = null;
			thrownBeacon = null;
			beaconWatch.clear();
			watchedTag = null;
			unlink();
			return;
		}
		boolean ownQuest = SkyBlockArea.slayerBossUp() || SkyBlockArea.slayerQuest();
		boolean carrying = !carriedName.isEmpty();
		// while no boss is up, a search every few ticks; every tick once the scoreboard shows the boss or one was found in the last tick
		boolean found = own != null || carried != null || ownQuest && SkyBlockArea.slayerBossUp();
		if (!ownQuest && !carrying || !found && tick % SEARCH_INTERVAL_TICKS != 0) {
			own = cocooned();
			carried = null;
			thrownBeacon = null;
			followBeacon(client);
			watchedTag = null;
			if (ownQuest || carrying) {
				followWithoutTag();
			} else {
				unlink();
			}
			return;
		}
		String me = client.player.getGameProfile().name();
		AABB around = client.player.getBoundingBox().inflate(READ_RANGE);
		List<Entity> all = client.level.getEntities((Entity) null, around, entity -> entity != client.player);
		List<SlayerTag> tags = SlayerTag.of(all);
		// the beacons are only told apart while their alert or highlight is wanted
		boolean beaconWanted = SkyBlockFeatures.VOIDGLOOM_HELPER != null && SkyBlockFeatures.VOIDGLOOM_HELPER.beaconWanted();
		thrownBeacon = beaconWanted ? beacons(all, tags, me) : null;
		if (!beaconWanted) {
			beaconOwners.clear();
		}
		followBeacon(client);
		boolean beaconAlert = SkyBlockFeatures.VOIDGLOOM_HELPER != null && SkyBlockFeatures.VOIDGLOOM_HELPER.beaconAlert();
		double beaconBlocks = beaconAlert && beaconWatch.up() ? client.player.position().distanceTo(new Vec3(beaconWatch.x(), beaconWatch.y(), beaconWatch.z())) : -1.0;
		if (cocoonedAt > 0) {
			own = cocooned();
		} else {
			own = ownQuest ? ownReader.read(client, tags, me, beaconBlocks, true) : null;
			if (own != null) {
				lastOwn = own;
			}
		}
		carried = carrying ? carriedReader.read(client, tags, carriedName, beaconBlocks, false) : null;
		remember(ownReader.tag(), ownAt);
		remember(carriedReader.tag(), carriedAt);
		// a carried boss has no chat message of its own: gone with hardly any health left means it was killed
		if (carried == null && carriedBefore != null && carriedBefore.maxHealth() > 0 && carriedBefore.health() / carriedBefore.maxHealth() < CARRIED_DEAD_SHARE) {
			died(carriedAt);
		}
		carriedBefore = carried;
		follow(all, tags);
	}

	private static void remember(Entity tag, double[] at) {
		if (tag != null) {
			at[0] = tag.getX();
			at[1] = tag.getY();
			at[2] = tag.getZ();
		}
	}

	private static void died(double[] at) {
		unlink();
		System.arraycopy(at, 0, deathAt, 0, deathAt.length);
		deathMillis = System.currentTimeMillis();
	}

	/** Finds the mob and the top of the name tags of the boss the labels and highlights are about: the carried one first. Once found, the mob stays the boss ({@link BossLink}). */
	private static void follow(List<Entity> all, List<SlayerTag> tags) {
		watchedTag = carried != null ? carriedReader.tag() : own != null && cocoonedAt == 0 ? ownReader.tag() : null;
		if (watchedTag == null) {
			followWithoutTag();
			return;
		}
		SlayerBoss boss = carried != null ? carried : own;
		String bossName = boss == null ? "" : boss.name().toLowerCase(Locale.ROOT);
		noTagTicks = 0;
		boolean sameBoss = bossName.equals(linkedName) && (carried != null) == linkedCarried;
		if (linkedBody == null || !BossLink.keeps(linkedBody.isRemoved() || !linkedBody.isAlive(), sameBoss, true, linkedBody.distanceTo(watchedTag), 0)) {
			linkedBody = findBody(all, bossName);
			linkedName = bossName;
			linkedCarried = carried != null;
		}
		watchedBody = linkedBody;
		watchedTop = watchedTag.getY();
		for (SlayerTag tag : tags) {
			Entity entity = tag.entity();
			double dx = entity.getX() - watchedTag.getX();
			double dz = entity.getZ() - watchedTag.getZ();
			if (dx * dx + dz * dz <= TAG_COLUMN * TAG_COLUMN && entity.getY() - watchedTag.getY() <= BODY_RANGE) {
				watchedTop = Math.max(watchedTop, entity.getY());
			}
		}
	}

	/** @return the mob near the name tag that is the boss: the nearest of its kind, one that can be seen before an invisible one; never a helper such as the invisible guardian of the Voidgloom's laser */
	private static Entity findBody(List<Entity> all, String bossName) {
		Entity found = null;
		double best = Double.MAX_VALUE;
		for (Entity entity : all) {
			if (entity instanceof LivingEntity && !(entity instanceof ArmorStand) && !(entity instanceof Player)) {
				double distance = entity.distanceTo(watchedTag);
				if (distance <= BODY_RANGE && BossBodies.isBody(bossName, BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath(), entity.isInvisible(), entity.isPassenger())) {
					double rank = entity.isInvisible() ? distance + BODY_RANGE : distance;
					if (rank < best) {
						best = rank;
						found = entity;
					}
				}
			}
		}
		return found;
	}

	/** Without its name tag for a moment, the boss stays the mob it was. */
	private static void followWithoutTag() {
		noTagTicks++;
		if (linkedBody != null && !BossLink.keeps(linkedBody.isRemoved() || !linkedBody.isAlive(), true, false, 0.0, noTagTicks)) {
			linkedBody = null;
		}
		watchedBody = linkedBody;
	}

	private static void unlink() {
		linkedBody = null;
		linkedName = "";
		watchedBody = null;
		noTagTicks = 0;
	}

	/** Follows a watched boss's beacon from the throw to its block: it lands as a real block near where the thrown entity disappeared, and is watched until it is gone. */
	private static void followBeacon(Minecraft client) {
		boolean before = beaconWatch.up();
		if (thrownBeacon != null) {
			beaconWatch.thrown(thrownBeacon.getId(), thrownBeacon.getX(), thrownBeacon.getY(), thrownBeacon.getZ());
		} else {
			beaconWatch.noneThrown(tick);
		}
		// it can land while what carried it is still there
		if (beaconWatch.searching()) {
			BlockPos block = beaconNear(client);
			if (block != null) {
				beaconWatch.landed(block.getX(), block.getY(), block.getZ());
			}
		} else if (beaconWatch.placed() && !client.level.getBlockState(new BlockPos(beaconWatch.blockX(), beaconWatch.blockY(), beaconWatch.blockZ())).is(Blocks.BEACON)) {
			beaconWatch.clear();
		}
		if (!before && beaconWatch.up() && SkyBlockFeatures.VOIDGLOOM_HELPER != null) {
			SkyBlockFeatures.VOIDGLOOM_HELPER.beaconThrown();
		}
	}

	/** @return the beacon block nearest to where the thrown beacon disappeared, or null; a small box for a moment, not a scan of the world */
	private static BlockPos beaconNear(Minecraft client) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		int x = Mth.floor(beaconWatch.x());
		int y = Mth.floor(beaconWatch.y());
		int z = Mth.floor(beaconWatch.z());
		BlockPos best = null;
		double bestDistance = Double.MAX_VALUE;
		for (int dx = -BeaconWatch.LAND_ACROSS; dx <= BeaconWatch.LAND_ACROSS; dx++) {
			for (int dz = -BeaconWatch.LAND_ACROSS; dz <= BeaconWatch.LAND_ACROSS; dz++) {
				for (int dy = -BeaconWatch.LAND_BELOW; dy <= BeaconWatch.LAND_ABOVE; dy++) {
					if (client.level.getBlockState(at.set(x + dx, y + dy, z + dz)).is(Blocks.BEACON)) {
						double distance = dx * dx + dy * dy + dz * dz;
						if (distance < bestDistance) {
							bestDistance = distance;
							best = at.immutable();
						}
					}
				}
			}
		}
		return best;
	}

	/** @return the mob of the boss watched now, or null */
	public static Entity watchedBody() {
		return watchedBody;
	}

	/** @return the "Spawned by" tag of the boss watched now, or null */
	static Entity watchedTag() {
		return watchedTag;
	}

	/** @return the height of the top of the boss's name tags above its "Spawned by" tag */
	static double watchedTopAbove() {
		return watchedTag == null ? 0.0 : watchedTop - watchedTag.getY();
	}

	/** @return when a watched boss last died (System millis), or 0 */
	static long deathMillis() {
		return deathMillis;
	}

	/** @return how far a point is from where that boss died, in blocks */
	static double fromDeath(double x, double y, double z) {
		double dx = x - deathAt[0];
		double dy = y - deathAt[1];
		double dz = z - deathAt[2];
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** @return the thrown beacon of a watched boss while it flies, or null */
	static Entity thrownBeacon() {
		return thrownBeacon;
	}

	/** @return the block of a watched boss's beacon, or null while it has none */
	static BlockPos beaconBlock() {
		return beaconWatch.placed() ? new BlockPos(beaconWatch.blockX(), beaconWatch.blockY(), beaconWatch.blockZ()) : null;
	}

	/** @return the killed own boss shown as cocooned while it lies as one, or null */
	private static SlayerBoss cocooned() {
		if (cocoonedAt == 0 || lastOwn == null) {
			return null;
		}
		return new SlayerBoss(lastOwn.name(), 0, lastOwn.maxHealth(), "Cocooned", SystemColors.PURPLE, "Boss slain", "", lastOwn.icon(), false, null);
	}

	/**
	 * Keeps track of the Voidgloom beacons: each new one gets the owner of the boss that threw it, once, when it appears.
	 *
	 * @return a beacon that belongs to the player's own boss or the carried one, or null
	 */
	private static Entity beacons(List<Entity> all, List<SlayerTag> tags, String me) {
		List<Entity> beacons = null;
		for (Entity entity : all) {
			if (isThrownBeacon(entity)) {
				if (beacons == null) {
					beacons = new ArrayList<>();
				}
				beacons.add(entity);
			}
		}
		List<BeaconOwners.Boss> bosses = voidglooms(all, tags);
		if (beacons == null) {
			beaconOwners.clear();
			return null;
		}
		long now = System.currentTimeMillis();
		Entity watched = null;
		for (Entity beacon : beacons) {
			String owner = beaconOwners.get(beacon.getId());
			if (owner == null) {
				String found = BeaconOwners.ownerOf(beacon.getX(), beacon.getY(), beacon.getZ(), now, bosses);
				owner = found == null ? "" : found;
				if (beaconOwners.size() < MOST_BEACONS) {
					beaconOwners.put(beacon.getId(), owner);
				}
			}
			if (watched == null && (owner.equalsIgnoreCase(me) || !carriedName.isEmpty() && owner.equalsIgnoreCase(carriedName))) {
				watched = beacon;
			}
		}
		// beacons that are gone are forgotten
		List<Entity> present = beacons;
		beaconOwners.keySet().removeIf(id -> present.stream().noneMatch(entity -> entity.getId() == id));
		return watched;
	}

	/** @return whether an entity is a beacon in the air: an armor stand wearing or holding it, or a falling block of it */
	private static boolean isThrownBeacon(Entity entity) {
		if (entity instanceof ArmorStand stand) {
			return stand.getItemBySlot(EquipmentSlot.HEAD).is(Items.BEACON) || stand.getMainHandItem().is(Items.BEACON);
		}
		return entity instanceof FallingBlockEntity falling && falling.getBlockState().is(Blocks.BEACON);
	}

	/**
	 * Finds every Voidgloom Seraph near the player with its owner, and remembers when each was seen holding a beacon (the enderman carries it as
	 * its block before throwing it).
	 */
	private static List<BeaconOwners.Boss> voidglooms(List<Entity> all, List<SlayerTag> tags) {
		List<BeaconOwners.Boss> bosses = new ArrayList<>();
		Map<Integer, Long> stillHeld = new HashMap<>();
		long now = System.currentTimeMillis();
		for (SlayerTag tag : tags) {
			String line = SkyBlockArea.plainLine(tag.text());
			if (!SlayerOwners.isOwnerLine(line)) {
				continue;
			}
			if (SlayerTag.near(tags, tag.entity(), BOSS_NAME_RANGE, text -> text.contains("Voidgloom")) == null) {
				continue;
			}
			EnderMan body = nearestEnderman(all, tag.entity());
			Entity at = body != null ? body : tag.entity();
			long held = 0;
			if (body != null) {
				BlockState carriedBlock = body.getCarriedBlock();
				Long before = heldAt.get(body.getId());
				held = carriedBlock != null && carriedBlock.is(Blocks.BEACON) ? now : before == null ? 0 : before;
				if (held > 0) {
					stillHeld.put(body.getId(), held);
				}
			}
			bosses.add(new BeaconOwners.Boss(SlayerOwners.ownerOf(line), at.getX(), at.getY(), at.getZ(), held));
		}
		heldAt.clear();
		heldAt.putAll(stillHeld);
		return bosses;
	}

	private static EnderMan nearestEnderman(List<Entity> all, Entity to) {
		EnderMan best = null;
		double bestDistance = BOSS_BODY_RANGE;
		for (Entity entity : all) {
			if (entity instanceof EnderMan enderman) {
				double distance = enderman.distanceTo(to);
				if (distance <= bestDistance) {
					bestDistance = distance;
					best = enderman;
				}
			}
		}
		return best;
	}

	/** Reads the slayer messages of the player's own quest; the hatchlings message can belong to either fight. */
	public static void onMessage(Component message) {
		String text = message.getString();
		// A killed boss can be cocooned: it lies as a cocoon until the quest is turned in, and the next quest starts with it already spawned (no
		// "SLAYER BOSS spawned"). Either way, what follows is a new fight.
		if (text.contains("YOU COCOONED YOUR SLAYER BOSS")) {
			cocoonedAt = System.currentTimeMillis();
			ownReader.newFight();
			// killed, and its drops are on the ground now: the quest's end comes only when the cocoon is done
			died(ownAt);
		} else if (text.contains("SLAYER QUEST STARTED")) {
			cocoonedAt = 0;
			ownReader.questStarted();
		} else if (text.contains("SLAYER QUEST COMPLETE!")) {
			ownReader.questDone();
			died(ownAt);
		}
		if (text.contains("You need to kill the Broodfather's hatchlings before it can be damaged again!")) {
			// whether this starts a web is decided by the boss health at the next read: tier V also says this right after spawning
			ownReader.hatchlings();
			carriedReader.hatchlings();
		}
	}

	private static void forget() {
		own = null;
		carried = null;
		lastOwn = null;
		cocoonedAt = 0;
		thrownBeacon = null;
		beaconWatch.clear();
		watchedTag = null;
		unlink();
		carriedBefore = null;
		deathMillis = 0;
		beaconOwners.clear();
		heldAt.clear();
		ownReader.clear();
		carriedReader.clear();
	}

	/** @return the player's own boss, or null */
	static SlayerBoss own() {
		return own;
	}

	/** @return the carried player's boss, or null */
	static SlayerBoss carried() {
		return carried;
	}

	/** @return the boss the panel shows: the carried one first, otherwise the player's own */
	static SlayerBoss shown() {
		return carried != null ? carried : own;
	}

	/** @return whether what is only for slayers shows now: with the quest on one of its islands, or with the boss near ({@link SlayerPlaces}); the quest is followed all the same */
	public static boolean showsHere() {
		String quest = SkyBlockArea.slayerQuest() ? SkyBlockArea.slayerBoss() : "";
		return SlayerPlaces.shows(quest, SkyBlockArea.island(), own != null || carried != null);
	}

	/** @return whether the own boss lies as a cocoon */
	static boolean ownCocooned() {
		return cocoonedAt > 0;
	}

	/** @return the name of the player's own boss being fought ("Tarantula Broodfather IV"), or ""; never the carried one */
	public static String ownBossName() {
		return own == null ? "" : own.name();
	}

	/** Adds every entity that is part of an egg sac of a watched Tarantula's web. */
	static void addEggParts(List<Entity> into) {
		if (own != null) {
			ownReader.addEggParts(into);
		}
		if (carried != null) {
			carriedReader.addEggParts(into);
		}
	}

	/** @return whether a stand is a part of an egg sac of a watched Tarantula */
	public static boolean isEggPart(Entity entity) {
		return own != null && ownReader.isEggPart(entity) || carried != null && carriedReader.isEggPart(entity);
	}

	/** @return whether a Tarantula boss is being fought here, as far as the quest or the watched bosses say */
	public static boolean tarantulaHere() {
		return dev.tim1337.lumio.skyblock.profit.Slayer.of(dev.tim1337.lumio.skyblock.data.SkyBlockArea.slayerBoss()) == dev.tim1337.lumio.skyblock.profit.Slayer.TARANTULA && dev.tim1337.lumio.skyblock.data.SkyBlockArea.slayerBossUp() || anyEggs();
	}

	/** @return whether a watched Tarantula has egg sacs up now */
	static boolean anyEggs() {
		return own != null && ownReader.anyEggs() || carried != null && carriedReader.anyEggs();
	}
}
