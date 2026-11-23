package com.faktocraft.common.block.impl.generators.solar_panels;

import com.faktocraft.common.block.BlockElectricMachine;
import com.faktocraft.common.interfaces.block.IGenerationInfo;
import com.faktocraft.common.interfaces.block.IHasMenu;
import com.faktocraft.common.tier.SolarGeneratorTier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class BlockSolarGenerator extends BlockElectricMachine implements IHasMenu, IGenerationInfo {

  @Override
  public void appendGenerationInfo(java.util.List<net.minecraft.network.chat.Component> tooltip) {
    tooltip.add(IGenerationInfo.line("generation_sun", IGenerationInfo.rate(solarTier.getDayGenerate())));
    if (solarTier.getNightGenerate() > 0) {
      tooltip.add(IGenerationInfo.line("generation_rain", IGenerationInfo.rate(solarTier.getNightGenerate())));
    }
    if (solarTier.getMoonlightGenerate() > 0) {
      tooltip.add(IGenerationInfo.line("generation_moon", IGenerationInfo.rate(solarTier.getMoonlightGenerate())));
    }
  }

  private final SolarGeneratorTier solarTier;
  protected final VoxelShape shape;

  public BlockSolarGenerator(SolarGeneratorTier tier, Properties properties) {
    super(tier.getEnergyTier(), properties);
    this.shape = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 8.0D, 16.0D);
    this.solarTier = tier;
  }

  @Override
  public BlockState updateShape(BlockState state, net.minecraft.world.level.LevelReader level,
      net.minecraft.world.level.ScheduledTickAccess ticks, BlockPos pos, net.minecraft.core.Direction direction,
      BlockPos neighborPos, BlockState neighborState, net.minecraft.util.RandomSource random) {
    if (SolarPanelFlags.isCableSide(direction) || SolarPanelFlags.isPanelSide(direction)) {
      if (level.getBlockEntity(pos) instanceof BlockEntitySolarGenerator panel) {
        com.faktocraft.common.block.ModelFlags flags = panel.panelFlags();
        SolarPanelFlags.onShapeUpdate(panel, flags, level, pos, direction, neighborState);
        if (flags.takeDirty() && level instanceof net.minecraft.world.level.LevelAccessor accessor
            && !accessor.isClientSide()) {
          state.updateNeighbourShapes(accessor, pos, Block.UPDATE_CLIENTS, Block.UPDATE_LIMIT);
        }
      }
      return state;
    }
    return super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random);
  }

  @Nullable
  @Override
  public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
    return new BlockEntitySolarGenerator(pos, state);
  }

  @Override
  public MenuSolarGenerator getMenu(int windowId, Level level, BlockPos pos, Inventory playerInventory, Player player) {
    return new MenuSolarGenerator(windowId, level, pos, playerInventory, player);
  }

  @Override
  public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return shape;
  }

  @Override
  public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
    return shape;
  }

  public SolarGeneratorTier getSolarGeneratorTier() {
    return solarTier;
  }
}
