package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.hud.HudEditor;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.feature.SettingGroup;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Toggle;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.motion.Easing;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.api.experimental.ui.SlotLayer;
import dev.tim1337.lumio.skyblock.data.PetIcons;
import dev.tim1337.lumio.skyblock.data.PetTracker;
import dev.tim1337.lumio.skyblock.data.SkyBlock;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The active Hypixel SkyBlock pet, in the family look: a level ring around its head (filled by its experience toward the next level, when the
 * player list widget shows it), its name in its rarity color, the rarity, its level, an experience bar, the experience in numbers and its held
 * item. As a card, or as one simple row. A level up is a quiet moment: the ring fills its last part, glows softly, and the level flips.
 */
public class PetHudFeature extends AddonPanel {
	/** The panel's looks: the card with the level ring, or one simple row. */
	public enum Look {
		CARD("Card"),
		SIMPLE("Simple");

		private final Component displayName;

		Look(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** The level ring, the plate in it and how much the pet's head is enlarged on it. */
	private static final int RING = 42;
	private static final float RING_THICKNESS = 2.5f;
	private static final float PLATE = 32f;
	private static final float HEAD = 1.4f;
	/** The gap between the ring and the text, the name's size, and the heights and gaps of the rows beside the ring. */
	private static final int BESIDE = 9;
	private static final float NAME = 1.15f;
	private static final float NAME_ROW = 10f;
	private static final float INFO_ROW = 10f;
	private static final float BAR_ROW = 3f;
	private static final float XP_ROW = 7f;
	private static final float ROW_GAP = 4f;
	/** The level up: the ring fills, glows, the level flips, and the ring fills again from empty. */
	private static final long LEVEL_UP_MILLIS = 1000L;
	private static final float FILL_END = 150f;
	private static final float GLOW_PEAK = 300f;
	private static final float FLIP_START = 400f;
	private static final float FLIP_END = 560f;
	private static final float GLOW_END = 700f;
	private static final int WARM = 0xFFFFB340;
	/** How much of the pet's color lies on the panel behind its head: light, so the plate follows the panel in every theme. */
	private static final float PLATE_SHARE = 0.28f;
	/** How far the level moves while it flips. */
	private static final float FLIP_RISE = 6f;
	/** The sample in the HUD editor: its experience, as the widget would show it. */
	private static final float SAMPLE_PROGRESS = 0.68f;
	private static final String SAMPLE_XP = "1.24M / 1.82M XP";
	/** Sample pet for the HUD editor, created lazily: items do not exist yet when the mod initializes. */
	private static PetTracker.Pet sample;

	private static PetTracker.Pet sample() {
		if (sample == null) {
			sample = new PetTracker.Pet("Golden Dragon", 87, 0xFFAA00, "Tier Boost", new ItemStack(Items.PLAYER_HEAD));
		}
		return sample;
	}

	private final Choice<Look> look = add(make().choice("look", Component.literal("Style"), Look.CARD, Look::displayName,
		Choice.Style.SEGMENTED), "General");
	private final Toggle showIcon = add(make().toggle("show_icon", Component.literal("Level ring"), true), "General");
	private final Toggle showName = add(make().toggle("show_name", Component.literal("Name"), true), "General");
	private final Toggle showRarity = add(make().toggle("show_rarity", Component.literal("Rarity"), false), "General");
	private final Toggle showLevel = add(make().toggle("show_level", Component.literal("Level"), true), "General");
	private final Toggle showXpBar = add(make().toggle("show_xp_bar", Component.literal("XP bar"), true), "General");
	private final Toggle showXp = add(make().toggle("show_xp", Component.literal("XP numbers"), false), "General");
	private final Toggle showItem = add(make().toggle("show_item", Component.literal("Held item"), false), "General");
	private final Toggle rarityColor = add(make().toggle("rarity_color", Component.literal("Name in its rarity color"), true), "General");
	private final Toggle highlightEquipped = add(make().toggle("highlight_equipped", Component.literal("Highlight equipped pet"), true), "General");
	private final EquippedPetHighlight equippedHighlight = new EquippedPetHighlight(() -> isEnabled() && highlightEquipped.get());

	/** Persisted: the last active pet, so it is shown right away after a restart. */
	private static final String PETS_OF_PROFILES = "pets_of_profiles";
	private static final String PET_ICONS = "pet_icons";
	private String savedName = "";
	private int savedLevel;
	private int savedColor = 0xFFFFFF;
	private String savedHeld = "";
	private String savedIcon = "";
	private int savedChanges = -1;
	private boolean restored;

	/** Pets from the pets menu as persisted, kept until the game can create the items for their heads. */
	private List<Object[]> keptPets = List.of();
	private int savedCache = -1;
	private int savedIcons = -1;
	private PetTracker.Pet shown;
	/** Whether what is shown is only the editor's sample. */
	private boolean showingSample;
	/** The experience shown, following the real one smoothly. */
	private final Spring shownProgress = Spring.smooth(Spring.GENTLE);
	private float progress = -1f;
	private String xpText = "";
	/** The pet and level seen last, to notice a level up, and the level and progress before it. */
	private String lastName = "";
	private int lastLevel;
	private int fromLevel;
	private float fromProgress;
	private final PanelMoment levelUp = new PanelMoment(LEVEL_UP_MILLIS);
	/** The texts drawn, made again only when what they show changes. */
	private int textLevel = -1;
	private String levelText = "";
	private String fromLevelText = "";
	private String shortLevel = "";

	/** Adds the highlight of the equipped pet to the menus' slots, at startup. */
	public void register() {
		SlotLayer.add((screen, graphics, mouseX, mouseY) -> equippedHighlight.draw(screen, graphics));
	}

	public PetHudFeature() {
		super("pet_hud", "Pet", "The pet that is out in Hypixel SkyBlock.", true, Anchor.LEFT, false, 12f, 120f);
		iconRgb(SystemColors.ORANGE_RGB);
		iconName("paw");
		summary("The pet you have out");
		offerCardSize();
		offerNoBackground();
		offerModes(look);
	}

	@Override
	public void prepare(Minecraft client) {
		if (!restored && client.level != null) {
			restored = true;
			PetTracker.restore(keptPets);
			savedCache = PetTracker.cacheVersion();
			if (!savedName.isEmpty() && PetTracker.current() == null) {
				PetTracker.restoreLast(new PetTracker.Pet(savedName, savedLevel, savedColor, savedHeld, PetTracker.decodeIcon(savedIcon)));
			}
			savedChanges = PetTracker.changes();
		}
		// pets seen in the menu are cached, so a message containing only a name can be resolved to the rest
		if (savedIcons != PetIcons.version()) {
			boolean first = savedIcons < 0;
			savedIcons = PetIcons.version();
			if (!first) {
				LumioApi.saveSoon();
			}
		}
		if (restored && savedCache != PetTracker.cacheVersion()) {
			savedCache = PetTracker.cacheVersion();
			LumioApi.saveSoon();
		}
		PetTracker.Pet pet = PetTracker.current();
		if (savedChanges != PetTracker.changes()) {
			savedChanges = PetTracker.changes();
			savedName = pet == null ? "" : pet.name();
			savedLevel = pet == null ? 0 : pet.level();
			savedColor = pet == null ? 0xFFFFFF : pet.color();
			savedHeld = pet == null ? "" : pet.heldItem();
			savedIcon = pet == null ? "" : PetTracker.encodeIcon(pet.icon());
			LumioApi.saveSoon();
		}
		boolean isSample = pet == null && (HudEditor.isOpen() || simulated());
		showingSample = isSample;
		if (isSample) {
			pet = sample();
		}
		shown = pet;
		if (pet == null) {
			return;
		}
		float real = isSample ? SAMPLE_PROGRESS : PetTracker.xpProgress();
		xpText = isSample ? SAMPLE_XP : PetTracker.xpText();
		// the same pet a level higher: the level up moment, from the level and experience shown before
		if (!isSample && pet.name().equalsIgnoreCase(lastName) && lastLevel > 0 && pet.level() > lastLevel) {
			fromLevel = lastLevel;
			fromProgress = Math.max(0f, progress);
			levelUp.start();
		}
		if (!isSample) {
			lastName = pet.name();
			lastLevel = pet.level();
		}
		float target = Math.max(0f, real);
		float eased = real < 0f ? -1f : shownProgress.follow(target, frameSeconds());
		float millis = levelUp.millis();
		if (millis >= 0f && millis < FLIP_START) {
			// the ring fills its last part
			eased = fromProgress + (1f - fromProgress) * Easing.easeOut(PanelMoment.stage(millis, 0f, FILL_END));
		} else if (millis >= FLIP_START) {
			// and restarts empty, filling to the new level's experience
			eased = target * Easing.easeOut(PanelMoment.stage(millis, FLIP_START, LEVEL_UP_MILLIS));
			shownProgress.snap(eased);
		}
		progress = eased;
		if (textLevel != pet.level()) {
			textLevel = pet.level();
			levelText = Integer.toString(pet.level());
			shortLevel = "Lv " + pet.level();
		}
		if (millis >= 0f) {
			fromLevelText = Integer.toString(fromLevel);
		}
	}

	@Override
	public boolean shown(Minecraft client) {
		// a sample only shows when the editor asks for it, which the main mod decides
		return shown != null && !showingSample && SkyBlock.allowed();
	}

	private boolean simple() {
		return look.get() == Look.SIMPLE;
	}

	@Override
	public int panelWidth() {
		return cardWidth();
	}

	@Override
	public int panelHeight() {
		return simple() ? PanelKit.SIMPLE_ROW : PanelKit.TOP + Math.round(bodyHeight()) + PanelKit.BOTTOM;
	}

	/** @return whether the level ring is there: chosen, or faded in the HUD editor */
	private boolean ringShown() {
		return showIcon.get() || HudEditor.isOpen();
	}

	/** @return the height of the panel's body: the ring's, or only the rows' without the ring, so a hidden part leaves no empty band */
	private float bodyHeight() {
		PetTracker.Pet pet = shown;
		float column = pet == null ? RING : columnHeight(pet);
		return ringShown() ? Math.max(RING, column) : column;
	}

	/** @return the height of the rows beside the ring, as they are shown now */
	private float columnHeight(PetTracker.Pet pet) {
		boolean editing = HudEditor.isOpen();
		String rarity = PetTracker.rarityName(pet.color());
		boolean nameRow = showName.get() || editing;
		boolean infoRow = (showRarity.get() && !rarity.isEmpty()) || showLevel.get() || editing;
		boolean barRow = (showXpBar.get() || editing) && progress >= 0f;
		boolean xpRow = (showXp.get() && !xpText.isEmpty()) || (showItem.get() && !pet.heldItem().isEmpty()) || editing;
		int rows = (nameRow ? 1 : 0) + (infoRow ? 1 : 0) + (barRow ? 1 : 0) + (xpRow ? 1 : 0);
		return (nameRow ? NAME_ROW : 0f) + (infoRow ? INFO_ROW : 0f) + (barRow ? BAR_ROW : 0f) + (xpRow ? XP_ROW : 0f) + Math.max(0, rows - 1) * ROW_GAP;
	}

	/** @return the pet's color for its name, ring and plate: its rarity color, or the accent */
	private int tint(PetTracker.Pet pet) {
		return HudTheme.readable(0xFF000000 | (rarityColor.get() ? pet.color() : accentArgb() & 0xFFFFFF));
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		PetTracker.Pet pet = shown;
		if (pet == null) {
			return;
		}
		if (simple()) {
			drawSimple(ui, pet);
			return;
		}
		int width = panelWidth();
		drawGlass(ui, width, panelHeight(), PanelKit.RADIUS);
		int tint = tint(pet);
		float millis = levelUp.millis();
		float textX = PanelKit.PAD;
		float ringAlpha = HudEditor.partAlpha(showIcon.get());
		if (ringAlpha > 0f) {
			drawRing(ui, graphics, pet, tint, millis, ringAlpha);
			part("Level ring", showIcon, PanelKit.PAD, PanelKit.TOP + (bodyHeight() - RING) / 2f, RING, RING);
			textX = PanelKit.PAD + RING + BESIDE;
		}
		drawColumn(ui, pet, tint, textX, width - PanelKit.PAD - textX, millis);
	}

	private void drawRing(Canvas ui, GuiGraphicsExtractor graphics, PetTracker.Pet pet, int tint, float millis, float alpha) {
		float before = Draw.alpha();
		Draw.setAlpha(before * alpha);
		float centerX = PanelKit.PAD + RING / 2f;
		float centerY = PanelKit.TOP + bodyHeight() / 2f;
		// the level up's warm glow, rising after the ring is full and settling as the level flips
		float glow = millis < 0f ? 0.6f : millis < GLOW_PEAK ? 0.6f + 0.4f * PanelMoment.stage(millis, FILL_END, GLOW_PEAK)
			: 1f - 0.4f * PanelMoment.stage(millis, GLOW_PEAK, GLOW_END);
		if (millis >= 0f) {
			ui.softLight(centerX, centerY, RING / 2f + 10f, Draw.withOpacity(WARM, 0.35f * (1f - PanelMoment.stage(millis, GLOW_PEAK, GLOW_END))));
		}
		PanelKit.ring(graphics, ui, centerX, centerY, RING, RING_THICKNESS, Math.max(0f, progress), tint, glow);
		ui.discAt(centerX, centerY, PLATE, Draw.withOpacity(tint, PLATE_SHARE));
		ui.softLight(centerX, centerY - 3f, PLATE / 2f, Draw.withOpacity(tint, 0.22f));
		ItemStack made = PetIcons.forPet(pet, pet.icon(), PetTracker.baseName(pet.name()), pet.color());
		ItemStack icon = made.isEmpty() ? itemIcon(Items.BONE) : made;
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(centerX - 8f * HEAD, centerY - 8f * HEAD);
		pose.scale(HEAD, HEAD);
		graphics.item(icon, 0, 0);
		pose.popMatrix();
		Draw.setAlpha(before);
	}

	/** Draws the rows beside the ring, centered on it: the name, the rarity and level, the bar, and the experience and held item. */
	private void drawColumn(Canvas ui, PetTracker.Pet pet, int tint, float x, float room, float millis) {
		boolean editing = HudEditor.isOpen();
		String rarity = PetTracker.rarityName(pet.color());
		boolean nameRow = showName.get() || editing;
		boolean infoRow = (showRarity.get() && !rarity.isEmpty()) || showLevel.get() || editing;
		boolean barRow = (showXpBar.get() || editing) && progress >= 0f;
		boolean xpRow = (showXp.get() && !xpText.isEmpty()) || (showItem.get() && !pet.heldItem().isEmpty()) || editing;
		// the rows centered on the ring, or from the top without it
		float y = PanelKit.TOP + (bodyHeight() - columnHeight(pet)) / 2f;
		float before = Draw.alpha();
		if (nameRow) {
			Draw.setAlpha(before * HudEditor.partAlpha(showName.get()));
			ui.textAtScaledBold(ui.fit(pet.name(), Math.round(room / NAME)), x, y, NAME, tint);
			Draw.setAlpha(before);
			part("Name", showName, x, y, room, NAME_ROW);
			y += NAME_ROW + ROW_GAP;
		}
		if (infoRow) {
			float cursor = x;
			if (!rarity.isEmpty() && (showRarity.get() || editing)) {
				Draw.setAlpha(before * HudEditor.partAlpha(showRarity.get()));
				float tag = PanelKit.tag(ui, rarity, cursor, y, tint);
				Draw.setAlpha(before);
				part("Rarity", showRarity, cursor, y, tag, PanelKit.TAG);
				cursor += tag + 5f;
			}
			Draw.setAlpha(before * HudEditor.partAlpha(showLevel.get()));
			drawLevel(ui, cursor, y + 1.5f, millis);
			Draw.setAlpha(before);
			part("Level", showLevel, cursor, y, x + room - cursor, INFO_ROW);
			y += INFO_ROW + ROW_GAP;
		}
		if (barRow) {
			Draw.setAlpha(before * HudEditor.partAlpha(showXpBar.get()));
			PanelKit.bar(ui, x, y, room, PanelKit.BAR, progress, tint);
			Draw.setAlpha(before);
			part("XP bar", showXpBar, x, y - 2f, room, BAR_ROW + 4f);
			y += BAR_ROW + ROW_GAP;
		}
		if (xpRow) {
			float half = room / 2f;
			if (!xpText.isEmpty() && (showXp.get() || editing)) {
				Draw.setAlpha(before * HudEditor.partAlpha(showXp.get()));
				ui.textAtScaled(ui.fit(xpText, Math.round(half / PanelKit.SMALL)), x, y, PanelKit.SMALL, HudTheme.secondary());
				Draw.setAlpha(before);
				part("XP numbers", showXp, x, y - 1f, half, XP_ROW + 2f);
			}
			if (!pet.heldItem().isEmpty() && (showItem.get() || editing)) {
				String held = ui.fit(pet.heldItem(), Math.round((half - 4f) / PanelKit.SMALL));
				float heldWidth = ui.widthScaled(held, PanelKit.SMALL);
				Draw.setAlpha(before * HudEditor.partAlpha(showItem.get()));
				ui.textAtScaled(held, x + room - heldWidth, y, PanelKit.SMALL, HudTheme.secondary());
				Draw.setAlpha(before);
				part("Held item", showItem, x + room - heldWidth, y - 1f, heldWidth, XP_ROW + 2f);
			}
		}
	}

	/** Draws "Level 87"; during a level up the old level flips up and out while the new one comes in from below. */
	private void drawLevel(Canvas ui, float x, float y, float millis) {
		ui.textAtScaled("Level", x, y, PanelKit.META, HudTheme.secondary());
		float numberX = x + ui.widthScaled("Level ", PanelKit.META);
		if (millis < 0f || millis >= FLIP_END) {
			ui.textAtScaledBold(levelText, numberX, y, PanelKit.META, HudTheme.text());
			return;
		}
		if (millis < FLIP_START) {
			ui.textAtScaledBold(fromLevelText, numberX, y, PanelKit.META, HudTheme.text());
			return;
		}
		float flip = Easing.easeOut(PanelMoment.stage(millis, FLIP_START, FLIP_END));
		float before = Draw.alpha();
		Draw.setAlpha(before * (1f - flip));
		ui.textAtScaledBold(fromLevelText, numberX, y - FLIP_RISE * flip, PanelKit.META, HudTheme.text());
		Draw.setAlpha(before * flip);
		ui.textAtScaledBold(levelText, numberX, y + FLIP_RISE * (1f - flip), PanelKit.META, HudTheme.text());
		Draw.setAlpha(before);
	}

	/** Plays the level up once, from the level before, for the HUD editor's simulation of the moments. */
	public void playLevelUp() {
		fromLevel = Math.max(1, (shown == null ? 2 : shown.level()) - 1);
		fromProgress = Math.max(0f, progress);
		fromLevelText = Integer.toString(fromLevel);
		levelUp.start();
	}

	/** One simple row: the pet's name, its experience bar and its level. */
	private void drawSimple(Canvas ui, PetTracker.Pet pet) {
		int width = panelWidth();
		float bar = showXpBar.get() && progress >= 0f ? progress : -1f;
		PanelKit.simpleRow(ui, width, 0f, hasBackground(), "paw", PanelKit.TILE_PET, showName.get() ? pet.name() : "Pet", bar, tint(pet),
			showLevel.get() ? shortLevel : "", 0xFFFFFFFF);
	}

	// ------------------------------------------------------------------------------------------------ persistence

	@Override
	public JsonObject save() {
		JsonObject json = super.save();
		var cached = PetTracker.exportCache();
		if (restored && !cached.isEmpty()) {
			json.add("pets", petArray(cached));
		} else if (!keptPets.isEmpty()) {
			// the game was not ready to recreate them: keep what was in the file
			json.add("pets", petArray(keptPets));
		}
		if (!savedName.isEmpty()) {
			JsonObject pet = new JsonObject();
			pet.addProperty("name", savedName);
			pet.addProperty("level", savedLevel);
			pet.addProperty("color", savedColor);
			pet.addProperty("held", savedHeld);
			pet.addProperty("icon", savedIcon);
			json.add("last_pet", pet);
		}
		JsonObject icons = new JsonObject();
		PetIcons.export().forEach((key, parts) -> {
			JsonArray array = new JsonArray();
			for (String part : parts) {
				array.add(part);
			}
			icons.add(key, array);
		});
		if (!icons.isEmpty()) {
			json.add(PET_ICONS, icons);
		}
		JsonObject ofProfiles = new JsonObject();
		PetTracker.ofProfiles().forEach((profile, pet) -> {
			JsonObject entry = new JsonObject();
			if (pet != null) {
				entry.addProperty("name", pet.name());
				entry.addProperty("level", pet.level());
				entry.addProperty("color", pet.color());
				entry.addProperty("held", pet.heldItem());
			}
			ofProfiles.add(profile, entry);
		});
		if (!ofProfiles.isEmpty()) {
			json.add(PETS_OF_PROFILES, ofProfiles);
		}
		return json;
	}

	private static JsonArray petArray(List<Object[]> pets) {
		JsonArray array = new JsonArray();
		for (Object[] item : pets) {
			JsonObject entry = new JsonObject();
			entry.addProperty("name", (String) item[0]);
			entry.addProperty("level", (Integer) item[1]);
			entry.addProperty("color", (Integer) item[2]);
			entry.addProperty("held", (String) item[3]);
			entry.addProperty("icon", (String) item[4]);
			array.add(entry);
		}
		return array;
	}

	@Override
	public void load(JsonObject json) {
		super.load(json);
		if (json.has(PETS_OF_PROFILES) && json.get(PETS_OF_PROFILES).isJsonObject()) {
			Map<String, PetTracker.Pet> read = new HashMap<>();
			for (var entry : json.getAsJsonObject(PETS_OF_PROFILES).entrySet()) {
				try {
					JsonObject pet = entry.getValue().getAsJsonObject();
					read.put(entry.getKey(), !pet.has("name") ? null
						: new PetTracker.Pet(pet.get("name").getAsString(), pet.get("level").getAsInt(), pet.get("color").getAsInt(), pet.get("held").getAsString(), ItemStack.EMPTY));
				} catch (RuntimeException ignored) {
				}
			}
			PetTracker.restoreProfiles(read);
		}
		if (json.get(PET_ICONS) instanceof JsonObject icons) {
			Map<String, String[]> read = new HashMap<>();
			for (var entry : icons.entrySet()) {
				if (entry.getValue() instanceof JsonArray array && array.size() <= 3) {
					try {
						String[] parts = new String[array.size()];
						for (int i = 0; i < parts.length; i++) {
							parts[i] = array.get(i).getAsString();
						}
						read.put(entry.getKey(), parts);
					} catch (RuntimeException ignored) {
						// an odd entry is left out
					}
				}
			}
			PetIcons.restore(read);
		}
		if (json.has("pets") && json.get("pets").isJsonArray()) {
			List<Object[]> read = new ArrayList<>();
			for (var element : json.getAsJsonArray("pets")) {
				try {
					JsonObject entry = element.getAsJsonObject();
					read.add(new Object[]{entry.get("name").getAsString(), entry.get("level").getAsInt(), entry.get("color").getAsInt(),
						entry.get("held").getAsString(), entry.get("icon").getAsString()});
				} catch (RuntimeException ignored) {
				}
			}
			keptPets = read;
		}
		if (json.has("last_pet") && json.get("last_pet").isJsonObject()) {
			JsonObject pet = json.getAsJsonObject("last_pet");
			try {
				savedName = pet.get("name").getAsString();
				savedLevel = pet.get("level").getAsInt();
				savedColor = pet.get("color").getAsInt();
				savedHeld = pet.get("held").getAsString();
				savedIcon = pet.get("icon").getAsString();
			} catch (RuntimeException ignored) {
				savedName = "";
			}
		}
	}
}
