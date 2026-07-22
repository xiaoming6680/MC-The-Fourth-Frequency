package com.xm.thefourthfrequency.content;

import com.xm.thefourthfrequency.bootstrap.TheFourthFrequency;
import com.xm.thefourthfrequency.correction.ReworkCollisionProfile;
import com.xm.thefourthfrequency.entity.ReworkEntity;
import com.xm.thefourthfrequency.entity.MisreadBodyEntity;
import com.xm.thefourthfrequency.entity.WatcherEntity;
import com.xm.thefourthfrequency.entity.WorldInterfaceEntity;
import com.xm.thefourthfrequency.entity.WorldInterfaceEnergyOrbEntity;
import com.xm.thefourthfrequency.entity.WorldInterfacePartEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class ModEntities {
	private static final ReworkCollisionProfile REWORK_BASE_COLLISION = ReworkCollisionProfile.forStage(1);
	private static final ResourceKey<EntityType<?>> REWORK_KEY = ResourceKey.create(
			Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, "rework_body"));
	private static final ResourceKey<EntityType<?>> MISREAD_KEY = ResourceKey.create(
			Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, "misread_body"));
	private static final ResourceKey<EntityType<?>> WATCHER_KEY = ResourceKey.create(
			Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, "watcher"));
	private static final ResourceKey<EntityType<?>> WORLD_INTERFACE_KEY = ResourceKey.create(
			Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, "world_interface"));
	private static final ResourceKey<EntityType<?>> WORLD_INTERFACE_PART_KEY = ResourceKey.create(
			Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(TheFourthFrequency.MOD_ID, "world_interface_part"));
	private static final ResourceKey<EntityType<?>> WORLD_INTERFACE_ENERGY_ORB_KEY = ResourceKey.create(
			Registries.ENTITY_TYPE, WorldInterfaceEnergyOrbEntity.TYPE_ID);

	public static final EntityType<ReworkEntity> REWORK_BODY = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			REWORK_KEY,
			EntityType.Builder.of(ReworkEntity::new, MobCategory.MONSTER)
					.sized(REWORK_BASE_COLLISION.width(), REWORK_BASE_COLLISION.height())
					.eyeHeight(REWORK_BASE_COLLISION.eyeHeight())
					.clientTrackingRange(8)
					.updateInterval(2)
					.build(REWORK_KEY));

	public static final EntityType<MisreadBodyEntity> MISREAD_BODY = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			MISREAD_KEY,
			EntityType.Builder.of(MisreadBodyEntity::new, MobCategory.MONSTER)
					.sized(1.35F, 1.8F)
					.eyeHeight(0.82F)
					.clientTrackingRange(10)
					.updateInterval(2)
					.build(MISREAD_KEY));

	public static final EntityType<WatcherEntity> WATCHER = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			WATCHER_KEY,
			EntityType.Builder.of(WatcherEntity::new, MobCategory.MONSTER)
					.sized(0.62F, 2.9F)
					.eyeHeight(2.62F)
					.clientTrackingRange(10)
					.updateInterval(2)
					.build(WATCHER_KEY));

	public static final EntityType<WorldInterfaceEntity> WORLD_INTERFACE = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			WORLD_INTERFACE_KEY,
			EntityType.Builder.of(WorldInterfaceEntity::new, MobCategory.MONSTER)
					.sized(7.0F, 16.0F)
					.eyeHeight(11.0F)
					.fireImmune()
					.clientTrackingRange(32)
					.updateInterval(1)
					.build(WORLD_INTERFACE_KEY));

	public static final EntityType<WorldInterfacePartEntity> WORLD_INTERFACE_PART = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			WORLD_INTERFACE_PART_KEY,
			EntityType.Builder.of(WorldInterfacePartEntity::new, MobCategory.MISC)
					.sized(3.5F, 7.0F)
					.noSave()
					.noSummon()
					.fireImmune()
					.clientTrackingRange(32)
					.updateInterval(1)
					.build(WORLD_INTERFACE_PART_KEY));

	public static final EntityType<WorldInterfaceEnergyOrbEntity> WORLD_INTERFACE_ENERGY_ORB = Registry.register(
			BuiltInRegistries.ENTITY_TYPE,
			WORLD_INTERFACE_ENERGY_ORB_KEY,
			EntityType.Builder.of(WorldInterfaceEnergyOrbEntity::new, MobCategory.MISC)
					.sized(1.0F, 1.0F)
					.fireImmune()
					.noSummon()
					.clientTrackingRange(24)
					.updateInterval(1)
					.build(WORLD_INTERFACE_ENERGY_ORB_KEY));

	private ModEntities() {
	}

	public static void initialize() {
		FabricDefaultAttributeRegistry.register(REWORK_BODY, ReworkEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(MISREAD_BODY, MisreadBodyEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(WATCHER, WatcherEntity.createAttributes());
		FabricDefaultAttributeRegistry.register(WORLD_INTERFACE, WorldInterfaceEntity.createAttributes());
		TheFourthFrequency.LOGGER.info("Registered rework, watcher, prelude misread, and world-interface entities");
	}
}
