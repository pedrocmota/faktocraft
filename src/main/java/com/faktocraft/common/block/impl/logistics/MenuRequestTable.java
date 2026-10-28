package com.faktocraft.common.block.impl.logistics;

import com.faktocraft.common.util.ItemStackHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public class MenuRequestTable extends AbstractContainerMenu {

  public static final int MATRIX_START = 0;
  public static final int RESULT_INDEX = 9;
  public static final int GHOST_INDEX = 10;
  public static final int STORAGE_START = 11;
  public static final int PLAYER_START = STORAGE_START + BlockEntityRequestTable.STORAGE_SLOTS;

  public static final int BUTTON_AUTO_EXTRACT = 1;
  public static final int BUTTON_REQUEST_BASE = 1000;
  public static final int MAX_REQUEST = 1728;

  private final BlockEntityRequestTable table;
  private final BlockPos tablePos;
  private final boolean remote;
  private final Level level;
  private final CraftView craftView;
  private final ResultContainer result = new ResultContainer();
  private final ItemStackHandler ghostView = new ItemStackHandler(1);

  private int autoExtractMirror;

  public MenuRequestTable(int windowId, Level level, BlockPos pos, Inventory playerInventory, Player player) {
    this(windowId, level, pos, playerInventory, player, false);
  }

  public MenuRequestTable(int windowId, Level level, BlockPos pos, Inventory playerInventory, Player player,
      boolean remote) {
    super(LogisticsRegistry.REQUEST_TABLE_MENU, windowId);
    this.tablePos = pos;
    this.remote = remote;
    this.level = level;
    this.table = !(remote && level.isClientSide())
        && level.getBlockEntity(pos) instanceof BlockEntityRequestTable found ? found : null;
    this.craftView = new CraftView(table != null ? table.getCraftMatrix() : new ItemStackHandler(9));

    for (int row = 0; row < 3; row++) {
      for (int col = 0; col < 3; col++) {
        addSlot(new Slot(craftView, col + row * 3, 8 + col * 18, 37 + row * 18));
      }
    }
    addSlot(new Slot(result, 0, 102, 55) {
      @Override
      public boolean mayPlace(ItemStack stack) {
        return false;
      }

      @Override
      public boolean mayPickup(Player taker) {
        return resultStillCurrent();
      }

      @Override
      public void onTake(Player taker, ItemStack stack) {
        consumeMatrix(taker);
        stack.getItem().onCraftedBy(stack, level, taker);
        super.onTake(taker, stack);
      }
    });
    if (table != null) {
      ghostView.setStackInSlot(0, table.getGhostTarget());
    }
    addSlot(new Slot(ghostView, 0, 177, 193) {
      @Override
      public boolean mayPlace(ItemStack stack) {
        return false;
      }

      @Override
      public boolean mayPickup(Player taker) {
        return false;
      }
    });
    for (int i = 0; i < BlockEntityRequestTable.STORAGE_SLOTS; i++) {
      int x = 8 + (i % 9) * 18;
      int y = 105 + (i / 9) * 18;
      if (table != null) {
        addSlot(new Slot(table.getItemStackHandler(), i, x, y));
      } else {
        addSlot(new Slot(new ItemStackHandler(1), 0, x, y));
      }
    }

    for (int row = 0; row < 3; row++) {
      for (int col = 0; col < 9; col++) {
        addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 173 + row * 18));
      }
    }
    for (int col = 0; col < 9; col++) {
      addSlot(new Slot(playerInventory, col, 8 + col * 18, 231));
    }

    if (table != null && !level.isClientSide()) {
      table.attachMenu(this);
    }

    addDataSlot(new net.minecraft.world.inventory.DataSlot() {
      @Override
      public int get() {
        return table != null && table.isAutoExtract() ? 1 : 0;
      }

      @Override
      public void set(int value) {
        autoExtractMirror = value;
      }
    });

    refreshResult();
  }

  public BlockPos getTablePos() {
    return tablePos;
  }

  public boolean isRemote() {
    return remote;
  }

  @Nullable
  public BlockEntityRequestTable getTable() {
    return table;
  }

  public ItemStack getGhostStack() {
    return ghostView.getStackInSlot(0);
  }

  public void setGhostFromPacket(ItemStack stack) {
    if (table == null || !isKnownTarget(stack)) {
      return;
    }
    table.setGhostTarget(stack);
    ghostView.setStackInSlot(0, table.getGhostTarget());
    broadcastChanges();
  }

  private boolean isKnownTarget(ItemStack stack) {
    if (stack.isEmpty()) {
      return true;
    }
    BlockEntityLogisticsController core = table.findCore();
    LogisticsGraph graph = core != null ? core.graph() : null;
    Level tableLevel = table.getLevel();
    if (graph == null || tableLevel == null) {
      return false;
    }
    BlockEntityLogisticsController.GuiSnapshot snapshot = core.guiSnapshot(tableLevel, graph);
    ItemKey key = ItemKey.of(stack);
    if (snapshot.stock().containsKey(key) || snapshot.known().contains(key)) {
      return true;
    }
    for (LogisticsPlanner.CraftDecl decl : snapshot.decls()) {
      if (decl.result().equals(key)) {
        return true;
      }
    }
    return false;
  }

  void matrixChanged() {
    if (table != null && !level.isClientSide()) {
      table.refreshOpenMenus();
    } else {
      refreshResult();
    }
  }

  private ItemStack currentResult() {
    CraftingRecipe recipe = level.getRecipeManager()
        .getRecipeFor(RecipeType.CRAFTING, craftView, level).orElse(null);
    return recipe != null ? recipe.assemble(craftView, level.registryAccess()) : ItemStack.EMPTY;
  }

  public void refreshResult() {
    if (table == null || level.isClientSide()) {
      return;
    }
    result.setItem(0, currentResult());
    broadcastChanges();
  }

  private boolean resultStillCurrent() {
    if (table == null || level.isClientSide()) {
      return true;
    }
    ItemStack shown = result.getItem(0);
    ItemStack current = currentResult();
    if (shown.isEmpty() || !ItemStack.matches(shown, current)) {
      result.setItem(0, current);
      broadcastChanges();
      return false;
    }
    return true;
  }

  private void consumeMatrix(Player taker) {
    NonNullList<ItemStack> remainders = level.getRecipeManager()
        .getRemainingItemsFor(RecipeType.CRAFTING, craftView, level);
    for (int i = 0; i < 9; i++) {
      ItemStack inMatrix = craftView.getItem(i);
      if (inMatrix.isEmpty()) {
        continue;
      }
      ItemStack remainder = remainders.get(i);
      craftView.removeItem(i, 1);
      if (!remainder.isEmpty()) {
        if (craftView.getItem(i).isEmpty()) {
          craftView.setItem(i, remainder);
        } else if (!taker.getInventory().add(remainder)) {
          taker.drop(remainder, false);
        }
      }
    }
  }

  @Override
  public void clicked(int slotId, int button, ClickType clickType, Player player) {
    if (slotId == GHOST_INDEX && table != null) {
      table.setGhostTarget(getCarried());
      ghostView.setStackInSlot(0, table.getGhostTarget());
      broadcastChanges();
      return;
    }
    super.clicked(slotId, button, clickType, player);
  }

  public boolean isAutoExtract() {
    return autoExtractMirror != 0;
  }

  @Override
  public boolean clickMenuButton(Player player, int id) {
    if (table == null || !(player instanceof ServerPlayer serverPlayer)) {
      return false;
    }
    if (id == BUTTON_AUTO_EXTRACT) {
      table.setAutoExtract(!table.isAutoExtract());
      return true;
    }
    int quantity = id - BUTTON_REQUEST_BASE;
    if (quantity < 1 || quantity > MAX_REQUEST) {
      return false;
    }
    table.request(serverPlayer, quantity);
    return true;
  }

  @Override
  public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
    return target.container != result && super.canTakeItemForPickAll(carried, target);
  }

  @Override
  public ItemStack quickMoveStack(Player player, int index) {
    Slot slot = this.slots.get(index);
    if (!slot.hasItem() || index == GHOST_INDEX) {
      return ItemStack.EMPTY;
    }
    ItemStack stack = slot.getItem();
    ItemStack original = stack.copy();
    if (index == RESULT_INDEX) {
      if (!resultStillCurrent()) {
        return ItemStack.EMPTY;
      }
      ItemStack crafted = stack.copy();
      if (!moveItemStackTo(stack, PLAYER_START, this.slots.size(), true)) {
        return ItemStack.EMPTY;
      }

      if (!stack.isEmpty()) {
        ItemStack leftover = stack.copy();
        stack.setCount(0);
        player.drop(leftover, false);
      }
      slot.onTake(player, crafted);
      return crafted;
    } else if (index < PLAYER_START) {
      if (!moveItemStackTo(stack, PLAYER_START, this.slots.size(), true)) {
        return ItemStack.EMPTY;
      }
    } else if (!moveItemStackTo(stack, STORAGE_START,
        STORAGE_START + BlockEntityRequestTable.STORAGE_SLOTS, false)) {
      return ItemStack.EMPTY;
    }
    if (stack.isEmpty()) {
      slot.set(ItemStack.EMPTY);
    } else {
      slot.setChanged();
    }
    return stack.getCount() == original.getCount() ? ItemStack.EMPTY : original;
  }

  @Override
  public void removed(Player player) {
    if (table != null && !level.isClientSide()) {
      table.detachMenu(this);
      table.setAutoExtract(false);
    }
    super.removed(player);
  }

  @Override
  public boolean stillValid(Player player) {
    if (table == null) {
      return remote && level.isClientSide();
    }
    if (table.isRemoved() || !level.isLoaded(tablePos) || level.getBlockEntity(tablePos) != table) {
      return false;
    }
    if (remote) {
      return true;
    }
    return player.distanceToSqr(table.getBlockPos().getCenter()) <= 64.0;
  }

  private class CraftView implements CraftingContainer {

    private final ItemStackHandler backing;

    CraftView(ItemStackHandler backing) {
      this.backing = backing;
    }

    @Override
    public int getWidth() {
      return 3;
    }

    @Override
    public int getHeight() {
      return 3;
    }

    @Override
    public List<ItemStack> getItems() {
      NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
      for (int i = 0; i < 9; i++) {
        items.set(i, backing.getStackInSlot(i));
      }
      return items;
    }

    @Override
    public int getContainerSize() {
      return 9;
    }

    @Override
    public boolean isEmpty() {
      return backing.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
      return backing.getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
      ItemStack removed = backing.removeItem(slot, amount);
      if (!removed.isEmpty()) {
        matrixChanged();
      }
      return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
      return backing.removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
      backing.setItem(slot, stack);
      matrixChanged();
    }

    @Override
    public void setChanged() {
      matrixChanged();
    }

    @Override
    public boolean stillValid(Player player) {
      return true;
    }

    @Override
    public void clearContent() {
      for (int i = 0; i < 9; i++) {
        backing.setStackInSlot(i, ItemStack.EMPTY);
      }
      matrixChanged();
    }

    @Override
    public void fillStackedContents(StackedContents contents) {
      for (int i = 0; i < 9; i++) {
        contents.accountSimpleStack(backing.getStackInSlot(i));
      }
    }
  }
}
