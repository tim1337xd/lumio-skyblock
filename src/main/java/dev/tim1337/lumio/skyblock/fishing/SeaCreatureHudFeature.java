package dev.tim1337.lumio.skyblock.fishing;

import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.hud.HudEditor;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.feature.SettingGroup;
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
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

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

	/** A sea creature: name, rarity (0 common to 5 mythic), and a fragment of its catch message. */
	private record Creature(String name, int rarity, String message) {
	}

	private static final Creature[] CREATURES = {
		// water
		new Creature("Squid", 0, "A Squid appeared."),
		new Creature("Sea Walker", 0, "You caught a Sea Walker."),
		new Creature("Night Squid", 0, "Pitch darkness reveals a Night Squid."),
		new Creature("Sea Guardian", 1, "You stumbled upon a Sea Guardian."),
		new Creature("Sea Witch", 1, "It looks like you've disrupted the Sea Witch's brewing session."),
		new Creature("Sea Archer", 1, "You reeled in a Sea Archer."),
		new Creature("Rider of the Deep", 1, "The Rider of the Deep has emerged."),
		new Creature("Catfish", 2, "Huh? A Catfish!"),
		new Creature("Carrot King", 2, "Is this even a fish? It's the Carrot King!"),
		new Creature("Agarimoo", 2, "Agarimoo"),
		new Creature("Sea Leech", 2, "Gross! A Sea Leech!"),
		new Creature("Guardian Defender", 3, "You've discovered a Guardian Defender of the sea."),
		new Creature("Deep Sea Protector", 3, "You have awoken the Deep Sea Protector"),
		new Creature("Water Hydra", 4, "The Water Hydra has come to test your strength."),
		new Creature("Sea Emperor", 4, "The Sea Emperor arises from the depths."),
		new Creature("Oasis Rabbit", 1, "An Oasis Rabbit appears from the water."),
		new Creature("Oasis Sheep", 1, "An Oasis Sheep appears from the water."),
		// mines
		new Creature("Water Worm", 2, "A Water Worm surfaces!"),
		new Creature("Poisoned Water Worm", 2, "A Poisoned Water Worm surfaces!"),
		new Creature("Abyssal Miner", 3, "An Abyssal Miner breaks out of the water!"),
		new Creature("Flaming Worm", 2, "A Flaming Worm surfaces from the depths!"),
		new Creature("Lava Blaze", 3, "A Lava Blaze has surfaced from the depths!"),
		new Creature("Lava Pigman", 3, "A Lava Pigman arose from the depths!"),
		// Crimson Isle lava
		new Creature("Magma Slug", 0, "From beneath the lava appears a Magma Slug."),
		new Creature("Moogma", 0, "You hear a faint Moo from the lava... A Moogma appears."),
		new Creature("Lava Leech", 1, "A small but fearsome Lava Leech emerges."),
		new Creature("Pyroclastic Worm", 1, "You feel the heat radiating as a Pyroclastic Worm surfaces."),
		new Creature("Lava Flame", 2, "A Lava Flame flies out from beneath the lava."),
		new Creature("Fire Eel", 2, "A Fire Eel slithers out from the depths."),
		new Creature("Taurus", 3, "Taurus and his steed emerge."),
		new Creature("Plhlegblast", 4, "WOAH! A Plhlegblast appeared."),
		new Creature("Thunder", 4, "You hear a massive rumble as Thunder emerges."),
		new Creature("Lord Jawbus", 5, "You have angered a legendary creature... Lord Jawbus has arrived."),
		new Creature("Fiery Scuttler", 4, "A Fiery Scuttler inconspicuously waddles up to you"),
		new Creature("Ragnarok", 5, "The end times are upon us: Ragnarok is here."),
		// Spooky Festival
		new Creature("Scarecrow", 0, "Phew! It's only a Scarecrow."),
		new Creature("Nightmare", 2, "you caught a Nightmare."),
		new Creature("Werewolf", 3, "It must be a full moon, a Werewolf appears."),
		new Creature("Phantom Fisher", 4, "The spirit of a long lost Phantom Fisher has come to haunt you."),
		new Creature("Grim Reaper", 4, "This can't be! The manifestation of death himself!"),
		// Jerry's Workshop
		new Creature("Frozen Steve", 0, "Frozen Steve fell into the pond long ago"),
		new Creature("Frosty", 0, "It's a snowman! He looks harmless."),
		new Creature("Grinch", 1, "The Grinch stole Jerry's Gifts"),
		new Creature("Nutcracker", 3, "You found a forgotten Nutcracker laying beneath the ice."),
		new Creature("Yeti", 4, "What is this creature!?"),
		new Creature("Reindrake", 5, "A Reindrake forms from the depths."),
		// the Fishing Festival
		new Creature("Nurse Shark", 0, "you've caught a Nurse Shark."),
		new Creature("Blue Shark", 1, "it's a Blue Shark."),
		new Creature("Tiger Shark", 2, "the wild Tiger Shark!"),
		new Creature("Great White Shark", 4, "a Great White Shark has tracked your scent"),
		// Backwater Bayou
		new Creature("Frog Man", 0, "it's a Frog Man!"),
		new Creature("Trash Gobbler", 0, "The Trash Gobbler is hungry for you!"),
		new Creature("Dumpster Diver", 1, "A Dumpster Diver has emerged from the swamp!"),
		new Creature("Banshee", 2, "The desolate wail of a Banshee breaks the silence."),
		new Creature("Bayou Sludge", 2, "the Bayou Sludge!"),
		new Creature("Alligator", 3, "It's an Alligator!"),
		new Creature("Blue Ringed Octopus", 3, "It's a Blue Ringed Octopus!"),
		new Creature("Titanoboa", 5, "A massive Titanoboa surfaces."),
		new Creature("Wiki Tiki", 5, "you have disturbed the Wiki Tiki!"),
	};

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
		long catches;
		long doubleHooks;
		long activeMillis;
		String day = "";

		void clear() {
			counts.clear();
			since.clear();
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
						counts.put(entry.getKey(), entry.getValue().getAsLong());
					}
				}
				if (json.has("since") && json.get("since").isJsonObject()) {
					for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("since").entrySet()) {
						if (BY_NAME.containsKey(entry.getKey())) {
							since.put(entry.getKey(), Math.max(0L, entry.getValue().getAsLong()));
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
	private final Toggle showRare = add(make().toggle("show_rare", Component.literal("Rare creature"), true), "General");
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
		super("sea_creatures", "Sea creatures", "The sea creatures you fish up, with double hooks, their share of all, and how many in an hour: for the session, today and all time.",
			true, Anchor.LEFT, false, 12f, 220f);
		offerCardSize();
		offerNoBackground();
		iconRgb(SystemColors.BLUE_RGB);
		iconName("fish");
		summary("The sea creatures you catch, and how many per hour");
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
		if (text.contains("Double Hook!")) {
			doubleHookAt = System.currentTimeMillis();
			return;
		}
		for (Creature creature : CREATURES) {
			if (text.contains(creature.message())) {
				caught(creature);
				return;
			}
		}
	}

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
		if (ProfitActivity.FISHING.now() && FishingRods.onFishingIsland()) {
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
		// auto-detected, as the fishing tracker: on the fishing islands, while fishing, and afterwards as long as the player stays where they fished
		return FishingRods.onFishingIsland() && ProfitActivity.FISHING.engaged();
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

	/** The hero block (the count with its rate, and the rare creature beside it), a creature's row, and the list's label row. */
	private static final int HERO_BLOCK = 30;
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
			drawGlass(ui, cardWidth(), height, PanelKit.RADIUS);
			graphics.enableScissor(0, 0, cardWidth(), height - 2);
			try {
				drawContent(ui);
			} finally {
				graphics.disableScissor();
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
		float leftWidth = Math.max(ui.boldWidthScaled(heroText, PanelKit.HERO_MEDIUM), ui.widthScaled(rateText, PanelKit.SMALL));
		float x = PanelKit.PAD + leftWidth + 12f;
		float room = width - PanelKit.PAD - x;
		if (showRare.get() || HudEditor.isOpen()) {
			Draw.setAlpha(before * HudEditor.partAlpha(showRare.get()));
			drawRare(ui, bucket, x, top, room);
			Draw.setAlpha(before);
			part("Rare creature", showRare, x, top, room, HERO_BLOCK);
		}
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
			y += line(ROW_BASE);
		}
		Draw.setAlpha(before);
		part("Creature list", showList, PanelKit.PAD, top + HERO_BLOCK + PanelKit.GAP, width - 2 * PanelKit.PAD,
			LIST_TOP + Math.max(1, listed.size()) * line(ROW_BASE));
	}

	/** The rare creature: the rarest caught (rare and up), its catches since the last one with a bar toward its average, and that average. */
	private void drawRare(Canvas ui, Bucket bucket, float x, float top, float room) {
		String rare = rareCreature(bucket);
		if (rare == null) {
			if (showDoubleHooks.get()) {
				long hooks = Math.max(1, bucket.catches - (doubleAsTwo.get() ? bucket.doubleHooks : 0));
				ui.textAtScaled("Double hooks", x, top + 2f, PanelKit.SMALL, HudTheme.secondary());
				String value = bucket.doubleHooks + (bucket.doubleHooks > 0 ? " · " + percent(bucket.doubleHooks / (double) hooks) : "");
				ui.textAtScaledBold(value, x, top + 11f, TextScale.BODY, HudTheme.text());
			} else {
				ui.textAtScaled("No rare creature yet", x, top + 6f, PanelKit.SMALL, HudTheme.secondary());
			}
			return;
		}
		int color = colorOf(rare);
		long caught = bucket.counts.getOrDefault(rare, 0L);
		// unknown until it is caught once more: the count started with this version
		Long counted = bucket.since.get(rare);
		long since = counted == null ? -1 : counted;
		long average = caught <= 0 ? 0 : Math.max(1, bucket.catches / caught);
		if (since != rareSince || average != rareAverage || !rare.equals(rareName)) {
			rareSince = since;
			rareAverage = average;
			rareName = rare;
			sinceText = since < 0 ? "" : PanelKit.grouped(since) + " since last";
			averageText = "Average 1 in " + PanelKit.grouped(average);
		}
		float sinceWidth = ui.widthScaled(sinceText, PanelKit.SMALL);
		ui.textAtScaled(sinceText, x + room - sinceWidth, top + 1.5f, PanelKit.SMALL, HudTheme.secondary());
		ui.textAtScaledBold(ui.fit(rare, Math.round((room - sinceWidth - 6f) / PanelKit.META)), x, top + 1f, PanelKit.META, PanelKit.readable(color));
		if (since >= 0) {
			PanelKit.bar(ui, x, top + 12f, room, PanelKit.BAR, average <= 0 ? 0f : Math.min(1f, since / (float) average), color);
		}
		ui.textAtScaled(averageText, x, top + 18f, PanelKit.SMALL, Draw.withOpacity(HudTheme.secondary(), 0.8f));
	}

	/** The rare creature's texts, made again only when they change. */
	private long rareSince = -2;
	private long rareAverage = -1;
	private String rareName = "";
	private String sinceText = "";
	private String averageText = "";

	/** @return the rarest creature caught in a time range (rare and up, the least caught of those), or null if none */
	private static String rareCreature(Bucket bucket) {
		String found = null;
		int foundRarity = RARE_FROM - 1;
		long foundCount = Long.MAX_VALUE;
		for (Map.Entry<String, Long> entry : bucket.counts.entrySet()) {
			int rarity = rarityOf(entry.getKey());
			if (rarity > foundRarity || rarity == foundRarity && found != null && entry.getValue() < foundCount) {
				found = entry.getKey();
				foundRarity = rarity;
				foundCount = entry.getValue();
			}
		}
		return found;
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
