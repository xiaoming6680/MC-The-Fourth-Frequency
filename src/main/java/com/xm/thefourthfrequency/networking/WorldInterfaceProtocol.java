package com.xm.thefourthfrequency.networking;

import net.minecraft.network.RegistryFriendlyByteBuf;

import java.util.List;
import java.util.Objects;

/** Stable wire identifiers and shared bounds for the v1 world-interface protocol. */
public final class WorldInterfaceProtocol {
	public static final int VERSION = 1;
	public static final int MAX_PARTICIPANTS = 8;
	public static final int MAX_GATEWAYS = 20;
	public static final int ANCHOR_MASK = 0x03FF;
	public static final long COLLAPSE_DURATION_TICKS = 12_000L;

	private WorldInterfaceProtocol() {
	}

	public interface WireValue {
		int wireId();
	}

	public enum Stage implements WireValue {
		UNPREPARED(0),
		ARENA_READY(1),
		WAITING_TERMINALS(2),
		SUMMONING(3),
		PHASE_1(4),
		PHASE_2(5),
		PHASE_3(6),
		SUCCESS_RESOLUTION(7),
		FAILURE_RESOLUTION(8),
		PORTAL_OPEN(9),
		COMPLETE(10);

		private final int wireId;
		Stage(int wireId) { this.wireId = wireId; }
		@Override public int wireId() { return wireId; }
		public static Stage fromWireId(int wireId) { return decode(values(), wireId, "stage"); }
	}

	public enum Form implements WireValue {
		NONE(0),
		LISTENING_EMBRYO(1),
		FREQUENCY_DEVOURER(2),
		WORLD_INTERFACE(3);

		private final int wireId;
		Form(int wireId) { this.wireId = wireId; }
		@Override public int wireId() { return wireId; }
		public static Form fromWireId(int wireId) { return decode(values(), wireId, "form"); }
	}

	public enum GatewayState implements WireValue {
		DORMANT(0),
		PURPLE(1),
		GOLD(2),
		RED(3);

		private final int wireId;
		GatewayState(int wireId) { this.wireId = wireId; }
		@Override public int wireId() { return wireId; }
		public static GatewayState fromWireId(int wireId) { return decode(values(), wireId, "gateway state"); }
	}

	public enum Outcome implements WireValue {
		NONE(0),
		SUCCESS(1),
		FAILURE(2);

		private final int wireId;
		Outcome(int wireId) { this.wireId = wireId; }
		@Override public int wireId() { return wireId; }
		public static Outcome fromWireId(int wireId) { return decode(values(), wireId, "outcome"); }
	}

	public enum AltarAction implements WireValue {
		DEPOSIT(1),
		WITHDRAW(2),
		CANCEL(3);

		private final int wireId;
		AltarAction(int wireId) { this.wireId = wireId; }
		@Override public int wireId() { return wireId; }
		public static AltarAction fromWireId(int wireId) { return decode(values(), wireId, "altar action"); }
	}

	/** Fixed client-visible ritual results. Internal journal errors collapse to UNKNOWN. */
	public enum AltarStatus implements WireValue {
		WAITING(0, "waiting"),
		READY(1, "ready"),
		INVALID_CONTEXT(2, "invalid_context"),
		ALREADY_DEPOSITED(3, "already_deposited"),
		REVISION_MISMATCH(4, "revision_mismatch"),
		RITUAL_NOT_WAITING(5, "ritual_not_waiting"),
		INVALID_ROSTER_SIZE(6, "invalid_roster_size"),
		ROSTER_CHANGED(7, "roster_changed"),
		VALID_BOUND_TERMINAL_MISSING(8, "valid_bound_terminal_missing"),
		TERMINAL_MISMATCH(9, "terminal_mismatch"),
		TERMINAL_DISAPPEARED(10, "terminal_disappeared"),
		TERMINAL_DEPOSITED(11, "terminal_deposited"),
		SACRIFICE_COMMITTED(12, "sacrifice_committed"),
		NOTHING_DEPOSITED(13, "nothing_deposited"),
		ALREADY_COMMITTED(14, "already_committed"),
		WITHDRAWN(15, "withdrawn"),
		RITUAL_ALREADY_EMPTY(16, "ritual_already_empty"),
		ROLLBACK_ALREADY_PENDING(17, "rollback_already_pending"),
		NOT_IN_FROZEN_ROSTER(18, "not_in_frozen_roster"),
		CANCELLED(19, "cancelled"),
		PREPARED_RECOVERY(20, "prepared_recovery"),
		STATE_UNAVAILABLE(21, "state_unavailable"),
		ENCOUNTER_MISMATCH(22, "encounter_mismatch"),
		INVALID_MUTATION_RITUAL_NOT_WAITING(23, "invalid_mutation_ritual_not_waiting"),
		INVALID_MUTATION_ROSTER_CHANGED(24, "invalid_mutation_roster_changed"),
		INVALID_MUTATION_PREPARED_TRANSACTION_MISSING(25, "invalid_mutation_prepared_transaction_missing"),
		INVALID_MUTATION_TRANSACTION_MISSING(26, "invalid_mutation_transaction_missing"),
		PREPARED_TRANSACTION_MISSING(27, "prepared_transaction_missing"),
		TRANSACTION_MISSING(28, "transaction_missing"),
		SACRIFICE_NOT_READY(29, "sacrifice_not_ready"),
		UNKNOWN(30, "unknown");

		private final int wireId;
		private final String key;
		AltarStatus(int wireId, String key) { this.wireId = wireId; this.key = key; }
		@Override public int wireId() { return wireId; }
		public String translationKey() {
			return "screen.thefourthfrequency.resonance_altar.status." + key;
		}
		public static AltarStatus fromWireId(int wireId) { return decode(values(), wireId, "altar status"); }
		public static AltarStatus fromReason(String reason) {
			String normalized = reason == null ? "unknown" : reason.replace(':', '_');
			if ("rollback_pending".equals(normalized)) return ROLLBACK_ALREADY_PENDING;
			for (AltarStatus status : values()) if (status.key.equals(normalized)) return status;
			return UNKNOWN;
		}
	}

	public enum BossAction implements WireValue {
		NONE(0),
		LASER_SWEEP(1),
		ENERGY_ORB(2),
		GRAB_SLAM(3),
		MENTAL_ATTACK(4),
		WEAPON_CHARGE(5),
		GRAB_THROW(6),
		HOTBAR_PURGE(7),
		ARROW_REFLECTION(8),
		FORCED_EXPULSION(9),
		SUMMONING(10),
		MORPH_TO_SECOND(11),
		MORPH_TO_THIRD(12),
		SUCCESS_DEATH(13),
		FAILURE_ESCAPE(14);

		private final int wireId;
		BossAction(int wireId) { this.wireId = wireId; }
		@Override public int wireId() { return wireId; }
		public static BossAction fromWireId(int wireId) { return decode(values(), wireId, "boss action"); }
	}

	public enum PoemCompletion implements WireValue {
		READ(1),
		SKIPPED(2);

		private final int wireId;
		PoemCompletion(int wireId) { this.wireId = wireId; }
		@Override public int wireId() { return wireId; }
		public static PoemCompletion fromWireId(int wireId) { return decode(values(), wireId, "poem completion"); }
	}

	static void requireVersion(int protocolVersion) {
		if (protocolVersion != VERSION) {
			throw new IllegalArgumentException("Unsupported world-interface protocol " + protocolVersion);
		}
	}

	static void requireSequence(long sequence) {
		if (sequence < 0) throw new IllegalArgumentException("Sequence must be non-negative");
	}

	static int readBoundedSize(RegistryFriendlyByteBuf buffer, int maximum, String label) {
		int size = buffer.readVarInt();
		if (size < 0 || size > maximum) {
			throw new IllegalArgumentException(label + " size " + size + " exceeds " + maximum);
		}
		return size;
	}

	static <T> List<T> copyBounded(List<T> values, int maximum, String label) {
		Objects.requireNonNull(values, label);
		if (values.size() > maximum) {
			throw new IllegalArgumentException(label + " size " + values.size() + " exceeds " + maximum);
		}
		for (T value : values) Objects.requireNonNull(value, label + " entry");
		return List.copyOf(values);
	}

	static String requireUtf(String value, int maximumLength, String label) {
		Objects.requireNonNull(value, label);
		if (value.length() > maximumLength) {
			throw new IllegalArgumentException(label + " is longer than " + maximumLength);
		}
		return value;
	}

	private static <T extends Enum<T> & WireValue> T decode(T[] values, int wireId, String label) {
		for (T value : values) if (value.wireId() == wireId) return value;
		throw new IllegalArgumentException("Unknown " + label + " wire id " + wireId);
	}
}
