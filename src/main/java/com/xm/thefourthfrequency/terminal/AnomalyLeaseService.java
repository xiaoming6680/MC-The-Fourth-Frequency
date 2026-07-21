package com.xm.thefourthfrequency.terminal;

import com.xm.thefourthfrequency.content.TerminalData;
import com.xm.thefourthfrequency.world.FrequencyWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public final class AnomalyLeaseService {
	private AnomalyLeaseService() { }

	public static int cascadeDoors(ServerPlayer owner, int durationTicks, int limit) {
		if (!(owner.level() instanceof ServerLevel level)) return 0;
		FrequencyWorldData data = FrequencyWorldData.get(level.getServer());
		CompoundTag record = data.terminalRecord(owner.getUUID()).orElse(null);
		if (record == null) return 0;
		ListTag leases = record.getListOrEmpty(TerminalData.ANOMALY_LEASES).copy();
		int changed = 0;
		BlockPos origin = owner.blockPosition();
		for (int radius = 1; radius <= 12 && changed < limit; radius++) {
			for (int dx = -radius; dx <= radius && changed < limit; dx++) {
				for (int dy = -4; dy <= 4 && changed < limit; dy++) {
					for (int dz = -radius; dz <= radius && changed < limit; dz++) {
						if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
						BlockPos pos = origin.offset(dx, dy, dz);
						if (!level.hasChunkAt(pos)) continue;
						BlockState state = level.getBlockState(pos);
						if (!(state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapDoorBlock)
								|| !state.hasProperty(BlockStateProperties.OPEN)) continue;
						boolean original = state.getValue(BlockStateProperties.OPEN);
						level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, !original), 3);
						CompoundTag lease = new CompoundTag();
						lease.putString("type", "door");
						lease.putString("dimension", level.dimension().identifier().toString());
						lease.putLong("position", pos.asLong());
						lease.putBoolean("open", original);
						lease.putLong("until", level.getGameTime() + durationTicks);
						leases.add(lease);
						changed++;
					}
				}
			}
		}
		if (changed > 0) data.updateTerminalRecord(owner.getUUID(), tag -> tag.put(TerminalData.ANOMALY_LEASES, leases));
		return changed;
	}

	public static void recoverDue(MinecraftServer server, FrequencyWorldData data, ServerPlayer owner, boolean force) {
		CompoundTag record = data.terminalRecord(owner.getUUID()).orElse(null);
		if (record == null) return;
		ListTag leases = record.getListOrEmpty(TerminalData.ANOMALY_LEASES);
		if (leases.isEmpty()) return;
		ListTag remaining = new ListTag();
		long now = owner.level().getGameTime();
		for (int index = 0; index < leases.size(); index++) {
			CompoundTag lease = leases.getCompoundOrEmpty(index);
			if (!force && lease.getLongOr("until", Long.MAX_VALUE) > now) {
				remaining.add(lease.copy());
				continue;
			}
			for (ServerLevel level : server.getAllLevels()) {
				if (!level.dimension().identifier().toString().equals(lease.getStringOr("dimension", ""))) continue;
				BlockPos pos = BlockPos.of(lease.getLongOr("position", 0L));
				if (!level.hasChunkAt(pos)) {
					remaining.add(lease.copy());
					break;
				}
				BlockState state = level.getBlockState(pos);
				if (state.hasProperty(BlockStateProperties.OPEN))
					level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, lease.getBooleanOr("open", false)), 3);
				break;
			}
		}
		data.updateTerminalRecord(owner.getUUID(), tag -> tag.put(TerminalData.ANOMALY_LEASES, remaining));
	}
}
