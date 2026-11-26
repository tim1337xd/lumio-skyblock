package dev.tim1337.lumio.skyblock.dungeon.f7;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Random;

/** The practice terminals can be solved by following the solver, one click at a time, and wrong clicks change nothing. */
class TerminalSimTest {
	@Test
	void everyKindSolvesByTheSolver() {
		for (long seed = 0; seed < 30; seed++) {
			for (Terminals.Kind kind : TerminalSim.KINDS) {
				TerminalSim sim = TerminalSim.create(kind, new Random(seed));
				int guard = 0;
				while (!sim.solved() && guard++ < 200) {
					Terminals.Solution solution = sim.solution();
					int slot = solution.order().getFirst();
					sim.click(slot, solution.clicksAt(slot) < 0);
				}
				assertTrue(sim.solved(), kind + " seed " + seed);
			}
		}
	}

	@Test
	void wrongNumberDoesNothing() {
		TerminalSim sim = TerminalSim.create(Terminals.Kind.NUMBERS, new Random(3));
		int second = sim.solution().order().get(1);
		sim.click(second, false);
		assertTrue(sim.solution().order().contains(second));
		assertFalse(sim.solved());
	}
}
