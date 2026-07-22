package com.xm.thefourthfrequency.ending;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Deterministic attack cadence, shuffle and strong-control admission policy. */
public final class WorldInterfaceActionScheduler {
	public static final int STRONG_CONTROL_IMMUNITY_TICKS = 600;
	public static final int FORCED_EVICTION_WARNING_TICKS = 120;
	public static final int FORCED_EVICTION_COOLDOWN_TICKS = 3_600;
	public static final int RESTART_RECOVERY_TICKS = 40;

	private static final long SHUFFLE_GAMMA = 0x9E3779B97F4A7C15L;
	private static final long INTERVAL_SALT = 0x632BE59BD9B4E019L;

	private WorldInterfaceActionScheduler() {
	}

	public static WorldInterfaceAction nextAction(WorldInterfaceStage stage, long encounterSeed,
			long sequence) {
		return nextAction(stage, encounterSeed, sequence, null);
	}

	/**
	 * Chooses from a deterministic per-cycle shuffle. Supplying the previously emitted action makes
	 * phase boundaries and shuffle-cycle boundaries adjacent-repeat safe.
	 */
	public static WorldInterfaceAction nextAction(WorldInterfaceStage stage, long encounterSeed,
			long sequence, WorldInterfaceAction previousAction) {
		requireCombatStage(stage);
		requireSequence(sequence);
		List<WorldInterfaceAction> eligible = new ArrayList<>(WorldInterfaceAction.unlockedAt(stage));
		int size = eligible.size();
		long cycle = sequence / size;
		shuffle(eligible, mix64(encounterSeed ^ (stage.wireId() * SHUFFLE_GAMMA) ^ cycle));
		if (cycle > 0L && eligible.getFirst() == lastOfPreviousCycle(stage, encounterSeed, cycle, size)) {
			WorldInterfaceAction first = eligible.getFirst();
			eligible.set(0, eligible.get(1));
			eligible.set(1, first);
		}
		int index = (int) (sequence % size);
		WorldInterfaceAction selected = eligible.get(index);
		if (selected == previousAction && size > 1) {
			selected = eligible.get((index + 1) % size);
		}
		return selected;
	}

	private static WorldInterfaceAction lastOfPreviousCycle(WorldInterfaceStage stage, long encounterSeed,
			long cycle, int size) {
		List<WorldInterfaceAction> previous = new ArrayList<>(WorldInterfaceAction.unlockedAt(stage));
		shuffle(previous, mix64(encounterSeed ^ (stage.wireId() * SHUFFLE_GAMMA) ^ (cycle - 1L)));
		return previous.get(size - 1);
	}

	/** Inclusive, unscaled global attack interval for the current combat phase. */
	public static int baseIntervalTicks(WorldInterfaceStage stage, long encounterSeed, long sequence) {
		requireCombatStage(stage);
		requireSequence(sequence);
		IntervalBounds bounds = intervalBounds(stage);
		int span = bounds.maximumTicks - bounds.minimumTicks + 1;
		long value = mix64(encounterSeed ^ INTERVAL_SALT ^ (stage.wireId() * SHUFFLE_GAMMA)
				^ (sequence * 0xD1342543DE82EF95L));
		return bounds.minimumTicks + floorMod(value, span);
	}

	/** Applies the authoritative anchor cooldown multiplier to the deterministic base interval. */
	public static int scaledIntervalTicks(WorldInterfaceStage stage, long encounterSeed, long sequence,
			int destroyedAnchors) {
		double scaled = baseIntervalTicks(stage, encounterSeed, sequence)
				* WorldInterfacePolicy.attackCooldownMultiplier(destroyedAnchors);
		return Math.max(1, (int) Math.round(scaled));
	}

	public static IntervalBounds intervalBounds(WorldInterfaceStage stage) {
		requireCombatStage(stage);
		return switch (stage) {
			case PHASE_1 -> new IntervalBounds(140, 180);
			case PHASE_2 -> new IntervalBounds(100, 140);
			case PHASE_3 -> new IntervalBounds(70, 110);
			default -> throw new IllegalArgumentException("Stage is not a combat phase: " + stage);
		};
	}

	/** Exclusive controls share one global lane, while ordinary attacks do not consume it. */
	public static boolean canStartExclusiveControl(WorldInterfaceAction candidate,
			WorldInterfaceAction activeExclusiveAction) {
		Objects.requireNonNull(candidate, "candidate");
		if (!candidate.requiresExclusiveControl()) return true;
		return activeExclusiveAction == null || !activeExclusiveAction.requiresExclusiveControl();
	}

	/** A player cannot be selected for another strong control until 600 ticks have elapsed. */
	public static boolean isStrongControlTargetEligible(WorldInterfaceAction candidate, long currentTick,
			long targetLastControlledTick) {
		Objects.requireNonNull(candidate, "candidate");
		if (currentTick < 0L) throw new IllegalArgumentException("Current tick cannot be negative");
		if (!candidate.requiresExclusiveControl() || targetLastControlledTick < 0L) return true;
		if (targetLastControlledTick > currentTick) return false;
		return currentTick - targetLastControlledTick >= STRONG_CONTROL_IMMUNITY_TICKS;
	}

	public static boolean canStartAction(WorldInterfaceAction candidate,
			WorldInterfaceAction activeExclusiveAction, long currentTick, long targetLastControlledTick) {
		return canStartExclusiveControl(candidate, activeExclusiveAction)
				&& isStrongControlTargetEligible(candidate, currentTick, targetLastControlledTick);
	}

	public static boolean isForcedEvictionReady(long currentTick, long lastForcedEvictionTick,
			int islandParticipantCount) {
		if (currentTick < 0L) throw new IllegalArgumentException("Current tick cannot be negative");
		if (WorldInterfacePolicy.forcedEvictionTargetCount(islandParticipantCount) == 0) return false;
		if (lastForcedEvictionTick < 0L) return true;
		if (lastForcedEvictionTick > currentTick) return false;
		return currentTick - lastForcedEvictionTick >= FORCED_EVICTION_COOLDOWN_TICKS;
	}

	/**
	 * Selects stable real-disconnect targets. The integrated-server host participates in the count
	 * threshold but is never returned as a target.
	 */
	public static List<UUID> selectForcedEvictionTargets(List<UUID> islandParticipants,
			UUID integratedServerHost, long encounterSeed, long sequence) {
		Objects.requireNonNull(islandParticipants, "islandParticipants");
		requireSequence(sequence);
		int requested = WorldInterfacePolicy.forcedEvictionTargetCount(islandParticipants.size());
		if (new HashSet<>(islandParticipants).size() != islandParticipants.size()
				|| islandParticipants.stream().anyMatch(Objects::isNull)) {
			throw new IllegalArgumentException("Island participant roster must contain unique UUIDs");
		}
		if (requested == 0) return List.of();

		List<UUID> eligible = islandParticipants.stream()
				.filter(id -> !id.equals(integratedServerHost))
				.sorted(Comparator.comparing(UUID::toString))
				.collect(java.util.stream.Collectors.toCollection(ArrayList::new));
		shuffle(eligible, mix64(encounterSeed ^ (sequence * SHUFFLE_GAMMA) ^ 0xA0761D6478BD642FL));
		return List.copyOf(eligible.subList(0, Math.min(requested, eligible.size())));
	}

	private static <T> void shuffle(List<T> values, long key) {
		for (int index = values.size() - 1; index > 0; index--) {
			int swapIndex = floorMod(mix64(key + index * SHUFFLE_GAMMA), index + 1);
			T current = values.get(index);
			values.set(index, values.get(swapIndex));
			values.set(swapIndex, current);
		}
	}

	private static int floorMod(long value, int bound) {
		return (int) Math.floorMod(value, (long) bound);
	}

	private static long mix64(long value) {
		value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
		value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
		return value ^ (value >>> 31);
	}

	private static void requireCombatStage(WorldInterfaceStage stage) {
		if (stage == null || !stage.isCombat()) {
			throw new IllegalArgumentException("Stage must be a combat phase");
		}
	}

	private static void requireSequence(long sequence) {
		if (sequence < 0L) throw new IllegalArgumentException("Sequence cannot be negative");
	}

	public record IntervalBounds(int minimumTicks, int maximumTicks) {
		public IntervalBounds {
			if (minimumTicks < 1 || maximumTicks < minimumTicks) {
				throw new IllegalArgumentException("Invalid attack interval bounds");
			}
		}
	}
}
