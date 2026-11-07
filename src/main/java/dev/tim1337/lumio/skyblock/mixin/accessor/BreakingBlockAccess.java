package dev.tim1337.lumio.skyblock.mixin.accessor;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The block being broken, for counting the ticks it takes, and the item it is broken with, which Break Reset Fix keeps up to date. */
@Mixin(MultiPlayerGameMode.class)
public interface BreakingBlockAccess {
	@Accessor("destroyBlockPos")
	BlockPos evskyblock$destroyPos();

	@Accessor("destroyingItem")
	void evskyblock$setDestroyingItem(ItemStack stack);

	@Accessor("destroyingItem")
	ItemStack evskyblock$destroyingItem();

	@Accessor("destroyProgress")
	float evskyblock$destroyProgress();
}
