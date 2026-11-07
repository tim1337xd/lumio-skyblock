package dev.tim1337.lumio.skyblock.mixin.accessor;

import com.mojang.blaze3d.textures.GpuTextureView;

import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The add-on's access to the image a picture-in-picture renderer drew, to show parts of it: the wardrobe's figures, each toned by its card. */
@Mixin(PictureInPictureRenderer.class)
public interface PictureImageAccess {
	@Accessor("textureView")
	GpuTextureView evskyblock$image();
}
