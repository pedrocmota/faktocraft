package com.faktocraft.common.block.impl.generators.solar_panels;

import com.faktocraft.common.energy.interfaces.IEnergyBlock;
import com.faktocraft.common.entity.block.FaktocraftBlockEntity;
import com.faktocraft.common.enums.EnergyType;
import com.faktocraft.common.registries.machines.M1Registry;
import com.faktocraft.common.tier.SolarGeneratorTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class BlockEntitySolarGenerator extends FaktocraftBlockEntity
    implements IEnergyBlock, com.faktocraft.common.energy.ICableSideFilter {

  @Override
  public boolean acceptsCableFrom(net.minecraft.core.Direction side) {
    return side != net.minecraft.core.Direction.UP;
  }

  private final SolarGeneratorTier tier;
  private final com.faktocraft.common.block.ModelFlags panelFlags = SolarPanelFlags.create();
  private int lastAmount = 0;
  public int amount = 0;

  public com.faktocraft.common.block.ModelFlags panelFlags() {
    return panelFlags;
  }

  private final com.faktocraft.common.block.ModelFlags.Resolver panelResolver =
      (blockEntity, level, pos, state, pending) -> SolarPanelFlags.resolve(blockEntity, panelFlags, level, pos,
          pending);

  private void resolvePanelFlags() {
    panelFlags.resolve(this, panelResolver);
  }

  @Override
  public net.neoforged.neoforge.model.data.ModelData getModelData() {
    return panelFlags.modelData(net.neoforged.neoforge.model.data.ModelData.EMPTY);
  }

  @Override
  public void onDataPacket(net.minecraft.network.Connection connection,
      net.minecraft.world.level.storage.ValueInput input) {
    int previous = panelFlags.mask();
    super.onDataPacket(connection, input);
    if (panelFlags.mask() != previous) {
      com.faktocraft.common.cover.CoverSupport.refreshClientModel(this);
    }
  }

  @Override
  public void setLevel(net.minecraft.world.level.Level level) {
    super.setLevel(level);
    panelFlags.onLevelSet(this);
  }

  @Override
  public void onLoad() {
    super.onLoad();
    resolvePanelFlags();
    panelFlags.onClientLoad(this);
  }

  @Override
  public CompoundTag getUpdateTag() {
    panelFlags.resolveForSync(this, panelResolver);
    return super.getUpdateTag();
  }

  public BlockEntitySolarGenerator(BlockPos pos, BlockState state) {
    super(M1Registry.SOLAR_GENERATOR_BE, pos, state);

    BlockSolarGenerator block = (BlockSolarGenerator) state.getBlock();
    this.tier = block.getSolarGeneratorTier();

    createEnergyStorage(0, tier.getEnergyCapacity(), EnergyType.EXTRACT, tier.getEnergyTier());
  }

  @Override
  public boolean getActive() {
    return super.getActive();
  }

  @Override
  public void tickWork(BlockState state) {
    resolvePanelFlags();
    boolean active = false;
    amount = 0;
    getEnergyStorage().updateGenerated(0);

    if (level != null
        && level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling()
        && level.canSeeSky(getBlockPos().above())) {
      if (level.isBrightOutside()) {
        amount = level.isThundering() || level.isRaining()
            ? tier.getNightGenerate()
            : tier.getDayGenerate();
      } else {
        amount = tier.getMoonlightGenerate();
      }
    }

    if (amount > 0) {
      active = true;
      if (getEnergyStorage().generateEnergy(amount, true) == amount) {
        getEnergyStorage().generateEnergy(amount, false);
        getEnergyStorage().updateGenerated(amount);
      }
    }

    if (amount != lastAmount) {
      lastAmount = amount;
      super.updateBlockState();
    }

    boolean wasActive = activeState;
    setActive(active);
    if (wasActive != active) {
      super.updateBlockState();
    }
  }

  @Override
  protected void saveAdditional(CompoundTag tag) {
    tag.putBoolean("active", activeState);
    tag.putInt("amount", amount);
    tag.putInt("lastAmount", lastAmount);
    panelFlags.save(tag);
    super.saveAdditional(tag);
  }

  @Override
  public void load(CompoundTag tag) {
    super.load(tag);
    activeState = tag.getBooleanOr("active", false);
    amount = tag.contains("amount") ? tag.getIntOr("amount", 0) : 0;
    lastAmount = tag.contains("lastAmount") ? tag.getIntOr("lastAmount", 0) : 0;
    panelFlags.load(tag);
  }

  @Override
  public boolean canExtractEnergyDir(@Nullable Direction side) {
    return side != Direction.UP;
  }

  @Override
  public int defaultGeneratorPriority() {
    return com.faktocraft.common.config.ModConfig.server().priority_solar_generator;
  }

  @Override
  public boolean showBarInGui() {
    return false;
  }
}
