package dev.tim1337.lumio.skyblock.profit;

import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.data.ProfitTracker;
import dev.tim1337.lumio.skyblock.data.SkyBlockPrices;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * What one opened corpse brought in, for the line in chat after its loot list: its loot at current prices less the key it used, valued exactly as the
 * corpse profit panel values it (the same drops, powder left out, the same price mode and the same price lookup), so the line and the panel always agree.
 */
public final class CorpseProfit {
	private static final int PROFIT_RGB = 0x55FF55;
	private static final int LOSS_RGB = 0xFF5555;
	private static final int VALUE_RGB = 0xFFAA00;
	private static final String MINUS = "−";

	/** How an item and a key are priced: the panel's own lookup in the game, a table in a test. */
	public interface Prices {
		/** @return what an amount of an item is worth, 0 without a price */
		double value(String id, String name, long count, SkyBlockPrices.Mode mode);

		/** @return what one of an item costs, 0 without a price */
		double price(String id, SkyBlockPrices.Mode mode);
	}

	/** One line of the loot: its name, amount and value, and whether it had a price at all. */
	public record Line(String name, long count, double value, boolean priced) {
	}

	/** The corpse's result: its loot lines, the key it used (null for none) and what that cost, the total and the price mode. */
	public record Result(List<Line> lines, String keyName, double keyCost, double total, SkyBlockPrices.Mode mode) {
	}

	private CorpseProfit() {
	}

	/** @return the key a kind of corpse uses, as an item id, or null for one that needs none (Lapis) */
	static String keyOf(String kind) {
		return switch (kind) {
			case "TUNGSTEN" -> "TUNGSTEN_KEY";
			case "UMBER" -> "UMBER_KEY";
			case "VANGUARD" -> "SKELETON_KEY";
			default -> null;
		};
	}

	private static String keyName(String key) {
		return switch (key) {
			case "TUNGSTEN_KEY" -> "Tungsten Key";
			case "UMBER_KEY" -> "Umber Key";
			default -> "Skeleton Key";
		};
	}

	/** @return powder, which is no item and counts in no corpse's value */
	static boolean powder(String name) {
		return name.endsWith("Powder");
	}

	/** @return what the corpse brought in, or null when its loot list could not be read completely: then nothing is said rather than a wrong number */
	static Result of(ItemGains.Corpse corpse, SkyBlockPrices.Mode mode, Prices prices) {
		if (!corpse.complete()) {
			return null;
		}
		List<Line> lines = new ArrayList<>();
		double total = 0;
		for (ItemGains.Drop drop : corpse.drops()) {
			if (powder(drop.name())) {
				continue;
			}
			double value = Math.max(0, prices.value(drop.id(), drop.name(), drop.count(), mode));
			lines.add(new Line(drop.name(), drop.count(), value, value > 0));
			total += value;
		}
		String key = keyOf(corpse.kind());
		double keyCost = key != null && corpse.keyUsed() ? Math.max(0, prices.price(key, mode)) : 0;
		total -= keyCost;
		return new Result(lines, key != null && corpse.keyUsed() ? keyName(key) : null, keyCost, total, mode);
	}

	/** @return the panel's own pricing: an item as a tracker entry, so the name lookup and the price mode are the same as in the panel */
	static Prices live() {
		return new Prices() {
			@Override
			public double value(String id, String name, long count, SkyBlockPrices.Mode mode) {
				ProfitTracker.Entry entry = new ProfitTracker.Entry(id, name);
				entry.count = count;
				return entry.value(mode);
			}

			@Override
			public double price(String id, SkyBlockPrices.Mode mode) {
				return SkyBlockPrices.price(id, mode);
			}
		};
	}

	/** @return coins with a real minus sign */
	static String coins(double value) {
		return ProfitTracker.coins(value).replace("-", MINUS);
	}

	/** @return the line for chat: "Corpse profit: +1.23M" in green, or "Corpse loss: −85k" in red, with its breakdown when hovered */
	static Component chatLine(Result result) {
		boolean profit = result.total() >= 0;
		String text = profit ? "Corpse profit: +" + coins(result.total()) : "Corpse loss: " + coins(result.total());
		return Component.literal(text).withStyle(style -> style.withColor(profit ? PROFIT_RGB : LOSS_RGB)
			.withHoverEvent(new HoverEvent.ShowText(breakdown(result))));
	}

	/** @return the hover's breakdown: every item with its amount and value (or "no price"), the key's cost, the total and the price mode */
	static Component breakdown(Result result) {
		MutableComponent text = Component.empty();
		boolean first = true;
		for (Line line : result.lines()) {
			if (!first) {
				text.append("\n");
			}
			first = false;
			text.append(Component.literal(line.name()).withStyle(ChatFormatting.WHITE));
			if (line.count() > 1) {
				text.append(Component.literal(" ×" + line.count()).withStyle(ChatFormatting.DARK_GRAY));
			}
			text.append(Component.literal("  "));
			text.append(line.priced() ? Component.literal(coins(line.value())).withStyle(style -> style.withColor(VALUE_RGB))
				: Component.literal("no price").withStyle(ChatFormatting.GRAY));
		}
		if (result.keyName() != null) {
			text.append("\n").append(Component.literal(result.keyName()).withStyle(ChatFormatting.WHITE))
				.append(Component.literal("  " + MINUS + coins(result.keyCost())).withStyle(style -> style.withColor(LOSS_RGB)));
		}
		boolean profit = result.total() >= 0;
		text.append("\n\n").append(Component.literal(profit ? "Profit  +" + coins(result.total()) : "Loss  " + coins(result.total()))
			.withStyle(style -> style.withColor(profit ? PROFIT_RGB : LOSS_RGB).withBold(true)));
		text.append("\n").append(Component.literal("Prices: " + result.mode().title()).withStyle(ChatFormatting.DARK_GRAY));
		return text;
	}
}
