package com.faktocraft.common.util.transfer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class CapabilityBridge {
  private static final List<BlockEntityType<?>> TYPES = new ArrayList<>();

  private CapabilityBridge() {
  }

  public static void track(BlockEntityType<?> type) {
    TYPES.add(type);
  }

  public static void register(RegisterCapabilitiesEvent event) {
    for (BlockEntityType<?> type : TYPES) {
      registerType(event, type);
    }
  }

  private static <BE extends BlockEntity> void registerType(RegisterCapabilitiesEvent event,
      BlockEntityType<BE> type) {
    event.registerBlockEntity(Capabilities.Item.BLOCK, type, (be, side) -> {
      IItemHandler handler = local(be, ForgeCapabilities.ITEM_HANDLER, side);
      return handler == null ? null : toResource(handler);
    });
    event.registerBlockEntity(Capabilities.Fluid.BLOCK, type, (be, side) -> {
      IFluidHandler handler = local(be, ForgeCapabilities.FLUID_HANDLER, side);
      return handler == null ? null : toResource(handler);
    });
  }

  @Nullable
  private static <T> T local(BlockEntity be, Capability<T> cap, @Nullable Direction side) {
    if (be instanceof ICapabilityProvider provider) {
      return provider.getCapability(cap, side).orElse(null);
    }
    return null;
  }

  public static ResourceHandler<ItemResource> toResource(IItemHandler handler) {
    return handler instanceof ResourceItemHandler wrapped ? wrapped.handler() : new ItemHandlerResource(handler);
  }

  public static ResourceHandler<FluidResource> toResource(IFluidHandler handler) {
    return handler instanceof ResourceFluidHandler wrapped ? wrapped.handler() : new FluidHandlerResource(handler);
  }

  @Nullable
  public static IItemHandler itemHandler(Level level, BlockPos pos, @Nullable Direction side) {
    BlockEntity be = level.getBlockEntity(pos);
    if (be == null || be instanceof JukeboxBlockEntity) {
      return null;
    }
    IItemHandler local = local(be, ForgeCapabilities.ITEM_HANDLER, side);
    if (local != null) {
      return local;
    }
    ResourceHandler<ItemResource> foreign = level.getCapability(Capabilities.Item.BLOCK, pos,
        level.getBlockState(pos), be, side);
    if (foreign == null) {
      return null;
    }
    return foreign instanceof ItemHandlerResource wrapped ? wrapped.handler() : new ResourceItemHandler(foreign);
  }

  @Nullable
  public static IFluidHandler fluidHandler(Level level, BlockPos pos, @Nullable Direction side) {
    BlockEntity be = level.getBlockEntity(pos);
    if (be == null) {
      return null;
    }
    IFluidHandler local = local(be, ForgeCapabilities.FLUID_HANDLER, side);
    if (local != null) {
      return local;
    }
    ResourceHandler<FluidResource> foreign = level.getCapability(Capabilities.Fluid.BLOCK, pos,
        level.getBlockState(pos), be, side);
    if (foreign == null) {
      return null;
    }
    return foreign instanceof FluidHandlerResource wrapped ? wrapped.handler() : new ResourceFluidHandler(foreign);
  }

  @Nullable
  @SuppressWarnings("unchecked")
  public static <T> T get(@Nullable BlockEntity be, Capability<T> cap, @Nullable Direction side) {
    if (be == null || be.getLevel() == null) {
      return null;
    }
    if (cap == ForgeCapabilities.ITEM_HANDLER) {
      return (T) itemHandler(be.getLevel(), be.getBlockPos(), side);
    }
    if (cap == ForgeCapabilities.FLUID_HANDLER) {
      return (T) fluidHandler(be.getLevel(), be.getBlockPos(), side);
    }
    return local(be, cap, side);
  }

  @NotNull
  public static <T> LazyOptional<T> lazy(@Nullable BlockEntity be, Capability<T> cap, @Nullable Direction side) {
    T value = get(be, cap, side);
    return value == null ? LazyOptional.empty() : LazyOptional.of(() -> value);
  }

  @Nullable
  public static IFluidHandlerItem fluidHandlerItem(ItemStack stack) {
    if (stack.isEmpty()) {
      return null;
    }
    if (stack.getItem() instanceof IFluidHandlerItemProvider provider) {
      return provider.createFluidHandler(stack);
    }
    SingleStackHandler holder = new SingleStackHandler(stack);
    ResourceHandler<FluidResource> foreign = stack.getCapability(Capabilities.Fluid.ITEM,
        ItemAccess.forHandlerIndex(new ItemHandlerResource(holder), 0));
    if (foreign == null) {
      return null;
    }
    return new ForeignFluidItem(holder, new ResourceFluidHandler(foreign));
  }

  public interface IFluidHandlerItemProvider {
    IFluidHandlerItem createFluidHandler(ItemStack stack);
  }

  private static final class ForeignFluidItem implements IFluidHandlerItem {
    private final SingleStackHandler holder;
    private final ResourceFluidHandler wrapped;

    private ForeignFluidItem(SingleStackHandler holder, ResourceFluidHandler wrapped) {
      this.holder = holder;
      this.wrapped = wrapped;
    }

    @NotNull
    @Override
    public ItemStack getContainer() {
      return holder.stack;
    }

    @Override
    public int getTanks() {
      return wrapped.getTanks();
    }

    @NotNull
    @Override
    public FluidStack getFluidInTank(int tank) {
      return wrapped.getFluidInTank(tank);
    }

    @Override
    public int getTankCapacity(int tank) {
      return wrapped.getTankCapacity(tank);
    }

    @Override
    public boolean isFluidValid(int tank, @NotNull FluidStack fluid) {
      return wrapped.isFluidValid(tank, fluid);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
      return wrapped.fill(resource, action);
    }

    @NotNull
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
      return wrapped.drain(resource, action);
    }

    @NotNull
    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
      return wrapped.drain(maxDrain, action);
    }
  }

  private static final class SingleStackHandler implements IItemHandler {
    private ItemStack stack;

    private SingleStackHandler(ItemStack stack) {
      this.stack = stack;
    }

    @Override
    public int getSlots() {
      return 1;
    }

    @NotNull
    @Override
    public ItemStack getStackInSlot(int slot) {
      return stack;
    }

    @NotNull
    @Override
    public ItemStack insertItem(int slot, @NotNull ItemStack toInsert, boolean simulate) {
      if (!stack.isEmpty()) {
        return toInsert;
      }
      if (!simulate) {
        stack = toInsert.copy();
      }
      return ItemStack.EMPTY;
    }

    @NotNull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
      ItemStack out = stack.copyWithCount(Math.min(amount, stack.getCount()));
      if (!simulate) {
        stack = stack.copyWithCount(stack.getCount() - out.getCount());
      }
      return out;
    }

    @Override
    public int getSlotLimit(int slot) {
      return 64;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack candidate) {
      return true;
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack candidate) {
      stack = candidate;
    }

    @Override
    public boolean supportsSetStackInSlot() {
      return true;
    }
  }
}
