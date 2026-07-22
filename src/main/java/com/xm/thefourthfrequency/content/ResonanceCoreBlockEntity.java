package com.xm.thefourthfrequency.content;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Optional;
import java.util.UUID;

/**
 * Non-authoritative proxy identity for diagnostics and stale-block rejection.
 * Ritual contents remain exclusively in {@code WorldInterfaceState}.
 */
public final class ResonanceCoreBlockEntity extends BlockEntity {
	private UUID boundEncounterId;
	private long lastKnownRevision;

	public ResonanceCoreBlockEntity(BlockPos position, BlockState state) {
		super(WorldInterfaceBlockEntities.RESONANCE_CORE, position, state);
	}

	public Optional<UUID> boundEncounterId() {
		return Optional.ofNullable(boundEncounterId);
	}

	public long lastKnownRevision() {
		return lastKnownRevision;
	}

	public void bind(UUID encounterId, long revision) {
		long sanitizedRevision = Math.max(0L, revision);
		if (java.util.Objects.equals(boundEncounterId, encounterId) && lastKnownRevision == sanitizedRevision) return;
		boundEncounterId = encounterId;
		lastKnownRevision = sanitizedRevision;
		setChanged();
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		String encoded = input.getStringOr("encounter_id", "");
		try {
			boundEncounterId = encoded.isBlank() ? null : UUID.fromString(encoded);
		} catch (IllegalArgumentException exception) {
			boundEncounterId = null;
		}
		lastKnownRevision = Math.max(0L, input.getLongOr("last_known_revision", 0L));
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		if (boundEncounterId != null) output.putString("encounter_id", boundEncounterId.toString());
		output.putLong("last_known_revision", lastKnownRevision);
	}
}
