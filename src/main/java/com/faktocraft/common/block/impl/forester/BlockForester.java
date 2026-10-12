package com.faktocraft.common.block.impl.forester;

import com.faktocraft.common.util.PlayerMessages;
import net.minecraft.world.item.Item;
import java.util.function.Consumer;
import net.minecraft.world.item.component.TooltipDisplay;
import com.faktocraft.common.block.FaktocraftEntityBlock;
import com.faktocraft.common.block.impl.quarry.BlockEntityGantry;
import com.faktocraft.common.block.impl.quarry.BlockLandmark;
import com.faktocraft.common.block.impl.quarry.IGantryHost;
import com.faktocraft.common.interfaces.block.IHasMenu;
import com.faktocraft.common.interfaces.block.IStateActive;
import com.faktocraft.common.interfaces.block.IStateFacing;
import com.faktocraft.common.util.wrench.WrenchHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class BlockForester extends FaktocraftEntityBlock
    implements IStateFacing, IStateActive, IHasMenu, IGantryHost {

  public BlockForester(Properties properties) {
    super(properties);
    WrenchHelper.registerAction(this).add(WrenchHelper.rotationAction());
  }

  @Nullable
  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new BlockEntityForester(pos, state);
  }

  @Nullable
  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    BlockState state = super.getStateForPlacement(context);
    if (state == null) {
      return null;
    }
    Level level = context.getLevel();
    BlockPos pos = context.getClickedPos();
    int[] rect = BlockLandmark.rectAround(level, pos);
    Component problem = null;
    if (rect == null) {
      problem = Component.translatable("chat.faktocraft.quarry_needs_landmarks");
    } else if (!BlockLandmark.rectFits(rect, pos)) {
      problem = Component.translatable("chat.faktocraft.forester_area_invalid");
    } else {
      BlockPos blocker =
          BlockEntityGantry.findRingObstruction(level, pos, rect, BlockEntityForester.DEFAULT_FRAME_HEIGHT);
      if (blocker != null) {
        problem = Component.translatable("chat.faktocraft.frame_obstructed",
            level.getBlockState(blocker).getBlock().getName(), blocker.getX(), blocker.getY(), blocker.getZ());
      }
    }
    if (problem == null) {
      return state;
    }
    Player player = context.getPlayer();
    if (player != null && !level.isClientSide()) {
      PlayerMessages.display(player, problem.copy().withStyle(ChatFormatting.RED), true);
    }
    return null;
  }

  @Override
  public AbstractContainerMenu getMenu(int windowId, Level level, BlockPos pos, Inventory playerInventory,
      Player player) {
    return new MenuForester(windowId, level, pos, playerInventory, player);
  }

  @Override
  public void appendHoverText(ItemStack stack, Item.TooltipContext level, TooltipDisplay display,
      Consumer<Component> tooltip, TooltipFlag flag) {
    tooltip.accept(Component.translatable("tooltip.faktocraft.forester").withStyle(ChatFormatting.GRAY));
    tooltip.accept(Component.translatable("tooltip.faktocraft.forester_area").withStyle(ChatFormatting.DARK_GRAY));
    super.appendHoverText(stack, level, display, tooltip, flag);
  }
}
