package dev.tim1337.lumio.skyblock.tooltip;

import dev.tim1337.lumio.api.TooltipLine;

import net.minecraft.nbt.CompoundTag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The lines of a tooltip from sample data: the dungeon's, their order and switches, and how numbers read. */
class AddedLinesTest {
	private static final List<String> ALL = List.of(AddedLines.DUNGEON, AddedLines.LOWEST_BIN, AddedLines.MEDIAN, AddedLines.SOLD, AddedLines.CRAFT, AddedLines.BAZAAR, AddedLines.TREND, AddedLines.NPC);

	private static AddedLines.Facts auction() {
		return auction(1, false, AddedLines.Exact.NO, false, false);
	}

	private static AddedLines.Facts auction(int count, boolean shift, AddedLines.Exact exact, boolean soulbound, boolean loading) {
		return new AddedLines.Facts(false, true, 50, TooltipLine.MISSING, 4_200_000d, "Moulberry", 120_000L, 4_550_000d, 7, 38d, 3_900_000d, -8d, 0d, 0d, 0d, count, shift, 12d, 1000L, -1L, exact, soulbound, loading);
	}

	private static AddedLines.Facts bazaar() {
		return bazaar(1, false);
	}

	private static AddedLines.Facts bazaar(int count, boolean shift) {
		return new AddedLines.Facts(true, false, 0, TooltipLine.MISSING, Double.NaN, "", 0L, Double.NaN, 7, Double.NaN, Double.NaN, 6d, 258_400d, 251_900d, 1_000_000d, count, shift, Double.NaN, -1L, 1000L, AddedLines.Exact.NO, false, false);
	}

	private static List<String> names(List<TooltipLine> lines) {
		return lines.stream().map(TooltipLine::text).toList();
	}

	private static String value(List<TooltipLine> lines, String name) {
		return lines.stream().filter(line -> line.text().equals(name)).findFirst().orElseThrow().value();
	}

	@Test
	void theDungeonQualityComesFromTheItemsOwnData() {
		CompoundTag data = new CompoundTag();
		data.putInt("baseStatBoostPercentage", 37);
		assertEquals(37, DungeonData.quality(data).orElse(-1));
		assertTrue(DungeonData.quality(new CompoundTag()).isEmpty());
	}

	@Test
	void aQualityOutOfBoundsIsNotShown() {
		CompoundTag data = new CompoundTag();
		data.putInt("baseStatBoostPercentage", 900);
		assertTrue(DungeonData.quality(data).isEmpty());
	}

	@Test
	void theFloorIsADashUntilItIsKnownHowToReadIt() {
		CompoundTag data = new CompoundTag();
		data.putInt("item_tier", 3);
		assertEquals(TooltipLine.MISSING, DungeonData.floor(data));
	}

	@Test
	void theDebugLineListsTheFieldsThatCanSayTheFloor() {
		CompoundTag data = new CompoundTag();
		data.putInt("item_tier", 3);
		data.putInt("baseStatBoostPercentage", 50);
		data.putString("id", "SKELETON_MASTER_CHESTPLATE");
		data.putString("uuid", "not listed");
		assertEquals("baseStatBoostPercentage=50 item_tier=3", DungeonData.debugLine(data));
		assertEquals("", DungeonData.debugLine(new CompoundTag()));
	}

	@Test
	void linesComeInThePlayersOrderUnderTheirHeading() {
		List<TooltipLine> lines = AddedLines.build(List.of(AddedLines.SOLD, AddedLines.LOWEST_BIN), auction(), PriceText.Style.SHORT);
		assertEquals(List.of("Sold", "Lowest BIN"), names(lines));
	}

	@Test
	void noLineSaysWhereThePricesComeFrom() {
		List<String> all = List.of(AddedLines.DUNGEON, AddedLines.LOWEST_BIN, AddedLines.MEDIAN, AddedLines.SOLD, AddedLines.CRAFT, AddedLines.BAZAAR, AddedLines.TREND, AddedLines.NPC);
		for (AddedLines.Facts facts : List.of(auction(), bazaar())) {
			for (TooltipLine line : AddedLines.build(all, facts, PriceText.Style.SHORT)) {
				assertTrue(line.kind() != TooltipLine.Kind.FOOTNOTE, line.text());
			}
		}
	}

	@Test
	void nothingIsAddedTwice() {
		List<String> all = List.of(AddedLines.DUNGEON, AddedLines.LOWEST_BIN, AddedLines.MEDIAN, AddedLines.SOLD, AddedLines.CRAFT, AddedLines.BAZAAR, AddedLines.TREND, AddedLines.NPC);
		for (AddedLines.Facts facts : List.of(auction(), bazaar())) {
			List<String> names = names(AddedLines.build(all, facts, PriceText.Style.SHORT));
			assertEquals(names.size(), new java.util.HashSet<>(names).size(), names.toString());
		}
	}

	@Test
	void anOldConfigGetsTheNewDefaultsAndKeepsTheRest() {
		com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString("{\"lines\":{\"rows\":[\"lowest_bin\",\"sold\",\"npc\"],\"off\":[\"craft\"],\"known\":[]}}").getAsJsonObject();
		ItemTooltipLinesFeature.turnOff(json, List.of(AddedLines.SOLD, AddedLines.CRAFT, AddedLines.TREND));
		assertEquals("[\"craft\",\"sold\",\"trend\"]", json.getAsJsonObject("lines").get("off").toString());
		assertEquals("[\"lowest_bin\",\"sold\",\"npc\"]", json.getAsJsonObject("lines").get("rows").toString());
	}

	@Test
	void aConfigWithoutTheListStaysAsItIs() {
		com.google.gson.JsonObject json = new com.google.gson.JsonObject();
		ItemTooltipLinesFeature.turnOff(json, List.of(AddedLines.SOLD));
		assertEquals("{}", json.toString());
	}

	@Test
	void aLineSwitchedOffIsGone() {
		List<TooltipLine> lines = AddedLines.build(List.of(AddedLines.LOWEST_BIN), auction(), PriceText.Style.SHORT);
		assertFalse(names(lines).contains("Median"));
		assertFalse(names(lines).contains("Quality"));
		assertEquals(List.of(), AddedLines.build(List.of(), auction(), PriceText.Style.SHORT));
	}

	@Test
	void anAuctionItemGetsTheMarketAndABazaarItemTheBazaar() {
		List<TooltipLine> auction = AddedLines.build(ALL, auction(), PriceText.Style.SHORT);
		assertTrue(names(auction).containsAll(List.of("Quality", "Floor obtained", "Lowest BIN", "Median", "Sold", "Craft cost", "7-day trend", "NPC price")));
		assertFalse(names(auction).contains("Buy now"));
		List<TooltipLine> bazaar = AddedLines.build(ALL, bazaar(), PriceText.Style.SHORT);
		assertTrue(names(bazaar).containsAll(List.of("BAZAAR", "Buy now", "Sell now", "Volume")));
		assertFalse(names(bazaar).contains("Buy order"), "the two prices that are about the same are gone");
		assertFalse(names(bazaar).contains("Sell offer"));
		assertFalse(names(bazaar).contains("Lowest BIN"));
		assertFalse(names(bazaar).contains("Quality"));
	}

	@Test
	void theMedianIsMarkedAsAnEstimate() {
		List<TooltipLine> lines = AddedLines.build(ALL, auction(), PriceText.Style.SHORT);
		assertEquals("≈4.55M", value(lines, "Median"));
		assertEquals("50 / 50", value(lines, "Quality"));
		assertEquals("—", value(lines, "Floor obtained"));
		assertEquals("3.90M", value(lines, "Craft cost"));
		assertEquals("−8 %", value(lines, "7-day trend"));
	}

	@Test
	void missingDataIsADashNeverAValue() {
		AddedLines.Facts none = new AddedLines.Facts(false, false, 0, "", Double.NaN, "", 0L, Double.NaN, 7, Double.NaN, Double.NaN, Double.NaN, 0d, 0d, 0d, 1, false, Double.NaN, -1L, -1L, AddedLines.Exact.NO, false, false);
		List<TooltipLine> lines = AddedLines.build(ALL, none, PriceText.Style.SHORT);
		for (String name : List.of("Lowest BIN", "Median", "Sold", "Craft cost", "7-day trend", "NPC price")) {
			assertEquals("—", value(lines, name), name);
		}
		assertEquals(List.of(), lines.stream().filter(line -> line.kind() == TooltipLine.Kind.FOOTNOTE).toList());
	}

	@Test
	void shiftGivesTheRealStackOnTheBazaar() {
		List<TooltipLine> plain = AddedLines.build(ALL, bazaar(23, false), PriceText.Style.SHORT);
		TooltipLine heading = plain.stream().filter(line -> line.kind() == TooltipLine.Kind.HEADING).findFirst().orElseThrow();
		assertEquals("BAZAAR", heading.text());
		assertEquals("Shift: ×23", heading.value(), "a hint without Shift");
		assertEquals("258.4k", value(plain, "Buy now"), "per item");
		List<TooltipLine> stack = AddedLines.build(ALL, bazaar(23, true), PriceText.Style.SHORT);
		assertTrue(names(stack).contains("BAZAAR · ×23"));
		assertEquals("5.94M", value(stack, "Buy now"));
		assertEquals("5.79M", value(stack, "Sell now"));
		assertEquals("1.00M", value(stack, "Volume"), "kept as it is");
	}

	@Test
	void oneItemHasNoHint() {
		List<TooltipLine> one = AddedLines.build(ALL, bazaar(1, true), PriceText.Style.SHORT);
		assertEquals("", one.stream().filter(line -> line.kind() == TooltipLine.Kind.HEADING).findFirst().orElseThrow().value());
		assertTrue(names(one).contains("BAZAAR"));
		assertFalse(names(AddedLines.build(ALL, auction(), PriceText.Style.SHORT)).contains("AUCTION"), "a single auction item stays as it was");
	}

	@Test
	void shiftGivesTheRealStackAtAuction() {
		List<TooltipLine> plain = AddedLines.build(ALL, auction(4, false, AddedLines.Exact.NO, false, false), PriceText.Style.SHORT);
		assertEquals("Shift: ×4", plain.stream().filter(line -> line.text().equals("AUCTION")).findFirst().orElseThrow().value());
		assertEquals("4.20M", value(plain, "Lowest BIN"));
		List<TooltipLine> stack = AddedLines.build(ALL, auction(4, true, AddedLines.Exact.NO, false, false), PriceText.Style.SHORT);
		assertTrue(names(stack).contains("AUCTION · ×4"));
		assertEquals("16.80M", value(stack, "Lowest BIN"));
		assertEquals("≈18.20M", value(stack, "Median"));
		assertEquals("15.60M", value(stack, "Craft cost"), "a price like the others");
	}

	@Test
	void aPriceForTheTypeSaysSo() {
		List<TooltipLine> lines = AddedLines.build(ALL, auction(1, false, new AddedLines.Exact(true, true, true), false, true), PriceText.Style.SHORT);
		TooltipLine lowest = lines.stream().filter(line -> line.text().equals("Lowest BIN")).findFirst().orElseThrow();
		assertEquals("(base item)", lowest.note());
		assertEquals("4.20M", lowest.value(), "shown at once");
		assertEquals("", AddedLines.build(ALL, auction(1, false, new AddedLines.Exact(true, false, false), false, false), PriceText.Style.SHORT).stream().filter(line -> line.text().equals("Lowest BIN")).findFirst().orElseThrow().note());
	}

	@Test
	void aSoulboundItemSaysItCantBeSoldAndKeepsItsBasePrice() {
		List<TooltipLine> lines = AddedLines.build(List.of(AddedLines.LOWEST_BIN), auction(1, false, new AddedLines.Exact(true, true, true), true, false), PriceText.Style.SHORT);
		assertEquals(TooltipLine.Kind.FOOTNOTE, lines.get(0).kind());
		assertEquals("Soulbound: can't be sold", lines.get(0).text());
		assertEquals("Lowest BIN", lines.get(1).text(), "under it");
		assertEquals("(base item)", lines.get(1).note());
		assertTrue(ItemTooltipLinesFeature.isSoulboundLine("* Soulbound *"));
		assertTrue(ItemTooltipLinesFeature.isSoulboundLine("* Co-op Soulbound *"));
		assertFalse(ItemTooltipLinesFeature.isSoulboundLine("Soulbound items can't be traded"), "only the game's own line");
	}

	@Test
	void whatIsStillToComeIsMarkedAndWhatIsNotKnownIsADash() {
		AddedLines.Facts loading = new AddedLines.Facts(false, false, 0, "", Double.NaN, "", 0L, Double.NaN, 7, Double.NaN, Double.NaN, Double.NaN, 0d, 0d, 0d, 1, false, Double.NaN, -1L, -1L, new AddedLines.Exact(true, true, true), false, true);
		List<TooltipLine> lines = AddedLines.build(ALL, loading, PriceText.Style.SHORT);
		assertEquals("…", value(lines, "Lowest BIN"));
		assertEquals("…", value(lines, "Median"));
	}

	@Test
	void aBetterPriceGlidesInAndANewItemStartsAtOnce() {
		PriceGlide glide = new PriceGlide();
		assertEquals(100d, glide.follow(100d, 0L), "the first number is simply there");
		double halfway = glide.follow(200d, 1000L + PriceGlide.GLIDE_MILLIS / 2);
		assertEquals(100d, halfway, "it starts from what was shown");
		double moving = glide.follow(200d, 1000L + PriceGlide.GLIDE_MILLIS / 2 + 50L);
		assertTrue(moving > 100d && moving < 200d, "on its way: " + moving);
		assertEquals(200d, glide.follow(200d, 5000L), 0.001d, "and there");
		glide.reset();
		assertEquals(50d, glide.follow(50d, 6000L), "another item");
	}

	@Test
	void craftCostAddsThePartsAndSkipsEmptyCells() {
		java.util.List<dev.tim1337.lumio.skyblock.data.ItemRepo.Stack> cells = new java.util.ArrayList<>();
		cells.add(new dev.tim1337.lumio.skyblock.data.ItemRepo.Stack("A", 2d));
		cells.add(null);
		cells.add(new dev.tim1337.lumio.skyblock.data.ItemRepo.Stack("B", 1d));
		var recipe = new dev.tim1337.lumio.skyblock.data.ItemRepo.Recipe("crafting", cells, List.of(new dev.tim1337.lumio.skyblock.data.ItemRepo.Stack("OUT", 2d)), "", "");
		assertEquals(150d, ItemTooltipLinesFeature.costOf("OUT", recipe, id -> id.equals("A") ? 100d : 100d), 0.001d);
	}

	@Test
	void craftCostIsNotKnownWhileAPartHasNoPrice() {
		var recipe = new dev.tim1337.lumio.skyblock.data.ItemRepo.Recipe("crafting", List.of(new dev.tim1337.lumio.skyblock.data.ItemRepo.Stack("A", 1d)), List.of(), "", "");
		assertTrue(Double.isNaN(ItemTooltipLinesFeature.costOf("OUT", recipe, id -> 0d)));
	}

	@Test
	void numbersReadInBothStyles() {
		assertEquals("4.20M", PriceText.number(4_200_000d, PriceText.Style.SHORT));
		assertEquals("258.4k", PriceText.number(258_400d, PriceText.Style.SHORT));
		assertEquals("1.25B", PriceText.number(1_250_000_000d, PriceText.Style.SHORT));
		assertEquals("950", PriceText.number(950d, PriceText.Style.SHORT));
		assertEquals("4,200,000", PriceText.number(4_200_000d, PriceText.Style.FULL));
		assertEquals("999", PriceText.number(999d, PriceText.Style.FULL));
		assertEquals("—", PriceText.number(Double.NaN, PriceText.Style.FULL));
		assertEquals("—", PriceText.number(0d, PriceText.Style.SHORT));
	}

	@Test
	void changesAndAgesRead() {
		assertEquals("+6 %", PriceText.percent(6.2d));
		assertEquals("−8 %", PriceText.percent(-7.9d));
		assertEquals("—", PriceText.percent(Double.NaN));
		assertEquals("just now", PriceText.age(20_000L));
		assertEquals("2 min ago", PriceText.age(125_000L));
		assertEquals("3 h ago", PriceText.age(3 * 3_600_000L));
	}
}
