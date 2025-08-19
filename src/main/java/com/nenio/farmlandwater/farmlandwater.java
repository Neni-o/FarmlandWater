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

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(farmlandwater.MOD_ID)
public class farmlandwater {
    public static final String MOD_ID = "farmlandwater";

    public static GameRules.Key<GameRules.BooleanValue> GR_FARMLAND_WATER;

    private static final Direction[] CARDINALS = new Direction[]{
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private static final int SCAN_RADIUS = 8;
    private static final int SCAN_Y_RANGE = 6;
    private static final int SCAN_EVERY_TICKS = 10;

    public farmlandwater() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        modBus.addListener(this::onCommonSetup);

        // rejestracja na głównym busie Forge (zamiast NeoForge.EVENT_BUS)
        MinecraftForge.EVENT_BUS.register(this);
    }

    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> GR_FARMLAND_WATER = GameRules.register(
                "FarmlandWater", GameRules.Category.PLAYER, GameRules.BooleanValue.create(true)
        ));
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // W Forge używamy faz zamiast klas Pre/Post — END ≈ Post
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
                        if (st.is(ModBlocks.WATER_SURFACE_PLATFORM.get())) {
                            level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onFarmlandBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide) return;

        if (event.getState().getBlock() instanceof FarmBlock) {
            revertPlatformsAround(level, event.getPos());
        }
    }

    @SubscribeEvent
    public void onWaterPlacedByEntity(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide) return;
        if (!isFeatureEnabled(level)) return;

        BlockPos pos = event.getPos();
        BlockState placed = event.getPlacedBlock();

        if ((level.getFluidState(pos).is(Fluids.WATER) || placed.getFluidState().is(Fluids.WATER))
                && !level.getBlockState(pos).is(ModBlocks.WATER_SURFACE_PLATFORM.get())) {
            maybeConvertWaterToPlatform(level, pos);
        }
    }

    @SubscribeEvent
    public void onFluidPlaced(BlockEvent.FluidPlaceBlockEvent event) {
        if (!(event.getLevel() instanceof Level level)) return;
        if (level.isClientSide) return;
        if (!isFeatureEnabled(level)) return;

        BlockPos pos = event.getPos();
        BlockState newState = event.getNewState();

        if (level.getBlockState(pos).is(ModBlocks.WATER_SURFACE_PLATFORM.get())) return;

        if (level.getFluidState(pos).is(Fluids.WATER) || newState.getFluidState().is(Fluids.WATER)) {
            maybeConvertWaterToPlatform(level, pos);
        }
    }

    private static void placePlatformsAroundFarmland(Level level, BlockPos farmlandPos) {
        for (Direction dir : CARDINALS) {
            BlockPos wpos = farmlandPos.relative(dir);
            if (level.getFluidState(wpos).is(Fluids.WATER)) {
                if (!level.getBlockState(wpos).is(ModBlocks.WATER_SURFACE_PLATFORM.get())) {
                    BlockState platform = ModBlocks.WATER_SURFACE_PLATFORM.get()
                            .defaultBlockState()
                            .setValue(WaterSurfacePlatformBlock.WATERLOGGED, true);
                    level.setBlock(wpos, platform, 2);
                }
            } else {
                if (level.getBlockState(wpos).is(ModBlocks.WATER_SURFACE_PLATFORM.get())) {
                    level.setBlock(wpos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static void revertPlatformsAround(Level level, BlockPos oldFarmland) {
        for (Direction dir : CARDINALS) {
            BlockPos pos = oldFarmland.relative(dir);
            if (level.getBlockState(pos).is(ModBlocks.WATER_SURFACE_PLATFORM.get()) && !hasAdjacentFarmland(level, pos)) {
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), 2);
            }
        }
    }

    private static void maybeConvertWaterToPlatform(Level level, BlockPos pos) {
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
        try {
            if (level instanceof ServerLevel serverLevel) {
                GameRules rules = serverLevel.getGameRules();
                return GR_FARMLAND_WATER != null ? rules.getBoolean(GR_FARMLAND_WATER) : true;
            }
        } catch (Throwable ignored) { }
        return true;
    }
}
