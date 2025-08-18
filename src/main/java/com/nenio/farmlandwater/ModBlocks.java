package com.nenio.farmlandwater;

import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(farmlandwater.MOD_ID);

    public static final DeferredBlock<WaterSurfacePlatformBlock> WATER_SURFACE_PLATFORM =
            BLOCKS.register("water_surface_platform", WaterSurfacePlatformBlock::new);

    private ModBlocks() {}
}
