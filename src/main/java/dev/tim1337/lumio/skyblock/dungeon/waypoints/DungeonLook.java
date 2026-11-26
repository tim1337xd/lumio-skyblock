package dev.tim1337.lumio.skyblock.dungeon.waypoints;

import static dev.tim1337.lumio.skyblock.SkyBlockAddon.make;

import dev.tim1337.lumio.api.experimental.waypoint.WaypointLook;
import dev.tim1337.lumio.api.experimental.waypoint.WaypointStyle;
import dev.tim1337.lumio.api.setting.AddonSetting;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Slider;
import dev.tim1337.lumio.api.setting.Toggle;

import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The look of the dungeon waypoints: the general one of Waypoints › Look while "Use the general look" is on (as it is at first), or their own,
 * set in the same terms in their sheet. Dungeon labels have no distance with the general look, as they never had one; their own look may add it.
 */
final class DungeonLook {
	static final String GROUP = "Look";
	private static final float PERCENT = 100f;

	private final Toggle useGeneral = make().toggle("use_general_look", Component.literal("Use the general look"), true);
	private final Slider textSize = make().slider("look_text_size", Component.literal("Text size"), PERCENT, 50f, 200f, 0).unit("%");
	private final Choice<WaypointStyle.TextScaling> textScaling = make().choice("look_text_distance", Component.literal("Scale with distance"),
		WaypointStyle.TextScaling.SMALLER_FAR, value -> Component.literal(value.label()), Choice.Style.DROPDOWN);
	private final Toggle showName = make().toggle("look_show_name", Component.literal("Name"), true);
	private final Toggle showDistance = make().toggle("look_show_distance", Component.literal("Distance"), false);
	private final Toggle textShadow = make().toggle("look_text_shadow", Component.literal("Text shadow"), true);
	private final Toggle textBackground = make().toggle("look_text_background", Component.literal("Text background"), false);
	private final Choice<WaypointStyle.Marker> marker = make().choice("look_marker", Component.literal("Marker style"), WaypointStyle.Marker.BOX,
		value -> Component.literal(value.label()), Choice.Style.SEGMENTED);
	private final Slider lineWidth = make().slider("look_line_width", Component.literal("Line width"), WaypointStyle.DEFAULT_LINE_WIDTH, 1f, 6f, 1).unit(" px");
	private final Slider fill = make().slider("look_fill", Component.literal("Fill strength"), PERCENT, 0f, 200f, 0).unit("%");
	private final Toggle beam = make().toggle("look_beam", Component.literal("Beam"), true);
	private final Slider beamHeight = make().slider("look_beam_height", Component.literal("Beam height"), WaypointLook.BEAM_HEIGHT, 8f, 128f, 0).unit(" blocks");
	private final Slider beamStrength = make().slider("look_beam_strength", Component.literal("Beam strength"), PERCENT, 20f, 200f, 0).unit("%");
	private final Toggle fadeNear = make().toggle("look_fade_near", Component.literal("Fade out when very close"), true);
	private final Slider maxDistance = make().slider("look_max_distance", Component.literal("Max distance"), 0f, 0f, 512f, 0).unit(" blocks").offAtZero("Any");
	private final Slider iconSize = make().slider("look_icon_size", Component.literal("Icon size"), PERCENT, 50f, 200f, 0).unit("%");

	private WaypointStyle style = WaypointStyle.DEFAULT;
	private boolean distance;

	DungeonLook() {
		useGeneral.setDescription("Off: the look below");
		maxDistance.setDescription("0 draws at any distance");
		for (AddonSetting<?> setting : own()) {
			setting.visibleWhen(() -> !useGeneral.get());
		}
		lineWidth.visibleWhen(() -> !useGeneral.get() && (marker.get() == WaypointStyle.Marker.BOX || marker.get() == WaypointStyle.Marker.OUTLINE));
		fill.visibleWhen(() -> !useGeneral.get() && marker.get() == WaypointStyle.Marker.BOX);
		beam.visibleWhen(() -> !useGeneral.get() && marker.get() == WaypointStyle.Marker.BEAM);
		beamHeight.visibleWhen(() -> !useGeneral.get() && beam.get() && marker.get() == WaypointStyle.Marker.BEAM);
		beamStrength.visibleWhen(() -> !useGeneral.get() && beam.get() && marker.get() == WaypointStyle.Marker.BEAM);
		iconSize.visibleWhen(() -> !useGeneral.get() && marker.get() == WaypointStyle.Marker.ICON);
		textSize.visibleWhen(() -> !useGeneral.get() && (labelHasText() || iconMarker()));
		textScaling.visibleWhen(() -> !useGeneral.get() && (labelHasText() || iconMarker()));
		textBackground.visibleWhen(() -> !useGeneral.get() && labelHasText() && !iconMarker());
		// the icon's label has no background to replace its shadow, and casts it also without any text
		textShadow.visibleWhen(() -> !useGeneral.get() && (iconMarker() || labelHasText() && !textBackground.get()));
	}

	private boolean labelHasText() {
		return showName.get() || showDistance.get();
	}

	private boolean iconMarker() {
		return marker.get() == WaypointStyle.Marker.ICON;
	}

	private List<AddonSetting<?>> own() {
		return List.of(textSize, textScaling, showName, showDistance, textShadow, textBackground, marker, lineWidth, fill, beam, beamHeight, beamStrength,
			fadeNear, maxDistance, iconSize);
	}

	/** @return the group's settings, in the order they show: the switch, then the dungeon waypoints' own look */
	List<AddonSetting<?>> settings() {
		List<AddonSetting<?>> all = new java.util.ArrayList<>();
		all.add(useGeneral);
		all.addAll(own());
		return all;
	}

	/** Takes the look as it is set now; called once a tick, so drawing reads finished values. */
	void refresh() {
		boolean general = useGeneral.get();
		WaypointStyle own = general ? null : new WaypointStyle(textSize.get() / PERCENT, textScaling.get(), showName.get(), showDistance.get(),
			textShadow.get(), textBackground.get(), marker.get(), lineWidth.get(), fill.get() / PERCENT, beam.get(), beamHeight.get(),
			beamStrength.get() / PERCENT, fadeNear.get(), Math.round(maxDistance.get()), iconSize.get() / PERCENT);
		style = WaypointStyle.pick(general, WaypointStyle.general(), own);
		distance = showsDistance(general, style);
	}

	/** @return the look the dungeon waypoints draw by */
	WaypointStyle style() {
		return style;
	}

	/** @return whether the labels have the distance */
	boolean distance() {
		return distance;
	}

	/** @return whether dungeon labels have the distance: never with the general look (they never had one), with their own look where it says so */
	static boolean showsDistance(boolean useGeneral, WaypointStyle style) {
		return !useGeneral && style.showDistance();
	}
}
