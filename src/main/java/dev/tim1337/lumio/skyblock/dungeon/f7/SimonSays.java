package dev.tim1337.lumio.skyblock.dungeon.f7;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The order of the Simon Says buttons, without the game, also with the "SS skip" (the start button pressed several times at the start, which
 * skips rounds). It is told what the world shows: the start button pressed, a lantern going out (sea lantern to obsidian: its button joins the
 * order), a button pressed (by anyone: the next press is found from the presses seen, so a teammate's clicks count too), and whether the
 * button grid stands.
 * <p>
 * The first phase begins with a press of the start button and ends when the button grid is back after it was gone, or when the first round is
 * completed. In it the lanterns of a skip show in another order than the buttons are to be pressed, so the stored order is corrected: with the
 * second lantern it is turned around, and when a third shows, the one before the last is dropped. After the first phase the order is as shown.
 * A press of the start button starts over. The rules are those Odin's and NoammAddons' solvers follow; the code is our own.
 */
public final class SimonSays {
	/** The grid counts as gone or back once it was so for this many ticks in a row, so a block update in between is no reset. */
	static final int GRID_TICKS = 3;

	/** What happened, for the debug log. */
	public interface Log {
		void say(String what);
	}

	private final List<Integer> order = new ArrayList<>();
	private final Log log;
	private int next;
	private boolean firstPhase;
	private int startPresses;
	/** Whether the grid stood when last settled, whether it was gone since the first phase began, and for how many ticks it has been otherwise. */
	private boolean gridStands = true;
	private boolean goneInFirstPhase;
	private int gridChangeTicks;

	public SimonSays() {
		this(what -> {
		});
	}

	public SimonSays(Log log) {
		this.log = log;
	}

	/** The start button was pressed: the solution starts over, in the first phase. */
	public void startPressed() {
		order.clear();
		next = 0;
		startPresses = firstPhase ? startPresses + 1 : 1;
		firstPhase = true;
		goneInFirstPhase = false;
		log.say("start pressed (" + startPresses + " in this first phase): solution reset, first phase");
	}

	/** A lantern went out, so it was shown; its button joins the order once. @param button the index of the button (0 to 15) */
	public void shown(int button) {
		if (order.contains(button)) {
			return;
		}
		order.add(button);
		if (firstPhase) {
			if (order.size() == 2) {
				Collections.reverse(order);
			} else if (order.size() == 3) {
				order.remove(order.size() - 2);
			}
		}
		log.say("lantern " + button + " shown" + (firstPhase ? " in the first phase" : "") + ", order " + order);
	}

	/** A button was pressed, by the player or a teammate: the one expected moves on, a later one of the order jumps there. */
	public void pressed(int button) {
		int index = order.indexOf(button);
		if (index < next) {
			return;
		}
		next = index + 1;
		if (next >= order.size()) {
			// the round is done; the next round shows its lanterns from the start, in their order
			order.clear();
			next = 0;
			if (firstPhase) {
				firstPhase = false;
				log.say("first round completed: first phase over");
			}
		}
	}

	/** Once per tick: whether all the buttons of the grid stand. The grid back after it was gone ends the first phase. */
	public void grid(boolean stands) {
		if (stands == gridStands) {
			gridChangeTicks = 0;
			return;
		}
		if (++gridChangeTicks < GRID_TICKS) {
			return;
		}
		gridStands = stands;
		gridChangeTicks = 0;
		if (!stands) {
			goneInFirstPhase |= firstPhase;
		} else if (firstPhase && goneInFirstPhase) {
			firstPhase = false;
			log.say("button grid back: first phase over, order " + order);
		}
	}

	/** Everything forgotten: another world, the run's end, or Goldor's phase beginning. */
	public void reset() {
		if (!order.isEmpty() || firstPhase) {
			log.say("reset");
		}
		order.clear();
		next = 0;
		firstPhase = false;
		startPresses = 0;
		gridStands = true;
		goneInFirstPhase = false;
		gridChangeTicks = 0;
	}

	/** @return where a button stands from the next press (0 is next), or -1 when it is not to be pressed */
	public int place(int button) {
		int index = order.indexOf(button);
		return index < next ? -1 : index - next;
	}

	/** @return the buttons still to be pressed, in order */
	public List<Integer> remaining() {
		return List.copyOf(order.subList(Math.min(next, order.size()), order.size()));
	}

	public int size() {
		return order.size();
	}

	public boolean firstPhase() {
		return firstPhase;
	}

	/** @return how often the start button was pressed in the first phase running, 0 outside of it */
	public int startPresses() {
		return firstPhase ? startPresses : 0;
	}
}
