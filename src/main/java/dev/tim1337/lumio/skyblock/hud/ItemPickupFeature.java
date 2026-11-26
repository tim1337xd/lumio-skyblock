package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.hud.HudEditor;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.TextInput;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.data.ItemRepo;
import dev.tim1337.lumio.skyblock.data.SackMessage;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;
import dev.tim1337.lumio.skyblock.data.SkyBlockPrices;
import dev.tim1337.lumio.skyblock.data.StorageTracker;
import dev.tim1337.lumio.skyblock.dungeon.TabularText;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

/**
 * The item pickup log: a short list of what just entered or left the inventory ("+3 Enchanted Mithril", "−1 Booster Cookie"), each line with the item's
 * icon, the count in green or red and the name in its rarity color; changes of the same item within a few seconds add up on one line, and a line fades
 * out after a while. Optionally what went into the sacks, the purse's coins and the value of each change. It only reads: the inventory is compared
 * only when it changes, by SkyBlock id and amount ({@link PickupLedger}).
 */
public class ItemPickupFeature extends AddonPanel {
	/** What it shows. */
	public enum Show {
		BOTH("Gains and losses"),
		GAINS("Gains only");

		private final String label;

		Show(String label) {
			this.label = label;
		}

		Component displayName() {
			return Component.literal(label);
		}
	}

	/** How much a line says. */
	public enum Style {
		COMPACT("Compact"),
		DETAILED("Detailed");

		private final String label;

		Style(String label) {
			this.label = label;
		}

		Component displayName() {
			return Component.literal(label);
		}
	}

	private static final int WIDTH = 150;
	private static final int ROW = 12;
	private static final int ICON = 10;
	private static final int PAD = 5;
	private static final int GAP = 4;
	private static final int SACK_ICON = 6;
	private static final long FADE_IN_MILLIS = 150L;
	private static final long MILLIS = 1000L;
	private static final int GAIN = PanelKit.GREEN_TEXT;
	private static final int LOSS = PanelKit.RED_TEXT;
	private static final int GOLD = 0xFFFFC94A;
	/** The SkyBlock Menu's item, which is in every inventory and never counts. */
	private static final String MENU_ITEM = "SKYBLOCK_MENU";
	private static final Pattern LIST_SPLIT = Pattern.compile("[,;\\n]");
	private static final String[][] SAMPLE = {{"ENCHANTED_MITHRIL", "3", ""}, {"BOOSTER_COOKIE", "-1", ""}, {"MITHRIL_ORE", "128", "sack"},
		{PickupLedger.COINS, "12340", ""}};

	private final Choice<Show> show = add(make().choice("show", Component.literal("Show"), Show.BOTH, Show::displayName, Choice.Style.SEGMENTED), "Shows");
	private final Choice<Style> style = add(make().choice("style", Component.literal("Style"), Style.COMPACT, Style::displayName, Choice.Style.SEGMENTED), "Look");
	private final Toggle sacks = add(make().toggle("sacks", Component.literal("Sacks"), true), "Shows", "From the sack summary in chat");
	private final Toggle coins = add(make().toggle("coins", Component.literal("Coins from the purse"), false), "Shows");
	private final Toggle value = add(make().toggle("value", Component.literal("Value"), false), "Shows");
	private final Toggle whileMenu = add(make().toggle("while_menu", Component.literal("Also while a menu is open"), false), "Shows",
		"Chest, storage and trade moves too");
	private final Slider maxLines = add(make().slider("max_lines", Component.literal("Max lines"), 6f, 1f, 12f, 0), "Look");
	private final Slider duration = add(make().slider("duration", Component.literal("Duration"), 5f, 1f, 30f, 0), "Look", "Seconds a line stays");
	private final Slider textSize = add(make().slider("text_size", Component.literal("Text size"), 1f, 0.75f, 1.5f, 2), "Look");
	private final TextInput hideList = add(make().text("hide_list", Component.literal("Hide list"), "", false), "Shows",
		"Names or parts, comma separated");

	private final PickupLedger ledger = new PickupLedger();
	private final Map<String, Long> counts = new HashMap<>();
	private final Map<String, Boolean> hiddenCache = new HashMap<>();
	private final Set<String> hiddenIds = new HashSet<>();
	private List<String> patterns = List.of();
	private String patternsFor = "";
	private Object lastLevel;
	private String lastProfile;
	private int lastChange = -1;
	/** The lines as drawn, made again only when the ledger or the settings change. */
	private final List<Row> rows = new ArrayList<>();
	private int rowsFor = -1;
	private long rowsSettings = -1;

	/** A line as drawn: its icon, texts and colors. */
	private record Row(PickupLedger.Line line, ItemStack icon, String count, String name, int nameColor, String extra, String worth) {
	}

	public ItemPickupFeature() {
		super("pickup_log", "Item pickup log", "What entered or left your inventory.",
			false, Anchor.RIGHT, false, 8f, 140f);
		pageGroup("On screen");
		iconRgb(SystemColors.GREEN_RGB);
		iconName("backpack");
		summary("What just entered or left your inventory");
		offerNoBackground();
	}

	/** Listens to the sack summaries in chat. Called during mod initialization. */
	public void register() {
		ItemGains.listenSacks(this::sackSummary);
	}

	private void sackSummary(List<SackMessage.Entry> entries) {
		if (!isEnabled() || !sacks.get() || !SkyBlock.allowed()) {
			return;
		}
		long now = System.currentTimeMillis();
		for (SackMessage.Entry entry : entries) {
			String name = SkyBlockPrices.clean(entry.name());
			String id = SkyBlockPrices.idForName(name);
			if (id == null) {
				id = name.toUpperCase(Locale.ROOT).replace(' ', '_');
			}
			if (!hidden(id)) {
				ledger.sack(id, entry.count(), now);
			}
		}
	}

	/** Called every tick: the inventory is compared only when it changed. */
	public void tick(Minecraft client) {
		if (!isEnabled() || client.level == null || client.player == null || !SkyBlock.allowed()) {
			lastLevel = null;
			return;
		}
		long now = System.currentTimeMillis();
		String profile = StorageTracker.profile();
		if (client.level != lastLevel || profile != null && !profile.equals(lastProfile)) {
			lastLevel = client.level;
			lastProfile = profile;
			lastChange = -1;
			ledger.worldChanged(now);
		}
		Inventory inventory = client.player.getInventory();
		boolean menuOpen = client.gui.screen() != null && !(client.gui.screen() instanceof ChatScreen);
		if (inventory.getTimesChanged() != lastChange) {
			lastChange = inventory.getTimesChanged();
			count(inventory);
			ledger.inventory(counts, now, menuOpen, whileMenu.get(), hiddenIds);
		}
		if (coins.get()) {
			ledger.purse(SkyBlockArea.purse(), now);
		}
		ledger.expire(now, durationMillis());
	}

	/** Counts the inventory by SkyBlock id: the main slots, the hotbar, armor and the off hand; the SkyBlock Menu never. */
	private void count(Inventory inventory) {
		counts.clear();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			String id = stack.isEmpty() ? null : ItemGains.idOf(stack);
			if (id == null || MENU_ITEM.equals(id)) {
				continue;
			}
			counts.merge(id, (long) stack.getCount(), Long::sum);
			if (hidden(id)) {
				hiddenIds.add(id);
			}
		}
	}

	/** @return whether an item is on the hide list; worked out once per item and list */
	private boolean hidden(String id) {
		String list = hideList.get();
		if (!list.equals(patternsFor)) {
			patternsFor = list;
			List<String> parsed = new ArrayList<>();
			for (String part : LIST_SPLIT.split(list)) {
				if (!part.isBlank()) {
					parsed.add(part.strip());
				}
			}
			patterns = parsed;
			hiddenCache.clear();
			hiddenIds.clear();
		}
		if (patterns.isEmpty()) {
			return false;
		}
		return hiddenCache.computeIfAbsent(id, key -> PickupLedger.hiddenBy(patterns, key, nameOf(key)));
	}

	private long durationMillis() {
		return Math.round(duration.get()) * MILLIS;
	}

	private static String nameOf(String id) {
		if (PickupLedger.COINS.equals(id)) {
			return "coins";
		}
		ItemRepo.Entry entry = ItemRepo.get(id);
		return entry != null ? entry.plainName() : SkyBlockPrices.nameOf(id);
	}

	// ------------------------------------------------------------------------------------------------ the panel

	/** @return the rows to draw: the ledger's lines (gains only if wanted), the newest at the bottom, at most as many as set; made again on change */
	private List<Row> rows(boolean sample) {
		long settings = (show.get().ordinal() * 31L + style.get().ordinal()) * 31L + Math.round(maxLines.get()) * 7L + (value.get() ? 1 : 0)
			+ (coins.get() ? 2 : 0) + (sample ? 4 : 0);
		int version = sample ? -2 : ledger.version();
		if (version == rowsFor && settings == rowsSettings) {
			return rows;
		}
		rowsFor = version;
		rowsSettings = settings;
		rows.clear();
		List<PickupLedger.Line> lines = sample ? sampleLines() : ledger.lines();
		int most = Math.round(maxLines.get());
		for (int i = lines.size() - 1; i >= 0 && rows.size() < most; i--) {
			PickupLedger.Line line = lines.get(i);
			if (show.get() == Show.GAINS && line.count < 0 || line.coins() && !coins.get() && !sample) {
				continue;
			}
			rows.addFirst(row(line));
		}
		return rows;
	}

	private Row row(PickupLedger.Line line) {
		String count = (line.count > 0 ? "+" : "−") + PanelKit.grouped(Math.abs(line.count));
		if (line.coins()) {
			return new Row(line, new ItemStack(Items.GOLD_NUGGET), count, "coins", GOLD, "", "");
		}
		ItemRepo.Entry entry = ItemRepo.get(line.id);
		ItemStack icon = entry != null ? entry.stack() : ItemGains.iconOf(line.id);
		int color = entry != null ? HudTheme.readable(entry.rarityColor()) : HudTheme.text();
		String extra = "";
		if (style.get() == Style.DETAILED && !line.sack) {
			long total = counts.getOrDefault(line.id, 0L);
			extra = "×" + PanelKit.grouped(total) + " now";
		}
		String worth = "";
		if (value.get()) {
			double price = SkyBlockPrices.listedPrice(line.id);
			worth = price > 0 ? (line.count > 0 ? "" : "−") + ItemDetailView.coins(price * Math.abs(line.count)) : "";
		}
		return new Row(line, icon == null ? ItemStack.EMPTY : icon, count, nameOf(line.id), color, extra, worth);
	}

	/** The example lines of the HUD editor, with fresh times so they show fully. */
	private List<PickupLedger.Line> sampleLines() {
		List<PickupLedger.Line> lines = new ArrayList<>();
		long now = System.currentTimeMillis();
		for (String[] sample : SAMPLE) {
			PickupLedger.Line line = new PickupLedger.Line(sample[0], !sample[2].isEmpty(), Long.parseLong(sample[1]), now - FADE_IN_MILLIS);
			lines.add(line);
		}
		return lines;
	}

	private boolean sample() {
		return ledger.lines().isEmpty() && (simulated() || HudEditor.isOpen());
	}

	@Override
	public boolean shown(Minecraft client) {
		return SkyBlock.allowed() && !rows(false).isEmpty() || simulated();
	}

	private float scale() {
		return textSize.get();
	}

	@Override
	public int panelWidth() {
		return Math.round(WIDTH * scale());
	}

	@Override
	public int panelHeight() {
		int count = Math.max(1, rows(sample()).size());
		return Math.round((count * ROW + 2 * PAD - 2) * scale());
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		boolean sample = sample();
		List<Row> shownRows = rows(sample);
		float s = scale();
		int width = panelWidth();
		drawGlass(ui, width, panelHeight(), PanelKit.RADIUS);
		long now = System.currentTimeMillis();
		long show = durationMillis();
		float textScale = PanelKit.SMALL * s;
		float y = PAD * s;
		float alphaBefore = Draw.alpha();
		for (Row row : shownRows) {
			float alpha = sample ? 1f : PickupLedger.shown(row.line(), now, show, FADE_IN_MILLIS);
			Draw.setAlpha(alphaBefore * alpha);
			float x = PAD * s;
			float iconSize = ICON * s;
			if (!row.icon().isEmpty()) {
				var pose = graphics.pose();
				pose.pushMatrix();
				pose.translate(x, y + (ROW * s - iconSize) / 2f - 1f);
				pose.scale(iconSize / 16f, iconSize / 16f);
				graphics.item(row.icon(), 0, 0);
				pose.popMatrix();
			}
			x += iconSize + GAP * s;
			float textY = y + (ROW * s - 7f * textScale) / 2f;
			int countColor = row.line().count > 0 ? GAIN : LOSS;
			TabularText.draw(ui, row.count(), x, textY, textScale, true, countColor);
			x += TabularText.width(ui, row.count(), textScale, true) + GAP * s;
			float right = width - PAD * s;
			if (!row.worth().isEmpty()) {
				TabularText.drawRight(ui, row.worth(), right, textY, textScale, false, GOLD);
				right -= TabularText.width(ui, row.worth(), textScale, false) + GAP * s;
			}
			if (row.line().sack) {
				right -= SACK_ICON * s;
				ui.icon("backpack", Math.round(right), Math.round(y + (ROW * s - SACK_ICON * s) / 2f), Math.round(SACK_ICON * s),
					HudTheme.secondary());
				right -= GAP * s;
			}
			String extra = row.extra();
			float extraWidth = extra.isEmpty() ? 0f : ui.widthScaled(extra, textScale);
			float nameRoom = right - x - (extra.isEmpty() ? 0f : extraWidth + GAP * s);
			ui.textAtScaledBold(ui.fitScaled(row.name(), Math.max(0f, nameRoom), textScale), x, textY, textScale, row.nameColor());
			if (!extra.isEmpty()) {
				ui.textAtScaled(extra, right - extraWidth, textY, textScale, HudTheme.secondary());
			}
			y += ROW * s;
		}
		Draw.setAlpha(alphaBefore);
	}
}
