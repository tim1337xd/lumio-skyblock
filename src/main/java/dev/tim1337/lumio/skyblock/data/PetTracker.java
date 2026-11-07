package dev.tim1337.lumio.skyblock.data;

import dev.tim1337.lumio.api.experimental.util.Debug;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Determines the active Hypixel SkyBlock pet from the places the game reveals it: chat messages when a pet is summoned, despawned or changed by an
 * autopet rule; the pets menu, where the active one is marked; the level-up messages; plus the player list widget and the pet's name tag. The
 * pets menu also provides each pet's head, which is cached and displayed with the pet.
 */
public final class PetTracker {
	/** @param color the pet's rarity color as RGB, white if unknown */
	public record Pet(String name, int level, int color, String heldItem, ItemStack icon) {
		public Pet withLevel(int newLevel) {
			return new Pet(name, newLevel, color, heldItem, icon);
		}
	}

	// Not anchored to line start or end: surrounding text (a symbol, a button, a rank) does not matter. The level is not always present in autopet
	// messages.
	private static final Pattern SUMMONED = Pattern.compile("You summoned your (.+?)\\s*[✦⭐★]?\\s*!");
	private static final Pattern DESPAWNED = Pattern.compile("You despawned your (.+?)\\s*[✦⭐★]?\\s*!");
	private static final Pattern AUTOPET = Pattern.compile("Autopet\\s+equipped\\s+your\\s+(?:\\[Lvl\\s*(\\d{1,4})]\\s*)?(.+?)\\s*[✦⭐★]?\\s*!");
	private static final Pattern LEVEL_UP = Pattern.compile("Your (.+?) leveled up to level (\\d{1,4})!");
	private static final Pattern PETS_MENU = Pattern.compile("^(?:\\(\\d+/\\d+\\) )?Pets(?: \\(\\d+/\\d+\\))?$");
	private static final Pattern HOVER_NAME = Pattern.compile("^(?:⭐\\s*)?\\[Lvl\\s+(\\d+)]\\s*(.+?)(?:\\s*✦)?$");

	private static volatile Pet current;
	/** Whether a source has said which pet is out, this session or for this profile before: without that "none" means nothing. */
	private static volatile boolean known;
	/** The pet last known of every profile, by its id; the profile is what the server says when it starts. */
	private static final Map<String, Pet> OF_PROFILE = new HashMap<>();
	private static final int PROFILES_MOST = 32;
	private static String profile;
	/** Pets seen in the pets menu, keyed by name and by name plus rarity, so a message containing only a name can be resolved. */
	private static final Map<String, Pet> SEEN = new HashMap<>();
	/** Pet heads serialized for the settings file, by the same keys; encoded once per pet, not on every menu view. */
	private static final Map<String, String> ENCODED = new HashMap<>();
	/** Incremented whenever the known pets change, to trigger saving. */
	private static int cacheVersion;
	private static final int CACHE_MOST = 250;

	private static String keyOf(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	private static String keyOf(String name, int color) {
		return name.toLowerCase(Locale.ROOT) + "|" + Integer.toHexString(color);
	}

	/** Every pet seen in the menu, keyed by name, rarity, level and held item: a player can own several of one kind (and skins), which are different pets. */
	private static final Map<String, Pet> ALL = new LinkedHashMap<>();
	/** The previously active pet, the likely candidate when a pet of that name is summoned again. */
	private static String lastOutKey = "";

	/**
	 * @return the key of a known pet: a real pet from a menu by what tells it from others (its UUID, else type, rarity, skin and variant, see
	 *         {@link PetIdentity#key}), so two pets of one kind with different skins stay two; a pet known only from a message by name, rarity,
	 *         level and held item
	 */
	private static String fullKey(Pet pet) {
		PetIdentity identity = PetIcons.of(pet.icon());
		String kind = keyOf(pet.name(), pet.color()) + "|" + pet.level() + "|" + pet.heldItem().toLowerCase(Locale.ROOT);
		return identity == null ? kind + (hasSkin(pet) ? "|skin" : "") : identity.uuid().isEmpty() ? kind + "|" + identity.key() : identity.key();
	}

	/**
	 * @return the known pet with this name that is most likely meant, scored by level (exact or nearest), held item, rarity, and whether it was
	 *         active before; null if no pet of that name is known
	 */
	private static Pet lookup(String name, int color, int level, String held, boolean skin, boolean strict) {
		Pet best = null;
		double bestScore = -1e9;
		for (Pet pet : ALL.values()) {
			if (!baseName(pet.name()).equalsIgnoreCase(baseName(name))) {
				continue;
			}
			double score = 0;
			if (pet.name().equalsIgnoreCase(name)) {
				score += 8; // the same skin counter as well: this is the pet
			}
			if (color != 0 && pet.color() == color) {
				score += 3;
			}
			// skinned pets are recognized by their skin marker (the star in the name, or a counter such as "[324✦]")
			boolean skinned = hasSkin(pet);
			// the message states whether it has a skin: a pet that differs in that is a different pet and is not chosen. If the right one is unknown
			// (the pets menu was never opened), the pet is built from the message rather than from the other one sharing its name.
			if (skin != skinned) {
				if (strict) {
					continue;
				}
				score -= 6; // the message may not state it (a manually summoned pet): the other one is just less likely
			}
			if (level > 0) {
				score += pet.level() == level ? 10 : -Math.abs(pet.level() - level) / 1000.0;
			}
			if (!held.isEmpty() && pet.heldItem().equalsIgnoreCase(held)) {
				score += 4;
			}
			// the previously active pet is only preferred when nothing else is known (manually summoned pets have no level in the message)
			if (fullKey(pet).equals(lastOutKey) && level <= 0 && held.isEmpty()) {
				score += 0.5;
			}
			if (!pet.icon().isEmpty()) {
				score += 0.5;
			}
			// later entries win ties
			if (score >= bestScore) {
				bestScore = score;
				best = pet;
			}
		}
		return best;
	}

	/** @return the tracker state for the debug command: every pet seen, with its distinguishing attributes, and the last message */
	public static List<String> describe() {
		List<String> lines = new ArrayList<>();
		lines.add("Out now: " + (current == null ? "none" : current.name() + " lvl " + current.level() + " held \"" + current.heldItem() + "\" skin " + hasSkin(current) + " head " + !current.icon().isEmpty()));
		lines.add("Last message: " + lastPetText());
		lines.add("Known pets: " + ALL.size());
		for (Pet pet : ALL.values()) {
			lines.add("  " + pet.name() + " lvl " + pet.level() + " held \"" + pet.heldItem() + "\" skin " + hasSkin(pet) + " color " + Integer.toHexString(pet.color()));
		}
		return lines;
	}

	/** @return whether the pet has a skin, judged from the menu item it was created from */
	private static boolean hasSkin(Pet pet) {
		if (!pet.icon().isEmpty() && pet.icon().getHoverName().getString().contains("✦")) {
			return true; // the item name contains the skin counter: "[324✦] Golden Dragon"
		}
		CustomData data = pet.icon().isEmpty() ? null : pet.icon().get(DataComponents.CUSTOM_DATA);
		JsonObject info = data == null ? null : petInfo(data);
		if (info == null || !info.has("skin") || !info.get("skin").isJsonPrimitive()) {
			return false;
		}
		String skin = info.get("skin").getAsString();
		return !skin.isBlank() && !skin.equalsIgnoreCase("null") && !skin.equalsIgnoreCase("none");
	}

	/** Caches a seen pet with its head, to resolve it by name later, also after a restart. */
	private static void remember(Pet pet) {
		if (pet.name().isEmpty()) {
			return;
		}
		String full = fullKey(pet);
		boolean changed = !ALL.containsKey(full);
		ALL.remove(full);
		ALL.put(full, pet);
		while (ALL.size() > CACHE_MOST) {
			ALL.remove(ALL.keySet().iterator().next());
		}
		SEEN.put(keyOf(pet.name()), pet);
		if (!pet.icon().isEmpty() && (changed || !ENCODED.containsKey(full))) {
			String text = encodeIcon(pet.icon());
			if (!text.isEmpty()) {
				ENCODED.put(full, text);
			}
		}
		if (changed) {
			cacheVersion++;
		}
	}

	/** @return a counter incremented whenever the known pets change */
	public static int cacheVersion() {
		return cacheVersion;
	}

	/** @return the known pets with their heads serialized, for the settings file */
	public static List<Object[]> exportCache() {
		List<Object[]> list = new ArrayList<>();
		for (Map.Entry<String, Pet> entry : ALL.entrySet()) {
			Pet pet = entry.getValue();
			list.add(new Object[]{pet.name(), pet.level(), pet.color(), pet.heldItem(), ENCODED.getOrDefault(entry.getKey(), "")});
		}
		return list;
	}

	/** Restores the pets from the settings file. Heads are deserialized once the game can create the items. */
	public static void restore(List<Object[]> kept) {
		for (Object[] item : kept) {
			String name = (String) item[0];
			int level = (Integer) item[1];
			int color = (Integer) item[2];
			ItemStack icon = decodeIcon((String) item[4]);
			Pet pet = new Pet(name, level, color, (String) item[3], icon);
			String full = fullKey(pet);
			ALL.putIfAbsent(full, pet);
			SEEN.putIfAbsent(keyOf(name), pet);
			if (!((String) item[4]).isEmpty()) {
				ENCODED.putIfAbsent(full, (String) item[4]);
			}
		}
	}
	private static long lastScan;
	private static int changes;

	private PetTracker() {
	}

	public static void register() {
		// messages are read from the packet (see ClientPacketListenerMixin) rather than the chat event: other mods can hide a message, such as the
		// autopet one, before the event fires, and it would never be seen here
		ClientTickEvents.END_CLIENT_TICK.register(PetTracker::tick);
	}

	/** @return the active pet, or null if there is none or it is unknown */
	public static Pet current() {
		return current;
	}

	/** @return whether the pet out is of a type, by its name and never its look; null when it is not known which pet is out */
	public static Boolean isOut(String type) {
		if (!known) {
			return null;
		}
		Pet pet = current;
		return pet != null && PetNames.isType(pet.name(), type);
	}

	/** Puts back the pet of the last session to show it, which says nothing yet about the pet out now. */
	public static void restoreLast(Pet pet) {
		boolean knownBefore = known;
		set(pet);
		known = knownBefore;
	}

	/** The server named the profile: its pet, as long as nothing newer says otherwise. */
	private static void profileNamed(String named) {
		if (named.equals(profile)) {
			return;
		}
		boolean first = profile == null;
		profile = named;
		if (first && known) {
			// the pet was read already, before the server said whose it is
			remember(named, current);
			return;
		}
		if (OF_PROFILE.containsKey(named)) {
			Pet pet = OF_PROFILE.get(named);
			current = pet;
			known = true;
			changes++;
		} else {
			known = false;
		}
	}

	private static void remember(String ofProfile, Pet pet) {
		if (ofProfile != null && (OF_PROFILE.size() < PROFILES_MOST || OF_PROFILE.containsKey(ofProfile))) {
			OF_PROFILE.put(ofProfile, pet == null ? null : new Pet(pet.name(), pet.level(), pet.color(), pet.heldItem(), ItemStack.EMPTY));
			changes++;
		}
	}

	/** @return the pet known of every profile, for the settings file */
	public static Map<String, Pet> ofProfiles() {
		return new HashMap<>(OF_PROFILE);
	}

	/** Takes in the pets of the settings file. */
	public static void restoreProfiles(Map<String, Pet> pets) {
		OF_PROFILE.putAll(pets);
	}

	/** @return a counter incremented whenever the active pet changes, to trigger saving */
	public static int changes() {
		return changes;
	}

	/** Sets the pet directly: from saved state, or for the preview. */
	public static void set(Pet pet) {
		if (pet == null || current == null || !pet.name().equalsIgnoreCase(current.name())) {
			// another pet: its experience is unknown until the widget shows it
			xpProgress = -1f;
			xpText = "";
		}
		if (pet != null && pet.icon() != null && !pet.icon().isEmpty()) {
			remember(pet);
		}
		current = pet;
		known = true;
		remember(profile, pet);
		if (pet != null) {
			lastOutKey = fullKey(pet);
		}
		changes++;
	}

	// ------------------------------------------------------------------------------------------------------ chat

	/** Called with every chat message from the server. */
	public static void onMessage(Component message) {
		// no server check: only SkyBlock sends these messages, and their content is what counts
		String text = message.getString().strip();
		String named = StorageStore.profileOf(text);
		if (named != null) {
			profileNamed(named);
			return;
		}
		String lower = text.toLowerCase(Locale.ROOT);
		if (!lower.contains("your") && !lower.contains("leveled up")) {
			return;
		}
		Matcher matcher = SUMMONED.matcher(text);
		if (matcher.find()) {
			lastPetText = text + "  [summon]";
			SkyBlock.evidence(120_000);
			String name = cleanName(matcher.group(1));
			int color = colorOf(message, name);
			Pet known = lookup(name, color, 0, "", matcher.group(0).contains("✦"), false);
			set(known != null ? new Pet(known.name(), known.level(), color != 0 ? color : known.color(), known.heldItem(), known.icon())
				: new Pet(name, 0, color != 0 ? color : 0xFFFFFF, "", ItemStack.EMPTY));
			return;
		}
		if (DESPAWNED.matcher(text).find()) {
			lastPetText = text + "  [despawn]";
			SkyBlock.evidence(120_000);
			set(null);
			return;
		}
		matcher = AUTOPET.matcher(text);
		if (matcher.find()) {
			lastPetText = text + "  [autopet]";
			SkyBlock.evidence(120_000);
			String name = cleanName(matcher.group(2));
			int color = colorOf(message, name);
			// the held item stated by the autopet rule is in the message's hover text
			String held = heldItemOf(message);
			int said = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) : 0;
			Pet known = lookup(name, color, said, held, matcher.group(2).contains("✦") || matcher.group(0).contains("✦"), true);
			int level = said > 0 ? said : known != null ? known.level() : 0;
			if (known != null) {
				set(new Pet(known.name(), level, color != 0 ? color : known.color(), held.isEmpty() ? known.heldItem() : held, known.icon()));
			} else {
				set(new Pet(name, level, color != 0 ? color : 0xFFFFFF, held, ItemStack.EMPTY));
			}
			return;
		}
		matcher = LEVEL_UP.matcher(text);
		if (matcher.find() && current != null && baseName(current.name()).equalsIgnoreCase(baseName(matcher.group(1)))) {
			Pet leveled = current.withLevel(Integer.parseInt(matcher.group(2)));
			ALL.remove(fullKey(current)); // the pet is no longer at its old level
			ENCODED.remove(fullKey(current));
			remember(leveled);
			lastOutKey = fullKey(leveled);
			current = leveled;
			changes++;
		}
	}

	/** @return a pet name from a message, without skin stars and surrounding whitespace */
	private static String cleanName(String name) {
		// A skin can prefix its own counter, such as "[324✦] Golden Dragon": it is kept, since it distinguishes two pets of the same kind. Only a
		// trailing star (skin) and a leading star (favorite) are removed.
		String stripped = TRAILING_STARS.matcher(name.strip()).replaceAll("");
		return LEADING_STAR.matcher(stripped).replaceAll("").strip();
	}

	/** @return the pet kind without a leading skin counter: "Golden Dragon" */
	public static String baseName(String name) {
		return STARS.matcher(SKIN_COUNTERS.matcher(name).replaceAll("")).replaceAll("").strip();
	}

	/** A skin's stars at the end of a pet name, a favorite's star in front of it, any star, and the skin counters in front ("[324✦] "). */
	private static final Pattern TRAILING_STARS = Pattern.compile("\\s*[✦⭐★]+$");
	private static final Pattern LEADING_STAR = Pattern.compile("^[⭐★]\\s*");
	private static final Pattern STARS = Pattern.compile("[✦⭐★]");
	private static final Pattern SKIN_COUNTERS = Pattern.compile("^(?:\\s*\\[[^\\]]*]\\s*)+");

	private static volatile String lastPetText = "";

	/** @return the last understood pet message, for the debug command */
	public static String lastPetText() {
		return lastPetText.isEmpty() ? "none yet" : lastPetText;
	}

	/** @return the held item stated by an autopet rule, from the message's hover text, or an empty string */
	private static String heldItemOf(Component message) {
		String[] found = {""};
		message.visit((style, part) -> {
			if (found[0].isEmpty() && style.getHoverEvent() instanceof HoverEvent.ShowText show) {
				for (String line : show.value().getString().split("\\n")) {
					String plain = line.strip();
					if (plain.toLowerCase(Locale.ROOT).startsWith("held item:")) {
						found[0] = plain.substring("held item:".length()).strip();
					}
				}
			}
			return Optional.<Object>empty();
		}, Style.EMPTY);
		String item = found[0];
		return item.equalsIgnoreCase("none") ? "" : item;
	}

	/** @return the color of the message part containing the pet name, which is its rarity color */
	private static int colorOf(Component message, String fullName) {
		String name = baseName(fullName);
		int[] found = {0};
		message.visit((style, part) -> {
			if (found[0] == 0 && part.contains(name) && style.getColor() != null) {
				found[0] = style.getColor().getValue();
			}
			return Optional.<Object>empty();
		}, Style.EMPTY);
		return found[0];
	}

	// ----------------------------------------------------------------------------------------------------- menu and world

	private static final Pattern TAB_PET = Pattern.compile("^\\[Lvl (\\d+)] (.+?)(?: ✦)?$");
	private static long lastTab;
	/** The widget line under the pet: its experience toward the next level ("1,243,580/1,822,050 XP (68.3%)"), or that it is at its top level. */
	private static final Pattern TAB_XP = Pattern.compile("^([\\d,.]{1,16}[kKmMbB]?)/([\\d,.]{1,16}[kKmMbB]?) XP \\((\\d{1,3}(?:\\.\\d{1,3})?)%\\)$");
	private static final String TAB_MAX = "MAX LEVEL";
	/** The pet's experience toward its next level, from 0 to 1, or -1 when unknown; and as the widget writes it ("1.24M / 1.82M XP"). */
	private static float xpProgress = -1f;
	private static String xpText = "";

	/** @return the active pet's experience toward its next level from the player list widget, from 0 to 1 (1 at its top level), or -1 */
	public static float xpProgress() {
		return xpProgress;
	}

	/** @return the active pet's experience as the widget writes it ("1,243,580 / 1,822,050 XP"), or "" */
	public static String xpText() {
		return xpText;
	}

	/** Reads the widget line under the pet: its experience, or that it is at its top level. Untrusted text: the percent is bounded. */
	private static void readXp(String text) {
		if (text.toUpperCase(Locale.ROOT).contains(TAB_MAX)) {
			xpProgress = 1f;
			xpText = "Max level";
			return;
		}
		Matcher matcher = TAB_XP.matcher(text);
		if (matcher.matches()) {
			try {
				xpProgress = Math.max(0f, Math.min(1f, Float.parseFloat(matcher.group(3)) / 100f));
				xpText = matcher.group(1) + " / " + matcher.group(2) + " XP";
			} catch (NumberFormatException e) {
				xpProgress = -1f;
			}
		}
	}

	/**
	 * SkyBlock can show the active pet in a player list widget (enabled via /widgets: a "Pet:" line followed by level and name). It is read from
	 * there, since the game already has the list: nothing is requested from the server. It is the only source that is always correct, including
	 * after a level change and for a pet that was active before the game started.
	 */
	private static void scanTabList(Minecraft client) {
		long now = System.currentTimeMillis();
		if (now - lastTab < 1000 || client.getConnection() == null) {
			return;
		}
		lastTab = now;
		List<PlayerInfo> infos = new ArrayList<>(client.getConnection().getListedOnlinePlayers());
		infos.sort(Comparator.<PlayerInfo>comparingInt(info -> info.getGameMode() == GameType.SPECTATOR ? 1 : 0)
			.thenComparingInt(info -> -info.getTabListOrder())
			.thenComparing(info -> info.getTeam() == null ? "" : info.getTeam().getName())
			.thenComparing(info -> info.getProfile().name(), String.CASE_INSENSITIVE_ORDER));
		boolean afterHeader = false;
		boolean afterPet = false;
		for (var info : infos) {
			Component shown = client.gui.hud.getTabList().getNameForDisplay(info);
			String text = shown.getString().strip();
			if (text.equalsIgnoreCase("Pet:")) {
				afterHeader = true;
				continue;
			}
			if (!afterHeader || text.isEmpty()) {
				continue;
			}
			if (afterPet) {
				// the line right under the pet: its experience, when the widget shows it
				readXp(text);
				return;
			}
			Matcher matcher = TAB_PET.matcher(text);
			if (matcher.matches()) {
				String name = cleanName(matcher.group(2));
				int level = Integer.parseInt(matcher.group(1));
				SkyBlock.evidence(60_000);
				if (current == null || !current.name().equalsIgnoreCase(name) || current.level() != level || (!current.icon().isEmpty() && hasSkin(current) != text.contains("✦"))) {
					int color = colorOf(shown, name);
					Pet known = lookup(name, color, level, "", text.contains("✦"), true);
					set(known != null ? new Pet(known.name(), level, color != 0 ? color : known.color(), known.heldItem(), known.icon())
						: new Pet(name, level, color != 0 ? color : 0xFFFFFF, "", ItemStack.EMPTY));
				}
				afterPet = true;
				continue;
			}
			if (text.toLowerCase(Locale.ROOT).startsWith("no pet")) {
				if (current != null) {
					set(null);
				}
				return;
			}
			if (text.endsWith(":")) {
				return; // start of the next widget
			}
		}
	}

	private static final Pattern NAME_TAG = Pattern.compile("\\[Lvl\\s*(\\d+)]\\s*(.+?)'s\\s+(.+)$");
	private static long lastTag;

	/**
	 * The active pet floats next to its owner with a name tag: "[Lvl 100] Owner's Golden Dragon". When the player list has no pet widget (it must be
	 * enabled via /widgets, which most players have not done) and no message was seen (the pet was active before the game started), this identifies
	 * it. Only name tags containing the player's name count.
	 */
	private static void scanNameTags(Minecraft client) {
		long now = System.currentTimeMillis();
		if (now - lastTag < 1500 || client.player == null || client.level == null || !SkyBlock.inSkyBlock()) {
			return;
		}
		lastTag = now;
		String owner = client.player.getName().getString();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!entity.hasCustomName() || entity.distanceToSqr(client.player) > 20.0 * 20.0) {
				continue;
			}
			Component tag = entity.getCustomName();
			Matcher matcher = NAME_TAG.matcher(tag.getString().strip());
			if (!matcher.find() || !matcher.group(2).strip().equalsIgnoreCase(owner)) {
				continue;
			}
			String name = cleanName(matcher.group(3));
			int level = Integer.parseInt(matcher.group(1));
			if (current == null || !current.name().equalsIgnoreCase(name) || current.level() != level) {
				int color = colorOf(tag, name);
				Pet known = lookup(name, color, level, "", matcher.group(3).contains("✦"), true);
				set(known != null ? new Pet(known.name(), level, color != 0 ? color : known.color(), known.heldItem(), known.icon())
					: new Pet(name, level, color != 0 ? color : 0xFFFFFF, "", ItemStack.EMPTY));
				lastPetText = tag.getString().strip() + "  [name tag]";
			}
			return;
		}
	}

	/** Whether the pet is needed right now (the pet panel or the table's reminder is on); otherwise the player list and name tags are not scanned. */
	private static BooleanSupplier wanted = () -> true;

	public static void wantedWhen(BooleanSupplier condition) {
		wanted = condition;
	}

	private static void tick(Minecraft client) {
		if (!wanted.getAsBoolean() || !SkyBlock.inSkyBlock()) {
			return;
		}
		if (client.level != null) {
			scanTabList(client);
			if (current == null) {
				scanNameTags(client);
			}
		}
		if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen) || client.level == null || !PETS_MENU.matcher(screen.getTitle().getString()).matches()) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - lastScan < 400) {
			return;
		}
		lastScan = now;
		var slots = screen.getMenu().slots;
		// container part only: the last 36 slots are the player inventory
		int chest = Math.max(0, slots.size() - 36);
		Pet active = null;
		for (int i = 0; i < chest; i++) {
			Slot slot = slots.get(i);
			Pet pet = read(slot.getItem());
			if (pet == null) {
				continue;
			}
			PetIcons.seen(slot.getItem());
			remember(pet);
			if (isEquipped(slot.getItem())) {
				active = pet;
			}
		}
		if (active != null || SEEN.size() > 0) {
			SkyBlock.evidence(120_000);
		}
		if (active != null && (current == null || !fullKey(current).equals(fullKey(active)) || current.icon().isEmpty())) {
			current = active;
			known = true;
			remember(profile, active);
			changes++;
		}
	}

	/** @return whether a pet item of the Pets menu is the equipped one: its data says so, or its lore offers to despawn it */
	public static boolean isEquipped(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data != null) {
			JsonObject info = petInfo(data);
			if (info != null && info.has("active") && info.get("active").isJsonPrimitive() && info.get("active").getAsBoolean()) {
				return true;
			}
		}
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			for (Component line : lore.lines()) {
				if (line.getString().toLowerCase(Locale.ROOT).contains("click to despawn")) {
					return true;
				}
			}
		}
		return false;
	}

	private static JsonObject petInfo(CustomData data) {
		Optional<String> raw = data.copyTag().getString("petInfo");
		if (raw.isEmpty()) {
			return null;
		}
		try {
			return JsonParser.parseString(raw.get()).getAsJsonObject();
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** @return the pet represented by a menu item, or null if it is not one */
	private static Pet read(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		JsonObject info = data == null ? null : petInfo(data);
		if (info == null) {
			return null;
		}
		String type = info.has("type") ? info.get("type").getAsString() : "";
		Matcher matcher = HOVER_NAME.matcher(stack.getHoverName().getString().strip());
		int level = 0;
		String name = pretty(type);
		if (matcher.matches()) {
			level = Integer.parseInt(matcher.group(1));
			name = cleanName(matcher.group(2));
		}
		String held = info.has("heldItem") && info.get("heldItem").isJsonPrimitive() ? pretty(info.get("heldItem").getAsString().replace("PET_ITEM_", "")) : "";
		if (held.equalsIgnoreCase("none") || held.equalsIgnoreCase("null")) {
			held = "";
		}
		int color = rarityColor(info.has("tier") ? info.get("tier").getAsString() : "");
		return new Pet(name, level, color, held, stack.copyWithCount(1));
	}

	private static String pretty(String id) {
		StringBuilder out = new StringBuilder();
		for (String word : id.toLowerCase(Locale.ROOT).split("_")) {
			if (!word.isEmpty()) {
				out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			}
		}
		return out.toString();
	}

	public static int rarityColor(String tier) {
		return switch (tier.toUpperCase(Locale.ROOT)) {
			case "UNCOMMON" -> 0x55FF55;
			case "RARE" -> 0x5555FF;
			case "EPIC" -> 0xAA00AA;
			case "LEGENDARY" -> 0xFFAA00;
			case "MYTHIC" -> 0xFF55FF;
			case "DIVINE" -> 0x55FFFF;
			default -> 0xFFFFFF;
		};
	}

	/** @return the rarity of a pet by its rarity color, as SkyBlock writes it ("LEGENDARY"), or "" for white (common, or unknown) */
	public static String rarityName(int color) {
		return switch (color & 0xFFFFFF) {
			case 0x55FF55 -> "UNCOMMON";
			case 0x5555FF -> "RARE";
			case 0xAA00AA -> "EPIC";
			case 0xFFAA00 -> "LEGENDARY";
			case 0xFF55FF -> "MYTHIC";
			case 0x55FFFF -> "DIVINE";
			default -> "";
		};
	}

	// ----------------------------------------------------------------------------------------------- persistence

	/** @return the item serialized as text, so the pet head can be shown again after a restart, or an empty string */
	public static String encodeIcon(ItemStack stack) {
		Minecraft client = Minecraft.getInstance();
		if (stack == null || stack.isEmpty() || client.level == null) {
			return "";
		}
		try {
			RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, client.level.registryAccess());
			return ItemStack.CODEC.encodeStart(ops, stack).result().map(Tag::toString).orElse("");
		} catch (RuntimeException e) {
			Debug.log("Could not keep the head of a pet", e);
			return "";
		}
	}

	/** @return the item deserialized from text, or an empty stack if it cannot be read yet */
	public static ItemStack decodeIcon(String text) {
		Minecraft client = Minecraft.getInstance();
		if (text == null || text.isEmpty() || client.level == null) {
			return ItemStack.EMPTY;
		}
		try {
			RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, client.level.registryAccess());
			return ItemStack.CODEC.parse(ops, TagParser.parseCompoundFully(text)).result().orElse(ItemStack.EMPTY);
		} catch (Exception e) {
			return ItemStack.EMPTY;
		}
	}
}
