package dev.tim1337.lumio.skyblock.fishing;

import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.hud.HudEditor;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.setting.Action;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.api.experimental.ui.UiSounds;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.skyblock.SkyBlockSimulation;
import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;
import dev.tim1337.lumio.skyblock.fishing.SeaCreatures.Creature;
import dev.tim1337.lumio.skyblock.data.Activity;
import dev.tim1337.lumio.skyblock.data.FishingRods;
import dev.tim1337.lumio.skyblock.data.ProfitActivity;
import dev.tim1337.lumio.skyblock.data.ProfitTracker;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;
import dev.tim1337.lumio.skyblock.hud.InventoryHover;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.util.StringDecomposer;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hypixel SkyBlock sea creature tracker, counting like SkyHanni's: every creature has its own chat message when it surfaces, and a double hook is
 * announced in the message before it. Counts are kept for the session, today and all time, each with its share of all catches, the number of
 * double hooks, and the catches per hour of fishing.
 */
public class SeaCreatureHudFeature extends AddonPanel {
	public enum Sort {
		COUNT("Most caught"),
		RARITY("Rarest first");

		private final String title;

		Sort(String title) {
			this.title = title;
		}

		public Component displayName() {
			return Component.literal(title);
		}
	}

	/** Every sea creature, from the list shared with the fishing alerts. */
	private static final Creature[] CREATURES = SeaCreatures.all().toArray(new Creature[0]);

	/** SkyBlock rarity colors, from common to mythic. */
	private static final int[] RARITY_COLORS = {0xFFFFFFFF, 0xFF55FF55, 0xFF5555FF, 0xFFAA00AA, 0xFFFFAA00, 0xFFFF55FF};
	private static final Map<String, Creature> BY_NAME = new HashMap<>();

	static {
		for (Creature creature : CREATURES) {
			BY_NAME.put(creature.name(), creature);
		}
	}

	/** Catches within one time range. */
	private static final class Bucket {
		final Map<String, Long> counts = new LinkedHashMap<>();
		/** Per rare creature caught: the catches since it was last caught. */
		final Map<String, Long> since = new HashMap<>();
		/** Per creature caught: when it was last caught (epoch milliseconds). */
		final Map<String, Long> lastAt = new HashMap<>();
		long catches;
		long doubleHooks;
		long activeMillis;
		String day = "";

		void clear() {
			counts.clear();
			since.clear();
			lastAt.clear();
			catches = 0;
			doubleHooks = 0;
			activeMillis = 0;
		}

		JsonObject toJson() {
			JsonObject json = new JsonObject();
			json.addProperty("catches", catches);
			json.addProperty("double_hooks", doubleHooks);
			json.addProperty("active_ms", activeMillis);
			json.addProperty("day", day);
			JsonObject list = new JsonObject();
			counts.forEach(list::addProperty);
			json.add("creatures", list);
			JsonObject sinceList = new JsonObject();
			since.forEach(sinceList::addProperty);
			json.add("since", sinceList);
			JsonObject lastList = new JsonObject();
			lastAt.forEach(lastList::addProperty);
			json.add("last_at", lastList);
			return json;
		}

		void fromJson(JsonObject json) {
			clear();
			try {
				catches = json.has("catches") ? json.get("catches").getAsLong() : 0;
				doubleHooks = json.has("double_hooks") ? json.get("double_hooks").getAsLong() : 0;
				activeMillis = json.has("active_ms") ? json.get("active_ms").getAsLong() : 0;
				day = json.has("day") ? json.get("day").getAsString() : "";
				if (json.has("creatures") && json.get("creatures").isJsonObject()) {
					for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("creatures").entrySet()) {
						// a creature that was renamed is counted on under its name of today, with what was counted under the old one
						counts.merge(SeaCreatures.nameNow(entry.getKey()), entry.getValue().getAsLong(), Long::sum);
					}
				}
				if (json.has("since") && json.get("since").isJsonObject()) {
					for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("since").entrySet()) {
						if (BY_NAME.containsKey(entry.getKey())) {
							since.merge(SeaCreatures.nameNow(entry.getKey()), Math.max(0L, entry.getValue().getAsLong()), Math::min);
						}
					}
				}
				if (json.has("last_at") && json.get("last_at").isJsonObject()) {
					for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("last_at").entrySet()) {
						if (BY_NAME.containsKey(SeaCreatures.nameNow(entry.getKey()))) {
							lastAt.merge(SeaCreatures.nameNow(entry.getKey()), Math.max(0L, entry.getValue().getAsLong()), Math::max);
						}
					}
				}
			} catch (RuntimeException e) {
				clear();
			}
		}
	}

	private final Bucket session = new Bucket();
	private final Bucket today = new Bucket();
	private final Bucket lifetime = new Bucket();

	private final Choice<ProfitTracker.Range> range = add(make().choice("range", Component.literal("Time range"), ProfitTracker.Range.SESSION, ProfitTracker.Range::displayName, Choice.Style.SEGMENTED), "General");
	private final Toggle percentages = add(make().toggle("percentages", Component.literal("Percentages"), true), "General");
	private final Toggle doubleAsTwo = add(make().toggle("double_as_two", Component.literal("Count double hooks twice"), true), "General");
	private final Toggle showDoubleHooks = add(make().toggle("show_double_hooks", Component.literal("Double hook rate"), true), "General");
	private final Toggle showPerHour = add(make().toggle("show_per_hour", Component.literal("Per hour"), true), "General");
	private final Toggle rarityColors = add(make().toggle("rarity_colors", Component.literal("Rarity colors"), true), "General");
	private final Toggle showRare = add(make().toggle("show_rare", Component.literal("Rare creatures on hover"), true), "General");
	private final Toggle showList = add(make().toggle("show_list", Component.literal("Creature list"), true), "General");
	private final Choice<Sort> sort = add(make().choice("sort", Component.literal("Sort by"), Sort.COUNT, Sort::displayName, Choice.Style.SEGMENTED), "General");
	private final Slider rowsShown = add(make().slider("rows", Component.literal("Rows"), 8f, 1f, 20f, 0), "General");
	private final Action resetSession = add(make().action("reset_session", Component.literal("The count of this session"), Component.literal("Start over"), true, () -> reset(session)), "Reset");
	private final Action resetToday = add(make().action("reset_today", Component.literal("The count of today"), Component.literal("Start over"), true, () -> reset(today)), "Reset");
	private final Action resetAll = add(make().action("reset_lifetime", Component.literal("The count of all time"), Component.literal("Start over"), true, () -> reset(lifetime)), "Reset");

	private final Spring shownAmount = Spring.bouncy(Spring.GENTLE);
	private final Spring shownHeight = Spring.bouncy(Spring.GENTLE);
	private final Spring total = Spring.smooth(Spring.GENTLE);
	/** When the double hook message arrived: the next creature counts twice. */
	private long doubleHookAt;
	private long lastTick;
	private int changes;
	private int savedChanges;
	private List<Map.Entry<String, Long>> listed = List.of();

	public SeaCreatureHudFeature() {
		super("sea_creatures", "Sea creatures", "Sea creatures you fish up, with rates.",
			true, Anchor.LEFT, false, 12f, 220f);
		offerCardSize();
		offerNoBackground();
		iconRgb(SystemColors.BLUE_RGB);
		iconName("fish");
		summary("Sea creatures you catch, per hour");
		sort.visibleWhen(showList::get);
		rowsShown.visibleWhen(showList::get);
		percentages.visibleWhen(showList::get);
		// the tooltip comes with "Rare creatures on hover", or over a line of the list
		rarityColors.visibleWhen(() -> showList.get() || showRare.get());
		showDoubleHooks.visibleWhen(() -> showList.get() || showRare.get());
		offerHeader();
	}

	private void reset(Bucket bucket) {
		bucket.clear();
		if (bucket == today) {
			today.day = LocalDate.now().toString();
		}
		changes++;
		LumioApi.saveSoon();
	}

	/** Sample catches shown while the HUD editor simulates fishing and nothing was caught yet, for water or for lava; built when needed. */
	private Bucket sample;
	private Activity sampleFor;

	/** @return the catches shown: the real ones, or the sample while fishing is simulated in the editor with nothing caught */
	private Bucket shownBucket() {
		Bucket real = bucket(range.get());
		if (!simulated() || real.catches > 0) {
			return real;
		}
		Activity activity = SkyBlockSimulation.activity();
		if (sample == null || sampleFor != activity) {
			sample = new Bucket();
			sampleFor = activity;
			String[] names = activity == Activity.LAVA_FISHING ? new String[]{"Magma Slug", "Moogma", "Lava Leech", "Pyroclastic Worm", "Fire Eel", "Taurus"}
				: new String[]{"Squid", "Sea Walker", "Night Squid", "Sea Guardian", "Sea Archer", "Catfish"};
			long[] counts = {64, 41, 27, 12, 6, 2};
			for (int i = 0; i < names.length; i++) {
				sample.counts.put(names[i], counts[i]);
				sample.catches += counts[i];
			}
			sample.doubleHooks = 9;
			sample.since.put(names[5], 41L);
			sample.since.put(names[4], 7L);
			// when each was last caught, so the tooltip shows its "ago" too
			long now = System.currentTimeMillis();
			for (int i = 0; i < names.length; i++) {
				sample.lastAt.put(names[i], now - (i + 1) * 95_000L);
			}
			sample.activeMillis = 48 * 60_000L;
		}
		return sample;
	}

	private Bucket bucket(ProfitTracker.Range which) {
		return switch (which) {
			case SESSION -> session;
			case TODAY -> today;
			case LIFETIME -> lifetime;
		};
	}

	private void rollDay() {
		String key = LocalDate.now().toString();
		if (!today.day.equals(key)) {
			today.clear();
			today.day = key;
			changes++;
		}
	}

	/** Handles a chat message: a double hook, or a sea creature surfacing. */
	public void onMessage(Component message) {
		if (!isEnabled() || !SkyBlock.allowed()) {
			return;
		}
		// strip color codes, which Hypixel may put in the middle of a message
		String text = TextColors.noColors(message.getString()).strip();
		if (text.contains(SeaCreatures.DOUBLE_HOOK)) {
			doubleHookAt = System.currentTimeMillis();
			return;
		}
		for (Creature creature : CREATURES) {
			if (text.contains(creature.message())) {
				caught(creature);
				return;
			}
		}
		// a creature the list does not know yet (a new island's): counted by the name its line gives, not dropped
		String unknown = UnknownCatch.of(text, catchGreen(message), hookOutAt == 0 ? -1 : System.currentTimeMillis() - hookOutAt);
		if (unknown != null) {
			if (DebugHooks.ACTIVE) {
				DebugHooks.log(SkyBlockDebug.FISHING, "A sea creature the list does not know, counted as \"{}\": {}", unknown, text);
			}
			caught(new Creature(unknown, 0, text, SeaCreatures.Place.WATER, false));
		}
	}

	/** When the player's own bobber was last out, for telling a catch line of a creature the list does not know. */
	private long hookOutAt;

	/** @return whether every letter of a line is in the plain green the server writes its catch lines in */
	private static boolean catchGreen(Component message) {
		boolean[] green = {true};
		boolean[] any = {false};
		StringDecomposer.iterateFormatted(message, Style.EMPTY, (position, style, codePoint) -> {
			if (!Character.isWhitespace(codePoint)) {
				any[0] = true;
				green[0] &= style.getColor() != null && style.getColor().getValue() == CATCH_GREEN && !style.isBold();
			}
			return green[0];
		});
		return green[0] && any[0];
	}

	private static final int CATCH_GREEN = 0x55FF55;

	private void caught(Creature creature) {
		boolean doubled = System.currentTimeMillis() - doubleHookAt < 3000L;
		doubleHookAt = 0;
		int amount = doubled && doubleAsTwo.get() ? 2 : 1;
		rollDay();
		for (Bucket bucket : new Bucket[]{session, today, lifetime}) {
			// the catches since each rare creature was last caught: all go up, the one caught starts over
			bucket.since.replaceAll((name, count) -> count + amount);
			if (creature.rarity() >= RARE_FROM) {
				bucket.since.put(creature.name(), 0L);
			}
			bucket.counts.merge(creature.name(), (long) amount, Long::sum);
			bucket.lastAt.put(creature.name(), System.currentTimeMillis());
			bucket.catches += amount;
			if (doubled) {
				bucket.doubleHooks++;
			}
		}
		changes++;
	}

	public void tick(Minecraft client) {
		long now = System.currentTimeMillis();
		long dt = lastTick == 0 ? 0 : Math.min(1000, now - lastTick);
		lastTick = now;
		if (!isEnabled() || client.player == null) {
			return;
		}
		if (client.player.fishing != null && FishingRods.holding(client.player.getMainHandItem())) {
			hookOutAt = now;
		}
		// fishing is told from the cast itself, on whatever island: a list of islands left every new one out
		if (ProfitActivity.FISHING.now()) {
			rollDay();
			session.activeMillis += dt;
			today.activeMillis += dt;
			lifetime.activeMillis += dt;
		}
		if (changes != savedChanges) {
			savedChanges = changes;
			LumioApi.saveSoon();
		}
	}

	// ------------------------------------------------------------------------------------------------ display

	private boolean wanted() {
		// while changing worlds (entering a mineshaft, warping) the location is briefly unknown: keep the previous visibility
		if (ProfitActivity.changingWorld()) {
			return wasWanted;
		}
		return wasWanted = wantedNow();
	}

	/** The last visibility, kept while changing worlds. */
	private boolean wasWanted;

	private boolean wantedNow() {
		if (!SkyBlock.allowed()) {
			return false;
		}
		// auto-detected, as the fishing tracker: while fishing, on whatever island, and afterwards as long as the player stays where they fished
		return ProfitActivity.FISHING.engaged();
	}

	@Override
	public void prepare(Minecraft client) {
		shownAmount.follow(simulated() || wanted() ? 1f : 0f);
		Bucket bucket = shownBucket();
		total.follow(bucket.catches);
		shownHeight.follow(targetHeight());
		// only re-sort when a creature was caught or the view settings changed, not every frame
		String key = changes + "|" + range.get() + "|" + sort.get() + "|" + Math.round(rowsShown.get()) + "|" + (bucket == sample ? sampleFor : "");
		if (key.equals(listedFor)) {
			return;
		}
		listedFor = key;
		List<Map.Entry<String, Long>> all = new ArrayList<>();
		bucket.counts.forEach((name, count) -> all.add(Map.entry(name, count)));
		all.sort((a, b) -> {
			if (sort.get() == Sort.RARITY) {
				int rarity = Integer.compare(rarityOf(b.getKey()), rarityOf(a.getKey()));
				if (rarity != 0) {
					return rarity;
				}
			}
			int count = Long.compare(b.getValue(), a.getValue());
			return count != 0 ? count : a.getKey().compareToIgnoreCase(b.getKey());
		});
		int most = Math.round(rowsShown.get());
		listed = all.size() > most ? new ArrayList<>(all.subList(0, most)) : all;
		shownHeight.follow(targetHeight());
	}

	private String listedFor = "";

	/** Draws the panel again on top of an open inventory while the mouse is over it, so it can be clicked there; otherwise it stays under the dimming like the others. */
	public void renderInInventory(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!isEnabled() || shownAmount.value() <= 0.5f) {
			return;
		}
		int[] box = hudBounds();
		float lit = inventoryHover.lit(box, mouseX, mouseY);
		if (!InventoryHover.drawn(lit)) {
			return;
		}
		hoveredInInventory = InventoryHover.over(box, mouseX, mouseY);
		float alphaBefore = Draw.alpha();
		Draw.setAlpha(alphaBefore * lit);
		try {
			render(graphics, null);
		} finally {
			Draw.setAlpha(alphaBefore);
			hoveredInInventory = false;
		}
	}

	private final InventoryHover inventoryHover = new InventoryHover();

	/** Whether the mouse is over the panel in an inventory, where it is highlighted like a button. */
	private boolean hoveredInInventory;

	/** @return whether the click hit the panel, which then switches to the next time range (right click: the previous one) */
	public boolean clickInInventory(double mouseX, double mouseY, int button) {
		if (!isEnabled() || shownAmount.value() <= 0.5f || button != 0 && button != 1) {
			return false;
		}
		int[] box = hudBounds();
		if (mouseX < box[0] || mouseY < box[1] || mouseX >= box[0] + box[2] || mouseY >= box[1] + box[3]) {
			return false;
		}
		ProfitTracker.Range[] all = ProfitTracker.Range.values();
		range.set(all[Math.floorMod(range.get().ordinal() + (button == 1 ? -1 : 1), all.length)]);
		UiSounds.click();
		LumioApi.saveSoon();
		return true;
	}

	/** Used with the mouse while an inventory is open: it stays as it is behind menus. */
	@Override
	public boolean usedInMenus() {
		return true;
	}

	private static int rarityOf(String name) {
		Creature creature = BY_NAME.get(name);
		return creature == null ? 0 : creature.rarity();
	}

	@Override
	public boolean shown(Minecraft client) {
		return shownAmount.value() > 0.01f;
	}

	@Override
	public boolean inEditorWhenHidden() {
		return false;
	}

	@Override
	public int panelWidth() {
		return cardWidth();
	}

	@Override
	public int panelHeight() {
		return Math.round(shownHeight.value() > 0f ? shownHeight.value() : targetHeight());
	}

	/** The hero block (the count with its rate), a creature's row, and the list's label row. */
	private static final int HERO_BLOCK = 24;
	private static final int ROW_BASE = 13;
	private static final int LIST_TOP = 6;
	/** The least rarity a creature needs to be the rare one the panel follows (rare and up). */
	private static final int RARE_FROM = 2;

	private boolean listShown() {
		return showList.get() || HudEditor.isOpen();
	}

	private int targetHeight() {
		float height = contentTop() + HERO_BLOCK;
		if (listShown()) {
			height += PanelKit.GAP + LIST_TOP + Math.max(1, listed.size()) * line(ROW_BASE);
		}
		return Math.round(height + PanelKit.BOTTOM);
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		float amount = Mth.clamp(shownAmount.value(), 0f, 1f);
		float alphaBefore = Draw.alpha();
		Draw.setAlpha(alphaBefore * amount);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(0f, (1f - amount) * -8f);
		pose.translate(cardWidth() / 2f, 0f);
		pose.scale(0.94f + 0.06f * amount, 0.94f + 0.06f * amount);
		pose.translate(-cardWidth() / 2f, 0f);
		try {
			int height = panelHeight();
			mouseFree = mouseOver(client);
			drawGlass(ui, cardWidth(), height, PanelKit.RADIUS);
			graphics.enableScissor(0, 0, cardWidth(), height - 2);
			try {
				drawContent(ui);
			} finally {
				graphics.disableScissor();
			}
			// outside the panel's clip, over it and beside it
			if (mouseFree && (showRare.get() || hoveredCreature != null)) {
				drawTooltip(ui, shownBucket(), client);
			}
		} finally {
			pose.popMatrix();
			Draw.setAlpha(alphaBefore);
		}
	}

	/** The count of the hero and its line, made again only when they change. */
	private long heroCount = -1;
	private String heroText = "";
	private long rateShown = -2;
	private String rateText = "";

	private void drawContent(Canvas ui) {
		Bucket bucket = shownBucket();
		int width = cardWidth();
		// the area where the creatures are caught, or the time range over an inventory, where a click switches it
		String area = SkyBlockArea.island();
		String meta = hoveredInInventory || area.isEmpty() ? range.get().title() : area;
		familyHeader(ui, width, "fish", PanelKit.TILE_SEA, "Sea creatures", meta, hoveredInInventory ? accentArgb() : 0, false);
		float top = contentTop();
		long count = Math.round(total.value());
		if (count != heroCount) {
			heroCount = count;
			heroText = PanelKit.grouped(count);
		}
		long rate = bucket.activeMillis < 30_000L ? -1 : Math.round(bucket.catches / (bucket.activeMillis / 3_600_000.0));
		if (rate != rateShown) {
			rateShown = rate;
			rateText = "caught · " + (rate < 0 ? "-" : String.valueOf(rate)) + " / h";
		}
		PanelKit.hero(ui, heroText, PanelKit.PAD, top, PanelKit.HERO_MEDIUM, HudTheme.text(), false);
		float before = Draw.alpha();
		Draw.setAlpha(before * HudEditor.partAlpha(showPerHour.get()));
		ui.textAtScaled(showPerHour.get() || HudEditor.isOpen() ? rateText : "caught", PanelKit.PAD, top + 15f, PanelKit.SMALL, HudTheme.secondary());
		Draw.setAlpha(before);
		part("Per hour", showPerHour, PanelKit.PAD, top + 14f, ui.widthScaled(rateText, PanelKit.SMALL), 8f);
		hoveredCreature = null;
		if (!listShown()) {
			return;
		}
		float y = top + HERO_BLOCK + PanelKit.GAP;
		Draw.setAlpha(before * HudEditor.partAlpha(showList.get()));
		PanelKit.divider(ui, width, y);
		y += LIST_TOP;
		if (listed.isEmpty()) {
			ui.textAtScaled("Nothing caught yet", PanelKit.PAD, y + 2f, PanelKit.META, HudTheme.secondary());
		}
		for (Map.Entry<String, Long> entry : listed) {
			drawCreature(ui, bucket, entry, y);
			// the creature line under the mouse: the tooltip then shows that creature alone
			if (mouseFree && mouseY >= y && mouseY < y + line(ROW_BASE)) {
				hoveredCreature = entry.getKey();
			}
			y += line(ROW_BASE);
		}
		Draw.setAlpha(before);
		part("Creature list", showList, PanelKit.PAD, top + HERO_BLOCK + PanelKit.GAP, width - 2 * PanelKit.PAD,
			LIST_TOP + Math.max(1, listed.size()) * line(ROW_BASE));
	}

	/** Whether the mouse is free and over the panel this frame, where it is in the panel's units, and the creature line it is over. */
	private boolean mouseFree;
	private float mouseX;
	private float mouseY;
	private String hoveredCreature;

	/**
	 * @return whether the mouse is free and over the panel as the HUD shows it: with the chat open, in a menu with slots (where the panel is drawn
	 *         over it) or in the HUD editor; never in the settings, where the panel is a preview elsewhere
	 */
	private boolean mouseOver(Minecraft client) {
		var screen = client.gui.screen();
		if (!(screen instanceof ChatScreen) && !(screen instanceof AbstractContainerScreen<?>) && !HudEditor.isOpen()) {
			return false;
		}
		int[] box = hudBounds();
		if (box == null || box[2] <= 0 || box[3] <= 0) {
			return false;
		}
		double x = client.mouseHandler.getScaledXPos(client.getWindow());
		double y = client.mouseHandler.getScaledYPos(client.getWindow());
		if (x < box[0] || y < box[1] || x >= box[0] + box[2] || y >= box[1] + box[3]) {
			return false;
		}
		mouseX = (float) ((x - box[0]) * cardWidth() / box[2]);
		mouseY = (float) ((y - box[1]) * panelHeight() / box[3]);
		return true;
	}

	/**
	 * The tooltip: the rare creatures followed (rare and up, caught in the time range), one line each with the catches since the last one, the
	 * average and how long ago; over a creature's line, that creature alone. The double hook rate stands under the title.
	 */
	private void drawTooltip(Canvas ui, Bucket bucket, Minecraft client) {
		long now = System.currentTimeMillis();
		List<SeaCreatureTooltip.Line> lines = new ArrayList<>();
		String title;
		if (hoveredCreature != null) {
			title = range.get().title();
			lines.add(lineOf(bucket, hoveredCreature, now));
		} else {
			title = "Rare sea creatures";
			List<String> rare = new ArrayList<>();
			for (String name : bucket.counts.keySet()) {
				if (rarityOf(name) >= RARE_FROM) {
					rare.add(name);
				}
			}
			// the rarest first
			rare.sort((a, b) -> Integer.compare(rarityOf(b), rarityOf(a)));
			for (String name : rare) {
				lines.add(lineOf(bucket, name, now));
			}
		}
		String note = null;
		if (showDoubleHooks.get() && bucket.catches > 0) {
			long hooks = Math.max(1, bucket.catches - (doubleAsTwo.get() ? bucket.doubleHooks : 0));
			note = "Double hooks " + bucket.doubleHooks + (bucket.doubleHooks > 0 ? " · " + percent(bucket.doubleHooks / (double) hooks) : "");
		}
		if (HudEditor.isOpen()) {
			// the editor draws its selection outline over the panels: the tooltip goes over it, at the mouse in GUI pixels
			double screenX = client.mouseHandler.getScaledXPos(client.getWindow());
			double screenY = client.mouseHandler.getScaledYPos(client.getWindow());
			boolean left = screenX > client.getWindow().getGuiScaledWidth() / 2.0;
			String shownNote = note;
			HudEditor.drawOnTop(canvas -> SeaCreatureTooltip.draw(canvas, title, shownNote, lines, "No rare creature yet", (float) screenX, (float) screenY, left));
			return;
		}
		SeaCreatureTooltip.draw(ui, title, note, lines, "No rare creature yet", mouseX, mouseY, mouseX > cardWidth() / 2f);
	}

	/** @return a creature's tooltip line: catches since the last one (rare ones only), the average, and how long ago */
	private SeaCreatureTooltip.Line lineOf(Bucket bucket, String name, long now) {
		long caught = bucket.counts.getOrDefault(name, 0L);
		Long since = bucket.since.get(name);
		Long last = bucket.lastAt.get(name);
		long average = caught <= 0 ? 0 : Math.max(1, bucket.catches / caught);
		return new SeaCreatureTooltip.Line(name, colorOf(name) & 0xFFFFFF, new String[] {
			since == null ? "" : PanelKit.grouped(since) + " since last",
			average <= 0 ? "" : "1 in " + PanelKit.grouped(average),
			last == null ? "" : SeaCreatureTooltip.ago(now - last)});
	}

	private int colorOf(String creature) {
		return rarityColors.get() ? RARITY_COLORS[Mth.clamp(rarityOf(creature), 0, RARITY_COLORS.length - 1)] : HudTheme.text();
	}

	/** A creature of the list: a dot and its name in its rarity color, its count and its share. */
	private void drawCreature(Canvas ui, Bucket bucket, Map.Entry<String, Long> entry, float y) {
		int color = colorOf(entry.getKey());
		double share = bucket.catches <= 0 ? 0 : entry.getValue() / (double) bucket.catches;
		float right = cardWidth() - PanelKit.PAD;
		ui.discAt(PanelKit.PAD + 2.5f, y + ROW_BASE / 2f - 1f, 4f, Draw.withOpacity(color, 0.95f));
		if (percentages.get()) {
			String percent = percent(share);
			float percentWidth = ui.widthScaled(percent, PanelKit.SMALL);
			ui.textAtScaled(percent, right - percentWidth, y + 1.5f, PanelKit.SMALL, HudTheme.secondary());
			right -= Math.max(percentWidth, PERCENT_COLUMN) + 6f;
		}
		String count = PanelKit.grouped(entry.getValue());
		float countWidth = ui.boldWidthScaled(count, PanelKit.META);
		ui.textAtScaledBold(count, right - countWidth, y + 1f, PanelKit.META, HudTheme.text());
		float nameX = PanelKit.PAD + 9f;
		ui.textAtScaled(ui.fit(entry.getKey(), Math.round((right - countWidth - 6f - nameX) / PanelKit.META)), nameX, y + 1f, PanelKit.META,
			PanelKit.readable(color));
	}

	/** The width the share column keeps, so the counts stand in one column. */
	private static final float PERCENT_COLUMN = 18f;

	private static String percent(double share) {
		double value = share * 100.0;
		return value >= 10 || value == 0 ? Math.round(value) + "%" : PanelKit.decimals(value, 1) + "%";
	}

	// ------------------------------------------------------------------------------------------------ persistence

	@Override
	public JsonObject save() {
		JsonObject json = super.save();
		rollDay();
		JsonObject counts = new JsonObject();
		counts.add("today", today.toJson());
		counts.add("lifetime", lifetime.toJson());
		json.add("total", counts);
		return json;
	}

	@Override
	public void load(JsonObject json) {
		super.load(json);
		if (json.has("total") && json.get("total").isJsonObject()) {
			JsonObject counts = json.getAsJsonObject("total");
			if (counts.has("today") && counts.get("today").isJsonObject()) {
				today.fromJson(counts.getAsJsonObject("today"));
			}
			if (counts.has("lifetime") && counts.get("lifetime").isJsonObject()) {
				lifetime.fromJson(counts.getAsJsonObject("lifetime"));
			}
			rollDay();
		}
	}
}
