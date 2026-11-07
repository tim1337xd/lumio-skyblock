package dev.tim1337.lumio.skyblock.mixin.accessor;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The add-on's own access to a container screen's slot area: its position and size for what is drawn next to it, the hovered slot, and the
 * area moved for the storage overlay. Separate from the main mod's accessor, with its own names, so the two never clash.
 */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccess {
	@Accessor("leftPos")
	int evskyblock$left();

	@Accessor("topPos")
	int evskyblock$top();

	@Accessor("imageWidth")
	int evskyblock$width();

	@Accessor("imageHeight")
	int evskyblock$height();

	@Accessor("hoveredSlot")
	Slot evskyblock$hoveredSlot();

	@Mutable
	@Accessor("imageHeight")
	void evskyblock$setHeight(int height);

	@Accessor("topPos")
	void evskyblock$setTop(int top);

	@Accessor("leftPos")
	void evskyblock$setLeft(int left);

	@Mutable
	@Accessor("imageWidth")
	void evskyblock$setWidth(int width);
}
