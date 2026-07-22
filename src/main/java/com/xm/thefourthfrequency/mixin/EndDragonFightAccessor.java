package com.xm.thefourthfrequency.mixin;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.level.dimension.end.DragonRespawnAnimation;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.UUID;

@Mixin(EndDragonFight.class)
public interface EndDragonFightAccessor {
	@Accessor("dragonEvent")
	ServerBossEvent thefourthfrequency$dragonEvent();

	@Accessor("dragonUUID")
	void thefourthfrequency$setDragonUuid(UUID uuid);

	/** Suppresses vanilla respawn/portal side effects without invoking setDragonKilled. */
	@Accessor("dragonKilled")
	void thefourthfrequency$setDragonKilledSilently(boolean dragonKilled);

	@Accessor("needsStateScanning")
	void thefourthfrequency$setNeedsStateScanning(boolean needsStateScanning);

	@Accessor("respawnStage")
	void thefourthfrequency$setRespawnStage(DragonRespawnAnimation respawnStage);

	@Accessor("respawnCrystals")
	void thefourthfrequency$setRespawnCrystals(List<EndCrystal> respawnCrystals);
}
