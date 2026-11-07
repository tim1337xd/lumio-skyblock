package dev.tim1337.lumio.skyblock.world;

import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.Action;
import dev.tim1337.lumio.api.setting.MultiToggle;
import dev.tim1337.lumio.api.setting.Toggle;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.skyblock.data.SkyBlock;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;
import dev.tim1337.lumio.skyblock.mixin.accessor.ChunkRadiusAccess;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.IOWorker;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.lighting.LevelLightEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Keep Terrain Loaded, like SkySoft's: on the selected SkyBlock islands the world is cached the way Bobby does it. Every chunk the server sends is
 * saved to disk for its island, chunks the server unloads stay loaded, and within the render distance (not only the server's view distance)
 * chunks the server has not sent are filled in from the cache. Distant parts of an island stay visible, and returning does not mean watching the
 * world load again. Chunks from the server always replace cached ones. Islands that are generated fresh each time (mineshafts, dungeons) or that
 * belong to someone else (private islands) cannot be selected.
 */
public class KeepTerrainFeature extends AddonFeature {
	/** A selectable island: its key in the settings and on disk, its display name, and its area names in the player list. */
	private record Island(String key, String name, String... areas) {
	}

	private static final List<Island> ISLANDS = List.of(
		new Island("hub", "Hub", "hub"),
		new Island("dwarven_mines", "Dwarven Mines", "dwarven mines"),
		new Island("crystal_hollows", "Crystal Hollows", "crystal hollows"),
		new Island("deep_caverns", "Deep Caverns", "deep caverns"),
		new Island("gold_mine", "Gold Mine", "gold mine"),
		new Island("the_park", "The Park", "the park"),
		new Island("spiders_den", "Spider's Den", "spider's den"),
		new Island("the_end", "The End", "the end"),
		new Island("crimson_isle", "Crimson Isle", "crimson isle"),
		new Island("farming_islands", "The Farming Islands", "the farming islands", "the barn", "mushroom desert"),
		new Island("garden", "Garden", "garden", "the garden"),
		new Island("dungeon_hub", "Dungeon Hub", "dungeon hub"),
		new Island("jerrys_workshop", "Jerry's Workshop", "jerry's workshop"),
		new Island("the_rift", "The Rift", "the rift"),
		new Island("backwater_bayou", "Backwater Bayou", "backwater bayou"),
		new Island("galatea", "Galatea", "galatea"),
		new Island("torrhus_canyon", "Torrhus Canyon", "torrhus canyon"),
		new Island("safari", "Safari", "safari"),
		new Island("lotus_atoll", "Lotus Atoll", "lotus atoll"));

	/** Maximum distance kept and filled in, in chunks, regardless of the render distance. */
	private static final int MOST_DISTANCE = 32;
	/** Work per tick: cached chunks read at once, positions scanned, and chunks written. */
	private static final int MOST_LOADING = 8;
	private static final int CHECKS_PER_TICK = 64;
	private static final int SAVES_PER_TICK = 6;
	private static final int MOST_PENDING_SAVES = 512;
	private static final int MOST_PACKET_BYTES = 4 * 1024 * 1024;

	private final List<Toggle> islandSwitches = ISLANDS.stream()
		.map(island -> make().toggle("island_" + island.key(), Component.literal(island.name()), true, Toggle.Style.CHECKBOX)).toList();
	private final MultiToggle islands = add(make().multi("islands", Component.literal("Islands"), islandSwitches), "General");
	private final Action deleteSaved = add(make().action("delete_saved", Component.literal("Saved terrain"), Component.literal("Delete"), true,
		this::deleteSaved).confirm(), "General");

	/** Bobby does the same for every world: when it is installed, this feature stays out of its way. */
	private final boolean bobby = FabricLoader.getInstance().isModLoaded("bobby");

	/** The current level and island and the island's storage; null while inactive. */
	private ClientLevel level;
	private Island island;
	private Storage storage;
	/** Chunks the server unloaded, or that were filled in from the cache; they are unloaded when the feature stops. */
	private final Set<ChunkPos> kept = new HashSet<>();
	/** Server chunks still to be saved, oldest first; plus cached chunks currently being read. */
	private final Map<ChunkPos, Pending> pending = new LinkedHashMap<>();
	private final Set<ChunkPos> loading = new HashSet<>();
	private final Map<String, Storage> storages = new HashMap<>();
	/** Guards against feedback: while the server view distance is restored it is not enlarged again, and while a cached chunk is applied it is not saved again. */
	private boolean restoring;
	private boolean applying;
	/** Positions around the player to scan, nearest first, and the scan progress. */
	private ChunkPos scanCenter;
	private int scanDistance = -1;
	private List<int[]> offsets = List.of();
	private int scanIndex;

	/** A server chunk still to be saved, with the island storage it belongs to (null while the island is not certain yet). */
	private record Pending(ClientboundLevelChunkWithLightPacket packet, RegistryAccess registries, int sections, Storage storage) {
	}

	public KeepTerrainFeature() {
		super("keep_terrain", "Keep terrain loaded", "The islands you choose are saved as you see them, and stay as far as your render distance reaches, not only as far as the server sends.", false);
		iconRgb(SystemColors.GREEN_RGB);
		iconName("map");
		summary("Keeps the islands you choose loaded as you saw them");
	}

	public void register() {
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> close());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> close());
	}

	private static void deleteFolder(Path root) {
		try (var paths = Files.walk(root)) {
			for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(path);
			}
		} catch (IOException e) {
			Logs.warn("Keep terrain loaded: could not delete the saved terrain", e);
		}
	}

	// ------------------------------------------------------------------------------------------------ hooks called by mixins

	/** @return the client chunk cache radius: the render distance while active, otherwise the server's view distance */
	public int storageRadius(int serverRadius) {
		if (restoring || level == null) {
			return serverRadius;
		}
		return Math.max(serverRadius, distance());
	}

	/** The server unloads a chunk: while active it stays loaded, without its block entities. @return whether it is kept */
	public boolean keep(ClientLevel from, ChunkPos pos) {
		if (level == null || from != level) {
			return false;
		}
		LevelChunk chunk = from.getChunkSource().getChunk(pos.x(), pos.z(), ChunkStatus.FULL, false);
		if (chunk == null) {
			return false;
		}
		chunk.clearAllBlockEntities();
		kept.add(pos);
		return true;
	}

	/** The server sent a chunk: it is queued for saving for the island (a few per tick) and is now a server chunk rather than a kept one. */
	public void onChunk(ClientLevel from, ClientboundLevelChunkWithLightPacket packet) {
		if (applying || from == null) {
			return;
		}
		if (level == null) {
			// A freshly joined world whose island is not certain yet (the player list still shows the previous island for a moment): hold what the
			// server sends right away and save it only once the island is certain, for that island.
			if (from == seenLevel && waiting.size() < MOST_WAITING && System.currentTimeMillis() - levelAt < SETTLE_MS + STEADY_MS + 4000L) {
				waiting.add(new Pending(packet, from.registryAccess(), from.getSectionsCount(), null));
			}
			return;
		}
		if (from != level) {
			return;
		}
		queue(packet, from.registryAccess(), from.getSectionsCount(), storage);
	}

	/** Queues a chunk for saving, bound to its island's storage, so it can never be written into another island. */
	private void queue(ClientboundLevelChunkWithLightPacket packet, RegistryAccess registries, int sections, Storage into) {
		ChunkPos pos = new ChunkPos(packet.getX(), packet.getZ());
		kept.remove(pos);
		pending.remove(pos);
		pending.put(pos, new Pending(packet, registries, sections, into));
		while (pending.size() > MOST_PENDING_SAVES) {
			pending.remove(pending.keySet().iterator().next());
		}
	}

	// ------------------------------------------------------------------------------------------------ per tick

	/**
	 * A joined world is only treated as an island after a delay, and once the player list has reported the same island for a while: right after a
	 * warp it still shows the previous island (the Garden while already on the private island), and chunks of one island were saved for another.
	 */
	private static final long SETTLE_MS = 3000L;
	private static final long STEADY_MS = 2000L;
	private static final int MOST_WAITING = 1200;

	/** The current level and since when, and the island it appears to be and since when. */
	private ClientLevel seenLevel;
	private long levelAt;
	private Island candidate;
	private long candidateAt;
	/** Chunks the server sent while the island was not certain yet. */
	private final List<Pending> waiting = new ArrayList<>();

	private void tick(Minecraft client) {
		saveSome();
		long time = System.currentTimeMillis();
		ClientLevel now = client.level;
		if (now != seenLevel) {
			seenLevel = now;
			levelAt = time;
			candidate = null;
			waiting.clear();
		}
		Island here = now == null ? null : islandHere();
		if (here != candidate) {
			candidate = here;
			candidateAt = time;
		}
		boolean sure = here != null && time - levelAt >= SETTLE_MS && time - candidateAt >= STEADY_MS;
		if (!sure) {
			stop(client);
			if (here == null && time - levelAt >= SETTLE_MS + STEADY_MS) {
				waiting.clear(); // an island that is not selected: keep nothing
			}
			return;
		}
		if (level != now || island != here) {
			start(client, now, here);
			// chunks received while the island was uncertain belong to this island: save them now
			for (Pending held : waiting) {
				queue(held.packet(), held.registries(), held.sections(), storage);
			}
			waiting.clear();
		}
		Integer serverRadius = serverRadius(client);
		if (serverRadius == null || client.player == null) {
			return;
		}
		int distance = distance();
		now.getChunkSource().updateViewRadius(Math.max(serverRadius, distance));
		ChunkPos center = client.player.chunkPosition();
		if (!center.equals(scanCenter) || distance != scanDistance) {
			restartScan(center, distance);
		}
		loadSome(client);
	}

	/** @return the island to work on here: on SkyBlock, a selected island, and the feature enabled; otherwise null */
	private Island islandHere() {
		if (!isEnabled() || bobby || !SkyBlock.inSkyBlock()) {
			return null;
		}
		String area = SkyBlockArea.island().toLowerCase(Locale.ROOT).strip();
		// private islands (anyone's) are never kept, also when only the scoreboard says so and the player list does not yet
		if (area.contains("private island") || SkyBlockArea.zone().toLowerCase(Locale.ROOT).contains("your island")) {
			return null;
		}
		for (int i = 0; i < ISLANDS.size(); i++) {
			for (String name : ISLANDS.get(i).areas()) {
				if (area.equals(name)) {
					return islandSwitches.get(i).get() ? ISLANDS.get(i) : null;
				}
			}
		}
		return null;
	}

	private void start(Minecraft client, ClientLevel now, Island here) {
		stop(client);
		level = now;
		island = here;
		storage = storages.computeIfAbsent(here.key(), key -> new Storage(folder().resolve(key)));
	}

	/** Unloads everything kept and restores the server's view distance. */
	private void stop(Minecraft client) {
		if (level != null && client.level == level) {
			for (ChunkPos pos : kept) {
				level.getChunkSource().drop(pos);
				removeLight(level, pos);
			}
			Integer serverRadius = serverRadius(client);
			if (serverRadius != null) {
				restoring = true;
				try {
					level.getChunkSource().updateViewRadius(serverRadius);
				} finally {
					restoring = false;
				}
			}
		}
		kept.clear();
		loading.clear();
		level = null;
		island = null;
		storage = null;
		scanCenter = null;
		scanDistance = -1;
	}

	/** Removes the light data of an unloaded chunk too, otherwise its area would keep the light of a missing chunk. */
	private static void removeLight(ClientLevel level, ChunkPos pos) {
		level.queueLightUpdate(() -> {
			if (level.getChunkSource().getChunk(pos.x(), pos.z(), ChunkStatus.FULL, false) != null) {
				return; // the server re-sent it in the meantime
			}
			LevelLightEngine light = level.getLightEngine();
			light.setLightEnabled(pos, false);
			for (int y = light.getMinLightSection(); y < light.getMaxLightSection(); y++) {
				light.queueSectionData(LightLayer.BLOCK, SectionPos.of(pos, y), null);
				light.queueSectionData(LightLayer.SKY, SectionPos.of(pos, y), null);
			}
			for (int y = level.getMinSectionY(); y <= level.getMaxSectionY(); y++) {
				light.updateSectionStatus(SectionPos.of(pos, y), true);
			}
		});
	}

	/** Writes a few server chunks to disk, in arrival order. */
	private void saveSome() {
		for (int i = 0; i < SAVES_PER_TICK && !pending.isEmpty(); i++) {
			var first = pending.entrySet().iterator().next();
			pending.remove(first.getKey());
			try {
				// into the island it came from, even if another island is active by now
				first.getValue().storage().save(first.getKey(), encode(first.getValue().packet(), first.getValue().registries()), first.getValue().sections());
			} catch (RuntimeException e) {
				Logs.warn("Keep terrain loaded: could not save a chunk", e);
			}
		}
	}

	private void restartScan(ChunkPos center, int distance) {
		scanCenter = center;
		scanIndex = 0;
		if (distance != scanDistance) {
			scanDistance = distance;
			offsets = ring(distance);
		}
	}

	/** Fills positions around the player that the server has not sent from the cache, nearest first, a few per tick. */
	private void loadSome(Minecraft client) {
		ClientLevel at = level;
		Storage from = storage;
		int checks = 0;
		while (scanIndex < offsets.size() && loading.size() < MOST_LOADING && checks++ < CHECKS_PER_TICK) {
			int[] offset = offsets.get(scanIndex++);
			ChunkPos pos = new ChunkPos(scanCenter.x() + offset[0], scanCenter.z() + offset[1]);
			if (at.getChunkSource().getChunk(pos.x(), pos.z(), ChunkStatus.FULL, false) != null || !loading.add(pos)) {
				continue;
			}
			from.load(pos, at.getSectionsCount()).whenComplete((bytes, error) -> client.execute(() -> {
				loading.remove(pos);
				if (error != null) {
					Logs.warn("Keep terrain loaded: could not read a chunk", error);
				} else if (bytes != null) {
					apply(client, at, pos, bytes);
				}
			}));
		}
	}

	/** Applies a cached chunk as if the server had sent it, if it is still wanted and the server has not sent it in the meantime. */
	private void apply(Minecraft client, ClientLevel at, ChunkPos pos, byte[] bytes) {
		if (level != at || scanCenter == null || Math.abs(pos.x() - scanCenter.x()) > scanDistance || Math.abs(pos.z() - scanCenter.z()) > scanDistance
			|| at.getChunkSource().getChunk(pos.x(), pos.z(), ChunkStatus.FULL, false) != null || client.getConnection() == null) {
			return;
		}
		applying = true;
		try {
			ClientboundLevelChunkWithLightPacket packet = decode(bytes, at.registryAccess());
			if (packet.getX() != pos.x() || packet.getZ() != pos.z()) {
				return;
			}
			packet.handle(client.getConnection());
			LevelChunk chunk = at.getChunkSource().getChunk(pos.x(), pos.z(), ChunkStatus.FULL, false);
			if (chunk != null) {
				chunk.clearAllBlockEntities();
				kept.add(pos);
			}
		} catch (RuntimeException e) {
			Logs.warn("Keep terrain loaded: could not put in a saved chunk", e);
		} finally {
			applying = false;
		}
	}

	// ------------------------------------------------------------------------------------------------ helpers

	private static int distance() {
		return Math.min(Minecraft.getInstance().options.renderDistance().get(), MOST_DISTANCE);
	}

	private static Integer serverRadius(Minecraft client) {
		return client.getConnection() instanceof ChunkRadiusAccess accessor ? accessor.evskyblock$serverChunkRadius() : null;
	}

	/** @return the positions of a square around the player, ring by ring from the center outwards */
	private static List<int[]> ring(int distance) {
		List<int[]> list = new ArrayList<>();
		list.add(new int[]{0, 0});
		for (int r = 1; r <= distance; r++) {
			for (int x = -r; x <= r; x++) {
				list.add(new int[]{x, -r});
				list.add(new int[]{x, r});
			}
			for (int z = -r + 1; z < r; z++) {
				list.add(new int[]{-r, z});
				list.add(new int[]{r, z});
			}
		}
		return list;
	}

	private static byte[] encode(ClientboundLevelChunkWithLightPacket packet, RegistryAccess registries) {
		ByteBuf buffer = Unpooled.buffer();
		try {
			RegistryFriendlyByteBuf out = new RegistryFriendlyByteBuf(buffer, registries);
			ClientboundLevelChunkWithLightPacket.STREAM_CODEC.encode(out, packet);
			byte[] bytes = new byte[out.readableBytes()];
			out.readBytes(bytes);
			return bytes;
		} finally {
			buffer.release();
		}
	}

	private static ClientboundLevelChunkWithLightPacket decode(byte[] bytes, RegistryAccess registries) {
		if (bytes.length > MOST_PACKET_BYTES) {
			throw new IllegalArgumentException("saved chunk too large: " + bytes.length);
		}
		ByteBuf buffer = Unpooled.wrappedBuffer(bytes);
		try {
			return ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode(new RegistryFriendlyByteBuf(buffer, registries));
		} finally {
			buffer.release();
		}
	}

	private static Path folder() {
		// a new folder: the previous one could contain private island chunks saved as the Garden
		return FabricLoader.getInstance().getGameDir().resolve("lumio").resolve("terrain2");
	}

	/** Closes the island storages: when leaving the server and when the game closes. */
	private void close() {
		pending.clear();
		loading.clear();
		kept.clear();
		level = null;
		island = null;
		storage = null;
		scanCenter = null;
		scanDistance = -1;
		for (Storage each : storages.values()) {
			try {
				each.close();
			} catch (IOException e) {
				Logs.warn("Keep terrain loaded: could not close the saved terrain", e);
			}
		}
		storages.clear();
	}

	/** Delete button: deletes the cache of every island. An island that is currently active starts over. */
	private void deleteSaved() {
		Minecraft client = Minecraft.getInstance();
		stop(client);
		close();
		if (Files.isDirectory(folder())) {
			deleteFolder(folder());
		}
	}

	/** Chunk storage of one island on disk, in region files like a vanilla world, tagged with the game version. */
	private static final class Storage extends IOWorker {
		private static final String PROTOCOL = "protocol";
		private static final String SECTIONS = "sections";
		private static final String PACKET = "packet";

		Storage(Path folder) {
			super(new RegionStorageInfo("lumio", Level.OVERWORLD, "terrain"), folder, false);
		}

		void save(ChunkPos pos, byte[] packet, int sections) {
			CompoundTag tag = new CompoundTag();
			tag.putInt(PROTOCOL, SharedConstants.getProtocolVersion());
			tag.putInt(SECTIONS, sections);
			tag.putByteArray(PACKET, packet);
			store(pos, tag).exceptionally(error -> {
				Logs.warn("Keep terrain loaded: could not write a chunk", error);
				return null;
			});
		}

		/** @return the cached chunk packet, or null if there is none or it is from another game version or world height */
		CompletableFuture<byte[]> load(ChunkPos pos, int sections) {
			return loadAsync(pos).thenApply(found -> {
				CompoundTag tag = found.orElse(null);
				if (tag == null || tag.getIntOr(PROTOCOL, -1) != SharedConstants.getProtocolVersion() || tag.getIntOr(SECTIONS, -1) != sections) {
					return null;
				}
				return tag.getByteArray(PACKET).filter(bytes -> bytes.length <= MOST_PACKET_BYTES).orElse(null);
			});
		}
	}
}
