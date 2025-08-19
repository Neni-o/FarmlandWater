package com.nenio.farmlandwater;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, farmlandwater.MOD_ID);

    public static final RegistryObject<WaterSurfacePlatformBlock> WATER_SURFACE_PLATFORM =
            BLOCKS.register("water_surface_platform",
                    () -> new WaterSurfacePlatformBlock(
                            BlockBehaviour.Properties.ofFullCopy(Blocks.WATER)
                                    .noCollission()
                                    .noOcclusion()
                                    .replaceable()
                                    .strength(-1.0F, 3_600_000F)
                                    .isValidSpawn((s, l, p, e) -> false)
                                    .noLootTable()
                                    .setId(ResourceKey.create(
                                            Registries.BLOCK,
                                            ResourceLocation.fromNamespaceAndPath(
                                                    farmlandwater.MOD_ID, "water_surface_platform")
                                    ))
                    )
            );

    private ModBlocks() {}
}
