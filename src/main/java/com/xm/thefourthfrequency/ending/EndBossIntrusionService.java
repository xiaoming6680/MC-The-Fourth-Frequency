package com.xm.thefourthfrequency.ending;

import com.xm.thefourthfrequency.content.ModItems;
import com.xm.thefourthfrequency.networking.EndBossIntrusionS2C;
import com.xm.thefourthfrequency.state.PlayerPatternState;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owns permanent hotbar swaps and short-lived server-authoritative slot leases. */
public final class EndBossIntrusionService {
	public static final int WARNING_TICKS = 30;
	public static final int LOCK_TICKS = 100;

	private static final Map<MinecraftServer, State> STATES = new IdentityHashMap<>();
	private static boolean initialized;

	private EndBossIntrusionService() {
	}

	public static void initialize() {
		if (initialized) return;
		initialized = true;
		ServerTickEvents.END_SERVER_TICK.register(EndBossIntrusionService::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(STATES::remove);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> handleDisconnect(server, handler.player));
		ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
			if (origin.dimension() == Level.END || destination.dimension() != Level.END) {
				clearPlayer(destination.getServer(), player, true);
			}
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				rejects(player instanceof ServerPlayer serverPlayer ? serverPlayer : null, hand)
						? InteractionResult.FAIL : InteractionResult.PASS);
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
				rejects(player instanceof ServerPlayer serverPlayer ? serverPlayer : null, hand)
						? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.register((player, level, hand) ->
				rejects(player instanceof ServerPlayer serverPlayer ? serverPlayer : null, hand)
						? InteractionResult.FAIL : InteractionResult.PASS);
		UseBlockCallback.EVENT.register((player, level, hand, hit) ->
				rejects(player instanceof ServerPlayer serverPlayer ? serverPlayer : null, hand)
						? InteractionResult.FAIL : InteractionResult.PASS);
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
				rejects(player instanceof ServerPlayer serverPlayer ? serverPlayer : null, hand)
						? InteractionResult.FAIL : InteractionResult.PASS);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer serverPlayer && isSelectedSlotLocked(serverPlayer)) {
				rejected(serverPlayer);
				return false;
			}
			return true;
		});
	}

	/** Called by the encounter authority after phase advancement. */
	public static void schedule(MinecraftServer server, FrequencyWorldData data, EndBossPhase phase,
			List<ServerPlayer> participants) {
		if (participants.isEmpty() || !phase.hasIntrusion() || EndingState.endBossDefeated(data)) {
			if (!phase.hasIntrusion() && EndingState.endBossIntrusionPhase(data) != phase.id()) {
				EndingState.setEndBossIntrusionPhase(data, phase);
			}
			return;
		}
		State state = STATES.computeIfAbsent(server, ignored -> new State());
		long now = participants.getFirst().level().getGameTime();
		boolean entered = EndingState.endBossIntrusionPhase(data) != phase.id();
		long next = EndingState.endBossNextIntrusionTick(data);
		if (!entered && next > now) return;
		if (state.pending != null || hasActiveLease(state, now)) return;
		ServerPlayer target = chooseTarget(state, participants);
		if (target == null) return;
		ServerLevel end = server.getLevel(Level.END);
		if (end == null) return;
		var body = EndBossEncounterService.findEncounterBoss(end).orElse(null);
		if (body == null || !body.beginEndBossIntrusionVisual(target)) return;
		state.pending = new PendingIntrusion(EndingState.endBossEncounterId(data), target.getUUID(),
				now + WARNING_TICKS, phase);
		target.displayClientMessage(Component.translatable(
				"message.thefourthfrequency.end_boss.intrusion_warning"), true);
		EndingState.setEndBossIntrusionPhase(data, phase);
		EndingState.setEndBossNextIntrusionTick(data, now + phase.intrusionInterval());
	}

	private static void tick(MinecraftServer server) {
		State state = STATES.get(server);
		if (state == null) return;
		FrequencyWorldData data = FrequencyWorldData.get(server);
		long now = server.getTickCount();
		boolean combatActive = EndingState.endBossEncounter(data) && !EndingState.endBossDefeated(data)
				&& EndingState.get(data).getBooleanOr("body_active", false);
		for (UUID playerId : List.copyOf(state.leases.keySet())) {
			ServerPlayer player = server.getPlayerList().getPlayer(playerId);
			Lease lease = state.leases.get(playerId);
			long levelTick = player == null ? now : player.level().getGameTime();
			if (!combatActive || player == null || !player.isAlive() || player.level().dimension() != Level.END
					|| lease.expiresAtTick <= levelTick) clearPlayer(server, playerId, player, player != null);
		}
		if (!combatActive) {
			state.pending = null;
			return;
		}
		PendingIntrusion pending = state.pending;
		if (pending == null) return;
		ServerPlayer target = server.getPlayerList().getPlayer(pending.targetId);
		if (target == null || !target.isAlive() || target.level().dimension() != Level.END) {
			state.pending = null;
			return;
		}
		if (target.level().getGameTime() < pending.dueTick) return;
		performSwap(data, state, target, pending.encounterId);
		state.lastAfflicted.put(target.getUUID(), target.level().getGameTime());
		state.pending = null;
	}

	private static boolean performSwap(FrequencyWorldData data, State state, ServerPlayer player, UUID encounterId) {
		Inventory inventory = player.getInventory();
		int first = primarySlot(data, player);
		int second = partnerSlot(inventory, first);
		if (first < 0 || second < 0 || first == second) return false;
		ItemStack firstStack = inventory.getItem(first);
		ItemStack secondStack = inventory.getItem(second);
		inventory.setItem(first, secondStack);
		inventory.setItem(second, firstStack);
		inventory.setChanged();
		player.inventoryMenu.broadcastChanges();
		if (player.containerMenu != player.inventoryMenu) player.containerMenu.broadcastChanges();
		int mask = 1 << first | 1 << second;
		long expiresAt = player.level().getGameTime() + LOCK_TICKS;
		int sequence = EndingState.nextEndBossIntrusionSequence(data);
		state.leases.put(player.getUUID(), new Lease(encounterId, sequence, mask, expiresAt,
				firstStack, secondStack));
		ServerPlayNetworking.send(player, new EndBossIntrusionS2C(encounterId, sequence, mask, expiresAt));
		player.displayClientMessage(Component.translatable("message.thefourthfrequency.end_boss.intrusion_locked"), true);
		return true;
	}

	private static int primarySlot(FrequencyWorldData data, ServerPlayer player) {
		Inventory inventory = player.getInventory();
		String preferred = data.terminalRecord(player.getUUID()).map(PlayerPatternState::read)
				.map(PlayerPatternState::preferredWeapon).orElse("");
		if (!preferred.isBlank()) {
			for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
				ItemStack stack = inventory.getItem(slot);
				if (!stack.isEmpty() && !stack.is(ModItems.OLD_TERMINAL)
						&& BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(preferred)) return slot;
			}
		}
		int selected = inventory.getSelectedSlot();
		if (selected >= 0 && selected < Inventory.getSelectionSize()
				&& !inventory.getItem(selected).is(ModItems.OLD_TERMINAL)) return selected;
		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			ItemStack stack = inventory.getItem(slot);
			if (!stack.isEmpty() && !stack.is(ModItems.OLD_TERMINAL)) return slot;
		}
		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			if (!inventory.getItem(slot).is(ModItems.OLD_TERMINAL)) return slot;
		}
		return -1;
	}

	private static int partnerSlot(Inventory inventory, int first) {
		if (first < 0) return -1;
		List<Integer> eligible = new ArrayList<>();
		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			if (slot != first && !inventory.getItem(slot).is(ModItems.OLD_TERMINAL)) eligible.add(slot);
		}
		if (eligible.isEmpty()) return -1;
		List<Integer> occupied = eligible.stream().filter(slot -> !inventory.getItem(slot).isEmpty()).toList();
		List<Integer> choices = occupied.isEmpty() ? eligible : occupied;
		return choices.stream().max(Comparator.comparingInt((Integer slot) -> Math.abs(slot - first))
				.thenComparingInt(Integer::intValue)).orElse(-1);
	}

	private static ServerPlayer chooseTarget(State state, List<ServerPlayer> participants) {
		return participants.stream().filter(player -> !state.leases.containsKey(player.getUUID()))
				.min(Comparator.comparingLong((ServerPlayer player) ->
						state.lastAfflicted.getOrDefault(player.getUUID(), Long.MIN_VALUE))
						.thenComparing(player -> player.getUUID().toString())).orElse(null);
	}

	private static boolean hasActiveLease(State state, long now) {
		return state.leases.values().stream().anyMatch(lease -> lease.expiresAtTick > now);
	}

	private static boolean rejects(ServerPlayer player, InteractionHand hand) {
		if (player == null || hand != InteractionHand.MAIN_HAND || !isSelectedSlotLocked(player)) return false;
		rejected(player);
		return true;
	}

	private static void rejected(ServerPlayer player) {
		player.displayClientMessage(Component.translatable("message.thefourthfrequency.end_boss.intrusion_rejected"), true);
	}

	public static void notifyRejected(ServerPlayer player) {
		rejected(player);
	}

	public static boolean isSelectedSlotLocked(ServerPlayer player) {
		if (!player.isAlive()) return false;
		State state = STATES.get(player.level().getServer());
		if (state == null) return false;
		Lease lease = state.leases.get(player.getUUID());
		if (lease == null || lease.expiresAtTick <= player.level().getGameTime()) return false;
		return (lease.slotMask & 1 << player.getInventory().getSelectedSlot()) != 0;
	}

	public static int lockedMask(ServerPlayer player) {
		if (!player.isAlive()) return 0;
		State state = STATES.get(player.level().getServer());
		if (state == null) return 0;
		Lease lease = state.leases.get(player.getUUID());
		return lease == null || lease.expiresAtTick <= player.level().getGameTime() ? 0 : lease.slotMask;
	}

	public static boolean isSlotLocked(ServerPlayer player, int inventorySlot) {
		return inventorySlot >= 0 && inventorySlot < Inventory.getSelectionSize()
				&& (lockedMask(player) & 1 << inventorySlot) != 0;
	}

	/** Used by drop interception after vanilla has detached a stack from its slot. */
	public static boolean isLockedStack(ServerPlayer player, ItemStack stack) {
		if (!player.isAlive() || stack.isEmpty()) return false;
		State state = STATES.get(player.level().getServer());
		if (state == null) return false;
		Lease lease = state.leases.get(player.getUUID());
		if (lease == null || lease.expiresAtTick <= player.level().getGameTime()) return false;
		if (stack == lease.firstStack || stack == lease.secondStack) return true;
		for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
			if ((lease.slotMask & 1 << slot) != 0 && player.getInventory().getItem(slot) == stack) return true;
		}
		return false;
	}

	public static void clearAll(MinecraftServer server) {
		State state = STATES.get(server);
		if (state == null) return;
		for (UUID playerId : List.copyOf(state.leases.keySet())) {
			clearPlayer(server, playerId, server.getPlayerList().getPlayer(playerId), true);
		}
		state.pending = null;
	}

	/** Shared by the Fabric disconnect callback and integration fixtures. Item order is never restored. */
	public static void handleDisconnect(MinecraftServer server, ServerPlayer player) {
		clearPlayer(server, player, false);
	}

	/** Clears before vanilla death inventory handling so leased items can follow normal keep/drop rules. */
	public static void handleDeath(MinecraftServer server, ServerPlayer player) {
		clearPlayer(server, player, true);
	}

	private static void clearPlayer(MinecraftServer server, ServerPlayer player, boolean notify) {
		if (player == null) return;
		clearPlayer(server, player.getUUID(), player, notify);
	}

	private static void clearPlayer(MinecraftServer server, UUID playerId, ServerPlayer player, boolean notify) {
		State state = STATES.get(server);
		if (state == null) return;
		Lease removed = state.leases.remove(playerId);
		if (state.pending != null && state.pending.targetId.equals(playerId)) state.pending = null;
		if (removed == null || !notify || player == null) return;
		FrequencyWorldData data = FrequencyWorldData.get(server);
		int sequence = EndingState.endBossEncounter(data)
				? EndingState.nextEndBossIntrusionSequence(data) : removed.sequence + 1;
		ServerPlayNetworking.send(player, new EndBossIntrusionS2C(removed.encounterId, sequence, 0,
				player.level().getGameTime()));
	}

	private static final class State {
		private final Map<UUID, Lease> leases = new HashMap<>();
		private final Map<UUID, Long> lastAfflicted = new HashMap<>();
		private PendingIntrusion pending;
	}

	private record PendingIntrusion(UUID encounterId, UUID targetId, long dueTick, EndBossPhase phase) {
	}

	private record Lease(UUID encounterId, int sequence, int slotMask, long expiresAtTick,
			ItemStack firstStack, ItemStack secondStack) {
	}
}
