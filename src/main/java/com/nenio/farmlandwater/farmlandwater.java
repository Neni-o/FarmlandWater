package com.nenio.farmlandwater;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.levelgen.Heightmap;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(farmlandwater.MOD_ID)
public class farmlandwater {
    public static final String MOD_ID = "farmlandwater";

    // GameRule key; set during common setup
    public static GameRules.Key<GameRules.BooleanValue> GR_FARMLAND_WATER;

    private static final Direction[] CARDINALS = new Direction[]{
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    // Scan settings around the player
    private static final int SCAN_RADIUS = 8;
    private static final int SCAN_Y_RANGE = 6;
    private static final int SCAN_EVERY_TICKS = 10;

    public farmlandwater(FMLJavaModLoadingContext context) {
        // New in Forge 1.21.6: use BusGroup instead of IEventBus for mod events
        BusGroup modBus = context.getModBusGroup();

        // DeferredRegister now registers against BusGroup
        ModBlocks.BLOCKS.register(modBus);

        // Mod lifecycle event: hook via the event's own bus + BusGroup
        FMLCommonSetupEvent.getBus(modBus).addListener(this::onCommonSetup);

        // Forge/game events: use the static BUS on each event class (no @SubscribeEvent)
        TickEvent.PlayerTickEvent.BUS.addListener(this::onPlayerTick);
        BlockEvent.BreakEvent.BUS.addListener(this::onFarmlandBreak);
        BlockEvent.EntityPlaceEvent.BUS.addListener(this::onWaterPlacedByEntity);
        BlockEvent.FluidPlaceBlockEvent.BUS.addListener(this::onFluidPlaced);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        // Register the gamerule (default: true) on the main thread after parallel setup completes
        event.enqueueWork(() -> GR_FARMLAND_WATER = GameRules.register(
                "FarmlandWater", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true)
        ));
    }

    private void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // Phase check (END ≈ Post)
        if (event.phase != TickEvent.Phase.END) return;

        var player = event.player;
        Level level = player.level();
        if (level.isClientSide) return;

        final boolean enabled = isFeatureEnabled(level);
        if ((player.tickCount % SCAN_EVERY_TICKS) != 0) return;

        final int px = (int) Math.floor(player.getX());
        final int pz = (int) Math.floor(player.getZ());

        int topY = level.getHeight(Heightmap.Types.WORLD_SURFACE, px, pz);
        final int worldMinY = level.dimensionType().minY();
        final int worldMaxY = worldMinY + level.dimensionType().height() - 1;

        int yMin = Math.max(worldMinY, topY - SCAN_Y_RANGE);
        int yMax = Math.min(worldMaxY, topY + SCAN_Y_RANGE);

        // Walk a cube around the player and update platforms/water accordingly
        for (int x = px - SCAN_RADIUS; x <= px + SCAN_RADIUS; x++) {
            for (int z = pz - SCAN_RADIUS; z <= pz + SCAN_RADIUS; z++) {
                for (int y = yMax; y >= yMin; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState st = level.getBlockState(pos);

                    if (enabled) {
                        if (st.getBlock() instanceof FarmBlock) {
                            placePlatformsAroundFarmland(level, pos);
                        } else if (st.is(ModBlocks.WATER_SURFACE_PLATFORM.get())) {
                            if (!hasAdjacentFarmland(level, pos)) {
                                level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
                            }
                        }
                    } else {
                        // Feature disabled: revert any platforms back to water
                        if (st.is(ModBlocks.WATER_SURFACE_PLATFORM.get())) {
                            level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }
    }

    private void onFarmlandBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide) return;

        // If farmland is broken, revert adjacent platforms that are no longer needed
        if (event.getState().getBlock() instanceof FarmBlock) {
            revertPlatformsAround(level, event.getPos());
        }
    }

    private void onWaterPlacedByEntity(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide) return;
        if (!isFeatureEnabled(level)) return;

        BlockPos pos = event.getPos();
        BlockState placed = event.getPlacedBlock();

        // Convert newly placed water into a platform if it borders farmland
        if ((level.getFluidState(pos).is(Fluids.WATER) || placed.getFluidState().is(Fluids.WATER))
                && !level.getBlockState(pos).is(ModBlocks.WATER_SURFACE_PLATFORM.get())) {
            maybeConvertWaterToPlatform(level, pos);
        }
    }

    private void onFluidPlaced(BlockEvent.FluidPlaceBlockEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide) return;
        if (!isFeatureEnabled(level)) return;

        BlockPos pos = event.getPos();
        BlockState newState = event.getNewState();

        if (level.getBlockState(pos).is(ModBlocks.WATER_SURFACE_PLATFORM.get())) return;

        // Same as above, but for “fluid updates” rather than entity placement
        if (level.getFluidState(pos).is(Fluids.WATER) || newState.getFluidState().is(Fluids.WATER)) {
            maybeConvertWaterToPlatform(level, pos);
        }
    }

    private static void placePlatformsAroundFarmland(Level level, BlockPos farmlandPos) {
        for (Direction dir : CARDINALS) {
            BlockPos wpos = farmlandPos.relative(dir);
            if (level.getFluidState(wpos).is(Fluids.WATER)) {
                if (!level.getBlockState(wpos).is(ModBlocks.WATER_SURFACE_PLATFORM.get())) {
                    // Place a waterlogged platform on water next to farmland
                    BlockState platform = ModBlocks.WATER_SURFACE_PLATFORM.get()
                            .defaultBlockState()
                            .setValue(WaterSurfacePlatformBlock.WATERLOGGED, true);
                    level.setBlock(wpos, platform, 2);
                }
            } else {
                // Remove platform if the adjacent block is no longer water
                if (level.getBlockState(wpos).is(ModBlocks.WATER_SURFACE_PLATFORM.get())) {
                    level.setBlock(wpos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static void revertPlatformsAround(Level level, BlockPos oldFarmland) {
        for (Direction dir : CARDINALS) {
            BlockPos pos = oldFarmland.relative(dir);
            // Turn platform back into water if it no longer touches any farmland
            if (level.getBlockState(pos).is(ModBlocks.WATER_SURFACE_PLATFORM.get()) && !hasAdjacentFarmland(level, pos)) {
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
            }
        }
    }

    private static void maybeConvertWaterToPlatform(Level level, BlockPos pos) {
        // Convert a water block into a platform only if there is adjacent farmland
        if (!level.getFluidState(pos).is(Fluids.WATER)) return;
        if (!hasAdjacentFarmland(level, pos)) return;

        BlockState platform = ModBlocks.WATER_SURFACE_PLATFORM.get()
                .defaultBlockState()
                .setValue(WaterSurfacePlatformBlock.WATERLOGGED, true);
        level.setBlock(pos, platform, 2);
    }

    private static boolean hasAdjacentFarmland(Level level, BlockPos pos) {
        for (Direction d : CARDINALS) {
            if (level.getBlockState(pos.relative(d)).getBlock() instanceof FarmBlock) return true;
        }
        return false;
    }

    private static boolean isFeatureEnabled(Level level) {
        // Read the gamerule on the server; default to true if not registered yet
        try {
            if (level instanceof ServerLevel serverLevel) {
                GameRules rules = serverLevel.getGameRules();
                return GR_FARMLAND_WATER != null ? rules.getBoolean(GR_FARMLAND_WATER) : true;
            }
        } catch (Throwable ignored) { }
        return true;
    }
}
