package dev.tim1337.lumio.skyblock.mixin.accessor;

import net.minecraft.client.multiplayer.ClientPacketListener;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The server's chunk view distance, which the chunk cache returns to when Keep Terrain Loaded stops. */
@Mixin(ClientPacketListener.class)
public interface ChunkRadiusAccess {
	@Accessor("serverChunkRadius")
	int evskyblock$serverChunkRadius();
}
