package dev.tim1337.lumio.skyblock.chat;

import dev.tim1337.lumio.api.ChatTabProvider;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.api.experimental.util.ModFiles;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.AddonSetting;
import dev.tim1337.lumio.api.setting.Toggle;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.List;

/**
 * The SkyBlock tab of the chat: the game's own messages, not what players write. The formats come from a data file of the add-on and, if the player made one, from {@code skyblock_chat_formats.json} in the mod's config
 * folder. Its one setting shows in the chat's card.
 */
public final class SkyBlockChatTabFeature extends AddonFeature implements ChatTabProvider {
	private static final String BUILT_IN = "/assets/lumio-skyblock/skyblock/chat_formats.json";
	private static final String PLAYERS_FILE = "skyblock_chat_formats.json";

	private final Toggle keepOut = add(settings().toggle("keep_out_of_all", Component.literal("Keep game messages out of All"), false), null,
		"All then shows only what players write");
	/** The formats with the player's own, read in the background as the game starts: the file is not read on the game's thread. */
	private final CompletableFuture<SkyBlockChatFormats> reading = CompletableFuture.supplyAsync(() -> SkyBlockChatFormats.read(builtIn(), playersFile()));
	private SkyBlockChatFormats formats;
	/** The built-in formats alone, for a message that comes before the reading is done. */
	private SkyBlockChatFormats builtInOnly;

	public SkyBlockChatTabFeature() {
		super("skyblock_chat_tab", "SkyBlock chat tab", "The game's messages in a tab of the chat.", true, false);
	}

	@Override
	public String tabId() {
		return "skyblock";
	}

	@Override
	public String title() {
		return "SkyBlock";
	}

	@Override
	public boolean matches(String plainText) {
		if (formats == null) {
			if (!reading.isDone()) {
				if (builtInOnly == null) {
					builtInOnly = SkyBlockChatFormats.read(builtIn(), null);
				}
				return builtInOnly.matches(plainText);
			}
			formats = reading.join();
		}
		return formats.matches(plainText);
	}

	@Override
	public boolean keptOutOfAll() {
		return keepOut.get();
	}

	@Override
	public List<AddonSetting<?>> tabSettings() {
		return List.of(keepOut);
	}

	private static JsonElement builtIn() {
		try (InputStream stream = SkyBlockChatTabFeature.class.getResourceAsStream(BUILT_IN)) {
			if (stream == null) {
				return null;
			}
			try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
				return JsonParser.parseReader(reader);
			}
		} catch (IOException | RuntimeException e) {
			Logs.warn("The chat formats of SkyBlock could not be read: {}", e.toString());
			return null;
		}
	}

	private static JsonElement playersFile() {
		Path file = ModFiles.dir().resolve(PLAYERS_FILE);
		if (!Files.isRegularFile(file)) {
			return null;
		}
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader);
		} catch (IOException | RuntimeException e) {
			Logs.warn("Your chat formats in {} could not be read: {}", PLAYERS_FILE, e.toString());
			return null;
		}
	}
}
