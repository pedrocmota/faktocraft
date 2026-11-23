package com.faktocraft.gametest.machines;

import com.faktocraft.common.block.impl.pipe.IExtractorPipe;
import com.faktocraft.common.block.impl.pipe.MenuExtractorPipe;
import com.faktocraft.common.block.impl.pipe.PipeExtractor;
import com.faktocraft.common.container.TensionRemoval;
import com.faktocraft.common.energy.impl.BasicEnergyStorage;
import com.faktocraft.common.entity.block.FaktocraftBlockEntity;
import com.faktocraft.common.enums.EnergyTier;
import com.faktocraft.common.enums.EnergyType;
import com.faktocraft.common.interfaces.block.IHasMenu;
import com.faktocraft.common.registries.ModItems;
import com.faktocraft.common.registries.PipeRegistry;
import com.faktocraft.common.registries.machines.M2Registry;
import com.faktocraft.gametest.GameTest;
import com.faktocraft.gametest.TestUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.Nullable;

public class TensionRemovalGameTest {

  private static final String TEMPLATE = "gametest_platform";
  private static final BlockPos POS = new BlockPos(1, 1, 1);
  private static final int HOTBAR = 3;
  private static final int LEFT = 0;

  private record Dock(FaktocraftBlockEntity machine, AbstractContainerMenu menu, Slot tension, Inventory inventory) {
  }

  private static Dock chargedExtractor(GameTestHelper helper) {
    helper.setBlock(POS, M2Registry.EXTRACTOR.defaultBlockState());
    if (!(TestUtil.blockEntity(helper, POS) instanceof FaktocraftBlockEntity machine)) {
      throw TestUtil.assertion(helper, "no extractor block entity");
    }
    machine.getBatteryStackHandler().setStackInSlot(0, new ItemStack(ModItems.BASIC_CAPACITOR));
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    AbstractContainerMenu menu = ((IHasMenu) M2Registry.EXTRACTOR).getMenu(1, helper.getLevel(),
        helper.absolutePos(POS), player.getInventory(), player);
    Slot tension = dockSlot(helper, menu, machine.getBatteryStackHandler(), FaktocraftBlockEntity.TENSION_DOCK_SLOT);
    tension.set(new ItemStack(ModItems.TENSION_UPGRADE_MK2));
    BasicEnergyStorage storage = machine.getEnergyStorage();
    if (TensionRemoval.energized(storage)) {
      throw TestUtil.assertion(helper, "an empty machine should not ask for confirmation");
    }
    storage.setEnergy(storage.maxEnergy());
    if (!TensionRemoval.energized(storage)) {
      throw TestUtil.assertion(helper, "a charged machine should ask for confirmation");
    }
    return new Dock(machine, menu, tension, player.getInventory());
  }

  private static Slot dockSlot(GameTestHelper helper, AbstractContainerMenu menu, Object container, int index) {
    for (Slot slot : menu.slots) {
      if (slot.container == container && slot.getContainerSlot() == index) {
        return slot;
      }
    }
    throw TestUtil.assertion(helper, "no dock slot " + index + " in " + menu.getClass().getSimpleName());
  }

  private static Slot playerSlot(GameTestHelper helper, AbstractContainerMenu menu, int skip) {
    int seen = 0;
    for (Slot slot : menu.slots) {
      if (slot.container instanceof Inventory && seen++ == skip) {
        return slot;
      }
    }
    throw TestUtil.assertion(helper, "no player slot " + skip + " in " + menu.getClass().getSimpleName());
  }

  @Nullable
  private static TensionRemoval.Change click(Dock dock, @Nullable Slot slot, int button, ContainerInput type) {
    return TensionRemoval.change(dock.menu(), slot, dock.inventory(), button, type);
  }

  private static void drops(GameTestHelper helper, @Nullable TensionRemoval.Change change, int from, int to,
      String action) {
    if (change == null || change.from() != from || change.to() != to) {
      throw TestUtil.assertion(helper, action + " should ask before dropping MK" + from + " to MK" + to
          + ", decided " + change);
    }
  }

  private static void silent(GameTestHelper helper, @Nullable TensionRemoval.Change change, String action) {
    if (change != null) {
      throw TestUtil.assertion(helper, action + " should not ask for confirmation, decided " + change);
    }
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 20)
  public static void takingTheUpgradeOutOfAnEnergizedMachineAsks(GameTestHelper helper) {
    Dock dock = chargedExtractor(helper);
    drops(helper, click(dock, dock.tension(), LEFT, ContainerInput.PICKUP), 2, 0, "picking the upgrade up");
    drops(helper, click(dock, dock.tension(), LEFT, ContainerInput.QUICK_MOVE), 2, 0, "shift-clicking the upgrade");
    drops(helper, click(dock, dock.tension(), LEFT, ContainerInput.THROW), 2, 0, "dropping the upgrade");
    drops(helper, click(dock, dock.tension(), HOTBAR, ContainerInput.SWAP), 2, 0, "swapping with an empty hotbar");
    silent(helper, click(dock, dock.tension(), LEFT, ContainerInput.CLONE), "cloning the upgrade");
    dock.menu().setCarried(new ItemStack(Items.COAL));
    silent(helper, click(dock, dock.tension(), LEFT, ContainerInput.THROW), "pressing drop with coal in hand");
    silent(helper, click(dock, dock.tension(), LEFT, ContainerInput.PICKUP), "clicking with coal in hand");
    helper.succeed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 20)
  public static void replacingTheUpgradeOnlyAsksWhenTheVoltageDrops(GameTestHelper helper) {
    Dock dock = chargedExtractor(helper);
    dock.menu().setCarried(new ItemStack(ModItems.TENSION_UPGRADE_MK1));
    drops(helper, click(dock, dock.tension(), LEFT, ContainerInput.PICKUP), 2, 1, "swapping in an MK1");
    dock.menu().setCarried(new ItemStack(ModItems.TENSION_UPGRADE_MK3));
    silent(helper, click(dock, dock.tension(), LEFT, ContainerInput.PICKUP), "swapping in an MK3");
    dock.menu().setCarried(new ItemStack(ModItems.TENSION_UPGRADE_MK2));
    silent(helper, click(dock, dock.tension(), LEFT, ContainerInput.PICKUP), "clicking with the same upgrade");
    dock.menu().setCarried(new ItemStack(ModItems.TENSION_UPGRADE_MK1, 2));
    silent(helper, click(dock, dock.tension(), LEFT, ContainerInput.PICKUP), "clicking with two MK1");
    dock.menu().setCarried(ItemStack.EMPTY);
    dock.inventory().setItem(HOTBAR, new ItemStack(ModItems.TENSION_UPGRADE_MK1));
    drops(helper, click(dock, dock.tension(), HOTBAR, ContainerInput.SWAP), 2, 1, "hotbar-swapping an MK1");
    dock.inventory().setItem(HOTBAR, new ItemStack(ModItems.TENSION_UPGRADE_MK3));
    silent(helper, click(dock, dock.tension(), HOTBAR, ContainerInput.SWAP), "hotbar-swapping an MK3");
    dock.inventory().setItem(HOTBAR, new ItemStack(Items.COAL));
    silent(helper, click(dock, dock.tension(), HOTBAR, ContainerInput.SWAP), "hotbar-swapping coal");
    helper.succeed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 20)
  public static void otherSlotsOnlyAskWhenADoubleClickWouldGatherTheUpgrade(GameTestHelper helper) {
    Dock dock = chargedExtractor(helper);
    Slot capacitor = dockSlot(helper, dock.menu(), dock.machine().getBatteryStackHandler(), 0);
    silent(helper, click(dock, capacitor, LEFT, ContainerInput.PICKUP), "taking the capacitor");
    silent(helper, click(dock, capacitor, LEFT, ContainerInput.QUICK_MOVE), "shift-clicking the capacitor");
    Slot pocket = playerSlot(helper, dock.menu(), 0);
    pocket.set(new ItemStack(ModItems.TENSION_UPGRADE_MK2));
    silent(helper, click(dock, pocket, LEFT, ContainerInput.PICKUP), "taking an upgrade from the inventory");
    silent(helper, click(dock, null, LEFT, ContainerInput.PICKUP), "clicking outside the slots");
    Slot empty = playerSlot(helper, dock.menu(), 1);
    dock.menu().setCarried(new ItemStack(ModItems.TENSION_UPGRADE_MK2));
    drops(helper, click(dock, empty, LEFT, ContainerInput.PICKUP_ALL), 2, 0,
        "double-clicking with a matching upgrade in hand");
    dock.menu().setCarried(new ItemStack(ModItems.TENSION_UPGRADE_MK1));
    silent(helper, click(dock, empty, LEFT, ContainerInput.PICKUP_ALL),
        "double-clicking with a different upgrade in hand");
    dock.menu().setCarried(new ItemStack(ModItems.TENSION_UPGRADE_MK2));
    silent(helper, click(dock, pocket, LEFT, ContainerInput.PICKUP_ALL), "double-clicking a filled slot");
    helper.succeed();
  }

  @GameTest(template = TEMPLATE, timeoutTicks = 20)
  public static void tierLimitsAndTheExtractorPipeUpgradeFollowTheSameRule(GameTestHelper helper) {
    BasicEnergyStorage low = new BasicEnergyStorage(10, 100, EnergyType.RECEIVE, EnergyTier.LOW);
    if (TensionRemoval.inputTier(low, 0) != EnergyTier.LOW || TensionRemoval.inputTier(low, 2) != EnergyTier.HIGH
        || TensionRemoval.inputTier(low, 3) != EnergyTier.VERY_HIGH
        || TensionRemoval.inputTier(low, 9) != EnergyTier.VERY_HIGH) {
      throw TestUtil.assertion(helper, "input tier does not follow the upgrade level");
    }
    BasicEnergyStorage top = new BasicEnergyStorage(10, 100, EnergyType.RECEIVE, EnergyTier.VERY_HIGH);
    if (TensionRemoval.energized(top) || TensionRemoval.energized(null)) {
      throw TestUtil.assertion(helper, "a very high machine or a missing storage should never ask");
    }

    helper.setBlock(POS, PipeRegistry.FLUID_EXTRACTOR_PIPE.defaultBlockState());
    if (!(TestUtil.blockEntity(helper, POS) instanceof IExtractorPipe pipe)) {
      throw TestUtil.assertion(helper, "no extractor pipe block entity");
    }
    PipeExtractor extractor = pipe.extractor();
    extractor.getDock().setStackInSlot(0, new ItemStack(ModItems.BASIC_CAPACITOR));
    Player player = helper.makeMockPlayer(GameType.SURVIVAL);
    AbstractContainerMenu menu = new MenuExtractorPipe(1, helper.getLevel(), helper.absolutePos(POS),
        player.getInventory(), player);
    Slot tension = dockSlot(helper, menu, extractor.getDock(), PipeExtractor.DOCK_TENSION_SLOT);
    tension.set(new ItemStack(ModItems.TENSION_UPGRADE_MK1));
    if (TensionRemoval.energized(extractor.energy())) {
      throw TestUtil.assertion(helper, "an empty pipe should not ask for confirmation");
    }
    extractor.energy().setEnergy(extractor.energy().maxEnergy());
    if (!TensionRemoval.energized(extractor.energy())) {
      throw TestUtil.assertion(helper, "a charged pipe should ask for confirmation");
    }
    drops(helper, TensionRemoval.change(menu, tension, player.getInventory(), LEFT, ContainerInput.PICKUP), 1, 0,
        "taking the upgrade out of the pipe");
    helper.succeed();
  }
}
