package dev.tim1337.lumio.skyblock.mixin;

import dev.tim1337.lumio.skyblock.SkyBlockAddon;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import net.minecraft.client.multiplayer.ClientChunkCache;

/** While Keep Terrain Loaded is active, the client chunk cache spans the render distance instead of only the server's view distance. */
@Mixin(ClientChunkCache.class)
public abstract class KeepTerrainChunkCacheMixin {
	@ModifyVariable(method = "updateViewRadius", at = @At("HEAD"), argsOnly = true, ordinal = 0)
	private int lumio$keepFar(int radius) {
		return !SkyBlockAddon.active() ? radius : SkyBlockFeatures.KEEP_TERRAIN.storageRadius(radius);
	}
}
