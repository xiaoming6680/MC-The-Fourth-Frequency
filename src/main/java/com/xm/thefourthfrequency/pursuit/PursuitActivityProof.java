package com.xm.thefourthfrequency.pursuit;

/** Independent early-game routes that prove a player is actively progressing. */
public enum PursuitActivityProof {
	MINING(0),
	EXPLORATION(1),
	LOOTING(2),
	BUILDING(3),
	TRADING(4);

	private final int bit;

	PursuitActivityProof(int bit) {
		this.bit = bit;
	}

	public int mask() {
		return 1 << bit;
	}

	public boolean present(int proofMask) {
		return (proofMask & mask()) != 0;
	}

	public static boolean any(int proofMask) {
		return (proofMask & knownMask()) != 0;
	}

	public static int knownMask() {
		int mask = 0;
		for (PursuitActivityProof proof : values()) mask |= proof.mask();
		return mask;
	}
}
