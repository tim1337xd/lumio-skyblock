package dev.tim1337.lumio.skyblock.experiments;

/**
 * Whether a click of the player in Chronomatron or Ultrasequencer is held back. It only ever keeps a wrong click from going out: nothing is sent, delayed, added or changed. Plain logic, so it is unit tested. Inspired by SkyHanni's option; the code
 * is this mod's own.
 */
final class WrongClickGuard {
	private WrongClickGuard() {
	}

	/**
	 * @param switchOn      the player's switch
	 * @param privateIsland whether the player is on the private island
	 * @param shiftHeld     whether Shift is held, which lets any click through
	 * @param stepsLeft     whether the player repeats the order now and steps of this round are left
	 * @param right         whether the click is the next step
	 * @return whether the click is held back
	 */
	static boolean holdsBack(boolean switchOn, boolean privateIsland, boolean shiftHeld, boolean stepsLeft, boolean right) {
		return switchOn && privateIsland && !shiftHeld && stepsLeft && !right;
	}
}
