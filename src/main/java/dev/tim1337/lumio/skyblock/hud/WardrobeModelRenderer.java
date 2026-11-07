package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.skyblock.mixin.accessor.PictureImageAccess;
import dev.tim1337.lumio.skyblock.mixin.accessor.RenderPipelinesAccess;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.gui.BlitRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;

import org.joml.Matrix3x2f;

import java.util.List;

/**
 * Draws all the wardrobe's models into one image, once: while its version stays the same a frame only shows parts of that image, one per card, each with its card's tone. So windows can grow, cards can light up and the grid can scroll, and the models
 * cost nothing per frame however many there are.
 */
public final class WardrobeModelRenderer extends PictureInPictureRenderer<WardrobeModelRenderer.Picture> {
	/** A model in the image: where its feet stand, from the image's top left, and its size in pixels of the interface per block. */
	public record Model(EntityRenderState state, float x, float feet, float perBlock) {
	}

	/** A part of the image shown on the screen, under a pose, from u0, v0 to u1, v1, in a card's tone. */
	public record Part(Matrix3x2f pose, int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1, WardrobeCardLook look) {
	}

	/** The image and the parts of it this frame shows: its version says when it has to be drawn again. */
	public record Picture(int version, List<Model> models, List<Part> parts, int x0, int y0, int x1, int y1, ScreenRectangle scissorArea, ScreenRectangle bounds) implements PictureInPictureRenderState {
		@Override
		public float scale() {
			return 1f;
		}
	}

	private static final RenderPipeline FIGURE = RenderPipeline.builder(RenderPipelinesAccess.evskyblock$guiTextured())
		.withLocation(id("pipeline/wardrobe_figure"))
		.withVertexShader(id("core/wardrobe_figure"))
		.withFragmentShader(id("core/wardrobe_figure"))
		.build();

	/** How many times the image was drawn, for the tests. */
	private static int draws;
	private int drawnVersion = -1;

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("lumio-skyblock", path);
	}

	/** @return how many times an image was drawn so far */
	public static int draws() {
		return draws;
	}

	/** @return whether the figures' shader compiled: one that did not must never be bound */
	private static boolean toned() {
		//#if 26.3
		//$ return RenderSystem.getCompiledPipelineNullable(FIGURE) != null;
		//#else
		return RenderSystem.getDevice().precompilePipeline(FIGURE).isValid();
		//#endif
	}

	@Override
	public Class<Picture> getRenderStateClass() {
		return Picture.class;
	}

	@Override
	protected boolean textureIsReadyToBlit(Picture picture) {
		return picture.version() == drawnVersion;
	}

	@Override
	protected float getTranslateY(int height, int guiScale) {
		return height / 2f;
	}

	@Override
	protected void renderToTexture(Picture picture, PoseStack pose, SubmitNodeCollector collector) {
		drawnVersion = picture.version();
		draws++;
		Minecraft client = Minecraft.getInstance();
		client.gameRenderer.lighting().setupFor(Lighting.Entry.ENTITY_IN_UI);
		var dispatcher = client.getEntityRenderDispatcher();
		CameraRenderState camera = new CameraRenderState();
		float centerX = (picture.x1() - picture.x0()) / 2f;
		float centerY = (picture.y1() - picture.y0()) / 2f;
		for (Model model : picture.models()) {
			pose.pushPose();
			pose.translate(model.x() - centerX, model.feet() - centerY, 0f);
			pose.scale(model.perBlock(), model.perBlock(), model.perBlock());
			// the game's model stands upside down in this space, as in its own inventory
			pose.mulPose(Axis.ZP.rotation((float) Math.PI));
			dispatcher.submit(model.state(), camera, 0.0, 0.0, 0.0, pose, collector);
			pose.popPose();
		}
	}

	@Override
	protected void blitTexture(Picture picture, GuiRenderState state) {
		boolean toned = toned();
		RenderPipeline pipeline = toned ? FIGURE : RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA;
		// filtered: the windows of the other pages show the image smaller
		TextureSetup setup = TextureSetup.singleTexture(((PictureImageAccess) (Object) this).evskyblock$image(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
		for (Part part : picture.parts()) {
			state.addBlitToCurrentLayer(new BlitRenderState(pipeline, setup, part.pose(), part.x0(), part.y0(), part.x1(), part.y1(), part.u0(), part.u1(), part.v0(), part.v1(),
				toned ? part.look().shaderColor() : part.look().plainColor(), picture.scissorArea()));
		}
	}

	@Override
	protected String getTextureLabel() {
		return "lumio wardrobe models";
	}
}
