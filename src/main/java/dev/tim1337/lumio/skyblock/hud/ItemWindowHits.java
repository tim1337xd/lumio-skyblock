package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.experimental.draw.PanelKit;

import java.util.ArrayList;
import java.util.List;

/** The entries of the item window as drawn last (an ingredient, a drop, a mob, a use): where each lies and what a click on it opens. */
final class ItemWindowHits {
	private final List<int[]> boxes = new ArrayList<>();
	private final List<String> ids = new ArrayList<>();

	void clear() {
		boxes.clear();
		ids.clear();
	}

	void add(int[] box, String id) {
		boxes.add(box);
		ids.add(id);
	}

	/** @return the entry under a point, or null */
	String at(double x, double y) {
		for (int i = 0; i < boxes.size(); i++) {
			if (PanelKit.inside(boxes.get(i), x, y)) {
				return ids.get(i);
			}
		}
		return null;
	}
}
