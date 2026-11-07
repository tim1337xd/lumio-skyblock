package dev.tim1337.lumio.skyblock.mixin;

import dev.tim1337.lumio.skyblock.SkyBlockAddon;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;

/** Keep Terrain Loaded: chunks the server unloads are kept, and chunks it sends are saved, on the render thread after the game's own handling. */
@Mixin(ClientPacketListener.class)
public abstract class KeepTerrainPacketMixin {
	@Shadow
	private ClientLevel level;

	private static final String ON_THREAD = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V";

	@Inject(method = "handleForgetLevelChunk", at = @At(value = "INVOKE", target = ON_THREAD, shift = At.Shift.AFTER), cancellable = true)
	private void lumio$keep(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
		if (SkyBlockAddon.active() && SkyBlockFeatures.KEEP_TERRAIN.keep(level, packet.pos())) {
			ci.cancel();
		}
	}

	@Inject(method = "handleLevelChunkWithLight", at = @At(value = "INVOKE", target = ON_THREAD, shift = At.Shift.AFTER))
	private void lumio$save(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
		if (SkyBlockAddon.active()) {
			SkyBlockFeatures.KEEP_TERRAIN.onChunk(level, packet);
		}
	}
}
