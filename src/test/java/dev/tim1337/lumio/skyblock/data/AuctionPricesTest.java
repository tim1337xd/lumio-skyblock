package dev.tim1337.lumio.skyblock.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/** SkyCofl's answers are read carefully, and a pause it asks for is kept. */
class AuctionPricesTest {
	@Test
	void retryAfterIsKeptWithinBounds() {
		assertEquals(30_000L, SkyBlockPrices.retryAfterMillis("30"));
		assertEquals(60_000L, SkyBlockPrices.retryAfterMillis(null), "nothing said: a minute");
		assertEquals(60_000L, SkyBlockPrices.retryAfterMillis("Wed, 21 Oct 2015 07:28:00 GMT"), "a date: a minute");
		assertEquals(1_000L, SkyBlockPrices.retryAfterMillis("0"), "at least a second");
		assertEquals(15 * 60_000L, SkyBlockPrices.retryAfterMillis("999999"), "at most a quarter of an hour");
	}

	@Test
	void numbersAreReadOnlyWhenTheyMakeSense() {
		JsonObject bin = JsonParser.parseString("{\"lowest\":517990000,\"uuid\":\"x\",\"secondLowest\":560000000}").getAsJsonObject();
		assertEquals(517_990_000d, AuctionPrices.positive(bin, "lowest"));
		JsonObject analysis = JsonParser.parseString("{\"medianPrice\":0,\"totalSales\":0,\"avgPrice\":\"many\",\"minPrice\":null}").getAsJsonObject();
		assertTrue(Double.isNaN(AuctionPrices.positive(analysis, "medianPrice")), "no median is not a price of 0");
		assertEquals(0d, AuctionPrices.atLeastZero(analysis, "totalSales"), "none sold is a count");
		assertTrue(Double.isNaN(AuctionPrices.positive(analysis, "avgPrice")), "text is no number");
		assertTrue(Double.isNaN(AuctionPrices.positive(analysis, "minPrice")), "null is no number");
		assertTrue(Double.isNaN(AuctionPrices.positive(analysis, "missing")));
		JsonObject negative = JsonParser.parseString("{\"totalSales\":-5}").getAsJsonObject();
		assertTrue(Double.isNaN(AuctionPrices.atLeastZero(negative, "totalSales")));
	}

	@Test
	void theItemPageIsSkyCoflsOwn() {
		assertEquals("https://sky.coflnet.com/item/HYPERION", AuctionPrices.pageOf("HYPERION"));
	}
}
