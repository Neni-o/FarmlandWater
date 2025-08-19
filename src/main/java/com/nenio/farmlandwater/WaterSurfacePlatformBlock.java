package com.nenio.farmlandwater;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;

/**
 * Invisible, waterlogged-only “platform” that lets players walk across irrigating water.
 * - Collides only near the top and only while WATERLOGGED = true.
 * - Picking up the water with a bucket removes the platform (prevents ghost hitboxes).
 * - If waterlogging is lost for any reason, the block self-removes.
 */
public class WaterSurfacePlatformBlock extends Block implements SimpleWaterloggedBlock, BucketPickup {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    // Thin top plate; slightly inset so you don't snag on side faces.
    private static final VoxelShape TOP_PLATE = Block.box(0.25, 14.0, 0.25, 15.75, 15.0, 15.75);

    // Collision tuning: allow “from-top” capture for feet within this band from the top.
    private static final double CAPTURE_TOLERANCE = 0.125;
    private static final double UPWARD_EPS = 0.001; // treat as “going up” if > this

    // Properties come from registry (with setId applied there)
    public WaterSurfacePlatformBlock(BlockBehaviour.Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(WATERLOGGED, true));
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(WATERLOGGED);
    }

    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE; // invisible; the water renders instead
    }

    // --- Collision only from above / near-top band AND only if there is water ---
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        if (!state.getValue(WATERLOGGED)) return Shapes.empty();
        return collidesFromTopOrNearTop(ctx, pos) ? TOP_PLATE : Shapes.empty();
    }

    // Support shape for standing/walking — also only while waterlogged
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(WATERLOGGED) ? TOP_PLATE : Shapes.empty();
    }

    // No outline/visual shape; water raytracing is preferred
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return Shapes.empty(); }
    public VoxelShape getVisualShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return Shapes.empty(); }

    // Keep actual water in this cell while waterlogged
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    // If waterlogging is lost, remove the block to avoid “ghost” collision
    public BlockState updateShape(BlockState state, Direction dir, BlockState nd, LevelAccessor level, BlockPos pos, BlockPos nPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
            return state;
        }
        return Blocks.AIR.defaultBlockState();
    }

    // Mapping quirk in 21.2.1-beta — leave without @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    // ========= BucketPickup =========
    // Leave without @Override to be mapping-agnostic in 21.2.1-beta.

    /** NeoForge/Mojmap 1.21.x commonly exposes this signature (no Player parameter). */
    public ItemStack pickupBlock(LevelAccessor level, BlockPos pos, BlockState state) {
        if (state.getValue(WATERLOGGED)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            return new ItemStack(Items.WATER_BUCKET);
        }
        return ItemStack.EMPTY;
    }

    /** Mojmap 1.21.x: no-arg sound getter */
    public Optional<SoundEvent> getPickupSound() {
        return Fluids.WATER.getPickupSound();
    }

    // ==================== Helpers ====================

    /**
     * Allow collision when the entity’s feet are within a small band below the top
     * (so walking from farmland into the platform doesn’t drop you into water),
     * and only if the entity is NOT moving upward (to keep it non-colliding from below).
     */
    private static boolean collidesFromTopOrNearTop(CollisionContext ctx, BlockPos pos) {
        if (ctx instanceof EntityCollisionContext ecc) {
            Entity e = ecc.getEntity();
            if (e == null) return false;

            final double topY = pos.getY() + 1.0;
            final double minY = e.getBoundingBox().minY;
            final double vy   = e.getDeltaMovement().y;

            boolean withinCaptureBand = minY >= topY - CAPTURE_TOLERANCE; // allow slight below-top capture
            boolean notGoingUp        = vy <= UPWARD_EPS;                 // disallow from-below “push up”

            return withinCaptureBand && notGoingUp;
        }
        // No entity context — do not collide
        return false;
    }

    /** Allow replacing the platform with another block placed by hand (no sneak = replace). */
    public boolean canBeReplaced(BlockState state, BlockPlaceContext ctx) {
        if (ctx.isSecondaryUseActive()) return false; // sneaking keeps the block
        ItemStack stack = ctx.getItemInHand();
        return stack.getItem() instanceof BlockItem;
    }
}
