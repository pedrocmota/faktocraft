package com.faktocraft.common.block.impl.machines.nuclear_reactor;

import com.faktocraft.common.util.InteractionResults;
import com.faktocraft.common.util.PlayerMessages;
import com.faktocraft.Faktocraft;
import com.faktocraft.common.container.FaktocraftMenuProvider;
import com.faktocraft.common.energy.provider.EnergyCore;
import com.faktocraft.common.entity.block.FaktocraftBlockEntity;
import com.faktocraft.common.interfaces.block.IHasMenu;
import com.faktocraft.common.util.FluidInteractionHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class BlockNuclearReactor extends Block implements EntityBlock, IHasMenu {
  public static final EnumProperty<ReactorPart> PART = EnumProperty.create("part", ReactorPart.class);
  public static final BooleanProperty PLACED = BooleanProperty.create("placed");
  public static final ReactorPart CORE_PART = ReactorPart.of(ReactorPart.SIZE / 2, ReactorPart.SIZE - 1,
      ReactorPart.SIZE / 2);

  public BlockNuclearReactor(Properties properties) {
    super(properties);
    registerDefaultState(getStateDefinition().any().setValue(PART, ReactorPart.SINGLE).setValue(PLACED, true));
  }

  public static BlockBehaviour.Properties reactorProperties() {
    return BlockBehaviour.Properties.of()
        .mapColor(MapColor.METAL)
        .strength(5F, 12F)
        .sound(SoundType.METAL)
        .pushReaction(PushReaction.IMMOVEABLE)
        .requiresCorrectToolForDrops();
  }

  public static boolean isCore(BlockState state) {
    return state.getValue(PART) == CORE_PART;
  }

  @Nullable
  public static BlockPos corePos(BlockState state, BlockPos pos) {
    ReactorPart part = state.getValue(PART);
    if (part.isSingle()) {
      return null;
    }
    return pos.offset(CORE_PART.x() - part.x(), CORE_PART.y() - part.y(), CORE_PART.z() - part.z());
  }

  @Nullable
  @Override
  public BlockState getStateForPlacement(BlockPlaceContext context) {
    Level level = context.getLevel();
    if (NuclearReactorMultiblock.canComplete(level, context.getClickedPos())) {
      return defaultBlockState();
    }
    Player player = context.getPlayer();
    if (player != null && level.isClientSide()) {
      PlayerMessages.display(player, Component.translatable("chat." + Faktocraft.MODID + ".nuclear_reactor.needs_cube")
          .withStyle(ChatFormatting.RED), true);
    }
    return null;
  }

  @Override
  public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
    super.onPlace(state, level, pos, oldState, isMoving);
    if (oldState.is(this) && level instanceof ServerLevel serverLevel) {
      ReactorPart part = oldState.getValue(PART);
      boolean partGone = !part.isSingle() && state.getValue(PART) != part;
      if (partGone) {
        if (isCore(oldState) && level.getBlockEntity(pos) instanceof FaktocraftBlockEntity blockEntity) {
          blockEntity.preRemoveSideEffects(pos, oldState);
        }
        EnergyCore.get(serverLevel).removeEnergyBlock(pos);
        level.removeBlockEntity(pos);
      }
    }
    if (!isMoving && state.getValue(PART).isSingle() && !oldState.is(this) && !level.isClientSide()) {
      level.scheduleTick(pos, this, 1);
    }
  }

  @Override
  public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    super.tick(state, level, pos, random);
    if (state.getValue(PART).isSingle()) {
      NuclearReactorMultiblock.tryForm(level, pos);
    }
  }

  @Override
  protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos,
      boolean movedByPiston) {
    super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    if (!state.getValue(PART).isSingle()) {
      EnergyCore.get(level).removeEnergyBlock(pos);
    }
    NuclearReactorMultiblock.unform(level, pos, state.getValue(PART));
  }

  @Override
  public void onBlockStateChange(LevelReader levelReader, BlockPos pos, BlockState oldState, BlockState newState) {
    super.onBlockStateChange(levelReader, pos, oldState, newState);
    if (!(levelReader instanceof Level level) || !level.isClientSide()) {
      return;
    }
    BlockEntity blockEntity = level.getChunkAt(pos).getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK);
    if (blockEntity == null) {
      return;
    }
    boolean matches = isCore(newState) ? blockEntity instanceof BlockEntityNuclearReactor
        : !newState.getValue(PART).isSingle() && blockEntity instanceof BlockEntityReactorPart;
    if (!matches) {
      level.removeBlockEntity(pos);
    }
  }

  @Override
  protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
    builder.add(PART, PLACED);
  }

  @Override
  public boolean addRunningEffects(BlockState state, Level level, BlockPos pos, Entity entity) {
    return true;
  }

  @Nullable
  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    if (isCore(state)) {
      return new BlockEntityNuclearReactor(pos, state);
    }
    return state.getValue(PART).isSingle() ? null : new BlockEntityReactorPart(pos, state);
  }

  @Nullable
  @Override
  public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
      BlockEntityType<T> type) {
    if (!isCore(state)) {
      return null;
    }
    if (level.isClientSide()) {
      return (lvl, pos, blockState, blockEntity) -> {
        if (blockEntity instanceof FaktocraftBlockEntity faktocraftBlockEntity) {
          faktocraftBlockEntity.tickClient(blockState);
        }
      };
    }
    return (lvl, pos, blockState, blockEntity) -> {
      if (blockEntity instanceof FaktocraftBlockEntity faktocraftBlockEntity) {
        faktocraftBlockEntity.tickServer(blockState);
      }
    };
  }

  @Override
  protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
      InteractionHand hand, BlockHitResult hitResult) {
    if (!isCore(state) || !(level.getBlockEntity(pos) instanceof BlockEntityNuclearReactor reactor)) {
      return InteractionResult.PASS;
    }
    BlockPos core = pos;
    if (!level.isClientSide()) {
      if (!player.isShiftKeyDown()
          && FluidInteractionHelper.tryFillTankFromHand(player, hand, reactor.water, Fluids.WATER)) {
        return InteractionResult.SUCCESS;
      }
      if (player instanceof ServerPlayer serverPlayer) {
        serverPlayer.openMenu(new FaktocraftMenuProvider(this, level, core, getName()),
            buf -> buf.writeBlockPos(core));
      }
    }
    return InteractionResults.sided(level);
  }

  @Override
  public AbstractContainerMenu getMenu(int windowId, Level level, BlockPos pos, Inventory playerInventory,
      Player player) {
    return new MenuNuclearReactor(windowId, level, pos, playerInventory, player);
  }
}
