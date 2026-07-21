package com.xm.thefourthfrequency.mixin;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.level.dimension.end.EndDragonFight;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

@Mixin(EndDragonFight.class)
public interface EndDragonFightAccessor {
	@Accessor("dragonEvent")
	ServerBossEvent thefourthfrequency$dragonEvent();

	@Accessor("dragonUUID")
	void thefourthfrequency$setDragonUuid(UUID uuid);
}
