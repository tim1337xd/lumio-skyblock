package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.api.InfoSegment;
import dev.tim1337.lumio.api.InfoValueProvider;
import dev.tim1337.lumio.api.InfoValues;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.skyblock.data.Mayor;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The SkyBlock segments of the info bar: the SkyBlock date and time, the purse, bits and copper from the scoreboard, the mayor, and the island for
 * the Area segment. The texts are made only when their source changes, so reading them a few times per second makes nothing new.
 */
final class SkyBlockInfoValues implements InfoValueProvider {
	private static final String SKYBLOCK_TIME = "skyblock_time";
	private static final String PURSE = "purse";
	private static final String BITS = "bits";
	private static final String COPPER = "copper";
	private static final String MAYOR = "mayor";
	/** The widest the SkyBlock date and time is drawn, in GUI pixels: wider than other texts, to fit "Early Spring 12th, 6:40pm". */
	private static final float DATE_WIDTH = 150f;
	/** The SkyBlock segments, listed where they were in the bar's list before the add-on had its own: the date after the clock, the money after the coordinates. */
	private static final List<InfoSegment> SEGMENTS = List.of(
		new InfoSegment(SKYBLOCK_TIME, "SkyBlock date and time", "Date", "sun", 0, false, DATE_WIDTH, "clock", List.of()),
		new InfoSegment(PURSE, "Purse", "Purse", "coins", SystemColors.YELLOW, true, InfoSegment.TEXT_WIDTH, "coords", List.of()),
		new InfoSegment(BITS, "Bits", "Bits", "star", SystemColors.TEAL, true, InfoSegment.TEXT_WIDTH, PURSE, List.of()),
		new InfoSegment(COPPER, "Copper", "Copper", "wheat", SystemColors.ORANGE, true, InfoSegment.TEXT_WIDTH, BITS, List.of()),
		new InfoSegment(MAYOR, "Mayor", "Mayor", "crown", SystemColors.YELLOW, false, InfoSegment.TEXT_WIDTH, COPPER, List.of()));

	private static final Pattern DATE = Pattern.compile("^(?:Early |Late )?(?:Spring|Summer|Autumn|Fall|Winter)\\s+\\d+\\w*");
	private static final Pattern TIME = Pattern.compile("^(\\d{1,2}:\\d{2}\\s?(?:am|pm))", Pattern.CASE_INSENSITIVE);
	private static final Pattern AMOUNT = Pattern.compile("^(?:Bits|Copper):\\s*([\\d,.]+[kKmMbB]?)");

	private long shownPurse = Long.MIN_VALUE;
	private String purseText = "";
	/** The sidebar list last parsed: it is replaced when it is read again, about once a second, and parsed only then. */
	private List<String> parsedSidebar;
	private String timeText;
	private String bitsText;
	private String copperText;
	private String shownMayor = "";
	private String mayorText = "";

	@Override
	public List<InfoSegment> segments() {
		return SEGMENTS;
	}

	@Override
	public String area() {
		if (!SkyBlock.allowed() || !SkyBlock.inSkyBlock()) {
			return null;
		}
		String area = SkyBlockArea.island();
		return area.isEmpty() ? SkyBlockArea.zone() : area;
	}

	@Override
	public void read(InfoValues values) {
		if (!SkyBlock.allowed() || !SkyBlock.inSkyBlock()) {
			for (InfoSegment segment : SEGMENTS) {
				values.none(segment.id());
			}
			parsedSidebar = null;
			return;
		}
		long purse = SkyBlockArea.purse();
		if (purse < 0) {
			values.none(PURSE);
		} else {
			if (purse != shownPurse) {
				shownPurse = purse;
				purseText = PanelKit.grouped(purse);
			}
			values.set(PURSE, purseText);
		}
		List<String> sidebar = SkyBlockArea.sidebar();
		if (sidebar != parsedSidebar) {
			parsedSidebar = sidebar;
			parseSidebar(sidebar);
		}
		show(values, SKYBLOCK_TIME, timeText);
		show(values, BITS, bitsText);
		show(values, COPPER, copperText);
		String mayor = TextColors.noColors(Mayor.line());
		if (mayor.isEmpty()) {
			values.none(MAYOR);
		} else {
			if (!mayor.equals(shownMayor)) {
				shownMayor = mayor;
				mayorText = mayorName(mayor);
			}
			values.set(MAYOR, mayorText);
		}
	}

	private static void show(InfoValues values, String segment, String text) {
		if (text == null) {
			values.none(segment);
		} else {
			values.set(segment, text);
		}
	}

	/** Reads the date, time, bits and copper from the sidebar lines. */
	private void parseSidebar(List<String> sidebar) {
		String date = null;
		String time = null;
		String bits = null;
		String copper = null;
		for (String line : sidebar) {
			if (date == null) {
				Matcher found = DATE.matcher(line);
				if (found.find()) {
					date = found.group();
					continue;
				}
			}
			if (time == null) {
				Matcher found = TIME.matcher(line);
				if (found.find()) {
					time = found.group(1);
					continue;
				}
			}
			if (line.startsWith("Bits:") || line.startsWith("Copper:")) {
				Matcher amount = AMOUNT.matcher(line);
				if (amount.find()) {
					if (line.startsWith("Bits:")) {
						bits = amount.group(1);
					} else {
						copper = amount.group(1);
					}
				}
			}
		}
		timeText = date == null && time == null ? null : date == null ? time : time == null ? date : date + ", " + time;
		bitsText = bits;
		copperText = copper;
	}

	/** @return the mayor's name from the line "Mayor: Aatrox · Diana" */
	private static String mayorName(String line) {
		String name = line.startsWith("Mayor:") ? line.substring("Mayor:".length()).strip() : line;
		int minister = name.indexOf(" · ");
		return minister > 0 ? name.substring(0, minister) : name;
	}
}
