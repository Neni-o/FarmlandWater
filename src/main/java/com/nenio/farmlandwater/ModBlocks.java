package com.nenio.farmlandwater;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

public final class ModBlocks {

    public static final RegistryKey<Block> WATER_SURFACE_PLATFORM_KEY =
            RegistryKey.of(RegistryKeys.BLOCK, id("water_surface_platform"));

    public static WaterSurfacePlatformBlock WATER_SURFACE_PLATFORM;

    public static void register() {
        WATER_SURFACE_PLATFORM = Registry.register(
                Registries.BLOCK,
                WATER_SURFACE_PLATFORM_KEY,
                new WaterSurfacePlatformBlock(
                        Block.Settings.copy(Blocks.WATER)
                                .noCollision()
                                .nonOpaque()
                                .replaceable()
                                .strength(-1.0F, 3_600_000F)
                                .allowsSpawning((s, w, p, t) -> false)
                                .dropsNothing()
                                .registryKey(WATER_SURFACE_PLATFORM_KEY)
                )
        );

    }

    private static Identifier id(String path) {
        return Identifier.of(farmlandwater.MOD_ID, path);
    }

    private ModBlocks() {}
}
