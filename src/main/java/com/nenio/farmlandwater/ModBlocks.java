package com.nenio.farmlandwater;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(farmlandwater.MOD_ID);

    public static final DeferredBlock<WaterSurfacePlatformBlock> WATER_SURFACE_PLATFORM =
            BLOCKS.register("water_surface_platform", () ->
                    new WaterSurfacePlatformBlock(
                            BlockBehaviour.Properties.of()
                                    .noOcclusion()
                                    .replaceable()
                                    .strength(-1.0F, 3_600_000F)
                                    .isValidSpawn((s, l, p, e) -> false)
                                    .noLootTable()
                                    .setId(ResourceKey.create(
                                            Registries.BLOCK,
                                            ResourceLocation.fromNamespaceAndPath(
                                                    farmlandwater.MOD_ID, "water_surface_platform"
                                            )
                                    ))
                    )
            );

    private ModBlocks() {}
}
