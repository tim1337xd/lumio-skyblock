package dev.tim1337.lumio.skyblock.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WikiLinkTest {
	@Test
	void itemsKeepTheirNameWithoutMarks() {
		assertEquals("Aspect of the Void", WikiLink.page("Aspect of the Void", false));
		assertEquals("Hyperion", WikiLink.page("§6Hyperion ✪✪✪✪✪", false));
		assertEquals("Ender Dragon", WikiLink.page("[Lvl 100] Ender Dragon", false));
		assertEquals("Superior Dragon Helmet", WikiLink.page("Superior Dragon Helmet", false), "an item's kind word stays");
		assertEquals("Music Rune I", WikiLink.page("◆ Music Rune I", false), "an item's tier stays");
		assertEquals("Farmer's Boots", WikiLink.page("Farmer's Boots", false));
	}

	@Test
	void mobsLoseTheirKindLevelAndTier() {
		assertEquals("Ender Dragon", WikiLink.page("Superior Ender Dragon (Boss)", true));
		assertEquals("Ender Dragon", WikiLink.page("Young Ender Dragon", true));
		assertEquals("Zombie", WikiLink.page("[Lv5] Zombie", true));
		assertEquals("Revenant Horror", WikiLink.page("§cRevenant Horror IV", true));
		assertEquals("Zombie Villager", WikiLink.page("Zombie Villager 3", true));
		assertEquals("Strong Dragon Fragment", WikiLink.page("Strong Dragon Fragment", true), "a kind word before something else stays");
	}

	@Test
	void theLinkOpensTheWikisSearchWithGo() {
		assertEquals("https://hypixelskyblock.minecraft.wiki/w/Special:Search?search=Aspect+of+the+Void&go=Go", WikiLink.of("Aspect of the Void", false));
		assertEquals("https://hypixelskyblock.minecraft.wiki/w/Special:Search?search=Farmer%27s+Boots&go=Go", WikiLink.of("Farmer's Boots", false));
		assertEquals("https://hypixelskyblock.minecraft.wiki/w/Special:Search?search=Ender+Dragon&go=Go", WikiLink.of("Superior Ender Dragon (Boss)", true));
	}

	@Test
	void noNameNoLink() {
		assertEquals("", WikiLink.of("", false));
		assertEquals("", WikiLink.of(null, false));
		assertEquals("", WikiLink.of("✪✪", false));
		assertEquals("", WikiLink.of("(Boss)", true));
	}
}
