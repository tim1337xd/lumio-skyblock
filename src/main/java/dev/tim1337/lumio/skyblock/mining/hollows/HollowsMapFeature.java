package dev.tim1337.lumio.skyblock.mining.hollows;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;

import dev.tim1337.lumio.api.LumioChat;
import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.HudTheme;
import dev.tim1337.lumio.api.experimental.draw.PanelKit;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.ui.SettingRows;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.api.experimental.util.ModFiles;
import dev.tim1337.lumio.api.experimental.util.Threads;
import dev.tim1337.lumio.api.experimental.util.WorldLabels;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.feature.KeyPlace;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.ColorChoice;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockAddon;
import dev.tim1337.lumio.skyblock.data.ChatGuard;
import dev.tim1337.lumio.skyblock.data.LobbyId;
import dev.tim1337.lumio.skyblock.data.PartyList;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;
import dev.tim1337.lumio.skyblock.dungeon.AnnouncementQueue;
import dev.tim1337.lumio.skyblock.hud.MapMarker;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadLocalRandom;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

/**
 * The Crystal Hollows map: the whole island as a square HUD element with its four quadrants and the Crystal Nucleus, the player (and party members
 * the game already shows) as arrows, and the places found so far in this lobby. A place is found by the player's own visit (the scoreboard's area,
 * a line of its character, its crystal) or from coordinates players share in chat; nothing is scanned and nothing comes from elsewhere. The
 * places are kept per lobby; a key or a click in the settings shares one, one message per press, through the send queue.
 */
public class HollowsMapFeature extends AddonPanel {
	/** Where a shared place goes. */
	public enum ShareTo {
		PARTY("Party", "pc "), ALL("All", "ac ");

		final String label;
		final String command;

		ShareTo(String label, String command) {
			this.label = label;
			this.command = command;
		}
	}

	private static final String FILE = "crystal_hollows_map.json";
	private static final String HOLLOWS = "Crystal Hollows";
	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SkyBlockAddon.ID, "mining"));
	/** The map's side at size 1, in GUI pixels. */
	private static final int MAP_BASE = 120;
	private static final int PAD = PanelKit.PAD;
	private static final int COORDS_LINE = 11;
	private static final float QUADRANT_OPACITY = 0.32f;
	private static final float NUCLEUS_OPACITY = 0.55f;
	private static final float LABEL_SCALE = TextScale.SMALL * 0.62f;
	private static final float NAME_SCALE = TextScale.SMALL * 0.62f;
	private static final int SPOT = 4;
	private static final int SPOT_EDGE = 0xE0101012;
	private static final float PARTY_ARROW = 0.7f;
	private static final int PARTY_RGB = 0x5EE0D0;
	/** How often the scoreboard is looked at, in client ticks, and how often the stay in a lobby is written down, in milliseconds. */
	private static final int READ_TICKS = 20;
	private static final long SEEN_SAVE_MILLIS = 10 * 60 * 1000;
	/** The same place offered from chat again within this time is not offered twice, in milliseconds. */
	private static final long OFFER_AGAIN_MILLIS = 30_000;
	private static final ExecutorService WORKER = Threads.daemonExecutor("Lumio Crystal Hollows map");
	/** The editor's sample: a few places and the player. */
	private static final Map<HollowsStructure, HollowsLobbies.Spot> SAMPLE = sample();

	/** Its own range, narrower than the panels' (0.5 to 2): a map smaller than this has unreadable rooms, a larger one covers the view. */
	private final Slider size = add(make().slider("size", Component.literal("Size"), 1f, 0.6f, 1.6f, 2).percent(), "Map");
	private final Slider opacity = add(make().slider("opacity", Component.literal("Opacity"), 0.9f, 0.3f, 1f, 2), "Map");
	private final Toggle showNames = add(make().toggle("show_names", Component.literal("Show names"), true), "Map");
	private final Toggle quadrantLabels = add(make().toggle("quadrant_labels", Component.literal("Show quadrant labels"), true), "Map");
	private final Toggle myCoords = add(make().toggle("my_coords", Component.literal("Show my coordinates"), false), "Map");
	private final Toggle party = add(make().toggle("party", Component.literal("Party members"), false), "Map", "Only those the game shows near you");
	private final Toggle waypoints = add(make().toggle("waypoints", Component.literal("Waypoints"), false), "Waypoints");
	private final Choice<ShareTo> shareTo = add(make().choice("share_to", Component.literal("Share to"), ShareTo.PARTY, s -> Component.literal(s.label)),
		"Sharing");
	private final Map<HollowsStructure, ColorChoice> colors = new EnumMap<>(HollowsStructure.class);

	private final HollowsLobbies lobbies = new HollowsLobbies();
	private final HollowsWaypoints world = new HollowsWaypoints(this);
	private final AnnouncementQueue queue = new AnnouncementQueue(System::currentTimeMillis, span -> ThreadLocalRandom.current().nextLong(span + 1),
		AnnouncementQueue.SHARED_GAP);
	private KeyMapping shareKey;
	private boolean loaded;
	private boolean dirty;
	private long seenSavedAt;
	private int tickCount;
	private String lobby = "";
	private boolean inHollows;
	private String lastArea = "";
	private String lastOffer = "";
	private long lastOfferAt;
	private int shareNumber;

	public HollowsMapFeature() {
		super("crystal_hollows_map", "Crystal Hollows Map", "Crystal Hollows map with places found.",
			false, Anchor.RIGHT, false, 10f, 60f);
		pageGroup("Mineshafts and Hollows");
		iconRgb(SystemColors.PURPLE_RGB);
		iconName("map");
		summary("The Hollows and places found here");
		withoutHeader();
		for (HollowsStructure structure : HollowsStructure.values()) {
			add(SettingRows.buttons("found_" + structure.key(), Component.literal(structure.title), () -> foundText(structure), false, null,
				() -> spotOf(structure) == null ? List.of() : List.of(new SettingRows.Button("Share", SettingRows.Kind.NORMAL, () -> share(structure)))),
				"Places in this lobby");
		}
		add(make().action("clear_lobby", Component.literal("Clear this lobby"), Component.literal("Clear"), true, this::clearLobby),
			"Places in this lobby");
		for (HollowsStructure structure : HollowsStructure.values()) {
			colors.put(structure, add(make().color("color_" + structure.key(), Component.literal(structure.title), structure.defaultRgb).ownColorOnly(), "Colors"));
		}
	}

	private static Map<HollowsStructure, HollowsLobbies.Spot> sample() {
		Map<HollowsStructure, HollowsLobbies.Spot> spots = new EnumMap<>(HollowsStructure.class);
		spots.put(HollowsStructure.JUNGLE_TEMPLE, new HollowsLobbies.Spot(330, 110, 360, true));
		spots.put(HollowsStructure.MINES_OF_DIVAN, new HollowsLobbies.Spot(700, 80, 330, true));
		spots.put(HollowsStructure.GOBLIN_QUEENS_DEN, new HollowsLobbies.Spot(300, 120, 700, false));
		spots.put(HollowsStructure.LOST_PRECURSOR_CITY, new HollowsLobbies.Spot(720, 120, 720, true));
		spots.put(HollowsStructure.KHAZAD_DUM, new HollowsLobbies.Spot(540, 50, 540, false));
		return spots;
	}

	public void register() {
		shareKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + SkyBlockAddon.ID + ".hollows_share", InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(), CATEGORY));
		addKey(shareKey, KeyPlace.WORLD);
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		LevelRenderEvents.COLLECT_SUBMITS.register(context -> world.draw(context));
		WorldLabels.add((graphics, tracker) -> world.drawLabels(graphics));
		// the chat line's buttons run these on the client only: nothing reaches the server
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> dispatcher.register(ClientCommands.literal("lumio")
			.then(ClientCommands.literal("hollows")
				.then(ClientCommands.literal("pick").then(coords(ClientCommands.argument("x", IntegerArgumentType.integer()), this::pick)))
				.then(ClientCommands.literal("add").then(ClientCommands.argument("place", StringArgumentType.word())
					.then(coords(ClientCommands.argument("x", IntegerArgumentType.integer()), this::addPicked)))))));
	}

	/** Adds y and z after x, and what runs with all three. */
	private static com.mojang.brigadier.builder.ArgumentBuilder<FabricClientCommandSource, ?> coords(
		com.mojang.brigadier.builder.RequiredArgumentBuilder<FabricClientCommandSource, Integer> x,
		com.mojang.brigadier.Command<FabricClientCommandSource> run) {
		return x.then(ClientCommands.argument("y", IntegerArgumentType.integer())
			.then(ClientCommands.argument("z", IntegerArgumentType.integer()).executes(run)));
	}

	// ------------------------------------------------------------------------------------------------------------ state

	private void tick(Minecraft client) {
		if (!loaded) {
			loaded = true;
			CompletableFuture.supplyAsync(HollowsMapFeature::read, WORKER).thenAcceptAsync(json -> {
				if (json != null) {
					lobbies.load(json, System.currentTimeMillis());
				}
			}, client);
		}
		pollQueue(client);
		if (!isEnabled() || client.player == null || !SkyBlock.inSkyBlock()) {
			inHollows = false;
			return;
		}
		boolean pressed = false;
		while (shareKey != null && shareKey.consumeClick()) {
			pressed = true;
		}
		if (++tickCount % READ_TICKS == 0) {
			inHollows = HOLLOWS.equals(SkyBlockArea.island());
			lobby = inHollows ? LobbyId.of(SkyBlockArea.sidebar()) : "";
			if (inHollows) {
				readArea(client);
				long now = System.currentTimeMillis();
				if (now - seenSavedAt > SEEN_SAVE_MILLIS && !lobbies.spots(lobby).isEmpty()) {
					seenSavedAt = now;
					lobbies.seen(lobby, now);
					dirty = true;
				}
			}
			if (dirty) {
				writePlaces();
			}
		}
		if (pressed && client.gui.screen() == null && inHollows) {
			HollowsStructure structure = structureForKey(client);
			if (structure != null) {
				share(structure);
			}
		}
	}

	/** The scoreboard's area: entering a structure's own area marks it where the player stands. */
	private void readArea(Minecraft client) {
		String area = SkyBlockArea.zone();
		if (area.equals(lastArea)) {
			return;
		}
		lastArea = area;
		HollowsStructure structure = HollowsStructure.byArea(area);
		if (structure != null) {
			foundHere(client, structure);
		}
	}

	private void foundHere(Minecraft client, HollowsStructure structure) {
		if (client.player == null || lobby.isEmpty()) {
			return;
		}
		if (lobbies.put(lobby, structure, client.player.getBlockX(), client.player.getBlockY(), client.player.getBlockZ(), true, System.currentTimeMillis())) {
			dirty = true;
		}
	}

	/** A line of the server's (never a player's): a structure's character speaking, or its crystal found. */
	public void onServerLine(String line) {
		if (!isEnabled() || !inHollows) {
			return;
		}
		HollowsStructure structure = HollowsStructure.byServerLine(line);
		if (structure != null) {
			foundHere(Minecraft.getInstance(), structure);
		}
	}

	/**
	 * A player's chat line: coordinates inside the island are offered. Only from party chat and public chat, which are in this lobby; with a place
	 * name they are kept at once, without one a chat line offers to add them.
	 */
	public void onPlayerLine(String text) {
		if (!isEnabled() || !inHollows || lobby.isEmpty() || !ChatGuard.fromLobby(text)) {
			return;
		}
		int colon = text.indexOf(": ");
		if (colon < 0) {
			return;
		}
		HollowsChat.Offer offer = HollowsChat.read(text.substring(colon + 2));
		if (offer == null) {
			return;
		}
		if (offer.structure() != null) {
			if (lobbies.put(lobby, offer.structure(), offer.x(), offer.y(), offer.z(), false, System.currentTimeMillis())) {
				dirty = true;
			}
			return;
		}
		String coords = offer.x() + " " + offer.y() + " " + offer.z();
		long now = System.currentTimeMillis();
		if (coords.equals(lastOffer) && now - lastOfferAt < OFFER_AGAIN_MILLIS) {
			return;
		}
		lastOffer = coords;
		lastOfferAt = now;
		LumioChat.info(Component.literal("Crystal Hollows place " + coords + "  ").withStyle(ChatFormatting.GRAY)
			.append(Component.literal("[Add to map]").withStyle(style -> style.withColor(ChatFormatting.AQUA)
				.withClickEvent(new ClickEvent.RunCommand("/lumio hollows pick " + coords))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal("Choose which place it is"))))));
	}

	/** "[Add to map]" was clicked: a line with a button for each place. */
	private int pick(CommandContext<FabricClientCommandSource> context) {
		int x = IntegerArgumentType.getInteger(context, "x");
		int y = IntegerArgumentType.getInteger(context, "y");
		int z = IntegerArgumentType.getInteger(context, "z");
		if (!HollowsGeometry.inside(x, y, z)) {
			return 0;
		}
		String coords = x + " " + y + " " + z;
		MutableComponent line = Component.literal(coords + " is: ").withStyle(ChatFormatting.GRAY);
		for (HollowsStructure structure : HollowsStructure.values()) {
			line.append(Component.literal("[" + structure.shortName + "]").withStyle(style -> style.withColor(TextColor.fromRgb(colorOf(structure)))
				.withClickEvent(new ClickEvent.RunCommand("/lumio hollows add " + structure.key() + " " + coords))
				.withHoverEvent(new HoverEvent.ShowText(Component.literal(structure.title))))).append(Component.literal(" "));
		}
		LumioChat.info(line);
		return 1;
	}

	/** A place's button of the picked coordinates was clicked: it goes on the map. */
	private int addPicked(CommandContext<FabricClientCommandSource> context) {
		HollowsStructure structure = HollowsStructure.byKey(StringArgumentType.getString(context, "place"));
		int x = IntegerArgumentType.getInteger(context, "x");
		int y = IntegerArgumentType.getInteger(context, "y");
		int z = IntegerArgumentType.getInteger(context, "z");
		if (structure == null || lobby.isEmpty() || !inHollows) {
			return 0;
		}
		if (lobbies.put(lobby, structure, x, y, z, false, System.currentTimeMillis())) {
			dirty = true;
			LumioChat.info(Component.literal(structure.title + " is on the map").withStyle(ChatFormatting.GRAY));
		} else {
			LumioChat.info(Component.literal(structure.title + " was already on the map").withStyle(ChatFormatting.GRAY));
		}
		return 1;
	}

	private void clearLobby() {
		if (lobbies.clear(lobby)) {
			dirty = true;
			writePlaces();
		}
	}

	// ------------------------------------------------------------------------------------------------------------ share

	/** @return the place the player is in (by the area), else the nearest place found in this lobby */
	private HollowsStructure structureForKey(Minecraft client) {
		HollowsStructure here = HollowsStructure.byArea(SkyBlockArea.zone());
		Map<HollowsStructure, HollowsLobbies.Spot> spots = lobbies.spots(lobby);
		if (here != null && spots.containsKey(here)) {
			return here;
		}
		HollowsStructure nearest = null;
		double nearestSq = Double.MAX_VALUE;
		for (Map.Entry<HollowsStructure, HollowsLobbies.Spot> entry : spots.entrySet()) {
			HollowsLobbies.Spot spot = entry.getValue();
			double distance = client.player.distanceToSqr(spot.x() + 0.5, spot.y(), spot.z() + 0.5);
			if (distance < nearestSq) {
				nearestSq = distance;
				nearest = entry.getKey();
			}
		}
		return nearest;
	}

	/** Queues one message with the place, as the player asked (one press or one click); the queue sends it after its gap. */
	private void share(HollowsStructure structure) {
		HollowsLobbies.Spot spot = spotOf(structure);
		if (spot == null) {
			return;
		}
		queue.add("hollows share " + shareNumber++, shareTo.get().command + HollowsChat.shareText(structure, spot.x(), spot.y(), spot.z()));
	}

	private void pollQueue(Minecraft client) {
		if (queue.waiting() == 0) {
			return;
		}
		if (client.player == null || client.getConnection() == null || !isEnabled()) {
			queue.clear();
			return;
		}
		AnnouncementQueue.Waiting next = queue.poll();
		if (next != null) {
			client.getConnection().sendCommand(next.text());
		}
		if (queue.waiting() == 0) {
			queue.forgetEvents();
		}
	}

	// ------------------------------------------------------------------------------------------------------------- data

	private HollowsLobbies.Spot spotOf(HollowsStructure structure) {
		return inHollows ? lobbies.spots(lobby).get(structure) : null;
	}

	private String foundText(HollowsStructure structure) {
		if (!inHollows || lobby.isEmpty()) {
			return "Only in the Crystal Hollows";
		}
		HollowsLobbies.Spot spot = spotOf(structure);
		return spot == null ? "Not found yet" : spot.x() + ", " + spot.y() + ", " + spot.z() + (spot.own() ? "" : " (from chat)");
	}

	int colorOf(HollowsStructure structure) {
		return colors.get(structure).get() & 0xFFFFFF;
	}

	/** @return the places shown as waypoints now: none while they are off or outside the Crystal Hollows */
	Map<HollowsStructure, HollowsLobbies.Spot> waypointSpots() {
		if (!isEnabled() || !waypoints.get() || !inHollows) {
			return Map.of();
		}
		return lobbies.spots(lobby);
	}

	private void writePlaces() {
		dirty = false;
		lobbies.expire(System.currentTimeMillis());
		String json = lobbies.toJson();
		WORKER.execute(() -> {
			try {
				ModFiles.writeAtomically(ModFiles.file(FILE), json);
			} catch (IOException e) {
				Logs.warn("Could not write the Crystal Hollows map", e);
			}
		});
	}

	private static String read() {
		try {
			Path file = ModFiles.file(FILE);
			return Files.exists(file) ? Files.readString(file) : null;
		} catch (IOException | RuntimeException e) {
			Logs.warn("Could not read the Crystal Hollows map", e);
			return null;
		}
	}

	// -------------------------------------------------------------------------------------------------------------- HUD

	@Override
	public boolean shown(Minecraft client) {
		return simulated() || isEnabled() && inHollows;
	}

	@Override
	public boolean inEditorWhenHidden() {
		return false;
	}

	private int mapSide() {
		return Math.round(MAP_BASE * size.get());
	}

	@Override
	public int panelWidth() {
		return mapSide() + 2 * PAD;
	}

	@Override
	public int panelHeight() {
		return panelWidth() + (myCoords.get() ? COORDS_LINE : 0);
	}

	@Override
	public float hudRadius() {
		return Radius.SM * hudScale();
	}

	@Override
	public void drawPanel(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		float before = Draw.alpha();
		Draw.setAlpha(before * opacity.get());
		try {
			drawGlass(ui, panelWidth(), panelHeight(), Radius.SM);
			drawMap(ui, graphics, client);
		} finally {
			Draw.setAlpha(before);
		}
	}

	private void drawMap(Canvas ui, GuiGraphicsExtractor graphics, Minecraft client) {
		boolean live = inHollows && !simulated();
		float side = mapSide();
		float half = side / 2f;
		// the quadrants: north-west, north-east, south-west, south-east
		HollowsGeometry.Quadrant[] corners = {HollowsGeometry.Quadrant.JUNGLE, HollowsGeometry.Quadrant.MITHRIL_DEPOSITS,
			HollowsGeometry.Quadrant.GOBLIN_HOLDOUT, HollowsGeometry.Quadrant.PRECURSOR_REMNANTS};
		for (int i = 0; i < corners.length; i++) {
			float x = PAD + (i % 2) * half;
			float y = PAD + (i / 2) * half;
			ui.roundedSmooth(x, y, half, half, 0f, Draw.withOpacity(0xFF000000 | corners[i].rgb, QUADRANT_OPACITY));
		}
		float nucleus = HollowsGeometry.nucleusOnMap(side);
		ui.roundedSmooth(PAD + half - nucleus / 2f, PAD + half - nucleus / 2f, nucleus, nucleus, Radius.XXS,
			Draw.withOpacity(0xFF000000 | HollowsGeometry.Quadrant.NUCLEUS.rgb, NUCLEUS_OPACITY));
		if (quadrantLabels.get()) {
			for (int i = 0; i < corners.length; i++) {
				drawQuadrantLabel(ui, corners[i], i % 2 == 1, i / 2 == 1, half);
			}
		}
		Map<HollowsStructure, HollowsLobbies.Spot> spots = live ? lobbies.spots(lobby) : simulated() ? SAMPLE : Map.of();
		for (Map.Entry<HollowsStructure, HollowsLobbies.Spot> entry : spots.entrySet()) {
			float x = PAD + HollowsGeometry.toMap(entry.getValue().x(), side);
			float y = PAD + HollowsGeometry.toMap(entry.getValue().z(), side);
			ui.roundedSmooth(x - SPOT / 2f - 1, y - SPOT / 2f - 1, SPOT + 2, SPOT + 2, 1f, SPOT_EDGE);
			ui.roundedSmooth(x - SPOT / 2f, y - SPOT / 2f, SPOT, SPOT, 0.5f, 0xFF000000 | colorOf(entry.getKey()));
			if (showNames.get()) {
				String name = entry.getKey().shortName;
				float width = ui.widthScaled(name, NAME_SCALE);
				// right of the mark, or left of it near the right edge
				float nameX = x + SPOT / 2f + 2 + width > PAD + side ? x - SPOT / 2f - 2 - width : x + SPOT / 2f + 2;
				ui.textAtScaled(name, nameX, y - 2.5f, NAME_SCALE, 0xFFFFFFFF);
			}
		}
		if (live && party.get() && client.level != null) {
			List<String> members = PartyList.INSTANCE.members();
			if (!members.isEmpty()) {
				for (Player other : client.level.players()) {
					if (other != client.player && members.contains(other.getGameProfile().name())) {
						MapMarker.arrow(ui, graphics, PAD + HollowsGeometry.toMap(other.getX(), side), PAD + HollowsGeometry.toMap(other.getZ(), side),
							other.getYRot(), 0xFF000000 | PARTY_RGB, PARTY_ARROW);
					}
				}
			}
		}
		if (client.player != null && (live || simulated())) {
			double px = live ? client.player.getX() : HollowsGeometry.MIDDLE + 60;
			double pz = live ? client.player.getZ() : HollowsGeometry.MIDDLE - 90;
			MapMarker.arrow(ui, graphics, PAD + HollowsGeometry.toMap(px, side), PAD + HollowsGeometry.toMap(pz, side), client.player.getYRot(), 0xFFFFFFFF,
				1f);
		}
		if (myCoords.get()) {
			String coords = client.player == null ? "573, 110, 423"
				: client.player.getBlockX() + ", " + client.player.getBlockY() + ", " + client.player.getBlockZ();
			float width = ui.widthScaled(coords, TextScale.SMALL);
			ui.textAtScaled(coords, PAD + half - width / 2f, PAD + side + 2, TextScale.SMALL, HudTheme.secondary());
		}
	}

	/** A quadrant's name in its outer corner, in a lighter version of its color. */
	private static void drawQuadrantLabel(Canvas ui, HollowsGeometry.Quadrant quadrant, boolean east, boolean south, float half) {
		String label = ui.fitScaled(quadrant.label, half - 4, LABEL_SCALE);
		float width = ui.widthScaled(label, LABEL_SCALE);
		float x = east ? PAD + 2 * half - 2 - width : PAD + 2;
		float y = south ? PAD + 2 * half - 2 - 7 * LABEL_SCALE : PAD + 2;
		ui.textAtScaled(label, x, y, LABEL_SCALE, Draw.mix(0xFF000000 | quadrant.rgb, 0xFFFFFFFF, 0.45f));
	}
}
