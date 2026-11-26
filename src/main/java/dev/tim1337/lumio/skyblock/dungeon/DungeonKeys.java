package dev.tim1337.lumio.skyblock.dungeon;

import dev.tim1337.lumio.skyblock.SkyBlockAddon;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

/** The keys of the dungeon features, in a category of their own; every one is registered without a key set. */
public final class DungeonKeys {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SkyBlockAddon.ID, "dungeons"));

	private DungeonKeys() {
	}

	/** @return a new dungeon key, unbound */
	public static KeyMapping register(String name) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + SkyBlockAddon.ID + "." + name, InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(), CATEGORY));
	}

	/** @return how often a key was pressed since the last tick */
	public static int presses(KeyMapping key) {
		int count = 0;
		while (key != null && key.consumeClick()) {
			count++;
		}
		return count;
	}
}
