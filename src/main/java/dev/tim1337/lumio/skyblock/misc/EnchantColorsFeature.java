package dev.tim1337.lumio.skyblock.misc;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.AddonSetting;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.ColorChoice;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;
import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.skyblock.data.EnchantTiers;
import dev.tim1337.lumio.skyblock.data.ItemRepo;
import dev.tim1337.lumio.skyblock.data.SkyBlock;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Colors the SkyBlock enchants in item tooltips by how good they are: only their colors change (and Ultimate ones are bold), their text and order stay. The idea is from SkyHanni; the levels
 * come from the item repository. Which part of a line is an enchant is worked out once per line; a tier in Chroma or Custom gets its letters' colors for every frame its tooltip is shown, and
 * only then. Other mods' enchant coloring is left alone.
 */
public class EnchantColorsFeature extends AddonFeature {
	/** How a tier's letters are colored. */
	public enum Look {
		COLOR("Color"),
		CHROMA("Chroma"),
		CUSTOM("Custom");

		private final Component displayName;

		Look(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}


	private static final float SLOWEST = 0.1f;
	private static final float FASTEST = 3f;
	private static final float CALM = 0.5f;
	/** The most lines whose parts are remembered; a line is rarely longer than the screen, and the text of the tooltips seen repeats. */
	private static final int MOST_LINES = 1024;
	private static final float NANOS_PER_SECOND = 1_000_000_000f;
	private static final long TIME_WRAP_NANOS = 3_600_000_000_000L;
	private static final char FORMAT_MARK = '§';
	// the preview
	private static final int PREVIEW_HEIGHT = 92;
	private static final int PREVIEW_PAD = 8;
	private static final int LINE_HEIGHT = 11;
	private static final int ICON = 18;
	private static final int TEXT_COLOR = 0xFFFFFFFF;
	private static final int NAME_RARITY = 0xFFFFAA00;
	private static final int BOOK_RARITY = 0xFFFFFF55;
	private static final EnchantTiers SAMPLE = sampleTiers();
	private static final String[] SAMPLE_SWORD = {"Sharpness VII", "Smite VI", "Cubism V", "Ender Slayer III", "Ultimate Wise V"};
	private static final String SAMPLE_BOOK = "Sharpness VII";

	private final Toggle hideGameLines = add(make().toggle("hide_game_lines", Component.literal("Hide the game's enchant line"), true), "Colors", "The one under the name, when the lore lists that enchant too");
	private final AddonSetting<Void> preview = add(HudTheme.preview("preview", Component.literal("Preview"), PREVIEW_HEIGHT, this::drawPreview), "Colors");
	private final ColorChoice ultimate = add(make().color("ultimate", Component.literal("Ultimate"), EnchantDefaults.ULTIMATE).inRow("Colors"), "Colors");
	private final ColorChoice perfect = add(make().color("perfect", Component.literal("Perfect"), EnchantDefaults.PERFECT).inRow("Colors"), "Colors");
	private final ColorChoice great = add(make().color("great", Component.literal("Great"), EnchantDefaults.GREAT).inRow("Colors"), "Colors");
	private final ColorChoice good = add(make().color("good", Component.literal("Good"), EnchantDefaults.GOOD).inRow("Colors"), "Colors");
	private final ColorChoice poor = add(make().color("poor", Component.literal("Poor"), EnchantDefaults.POOR).inRow("Colors"), "Colors");
	private final TierLook perfectStyle = new TierLook("perfect", "Perfect style", Look.CHROMA);
	private final TierLook ultimateStyle = new TierLook("ultimate", "Ultimate style", Look.COLOR);
	private final Map<String, Line> lines = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Line> eldest) {
			return size() > MOST_LINES;
		}
	};

	/** A tier's look: its style, and its own colors with their speed. */
	private final class TierLook {
		final Choice<Look> look;
		final ColorChoice first;
		final ColorChoice second;
		final ColorChoice third;
		final Slider count;
		final Slider speed;
		/** The own colors, each as light as it takes to read on a tooltip. */
		final int[] colors = new int[3];
		int readableFor = -1;

		TierLook(String key, String group, Look fallback) {
			look = add(make().choice(key + "_look", Component.literal("Style"), fallback, Look::displayName), group);
			count = add(make().slider(key + "_count", Component.literal("Colors"), 2f, 2f, 3f, 0), group);
			first = add(make().color(key + "_first", Component.literal("First"), EnchantDefaults.FLOW_A).inRow("Own colors"), group);
			second = add(make().color(key + "_second", Component.literal("Second"), EnchantDefaults.FLOW_B).inRow("Own colors"), group);
			third = add(make().color(key + "_third", Component.literal("Third"), EnchantDefaults.FLOW_C).inRow("Own colors"), group);
			speed = add(make().slider(key + "_speed", Component.literal("Speed"), CALM, SLOWEST, FASTEST, 2), group);
			count.under(look, () -> look.get() == Look.CUSTOM);
			first.under(look, () -> look.get() == Look.CUSTOM);
			second.under(look, () -> look.get() == Look.CUSTOM);
			third.under(look, () -> look.get() == Look.CUSTOM && count.get() >= 3f);
			speed.under(look, () -> look.get() == Look.CUSTOM);
		}

		int letter(float seconds, int index) {
			if (look.get() == Look.CHROMA) {
				return EnchantPaint.chroma(seconds, index);
			}
			// asked for every letter of every frame, and a dark color takes a while to lift: only when the colors change
			int now = version();
			if (now != readableFor) {
				readableFor = now;
				colors[0] = EnchantPaint.readable(first.get() & 0xFFFFFF);
				colors[1] = EnchantPaint.readable(second.get() & 0xFFFFFF);
				colors[2] = EnchantPaint.readable(third.get() & 0xFFFFFF);
			}
			return EnchantPaint.flow(colors, Math.round(count.get()), speed.get(), seconds, index);
		}

		int version() {
			return look.get().ordinal() * 31 + first.get() * 7 + second.get() * 5 + third.get() * 3 + Math.round(count.get());
		}
	}

	/** What is known about a line: its enchants, and the finished line while nothing in it moves. */
	private static final class Line {
		final List<EnchantLore.Span> spans;
		Component painted;
		int paintedFor;

		Line(List<EnchantLore.Span> spans) {
			this.spans = spans;
		}
	}

	private record Run(String text, Style style) {
	}

	public EnchantColorsFeature() {
		super("enchant_colors", "Enchant colors", "Colors the enchants in item tooltips by how good their level is.", true);
		iconRgb(SystemColors.PURPLE_RGB);
		iconName("sparkle");
		summary("Enchants colored by how good they are");
	}

	public void register() {
		ItemTooltipCallback.EVENT.register(this::onTooltip);
	}

	/** A config from before the brighter defaults gets them, once; colors the player picked stay. */
	@Override
	public void load(JsonObject json) {
		EnchantDefaults.bringUpToDate(json);
		super.load(json);
	}

	@Override
	public JsonObject save() {
		JsonObject json = super.save();
		json.addProperty(EnchantDefaults.APPLIED, true);
		return json;
	}

	private void onTooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> tooltip) {
		if (!isEnabled() || tooltip.size() < 2 && !stack.is(Items.ENCHANTED_BOOK) || !SkyBlock.allowed()) {
			return;
		}
		if (hideGameLines.get()) {
			takeOutGameLines(stack, context, tooltip);
		}
		EnchantTiers tiers = ItemRepo.enchantTiers();
		if (tiers == EnchantTiers.EMPTY) {
			ItemRepo.loadIfNeeded();
			return;
		}
		float seconds = seconds();
		int version = version();
		for (int i = 1; i < tooltip.size(); i++) {
			Component painted = paintLine(tooltip.get(i), tiers, seconds, version, false);
			if (painted != null) {
				tooltip.set(i, painted);
			}
		}
		// a book's name only moves: its name is not to be recolored otherwise
		if (stack.is(Items.ENCHANTED_BOOK) && perfectStyle.look.get() != Look.COLOR && !tooltip.isEmpty()) {
			Component painted = paintLine(tooltip.get(0), tiers, seconds, version, true);
			if (painted != null) {
				tooltip.set(0, painted);
			}
		}
	}

	/** Takes out the game's own line of a vanilla enchantment under the name, when the lore lists it as well: the server leaves those shown, and they are no part of its text. */
	private void takeOutGameLines(ItemStack stack, Item.TooltipContext context, List<Component> tooltip) {
		ItemEnchantments enchants = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
		if (enchants.isEmpty() && stored.isEmpty()) {
			return;
		}
		List<String> game = new ArrayList<>();
		addNames(enchants, game);
		addNames(stored, game);
		List<String> plain = new ArrayList<>(tooltip.size());
		for (Component line : tooltip) {
			plain.add(TextColors.noColors(line.getString()));
		}
		for (int place : GameEnchantLines.repeated(plain, game)) {
			tooltip.remove(place);
		}
	}

	private static void addNames(ItemEnchantments enchants, List<String> names) {
		for (var entry : enchants.entrySet()) {
			names.add(Enchantment.getFullname(entry.getKey(), entry.getIntValue()).getString());
		}
	}

	private static float seconds() {
		return (System.nanoTime() % TIME_WRAP_NANOS) / NANOS_PER_SECOND;
	}

	private int version() {
		return ultimate.get() * 3 + perfect.get() * 5 + great.get() * 7 + good.get() * 11 + poor.get() * 13 + perfectStyle.version() * 17 + ultimateStyle.version() * 19;
	}

	/** @return the line with its enchants colored, or null when it holds none */
	private Component paintLine(Component line, EnchantTiers tiers, float seconds, int version, boolean onlyPerfect) {
		String raw = line.getString();
		Line known = lines.get(raw);
		if (known == null) {
			known = new Line(EnchantLore.parse(TextColors.noColors(raw), tiers));
			lines.put(raw, known);
		}
		if (known.spans.isEmpty() || onlyPerfect && !(known.spans.size() == 1 && known.spans.get(0).tier() == EnchantTiers.Tier.PERFECT)) {
			return null;
		}
		if (known.painted != null && known.paintedFor == version && !onlyPerfect) {
			return known.painted;
		}
		Component painted = paint(line, known.spans, seconds);
		if (!moves(known.spans) && !onlyPerfect) {
			known.painted = painted;
			known.paintedFor = version;
		} else {
			known.painted = null;
		}
		return painted;
	}

	private boolean moves(List<EnchantLore.Span> spans) {
		for (EnchantLore.Span span : spans) {
			if (styleOf(span.tier()) != null && styleOf(span.tier()).look.get() != Look.COLOR) {
				return true;
			}
		}
		return false;
	}

	private TierLook styleOf(EnchantTiers.Tier tier) {
		return tier == EnchantTiers.Tier.PERFECT ? perfectStyle : tier == EnchantTiers.Tier.ULTIMATE ? ultimateStyle : null;
	}

	private int colorOf(EnchantTiers.Tier tier) {
		return switch (tier) {
			case ULTIMATE -> ultimate.get();
			case PERFECT -> perfect.get();
			case GREAT -> great.get();
			case GOOD -> good.get();
			case POOR -> poor.get();
		} & 0xFFFFFF;
	}

	/** Builds the line again, part by part: the text stays, only an enchant's color changes. */
	private Component paint(Component line, List<EnchantLore.Span> spans, float seconds) {
		List<Run> runs = runs(line);
		MutableComponent out = Component.empty();
		int at = 0;
		int spanIndex = 0;
		for (Run run : runs) {
			int runAt = 0;
			String text = run.text();
			while (runAt < text.length()) {
				while (spanIndex < spans.size() && spans.get(spanIndex).end() <= at) {
					spanIndex++;
				}
				EnchantLore.Span span = spanIndex < spans.size() ? spans.get(spanIndex) : null;
				if (span == null || at < span.start()) {
					int until = span == null ? text.length() : Math.min(text.length(), runAt + span.start() - at);
					out.append(Component.literal(text.substring(runAt, until)).setStyle(run.style()));
					at += until - runAt;
					runAt = until;
				} else {
					int until = Math.min(text.length(), runAt + span.end() - at);
					appendEnchant(out, text.substring(runAt, until), run.style(), span, at - span.start(), seconds);
					at += until - runAt;
					runAt = until;
				}
			}
		}
		return out;
	}

	private void appendEnchant(MutableComponent out, String text, Style style, EnchantLore.Span span, int letterFrom, float seconds) {
		Style bold = span.tier() == EnchantTiers.Tier.ULTIMATE ? style.withBold(true) : style;
		TierLook own = styleOf(span.tier());
		if (own == null || own.look.get() == Look.COLOR) {
			out.append(Component.literal(text).setStyle(bold.withColor(colorOf(span.tier()))));
			return;
		}
		for (int i = 0; i < text.length(); i++) {
			out.append(Component.literal(text.substring(i, i + 1)).setStyle(bold.withColor(own.letter(seconds, letterFrom + i))));
		}
	}

	/** The line's parts, with formatting codes in its text taken out and turned into styles: the server's lines can hold them. */
	private static List<Run> runs(Component line) {
		List<Run> runs = new ArrayList<>();
		line.visit((style, text) -> {
			addRuns(runs, style, text);
			return Optional.empty();
		}, Style.EMPTY);
		return runs;
	}

	private static void addRuns(List<Run> runs, Style style, String text) {
		Style now = style;
		int start = 0;
		for (int i = 0; i + 1 < text.length(); i++) {
			if (text.charAt(i) == FORMAT_MARK) {
				if (i > start) {
					runs.add(new Run(text.substring(start, i), now));
				}
				ChatFormatting format = ChatFormatting.getByCode(text.charAt(i + 1));
				if (format != null) {
					now = now.applyLegacyFormat(format);
				}
				i++;
				start = i + 1;
			}
		}
		if (start < text.length()) {
			runs.add(new Run(text.substring(start), now));
		}
	}

	// ------------------------------------------------------------------------------------------------ preview

	private static EnchantTiers sampleTiers() {
		JsonObject sample = JsonParser.parseString("""
			{"enchants_xp_cost":{"sharpness":[1,2,3,4,5,6,7],"smite":[1,2,3,4,5,6,7],"cubism":[1,2,3,4,5,6],"ender_slayer":[1,2,3,4,5,6,7]},
			"max_xp_table_levels":{"sharpness":5,"smite":5,"cubism":5,"ender_slayer":5},"enchants":{"SWORD":["ultimate_wise"]}}""").getAsJsonObject();
		return EnchantTiers.of(sample);
	}

	/** A sword and a T7 book, with the colors as they are set. */
	private void drawPreview(GuiGraphicsExtractor graphics, Canvas ui, int x, int y, int width, int height) {
		var font = Minecraft.getInstance().font;
		float seconds = seconds();
		int column = (width - 3 * PREVIEW_PAD) / 2;
		int left = x + PREVIEW_PAD;
		int top = y + PREVIEW_PAD;
		graphics.item(new ItemStack(Items.DIAMOND_SWORD), left, top);
		graphics.text(font, Component.literal("Aspect of the Dragons"), left + ICON + 2, top + 4, NAME_RARITY, false);
		int lineY = top + ICON + 2;
		for (String text : SAMPLE_SWORD) {
			graphics.text(font, paint(Component.literal(text), EnchantLore.parse(text, SAMPLE), seconds), left, lineY, TEXT_COLOR, false);
			lineY += LINE_HEIGHT;
		}
		int right = left + column + PREVIEW_PAD;
		graphics.item(new ItemStack(Items.ENCHANTED_BOOK), right, top);
		List<EnchantLore.Span> book = EnchantLore.parse(SAMPLE_BOOK, SAMPLE);
		Component name = perfectStyle.look.get() == Look.COLOR ? Component.literal(SAMPLE_BOOK) : paint(Component.literal(SAMPLE_BOOK), book, seconds);
		graphics.text(font, name, right + ICON + 2, top + 4, BOOK_RARITY, false);
		graphics.text(font, paint(Component.literal(SAMPLE_BOOK), book, seconds), right, top + ICON + 2, TEXT_COLOR, false);
	}
}
