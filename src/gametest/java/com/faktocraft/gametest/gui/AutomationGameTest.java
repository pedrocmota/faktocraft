package com.faktocraft.gametest.gui;

import com.faktocraft.common.entity.block.FaktocraftBlockEntity;
import com.faktocraft.common.entity.block.FluidStorage;
import com.faktocraft.common.util.ItemStackHandler;
import com.faktocraft.common.util.transfer.ForgeCapabilities;
import com.faktocraft.common.util.transfer.ICapabilityProvider;
import com.faktocraft.common.util.transfer.IFluidHandler;
import com.faktocraft.common.util.transfer.IItemHandler;
import com.faktocraft.gametest.GameTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

public class AutomationGameTest {

  private static final String TEMPLATE = "gametest_platform";
  private static final BlockPos MACHINE = new BlockPos(1, 1, 1);
  private static final Direction[] SIDES = { null, Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH,
      Direction.WEST, Direction.EAST };
  private static final int SAMPLE_EVERY = 40;
  private static final Set<String> IGNORED_FLUID_REVERT_MACHINES = Set.of("faktocraft:distillery",
      "faktocraft:extruder", "faktocraft:fermenter", "faktocraft:fluid_enricher", "faktocraft:ore_washing_plant",
      "faktocraft:polymerizer");

  private static List<Block> blocksWithEntities() {
    List<Block> out = new ArrayList<>();
    for (Block block : BuiltInRegistries.BLOCK) {
      if (block instanceof EntityBlock && GuiProbes.isMod(block)) {
        out.add(block);
      }
    }
    out.sort((a, b) -> GuiProbes.blockId(a).compareTo(GuiProbes.blockId(b)));
    return out;
  }

  private static String side(Direction side) {
    return side == null ? "no side" : side.getName();
  }

  private static void describe(StringBuilder out, String label, ItemStackHandler handler) {
    if (handler == null) {
      return;
    }
    for (int i = 0; i < handler.getSlots(); i++) {
      ItemStack stack = handler.getStackInSlot(i);
      if (!stack.isEmpty()) {
        out.append(label).append(i).append('=').append(GuiProbes.itemId(stack.getItem())).append('x')
            .append(stack.getCount()).append(stack.getComponentsPatch()).append(' ');
      }
    }
  }

  static String contents(BlockEntity be) {
    StringBuilder out = new StringBuilder();
    if (be instanceof FaktocraftBlockEntity machine) {
      describe(out, "i", machine.getItemStackHandler());
      describe(out, "b", machine.getBatteryStackHandler());
      describe(out, "u", machine.getUpgradeStackHandler());
      List<FluidStorage> tanks = machine.getGuiTanks();
      for (int i = 0; i < tanks.size(); i++) {
        FluidStack fluid = tanks.get(i).getFluidStack();
        if (!fluid.isEmpty()) {
          out.append('g').append(i).append('=').append(GuiProbes.fluidId(fluid.getFluid())).append('x')
              .append(fluid.getAmount()).append(' ');
        }
      }
    }
    if (be instanceof ICapabilityProvider provider) {
      IItemHandler items = provider.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
      if (items != null) {
        for (int i = 0; i < items.getSlots(); i++) {
          ItemStack stack = items.getStackInSlot(i);
          if (!stack.isEmpty()) {
            out.append('c').append(i).append('=').append(GuiProbes.itemId(stack.getItem())).append('x')
                .append(stack.getCount()).append(' ');
          }
        }
      }
      IFluidHandler fluids = provider.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElse(null);
      if (fluids != null) {
        for (int i = 0; i < fluids.getTanks(); i++) {
          FluidStack fluid = fluids.getFluidInTank(i);
          if (!fluid.isEmpty()) {
            out.append('t').append(i).append('=').append(GuiProbes.fluidId(fluid.getFluid())).append('x')
                .append(fluid.getAmount()).append(' ');
          }
        }
      }
    }
    return out.toString().trim();
  }

  private static int fluidTotal(BlockEntity be, Fluid fluid) {
    if (!(be instanceof ICapabilityProvider provider)) {
      return 0;
    }
    IFluidHandler fluids = provider.getCapability(ForgeCapabilities.FLUID_HANDLER, null).orElse(null);
    int total = 0;
    if (fluids != null) {
      for (int i = 0; i < fluids.getTanks(); i++) {
        FluidStack stack = fluids.getFluidInTank(i);
        if (!stack.isEmpty() && stack.getFluid().isSame(fluid)) {
          total += stack.getAmount();
        }
      }
    }
    return total;
  }

  private static int itemTotal(BlockEntity be, ItemStack like) {
    if (!(be instanceof ICapabilityProvider provider)) {
      return 0;
    }
    IItemHandler items = provider.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
    int total = 0;
    if (items != null) {
      for (int i = 0; i < items.getSlots(); i++) {
        ItemStack stack = items.getStackInSlot(i);
        if (ItemStack.isSameItemSameComponents(stack, like)) {
          total += stack.getCount();
        }
      }
    }
    return total;
  }

  private static List<GuiProbes.Probe> candidates(BlockEntity be) {
    IItemHandler internal = be instanceof ICapabilityProvider provider
        ? provider.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null) : null;
    List<GuiProbes.Probe> probes = GuiProbes.all();
    List<GuiProbes.Probe> out = new ArrayList<>();
    for (int p = 0; p < probes.size(); p++) {
      GuiProbes.Probe probe = probes.get(p);
      boolean keep = p % SAMPLE_EVERY == 0;
      if (!keep && internal != null) {
        ItemStack sample = probe.create();
        for (int slot = 0; slot < internal.getSlots() && !keep; slot++) {
          keep = internal.isItemValid(slot, sample);
        }
      }
      if (keep) {
        out.add(probe);
      }
    }
    return out;
  }

  private static ResourceHandler<FluidResource> fluidCap(ServerLevel level, BlockPos pos, Direction side) {
    BlockState state = level.getBlockState(pos);
    return level.getCapability(Capabilities.Fluid.BLOCK, pos, state, level.getBlockEntity(pos), side);
  }

  private static ResourceHandler<ItemResource> itemCap(ServerLevel level, BlockPos pos, Direction side) {
    BlockState state = level.getBlockState(pos);
    return level.getCapability(Capabilities.Item.BLOCK, pos, state, level.getBlockEntity(pos), side);
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 400)
  public static void simulatedFluidInsertsLeaveMachinesUntouched(GameTestHelper helper) {
    BlockSweep.Report report = new BlockSweep.Report("fluid-simulate");
    ServerLevel level = helper.getLevel();
    BlockPos pos = helper.absolutePos(MACHINE);
    BlockSweep.forEachBlock(helper, MACHINE, blocksWithEntities(), report, block -> {
      BlockEntity be = level.getBlockEntity(pos);
      if (be == null || IGNORED_FLUID_REVERT_MACHINES.contains(GuiProbes.blockId(block))) {
        return;
      }
      for (Direction side : SIDES) {
        ResourceHandler<FluidResource> handler = fluidCap(level, pos, side);
        if (handler == null) {
          continue;
        }
        for (Fluid fluid : GuiProbes.sourceFluids()) {
          String before = contents(be);
          int inserted;
          try (Transaction tx = Transaction.openRoot()) {
            inserted = handler.insert(FluidResource.of(fluid), 1000, tx);
          }
          String after = contents(be);
          if (!before.equals(after)) {
            report.add(block, "an aborted insert of " + inserted + " mB " + GuiProbes.fluidId(fluid) + " from "
                + side(side) + " changed the machine: [" + before + "] -> [" + after + "]");
          }
        }
      }
    }, () -> {
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 400)
  public static void simulatedFluidExtractsLeaveMachinesUntouched(GameTestHelper helper) {
    BlockSweep.Report report = new BlockSweep.Report("fluid-extract");
    ServerLevel level = helper.getLevel();
    BlockPos pos = helper.absolutePos(MACHINE);
    BlockSweep.forEachBlock(helper, MACHINE, blocksWithEntities(), report, block -> {
      if (!(level.getBlockEntity(pos) instanceof FaktocraftBlockEntity machine)
          || IGNORED_FLUID_REVERT_MACHINES.contains(GuiProbes.blockId(block))) {
        return;
      }
      List<FluidStorage> tanks = machine.getGuiTanks();
      for (FluidStorage tank : tanks) {
        Fluid chosen = null;
        for (Fluid fluid : GuiProbes.sourceFluids()) {
          if (tank.isValid(new FluidStack(fluid, 1000))) {
            chosen = fluid;
            break;
          }
        }
        tank.setFluid(new FluidStack(chosen != null ? chosen : net.minecraft.world.level.material.Fluids.WATER,
            1000), Math.min(2000, tank.getCapacityMb()));
      }
      for (Direction side : SIDES) {
        ResourceHandler<FluidResource> handler = fluidCap(level, pos, side);
        if (handler == null) {
          continue;
        }
        for (int index = 0; index < handler.size(); index++) {
          FluidResource resource = handler.getResource(index);
          if (resource.isEmpty()) {
            continue;
          }
          String before = contents(machine);
          int extracted;
          try (Transaction tx = Transaction.openRoot()) {
            extracted = handler.extract(index, resource, 1000, tx);
          }
          String after = contents(machine);
          if (!before.equals(after)) {
            report.add(block, "an aborted extract of " + extracted + " mB from tank " + index + " on "
                + side(side) + " changed the machine: [" + before + "] -> [" + after + "]");
          }
        }
      }
    }, () -> {
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 400)
  public static void fluidInsertsStoreWhatTheyReport(GameTestHelper helper) {
    BlockSweep.Report report = new BlockSweep.Report("fluid-commit");
    ServerLevel level = helper.getLevel();
    BlockPos pos = helper.absolutePos(MACHINE);
    BlockSweep.forEachBlock(helper, MACHINE, blocksWithEntities(), report, block -> {
      BlockEntity be = level.getBlockEntity(pos);
      if (be == null) {
        return;
      }
      for (Direction side : SIDES) {
        ResourceHandler<FluidResource> handler = fluidCap(level, pos, side);
        if (handler == null) {
          continue;
        }
        for (Fluid fluid : GuiProbes.sourceFluids()) {
          int before = fluidTotal(be, fluid);
          int inserted;
          try (Transaction tx = Transaction.openRoot()) {
            inserted = handler.insert(FluidResource.of(fluid), 1000, tx);
            tx.commit();
          }
          int after = fluidTotal(be, fluid);
          if (after - before != inserted) {
            report.add(block, "inserting " + GuiProbes.fluidId(fluid) + " from " + side(side) + " reported "
                + inserted + " mB but the tanks changed by " + (after - before) + " mB");
          }
        }
      }
    }, () -> {
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 400)
  public static void simulatedItemInsertsLeaveMachinesUntouched(GameTestHelper helper) {
    BlockSweep.Report report = new BlockSweep.Report("item-simulate");
    ServerLevel level = helper.getLevel();
    BlockPos pos = helper.absolutePos(MACHINE);
    BlockSweep.forEachBlock(helper, MACHINE, blocksWithEntities(), report, block -> {
      BlockEntity be = level.getBlockEntity(pos);
      if (be == null) {
        return;
      }
      List<GuiProbes.Probe> probes = candidates(be);
      for (Direction side : SIDES) {
        ResourceHandler<ItemResource> handler = itemCap(level, pos, side);
        if (handler == null) {
          continue;
        }
        String before = contents(be);
        for (GuiProbes.Probe probe : probes) {
          ItemStack stack = probe.full();
          int inserted;
          try (Transaction tx = Transaction.openRoot()) {
            inserted = handler.insert(ItemResource.of(stack), stack.getCount(), tx);
          }
          String after = contents(be);
          if (!before.equals(after)) {
            report.add(block, "an aborted insert of " + inserted + "x " + probe.key() + " from " + side(side)
                + " changed the machine: [" + before + "] -> [" + after + "]");
            before = after;
          }
        }
      }
    }, () -> {
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 400)
  public static void simulatedItemExtractsLeaveMachinesUntouched(GameTestHelper helper) {
    BlockSweep.Report report = new BlockSweep.Report("item-extract");
    ServerLevel level = helper.getLevel();
    BlockPos pos = helper.absolutePos(MACHINE);
    BlockSweep.forEachBlock(helper, MACHINE, blocksWithEntities(), report, block -> {
      BlockEntity be = level.getBlockEntity(pos);
      if (!(be instanceof FaktocraftBlockEntity machine) || machine.getItemStackHandler() == null) {
        return;
      }
      ItemStackHandler stacks = machine.getItemStackHandler();
      for (int slot = 0; slot < stacks.getSlots(); slot++) {
        stacks.setStackInSlot(slot, new ItemStack(Items.COBBLESTONE, 10));
      }
      try {
        for (Direction side : SIDES) {
          ResourceHandler<ItemResource> handler = itemCap(level, pos, side);
          if (handler == null) {
            continue;
          }
          for (int index = 0; index < handler.size(); index++) {
            String before = contents(be);
            ItemResource resource = handler.getResource(index);
            if (resource.isEmpty()) {
              continue;
            }
            int extracted;
            try (Transaction tx = Transaction.openRoot()) {
              extracted = handler.extract(index, resource, 64, tx);
            }
            String after = contents(be);
            if (!before.equals(after)) {
              report.add(block, "an aborted extract of " + extracted + " from slot " + index + " on "
                  + side(side) + " changed the machine: [" + before + "] -> [" + after + "]");
            }
          }
        }
      } finally {
        for (int slot = 0; slot < stacks.getSlots(); slot++) {
          stacks.setStackInSlot(slot, ItemStack.EMPTY);
        }
      }
    }, () -> {
    });
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 400)
  public static void itemInsertsStoreWhatTheyReport(GameTestHelper helper) {
    BlockSweep.Report report = new BlockSweep.Report("item-commit");
    ServerLevel level = helper.getLevel();
    BlockPos pos = helper.absolutePos(MACHINE);
    BlockSweep.forEachBlock(helper, MACHINE, blocksWithEntities(), report, block -> {
      BlockEntity be = level.getBlockEntity(pos);
      if (be == null) {
        return;
      }
      List<GuiProbes.Probe> probes = candidates(be);
      ItemStackHandler stacks = be instanceof FaktocraftBlockEntity machine ? machine.getItemStackHandler() : null;
      for (Direction side : SIDES) {
        ResourceHandler<ItemResource> handler = itemCap(level, pos, side);
        if (handler == null) {
          continue;
        }
        for (GuiProbes.Probe probe : probes) {
          ItemStack stack = probe.full();
          int before = itemTotal(be, stack);
          int inserted;
          try (Transaction tx = Transaction.openRoot()) {
            inserted = handler.insert(ItemResource.of(stack), stack.getCount(), tx);
            tx.commit();
          }
          int after = itemTotal(be, stack);
          if (after - before != inserted) {
            report.add(block, "inserting " + probe.key() + " from " + side(side) + " reported " + inserted
                + " but the inventory changed by " + (after - before));
          }
          if (stacks != null) {
            for (int slot = 0; slot < stacks.getSlots(); slot++) {
              stacks.setStackInSlot(slot, ItemStack.EMPTY);
            }
          }
        }
      }
    }, () -> {
    });
  }
}
