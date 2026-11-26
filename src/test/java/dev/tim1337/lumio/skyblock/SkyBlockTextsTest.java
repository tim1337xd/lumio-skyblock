package dev.tim1337.lumio.skyblock;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Adapters;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.PageOutlines;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.config.setting.ChipsInfoSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.HomeSetting;
import dev.tim1337.lumio.config.setting.InfoSetting;
import dev.tim1337.lumio.config.setting.KeySetting;
import dev.tim1337.lumio.config.setting.MultiSelectSetting;
import dev.tim1337.lumio.config.setting.NoteSetting;
import dev.tim1337.lumio.config.setting.PreviewSetting;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.config.setting.StringSetting;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Short texts in the add-on's settings, as the main mod checks its own (SettingTextsTest there): every label fits beside its control, every description and page line is one line, a dropdown's choices fit its
 * button and every tooltip its box, in the mod's font and in the Minecraft font, measured in a sheet (the narrowest place they are shown) at GUI
 * scale 2. Longer explanations belong in the row's tooltip.
 * The menus' own audit in the game (fitaudit) checks the drawn pages; this catches a long text before a game is started.
 */
class SkyBlockTextsTest {
	/**
	 * The room for a label in a sheet (about 230 menu pixels of row), in menu pixels at the body size: beside a switch or a slider, and beside a
	 * wide control (a dropdown, a text or key field, a list of choices), which takes about half the row.
	 */
	static final float LABEL_MAX = 170f;
	static final float LABEL_MAX_WIDE = 100f;
	/** The room for a description in a sheet, under an indented row: one line. */
	static final float DESCRIPTION_MAX = 220f;
	/** The room for a page's line under its title in a sheet. */
	static final float PAGE_LINE_MAX = 240f;
	/** The tooltip's mark after a name, with its gap. */
	static final float TIP_MARK = 13f;
	/** A dropdown's choice: the room its button gets beside a short label in a sheet, less the room beside its text. */
	static final float CHOICE_MAX = 110f;
	/** A tooltip's text: its box's width less its padding, and its most lines. */
	static final float TOOLTIP_TEXT = 280f;
	static final int TOOLTIP_LINES = 6;

	private static Font inter;
	private static final java.util.Map<String, String> ENGLISH = new java.util.HashMap<>();
	private static final String[] LANGUAGE_FILES = {"/assets/lumio/lang/en_us.json", "/assets/lumio-skyblock/lang/en_us.json"};
	private static final FontRenderContext CONTEXT = new FontRenderContext(null, true, true);
	/** Inter's size at which its capitals are as tall as the Minecraft font's (7 of its 8 pixels). */
	private static final float INTER_SIZE = 9.6f;

	@BeforeAll
	static void setUp() throws Exception {
		System.setProperty("java.awt.headless", "true");
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ModFeatures.createAll();
		PageOutlines.apply("lumio-skyblock");
		try (InputStream in = SkyBlockTextsTest.class.getResourceAsStream("/assets/lumio/font/inter.ttf")) {
			inter = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(INTER_SIZE);
		}
		// key names are translation keys: the menus show their English names
		for (String lang : LANGUAGE_FILES) {
			try (InputStream in = SkyBlockTextsTest.class.getResourceAsStream(lang)) {
				if (in != null) {
					com.google.gson.JsonParser.parseString(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject()
						.entrySet().forEach(entry -> ENGLISH.put(entry.getKey(), entry.getValue().getAsString()));
				}
			}
		}
	}

	@Test
	void labelsFitTheirRowAndDescriptionsAreOneLine() {
		List<String> tooLong = new ArrayList<>();
		for (AddonFeature addon : SkyBlockFeatures.ALL) {
			Feature feature = Adapters.featureOf(addon);
			check(feature.id() + " page line", feature.description().getString(), 1f, 1f, PAGE_LINE_MAX, tooLong);
			for (Setting<?> setting : feature.settings()) {
				if (!hasLabel(setting)) {
					continue;
				}
				String where = feature.id() + "." + setting.key();
				float room = (wide(setting) ? LABEL_MAX_WIDE : LABEL_MAX) - (setting.tooltip() != null ? TIP_MARK : 0f);
				String label = ENGLISH.getOrDefault(setting.labelText(), setting.labelText());
				// a key on its feature's page goes without the feature's name in front
				if (setting instanceof KeySetting) {
					label = KeySetting.withoutOwner(label, feature.nameText());
				}
				check(where + " label", label, 1f, 1f, room, tooLong);
				if (setting.description() != null) {
					// the Minecraft font is a bitmap and is not drawn smaller than its whole pixels allow: at GUI scale 2 small text is full size
					check(where + " description", setting.description(), 1f, TextScale.SMALL, DESCRIPTION_MAX, tooLong);
				}
				// a dropdown's choices, in the button and in its open list
				if (setting instanceof EnumSetting<?> choice && choice.style() == EnumSetting.Style.DROPDOWN) {
					for (Object value : choice.choices()) {
						check(where + " choice", nameOf(choice, value), 1f, 1f, CHOICE_MAX, tooLong);
					}
				}
				// the tooltip, in its box
				if (setting.tooltip() != null && lines(setting.tooltip(), TOOLTIP_TEXT) > TOOLTIP_LINES) {
					tooLong.add(where + " tooltip (" + lines(setting.tooltip(), TOOLTIP_TEXT) + " lines of " + TOOLTIP_LINES + "): " + setting.tooltip());
				}
			}
		}
		assertTrue(tooLong.isEmpty(), "Too long (shorten it, or move the explanation into the tooltip):\n" + String.join("\n", tooLong));
	}

	@Test
	void measuresAsTheMenusDo() {
		// the Minecraft font: 6 pixels for most letters, less for the narrow ones
		assertTrue(minecraftWidth("Hello") == 6 + 6 + 3 + 3 + 6);
		assertTrue(interWidth("Hello") > 15f && interWidth("Hello") < 35f);
	}

	/** Rows whose name is not a label beside a control: notes and information blocks, whose name is their text, and page-wide panels. */
	private static boolean hasLabel(Setting<?> setting) {
		return !(setting instanceof NoteSetting || setting instanceof InfoSetting || setting instanceof ChipsInfoSetting || setting instanceof PreviewSetting
			|| setting instanceof HomeSetting);
	}

	/** @return whether its control is wide: the label beside it has less room */
	private static boolean wide(Setting<?> setting) {
		return setting instanceof EnumSetting<?> choice && choice.style() == EnumSetting.Style.DROPDOWN || setting instanceof MultiSelectSetting
			|| setting instanceof StringSetting || setting instanceof KeySetting;
	}

	private static void check(String where, String text, float gameScale, float ownScale, float most, List<String> tooLong) {
		if (text == null || text.isBlank()) {
			return;
		}
		float game = minecraftWidth(text) * gameScale;
		float own = interWidth(text) * ownScale;
		if (Math.max(game, own) > most) {
			tooLong.add(where + " (" + Math.round(game) + " / " + Math.round(own) + " of " + Math.round(most) + " px): " + text);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static String nameOf(EnumSetting choice, Object value) {
		return choice.nameOf((Enum) value).getString();
	}

	/** @return how many lines a text takes, wrapped by words at a width in the wider of the two fonts */
	private static int lines(String text, float width) {
		int lines = 1;
		String line = "";
		for (String word : text.split(" ")) {
			String longer = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && Math.max(minecraftWidth(longer), interWidth(longer)) > width) {
				lines++;
				line = word;
			} else {
				line = longer;
			}
		}
		return lines;
	}

	private static float interWidth(String text) {
		return (float) inter.getStringBounds(text, CONTEXT).getWidth();
	}

	/** @return the width of a text in the Minecraft font, in its pixels: each glyph's advance, which is its width and a pixel of space */
	static int minecraftWidth(String text) {
		int width = 0;
		for (int i = 0; i < text.length(); i++) {
			width += advance(text.charAt(i));
		}
		return width;
	}

	private static int advance(char c) {
		return switch (c) {
			case 'i', '!', '.', ',', ':', ';', '\'', '|' -> 2;
			case 'l', '`' -> 3;
			case 't', 'I', '[', ']', ' ' -> 4;
			case 'f', 'k', '(', ')', '{', '}', '<', '>', '"', '*' -> 5;
			case '@', '~' -> 7;
			default -> 6;
		};
	}
}
