package com.nenio.farmlandwater;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModBlocks {

    public static final WaterSurfacePlatformBlock WATER_SURFACE_PLATFORM =
            new WaterSurfacePlatformBlock(
                    FabricBlockSettings.copyOf(Blocks.WATER)
                            .noCollision()
                            .nonOpaque()
                            .replaceable()
                            .strength(-1.0F, 3_600_000F)
                            .allowsSpawning((s, w, p, t) -> false)
                            .dropsNothing()
            );

    public static void register() {
        Registry.register(Registries.BLOCK, id("water_surface_platform"), WATER_SURFACE_PLATFORM);
    }

    private static Identifier id(String path) {
        return Identifier.of(farmlandwater.MOD_ID, path);
    }

    private ModBlocks() {}
}
