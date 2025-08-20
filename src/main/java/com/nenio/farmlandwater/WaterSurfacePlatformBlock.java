package com.nenio.farmlandwater;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Waterloggable;
import net.minecraft.block.EntityShapeContext;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvent;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.block.FluidDrainable;
import net.minecraft.block.BlockState;

import java.util.Optional;

/**
 * Invisible, waterlogged-only “platform” that lets players walk across irrigating water.
 * - Collides only near the top and only while WATERLOGGED = true.
 * - Picking up the water with a bucket removes the platform (prevents ghost hitboxes).
 * - If waterlogging is lost for any reason, the block self-removes.
 */
public class WaterSurfacePlatformBlock extends Block implements Waterloggable, FluidDrainable {
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;

    // Thin top plate; slightly inset so you don't snag on side faces.
    private static final VoxelShape TOP_PLATE = Block.createCuboidShape(0.25, 14.0, 0.25, 15.75, 15.0, 15.75);

    // Collision tuning: allow “from-top” capture for feet within this band from the top.
    private static final double CAPTURE_TOLERANCE = 0.125;
    private static final double UPWARD_EPS = 0.001; // treat as “going up” if > this

    public WaterSurfacePlatformBlock(Settings props) {
        super(props);
        this.setDefaultState(this.getDefaultState().with(WATERLOGGED, true));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> b) {
        b.add(WATERLOGGED);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE; // invisible; the water renders instead
    }

    // --- Collision only from above / near-top band AND only if there is water ---
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView level, BlockPos pos, ShapeContext ctx) {
        if (!state.get(WATERLOGGED)) return VoxelShapes.empty();
        return collidesFromTopOrNearTop(ctx, pos) ? TOP_PLATE : VoxelShapes.empty();
    }

    // Support shape for standing/walking — also only while waterlogged
    // (Yarn: collision wystarczy; outline/culling zostają puste)
    @Override
    public VoxelShape getOutlineShape(BlockState s, BlockView l, BlockPos p, ShapeContext c) { return VoxelShapes.empty(); }

    @Override
    public VoxelShape getCullingShape(BlockState s, BlockView l, BlockPos p) { return VoxelShapes.empty(); }

    // Keep actual water in this cell while waterlogged
    @Override
    public FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    // ========= FluidDrainable =========

    /** Mojmap/Forge 1.21.3: no-arg sound getter. */
    @Override
    public Optional<SoundEvent> getBucketFillSound() {
        return Fluids.WATER.getBucketFillSound();
    }

    // ==================== Helpers ====================

    /**
     * Allow collision when the entity’s feet are within a small band below the top
     * (so walking from farmland into the platform doesn’t drop you into water),
     * and only if the entity is NOT moving upward (to keep it non-colliding from below).
     */
    private static boolean collidesFromTopOrNearTop(ShapeContext ctx, BlockPos pos) {
        if (ctx instanceof EntityShapeContext ecc) {
            var e = ecc.getEntity();
            if (e == null) return false;

            final double topY = pos.getY() + 1.0;
            final double minY = e.getBoundingBox().minY;
            final double vy   = e.getVelocity().y;

            boolean withinCaptureBand = minY >= topY - CAPTURE_TOLERANCE; // allow slight below-top capture
            boolean notGoingUp        = vy <= UPWARD_EPS;                 // disallow from-below “push up”

            return withinCaptureBand && notGoingUp;
        }
        // No entity context — do not collide
        return false;
    }

    /** Allow replacing the platform with another block placed by hand (no sneak = replace). */
    @Override
    public boolean canReplace(BlockState state, ItemPlacementContext ctx) {
        if (ctx.shouldCancelInteraction()) return false; // sneaking keeps the block
        ItemStack stack = ctx.getStack();
        return stack.getItem() instanceof BlockItem;
    }
}
