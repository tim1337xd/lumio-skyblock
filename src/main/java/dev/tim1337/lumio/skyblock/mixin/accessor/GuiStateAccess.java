package dev.tim1337.lumio.skyblock.mixin.accessor;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The add-on's access to what a frame of the interface collects, to add the wardrobe's image of models to it. */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiStateAccess {
	@Accessor("guiRenderState")
	GuiRenderState evskyblock$state();
}
