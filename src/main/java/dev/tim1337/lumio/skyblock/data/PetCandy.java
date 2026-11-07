package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.experimental.util.TextColors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How much Pet Candy a pet item has used and whether it is at its top level, read only from what the item itself says: its {@code petInfo} data and
 * its lore ("(3/10) Pet Candy Used", "MAX LEVEL"). Slots ask for it every frame, so an item is read once and the answer kept with it; only player
 * heads of stack size 1 (the look of every pet) get that far.
 */
public final class PetCandy {
	/** What an item says about its candy. */
	public record Info(int candy, boolean maxLevel, String label) {
	}

	/** The answer for an item that is no pet. */
	public static final Info NONE = new Info(0, false, "");

	/** The most candy a value may claim, and the longest petInfo read: anything beyond is not a pet's real data. */
	private static final int MOST_CANDY = 999;
	private static final int MOST_INFO_CHARS = 16_384;
	private static final int MOST_CACHED = 4096;
	private static final int MOST_LEVEL = 100;
	private static final int MOST_DRAGON_LEVEL = 200;
	/** The pets that go past level 100; their lore normally says so, this is for an item without those lines. */
	private static final Set<String> TO_LEVEL_200 = Set.of("GOLDEN_DRAGON", "JADE_DRAGON", "ROSE_DRAGON");

	private static final Pattern LORE_COUNT = Pattern.compile("\\(\\s*(\\d{1,3})\\s*/\\s*\\d{1,3}\\s*\\)\\s*Pet Candy Used|Pet Candy Used:?\\s*(\\d{1,3})");
	private static final Pattern NAME_LEVEL = Pattern.compile("^\\[Lvl (\\d{1,3})]");
	private static final String LORE_MAX = "MAX LEVEL";
	private static final String LORE_PROGRESS = "Progress to Level";

	private static final Map<ItemStack, Info> CACHE = new WeakHashMap<>();

	private PetCandy() {
	}

	/** @return what the item says about its candy, or {@link #NONE} for anything but a pet; cheap for every other item, and kept per item object */
	public static Info of(ItemStack stack) {
		if (stack.getCount() != 1 || !stack.is(Items.PLAYER_HEAD)) {
			return NONE;
		}
		Info cached = CACHE.get(stack);
		if (cached != null) {
			return cached;
		}
		Info read = read(stack);
		if (CACHE.size() >= MOST_CACHED) {
			CACHE.clear();
		}
		CACHE.put(stack, read);
		return read;
	}

	private static Info read(ItemStack stack) {
		try {
			CustomData data = stack.get(DataComponents.CUSTOM_DATA);
			Optional<String> info = data == null ? Optional.empty() : data.copyTag().getString("petInfo");
			List<String> lore = new ArrayList<>();
			ItemLore lines = stack.get(DataComponents.LORE);
			if (lines != null) {
				for (Component line : lines.lines()) {
					lore.add(TextColors.noColors(line.getString()));
				}
			}
			return parse(info.orElse(null), TextColors.noColors(stack.getHoverName().getString()).strip(), lore);
		} catch (RuntimeException error) {
			return NONE;
		}
	}

	/**
	 * @param petInfo the item's petInfo text, or null
	 * @param name    the item's name without colors
	 * @param lore    the item's lore lines without colors
	 * @return what they say about the candy; {@link #NONE} when neither the data nor the name makes it a pet
	 */
	static Info parse(String petInfo, String name, List<String> lore) {
		JsonObject info = json(petInfo);
		Matcher level = NAME_LEVEL.matcher(name);
		boolean hasLevel = level.find();
		if (info == null && !hasLevel) {
			return NONE;
		}
		int candy = candyOf(info);
		if (candy < 0) {
			candy = candyOf(lore);
		}
		candy = Math.max(0, candy);
		return new Info(candy, maxLevel(info, hasLevel ? Integer.parseInt(level.group(1)) : -1, lore), Integer.toString(candy));
	}

	private static JsonObject json(String text) {
		if (text == null || text.length() > MOST_INFO_CHARS) {
			return null;
		}
		try {
			JsonElement parsed = JsonParser.parseString(text);
			return parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
		} catch (RuntimeException error) {
			return null;
		}
	}

	/** @return the candy in the pet's data, or -1 when it is missing or not a believable number */
	private static int candyOf(JsonObject info) {
		JsonElement value = info == null ? null : info.get("candyUsed");
		if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
			return -1;
		}
		try {
			int candy = value.getAsInt();
			return candy >= 0 && candy <= MOST_CANDY ? candy : -1;
		} catch (NumberFormatException error) {
			return -1;
		}
	}

	/** @return the candy in the "Pet Candy Used" line, or -1 without one */
	private static int candyOf(List<String> lore) {
		for (String line : lore) {
			if (line.contains("Pet Candy Used")) {
				Matcher count = LORE_COUNT.matcher(line);
				if (count.find()) {
					return Integer.parseInt(count.group(1) != null ? count.group(1) : count.group(2));
				}
			}
		}
		return -1;
	}

	/** The lore decides, as it says so at the top level ("MAX LEVEL") and below it ("Progress to Level"); only an item without either goes by its level. */
	private static boolean maxLevel(JsonObject info, int level, List<String> lore) {
		for (String line : lore) {
			if (line.contains(LORE_MAX)) {
				return true;
			}
			if (line.contains(LORE_PROGRESS)) {
				return false;
			}
		}
		JsonElement type = info == null ? null : info.get("type");
		boolean toLevel200 = type != null && type.isJsonPrimitive() && TO_LEVEL_200.contains(type.getAsString());
		return level >= (toLevel200 ? MOST_DRAGON_LEVEL : MOST_LEVEL);
	}
}
