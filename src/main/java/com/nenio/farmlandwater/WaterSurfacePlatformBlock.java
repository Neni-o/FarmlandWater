package com.nenio.farmlandwater;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WaterSurfacePlatformBlock extends Block implements SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    // Thin top plate; slightly inset to avoid grabbing on side faces
    private static final VoxelShape TOP_PLATE = Block.box(0.25, 14.0, 0.25, 15.75, 15.0, 15.75);

    // Tuning: how far BELOW the top (in blocks) we still allow "from-top" capture.
    // 1/8 block (0.125) safely covers farmland's 15/16 top (0.0625 below full block) + some margin.
    private static final double CAPTURE_TOLERANCE = 0.125;
    private static final double UPWARD_EPS = 0.001; // treat as "going up" if > this

    public WaterSurfacePlatformBlock() {
        super(BlockBehaviour.Properties.of()
                .noOcclusion()
                .strength(-1.0F, 3_600_000F) // effectively unbreakable (technical)
                .isValidSpawn((s, l, p, e) -> false)
                .noLootTable()
        );
        this.registerDefaultState(this.stateDefinition.any().setValue(WATERLOGGED, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(WATERLOGGED);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE; // invisible block
    }

    // --- KEY: collide only when approaching from above / near-top band ---
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return collidesFromTopOrNearTop(ctx, pos) ? TOP_PLATE : Shapes.empty();
    }

    // Support shape for standing (pathfinding, friction) — when triggered
    @Override
    public VoxelShape getBlockSupportShape(BlockState state, BlockGetter level, BlockPos pos) {
        return TOP_PLATE;
    }

    // No outline/visual shape; water renders normally and raytraces won't grab sides
    @Override public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return Shapes.empty(); }
    @Override public VoxelShape getVisualShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return Shapes.empty(); }

    // Waterlogging: keep water inside this cell
    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState nd, LevelAccessor level, BlockPos pos, BlockPos nPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, dir, nd, level, pos, nPos);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    // ==================== Helper ====================

    /**
     * Allow collision when the entity's feet are at/above a small band below the top
     * (so walking from farmland into the platform doesn't drop you into water),
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
            boolean notGoingUp        = vy <= UPWARD_EPS;                 // disallow from-below "push up"

            return withinCaptureBand && notGoingUp;
        }
        // No entity context — do not collide
        return false;
    }
}
