package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.experimental.util.TextColors;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * The address of an item's or a mob's page on the community Hypixel SkyBlock Wiki (hypixelskyblock.minecraft.wiki), made from its clean name.
 * <p>
 * The mod fetches nothing, so it cannot know whether a page of that exact name exists. It opens MediaWiki's search with "go" instead: the wiki
 * jumps straight to the page when one matches the name and shows its search results otherwise, so a link never ends on a missing page. The
 * address only opens after the game's "Open link?" prompt. Plain text, so it is unit tested.
 */
final class WikiLink {
	/** The wiki's search; with go=Go it opens the page of an exact match. */
	static final String SEARCH = "https://hypixelskyblock.minecraft.wiki/w/Special:Search?search=";
	private static final String GO = "&go=Go";

	/** Bracketed parts anywhere: "(Boss)", "[Lv100]", "[Lvl 5]", "{LVL}". */
	private static final Pattern BRACKETED = Pattern.compile("[\\[({][^\\])}]{0,40}[\\])}]");
	/** Everything but letters, digits and the usual marks: stars, rune and gem symbols, icons. */
	private static final Pattern NOT_IN_NAMES = Pattern.compile("[^\\p{L}\\p{Nd} '\\-.&+!]");
	private static final Pattern SPACES = Pattern.compile("\\s+");
	/** A mob's tier at its end: "Zombie Villager 3", "Sea Walker IV". */
	private static final Pattern MOB_TIER = Pattern.compile("\\s+(?:[IVX]{1,5}|\\d{1,3})$");
	/**
	 * The dragons' kinds in front of "Dragon" or "Ender Dragon": the wiki has one page for the dragon, the kinds are parts of it. Only for mobs,
	 * so an item such as "Superior Dragon Helmet" keeps its name.
	 */
	private static final Pattern DRAGON_KIND = Pattern.compile("^(?:Superior|Strong|Young|Old|Protector|Unstable|Wise)\\s+(?=(?:Ender\\s+)?Dragon$)");

	private WikiLink() {
	}

	/**
	 * @param mob whether it is a mob (its tier and the dragons' kinds are left out too)
	 * @return the name the wiki knows it by: without colors, bracketed parts, levels and symbols; or "" for none
	 */
	static String page(String name, boolean mob) {
		if (name == null) {
			return "";
		}
		String plain = TextColors.noColors(name);
		plain = BRACKETED.matcher(plain).replaceAll(" ");
		plain = NOT_IN_NAMES.matcher(plain).replaceAll(" ");
		plain = SPACES.matcher(plain).replaceAll(" ").strip();
		if (mob) {
			plain = MOB_TIER.matcher(plain).replaceFirst("");
			plain = DRAGON_KIND.matcher(plain).replaceFirst("");
		}
		return plain;
	}

	/** @return the address that opens its wiki page (or the wiki's search for it), or "" when it has no name to look up */
	static String of(String name, boolean mob) {
		String page = page(name, mob);
		return page.isEmpty() ? "" : SEARCH + URLEncoder.encode(page, StandardCharsets.UTF_8) + GO;
	}
}
