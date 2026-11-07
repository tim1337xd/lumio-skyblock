package dev.tim1337.lumio.skyblock.data;

import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Counts crops immediately as they are farmed, like other trackers. The server stores the number of harvested crops in the farming tool itself (the
 * Counter, "mined_crops", and Cultivating, "farmed_cultivating") and updates it with every crop: its increase is the crops gained. Sacks report the
 * same crops only every 30 seconds, so what was counted here is subtracted from their later reports (and from what lands in the inventory, also as
 * enchanted crops), and nothing is counted twice. Only the held item is read; nothing is sent.
 */
final class CropCounter {
	enum Crop {
		WHEAT("WHEAT", "Wheat", null, "WHEAT"),
		CARROT("CARROT_ITEM", "Carrot", "carrot", "CARROT_ITEM", "CARROT"),
		POTATO("POTATO_ITEM", "Potato", "potato", "POTATO_ITEM", "POTATO"),
		CANE("SUGAR_CANE", "Sugar Cane", "sugar", "SUGAR_CANE"),
		WART("NETHER_STALK", "Nether Wart", "nether wart", "NETHER_STALK", "NETHER_WART"),
		MELON("MELON", "Melon", "melon", "MELON", "MELON_SLICE"),
		PUMPKIN("PUMPKIN", "Pumpkin", "pumpkin", "PUMPKIN"),
		CACTUS("CACTUS", "Cactus", "cactus", "CACTUS"),
		COCOA("INK_SACK:3", "Cocoa Beans", "cocoa", "INK_SACK:3", "COCOA_BEANS"),
		RED_MUSHROOM("RED_MUSHROOM", "Red Mushroom", "red mushroom", "RED_MUSHROOM"),
		BROWN_MUSHROOM("BROWN_MUSHROOM", "Brown Mushroom", "brown mushroom", "BROWN_MUSHROOM");

		final String id;
		final String name;
		/** A word in the name of its enchanted form ("Enchanted Sugar"), crafted from 160 of it, or null. */
		final String enchanted;
		final String[] ids;

		Crop(String id, String name, String enchanted, String... ids) {
			this.id = id;
			this.name = name;
			this.enchanted = enchanted;
			this.ids = ids;
		}

		boolean is(String other) {
			for (String known : ids) {
				if (known.equalsIgnoreCase(other)) {
					return true;
				}
			}
			return false;
		}
	}

	/** How long a counted crop waits for the sacks to report it: they report every 30 seconds, sometimes slightly later. */
	private static final long WAIT = 120_000L;
	private static final int ENCHANTED = 160;

	/** Crops counted from the tool but not yet reported by the sacks or seen in the inventory, per crop, with the last count time. */
	private static final Map<Crop, long[]> AHEAD = new EnumMap<>(Crop.class);
	private static String toolUuid = "";
	private static long lastCounter = -1;
	private static Crop lastCrop;

	private CropCounter() {
	}

	/** @return the crop of a farmed block, or null */
	private static Crop cropOf(Block block) {
		if (block == Blocks.WHEAT) return Crop.WHEAT;
		if (block == Blocks.CARROTS) return Crop.CARROT;
		if (block == Blocks.POTATOES) return Crop.POTATO;
		if (block == Blocks.SUGAR_CANE) return Crop.CANE;
		if (block == Blocks.NETHER_WART) return Crop.WART;
		if (block == Blocks.MELON) return Crop.MELON;
		if (block == Blocks.PUMPKIN) return Crop.PUMPKIN;
		if (block == Blocks.CACTUS) return Crop.CACTUS;
		if (block == Blocks.COCOA) return Crop.COCOA;
		if (block == Blocks.RED_MUSHROOM) return Crop.RED_MUSHROOM;
		if (block == Blocks.BROWN_MUSHROOM) return Crop.BROWN_MUSHROOM;
		return null;
	}

	/** @return the crop of a crop-specific tool: "THEORETICAL_HOE_CANE_3", "MELON_DICER_2" */
	private static Crop cropOf(String toolId) {
		String id = toolId.toUpperCase(Locale.ROOT);
		if (id.contains("WHEAT")) return Crop.WHEAT;
		if (id.contains("CARROT")) return Crop.CARROT;
		if (id.contains("POTATO")) return Crop.POTATO;
		if (id.contains("CANE")) return Crop.CANE;
		if (id.contains("WART")) return Crop.WART;
		if (id.contains("MELON")) return Crop.MELON;
		if (id.contains("PUMPKIN")) return Crop.PUMPKIN;
		if (id.contains("CACTUS")) return Crop.CACTUS;
		if (id.contains("COCO")) return Crop.COCOA;
		return null;
	}

	/** @return the harvested crop count stored in the tool, or -1 for a tool without a counter */
	private static long counterOf(CompoundTag tag) {
		long counter = tag.getLongOr("mined_crops", -1L);
		return counter >= 0 ? counter : tag.getLongOr("farmed_cultivating", -1L);
	}

	/** The held tool's custom data in the previous tick. */
	private static CustomData lastData;

	/** Called every tick while trackers need gains: compares the held tool's counter with the previous value. */
	static void tick(Minecraft client) {
		long now = System.currentTimeMillis();
		AHEAD.values().removeIf(ahead -> now - ahead[1] > WAIT);
		if (client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
			Crop looked = cropOf(client.level.getBlockState(hit.getBlockPos()).getBlock());
			if (looked != null) {
				lastCrop = looked;
			}
		}
		ItemStack held = client.player.getMainHandItem();
		CustomData data = held.get(DataComponents.CUSTOM_DATA);
		if (data == null) {
			toolUuid = "";
			lastCounter = -1;
			lastData = null;
			return;
		}
		// same data instance as last tick means the same count: the tool gets new data when its counter rises, and reading it copies all of it
		if (data == lastData) {
			return;
		}
		lastData = data;
		CompoundTag tag = data.copyTag();
		long counter = counterOf(tag);
		String uuid = tag.getStringOr("uuid", "") + "|" + tag.getStringOr("id", "");
		if (counter < 0) {
			toolUuid = "";
			lastCounter = -1;
			return;
		}
		// another tool in hand: its counter is only the new baseline
		if (!uuid.equals(toolUuid) || lastCounter < 0) {
			toolUuid = uuid;
			lastCounter = counter;
			return;
		}
		long gained = counter - lastCounter;
		lastCounter = counter;
		// a jump larger than a hand can farm in a tick is not crops (a tool modified in a menu)
		if (gained <= 0 || gained > 4000 || !SkyBlock.inSkyBlock()) {
			return;
		}
		Crop crop = cropOf(tag.getStringOr("id", ""));
		if (crop == null) {
			crop = lastCrop;
		}
		if (crop == null) {
			return;
		}
		long[] ahead = AHEAD.computeIfAbsent(crop, key -> new long[2]);
		ahead[0] += gained;
		ahead[1] = now;
		ItemGains.emit(crop.id, crop.name, gained, true);
	}

	/** @return the amount the sacks or inventory report for an item minus what was already counted from the tool: how many are still new */
	static long settle(String id, String name, long count) {
		for (Map.Entry<Crop, long[]> entry : AHEAD.entrySet()) {
			Crop crop = entry.getKey();
			long[] ahead = entry.getValue();
			if (ahead[0] <= 0) {
				continue;
			}
			if (crop.is(id)) {
				long taken = Math.min(ahead[0], count);
				ahead[0] -= taken;
				return count - taken;
			}
			// an enchanted crop the compactor made in the inventory is 160 crops that were already counted
			String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
			if (crop.enchanted != null && id.toUpperCase(Locale.ROOT).startsWith("ENCHANTED_") && lower.startsWith("enchanted ") && lower.contains(crop.enchanted)) {
				long covered = Math.min(count, ahead[0] / ENCHANTED);
				ahead[0] -= covered * ENCHANTED;
				return count - covered;
			}
		}
		return count;
	}

	/** @return the id under which a crop's remainder is counted: the same as the tool-counted crops, so they share one row */
	static String canonical(String id) {
		for (Crop crop : Crop.values()) {
			if (crop.is(id)) {
				return crop.id;
			}
		}
		return id;
	}
}
