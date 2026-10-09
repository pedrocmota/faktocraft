package com.faktocraft.common.block.impl.pipe;

import com.faktocraft.common.util.InteractionResults;
import net.minecraft.world.item.Item;
import java.util.function.Consumer;
import net.minecraft.world.item.component.TooltipDisplay;
import com.faktocraft.common.block.FaktocraftBlock;
import com.faktocraft.common.util.FluidInteractionHelper;
import com.faktocraft.common.interfaces.block.IHasMenu;
import com.faktocraft.common.interfaces.block.IStateFacing;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.faktocraft.common.util.transfer.ForgeCapabilities;
import com.faktocraft.common.util.transfer.IFluidHandler;
import org.jetbrains.annotations.Nullable;

public class BlockEnderTank extends FaktocraftBlock implements EntityBlock, IHasMenu, IStateFacing {

  private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

  public BlockEnderTank(Properties properties) {
    super(properties);
  }

  @Override
  public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return SHAPE;
  }

  @Override
  public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
      BlockHitResult hitResult) {
    if (level.getBlockEntity(pos) instanceof BlockEntityEnderTank tank && tank.hasCode()) {
      ItemStack stack = player.getItemInHand(hand);
      if (!stack.isEmpty() && FluidInteractionHelper.getFluidHandler(stack) != null) {
        IFluidHandler handler = tank.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElse(null);
        if (handler != null) {
          FluidInteractionHelper.interactWithFluidHandler(player, hand, handler);
        }
        return InteractionResults.sided(level);
      }
    }
    return super.use(state, level, pos, player, hand, hitResult);
  }

  @Override
  public AbstractContainerMenu getMenu(int windowId, Level level, BlockPos pos, Inventory playerInventory,
      Player player) {
    return new MenuEnderTank(windowId, level, pos, playerInventory, player);
  }

  @Nullable
  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new BlockEntityEnderTank(pos, state);
  }

  @Nullable
  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
      BlockEntityType<T> type) {
    if (level.isClientSide()) {
      return null;
    }
    return (tickLevel, pos, tickState, blockEntity) -> {
      if (blockEntity instanceof BlockEntityEnderTank tank) {
        tank.tick();
      }
    };
  }

  @Override
  public boolean propagatesSkylightDown(BlockState state) {
    return true;
  }

  @Override
  public void appendHoverText(ItemStack stack, Item.TooltipContext level, TooltipDisplay display,
      Consumer<Component> tooltip, TooltipFlag flag) {
    tooltip.accept(Component.translatable("tooltip.faktocraft.ender_tank").withStyle(ChatFormatting.GRAY));
    super.appendHoverText(stack, level, display, tooltip, flag);
  }
}
