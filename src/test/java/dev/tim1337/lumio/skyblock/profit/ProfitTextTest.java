package dev.tim1337.lumio.skyblock.profit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Totals beside the ring stay short. */
class ProfitTextTest {
	@Test
	void largeTotalsLoseADecimal() {
		assertEquals("+128.6M", ProfitText.total(128_580_000));
		assertEquals("+999.9M", ProfitText.total(999_900_000));
		assertEquals("+128.6B", ProfitText.total(128_580_000_000.0));
	}

	@Test
	void smallerTotalsStayAsTheyAre() {
		assertEquals("+1.43M", ProfitText.total(1_430_000));
		assertEquals("+78.0k", ProfitText.total(78_000));
		assertEquals("+12", ProfitText.total(12));
	}

	@Test
	void lossesKeepTheirSign() {
		assertEquals("-128.6M", ProfitText.total(-128_580_000));
	}

	@Test
	void noTotalIsLongerThanSevenCharacters() {
		for (double value = 1; value < 9e11; value *= 3.7) {
			assertTrue(ProfitText.total(value).length() <= 7, ProfitText.total(value));
		}
	}
}
