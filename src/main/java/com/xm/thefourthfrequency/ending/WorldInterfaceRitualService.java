package com.xm.thefourthfrequency.ending;

import com.xm.thefourthfrequency.content.ModBlocks;
import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.ending.WorldInterfaceState.MutationResult;
import com.xm.thefourthfrequency.ending.WorldInterfaceState.Snapshot;
import com.xm.thefourthfrequency.ending.WorldInterfaceState.TerminalTransaction;
import com.xm.thefourthfrequency.ending.WorldInterfaceState.TerminalTransactionState;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import com.xm.thefourthfrequency.world.TerminalLifecycleService;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;

import java.util.LinkedHashSet;
import java.util.Comparator;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Server-side journal and recovery loop for terminal sacrifice at the resonance core. */
public final class WorldInterfaceRitualService {
	private static final double MAX_INTERACTION_DISTANCE_SQUARED = 8.0D * 8.0D;
	private static AltarOpenHandler altarOpenHandler = (player, position) -> false;

	private WorldInterfaceRitualService() {
	}

	public static void initialize() {
		ServerPlayerEvents.JOIN.register(WorldInterfaceRitualService::reconcilePlayer);
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> reconcilePlayer(newPlayer));
		ServerTickEvents.END_SERVER_TICK.register(WorldInterfaceRitualService::tick);
	}

	/**
	 * The encounter orchestrator installs its screen-opening callback here. This
	 * keeps the block entity a proxy and avoids giving it ending authority.
	 */
	public static void registerAltarOpenHandler(AltarOpenHandler handler) {
		altarOpenHandler = Objects.requireNonNull(handler, "handler");
	}

	public static boolean openAltar(ServerPlayer player, BlockPos corePosition) {
		Snapshot snapshot = WorldInterfaceState.snapshot(player.level().getServer());
		if (!actionContextValid(player, snapshot, snapshot.encounterId().orElse(null), snapshot.revision(),
				corePosition, false)) return false;
		return altarOpenHandler.open(player, corePosition);
	}

	public static RitualResult deposit(ServerPlayer player, UUID encounterId, long expectedRevision) {
		MinecraftServer server = player.level().getServer();
		Snapshot before = WorldInterfaceState.snapshot(server);
		if (!actionContextValid(player, before, encounterId, expectedRevision, before.altarCenter(), false)) {
			return reject(before, "invalid_context");
		}
		TerminalTransaction existing = before.terminalTransactions().get(player.getUUID());
		if (existing != null && existing.state() == TerminalTransactionState.COMMITTED) {
			return RitualResult.idempotent(before, "already_deposited");
		}
		if (before.stage() != WorldInterfaceStage.WAITING_TERMINALS || before.sacrificeCommitted()) {
			return reject(before, "ritual_not_waiting");
		}
		if (existing != null && existing.state() == TerminalTransactionState.RETURN_PENDING) {
			return reject(before, "rollback_pending");
		}

		// Once every removal is durable, recovery must roll the journal forward even if the
		// server stopped before the single COMMITTED write. Returning at that boundary would
		// make the same persisted transaction resolve differently after a restart.
		if (readyToCommit(before)) return commitReady(server, before);
		Set<UUID> eligible = eligiblePlayers(server);
		if (eligible.isEmpty() || eligible.size() > WorldInterfaceState.MAX_ROSTER_SIZE
				|| !eligible.contains(player.getUUID())) return reject(before, "invalid_roster_size");
		if (!before.frozenRoster().isEmpty() && !before.frozenRoster().equals(eligible)) {
			return rollback(server, before, "roster_changed");
		}
		if (existing != null && existing.state() == TerminalTransactionState.REMOVED) {
			return RitualResult.idempotent(before, "already_deposited");
		}
		if (expectedRevision > before.revision()
				|| expectedRevision < before.revision() && before.frozenRoster().isEmpty()) {
			return reject(before, "revision_mismatch");
		}

		FrequencyWorldData data = FrequencyWorldData.get(server);
		LocatedTerminal located = findValidBoundTerminal(player, data);
		if (located == null) return reject(before, "valid_bound_terminal_missing");
		String terminalId = TerminalData.terminalId(located.stack());
		int generation = TerminalData.copyGeneration(located.stack());
		if (existing != null && (!existing.terminalId().equals(terminalId)
				|| existing.generation() != generation)) return reject(before, "terminal_mismatch");

		Snapshot prepared = before;
		if (existing == null) {
			TerminalTransaction transaction = new TerminalTransaction(player.getUUID(), terminalId, generation,
					TerminalTransactionState.PREPARED, Math.max(0L, player.level().getGameTime()),
					TerminalData.copyTag(located.stack()));
			MutationResult journal = WorldInterfaceState.mutate(server, encounterId, before.revision(), state -> {
				if (state.stage() != WorldInterfaceStage.WAITING_TERMINALS) {
					throw new IllegalStateException("ritual_not_waiting");
				}
				if (state.frozenRoster().isEmpty()) state.freezeRoster(eligible);
				else if (!state.frozenRoster().equals(eligible)) throw new IllegalStateException("roster_changed");
				state.putTerminalTransaction(transaction);
				state.setGateState(WorldInterfaceGatewayState.PURPLE);
			});
			if (!journal.applied()) return reject(journal.snapshot(), journal.reason());
			prepared = journal.snapshot();
		}

		// Re-resolve the slot after the durable PREPARED write. No client slot is trusted.
		located = findMatchingTerminal(player, data, terminalId, generation);
		if (located == null) {
			return markReturnPending(server, prepared, player.getUUID(), "terminal_disappeared");
		}
		removeMatchingTerminals(player, data, terminalId, generation);
		TerminalLifecycleService.clearTransientRecovery(player.getUUID());

		MutationResult removed = WorldInterfaceState.mutate(server, encounterId, prepared.revision(), state -> {
			TerminalTransaction transaction = state.terminalTransactions().get(player.getUUID());
			if (transaction == null || transaction.state() != TerminalTransactionState.PREPARED) {
				throw new IllegalStateException("prepared_transaction_missing");
			}
			state.putTerminalTransaction(transaction.withState(TerminalTransactionState.REMOVED));
		});
		if (!removed.applied()) {
			// PREPARED remains a durable return entitlement if the second write failed.
			return reject(removed.snapshot(), removed.reason());
		}

		Snapshot afterRemoval = removed.snapshot();
		if (!readyToCommit(afterRemoval)) return RitualResult.applied(afterRemoval, "terminal_deposited");
		return commitReady(server, afterRemoval);
	}

	public static RitualResult withdraw(ServerPlayer player, UUID encounterId, long expectedRevision) {
		MinecraftServer server = player.level().getServer();
		Snapshot snapshot = WorldInterfaceState.snapshot(server);
		if (!actionContextValid(player, snapshot, encounterId, expectedRevision, snapshot.altarCenter(), false)) {
			return reject(snapshot, "invalid_context");
		}
		TerminalTransaction transaction = snapshot.terminalTransactions().get(player.getUUID());
		if (transaction == null) return RitualResult.idempotent(snapshot, "nothing_deposited");
		if (snapshot.revision() != expectedRevision) return reject(snapshot, "revision_mismatch");
		if (snapshot.stage() != WorldInterfaceStage.WAITING_TERMINALS || snapshot.sacrificeCommitted()) {
			return reject(snapshot, "ritual_not_waiting");
		}
		if (transaction.state() == TerminalTransactionState.COMMITTED) return reject(snapshot, "already_committed");
		RitualResult pending = markReturnPending(server, snapshot, player.getUUID(), "withdrawn");
		processReturns(server);
		return pending.withSnapshot(WorldInterfaceState.snapshot(server));
	}

	public static RitualResult cancel(ServerPlayer player, UUID encounterId, long expectedRevision) {
		MinecraftServer server = player.level().getServer();
		Snapshot snapshot = WorldInterfaceState.snapshot(server);
		if (!actionContextValid(player, snapshot, encounterId, expectedRevision, snapshot.altarCenter(), false)) {
			return reject(snapshot, "invalid_context");
		}
		if (snapshot.frozenRoster().isEmpty()) return RitualResult.idempotent(snapshot, "ritual_already_empty");
		if (!snapshot.terminalTransactions().isEmpty() && snapshot.terminalTransactions().values().stream()
				.allMatch(value -> value.state() == TerminalTransactionState.RETURN_PENDING)) {
			return RitualResult.idempotent(snapshot, "rollback_already_pending");
		}
		if (snapshot.revision() != expectedRevision) return reject(snapshot, "revision_mismatch");
		if (snapshot.stage() != WorldInterfaceStage.WAITING_TERMINALS || snapshot.sacrificeCommitted()) {
			return reject(snapshot, "ritual_not_waiting");
		}
		if (!snapshot.frozenRoster().contains(player.getUUID())) return reject(snapshot, "not_in_frozen_roster");
		return rollback(server, snapshot, "cancelled");
	}

	/** True while the normal terminal lifecycle must not create another copy. */
	public static boolean terminalRecoverySuppressed(MinecraftServer server, UUID playerId) {
		Snapshot snapshot = WorldInterfaceState.snapshot(server);
		if (!snapshot.valid() || !snapshot.present()) return false;
		TerminalTransaction transaction = snapshot.terminalTransactions().get(playerId);
		return transaction != null || snapshot.sacrificeCommitted() && snapshot.frozenRoster().contains(playerId);
	}

	private static void tick(MinecraftServer server) {
		Snapshot snapshot = WorldInterfaceState.snapshot(server);
		if (!snapshot.valid() || !snapshot.present()) return;
		if (snapshot.sacrificeCommitted()) {
			if (server.getTickCount() % 20 == 0) reconcileCommittedProjection(server, snapshot);
			return;
		}
		if (snapshot.stage() != WorldInterfaceStage.WAITING_TERMINALS) return;
		if (snapshot.terminalTransactions().values().stream()
				.anyMatch(value -> value.state() == TerminalTransactionState.RETURN_PENDING)) {
			processReturns(server);
			return;
		}
		if (snapshot.terminalTransactions().values().stream()
				.anyMatch(value -> value.state() == TerminalTransactionState.PREPARED)) {
			// PREPARED is an uncertain crash boundary. Abort it into the recoverable path.
			rollback(server, snapshot, "prepared_recovery");
			return;
		}
		if (readyToCommit(snapshot)) {
			commitReady(server, snapshot);
			return;
		}
		if (!snapshot.frozenRoster().isEmpty() && !snapshot.frozenRoster().equals(eligiblePlayers(server))) {
			rollback(server, snapshot, "roster_changed");
		}
	}

	/** Rolls a fully removed journal forward to its one atomic sacrifice commit. */
	private static RitualResult commitReady(MinecraftServer server, Snapshot initial) {
		Snapshot snapshot = initial;
		for (int attempt = 0; attempt < 5; attempt++) {
			if (snapshot.sacrificeCommitted()) {
				return RitualResult.idempotent(snapshot, "already_deposited");
			}
			if (snapshot.stage() != WorldInterfaceStage.WAITING_TERMINALS || !readyToCommit(snapshot)) {
				return reject(snapshot, "sacrifice_not_ready");
			}
			double maximumHealth = 600.0D * snapshot.frozenRoster().size();
			MutationResult committed = WorldInterfaceState.mutate(server,
					snapshot.encounterId().orElseThrow(), snapshot.revision(),
					state -> state.commitSacrifice(maximumHealth));
			if (committed.applied()) {
				reconcileCommittedProjection(server, committed.snapshot());
				return RitualResult.applied(committed.snapshot(), "sacrifice_committed");
			}
			if (!"revision_mismatch".equals(committed.reason())) {
				return reject(committed.snapshot(), committed.reason());
			}
			snapshot = committed.snapshot();
		}
		return reject(snapshot, "revision_mismatch");
	}

	private static RitualResult rollback(MinecraftServer server, Snapshot snapshot, String reason) {
		if (!snapshot.valid() || !snapshot.present() || snapshot.encounterId().isEmpty()) return reject(snapshot, reason);
		if (snapshot.terminalTransactions().isEmpty()) {
			MutationResult cleared = WorldInterfaceState.mutate(server, snapshot.encounterId().orElseThrow(),
					snapshot.revision(), state -> {
						state.clearFrozenRoster();
						state.setGateState(WorldInterfaceGatewayState.DORMANT);
					});
			return cleared.applied() ? RitualResult.applied(cleared.snapshot(), reason)
					: reject(cleared.snapshot(), cleared.reason());
		}
		MutationResult pending = WorldInterfaceState.mutate(server, snapshot.encounterId().orElseThrow(),
				snapshot.revision(), state -> {
					for (Map.Entry<UUID, TerminalTransaction> entry : state.terminalTransactions().entrySet()) {
						TerminalTransaction value = entry.getValue();
						if (value.state() != TerminalTransactionState.COMMITTED) {
							state.putTerminalTransaction(value.withState(TerminalTransactionState.RETURN_PENDING));
						}
					}
					state.setGateState(WorldInterfaceGatewayState.DORMANT);
				});
		if (!pending.applied()) return reject(pending.snapshot(), pending.reason());
		processReturns(server);
		return RitualResult.applied(WorldInterfaceState.snapshot(server), reason);
	}

	private static RitualResult markReturnPending(MinecraftServer server, Snapshot snapshot, UUID playerId,
			String reason) {
		MutationResult pending = WorldInterfaceState.mutate(server, snapshot.encounterId().orElseThrow(),
				snapshot.revision(), state -> {
					TerminalTransaction value = state.terminalTransactions().get(playerId);
					if (value == null) throw new IllegalStateException("transaction_missing");
					state.putTerminalTransaction(value.withState(TerminalTransactionState.RETURN_PENDING));
				});
		return pending.applied() ? RitualResult.applied(pending.snapshot(), reason)
				: reject(pending.snapshot(), pending.reason());
	}

	private static void processReturns(MinecraftServer server) {
		while (true) {
			Snapshot snapshot = WorldInterfaceState.snapshot(server);
			if (!snapshot.valid() || !snapshot.present() || snapshot.sacrificeCommitted()) return;
			TerminalTransaction deliverable = snapshot.terminalTransactions().values().stream()
					.filter(value -> value.state() == TerminalTransactionState.RETURN_PENDING)
					.filter(value -> server.getPlayerList().getPlayer(value.playerId()) != null)
					.findFirst().orElse(null);
			if (deliverable == null) return;
			ServerPlayer player = server.getPlayerList().getPlayer(deliverable.playerId());
			FrequencyWorldData data = FrequencyWorldData.get(server);
			boolean alreadyCarried = findMatchingTerminal(player, data, deliverable.terminalId(),
					deliverable.generation()) != null;
			if (!alreadyCarried) {
				ItemStack returned = TerminalData.stackFromRecord(deliverable.terminalSnapshot());
				if (!TerminalLifecycleService.insertAuthoritativeReturn(player, returned)) return;
			}
			MutationResult consumed = WorldInterfaceState.mutate(server, snapshot.encounterId().orElseThrow(),
					snapshot.revision(), state -> {
						state.removeTerminalTransaction(deliverable.playerId());
						if (state.terminalTransactions().isEmpty()) {
							state.clearFrozenRoster();
							state.setGateState(WorldInterfaceGatewayState.DORMANT);
						}
					});
			if (!consumed.applied()) return;
		}
	}

	private static void reconcilePlayer(ServerPlayer player) {
		MinecraftServer server = player.level().getServer();
		Snapshot snapshot = WorldInterfaceState.snapshot(server);
		TerminalTransaction transaction = snapshot.terminalTransactions().get(player.getUUID());
		if (transaction != null && transaction.state() == TerminalTransactionState.RETURN_PENDING) {
			processReturns(server);
		}
		if (snapshot.sacrificeCommitted()) reconcileCommittedProjection(server, snapshot);
	}

	private static void reconcileCommittedProjection(MinecraftServer server, Snapshot snapshot) {
		FrequencyWorldData data = FrequencyWorldData.get(server);
		for (UUID playerId : snapshot.frozenRoster()) {
			data.terminalRecord(playerId).ifPresent(record -> {
				if (record.getBooleanOr(TerminalData.TERMINAL_CAPTURED, false)) return;
				data.updateTerminalRecord(playerId, value -> {
					value.putBoolean(TerminalData.TERMINAL_CAPTURED, true);
					value.putLong(TerminalData.TERMINAL_CAPTURED_TICK,
							Math.max(0L, server.overworld().getGameTime()));
				});
			});
			TerminalLifecycleService.clearTransientRecovery(playerId);
		}
	}

	private static boolean readyToCommit(Snapshot snapshot) {
		return !snapshot.frozenRoster().isEmpty()
				&& snapshot.terminalTransactions().size() == snapshot.frozenRoster().size()
				&& snapshot.terminalTransactions().values().stream()
				.allMatch(value -> value.state() == TerminalTransactionState.REMOVED);
	}

	private static boolean actionContextValid(ServerPlayer player, Snapshot snapshot, UUID encounterId,
			long expectedRevision, BlockPos corePosition, boolean exactRevision) {
		if (!snapshot.valid() || !snapshot.present() || encounterId == null
				|| snapshot.encounterId().filter(encounterId::equals).isEmpty()) return false;
		if (exactRevision && snapshot.revision() != expectedRevision) return false;
		if (player.level().dimension() != Level.END || isSpectator(player)) return false;
		if (!snapshot.altarCenter().equals(corePosition)
				|| player.distanceToSqr(corePosition.getX() + 0.5D, corePosition.getY() + 0.5D,
				corePosition.getZ() + 0.5D) > MAX_INTERACTION_DISTANCE_SQUARED) return false;
		return player.level().getBlockState(corePosition).is(ModBlocks.RESONANCE_CORE);
	}

	private static Set<UUID> eligiblePlayers(MinecraftServer server) {
		LinkedHashSet<UUID> result = new LinkedHashSet<>();
		server.getPlayerList().getPlayers().stream().filter(player -> !isSpectator(player))
				.map(ServerPlayer::getUUID).sorted(Comparator.comparing(UUID::toString)).forEach(result::add);
		return Collections.unmodifiableSet(result);
	}

	private static boolean isSpectator(ServerPlayer player) {
		return player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR;
	}

	private static LocatedTerminal findValidBoundTerminal(ServerPlayer player, FrequencyWorldData data) {
		if (!data.terminalRecord(player.getUUID())
				.map(record -> record.getBooleanOr(TerminalData.BOUND, false)).orElse(false)) return null;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (data.isValidTerminal(stack, player.getUUID()) && TerminalData.isBound(stack)) {
				return new LocatedTerminal(slot, stack);
			}
		}
		return null;
	}

	private static LocatedTerminal findMatchingTerminal(ServerPlayer player, FrequencyWorldData data,
			String terminalId, int generation) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (data.isValidTerminal(stack, player.getUUID())
					&& terminalId.equals(TerminalData.terminalId(stack))
					&& generation == TerminalData.copyGeneration(stack)) return new LocatedTerminal(slot, stack);
		}
		return null;
	}

	private static void removeMatchingTerminals(ServerPlayer player, FrequencyWorldData data,
			String terminalId, int generation) {
		boolean changed = false;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (data.isValidTerminal(stack, player.getUUID())
					&& terminalId.equals(TerminalData.terminalId(stack))
					&& generation == TerminalData.copyGeneration(stack)) {
				player.getInventory().setItem(slot, ItemStack.EMPTY);
				changed = true;
			}
		}
		if (changed) player.getInventory().setChanged();
	}

	private static RitualResult reject(Snapshot snapshot, String reason) {
		return new RitualResult(false, false, reason, snapshot);
	}

	@FunctionalInterface
	public interface AltarOpenHandler {
		boolean open(ServerPlayer player, BlockPos corePosition);
	}

	public record RitualResult(boolean applied, boolean idempotent, String reason, Snapshot snapshot) {
		private static RitualResult applied(Snapshot snapshot, String reason) {
			return new RitualResult(true, false, reason, snapshot);
		}

		private static RitualResult idempotent(Snapshot snapshot, String reason) {
			return new RitualResult(true, true, reason, snapshot);
		}

		private RitualResult withSnapshot(Snapshot value) {
			return new RitualResult(applied, idempotent, reason, value);
		}
	}

	private record LocatedTerminal(int slot, ItemStack stack) {
	}
}
