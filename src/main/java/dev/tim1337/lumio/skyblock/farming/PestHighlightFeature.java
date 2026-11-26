package dev.tim1337.lumio.skyblock.farming;

import dev.tim1337.lumio.api.MainSettings;
import dev.tim1337.lumio.api.experimental.debug.DebugHooks;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.setting.ColorChoice;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.skyblock.SkyBlockDebug;
import dev.tim1337.lumio.skyblock.data.SkyBlockArea;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.ArrayList;
import java.util.List;

/**
 * Pest highlight: a glow outline on the pests of the Garden, so they are easy to find among the crops. Only what the player can see is
 * outlined: the outline is Lumio's own, drawn where the pest itself is drawn and hidden by everything in front of it, never through a wall or
 * a crop. Which stand is a pest is told from its name tag (see {@link PestMarks}); the stands are looked through a few times a second and only
 * in the Garden, and a frame only asks a small set whether an entity is in it.
 */
public class PestHighlightFeature extends AddonFeature {
	/** The stands are looked through this often, in ticks: a pest moves, but which stand it is does not change. */
	private static final int LOOK_EVERY_TICKS = 5;
	/** The line and the glow are set in pixels at this GUI scale and look the same at every other. */
	private static final float REFERENCE_GUI_SCALE = 2f;
	/** A stand drawn within this many blocks of a pest's body is that body. */
	private static final double BODY_REACH = 0.5;

	private final Toggle menuColor = add(make().toggle("menu_color", Component.literal("Use the menu color"), true), "General");
	private final ColorChoice color = add(make().color("color", Component.literal("Color"), SystemColors.RED_RGB), "General");
	private final Slider glow = add(make().slider("glow", Component.literal("Glow strength"), 6f, 0f, 12f, 0), "General",
		"How far the soft light reaches");
	private final Slider lineWidth = add(make().slider("line_width", Component.literal("Line width"), 2f, 1f, 5f, 0), "General");

	/** The pests' bodies by entity id: what a frame asks. */
	private IntSet pests = new IntOpenHashSet();
	/** The same bodies as entities, for their places. */
	private List<ArmorStand> bodies = List.of();
	private final List<ArmorStand> tags = new ArrayList<>();
	private final List<ArmorStand> heads = new ArrayList<>();
	private double[] places = new double[48];
	private int tick;
	/** What the debug log was last told, so it is told only when it changes. */
	private int toldTags = -1;
	private int toldBodies = -1;

	public PestHighlightFeature() {
		super("pest_highlight", "Pest highlight",
			"Pests outlined only while you see them.", false);
		iconRgb(SystemColors.GREEN_RGB);
		iconName("bug");
		summary("A glow on pests you can see");
		color.visibleWhen(() -> !menuColor.get());
	}

	/** Looks through the stands of the Garden a few times a second; anywhere else one cheap check. */
	public void tick(Minecraft client) {
		if (!isEnabled() || client.level == null || client.player == null || !SkyBlockArea.inGarden()) {
			if (!pests.isEmpty()) {
				pests = new IntOpenHashSet();
				bodies = List.of();
			}
			toldTags = -1;
			toldBodies = -1;
			return;
		}
		if (++tick % LOOK_EVERY_TICKS != 0) {
			return;
		}
		tags.clear();
		heads.clear();
		IntSet own = new IntOpenHashSet();
		List<ArmorStand> foundBodies = new ArrayList<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (!(entity instanceof ArmorStand stand)) {
				continue;
			}
			Component name = stand.getCustomName();
			if (name != null && PestMarks.pestTag(name.getString())) {
				if (stand.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
					tags.add(stand);
				} else {
					// the name is on the body itself
					own.add(stand.getId());
					foundBodies.add(stand);
				}
			} else if (!stand.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
				heads.add(stand);
			}
		}
		IntSet found = own;
		if (!tags.isEmpty() && !heads.isEmpty()) {
			if (places.length < heads.size() * 3) {
				places = new double[heads.size() * 3];
			}
			for (int i = 0; i < heads.size(); i++) {
				ArmorStand head = heads.get(i);
				places[i * 3] = head.getX();
				places[i * 3 + 1] = head.getY();
				places[i * 3 + 2] = head.getZ();
			}
			for (ArmorStand tag : tags) {
				int body = PestMarks.body(tag.getX(), tag.getY(), tag.getZ(), places, heads.size());
				if (body >= 0) {
					found.add(heads.get(body).getId());
					foundBodies.add(heads.get(body));
				}
			}
		}
		pests = found;
		bodies = foundBodies;
		if (DebugHooks.ACTIVE && (tags.size() != toldTags || found.size() != toldBodies)) {
			toldTags = tags.size();
			toldBodies = found.size();
			DebugHooks.log(SkyBlockDebug.FARMING, "Pest highlight: {} pest name tags, {} bodies outlined, {} stands with a head looked at{}", tags.size(), found.size(),
				heads.size(), !tags.isEmpty() && tags.size() > found.size() ? " (a pest without a body: its name tag is \"" + tags.get(0).getCustomName().getString() + "\")" : "");
		}
		tags.clear();
		heads.clear();
	}

	/** @return whether the entity is a pest's body; asked for every entity in every frame */
	public boolean glows(Entity entity) {
		return !pests.isEmpty() && pests.contains(entity.getId()) && isEnabled();
	}

	/** @return whether any pest is outlined now: the stand model asks, to leave the sticks of a pest's invisible stand out of its outline */
	public boolean anyGlows() {
		return !pests.isEmpty() && isEnabled();
	}

	/**
	 * @return whether a pest's body stands here: the stand model asks with the place it draws a stand at, which is between two ticks, so a
	 *         pest counts within half a block
	 */
	public boolean pestAt(double x, double y, double z) {
		for (int i = 0; i < bodies.size(); i++) {
			ArmorStand body = bodies.get(i);
			if (Math.abs(body.getX() - x) < BODY_REACH && Math.abs(body.getY() - y) < BODY_REACH && Math.abs(body.getZ() - z) < BODY_REACH) {
				return true;
			}
		}
		return false;
	}

	/** @return the glow's color as 0xRRGGBB */
	public int color() {
		return PestMarks.color(menuColor.get(), MainSettings.accentRgb(), color.argb());
	}

	/** @return the line's width in screen pixels, looking the same at every GUI scale */
	public float lineWidth() {
		return lineWidth.get() * Minecraft.getInstance().getWindow().getGuiScale() / REFERENCE_GUI_SCALE;
	}

	/** @return how far the glow reaches around the line, in screen pixels */
	public float glowPixels() {
		return glow.get() * Minecraft.getInstance().getWindow().getGuiScale() / REFERENCE_GUI_SCALE;
	}

	/** @return how many pests are outlined now, for the tests */
	public int outlined() {
		return pests.size();
	}
}
