package com.nenio.farmlandwater;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.levelgen.Heightmap;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(farmlandwater.MOD_ID)
public class farmlandwater {
    public static final String MOD_ID = "farmlandwater";

    // /gamerule FarmlandWater <true|false>
    public static GameRules.Key<GameRules.BooleanValue> GR_FARMLAND_WATER;

    // 4 cardinal directions
    private static final Direction[] CARDINALS = new Direction[]{
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    // Player-local scan
    private static final int SCAN_RADIUS = 8;     // in blocks (x/z)
    private static final int SCAN_Y_RANGE = 6;    // +/- from surface
    private static final int SCAN_EVERY_TICKS = 10;

    public farmlandwater() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        modBus.addListener(this::onCommonSetup);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> GR_FARMLAND_WATER = GameRules.register(
                "FarmlandWater", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true)
        ));
    }

    // =====================================================================
    // 1) LIGHT SCAN AROUND THE PLAYER — both for placing (rule ON) and cleaning (rule OFF)
    // =====================================================================
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var player = event.player;
        Level level = player.level();
        if (level.isClientSide) return;

        final boolean enabled = isFeatureEnabled(level);

        // run every N ticks per player
        if ((player.tickCount % SCAN_EVERY_TICKS) != 0) return;

        final int px = (int)Math.floor(player.getX());
        final int pz = (int)Math.floor(player.getZ());

        // approximate surface via heightmap — limit Y scan range
        int topY = level.getHeight(Heightmap.Types.WORLD_SURFACE, px, pz);
        int yMin = Math.max(level.getMinBuildHeight(), topY - SCAN_Y_RANGE);
        int yMax = Math.min(level.getMaxBuildHeight()-1, topY + SCAN_Y_RANGE);

        for (int x = px - SCAN_RADIUS; x <= px + SCAN_RADIUS; x++) {
            for (int z = pz - SCAN_RADIUS; z <= pz + SCAN_RADIUS; z++) {
                // check a few layers around the surface (terraces, small height differences)
                for (int y = yMax; y >= yMin; y--) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState st = level.getBlockState(pos);

                    if (enabled) {
                        // Normal behavior: arm adjacent water around farmland; clean orphan platforms.
                        if (st.getBlock() instanceof FarmBlock) {
                            placePlatformsAroundFarmland(level, pos);
                        } else if (st.is(ModBlocks.WATER_SURFACE_PLATFORM.get())) {
                            if (!hasAdjacentFarmland(level, pos)) {
                                level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
                            }
                        }
                    } else {
                        // CLEAN-UP MODE: rule is OFF → turn any platform back into water.
                        if (st.is(ModBlocks.WATER_SURFACE_PLATFORM.get())) {
                            level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }
    }

    // =====================================================================
    // 2) FARMLAND broken → always revert nearby platforms (even if rule is OFF)
    // =====================================================================
    @SubscribeEvent
    public void onFarmlandBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide) return;

        if (event.getState().getBlock() instanceof FarmBlock) {
            revertPlatformsAround(level, event.getPos());
        }
    }

    // =====================================================================
    // 3) WATER appears as the SECOND block (bucket / flow) → convert immediately (only when rule ON)
    // =====================================================================
    @SubscribeEvent
    public void onWaterPlacedByEntity(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide) return;
        if (!isFeatureEnabled(level)) return; // do not place platforms when disabled

        BlockPos pos = event.getPos();
        BlockState placed = event.getPlacedBlock();

        // if this is water (source/flowing) and not already our platform
        if ((level.getFluidState(pos).is(Fluids.WATER) || placed.getFluidState().is(Fluids.WATER))
                && !level.getBlockState(pos).is(ModBlocks.WATER_SURFACE_PLATFORM.get())) {
            maybeConvertWaterToPlatform(level, pos);
        }
    }

    @SubscribeEvent
    public void onFluidPlaced(BlockEvent.FluidPlaceBlockEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide) return;
        if (!isFeatureEnabled(level)) return; // do not place platforms when disabled

        BlockPos pos = event.getPos();
        BlockState newState = event.getNewState();

        // loop guard: if already platform, skip
        if (level.getBlockState(pos).is(ModBlocks.WATER_SURFACE_PLATFORM.get())) return;

        if (level.getFluidState(pos).is(Fluids.WATER) || newState.getFluidState().is(Fluids.WATER)) {
            maybeConvertWaterToPlatform(level, pos);
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /** Place platforms in 4 directions ONLY where water is present. */
    private static void placePlatformsAroundFarmland(Level level, BlockPos farmlandPos) {
        for (Direction dir : CARDINALS) {
            BlockPos wpos = farmlandPos.relative(dir);
            // if this cell contains water → replace with platform
            if (level.getFluidState(wpos).is(Fluids.WATER)) {
                if (!level.getBlockState(wpos).is(ModBlocks.WATER_SURFACE_PLATFORM.get())) {
                    BlockState platform = ModBlocks.WATER_SURFACE_PLATFORM.get()
                            .defaultBlockState()
                            .setValue(WaterSurfacePlatformBlock.WATERLOGGED, true);
                    level.setBlock(wpos, platform, 2); // 2: send to client, minimal neighbor updates
                }
            } else {
                // if a platform is present and there's no adjacent farmland anymore → restore water
                if (level.getBlockState(wpos).is(ModBlocks.WATER_SURFACE_PLATFORM.get())
                        && !hasAdjacentFarmland(level, wpos)) {
                    level.setBlock(wpos, Blocks.WATER.defaultBlockState(), 2);
                }
            }
        }
    }

    /** When FARMLAND disappears — adjacent platforms revert to water (if no other adjacent FARMLAND). */
    private static void revertPlatformsAround(Level level, BlockPos oldFarmland) {
        for (Direction dir : CARDINALS) {
            BlockPos pos = oldFarmland.relative(dir);
            if (level.getBlockState(pos).is(ModBlocks.WATER_SURFACE_PLATFORM.get()) && !hasAdjacentFarmland(level, pos)) {
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
            }
        }
    }

    /** If the block at `pos` is water and there is adjacent FARMLAND → convert that water to a platform. */
    private static void maybeConvertWaterToPlatform(Level level, BlockPos pos) {
        if (!level.getFluidState(pos).is(Fluids.WATER)) return;
        if (!hasAdjacentFarmland(level, pos)) return;

        BlockState platform = ModBlocks.WATER_SURFACE_PLATFORM.get()
                .defaultBlockState()
                .setValue(WaterSurfacePlatformBlock.WATERLOGGED, true);
        level.setBlock(pos, platform, 2);
    }

    /** Checks for horizontally adjacent FARMLAND (N/S/W/E). */
    private static boolean hasAdjacentFarmland(Level level, BlockPos pos) {
        for (Direction d : CARDINALS) {
            if (level.getBlockState(pos.relative(d)).getBlock() instanceof FarmBlock) return true;
        }
        return false;
    }

    /** /gamerule FarmlandWater */
    private static boolean isFeatureEnabled(Level level) {
        try {
            GameRules rules = level.getGameRules();
            return rules != null && GR_FARMLAND_WATER != null ? rules.getBoolean(GR_FARMLAND_WATER) : true;
        } catch (Throwable ignored) { }
        return true;
    }
}
