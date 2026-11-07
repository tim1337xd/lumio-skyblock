package dev.tim1337.lumio.skyblock.hud;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.experimental.draw.Draw;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.skyblock.data.ItemGains;
import dev.tim1337.lumio.skyblock.data.ItemRepo;
import dev.tim1337.lumio.skyblock.mixin.accessor.ContainerScreenAccess;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Lights up the open inventory or chest while the search's light is on ({@link SearchLight}): matching slots get a ring in the accent, every other slot dims. Which
 * slots match is worked out again only when the search or the container's contents change, not every frame.
 */
final class SlotHighlights {
	private static final int MOST_SLOTS = 128;
	private static final int DIM = 0x99000000;

	private final boolean[] matching = new boolean[MOST_SLOTS];
	private Object screenFor;
	private int stateFor = -1;
	private String searchFor = "";
	private boolean loreFor;
	private int matches;

	/** @return how many slots of the open screen match the search */
	int matches() {
		return matches;
	}

	/** Works out the matching slots again if the search or the container changed. */
	void update(AbstractContainerScreen<?> screen, String search, boolean inLore) {
		int state = screen.getMenu().getStateId();
		String query = search.strip().toLowerCase(Locale.ROOT);
		if (screen == screenFor && state == stateFor && query.equals(searchFor) && inLore == loreFor) {
			return;
		}
		screenFor = screen;
		stateFor = state;
		searchFor = query;
		loreFor = inLore;
		matches = 0;
		String[] words = query.isEmpty() ? new String[0] : query.split("\\s+");
		var slots = screen.getMenu().slots;
		for (int i = 0; i < slots.size() && i < MOST_SLOTS; i++) {
			ItemStack stack = slots.get(i).getItem();
			matching[i] = words.length > 0 && !stack.isEmpty() && matches(stack, words, inLore);
			if (matching[i]) {
				matches++;
			}
		}
	}

	private static boolean matches(ItemStack stack, String[] words, boolean inLore) {
		String id = ItemGains.idOf(stack);
		ItemRepo.Entry entry = id == null ? null : ItemRepo.get(id);
		if (entry != null) {
			return ItemRepo.matches(entry, words, inLore) > 0;
		}
		String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
		for (String word : words) {
			if (!name.contains(word)) {
				return false;
			}
		}
		return true;
	}

	/** Draws the rings and the dimming over the screen's slots, while the search has text. */
	void draw(Canvas ui, AbstractContainerScreen<?> screen, float shown) {
		if (searchFor.isEmpty() || shown <= 0.01f) {
			return;
		}
		var access = (ContainerScreenAccess) screen;
		int left = access.evskyblock$left();
		int top = access.evskyblock$top();
		var slots = screen.getMenu().slots;
		ui.graphics().nextStratum();
		for (int i = 0; i < slots.size() && i < MOST_SLOTS; i++) {
			Slot slot = slots.get(i);
			int x = left + slot.x - 1;
			int y = top + slot.y - 1;
			if (matching[i]) {
				ui.ring(x, y, 18, 18, Radius.XS, 1, Draw.withOpacity(ui.accent(), shown));
			} else {
				ui.fill(x + 1, y + 1, 16, 16, Draw.withOpacity(DIM, shown));
			}
		}
	}
}
