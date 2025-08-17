package com.nenio.farmlandwater;

import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, farmlandwater.MOD_ID);

    public static final RegistryObject<Block> WATER_SURFACE_PLATFORM =
            BLOCKS.register("water_surface_platform", WaterSurfacePlatformBlock::new);

    private ModBlocks() {} // util
}
