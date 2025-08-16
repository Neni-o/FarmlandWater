package com.nenio.farmlandwater;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * FarmlandWater: allow walking on WATER blocks that touch at least one FARMLAND (horizontal only),
 * provided there is AIR above the water. Toggle with /gamerule FarmlandWater <true|false>.
 * For smooth feel, when a player is above eligible water mod temporarily disable gravity and
 * pin their Y to the water surface (no bouncing), restoring gravity when they step off.
 */
@Mod(farmlandwater.MOD_ID)
public class farmlandwater {
    public static final String MOD_ID = "farmlandwater";

    // /gamerule FarmlandWater <true|false>
    public static GameRules.Key<GameRules.BooleanValue> GR_FARMLAND_WATER;

    // Four horizontal directions only
    private static final Direction[] CARDINALS = new Direction[]{
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    // Track which players currently have gravity disabled by mod
    private static final Map<UUID, Boolean> NO_GRAVITY_STATE = new HashMap<>();
    // Persistent flag on Player to know if mod disabled gravity (survives relog/world load)
    private static final String NBT_NO_GRAV_KEY = "FarmlandWater_NoGrav";

    public farmlandwater() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::onCommonSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }

    /** Register the gamerule during common setup (vanilla API). */
    private void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> GR_FARMLAND_WATER = GameRules.register(
                "FarmlandWater",
                GameRules.Category.PLAYER,
                GameRules.BooleanValue.create(true)
        ));
    }

    // --------------------------------------------------------------------------------------------
    // Shape hook via reflection on BlockEvent subclasses (collision + support)
    // --------------------------------------------------------------------------------------------

    @SubscribeEvent
    public void onAnyBlockEvent(BlockEvent event) {
        // Only proceed for BlockEvent subclasses that actually expose a shape setter
        boolean hasSetShape = hasMethod(event, "setShape", VoxelShape.class) || hasMethod(event, "setNewShape", VoxelShape.class);
        if (!hasSetShape) return;

        BlockState state = event.getState();
        if (state.getBlock() != Blocks.WATER) return;

        BlockGetter level = event.getLevel();
        if (!isFeatureEnabled(level)) return;

        BlockPos pos = event.getPos();
        if (!canWalkOnThisWater(level, pos)) return;

        VoxelShape solid = Shapes.block();
        if (!invokeShapeSetter(event, "setShape", solid)) {
            invokeShapeSetter(event, "setNewShape", solid);
        }
    }

    private static boolean hasMethod(Object obj, String name, Class<?>... params) {
        try {
            obj.getClass().getMethod(name, params);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private static boolean invokeShapeSetter(Object event, String method, VoxelShape shape) {
        try {
            event.getClass().getMethod(method, VoxelShape.class).invoke(event, shape);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // --------------------------------------------------------------------------------------------
    // Helper to toggle gravity in a way we can detect later (uses persistent player NBT)
    // --------------------------------------------------------------------------------------------

    private static void setModNoGravity(Player player, boolean enable) {
        player.setNoGravity(enable);
        if (enable) {
            NO_GRAVITY_STATE.put(player.getUUID(), true);
        } else {
            NO_GRAVITY_STATE.remove(player.getUUID());
        }
        player.getPersistentData().putBoolean(NBT_NO_GRAV_KEY, enable);
    }

    // --------------------------------------------------------------------------------------------
    // Player-tick fallback (client + server): no-gravity over eligible water, smooth Y lock
    // --------------------------------------------------------------------------------------------

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        Player player = event.player;
        Level level = player.level();

        // Determine candidate water beneath or at feet
        BlockPos feet = new BlockPos(Mth.floor(player.getX()), Mth.floor(player.getY()), Mth.floor(player.getZ()));
        BlockPos below = feet.below();
        boolean feetWater  = level.getBlockState(feet).is(Blocks.WATER);
        boolean belowWater = level.getBlockState(below).is(Blocks.WATER);

        boolean allow = isFeatureEnabled(level);
        boolean walkFeet  = allow && feetWater  && canWalkOnThisWater(level, feet);
        boolean walkBelow = allow && belowWater && canWalkOnThisWater(level, below);

        BlockPos waterPos = walkFeet ? feet : (walkBelow ? below : null);
        boolean shouldFloat = waterPos != null;

        boolean wasNoGrav = NO_GRAVITY_STATE.getOrDefault(player.getUUID(), false);
        boolean tagNoGrav = player.getPersistentData().getBoolean(NBT_NO_GRAV_KEY);

        // Safety: if gamerule is OFF, always restore gravity we might have disabled before
        if (!allow) {
            if (wasNoGrav || tagNoGrav) setModNoGravity(player, false);
            return;
        }

        if (shouldFloat) {
            // Enable no-gravity while above eligible water (idempotent via NBT)
            if (!tagNoGrav) setModNoGravity(player, true);

            double targetY = waterPos.getY() + 1.0; // water surface
            if (Math.abs(player.getY() - targetY) > 1.0E-3) {
                player.setPos(player.getX(), targetY, player.getZ());
            }
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(v.x, 0.0, v.z);
            player.setSwimming(false);
            player.fallDistance = 0.0F;
        } else {
            // Not over eligible water → if our mod had disabled gravity earlier, restore it now
            if (tagNoGrav) setModNoGravity(player, false);
        }
    }

    // --------------------------------------------------------------------------------------------
    // Walkability logic
    // --------------------------------------------------------------------------------------------

    /**
     * Eligible if: this is WATER, there is AIR above, and at least one horizontal neighbor is FARMLAND.
     */
    public static boolean canWalkOnThisWater(BlockGetter level, BlockPos waterPos) {
        BlockState self = level.getBlockState(waterPos);
        if (self.getBlock() != Blocks.WATER) return false;
        if (!level.getBlockState(waterPos.above()).isAir()) return false; // need air above

        for (Direction dir : CARDINALS) {
            BlockPos nPos = waterPos.relative(dir);
            if (isFarmland(level.getBlockState(nPos))) {
                return true;
            }
        }
        return false;
    }

    /** Helper: farmland detection compatible with variants. */
    private static boolean isFarmland(BlockState state) {
        return state.getBlock() instanceof FarmBlock || state.is(Blocks.FARMLAND);
    }

    /** Read the gamerule if available; default to true if not queryable on this side. */
    private static boolean isFeatureEnabled(BlockGetter level) {
        if (level instanceof Level lvl) {
            try {
                GameRules rules = lvl.getGameRules();
                if (rules != null && GR_FARMLAND_WATER != null) {
                    return rules.getBoolean(GR_FARMLAND_WATER);
                }
            } catch (Throwable ignored) { }
        }
        return true; // Fallback: enabled; server authoritative logic still applies
    }
}
