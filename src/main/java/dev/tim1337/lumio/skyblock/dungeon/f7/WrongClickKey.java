package dev.tim1337.lumio.skyblock.dungeon.f7;

import dev.tim1337.lumio.skyblock.SkyBlockAddon;
import dev.tim1337.lumio.skyblock.dungeon.DungeonKeys;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/**
 * The key that lets every click through while it is held, shared by the terminal and the device solver's "Block wrong clicks", and the sound
 * of a blocked click. The key starts on Left Shift (asked for by name; the one key of the add-on with a key set at first), is listed in both
 * solvers' sheets, and is read from the keyboard itself, so it also counts while a menu is open.
 */
public final class WrongClickKey {
	private static final float BLOCKED_PITCH = 0.6f;
	private static KeyMapping key;

	private WrongClickKey() {
	}

	/** @return the key, registered on first use (while the add-on starts) */
	public static KeyMapping key() {
		if (key == null) {
			key = KeyMappingHelper.registerKeyMapping(new KeyMapping("key." + SkyBlockAddon.ID + ".dungeon_wrong_click_override", InputConstants.Type.KEYSYM,
				InputConstants.KEY_LSHIFT, DungeonKeys.CATEGORY));
		}
		return key;
	}

	/** @return whether the player holds the key now, in the world or in a menu; never with no key set */
	public static boolean holding() {
		if (key == null || key.isUnbound()) {
			return false;
		}
		InputConstants.Key bound = KeyMappingHelper.getBoundKeyOf(key);
		if (bound.getType() != InputConstants.Type.KEYSYM) {
			return key.isDown();
		}
		//#if 26.3
		//$ return InputConstants.isKeyDown(bound.getValue());
		//#else
		return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), bound.getValue());
		//#endif
	}

	/** The short low sound of a click that was not sent. */
	public static void playBlocked() {
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), BLOCKED_PITCH));
	}
}
