package dev.tim1337.lumio.skyblock.hud;

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
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.ui.Toasts;
import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;

import dev.tim1337.lumio.skyblock.data.SkullTextures;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hypixel SkyBlock deployables the player is in range of: power orbs (Radiant, Mana Flux, Overflux, Plasmaflux), flares, the Umberella, the mining
 * lanterns and the Black Hole. Each shows its icon in a ring that depletes over its duration, with the remaining seconds. They are read from
 * what the game renders (the name above an orb, the head of a flare); nothing is requested from the server.
 */
public class DeployableHudFeature extends AddonPanel {
	private enum Kind {
		RADIANT("Radiant", "Orb", 1, 18, 0x55FF55),
		MANA_FLUX("Mana Flux", "Orb", 2, 18, 0x5555FF),
		OVERFLUX("Overflux", "Orb", 3, 18, 0xAA00AA),
		PLASMAFLUX("Plasmaflux", "Orb", 4, 20, 0xFF55FF),
		DWARVEN_LANTERN("Dwarven Lantern", "Lantern", 1, 30, 0xFFFFFF),
		MITHRIL_LANTERN("Mithril Lantern", "Lantern", 2, 30, 0x55FF55),
		TITANIUM_LANTERN("Titanium Lantern", "Lantern", 3, 30, 0x5555FF),
		GLACITE_LANTERN("Glacite Lantern", "Lantern", 4, 30, 0xAA00AA),
		WILL_O_WISP("Will-o'-wisp", "Lantern", 5, 30, 0xFFAA00),
		BLACK_HOLE("Black Hole", "Black Hole", 1, 20, 0xAA00AA),
		UMBERELLA("Umberella", "Umberella", 1, 30, 0x5555FF),
		WARNING_FLARE("Warning Flare", "Flare", 1, 40, 0x55FF55),
		ALERT_FLARE("Alert Flare", "Flare", 2, 40, 0x5555FF),
		SOS_FLARE("SOS Flare", "Flare", 3, 40, 0xAA00AA);

		final String title;
		/** Deployables of the same group do not stack: only the strongest counts and is shown. */
		final String group;
		final int tier;
		final int range;
		final int rgb;

		Kind(String title, String group, int tier, int range, int rgb) {
			this.title = title;
			this.group = group;
			this.tier = tier;
			this.range = range;
			this.rgb = rgb;
		}
	}

	/** The name above a deployable: its type and remaining seconds, such as "Overflux 42s". */
	private static final Pattern TIMED = Pattern.compile("^(Radiant|Mana Flux|Overflux|Plasmaflux|Dwarven Lantern|Mithril Lantern|Titanium Lantern|Glacite Lantern|Will-o'-wisp|Black Hole|Umberella) (\\d+)s$");
	/** Flare head textures as the game provides them. A flare lasts three minutes from placement. */
	private static final String FLARE_WARNING = "ewogICJ0aW1lc3RhbXAiIDogMTY2MjY4Mjg0NTU4NiwKICAicHJvZmlsZUlkIiA6ICIwODFiZTAxZmZlMmU0ODMyODI3MDIwMjBlNmI1M2ExNyIsCiAgInByb2ZpbGVOYW1lIiA6ICJMeXJpY1BsYXRlMjUyNDIiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjJlMmJmNmMxZWMzMzAyNDc5MjdiYTYzNDc5ZTU4NzJhYzY2YjA2OTAzYzg2YzgyYjUyZGFjOWYxYzk3MTQ1OCIsCiAgICAgICJtZXRhZGF0YSIgOiB7CiAgICAgICAgIm1vZGVsIiA6ICJzbGltIgogICAgICB9CiAgICB9CiAgfQp9";
	private static final String FLARE_ALERT = "ewogICJ0aW1lc3RhbXAiIDogMTcxOTg1MDQzMTY4MywKICAicHJvZmlsZUlkIiA6ICJmODg2ZDI3YjhjNzU0NjAyODYyYTM1M2NlYmYwZTgwZiIsCiAgInByb2ZpbGVOYW1lIiA6ICJOb2JpbkdaIiwKICAic2lnbmF0dXJlUmVxdWlyZWQiIDogdHJ1ZSwKICAidGV4dHVyZXMiIDogewogICAgIlNLSU4iIDogewogICAgICAidXJsIiA6ICJodHRwOi8vdGV4dHVyZXMubWluZWNyYWZ0Lm5ldC90ZXh0dXJlLzlkMmJmOTg2NDcyMGQ4N2ZkMDZiODRlZmE4MGI3OTVjNDhlZDUzOWIxNjUyM2MzYjFmMTk5MGI0MGMwMDNmNmIiLAogICAgICAibWV0YWRhdGEiIDogewogICAgICAgICJtb2RlbCIgOiAic2xpbSIKICAgICAgfQogICAgfQogIH0KfQ==";
	private static final String FLARE_SOS = "ewogICJ0aW1lc3RhbXAiIDogMTY2MjY4Mjc3NjUxNiwKICAicHJvZmlsZUlkIiA6ICI4YjgyM2E1YmU0Njk0YjhiOTE0NmE5MWRhMjk4ZTViNSIsCiAgInByb2ZpbGVOYW1lIiA6ICJTZXBoaXRpcyIsCiAgInNpZ25hdHVyZVJlcXVpcmVkIiA6IHRydWUsCiAgInRleHR1cmVzIiA6IHsKICAgICJTS0lOIiA6IHsKICAgICAgInVybCIgOiAiaHR0cDovL3RleHR1cmVzLm1pbmVjcmFmdC5uZXQvdGV4dHVyZS9jMDA2MmNjOThlYmRhNzJhNmE0Yjg5NzgzYWRjZWYyODE1YjQ4M2EwMWQ3M2VhODdiM2RmNzYwNzJhODlkMTNiIiwKICAgICAgIm1ldGFkYXRhIiA6IHsKICAgICAgICAibW9kZWwiIDogInNsaW0iCiAgICAgIH0KICAgIH0KICB9Cn0=";
	private static final int FLARE_SECONDS = 180;

	/**
	 * A deployable in range, or one of the player's own that the player left the range of: its type, end time, total duration, icon, and distance
	 * beyond its range (otherwise 0).
	 */
	private record Active(Kind kind, long endsAt, float total, ItemStack icon, double outBy) {
		Active(Kind kind, long endsAt, float total, ItemStack icon) {
			this(kind, endsAt, total, icon, 0);
		}

		boolean outOfRange() {
			return outBy > 0;
		}

		Active out(double distance) {
			return new Active(kind, endsAt, total, icon, Math.max(0.01, distance));
		}

		float secondsLeft(long now) {
			return Math.max(0f, (endsAt - now) / 1000f);
		}
	}

	private final Choice<PanelLooks.Deployable> look = add(make().choice("look", Component.literal("Style"), PanelLooks.Deployable.RING,
		PanelLooks.Deployable::displayName, Choice.Style.SEGMENTED), "General");
	private final Toggle showName = add(make().toggle("show_name", Component.literal("Name"), true), "General");
	private final Toggle onlyMine = add(make().toggle("only_mine", Component.literal("Only my own"), true), "General");
	private final Toggle wispRange = add(make().toggle("wisp_range", Component.literal("Out of range alert"), true), "Will-o'-wisp",
		"Not in a Mineshaft, where its range has no end");
	private final Toggle wispOut = add(make().toggle("wisp_out", Component.literal("Expired alert"), true), "Will-o'-wisp");
	private final Toggle wispSound = add(make().toggle("wisp_sound", Component.literal("Alert sound"), true), "Will-o'-wisp");

	// the player's Will-o'-wisp, tracked beyond its range too: distance, remaining seconds, and which notifications were shown
	private int wispId = -1;
	private int wispSeconds = -1;
	private long wispSeenAt;
	private boolean wispOutSaid;

	// ------------------------------------------------------------------------------------------------ ownership
	// SkyBlock does not mark a deployable's owner. So each use is remembered (type, position, time), and the deployable of that type appearing
	// there shortly after is the one just placed: its entity is remembered as the player's own.

	/** A deployable use: its group, the player's position, and the time. */
	private record Use(String group, double x, double y, double z, long at) {
	}

	private final List<Use> uses = new ArrayList<>();
	private final Set<Integer> mine = new HashSet<>();
	private Object mineLevel;

	/** @return the group of a deployable item, from its SkyBlock id or name, or null if it is not one */
	private static String groupOf(ItemStack stack) {
		String id = ItemGains.idOf(stack);
		String text = (id != null ? id : stack.getHoverName().getString()).toUpperCase(Locale.ROOT).replace(' ', '_');
		if (text.contains("POWER_ORB") || text.contains("RADIANT") || text.contains("MANA_FLUX") || text.contains("OVERFLUX") || text.contains("PLASMAFLUX")) {
			return "Orb";
		}
		if (text.contains("FLARE")) {
			return "Flare";
		}
		if (text.contains("LANTERN") || text.contains("WISP")) {
			return "Lantern";
		}
		if (text.contains("UMBERELLA")) {
			return "Umberella";
		}
		if (text.contains("BLACK_HOLE")) {
			return "Black Hole";
		}
		return null;
	}

	/** The player used the held item: if it is a deployable, remember where and when. */
	public void onUse(Player player, ItemStack stack) {
		if (player == null || stack.isEmpty()) {
			return;
		}
		String group = groupOf(stack);
		if (group != null) {
			uses.add(new Use(group, player.getX(), player.getY(), player.getZ(), System.currentTimeMillis()));
			if (uses.size() > 20) {
				uses.remove(0);
			}
		}
	}

	/** @return whether the deployable is the player's: already known as such, or new, of a recently used type, near where it was used */
	private boolean isMine(Entity stand, Kind kind, long now) {
		if (mine.contains(stand.getId())) {
			return true;
		}
		for (Use use : uses) {
			if (use.group().equals(kind.group) && now - use.at() < 5000 && stand.tickCount < 200
				&& stand.distanceToSqr(use.x(), use.y(), use.z()) < 12 * 12) {
				mine.add(stand.getId());
				return true;
			}
		}
		return false;
	}

	private List<Active> active = List.of();
	/** The highest seconds seen on each deployable, taken as its duration: the ring starts full. */
	private final Map<Integer, Integer> longest = new HashMap<>();
	private long lastScan;

	public DeployableHudFeature() {
		super("deployables", "Deployables", "The power orbs, flares, Umberella, lanterns and Black Hole you are in range of, with the time they have left.", true,
			Anchor.LEFT, false, 12f, 170f);
		iconRgb(SystemColors.PURPLE_RGB);
		iconName("orb");
		summary("Power orbs and flares in range, and their time left");
		offerNoBackground();
		offerCardSize();
		offerModes(look);
		offerHeader();
	}

	@Override
	public void prepare(Minecraft client) {
		long now = System.currentTimeMillis();
		if (now - lastScan < 500 || client.level == null || client.player == null) {
			return;
		}
		lastScan = now;
		if (!SkyBlock.allowed()) {
			// no deployables exist outside SkyBlock, and the panel is hidden there: skip the entity scan, but let a tracked Will-o'-wisp expire as before
			active = List.of();
			watchWisp(client, false, 0, now);
			return;
		}
		if (client.level != mineLevel) {
			// entity ids are only valid within one level
			mineLevel = client.level;
			mine.clear();
		}
		uses.removeIf(use -> now - use.at() > 10_000);
		Map<String, Active> best = new HashMap<>();
		boolean wispFound = false;
		double wispDistance = 0;
		// stands wearing a head, collected once per scan when a deployable without its own head needs an icon
		List<ArmorStand> heads = null;
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof ArmorStand stand)) {
				continue;
			}
			double distance = stand.distanceTo(client.player);
			// the own Will-o'-wisp is tracked beyond its range, to detect when the player leaves it
			if (distance <= 120 && stand.hasCustomName() && stand.getCustomName() != null) {
				Matcher wisp = TIMED.matcher(stand.getCustomName().getString().strip());
				if (wisp.matches() && wisp.group(1).equals("Will-o'-wisp") && (!onlyMine.get() || isMine(stand, Kind.WILL_O_WISP, now))) {
					wispFound = true;
					wispDistance = distance;
					wispId = stand.getId();
					wispSeconds = Integer.parseInt(wisp.group(2));
					wispSeenAt = now;
				}
			}
			// beyond any range: only the player's own deployables, to report being out of range
			if (distance > 100) {
				continue;
			}
			Active found = null;
			String name = stand.hasCustomName() && stand.getCustomName() != null ? stand.getCustomName().getString().strip() : "";
			Matcher matcher = name.isEmpty() ? null : TIMED.matcher(name);
			if (matcher != null && matcher.matches()) {
				Kind kind = kindOf(matcher.group(1));
				int seconds = Integer.parseInt(matcher.group(2));
				if (kind != null) {
					int most = longest.merge(stand.getId(), seconds, Math::max);
					if (heads == null && stand.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
						heads = headStands(client);
					}
					found = new Active(kind, now + seconds * 1000L, most, iconNear(stand, heads));
					if (distance > kind.range) {
						found = found.out(distance);
					}
				}
			} else {
				Kind flare = flareOf(stand.getItemBySlot(EquipmentSlot.HEAD));
				if (flare != null && stand.tickCount < FLARE_SECONDS * 20) {
					long left = (FLARE_SECONDS * 20L - stand.tickCount) * 50L;
					found = new Active(flare, now + left, FLARE_SECONDS, stand.getItemBySlot(EquipmentSlot.HEAD).copy());
					if (distance > flare.range) {
						found = found.out(distance);
					}
				}
			}
			// others' deployables only while in range; the player's own also out of range, to report it
			if (found != null && (onlyMine.get() || found.outOfRange()) && !isMine(stand, found.kind(), now)) {
				found = null;
			}
			if (found != null) {
				// within a group, in range beats out of range; then only the strongest counts, and of equal ones the longest lasting
				Active before = best.get(found.kind().group);
				if (before == null || before.outOfRange() && !found.outOfRange()
					|| before.outOfRange() == found.outOfRange() && (found.kind().tier > before.kind().tier || (found.kind() == before.kind() && found.endsAt() > before.endsAt()))) {
					best.put(found.kind().group, found);
				}
			}
		}
		if (longest.size() > 200) {
			longest.clear();
		}
		List<Active> list = new ArrayList<>(best.values());
		list.sort((a, b) -> Long.compare(a.endsAt(), b.endsAt()));
		active = list;
		watchWisp(client, wispFound, wispDistance, now);
	}

	/**
	 * Will-o'-wisp notifications: when the player leaves its range (not in a Mineshaft, where it reaches everywhere), once until they return, and
	 * when it expires, i.e. disappears with its last seconds remaining.
	 */
	private void watchWisp(Minecraft client, boolean found, double distance, long now) {
		if (wispId < 0) {
			return;
		}
		if (found) {
			boolean mineshaft = inMineshaft();
			boolean outside = !mineshaft && distance > Kind.WILL_O_WISP.range;
			if (outside && !wispOutSaid && wispRange.get() && wispSeconds > 2) {
				wispOutSaid = true;
				notice(client, "Will-o'-wisp", "You are out of its range (" + Math.round(distance) + " of " + Kind.WILL_O_WISP.range + " blocks)");
			} else if (!outside) {
				wispOutSaid = false;
			}
			return;
		}
		// gone: with its last seconds remaining it expired; gone with time left means it is only out of view, or the world changed
		if (now - wispSeenAt > 1500) {
			if (wispSeconds >= 0 && wispSeconds <= 3 && wispOut.get()) {
				notice(client, "Will-o'-wisp", "It ran out");
			}
			wispId = -1;
			wispSeconds = -1;
			wispOutSaid = false;
		}
	}

	/** @return whether the player is in a Glacite Mineshaft, where the Will-o'-wisp's range is unlimited */
	private static boolean inMineshaft() {
		String island = SkyBlockArea.island().toLowerCase(Locale.ROOT);
		String zone = SkyBlockArea.zone().toLowerCase(Locale.ROOT);
		return island.contains("mineshaft") || zone.contains("mineshaft");
	}

	private void notice(Minecraft client, String title, String text) {
		Toasts.info(title, text);
		if (wispSound.get()) {
			client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.4f, 0.8f));
		}
	}

	private static Kind kindOf(String title) {
		for (Kind kind : Kind.values()) {
			if (kind.title.equals(title)) {
				return kind;
			}
		}
		return null;
	}

	private static final String SOS_TEXTURE = SkullTextures.ofProperty(FLARE_SOS);
	private static final String ALERT_TEXTURE = SkullTextures.ofProperty(FLARE_ALERT);
	private static final String WARNING_TEXTURE = SkullTextures.ofProperty(FLARE_WARNING);

	private static Kind flareOf(ItemStack head) {
		if (head.isEmpty() || !head.is(Items.PLAYER_HEAD)) {
			return null;
		}
		var profile = head.get(DataComponents.PROFILE);
		if (profile == null) {
			return null;
		}
		for (var property : profile.partialProfile().properties().get("textures")) {
			String texture = SkullTextures.ofProperty(property.value());
			if (texture.equals(SOS_TEXTURE)) {
				return Kind.SOS_FLARE;
			}
			if (texture.equals(ALERT_TEXTURE)) {
				return Kind.ALERT_FLARE;
			}
			if (texture.equals(WARNING_TEXTURE)) {
				return Kind.WARNING_FLARE;
			}
		}
		return null;
	}
	/** @return the deployable's head, on an armor stand at the named stand's position: its icon */
	private static ItemStack iconNear(ArmorStand named, List<ArmorStand> heads) {
		ItemStack own = named.getItemBySlot(EquipmentSlot.HEAD);
		if (!own.isEmpty()) {
			return own.copy();
		}
		ItemStack found = ItemStack.EMPTY;
		double nearest = 4.0;
		for (ArmorStand stand : heads) {
			double d = stand.distanceToSqr(named);
			if (stand != named && d < nearest) {
				nearest = d;
				found = stand.getItemBySlot(EquipmentSlot.HEAD);
			}
		}
		return found.copy();
	}

	/** @return the armor stands in the world that wear a head, in the level's order */
	private static List<ArmorStand> headStands(Minecraft client) {
		List<ArmorStand> heads = new ArrayList<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof ArmorStand stand && !stand.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
				heads.add(stand);
			}
		}
		return heads;
	}

	private static ItemStack fallbackIcon(Kind kind) {
		return switch (kind.group) {
			case "Lantern" -> new ItemStack(Items.LANTERN);
			case "Black Hole" -> new ItemStack(Items.ENDER_EYE);
			case "Umberella" -> new ItemStack(Items.STICK);
			case "Flare" -> new ItemStack(Items.FIREWORK_ROCKET);
			default -> new ItemStack(Items.HEART_OF_THE_SEA);
		};
	}

	private List<Active> shownList() {
		if (active.isEmpty() && (HudEditor.isOpen() || simulated())) {
			long now = System.currentTimeMillis();
			return List.of(new Active(Kind.PLASMAFLUX, now + 42_000L, 60f, ItemStack.EMPTY), new Active(Kind.SOS_FLARE, now + 95_000L, FLARE_SECONDS, ItemStack.EMPTY));
		}
		return active;
	}

	@Override
	public boolean shown(Minecraft client) {
		long now = System.currentTimeMillis();
		return active.stream().anyMatch(a -> a.endsAt() > now) && (SkyBlock.allowed());
	}

	/** @return the small line under the name when the player left its range: how far out, or "" */
	private static String outLine(Active entry) {
		return entry.outOfRange() ? "Out of range · " + Math.round(entry.outBy()) + "m" : "";
	}

	private static final int OUT_COLOR = SystemColors.RED;
	/** The most deployables listed: more than three at once is rare, and the panel stays small. */
	private static final int MOST_SHOWN = 3;
	/** The ring, the gap between the entries of the card and between a ring and its text. */
	private static final int RING = 33;
	private static final float RING_THICKNESS = 2f;
	private static final int ENTRY_GAP = 8;
	private static final int BESIDE = 7;

	private boolean simple() {
		return look.get() == PanelLooks.Deployable.SIMPLE;
	}

	private int entries() {
		return Math.max(1, Math.min(MOST_SHOWN, shownList().size()));
	}

	@Override
	public int panelWidth() {
		return cardWidth();
	}

	@Override
	public int panelHeight() {
		int count = entries();
		return simple() ? PanelKit.simpleHeight(count) : contentTop() + count * RING + (count - 1) * ENTRY_GAP + PanelKit.BOTTOM;
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		List<Active> list = shownList();
		int width = panelWidth();
		long now = System.currentTimeMillis();
		int count = Math.min(MOST_SHOWN, list.size());
		if (simple()) {
			for (int i = 0; i < count; i++) {
				Active entry = list.get(i);
				float left = entry.secondsLeft(now);
				float fraction = entry.total() <= 0f ? 0f : Math.min(1f, left / entry.total());
				int color = entry.outOfRange() ? OUT_COLOR : 0xFF000000 | entry.kind().rgb;
				PanelKit.simpleRow(ui, width, i * (PanelKit.SIMPLE_ROW + PanelKit.SIMPLE_GAP), hasBackground(), "sun", PanelKit.TILE_DEPLOYABLE,
					entry.kind().title, fraction, color, PanelKit.clock((long) Math.ceil(left)), entry.outOfRange() ? OUT_COLOR : 0xFFFFFFFF);
			}
			return;
		}
		drawGlass(ui, width, panelHeight(), PanelKit.RADIUS);
		boolean out = false;
		for (int i = 0; i < count; i++) {
			out |= list.get(i).outOfRange();
		}
		familyHeader(ui, width, "sun", PanelKit.TILE_DEPLOYABLE, "Deployable", out ? "Out of range" : "In range", out ? OUT_COLOR : 0, false);
		for (int i = 0; i < count; i++) {
			drawEntry(ui, graphics, list.get(i), contentTop() + i * (RING + ENTRY_GAP), width, now);
		}
	}

	/** Draws one deployable: the ring with the time left in it, and its name beside it (and how far out of range the player is). */
	private void drawEntry(Canvas ui, GuiGraphicsExtractor graphics, Active entry, int top, int width, long now) {
		float left = entry.secondsLeft(now);
		float fraction = entry.total() <= 0f ? 0f : Math.min(1f, left / entry.total());
		int color = 0xFF000000 | entry.kind().rgb;
		float centerX = PanelKit.PAD + RING / 2f;
		float centerY = top + RING / 2f;
		// out of range: the ring turns red and a red light pulses slowly behind it, so it is noticed at a glance
		float breath = 0.5f + 0.5f * (float) Math.sin(now / 1000.0 * Math.PI * 1.2);
		if (entry.outOfRange()) {
			color = Draw.mix(color, OUT_COLOR, 0.85f);
			ui.softLight(centerX, centerY, RING * 0.8f, Draw.withOpacity(OUT_COLOR, 0.18f + 0.22f * breath));
		}
		PanelKit.ring(graphics, ui, centerX, centerY, RING, RING_THICKNESS, fraction, color, 0f);
		String time = PanelKit.clock((long) Math.ceil(left));
		int timeColor = entry.outOfRange() || left < 10f ? PanelKit.RED_TEXT : HudTheme.text();
		float timeWidth = ui.boldWidthScaled(time, TextScale.BODY);
		ui.textAtScaledBold(time, centerX - timeWidth / 2f, centerY - 6f, TextScale.BODY, PanelKit.readable(timeColor));
		float leftWidth = ui.widthScaled("left", PanelKit.SMALL);
		ui.textAtScaled("left", centerX - leftWidth / 2f, centerY + 3f, PanelKit.SMALL, HudTheme.secondary());
		float x = PanelKit.PAD + RING + BESIDE;
		float room = width - PanelKit.PAD - x;
		// the deployable's item beside its name, as the game shows it
		ItemStack icon = entry.icon().isEmpty() ? fallbackIcon(entry.kind()) : entry.icon();
		boolean editing = HudEditor.isOpen();
		String outLine = outLine(entry);
		float nameY = outLine.isEmpty() ? centerY - 4f : centerY - 9f;
		if (showName.get() || editing) {
			float before = Draw.alpha();
			Draw.setAlpha(before * HudEditor.partAlpha(showName.get()));
			var pose = graphics.pose();
			pose.pushMatrix();
			pose.translate(x, nameY - 2f);
			pose.scale(ITEM_SCALE, ITEM_SCALE);
			graphics.item(icon, 0, 0);
			pose.popMatrix();
			ui.textAtScaledBold(ui.fit(entry.kind().title, Math.round(room - ITEM_ROOM)), x + ITEM_ROOM, nameY, TextScale.BODY, HudTheme.text());
			Draw.setAlpha(before);
			part("Name", showName, x, nameY - 2f, room, 11f);
		}
		if (!outLine.isEmpty()) {
			ui.textAtScaled(ui.fit(outLine, Math.round(room / PanelKit.SMALL)), x, centerY + 3f, PanelKit.SMALL,
				Draw.withOpacity(PanelKit.readable(OUT_COLOR), 0.95f));
		}
	}

	/** The deployable's item beside its name: drawn at this size, with room after it. */
	private static final float ITEM_SCALE = 0.7f;
	private static final float ITEM_ROOM = 15f;
}
