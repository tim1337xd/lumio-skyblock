package dev.tim1337.lumio.skyblock.dragons;

import java.util.ArrayList;
import java.util.List;

/** One dragon as it went for the player: its kind, the eyes placed, the place and weight, what was picked up. */
public final class DragonRecord {
	final String kind;
	final int eyes;
	boolean counted;
	int place;
	long weight;
	final List<String> drops = new ArrayList<>();

	DragonRecord(String kind, int eyes, boolean counted) {
		this.kind = kind;
		this.eyes = eyes;
		this.counted = counted;
	}
}
