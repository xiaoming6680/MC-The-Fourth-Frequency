package com.xm.thefourthfrequency.test;

import com.xm.thefourthfrequency.content.ModItems;
import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.networking.TerminalControlPayload;
import com.xm.thefourthfrequency.networking.TerminalNavigationPayload;
import com.xm.thefourthfrequency.state.NavigationState;
import com.xm.thefourthfrequency.terminal.TerminalControlPolicy;
import com.xm.thefourthfrequency.terminal.TerminalRuntimeService;
import com.xm.thefourthfrequency.terminal.TerminalResource;
import com.xm.thefourthfrequency.terminal.TerminalStructureTarget;
import com.xm.thefourthfrequency.terminal.TerminalTool;
import com.xm.thefourthfrequency.terminal.TerminalToolService;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import com.xm.thefourthfrequency.world.TerminalActivityTracker;
import com.xm.thefourthfrequency.world.ZeroStationLayout;
import com.xm.thefourthfrequency.world.ZeroStationService;
import com.xm.thefourthfrequency.world.ResourceGuidanceService;
import com.xm.thefourthfrequency.world.StoryProgressService;
import com.xm.thefourthfrequency.world.StructureNavigationService;
import com.xm.thefourthfrequency.world.SurvivalMilestone;
import com.xm.thefourthfrequency.world.SurvivalProgressService;
import com.xm.thefourthfrequency.world.TerminalLifecycleService;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.lang.reflect.Method;
import java.util.Set;

public final class M1GameTests implements CustomTestMethodInvoker {
	@GameTest
	public void worldOwnsExactlyOneBoundedStationPlan(GameTestHelper helper) {
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		var station = data.stationPosition().orElseThrow(() ->
				new AssertionError("Relay Station Zero was not allocated"));
		int placements = ZeroStationLayout.create(station).size();
		helper.assertTrue(placements > 32, "Station must require multiple bounded tick batches");
		helper.assertTrue(placements < 1_000, "Station plan must remain compact");
		helper.succeed();
	}

	@GameTest
	public void terminalLedgerIsIdempotentForMultiplePlayers(GameTestHelper helper) {
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		int grantsBefore = data.issuedPlayerCount();

		ServerPlayer first = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(data.hasTerminalIssued(first.getUUID()),
				"JOIN event must issue the first player's terminal");
		helper.assertFalse(ZeroStationService.issueTerminalIfNeeded(first),
				"Repeated join must not issue another terminal");
		helper.assertValueEqual(countTerminals(first), 1, "First player's terminal count");

		ServerPlayer second = helper.makeMockServerPlayerInLevel();
		helper.assertTrue(!first.getUUID().equals(second.getUUID()), "Mock players must have distinct identities");
		helper.assertTrue(data.hasTerminalIssued(second.getUUID()),
				"JOIN event must issue the second player's independent terminal");
		helper.assertFalse(ZeroStationService.issueTerminalIfNeeded(second),
				"Second player's repeated join must also be protected");
		helper.assertValueEqual(countTerminals(second), 1, "Second player's terminal count");
		helper.assertValueEqual(data.issuedPlayerCount(), grantsBefore + 2, "Persistent grant ledger size");
		ItemStack firstTerminal = findTerminal(first);
		ItemStack secondTerminal = findTerminal(second);
		helper.assertTrue(TerminalData.cacheVariant(firstTerminal) != TerminalData.cacheVariant(secondTerminal),
				"Adjacent multiplayer grants must receive different stable handover records");
		helper.assertFalse(TerminalData.secondCacheUnlocked(firstTerminal),
				"The single-player second cache must remain sealed before Fourth Frequency stabilization");
		helper.assertTrue(TerminalData.cacheVariant(firstTerminal) != TerminalData.secondCacheVariant(firstTerminal),
				"The sealed follow-up cache must preserve a distinct handover record");
		helper.assertTrue(TerminalData.unlockSecondCache(firstTerminal),
				"Fourth Frequency stabilization must be able to unlock the single-player follow-up cache");
		helper.assertTrue(TerminalData.secondCacheUnlocked(firstTerminal),
				"The follow-up cache unlock must persist on the terminal item");
		helper.assertFalse(TerminalData.unlockSecondCache(firstTerminal),
				"The follow-up cache unlock must be idempotent");

		TerminalActivityTracker.record(first, TerminalData.MINED_BLOCKS, "mined");
		TerminalActivityTracker.record(first, TerminalData.PLACED_BLOCKS, "placed");
		TerminalActivityTracker.recordCrafted(first, new ItemStack(Items.CRAFTING_TABLE, 2));
		var activity = data.terminalRecord(first.getUUID()).orElseThrow();
		helper.assertValueEqual(activity.getIntOr(TerminalData.MINED_BLOCKS, 0), 1, "Recorded mining events");
		helper.assertValueEqual(activity.getIntOr(TerminalData.PLACED_BLOCKS, 0), 1, "Recorded placement events");
		helper.assertValueEqual(activity.getIntOr(TerminalData.CRAFTED_ITEMS, 0), 2, "Recorded crafted output");
		helper.assertValueEqual(activity.getStringOr(TerminalData.LAST_CRAFTED_ITEM, ""), "minecraft:crafting_table",
				"Last crafted item identity");
		helper.assertValueEqual(activity.getStringOr(TerminalData.LAST_ACTIVITY, ""), "crafted",
				"Last behavior trace");
		helper.succeed();
	}

	@GameTest
	public void terminalRuntimeRemembersPagesAndRejectsGlobalTuning(GameTestHelper helper) {
		ServerPlayer first = helper.makeMockServerPlayerInLevel();
		ServerPlayer second = helper.makeMockServerPlayerInLevel();
		first.setItemInHand(InteractionHand.MAIN_HAND, findTerminal(first));
		second.setItemInHand(InteractionHand.MAIN_HAND, findTerminal(second));

		TerminalRuntimeService.open(first, 0);
		TerminalRuntimeService.open(second, 0);
		helper.assertTrue(TerminalRuntimeService.isOpen(first), "First valid terminal view must open");
		helper.assertTrue(TerminalRuntimeService.isOpen(second), "Second valid terminal view must open independently");

		TerminalRuntimeService.control(first, TerminalControlPayload.MODE,
				TerminalControlPolicy.Mode.FILES.ordinal());
		TerminalRuntimeService.control(first, TerminalControlPayload.TUNE, 65);
		TerminalRuntimeService.control(second, TerminalControlPayload.MODE,
				TerminalControlPolicy.Mode.SIGNAL.ordinal());
		TerminalRuntimeService.control(second, TerminalControlPayload.TUNE, 44);
		TerminalRuntimeService.control(first, TerminalControlPayload.CLOSE, 0);
		TerminalRuntimeService.control(second, TerminalControlPayload.CLOSE, 0);

		helper.assertFalse(TerminalRuntimeService.isOpen(first), "Close must clear the first transient view");
		helper.assertValueEqual(TerminalRuntimeService.rememberedMode(first.getUUID()),
				TerminalControlPolicy.Mode.FILES.ordinal(), "First remembered page");
		helper.assertValueEqual(TerminalRuntimeService.rememberedTuning(first.getUUID()),
				TerminalControlPolicy.DEFAULT_TUNING, "A non-signal page cannot alter the receiver");
		helper.assertValueEqual(TerminalRuntimeService.rememberedMode(second.getUUID()),
				TerminalControlPolicy.Mode.SIGNAL.ordinal(), "Second remembered page");
		helper.assertValueEqual(TerminalRuntimeService.rememberedTuning(second.getUUID()),
				TerminalControlPolicy.DEFAULT_TUNING, "The tools grid cannot alter the receiver");

		TerminalRuntimeService.open(first, 0);
		helper.assertTrue(TerminalRuntimeService.isOpen(first), "Reopening must restore the remembered view");
		TerminalRuntimeService.control(first, TerminalControlPayload.CLOSE, 0);
		helper.succeed();
	}

	@GameTest
	public void optionalResourceGuidancePrecedesButNeverReplacesNarrativeBinding(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		ItemStack beforeBinding = findTerminal(player).copy();
		var orePosition = player.blockPosition().below(2);
		helper.getLevel().setBlockAndUpdate(orePosition, Blocks.IRON_ORE.defaultBlockState());
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putInt(TerminalData.SURVIVAL_MILESTONE_MASK, SurvivalMilestone.MINED_LOGS.mask());
			record.putInt(TerminalData.SELECTED_RESOURCE, TerminalResource.IRON.wireId());
			record.putLong(TerminalData.MINERAL_SCAN_READY_GAME_TIME, player.level().getGameTime());
			record.putString(TerminalData.TARGET_KIND, "iron");
		});
		ResourceGuidanceService.requestRescan(player);
		ResourceGuidanceService.updatePlayer(player);

		var guided = data.terminalRecord(player.getUUID()).orElseThrow();
		helper.assertTrue(guided.getBooleanOr(TerminalData.TARGET_LOCATED, false),
				"Optional guidance may locate a real resource before the fourth band is revealed");
		helper.assertValueEqual(guided.getLongOr(TerminalData.TARGET_POSITION, 0L), orePosition.asLong(),
				"Authoritative real resource position");
		helper.assertValueEqual(guided.getIntOr(TerminalData.BAND_STAGE, 0), 0,
				"Optional resource guidance never changes the fourth-band reveal stage");
		helper.assertFalse(guided.getBooleanOr(TerminalData.BOUND, false),
				"Locating a resource never binds the terminal");
		helper.getLevel().setBlockAndUpdate(orePosition, Blocks.AIR.defaultBlockState());
		ResourceGuidanceService.updatePlayer(player);
		var invalidated = data.terminalRecord(player.getUUID()).orElseThrow();
		helper.assertTrue(!invalidated.getBooleanOr(TerminalData.TARGET_LOCATED, false)
				|| invalidated.getLongOr(TerminalData.TARGET_POSITION, 0L) != orePosition.asLong(),
				"A removed ore block must invalidate the stale target");
		helper.getLevel().setBlockAndUpdate(orePosition, Blocks.IRON_ORE.defaultBlockState());
		ResourceGuidanceService.requestRescan(player);
		ResourceGuidanceService.updatePlayer(player);
		helper.assertTrue(TerminalToolService.startGuidance(player, TerminalTool.MINERALS.slot()),
				"The explicit mineral navigation toggle must accept the located iron target");
		var navigation = TerminalRuntimeService.navigationSnapshot(player);
		helper.assertValueEqual(navigation.protocolVersion(), 6, "Navigation protocol version");
		helper.assertValueEqual(TerminalRuntimeService.navigationSyncTicks(), 4, "Navigation cadence");
		helper.assertValueEqual(navigation.targetKind(), 1, "Iron navigation kind");
		helper.assertTrue(navigation.located() && navigation.navigable(),
				"Located same-dimension resource must activate navigation");
		helper.assertValueEqual(navigation.targetY(), orePosition.getY(), "Navigation target Y");
		helper.assertTrue(player.teleportTo(helper.getLevel(), orePosition.getX() + 0.5,
				orePosition.getY() + 2.0, orePosition.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true),
				"Two-block mineral arrival boundary fixture");
		ResourceGuidanceService.updatePlayer(player);
		helper.assertValueEqual(data.terminalRecord(player.getUUID()).orElseThrow()
						.getIntOr(TerminalData.ACTIVE_GUIDANCE_TOOL, -1),
				TerminalTool.MINERALS.slot(), "Two blocks away must keep mineral navigation active");

		var netherBeforeBinding = helper.getLevel().getServer().getLevel(Level.NETHER);
		helper.assertTrue(netherBeforeBinding != null, "Nether level for navigation gating");
		helper.assertTrue(player.teleportTo(netherBeforeBinding, 0.5, 64.0, 0.5, Set.of(), 0.0F, 0.0F, true),
				"Cross-dimension navigation fixture");
		var crossDimensionNavigation = TerminalRuntimeService.navigationSnapshot(player);
		helper.assertTrue(crossDimensionNavigation.located(), "Cross-dimension target remains located");
		helper.assertFalse(crossDimensionNavigation.navigable(), "Cross-dimension target needle must be disabled");
		helper.assertTrue(player.teleportTo(helper.getLevel(), orePosition.getX() + 0.5, orePosition.getY() + 1.0,
				orePosition.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true), "Return to resource dimension");

		player.getInventory().add(new ItemStack(Items.RAW_IRON));
		ResourceGuidanceService.updatePlayer(player);
		var accepted = data.terminalRecord(player.getUUID()).orElseThrow();
		helper.assertFalse(accepted.getBooleanOr(TerminalData.BOUND, false),
				"Following optional advice does not itself bind the terminal");
		helper.assertValueEqual(accepted.getIntOr(TerminalData.BAND_STAGE, 0), 0,
				"Following optional advice leaves narrative reveal state unchanged");
		helper.assertTrue(accepted.getStringOr(TerminalData.ACCEPTED_ADVICE, "").contains("iron"),
				"Accurate advice still becomes part of the terminal's long-term player model");
		helper.assertValueEqual(accepted.getIntOr(TerminalData.ACTIVE_GUIDANCE_TOOL, -1),
				TerminalToolService.NO_TOOL, "Reaching the mineral within one block must stop navigation");
		helper.assertValueEqual(accepted.getIntOr(TerminalData.SELECTED_RESOURCE, -1),
				TerminalResource.NONE.wireId(), "Mineral arrival must clear the current resource target");
		helper.assertFalse(NavigationState.read(accepted).located(),
				"Mineral arrival must clear the concrete target position");
		helper.assertTrue(accepted.getBooleanOr(TerminalData.MINERAL_SURVEY_PROXIMITY, false)
						&& !accepted.getBooleanOr(TerminalData.MINERAL_SURVEY_NEARBY, false),
				"The reached ore must stay silently suppressed until the player leaves its survey episode");

		SurvivalProgressService.updatePlayer(player, data);
		StoryProgressService.update(player, data);
		var bound = data.terminalRecord(player.getUUID()).orElseThrow();
		helper.assertTrue(bound.getBooleanOr(TerminalData.BOUND, false),
				"A real home or iron milestone completes narrative binding without calibration");
		helper.assertValueEqual(bound.getIntOr(TerminalData.BAND_STAGE, 0), 0,
				"Personal binding still precedes the hidden fourth-band prelude");
		helper.assertTrue(bound.getBooleanOr(TerminalData.SECOND_CACHE_UNLOCKED, false),
				"Narrative binding unlocks the next predecessor note");
		helper.assertTrue(TerminalData.isBound(findTerminal(player)),
				"Authoritative binding must be synchronized to the carried item");
		var completedNavigation = TerminalRuntimeService.navigationSnapshot(player);
		helper.assertFalse(completedNavigation.navigable(),
				"Binding must not restore a mineral navigation that already completed");
		helper.assertValueEqual(completedNavigation.targetKind(), TerminalNavigationPayload.NONE,
				"Completed mineral navigation must no longer expose a compass target");
		helper.assertTrue(player.drop(findTerminal(player).copy(), false) == null,
				"Bound terminal drop must be rejected before an ItemEntity is created");
		ChestMenu chest = ChestMenu.oneRow(91, player.getInventory());
		chest.setCarried(findTerminal(player).copy());
		chest.clicked(0, 0, ClickType.PICKUP, player);
		helper.assertTrue(chest.getContainer().getItem(0).isEmpty(),
				"Bound terminal must not enter an external container");
		helper.assertTrue(TerminalData.isBound(chest.getCarried()),
				"Rejected container transfer must leave the terminal on the cursor");
		chest.setCarried(ItemStack.EMPTY);

		var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "Nether level must exist for cross-dimension verification");
		helper.assertTrue(player.teleportTo(nether, 0.5, 64.0, 0.5, Set.of(), 0.0F, 0.0F, true),
				"Cross-dimension teleport");
		TerminalLifecycleService.recordCurrentDimension(player);
		var crossDimension = data.terminalRecord(player.getUUID()).orElseThrow();
		helper.assertTrue(crossDimension.getStringOr(TerminalData.VISITED_DIMENSIONS, "")
				.contains("minecraft:the_nether"), "Cross-dimension history must persist");
		helper.assertTrue(data.isValidTerminal(findTerminal(player), player.getUUID()),
				"Terminal must stay valid across dimensions");

		int previousGeneration = TerminalData.copyGeneration(findTerminal(player));
		removeTerminals(player);
		ServerPlayer respawned = helper.getLevel().getServer().getPlayerList()
				.respawn(player, false, Entity.RemovalReason.KILLED);
		helper.assertTrue(respawned != player, "Vanilla respawn must replace the server player instance");
		helper.assertTrue(respawned.getUUID().equals(player.getUUID()), "Respawn must retain player identity");
		ItemStack recovered = findTerminal(respawned);
		helper.assertValueEqual(TerminalData.copyGeneration(recovered), previousGeneration + 1,
				"Bound terminal must be restored by the real respawn event chain");
		helper.assertFalse(data.isValidTerminal(beforeBinding, player.getUUID()),
				"Pre-binding copy must no longer be an exploitable valid terminal");
		helper.succeed();
	}

	@GameTest
	public void mineralRefreshIsWeightedServerOnlyAndHasAThreeSecondCooldown(GameTestHelper helper) {
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		ServerPlayer miner = helper.makeMockServerPlayerInLevel();
		data.updateTerminalRecord(miner.getUUID(), record -> record.putInt(
				TerminalData.SURVIVAL_MILESTONE_MASK,
				SurvivalMilestone.MINED_LOGS.mask() | SurvivalMilestone.RETURNED_NETHER.mask()));
		miner.getInventory().add(new ItemStack(Items.IRON_INGOT));
		miner.getInventory().add(new ItemStack(Items.IRON_PICKAXE));
		helper.assertFalse(TerminalToolService.selectResource(miner, TerminalResource.DIAMOND.wireId()),
				"Clients can no longer force a mineral category");
		helper.assertTrue(TerminalToolService.requestRescan(miner),
				"The unlocked mineral tool accepts one authoritative refresh");
		var refreshed = data.terminalRecord(miner.getUUID()).orElseThrow();
		TerminalResource selected = TerminalResource.fromWire(
				refreshed.getIntOr(TerminalData.SELECTED_RESOURCE, TerminalResource.NONE.wireId()));
		helper.assertTrue(Set.of(TerminalResource.IRON, TerminalResource.COAL,
				TerminalResource.GOLD, TerminalResource.DIAMOND).contains(selected),
				"Refresh must select one of the four published weighted categories");
		helper.assertValueEqual(refreshed.getLongOr(TerminalData.MINERAL_SCAN_READY_GAME_TIME, 0L)
				- miner.level().getGameTime(), 60L, "Refresh must create an exact three-second probe window");
		helper.assertFalse(refreshed.getBooleanOr(TerminalData.TARGET_LOCATED, false),
				"The previous concrete ore target must be hidden during the probe window");
		data.updateTerminalRecord(miner.getUUID(), record -> record.putLong(
				TerminalData.MINERAL_SCAN_READY_GAME_TIME, Math.max(1L, miner.level().getGameTime())));
		helper.assertValueEqual(TerminalToolService.snapshot(miner, TerminalTool.MINERALS.slot())
						.mineralScanTicks(), 1,
				"The UI must remain in its scanning state at the reveal boundary until the scan result commits");
		helper.assertFalse(TerminalToolService.requestRescan(miner),
				"The server rejects another refresh until the pending scan result commits");
		helper.assertFalse(TerminalToolService.startGuidance(miner, TerminalTool.MINERALS.slot()),
				"Mineral guidance must stay unavailable until a concrete ore block is located");
		helper.succeed();
	}

	@GameTest
	public void automaticMineralSurveyWaitsForAClosedTerminalAndClearsAfterLeaving(GameTestHelper helper) {
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		BlockPos ore = player.blockPosition().below(2);
		BlockPos fartherOre = player.blockPosition().offset(-8, 0, 0);
		helper.getLevel().setBlockAndUpdate(ore, Blocks.IRON_ORE.defaultBlockState());
		helper.getLevel().setBlockAndUpdate(fartherOre, Blocks.DIAMOND_ORE.defaultBlockState());
		data.updateTerminalRecord(player.getUUID(), record -> record.putInt(
				TerminalData.SURVIVAL_MILESTONE_MASK, SurvivalMilestone.MINED_LOGS.mask()));
		player.setItemInHand(InteractionHand.MAIN_HAND, findTerminal(player));

		TerminalRuntimeService.open(player, 0);
		helper.assertTrue(TerminalRuntimeService.isOpen(player), "Fixture terminal must be open");
		ResourceGuidanceService.updatePlayerForTesting(player, 0);
		var whileOpen = data.terminalRecord(player.getUUID()).orElseThrow();
		helper.assertFalse(whileOpen.getBooleanOr(TerminalData.MINERAL_SURVEY_PROXIMITY, false),
				"An open terminal must pause automatic mineral scanning");
		helper.assertFalse(whileOpen.getBooleanOr(TerminalData.MINERAL_SURVEY_NEARBY, false),
				"An open terminal must never roll or publish a nearby mineral prompt");

		TerminalRuntimeService.control(player, TerminalControlPayload.CLOSE, 0);
		ResourceGuidanceService.updatePlayerForTesting(player, 30);
		var missed = data.terminalRecord(player.getUUID()).orElseThrow();
		helper.assertTrue(missed.getBooleanOr(TerminalData.MINERAL_SURVEY_PROXIMITY, false),
				"A real ore inside five blocks must create one proximity episode");
		helper.assertFalse(missed.getBooleanOr(TerminalData.MINERAL_SURVEY_NEARBY, false),
				"A failed thirty-percent roll must stay silent");
		ResourceGuidanceService.updatePlayerForTesting(player, 0);
		helper.assertFalse(data.terminalRecord(player.getUUID()).orElseThrow()
						.getBooleanOr(TerminalData.MINERAL_SURVEY_NEARBY, false),
				"Standing beside the same ore must not repeatedly reroll until success");

		helper.assertTrue(player.teleportTo(helper.getLevel(), ore.getX() + 6.5, ore.getY() + 2.0,
				ore.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true), "Leave the failed proximity episode");
		ResourceGuidanceService.updatePlayerForTesting(player, 0);
		helper.assertFalse(data.terminalRecord(player.getUUID()).orElseThrow()
						.getBooleanOr(TerminalData.MINERAL_SURVEY_PROXIMITY, false),
				"Leaving the survey range must allow a future independent episode");
		helper.assertTrue(player.teleportTo(helper.getLevel(), ore.getX() + 0.5, ore.getY() + 2.0,
				ore.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true), "Return for a new proximity episode");
		ResourceGuidanceService.updatePlayerForTesting(player, 0);
		var surveyed = data.terminalRecord(player.getUUID()).orElseThrow();
		helper.assertTrue(surveyed.getBooleanOr(TerminalData.MINERAL_SURVEY_NEARBY, false),
				"A deterministic successful thirty-percent roll must publish the prompt");
		var snapshot = TerminalToolService.snapshot(player, TerminalToolService.NO_TOOL);
		helper.assertValueEqual(snapshot.protocolVersion(), 5, "Nearby mineral tool snapshot protocol");
		helper.assertTrue(snapshot.mineralSurveyNearby(), "Tool snapshot must expose the nearby mineral state");
		helper.assertValueEqual(snapshot.recommendedPrimaryTool(), TerminalTool.MINERALS.slot(),
				"The nearby mineral state must promote the mineral shortcut");
		TerminalRuntimeService.open(player, 0);
		TerminalRuntimeService.control(player, TerminalControlPayload.SELECT_TOOL, TerminalTool.MINERALS.slot());
		var preview = TerminalRuntimeService.navigationSnapshot(player);
		helper.assertValueEqual(preview.targetKind(), TerminalNavigationPayload.IRON,
				"Automatic surveying must describe the nearest mineral, not a farther ore");
		helper.assertTrue(preview.located() && preview.navigable(),
				"The mineral detail preview must expose a concrete navigable target");
		helper.assertTrue(TerminalToolService.startGuidance(player, TerminalTool.MINERALS.slot()),
				"The detail-page navigation button must accept an automatic survey target");
		var promoted = NavigationState.read(data.terminalRecord(player.getUUID()).orElseThrow());
		helper.assertValueEqual(promoted.kind(), TerminalResource.IRON.id(),
				"Starting navigation must preserve the automatically surveyed mineral kind");
		helper.assertValueEqual(promoted.position(), ore.asLong(),
				"Starting navigation must promote the nearest concrete ore block");
		TerminalRuntimeService.control(player, TerminalControlPayload.CLOSE, 0);

		helper.assertTrue(player.teleportTo(helper.getLevel(), ore.getX() + 6.5, ore.getY() + 2.0,
				ore.getZ() + 0.5, Set.of(), 0.0F, 0.0F, true), "Move outside the five-block survey range");
		ResourceGuidanceService.updatePlayerForTesting(player, 0);
		var cleared = data.terminalRecord(player.getUUID()).orElseThrow();
		helper.assertFalse(cleared.getBooleanOr(TerminalData.MINERAL_SURVEY_PROXIMITY, false),
				"Leaving the survey range must reset the proximity episode");
		helper.assertFalse(cleared.getBooleanOr(TerminalData.MINERAL_SURVEY_NEARBY, false),
				"Leaving the survey range must clear the visible prompt");
		helper.assertFalse(TerminalToolService.snapshot(player, TerminalToolService.NO_TOOL).mineralSurveyNearby(),
				"The cleared prompt must disappear from tools and shortcuts");
		helper.succeed();
	}

	@GameTest
	public void structureNavigationCompletesWithinFiftyBlocksAndBecomesAPersistentPrompt(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		BlockPos destination = player.blockPosition().offset(30, 200, 40);
		String dimension = player.level().dimension().identifier().toString();
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putInt(TerminalData.SURVIVAL_MILESTONE_MASK, SurvivalMilestone.MINED_LOGS.mask());
			record.putInt(TerminalData.ACTIVE_GUIDANCE_TOOL, TerminalTool.NAVIGATION.slot());
			new NavigationState(TerminalStructureTarget.VILLAGE.id(), "", true,
					TerminalStructureTarget.VILLAGE.id(), destination.asLong(), dimension,
					player.level().getGameTime()).writeTo(record);
		});

		StructureNavigationService.updatePlayer(player);
		var completed = data.terminalRecord(player.getUUID()).orElseThrow();
		helper.assertValueEqual(completed.getIntOr(TerminalData.ACTIVE_GUIDANCE_TOOL, -1),
				TerminalToolService.NO_TOOL, "Arrival must stop structure guidance");
		helper.assertFalse(NavigationState.read(completed).located(),
				"The completed destination must leave the active navigation state");
		helper.assertTrue(completed.getBooleanOr(TerminalData.NAVIGATION_COMPLETION_ACTIVE, false),
				"Arrival must leave a persistent completion card");
		helper.assertTrue(completed.getBooleanOr(TerminalData.NAVIGATION_COMPLETION_UNREAD, false),
				"The carried terminal must enter its prompt style until opened");
		helper.assertTrue((completed.getIntOr(TerminalData.COMPLETED_STRUCTURE_TARGETS_MASK, 0)
				& TerminalStructureTarget.bit(TerminalStructureTarget.VILLAGE)) != 0,
				"The arrived structure type must be persisted as completed");
		helper.assertTrue((StructureNavigationService.availableTargetsMask(player, completed)
				& TerminalStructureTarget.bit(TerminalStructureTarget.VILLAGE)) == 0,
				"A completed destination must not reappear in navigation candidates");
		var snapshot = TerminalToolService.snapshot(player, TerminalTool.NAVIGATION.slot());
		helper.assertTrue(snapshot.navigationCompletionAvailable(),
				"The home toolbar snapshot must keep the completion card available");
		helper.assertTrue(snapshot.navigationCompletionDirection() >= 0
				&& snapshot.navigationCompletionDirection() <= 3,
				"The completion prompt must use one of the four player-relative sides");
		var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "Nether level must exist for dimension-specific targets");
		helper.assertTrue(player.teleportTo(nether, 0.5, 64.0, 0.5, Set.of(), 0.0F, 0.0F, true),
				"Dimension-specific navigation fixture");
		var netherRecord = data.terminalRecord(player.getUUID()).orElseThrow();
		int netherTargets = StructureNavigationService.availableTargetsMask(player, netherRecord);
		helper.assertTrue((netherTargets & TerminalStructureTarget.bit(TerminalStructureTarget.FORTRESS)) != 0,
				"Entering the Nether must immediately offer a fortress");
		helper.assertTrue((netherTargets & TerminalStructureTarget.bit(TerminalStructureTarget.VILLAGE)) == 0,
				"Overworld structures must not leak into the Nether target list");
		var end = helper.getLevel().getServer().getLevel(Level.END);
		helper.assertTrue(end != null, "End level must exist for exclusion verification");
		helper.assertTrue(player.teleportTo(end, 0.5, 64.0, 0.5, Set.of(), 0.0F, 0.0F, true),
				"End exclusion fixture");
		helper.assertValueEqual(StructureNavigationService.availableTargetsMask(
				player, data.terminalRecord(player.getUUID()).orElseThrow()), 0,
				"The End must not expose structure-navigation candidates");
		helper.succeed();
	}

	@GameTest
	public void toolSnapshotUsesRealStateAndGuidanceReplacementKeepsSavedPlaces(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		var home = player.blockPosition().offset(7, 0, -5);
		var portal = player.blockPosition().offset(-12, 3, 9);
		var stronghold = player.blockPosition().offset(1_200, -20, 400);
		String dimension = player.level().dimension().identifier().toString();
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putLong(TerminalData.HOME_POSITION, home.asLong());
			record.putString(TerminalData.HOME_DIMENSION, dimension);
			record.putLong(TerminalData.LAST_PORTAL_POSITION, portal.asLong());
			record.putString(TerminalData.LAST_PORTAL_DIMENSION, dimension);
			record.putInt(TerminalData.CRAFTED_EYE_COUNT,
					SurvivalProgressService.REQUIRED_STRONGHOLD_UNLOCK_EYES);
			record.putInt(TerminalData.EYE_SAMPLE_COUNT, SurvivalProgressService.REQUIRED_EYE_SAMPLES);
			record.putLong(TerminalData.STRONGHOLD_POSITION, stronghold.asLong());
			record.putString(TerminalData.STRONGHOLD_DIMENSION, dimension);
		});
		var snapshot = TerminalToolService.snapshot(player, TerminalTool.HOME.slot());
		helper.assertValueEqual(snapshot.protocolVersion(), 5, "Independent tool snapshot protocol");
		helper.assertFalse(snapshot.homeKnown(),
				"Legacy manual home coordinates must not replace the player's real respawn point");
		helper.assertTrue(snapshot.portalKnown() && snapshot.portalSameDimension(), "Portal arrival must be real and local");
		helper.assertValueEqual(snapshot.weather(), player.level().isThundering() ? 2
				: player.level().isRaining() ? 1 : 0, "Tool weather must match the server level");
		helper.assertValueEqual(snapshot.eyeSampleCount(), SurvivalProgressService.REQUIRED_EYE_SAMPLES,
				"Eye sample count");
		helper.assertTrue(snapshot.strongholdKnown() && snapshot.strongholdMaxDistance()
				> snapshot.strongholdMinDistance(), "Three samples must yield a bounded, non-exact range");
		helper.assertTrue(Math.hypot(snapshot.strongholdDx(), snapshot.strongholdDz()) < 150.0D,
				"The client bearing must be a direction vector, not the exact stronghold coordinate delta");

		helper.assertTrue(TerminalToolService.startGuidance(player, TerminalTool.PORTAL.slot()),
				"A real portal arrival can control the compass");
		var replaced = data.terminalRecord(player.getUUID()).orElseThrow();
		helper.assertValueEqual(replaced.getIntOr(TerminalData.ACTIVE_GUIDANCE_TOOL, -1),
				TerminalTool.PORTAL.slot(), "Exactly one compass owner remains");
		helper.assertValueEqual(replaced.getLongOr(TerminalData.HOME_POSITION, 0L), home.asLong(),
				"Replacing guidance must preserve home");
		helper.assertValueEqual(replaced.getLongOr(TerminalData.LAST_PORTAL_POSITION, 0L), portal.asLong(),
				"Replacing guidance must preserve portal arrival");
		helper.assertTrue(TerminalToolService.stopGuidance(player, 0), "Guidance can stop cleanly");
		helper.assertValueEqual(data.terminalRecord(player.getUUID()).orElseThrow()
				.getIntOr(TerminalData.ACTIVE_GUIDANCE_TOOL, -1), TerminalToolService.NO_TOOL,
				"Stopping guidance clears only the active pointer");
		helper.succeed();
	}

	@GameTest
	public void legacyAutomaticTuningControlIsReservedAndIgnored(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		data.updateTerminalRecord(player.getUUID(), record -> {
			record.putInt(TerminalData.CALIBRATED_BANDS_MASK, 0b111);
			record.putBoolean(TerminalData.AUTO_TUNING, false);
		});
		player.setItemInHand(InteractionHand.MAIN_HAND, findTerminal(player));
		TerminalRuntimeService.open(player, 0);
		TerminalRuntimeService.control(player, TerminalControlPayload.SET_AUTO_TUNING, 1);
		var record = data.terminalRecord(player.getUUID()).orElseThrow();
		helper.assertValueEqual(record.getIntOr(TerminalData.CALIBRATED_BANDS_MASK, 0), 0b111,
				"Legacy calibration data remains readable for old saves");
		helper.assertFalse(record.getBooleanOr(TerminalData.AUTO_TUNING, true),
				"Reserved automatic-tuning input cannot reactivate the retired system");
		TerminalRuntimeService.control(player, TerminalControlPayload.CLOSE, 0);
		helper.succeed();
	}

	@GameTest
	public void boundRecoveryWaitsForSafeInventorySpace(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		data.updateTerminalRecord(player.getUUID(), record -> record.putBoolean(TerminalData.BOUND, true));
		TerminalLifecycleService.ensureCarried(player, false);
		int generation = TerminalData.copyGeneration(findTerminal(player));
		removeTerminals(player);
		for (int slot = 0; slot < player.getInventory().getNonEquipmentItems().size(); slot++) {
			player.getInventory().getNonEquipmentItems().set(slot, new ItemStack(Items.STONE, 64));
		}
		player.getInventory().setChanged();
		helper.assertValueEqual(player.getInventory().getFreeSlot(), -1, "Fixture must fill every recovery-safe slot");
		int stoneBefore = countItem(player, Items.STONE);

		helper.assertFalse(TerminalLifecycleService.ensureCarried(player, false),
				"Bound recovery must wait when no inventory slot is safe");
		helper.assertValueEqual(countTerminals(player), 0, "No terminal may overwrite a full slot");
		helper.assertValueEqual(countItem(player, Items.STONE), stoneBefore,
				"Failed recovery must preserve every existing item");

		player.getInventory().getNonEquipmentItems().set(0, ItemStack.EMPTY);
		helper.assertTrue(TerminalLifecycleService.ensureCarried(player, false),
				"Pending recovery must complete after a slot becomes available");
		helper.assertValueEqual(countTerminals(player), 1, "Exactly one pending recovery terminal");
		helper.assertValueEqual(TerminalData.copyGeneration(findTerminal(player)), generation + 1,
				"Waiting retries must not create additional terminal generations");
		helper.assertValueEqual(countItem(player, Items.STONE), stoneBefore - 64,
				"Only the explicitly cleared stack may be replaced");
		helper.succeed();
	}

	@GameTest
	public void unboundRecoveryInvalidatesDuplicatesWithoutForcingStorageReturn(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		FrequencyWorldData data = FrequencyWorldData.get(helper.getLevel().getServer());
		ItemStack original = findTerminal(player).copy();
		player.getInventory().add(original.copy());
		TerminalLifecycleService.ensureCarried(player, true);
		helper.assertValueEqual(countTerminals(player), 1, "Duplicate valid terminals after reconciliation");

		int generation = TerminalData.copyGeneration(findTerminal(player));
		removeTerminals(player);
		helper.assertFalse(TerminalLifecycleService.ensureCarried(player, false),
				"An unbound terminal may remain dropped or stored during the active session");
		helper.assertValueEqual(countTerminals(player), 0, "Unbound storage state");
		helper.assertTrue(TerminalLifecycleService.ensureCarried(player, true),
				"Reconnect or explicit recovery must restore an unbound terminal");
		ItemStack recovered = findTerminal(player);
		helper.assertValueEqual(TerminalData.copyGeneration(recovered), generation + 1,
				"Unbound recovery copy generation");
		helper.assertFalse(data.isValidTerminal(original, player.getUUID()),
				"Previous physical copies must be invalid after recovery");
		helper.assertTrue(data.isValidTerminal(recovered, player.getUUID()),
				"Exactly one recovered copy must remain authoritative");
		int recoveredGeneration = TerminalData.copyGeneration(recovered);
		helper.assertTrue(TerminalLifecycleService.adminRepair(player),
				"Administrator repair must work from the authoritative record");
		helper.assertValueEqual(countTerminals(player), 1, "Administrator repair terminal count");
		helper.assertValueEqual(TerminalData.copyGeneration(findTerminal(player)), recoveredGeneration + 1,
				"Administrator repair copy generation");
		helper.succeed();
	}

	private static ItemStack findTerminal(ServerPlayer player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(ModItems.OLD_TERMINAL)) {
				return stack;
			}
		}
		throw new AssertionError("Player has no terminal");
	}

	private static int countTerminals(ServerPlayer player) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (player.getInventory().getItem(slot).is(ModItems.OLD_TERMINAL)) {
				count++;
			}
		}
		return count;
	}

	private static int countItem(ServerPlayer player, net.minecraft.world.item.Item item) {
		int count = 0;
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			ItemStack stack = player.getInventory().getItem(slot);
			if (stack.is(item)) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static void removeTerminals(ServerPlayer player) {
		for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
			if (player.getInventory().getItem(slot).is(ModItems.OLD_TERMINAL)) {
				player.getInventory().setItem(slot, ItemStack.EMPTY);
			}
		}
		player.getInventory().setChanged();
	}

	@Override
	public void invokeTestMethod(GameTestHelper helper, Method method) throws ReflectiveOperationException {
		method.invoke(this, helper);
	}
}
