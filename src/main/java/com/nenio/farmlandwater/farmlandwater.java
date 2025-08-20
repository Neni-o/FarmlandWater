package com.nenio.farmlandwater;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.fluid.Fluids;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

public class farmlandwater implements ModInitializer {
    public static final String MOD_ID = "farmlandwater";

    // GameRule
    public static GameRules.Key<GameRules.BooleanRule> GR_FARMLAND_WATER;

    private static final Direction[] CARDINALS = new Direction[]{
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private static final int SCAN_RADIUS = 8;
    private static final int SCAN_Y_RANGE = 6;
    private static final int SCAN_EVERY_TICKS = 10;

    @Override
    public void onInitialize() {
        ModBlocks.register();

        GR_FARMLAND_WATER = GameRuleRegistry.register(
                "FarmlandWater",
                GameRules.Category.PLAYER,
                GameRuleFactory.createBooleanRule(true)
        );

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % SCAN_EVERY_TICKS != 0) return;
            world.getPlayers().forEach(p ->
                    scanAroundPlayer(world, p.getBlockX(), p.getBlockZ())
            );
        });

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> {
            if (state.getBlock() instanceof FarmlandBlock) {
                revertPlatformsAround(world, pos);
            }
        });
    }


    private static void scanAroundPlayer(ServerWorld world, int px, int pz) {
        final boolean enabled = isFeatureEnabled(world);

        int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, px, pz);
        int worldMinY = world.getBottomY();
        int worldMaxY = worldMinY + world.getHeight() - 1;

        int yMin = Math.max(worldMinY, topY - SCAN_Y_RANGE);
        int yMax = Math.min(worldMaxY, topY + SCAN_Y_RANGE);

        BlockPos.Mutable pos = new BlockPos.Mutable();

        for (int x = px - SCAN_RADIUS; x <= px + SCAN_RADIUS; x++) {
            for (int z = pz - SCAN_RADIUS; z <= pz + SCAN_RADIUS; z++) {
                for (int y = yMax; y >= yMin; y--) {
                    pos.set(x, y, z);
                    BlockState st = world.getBlockState(pos);

                    if (enabled) {
                        if (st.getBlock() instanceof FarmlandBlock) {
                            placePlatformsAroundFarmland(world, pos);
                        } else if (st.isOf(ModBlocks.WATER_SURFACE_PLATFORM)) {
                            if (!hasAdjacentFarmland(world, pos)) {
                                world.setBlockState(pos, Blocks.WATER.getDefaultState(), Block.NOTIFY_ALL);
                            }
                        }
                    } else {
                        if (st.isOf(ModBlocks.WATER_SURFACE_PLATFORM)) {
                            world.setBlockState(pos, Blocks.WATER.getDefaultState(), Block.NOTIFY_ALL);
                        }
                    }
                }
            }
        }
    }

    private static void placePlatformsAroundFarmland(World world, BlockPos farmlandPos) {
        for (Direction dir : CARDINALS) {
            BlockPos wpos = farmlandPos.offset(dir);
            if (world.getFluidState(wpos).isOf(Fluids.WATER)) {
                if (!world.getBlockState(wpos).isOf(ModBlocks.WATER_SURFACE_PLATFORM)) {
                    BlockState platform = ModBlocks.WATER_SURFACE_PLATFORM.getDefaultState()
                            .with(WaterSurfacePlatformBlock.WATERLOGGED, true);
                    world.setBlockState(wpos, platform, Block.NOTIFY_ALL);
                }
            } else {
                if (world.getBlockState(wpos).isOf(ModBlocks.WATER_SURFACE_PLATFORM)) {
                    world.setBlockState(wpos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
        }
    }

    private static void revertPlatformsAround(World world, BlockPos oldFarmland) {
        for (Direction dir : CARDINALS) {
            BlockPos pos = oldFarmland.offset(dir);
            if (world.getBlockState(pos).isOf(ModBlocks.WATER_SURFACE_PLATFORM) && !hasAdjacentFarmland(world, pos)) {
                world.setBlockState(pos, Blocks.WATER.getDefaultState(), Block.NOTIFY_ALL);
            }
        }
    }

    private static void maybeConvertWaterToPlatform(World world, BlockPos pos) {
        if (!world.getFluidState(pos).isOf(Fluids.WATER)) return;
        if (!hasAdjacentFarmland(world, pos)) return;

        BlockState platform = ModBlocks.WATER_SURFACE_PLATFORM.getDefaultState()
                .with(WaterSurfacePlatformBlock.WATERLOGGED, true);
        world.setBlockState(pos, platform, Block.NOTIFY_ALL);
    }

    private static boolean hasAdjacentFarmland(World world, BlockPos pos) {
        for (Direction d : CARDINALS) {
            if (world.getBlockState(pos.offset(d)).getBlock() instanceof FarmlandBlock) return true;
        }
        return false;
    }

    private static boolean isFeatureEnabled(World world) {
        return world.getGameRules().getBoolean(GR_FARMLAND_WATER);
    }
}
