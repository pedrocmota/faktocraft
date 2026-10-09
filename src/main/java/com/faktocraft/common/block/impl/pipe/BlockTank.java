package com.faktocraft.common.block.impl.pipe;

import com.faktocraft.common.util.InteractionResults;
import com.faktocraft.common.util.FluidInteractionHelper;
import com.faktocraft.common.util.FluidStackCompat;
import com.faktocraft.common.util.PlayerMessages;
import com.faktocraft.common.block.FaktocraftBlock;
import com.faktocraft.common.util.TextComponentUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidStack;
import com.faktocraft.common.util.transfer.IFluidHandler;
import com.faktocraft.common.util.transfer.IFluidHandlerItem;
import com.faktocraft.common.util.transfer.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

public class BlockTank extends FaktocraftBlock implements EntityBlock {
  @Override
  public void preRemoveSideEffects(BlockState state, Level level, BlockPos pos, BlockEntity blockEntity) {
    BlockState newState = level.getBlockState(pos);
    if (com.faktocraft.common.config.BasicConfig.tankBreakPlacesFluid()
        && !level.isClientSide()
        && blockEntity instanceof BlockEntityTank tankBe
        && !tankBe.tank.isEmpty() && tankBe.tank.getFluidAmount() >= 1000) {
      var fluid = tankBe.tank.getFluid();
      var fluidBlock = fluid.defaultFluidState().createLegacyBlock();
      if (!fluidBlock.isAir() && newState.isAir()) {
        var server = level.getServer();
        if (server != null) {
          server.execute(() -> {
            if (level.getBlockState(pos).isAir()) {
              level.setBlock(pos, fluidBlock, 3);
              level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8F, 0.9F);
            }
          });
        }
      }
    }
    super.preRemoveSideEffects(state, level, pos, blockEntity);
  }

  public static final BooleanProperty JOINED_BELOW = BooleanProperty.create("joined_below");
  public static final BooleanProperty JOINED_ABOVE = BooleanProperty.create("joined_above");

  public BlockTank(Properties properties) {
    super(properties);
    registerDefaultState(defaultBlockState().setValue(JOINED_BELOW, false).setValue(JOINED_ABOVE, false));
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    super.createBlockStateDefinition(builder);
    builder.add(JOINED_BELOW, JOINED_ABOVE);
  }

  @Override
  public BlockState setStateForPlacement(BlockPlaceContext context, BlockState state) {
    state = super.setStateForPlacement(context, state);
    return state
        .setValue(JOINED_BELOW,
            context.getLevel().getBlockState(context.getClickedPos().below()).getBlock() instanceof BlockTank)
        .setValue(JOINED_ABOVE,
            context.getLevel().getBlockState(context.getClickedPos().above()).getBlock() instanceof BlockTank);
  }

  @Override
  public BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
      Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
    if (direction == Direction.DOWN || direction == Direction.UP) {
      boolean joined = neighborState.getBlock() instanceof BlockTank;
      if (joined && level.getBlockEntity(pos) instanceof BlockEntityTank self
          && level.getBlockEntity(neighborPos) instanceof BlockEntityTank other
          && !self.tank.isEmpty() && !other.tank.isEmpty()
          && !FluidStackCompat.isFluidEqual(self.tank.getFluidStack(), other.tank.getFluidStack())) {
        joined = false;
      }
      return state.setValue(direction == Direction.DOWN ? JOINED_BELOW : JOINED_ABOVE, joined);
    }
    return state;
  }

  @Override
  public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
      InteractionHand hand, BlockHitResult hitResult) {
    if (level.getBlockEntity(pos) instanceof BlockEntityTank tankEntity) {
      ItemStack stack = player.getItemInHand(hand);
      if (!stack.isEmpty() && !(stack.getItem() instanceof BucketItem)) {
        IFluidHandlerItem itemHandler = FluidInteractionHelper.getFluidHandler(
            ItemHandlerHelper.copyStackWithSize(stack, 1));
        if (itemHandler != null) {
          if (!level.isClientSide()) {
            transferWithItem(level, pos, player, hand, stack, itemHandler, tankEntity.columnStorage);
          }
          return InteractionResults.sided(level);
        }
      }
      if (FluidInteractionHelper.interactWithFluidHandler(player, hand, tankEntity.columnStorage)) {
        return InteractionResult.SUCCESS;
      }
      if (stack.isEmpty()) {
        if (!level.isClientSide()) {
          FluidStack content = tankEntity.columnStorage.variant();
          Component message = content.isEmpty()
              ? Component.translatable("gui.faktocraft.fluid_empty")
              : Component.translatable("gui.faktocraft.fluid",
                  content.getHoverName(),
                  TextComponentUtil.getFormattedLong(tankEntity.columnStorage.totalMb()),
                  TextComponentUtil.getFormattedLong(tankEntity.columnStorage.capacityMb()));
          PlayerMessages.display(player, message, true);
        }
        return InteractionResult.SUCCESS;
      }
    }
    return super.use(state, level, pos, player, hand, hitResult);
  }

  private static void transferWithItem(Level level, BlockPos pos, Player player, InteractionHand hand,
      ItemStack stack, IFluidHandlerItem itemHandler, TankColumnStorage column) {
    FluidStack inItem = itemHandler.getFluidInTank(0);
    if (inItem.isEmpty()) {
      FluidStack inTank = column.getFluidInTank(0);
      if (inTank.isEmpty()) {
        return;
      }
      if (player.isCreative()) {
        itemHandler.fill(new FluidStack(inTank.getFluid(), itemHandler.getTankCapacity(0)),
            IFluidHandler.FluidAction.EXECUTE);
        ItemHandlerHelper.giveItemToPlayer(player, itemHandler.getContainer());
      } else {
        int accepted = itemHandler.fill(inTank.copy(), IFluidHandler.FluidAction.SIMULATE);
        if (accepted <= 0) {
          return;
        }
        FluidStack drained = column.drain(new FluidStack(inTank.getFluid(), accepted),
            IFluidHandler.FluidAction.EXECUTE);
        itemHandler.fill(drained, IFluidHandler.FluidAction.EXECUTE);
        ItemStack filledStack = itemHandler.getContainer();
        if (stack.getCount() == 1) {
          player.setItemInHand(hand, filledStack);
        } else {
          stack.shrink(1);
          ItemHandlerHelper.giveItemToPlayer(player, filledStack);
        }
      }
      playTransferSound(level, pos, inTank, true);
    } else {
      int accepted = column.fill(inItem.copy(), IFluidHandler.FluidAction.SIMULATE);
      if (accepted <= 0) {
        return;
      }
      FluidStack poured = itemHandler.drain(new FluidStack(inItem.getFluid(), accepted),
          IFluidHandler.FluidAction.EXECUTE);
      column.fill(poured, IFluidHandler.FluidAction.EXECUTE);
      if (!player.isCreative()) {
        ItemStack emptied = itemHandler.getContainer();
        if (stack.getCount() == 1) {
          player.setItemInHand(hand, emptied);
        } else {
          stack.shrink(1);
          ItemHandlerHelper.giveItemToPlayer(player, emptied);
        }
      }
      playTransferSound(level, pos, inItem, false);
    }
  }

  private static void playTransferSound(Level level, BlockPos pos, FluidStack fluid, boolean filling) {
    var sound = fluid.getFluid().getFluidType()
        .getSound(filling ? SoundActions.BUCKET_FILL : SoundActions.BUCKET_EMPTY);
    level.playSound(null, pos, sound != null ? sound
        : (filling ? SoundEvents.BUCKET_FILL : SoundEvents.BUCKET_EMPTY), SoundSource.BLOCKS, 1.0F, 1.0F);
  }

  @Nullable
  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new BlockEntityTank(pos, state);
  }

  @Nullable
  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
      BlockEntityType<T> type) {
    if (level.isClientSide()) {
      return null;
    }
    return (tickLevel, pos, tickState, blockEntity) -> {
      if (blockEntity instanceof BlockEntityTank tankEntity) {
        tankEntity.tick();
      }
    };
  }

  @Override
  public boolean propagatesSkylightDown(BlockState state) {
    return true;
  }
}
