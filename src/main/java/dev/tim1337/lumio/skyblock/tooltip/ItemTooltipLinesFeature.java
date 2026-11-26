package dev.tim1337.lumio.skyblock.tooltip;

import dev.tim1337.lumio.api.TooltipLine;
import dev.tim1337.lumio.api.TooltipLineProvider;
import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.AddonSetting;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.OrderedChoice;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;
import dev.tim1337.lumio.skyblock.data.AuctionPrices;
import dev.tim1337.lumio.skyblock.data.ExactItem;
import dev.tim1337.lumio.skyblock.data.ExactPrices;
import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.data.ItemMarket;
import dev.tim1337.lumio.skyblock.data.ItemRepo;
import dev.tim1337.lumio.skyblock.data.PriceKeys;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockPrices;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.serialization.JsonOps;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.ToDoubleFunction;

/**
 * The SkyBlock lines of item tooltips: the dungeon quality, the market or the bazaar, craft cost and NPC price, each with its own switch and in the player's order. Its settings show in the card of
 * the main mod's item tooltips, and the lines go into the one separate block it keeps apart from what the game sends.
 */
public final class ItemTooltipLinesFeature extends AddonFeature implements TooltipLineProvider {
	/** The span of the median. */
	enum MedianSpan {
		DAY("1 d", 1),
		WEEK("7 d", 7),
		MONTH("30 d", 30);

		private final Component name;
		private final int days;

		MedianSpan(String name, int days) {
			this.name = Component.literal(name);
			this.days = days;
		}
	}

	/** How numbers are written. */
	enum NumberStyle {
		SHORT("4.2M", PriceText.Style.SHORT),
		FULL("4,200,000", PriceText.Style.FULL);

		private final Component name;
		private final PriceText.Style style;

		NumberStyle(String name, PriceText.Style style) {
			this.name = Component.literal(name);
			this.style = style;
		}
	}

	private final OrderedChoice lines = add(settings().ordered("lines", Component.literal("Lines and their order"), List.of(
		new OrderedChoice.Entry(AddedLines.DUNGEON, "Dungeon quality and floor", "info", true),
		new OrderedChoice.Entry(AddedLines.LOWEST_BIN, "Lowest BIN", "info", true),
		new OrderedChoice.Entry(AddedLines.MEDIAN, "Median", "info", true),
		new OrderedChoice.Entry(AddedLines.SOLD, "Sold per day", "info", false),
		new OrderedChoice.Entry(AddedLines.CRAFT, "Craft cost", "info", false),
		new OrderedChoice.Entry(AddedLines.BAZAAR, "Bazaar prices", "info", true),
		new OrderedChoice.Entry(AddedLines.TREND, "7-day trend", "info", false),
		new OrderedChoice.Entry(AddedLines.NPC, "NPC price", "info", false))));
	private final Choice<MedianSpan> medianSpan = add(settings().choice("median_span", Component.literal("Median over"), MedianSpan.WEEK, span -> span.name));
	private final Choice<NumberStyle> numberStyle = add(settings().choice("number_style", Component.literal("Numbers"), NumberStyle.SHORT, style -> style.name));
	private String loggedLine = "";
	private static final String DEFAULTS_APPLIED = "market_defaults_applied";
	private static final String LINES_KEY = "lines";
	private static final String OFF_KEY = "off";
	private static final String SOULBOUND_LINE = "* Soulbound *";
	private static final String COOP_SOULBOUND_LINE = "* Co-op Soulbound *";

	public ItemTooltipLinesFeature() {
		super("item_tooltip_lines", "SkyBlock tooltip lines", "The SkyBlock lines of item tooltips.", true, false);
		medianSpan.visibleWhen(() -> lines.enabledInOrder().contains(AddedLines.MEDIAN));
	}

	/** A config from before the market's shorter defaults gets them, once. */
	@Override
	public void load(JsonObject json) {
		if (!json.has(DEFAULTS_APPLIED)) {
			turnOff(json, List.of(AddedLines.SOLD, AddedLines.CRAFT, AddedLines.TREND));
		}
		super.load(json);
	}

	@Override
	public JsonObject save() {
		JsonObject json = super.save();
		json.addProperty(DEFAULTS_APPLIED, true);
		return json;
	}

	/** Switches entries off in the saved form of the list of lines, which holds those that are off by their id; a config without the list has the defaults anyway. */
	static void turnOff(JsonObject json, List<String> ids) {
		if (!json.has(LINES_KEY) || !json.get(LINES_KEY).isJsonObject()) {
			return;
		}
		JsonObject saved = json.getAsJsonObject(LINES_KEY);
		JsonArray off = saved.has(OFF_KEY) && saved.get(OFF_KEY).isJsonArray() ? saved.getAsJsonArray(OFF_KEY) : new JsonArray();
		for (String id : ids) {
			JsonPrimitive entry = new JsonPrimitive(id);
			if (!off.contains(entry)) {
				off.add(entry);
			}
		}
		saved.add(OFF_KEY, off);
	}

	@Override
	public List<AddonSetting<?>> lineSettings() {
		return List.of(lines, medianSpan, numberStyle);
	}

	@Override
	public void addLines(ItemStack stack, List<TooltipLine> out) {
		String id = ItemGains.idOf(stack);
		// a menu's filler (a pane without a name) is no item to price
		if (id == null || !SkyBlock.allowed() || stack.getHoverName().getString().isBlank()) {
			return;
		}
		CustomData custom = stack.get(DataComponents.CUSTOM_DATA);
		CompoundTag data = custom == null ? new CompoundTag() : custom.copyTag();
		readItem(id, data, custom, stack);
		// decided at once by the lists already loaded: an item in neither has no market, and gets no block, not even "…"; while the lists are still
		// coming any item may wait for its prices. A pet is in the list by its kind and rarity, a book of several enchantments by the sum of theirs
		if (!SkyBlockPrices.listsLoaded() || SkyBlockPrices.onAMarket(id) || SkyBlockPrices.onAMarket(target.id()) || SkyBlockPrices.onAMarket(target.listId())
			|| target.summed()) {
			out.addAll(AddedLines.build(lines.enabledInOrder(), facts(id, data, stack, custom), numberStyle.get().style));
		}
		// a debug tool's "dungeon item data": for finding the floor, a line with its fields, also in the log
		if (DebugHooks.wants(SkyBlockDebug.ITEM_DATA)) {
			String line = DungeonData.debugLine(data);
			if (!line.isEmpty()) {
				out.add(TooltipLine.footnote(line));
				if (!line.equals(loggedLine)) {
					loggedLine = line;
					DebugHooks.log(SkyBlockDebug.DUNGEON, "Dungeon item data of {}: {}", id, line);
				}
			}
		}
	}

	/** The item's own data as Coflnet is sent it, made again only when the item changes: this runs on every frame the tooltip is shown. */
	private CustomData jsonFor;
	private JsonObject json = new JsonObject();
	private String jsonKey = "";
	private String pricedId = "";
	/** What the item is priced by: its Bazaar product, its id in the lowest BIN list, or the enchantments of a book. */
	private PriceKeys.Target target = PriceKeys.of(null, null, id -> null);
	private boolean soulbound;
	private final PriceGlide lowestGlide = new PriceGlide();
	private final PriceGlide medianGlide = new PriceGlide();

	private void readItem(String gameId, CompoundTag data, CustomData custom, ItemStack stack) {
		if (custom == jsonFor) {
			return;
		}
		jsonFor = custom;
		soulbound = soulbound(stack);
		lowestGlide.reset();
		medianGlide.reset();
		json = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, data) instanceof JsonObject object ? object : new JsonObject();
		target = PriceKeys.of(gameId, json, ItemRepo::shardProduct);
		pricedId = target.id();
		JsonObject item = ExactItem.request(pricedId, "", json);
		jsonKey = pricedId + item;
	}

	private AddedLines.Facts facts(String gameId, CompoundTag data, ItemStack stack, CustomData custom) {
		readItem(gameId, data, custom, stack);
		String id = pricedId;
		if (target.summed()) {
			return summedFacts(stack);
		}
		// on the Bazaar first when it is a product there, else at auction
		boolean bazaar = SkyBlockPrices.onBazaar(id);
		// a pet's or a rune's history by its id alone would mix every rarity or level: it has none
		boolean byId = bazaar || target.ownAuction();
		ItemMarket.Snapshot market = byId ? ItemMarket.INSTANCE.look(id, bazaar) : ItemMarket.Snapshot.EMPTY;
		OptionalInt quality = DungeonData.quality(data);
		long now = System.currentTimeMillis();
		double buyNow = SkyBlockPrices.bazaarBuy(id);
		double sellNow = SkyBlockPrices.bazaarSell(id);
		int days = medianSpan.get().days;
		// the item's own auction numbers, the same the item window shows (one source, one moment); the list's until they come
		AuctionPrices.Entry auction = bazaar || !target.ownAuction() ? AuctionPrices.Entry.NONE : AuctionPrices.look(id);
		boolean own = auction.known() && !Double.isNaN(auction.lowest());
		double lowest = own ? auction.lowest() : SkyBlockPrices.listedLowest(target.listId());
		long lowestAge = own ? auction.age(now) : now - SkyBlockPrices.lowestFetchedAt();
		double median = days == AuctionPrices.DAYS && auction.known() && !Double.isNaN(auction.median()) ? auction.median() : market.median(days);
		double volume = bazaar ? SkyBlockPrices.bazaarVolumeWeek(id) : auction.known() && !Double.isNaN(auction.volume()) ? auction.volume() : Double.NaN;
		double craft = craftCost(id);
		AddedLines.Exact exact = AddedLines.Exact.NO;
		boolean loading = byId && market.fetchedAt() <= 0L || !SkyBlockPrices.ready();
		if (!bazaar && soulbound) {
			// it can't be sold: what it is worth exactly says nothing, only its base item's price is left
			exact = new AddedLines.Exact(true, true, true);
		} else if (!bazaar && target.exact() && ExactItem.relevant(json.keySet())) {
			// an item whose properties change its value is priced as this item; materials and bazaar items stay with their id
			boolean wantCraft = lines.enabledInOrder().contains(AddedLines.CRAFT);
			ExactPrices.Prices prices = ExactPrices.INSTANCE.look(id, TextColors.noColors(stack.getHoverName().getString()), json, jsonKey, wantCraft);
			// until it is known, and where it is not: the base item's number, and it says so
			boolean lowestById = Double.isNaN(prices.lowest());
			boolean medianById = Double.isNaN(prices.median());
			exact = new AddedLines.Exact(true, lowestById, medianById);
			lowest = lowestGlide.follow(lowestById ? lowest : prices.lowest(), now);
			median = medianGlide.follow(medianById ? median : prices.median(), now);
			craft = prices.craft();
			loading = !prices.answered();
		}
		return new AddedLines.Facts(bazaar, quality.isPresent(), quality.orElse(0), DungeonData.floor(data), lowest, SkyBlockPrices.lowestSource(), lowestAge,
			median, days, market.soldPerDay(), craft, market.trendPercent(), buyNow, sellNow, volume, stack.getCount(), shiftHeld(),
			SkyBlockPrices.npcSell(id), market.fetchedAt() > 0L ? now - market.fetchedAt() : -1L,
			SkyBlockPrices.bazaarFetchedAt() > 0L ? now - SkyBlockPrices.bazaarFetchedAt() : -1L, exact, soulbound, loading,
			days == AuctionPrices.DAYS && auction.known() && !Double.isNaN(auction.median()) && !exact.forThisItem(), "");
	}

	/**
	 * An enchanted book with several enchantments: the sum of their Bazaar products, and it says so. Where one of them has no price there, the book
	 * has none, never a part of its worth.
	 */
	private AddedLines.Facts summedFacts(ItemStack stack) {
		double buy = SkyBlockPrices.bazaarSum(target.sum(), true);
		double sell = SkyBlockPrices.bazaarSum(target.sum(), false);
		long now = System.currentTimeMillis();
		return new AddedLines.Facts(true, false, 0, "", Double.NaN, SkyBlockPrices.lowestSource(), -1L, Double.NaN, medianSpan.get().days, Double.NaN,
			Double.NaN, Double.NaN, buy, sell, Double.NaN, stack.getCount(), shiftHeld(), 0d, -1L,
			SkyBlockPrices.bazaarFetchedAt() > 0L ? now - SkyBlockPrices.bazaarFetchedAt() : -1L, AddedLines.Exact.NO, soulbound, !SkyBlockPrices.ready(), false,
			AddedLines.SUM_OF_ENCHANTMENTS);
	}

	private static boolean shiftHeld() {
		//#if 26.3
		//$ return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
		//#else
		var window = Minecraft.getInstance().getWindow();
		return InputConstants.isKeyDown(window, InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(window, InputConstants.KEY_RSHIFT);
		//#endif
	}

	/** @return whether an item can't be sold, by the line the game shows on it */
	private static boolean soulbound(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return false;
		}
		for (Component line : lore.lines()) {
			if (isSoulboundLine(TextColors.noColors(line.getString()).strip())) {
				return true;
			}
		}
		return false;
	}

	/** @return whether a line of an item's lore says that it is soulbound, as the game writes it */
	static boolean isSoulboundLine(String line) {
		return line.equals(SOULBOUND_LINE) || line.equals(COOP_SOULBOUND_LINE);
	}

	private Object prefetchedFor;
	private int prefetchedState = -1;

	/** Once a tick: keeps the bulk list fresh, and asks for the exact prices of the items in an open inventory, slowly, in the background. */
	public void tick(Minecraft client) {
		if (!isEnabled() || !SkyBlock.allowed()) {
			return;
		}
		SkyBlockPrices.refreshIfOld();
		ExactPrices.INSTANCE.tick();
		if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
			prefetchedFor = null;
			return;
		}
		int state = screen.getMenu().getStateId();
		if (screen == prefetchedFor && state == prefetchedState) {
			return;
		}
		prefetchedFor = screen;
		prefetchedState = state;
		List<String> enabled = lines.enabledInOrder();
		if (!enabled.contains(AddedLines.LOWEST_BIN) && !enabled.contains(AddedLines.MEDIAN) && !enabled.contains(AddedLines.CRAFT)) {
			return;
		}
		boolean wantCraft = enabled.contains(AddedLines.CRAFT);
		List<Candidate> candidates = new ArrayList<>();
		for (Slot slot : screen.getMenu().slots) {
			ItemStack stack = slot.getItem();
			String id = stack.isEmpty() ? null : ItemGains.idOf(stack);
			CustomData custom = stack.isEmpty() ? null : stack.get(DataComponents.CUSTOM_DATA);
			if (id != null && custom != null && !soulbound(stack)) {
				candidates.add(new Candidate(id, TextColors.noColors(stack.getHoverName().getString()), custom.copyTag()));
			}
		}
		// the items' data is made ready away from the game's thread
		ExactPrices.INSTANCE.inBackground(() -> {
			for (Candidate candidate : candidates) {
				JsonObject data = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, candidate.data()) instanceof JsonObject object ? object : new JsonObject();
				if (!ExactItem.relevant(data.keySet())) {
					continue;
				}
				PriceKeys.Target priceTarget = PriceKeys.of(candidate.gameId(), data, ItemRepo::shardProduct);
				String priced = priceTarget.id();
				if (priceTarget.exact() && !SkyBlockPrices.onBazaar(priced)) {
					ExactPrices.INSTANCE.prefetch(priced, candidate.name(), data, priced + ExactItem.request(priced, "", data), wantCraft);
				}
			}
		});
	}

	/** An item of an open inventory, as it is taken along to the background. */
	private record Candidate(String gameId, String name, CompoundTag data) {
	}

	/** @return what the parts of its crafting recipe cost now, per item it makes; NaN without a recipe, or while a part has no price */
	private static double craftCost(String id) {
		for (ItemRepo.Recipe recipe : ItemRepo.recipesFor(id)) {
			if (recipe.crafting()) {
				return costOf(id, recipe, SkyBlockPrices::listedPrice);
			}
		}
		return Double.NaN;
	}

	/** @return what a recipe's parts cost, per item it makes; a grid's empty cells cost nothing, and a part without a price makes it NaN */
	static double costOf(String id, ItemRepo.Recipe recipe, ToDoubleFunction<String> priceOf) {
		double total = 0d;
		for (ItemRepo.Stack input : recipe.inputs()) {
			if (input != null) {
				double each = priceOf.applyAsDouble(input.id());
				if (!PriceText.known(each)) {
					return Double.NaN;
				}
				total += each * input.count();
			}
		}
		double made = 1d;
		for (ItemRepo.Stack output : recipe.outputs()) {
			if (output != null && output.id().equals(id) && output.count() > 0d) {
				made = output.count();
			}
		}
		return total > 0d ? total / made : Double.NaN;
	}
}
