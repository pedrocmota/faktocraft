package com.faktocraft.common.util;

import com.faktocraft.common.entity.block.FluidStorage;
import com.faktocraft.common.item.base.FluidItem;
import com.faktocraft.common.util.transfer.CapabilityBridge;
import com.faktocraft.common.util.transfer.IFluidHandler;
import com.faktocraft.common.util.transfer.IFluidHandlerItem;
import com.faktocraft.common.util.transfer.IItemHandler;
import com.faktocraft.common.util.transfer.ItemHandlerHelper;
import com.faktocraft.common.util.transfer.PlayerInvWrapper;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class FluidInteractionHelper {
  private FluidInteractionHelper() {
  }

  public static boolean isContainer(ItemStack stack) {
    return !stack.isEmpty() && !(stack.getItem() instanceof FluidItem)
        && getFluidHandler(stack) != null;
  }

  public static boolean canPourContainer(ItemStack stack, FluidStorage tank) {
    return isContainer(stack) && !tank.isOutputOnly()
        && tryEmptyContainer(stack, tank, Integer.MAX_VALUE, null, false).isSuccess();
  }

  public static boolean canFillContainer(ItemStack stack, FluidStorage tank) {
    return isContainer(stack)
        && tryFillContainer(stack, tank, Integer.MAX_VALUE, null, false).isSuccess();
  }

  public static boolean pourCarried(Player player, FluidStorage tank, boolean all) {
    return transferCarried(player, tank, all, true);
  }

  public static boolean fillCarried(Player player, FluidStorage tank, boolean all) {
    return transferCarried(player, tank, all, false);
  }

  private static boolean transferCarried(Player player, FluidStorage tank, boolean all, boolean pour) {
    ItemStack carried = player.containerMenu.getCarried();
    if (pour ? !canPourContainer(carried, tank) : !canFillContainer(carried, tank)) {
      return false;
    }
    int limit = all ? carried.getCount() : 1;
    List<ItemStack> results = new ArrayList<>();
    while (results.size() < limit) {
      Player sound = results.isEmpty() ? player : null;
      FluidActionResult result = pour
          ? tryEmptyContainer(carried, tank, Integer.MAX_VALUE, sound, true)
          : tryFillContainer(carried, tank, Integer.MAX_VALUE, sound, true);
      if (!result.isSuccess()) {
        break;
      }
      results.add(result.getResult());
    }
    if (results.isEmpty()) {
      return false;
    }
    ItemStack first = results.get(0);
    if (carried.getCount() == results.size() && first.getMaxStackSize() >= results.size()) {
      player.containerMenu.setCarried(first.copyWithCount(results.size()));
      return true;
    }
    carried.shrink(results.size());
    if (carried.isEmpty()) {
      player.containerMenu.setCarried(ItemStack.EMPTY);
    }
    for (ItemStack result : results) {
      player.getInventory().placeItemBackInInventory(result, net.minecraft.util.Prediction.SERVER_ONLY);
    }
    return true;
  }

  public static boolean tryFillTankFromHand(Player player, InteractionHand hand, FluidStorage tank, Fluid fluid) {
    ItemStack stack = player.getItemInHand(hand);
    if (stack.isEmpty()) {
      return false;
    }

    if (stack.getItem() instanceof FluidItem) {
      if (FluidItem.getFluid(stack) != fluid) {
        return false;
      }
      int amount = FluidItem.getFluidAmount(stack);
      if (amount <= 0 || tank.fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.SIMULATE) != amount) {
        return false;
      }

      tank.fill(new FluidStack(fluid, amount), IFluidHandler.FluidAction.EXECUTE);
      if (stack.getCount() == 1) {
        FluidItem.setFluid(stack, Fluids.EMPTY, 0);
      } else {
        ItemStack emptyCell = stack.copyWithCount(1);
        FluidItem.setFluid(emptyCell, Fluids.EMPTY, 0);
        stack.shrink(1);
        if (!player.addItem(emptyCell)) {
          player.drop(emptyCell, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
      }
      return true;
    }

    FluidStack contained = getFluidContained(stack).orElse(FluidStack.EMPTY);
    if (contained.isEmpty() || contained.getFluid() != fluid) {
      return false;
    }

    FluidActionResult result = tryEmptyContainerAndStow(stack, tank,
        PlayerInvWrapper.container(player.getInventory()), Integer.MAX_VALUE, player, true);
    if (result.isSuccess()) {
      player.setItemInHand(hand, result.getResult());
      return true;
    }
    return false;
  }

  public static final class FluidActionResult {
    public static final FluidActionResult FAILURE = new FluidActionResult(false, ItemStack.EMPTY);

    public final boolean success;
    @NotNull
    public final ItemStack result;

    public FluidActionResult(@NotNull ItemStack result) {
      this(true, result);
    }

    private FluidActionResult(boolean success, @NotNull ItemStack result) {
      this.success = success;
      this.result = result;
    }

    public boolean isSuccess() {
      return success;
    }

    @NotNull
    public ItemStack getResult() {
      return result;
    }
  }

  @Nullable
  public static IFluidHandlerItem getFluidHandler(ItemStack stack) {
    return CapabilityBridge.fluidHandlerItem(stack);
  }

  public static Optional<FluidStack> getFluidContained(ItemStack container) {
    if (!container.isEmpty()) {
      IFluidHandlerItem handler = getFluidHandler(ItemHandlerHelper.copyStackWithSize(container, 1));
      if (handler != null) {
        FluidStack contained = handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE);
        if (!contained.isEmpty()) {
          return Optional.of(contained);
        }
      }
    }
    return Optional.empty();
  }

  public static FluidActionResult tryFillContainer(ItemStack container, IFluidHandler fluidSource, int maxAmount,
      @Nullable Player player, boolean doFill) {
    ItemStack containerCopy = ItemHandlerHelper.copyStackWithSize(container, 1);
    IFluidHandlerItem containerFluidHandler = getFluidHandler(containerCopy);
    if (containerFluidHandler == null) {
      return FluidActionResult.FAILURE;
    }
    FluidStack simulatedTransfer = tryFluidTransfer(containerFluidHandler, fluidSource, maxAmount, false);
    if (simulatedTransfer.isEmpty()) {
      return FluidActionResult.FAILURE;
    }
    if (doFill) {
      tryFluidTransfer(containerFluidHandler, fluidSource, maxAmount, true);
      if (player != null) {
        playSound(player, simulatedTransfer, true);
      }
    } else {
      containerFluidHandler.fill(simulatedTransfer, IFluidHandler.FluidAction.EXECUTE);
    }
    return new FluidActionResult(containerFluidHandler.getContainer());
  }

  public static FluidActionResult tryEmptyContainer(ItemStack container, IFluidHandler fluidDestination,
      int maxAmount, @Nullable Player player, boolean doDrain) {
    ItemStack containerCopy = ItemHandlerHelper.copyStackWithSize(container, 1);
    IFluidHandlerItem containerFluidHandler = getFluidHandler(containerCopy);
    if (containerFluidHandler == null) {
      return FluidActionResult.FAILURE;
    }
    FluidStack transfer = tryFluidTransfer(fluidDestination, containerFluidHandler, maxAmount, doDrain);
    if (transfer.isEmpty()) {
      return FluidActionResult.FAILURE;
    }
    if (!doDrain) {
      containerFluidHandler.drain(transfer, IFluidHandler.FluidAction.EXECUTE);
    }
    if (doDrain && player != null) {
      playSound(player, transfer, false);
    }
    return new FluidActionResult(containerFluidHandler.getContainer());
  }

  public static FluidActionResult tryFillContainerAndStow(ItemStack container, IFluidHandler fluidSource,
      IItemHandler inventory, int maxAmount, @Nullable Player player, boolean doFill) {
    if (container.isEmpty()) {
      return FluidActionResult.FAILURE;
    }
    if (player != null && player.getAbilities().instabuild) {
      FluidActionResult filledReal = tryFillContainer(container, fluidSource, maxAmount, player, doFill);
      if (filledReal.isSuccess()) {
        return new FluidActionResult(container);
      }
    } else if (container.getCount() == 1) {
      FluidActionResult filledReal = tryFillContainer(container, fluidSource, maxAmount, player, doFill);
      if (filledReal.isSuccess()) {
        return filledReal;
      }
    } else {
      FluidActionResult filledSimulated = tryFillContainer(container, fluidSource, maxAmount, player, false);
      if (filledSimulated.isSuccess()) {
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(inventory, filledSimulated.getResult(), true);
        if (remainder.isEmpty() || player != null) {
          FluidActionResult filledReal = tryFillContainer(container, fluidSource, maxAmount, player, doFill);
          remainder = ItemHandlerHelper.insertItemStacked(inventory, filledReal.getResult(), !doFill);
          if (!remainder.isEmpty() && player != null && doFill) {
            ItemHandlerHelper.giveItemToPlayer(player, remainder);
          }
          ItemStack containerCopy = container.copy();
          containerCopy.shrink(1);
          return new FluidActionResult(containerCopy);
        }
      }
    }
    return FluidActionResult.FAILURE;
  }

  public static FluidActionResult tryEmptyContainerAndStow(ItemStack container, IFluidHandler fluidDestination,
      IItemHandler inventory, int maxAmount, @Nullable Player player, boolean doDrain) {
    if (container.isEmpty()) {
      return FluidActionResult.FAILURE;
    }
    if (player != null && player.getAbilities().instabuild) {
      FluidActionResult emptiedReal = tryEmptyContainer(container, fluidDestination, maxAmount, player, doDrain);
      if (emptiedReal.isSuccess()) {
        return new FluidActionResult(container);
      }
    } else if (container.getCount() == 1) {
      FluidActionResult emptiedReal = tryEmptyContainer(container, fluidDestination, maxAmount, player, doDrain);
      if (emptiedReal.isSuccess()) {
        return emptiedReal;
      }
    } else {
      FluidActionResult emptiedSimulated = tryEmptyContainer(container, fluidDestination, maxAmount, player, false);
      if (emptiedSimulated.isSuccess()) {
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(inventory, emptiedSimulated.getResult(), true);
        if (remainder.isEmpty() || player != null) {
          FluidActionResult emptiedReal = tryEmptyContainer(container, fluidDestination, maxAmount, player,
              doDrain);
          remainder = ItemHandlerHelper.insertItemStacked(inventory, emptiedReal.getResult(), !doDrain);
          if (!remainder.isEmpty() && player != null && doDrain) {
            ItemHandlerHelper.giveItemToPlayer(player, remainder);
          }
          ItemStack containerCopy = container.copy();
          containerCopy.shrink(1);
          return new FluidActionResult(containerCopy);
        }
      }
    }
    return FluidActionResult.FAILURE;
  }

  public static boolean interactWithFluidHandler(Player player, InteractionHand hand, IFluidHandler handler) {
    ItemStack heldItem = player.getItemInHand(hand);
    if (heldItem.isEmpty()) {
      return false;
    }
    IItemHandler playerInventory = PlayerInvWrapper.of(player.getInventory());
    FluidActionResult fluidActionResult = tryFillContainerAndStow(heldItem, handler, playerInventory,
        Integer.MAX_VALUE, player, true);
    if (!fluidActionResult.isSuccess()) {
      fluidActionResult = tryEmptyContainerAndStow(heldItem, handler, playerInventory, Integer.MAX_VALUE, player,
          true);
    }
    if (fluidActionResult.isSuccess()) {
      player.setItemInHand(hand, fluidActionResult.getResult());
      return true;
    }
    return false;
  }

  public static FluidStack tryFluidTransfer(IFluidHandler fluidDestination, IFluidHandler fluidSource,
      int maxAmount, boolean doTransfer) {
    FluidStack drainable = fluidSource.drain(maxAmount, IFluidHandler.FluidAction.SIMULATE);
    if (!drainable.isEmpty()) {
      return tryFluidTransferInternal(fluidDestination, fluidSource, drainable, doTransfer);
    }
    return FluidStack.EMPTY;
  }

  private static FluidStack tryFluidTransferInternal(IFluidHandler fluidDestination, IFluidHandler fluidSource,
      FluidStack drainable, boolean doTransfer) {
    int fillableAmount = fluidDestination.fill(drainable, IFluidHandler.FluidAction.SIMULATE);
    if (fillableAmount > 0) {
      drainable.setAmount(fillableAmount);
      if (doTransfer) {
        FluidStack drained = fluidSource.drain(drainable, IFluidHandler.FluidAction.EXECUTE);
        if (!drained.isEmpty()) {
          drained.setAmount(fluidDestination.fill(drained, IFluidHandler.FluidAction.EXECUTE));
          return drained;
        }
      } else {
        return drainable;
      }
    }
    return FluidStack.EMPTY;
  }

  private static void playSound(Player player, FluidStack fluid, boolean fill) {
    SoundEvent sound = fluid.getFluid().getFluidType()
        .getSound(fill ? SoundActions.BUCKET_FILL : SoundActions.BUCKET_EMPTY);
    if (sound != null) {
      player.level().playSound(null, player.getX(), player.getY() + 0.5, player.getZ(), sound, SoundSource.BLOCKS,
          1.0F, 1.0F);
    }
  }
}
