package dev.tim1337.lumio.skyblock.data;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What counts as fishing for the fishing profit tracker: a real fishing rod, by the SkyBlock category in the last lore lines ("LEGENDARY FISHING ROD"), never by the vanilla item (the Grappling Hook and the whips are vanilla rods too), and
 * the islands it applies to.
 */
public final class FishingRods {
	/** SkyBlock items made of a vanilla fishing rod that are no fishing rods, checked against the NEU repository: this is the one place that lists them, and it wins over the lore. */
	private static final Set<String> NOT_RODS = Set.of("GRAPPLING_HOOK", "SOUL_WHIP", "FLAMING_FLAY", "ZOMBIE_COMMANDER_WHIP");
	/** A recombobulated item has one obfuscated letter on each side of its category line. */
	private static final Pattern CATEGORY = Pattern.compile("^(?:\\S )?(?:COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|VERY SPECIAL|SPECIAL|ULTIMATE|ADMIN)(?: DUNGEON)? FISHING ROD(?: \\S)?$");
	private static final Set<SkyBlockIsland> ISLANDS = EnumSet.of(SkyBlockIsland.HUB, SkyBlockIsland.JERRYS_WORKSHOP, SkyBlockIsland.CRIMSON_ISLE, SkyBlockIsland.SPIDERS_DEN, SkyBlockIsland.BACKWATER_BAYOU, SkyBlockIsland.LOTUS_ATOLL);

	private static ItemStack checkedStack;
	private static ItemLore checkedLore;
	private static boolean checkedRod;
	private static String checkedArea;
	private static boolean checkedIsland;

	private FishingRods() {
	}

	/** @return whether an item with this SkyBlock id and these lore lines, without colors, is a real fishing rod; an item without a SkyBlock id is none */
	public static boolean isRod(String id, List<String> lore) {
		if (id == null || NOT_RODS.contains(id)) {
			return false;
		}
		// the category is at the end of the lore
		for (int i = lore.size() - 1; i >= 0; i--) {
			if (CATEGORY.matcher(lore.get(i)).matches()) {
				return true;
			}
		}
		return false;
	}

	/** @return whether the fishing profit tracker works on an island, by the name the player list shows after "Area:" */
	public static boolean fishingIsland(String area) {
		SkyBlockIsland island = SkyBlockIsland.ofArea(area);
		return island != null && ISLANDS.contains(island);
	}

	/** @return whether a stack is a real fishing rod; asked every tick and on every frame, so its lore is only read when the stack or its lore changes */
	public static boolean holding(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (stack != checkedStack || lore != checkedLore) {
			checkedStack = stack;
			checkedLore = lore;
			checkedRod = lore != null && isRod(ItemGains.idOf(stack), ItemGains.loreLines(stack));
		}
		return checkedRod;
	}

	/** @return whether the player is on one of the fishing tracker's islands */
	public static boolean onFishingIsland() {
		String area = SkyBlockArea.island();
		// the area is one string until it changes: this runs per frame, and the lookup compares many names
		if (area != checkedArea) {
			checkedArea = area;
			checkedIsland = fishingIsland(area);
		}
		return checkedIsland;
	}
}
