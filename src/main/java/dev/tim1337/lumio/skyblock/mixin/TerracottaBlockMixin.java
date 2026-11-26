package dev.tim1337.lumio.skyblock.mixin;

import dev.tim1337.lumio.skyblock.SkyBlockAddon;
import dev.tim1337.lumio.skyblock.SkyBlockFeatures;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Every block the server changes passes here once: the F6 terracotta timers learn of a terracotta's death from the flower pot the server places
 * where it stood, and the Powder Helper of the treasure chests that appear and vanish near the player. Each leaves after one boolean outside its
 * place (Sadan's boss room, the Crystal Hollows). Only observed; the change goes on unchanged.
 */
@Mixin(ClientLevel.class)
public abstract class TerracottaBlockMixin {
	@Inject(method = "sendBlockUpdated", at = @At("HEAD"))
	private void evskyblock$watchFlowerPots(BlockPos pos, BlockState before, BlockState after, int flags, CallbackInfo ci) {
		if (SkyBlockAddon.active()) {
			SkyBlockFeatures.DUNGEON_TERRACOTTA.blockChanged(pos, before, after);
			SkyBlockFeatures.POWDER_HELPER.blockChanged(pos, before, after);
		}
	}
}
