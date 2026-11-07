package dev.tim1337.lumio.skyblock.hud;

/**
 * How a card is toned: how much color and light its figure has, how visible it is, and how much of a background it has. Plain numbers, so it is unit tested.
 *
 * @param saturation how much color the figure keeps, from grey (0) to its own (1)
 * @param brightness how much light it keeps
 * @param opacity    how visible it is
 * @param background how much of a white background the card has
 */
public record WardrobeCardLook(float saturation, float brightness, float opacity, float background) {
	/** What a card shows. */
	public enum Kind {
		WORN, OPEN_PAGE, OTHER_PAGE, EMPTY, LOCKED
	}

	private static final WardrobeCardLook WORN = new WardrobeCardLook(1f, 1f, 1f, 0f);
	private static final WardrobeCardLook OPEN_PAGE = new WardrobeCardLook(0.55f, 0.7f, 1f, 0.035f);
	private static final WardrobeCardLook OTHER_PAGE = new WardrobeCardLook(0f, 0.4f, 0.55f, 0.02f);
	private static final WardrobeCardLook EMPTY = new WardrobeCardLook(1f, 0.5f, 0.55f, 0.035f);
	private static final WardrobeCardLook LOCKED = new WardrobeCardLook(1f, 0.35f, 0.45f, 0.035f);
	private static final WardrobeCardLook FULL = new WardrobeCardLook(1f, 1f, 1f, 0.09f);

	/**
	 * @param state    what the slot's button last said, or null for a slot not seen yet
	 * @param holds    whether it holds pieces
	 * @param openPage whether it is on the open page
	 * @return what a card shows. The worn set stands out on any page; a slot not seen yet is one of another page
	 */
	public static Kind kind(WardrobeMenu.State state, boolean holds, boolean openPage) {
		if (state == WardrobeMenu.State.EQUIPPED) {
			return Kind.WORN;
		}
		if (state == WardrobeMenu.State.LOCKED) {
			return Kind.LOCKED;
		}
		if (state != null && !holds) {
			return Kind.EMPTY;
		}
		return openPage && state != null ? Kind.OPEN_PAGE : Kind.OTHER_PAGE;
	}

	/** @param hover how far the mouse has brought it back, from 0 to 1: any card comes back to its full color */
	public static WardrobeCardLook of(Kind kind, float hover) {
		WardrobeCardLook rest = switch (kind) {
			case WORN -> WORN;
			case OPEN_PAGE -> OPEN_PAGE;
			case OTHER_PAGE -> OTHER_PAGE;
			case EMPTY -> EMPTY;
			case LOCKED -> LOCKED;
		};
		return new WardrobeCardLook(mix(rest.saturation, FULL.saturation, hover), mix(rest.brightness, FULL.brightness, hover), mix(rest.opacity, FULL.opacity, hover),
			kind == Kind.WORN ? 0f : mix(rest.background, FULL.background, hover));
	}

	private static float mix(float from, float to, float amount) {
		return from + (to - from) * amount;
	}

	/** @return what the figure's shader takes as the vertex color: saturation in red, brightness in green, opacity in alpha */
	public int shaderColor() {
		return Math.round(opacity * 255f) << 24 | Math.round(saturation * 255f) << 16 | Math.round(brightness * 255f) << 8;
	}

	/** @return the plain tint for a graphics card that rejects the shader: it can darken but not take the color out */
	public int plainColor() {
		int light = Math.round(brightness * opacity * 255f);
		return Math.round(opacity * 255f) << 24 | light << 16 | light << 8 | light;
	}
}
