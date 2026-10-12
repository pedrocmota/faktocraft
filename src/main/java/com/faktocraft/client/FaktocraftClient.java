package com.faktocraft.client;

import com.faktocraft.Faktocraft;
import com.faktocraft.client.model.ChargeRatioProperty;
import com.faktocraft.client.model.CoverBakedModel;
import com.faktocraft.client.model.CoverQuads;
import com.faktocraft.client.model.FluidTintSource;
import com.faktocraft.client.model.HazmatModel;
import com.faktocraft.client.model.ItemModelProperties;
import com.faktocraft.client.model.JetpackModel;
import com.faktocraft.client.render.BreakerRenderer;
import com.faktocraft.client.render.CableRenderer;
import com.faktocraft.client.render.ChunkBorderOverlay;
import com.faktocraft.client.render.DrillCrackOverlay;
import com.faktocraft.client.render.DrillHandAnimation;
import com.faktocraft.client.render.EnderTankRenderer;
import com.faktocraft.client.render.ExtractorRingRenderer;
import com.faktocraft.client.render.ExtractorSocketRenderer;
import com.faktocraft.client.render.FluidExtractorPipeRenderer;
import com.faktocraft.client.render.FluidFogVolume;
import com.faktocraft.client.render.FluidPipeRenderer;
import com.faktocraft.client.render.FluidSprites;
import com.faktocraft.client.render.GantryRenderer;
import com.faktocraft.client.render.LandmarkRenderer;
import com.faktocraft.client.render.PipeSupportRenderer;
import com.faktocraft.client.render.PumpRenderer;
import com.faktocraft.client.render.StatusClientBridges;
import com.faktocraft.client.render.StatusMonitorRenderer;
import com.faktocraft.client.render.TankRenderer;
import com.faktocraft.client.render.UraniumCentrifugeRenderer;
import com.faktocraft.client.render.WindRotorRenderer;
import com.faktocraft.common.block.impl.forester.ForesterRegistry;
import com.faktocraft.common.block.impl.machines.fueling_station.BlockEntityFuelingStation;
import com.faktocraft.common.block.impl.machines.fueling_station.FuelingStationRegistry;
import com.faktocraft.common.block.impl.monitor.MonitorRegistry;
import com.faktocraft.common.block.impl.monitor.StatusSources;
import com.faktocraft.common.block.impl.quarry.QuarryRegistry;
import com.faktocraft.common.cover.CoverSupport;
import com.faktocraft.common.cover.ICoverHost;
import com.faktocraft.common.fluid.ModFluids;
import com.faktocraft.common.item.impl.armor.JetpackItem;
import com.faktocraft.common.network.ClientPacketDispatch;
import com.faktocraft.common.network.ModNetworking;
import com.faktocraft.common.network.packet.PacketAnchorScreen;
import com.faktocraft.common.network.packet.PacketGeoScannerState;
import com.faktocraft.common.network.packet.PacketIEMeterInfo;
import com.faktocraft.common.network.packet.PacketJetpackInput;
import com.faktocraft.common.network.packet.PacketJetpackMode;
import com.faktocraft.common.network.packet.PacketLogisticsGhost;
import com.faktocraft.common.network.packet.PacketNightVision;
import com.faktocraft.common.network.packet.PacketParticle;
import com.faktocraft.common.network.packet.PacketProspectorState;
import com.faktocraft.common.network.packet.PacketRecipePipeRecipes;
import com.faktocraft.common.network.packet.PacketTableMessage;
import com.faktocraft.common.network.packet.PacketTableState;
import com.faktocraft.common.network.packet.PacketTeleportCharge;
import com.faktocraft.common.network.packet.PacketTeleportFx;
import com.faktocraft.common.network.packet.PacketWindInfo;
import com.faktocraft.common.registries.ModBlockEntities;
import com.faktocraft.common.registries.ModBlocks;
import com.faktocraft.common.registries.ModItems;
import com.faktocraft.common.registries.PipeRegistry;
import com.faktocraft.common.registries.machines.M1Registry;
import com.faktocraft.common.registries.machines.M3Registry;
import com.faktocraft.common.util.ClientProxy;
import com.faktocraft.common.util.RecipeUtil;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.color.block.BlockTintSources;
import net.minecraft.client.model.Model;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent;
import net.neoforged.neoforge.client.event.RegisterFluidModelsEvent;
import com.faktocraft.mixin.client.RegisterFluidModelsEventAccessor;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.neoforged.neoforge.client.extensions.common.IClientBlockExtensions;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.client.fluid.FluidTintSources;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@EventBusSubscriber(modid = Faktocraft.MODID, value = Dist.CLIENT)
public class FaktocraftClient {
  public static final KeyMapping.Category KEY_CATEGORY = new KeyMapping.Category(
      Identifier.fromNamespaceAndPath(Faktocraft.MODID, "main"));

  public static KeyMapping NIGHT_VISION_KEY;
  public static KeyMapping JETPACK_MODE_KEY;
  public static KeyMapping SLOT_IDS_KEY;
  public static KeyMapping VEIN_MINING_KEY;

  private static Set<ModFluids.FluidSet> translucentFluids() {
    return Set.of(ModFluids.COOLANT, ModFluids.BIOGAS, ModFluids.BIOMASS, ModFluids.MATTER,
        ModFluids.SULFURIC_ACID);
  }

  @SubscribeEvent
  public static void onClientSetup(FMLClientSetupEvent event) {
    ClientProxy.set(new FaktocraftClientProxy());
    registerPacketHandlers();
    for (ModFluids.FluidSet set : ModFluids.ALL) {
      ModFluids.ClientData data = set.clientData();
      FluidFogVolume.register(set.fluidType(), data.fogRed(), data.fogGreen(), data.fogBlue(), data.fogEnd());
    }
    ClientScreens.registerConfigScreen(event.getContainer());
  }

  private static void registerPacketHandlers() {
    ClientPacketDispatch.register(PacketParticle.class, ClientPacketHandlers::handleParticle);
    ClientPacketDispatch.register(PacketTeleportFx.class, ClientPacketHandlers::handleTeleportFx);
    ClientPacketDispatch.register(PacketTeleportCharge.class, ClientPacketHandlers::handleTeleportCharge);
    ClientPacketDispatch.register(PacketGeoScannerState.class, ClientPacketHandlers::handleGeoScannerState);
    ClientPacketDispatch.register(PacketProspectorState.class, ClientPacketHandlers::handleProspectorState);
    ClientPacketDispatch.register(PacketAnchorScreen.class, ClientPacketHandlers::handleAnchorScreen);
    ClientPacketDispatch.register(PacketIEMeterInfo.class, ClientPacketHandlers::handleIEMeterInfo);
    ClientPacketDispatch.register(PacketTableMessage.class, ClientPacketHandlers::handleTableMessage);
    ClientPacketDispatch.register(PacketRecipePipeRecipes.class, ClientPacketHandlers::handleRecipePipeRecipes);
    ClientPacketDispatch.register(PacketTableState.class, ClientPacketHandlers::handleTableState);
    ClientPacketDispatch.register(PacketWindInfo.class, ClientPacketHandlers::handleWindInfo);
    ClientPacketDispatch.register(PacketLogisticsGhost.class, LogisticsGhosts::add);
  }

  @SubscribeEvent
  public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
    ClientScreens.register(event);
  }

  @SubscribeEvent
  public static void onRegisterTooltipFactories(RegisterClientTooltipComponentFactoriesEvent event) {
    ClientScreens.registerTooltipFactories(event);
  }

  @SubscribeEvent
  public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
    CoverQuads.clear();
    Map<BlockState, BlockStateModel> models = event.getBakingResult().blockStateModels();
    List<BlockState> states = new ArrayList<>(models.keySet());
    for (BlockState state : states) {
      Block block = state.getBlock();
      boolean drilled = block == ModBlocks.DRILLED_BLOCK;
      if (drilled || CoverSupport.isCoverable(block)) {
        models.put(state, new CoverBakedModel(models.get(state), drilled));
      }
    }
  }

  @SubscribeEvent
  public static void onRegisterStandaloneModels(ModelEvent.RegisterStandalone event) {
    event.register(PipeSupportRenderer.CLAMP_KEY,
        SimpleUnbakedStandaloneModel.simpleModelWrapper(PipeSupportRenderer.CLAMP_MODEL));
    event.register(ExtractorSocketRenderer.SOCKET_KEY,
        SimpleUnbakedStandaloneModel.simpleModelWrapper(ExtractorSocketRenderer.SOCKET_MODEL));
    event.register(ExtractorRingRenderer.BAR_KEY,
        SimpleUnbakedStandaloneModel.simpleModelWrapper(ExtractorRingRenderer.BAR_MODEL));
    event.register(ExtractorRingRenderer.CORNERS_KEY,
        SimpleUnbakedStandaloneModel.simpleModelWrapper(ExtractorRingRenderer.CORNERS_MODEL));
    event.register(UraniumCentrifugeRenderer.DRUM_KEY,
        SimpleUnbakedStandaloneModel.simpleModelWrapper(UraniumCentrifugeRenderer.DRUM_MODEL));
    event.register(UraniumCentrifugeRenderer.DRUM_ACTIVE_KEY,
        SimpleUnbakedStandaloneModel.simpleModelWrapper(UraniumCentrifugeRenderer.DRUM_ACTIVE_MODEL));
    event.register(UraniumCentrifugeRenderer.SOCKET_CABLE_KEY,
        SimpleUnbakedStandaloneModel.simpleModelWrapper(UraniumCentrifugeRenderer.SOCKET_CABLE_MODEL));
  }

  @SubscribeEvent
  public static void onRegisterFluidModels(RegisterFluidModelsEvent event) {
    for (ModFluids.FluidSet set : ModFluids.ALL) {
      ModFluids.ClientData data = set.clientData();
      boolean translucent = translucentFluids().contains(set);
      FluidModel.Unbaked model = new FluidModel.Unbaked(
          new Material(data.stillTexture(), translucent),
          new Material(data.flowingTexture(), translucent),
          new Material(data.overlayTexture(), translucent),
          FluidTintSources.constant(data.tint()));
      event.register(model, set.still(), set.flowing());
      if (!translucent) {
        Map<Fluid, FluidModel> models = ((RegisterFluidModelsEventAccessor) (Object) event).faktocraftModels();
        FluidModel baked = models.get(set.still());
        FluidModel solid = new FluidModel(ChunkSectionLayer.SOLID, baked.stillMaterial(), baked.flowingMaterial(),
            baked.overlayMaterial(), baked.fluidTintSource(), baked.customRenderer());
        models.put(set.still(), solid);
        models.put(set.flowing(), solid);
      }
    }
  }

  @SubscribeEvent
  public static void onRegisterRangeProperties(RegisterRangeSelectItemModelPropertyEvent event) {
    event.register(ChargeRatioProperty.ID, ChargeRatioProperty.MAP_CODEC);
    event.register(ItemModelProperties.DOSE_ID, ItemModelProperties.Dose.MAP_CODEC);
    event.register(ItemModelProperties.PLUNGING_ID, ItemModelProperties.Plunging.MAP_CODEC);
  }

  @SubscribeEvent
  public static void onRegisterConditionalProperties(RegisterConditionalItemModelPropertyEvent event) {
    event.register(ItemModelProperties.ACTIVE_ID, ItemModelProperties.Active.MAP_CODEC);
    event.register(ItemModelProperties.FILLED_ID, ItemModelProperties.Filled.MAP_CODEC);
    event.register(ItemModelProperties.WORKING_ID, ItemModelProperties.Working.MAP_CODEC);
  }

  @SubscribeEvent
  public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
    event.registerLayerDefinition(JetpackModel.LAYER, JetpackModel::createLayer);
    event.registerLayerDefinition(HazmatModel.LAYER, HazmatModel::createLayer);
  }

  @SubscribeEvent
  public static void onRegisterItemTintSources(RegisterColorHandlersEvent.ItemTintSources event) {
    event.register(FluidTintSource.ID, FluidTintSource.MAP_CODEC);
  }

  @SubscribeEvent
  public static void onRegisterBlockTintSources(RegisterColorHandlersEvent.BlockTintSources event) {
    event.register(List.of(BlockTintSources.foliage()), ModBlocks.RUBBER_LEAVES);
    BlockTintSource fuelingTint = new BlockTintSource() {
      @Override
      public int color(BlockState state) {
        return 0xFFFFFFFF;
      }

      @Override
      public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof BlockEntityFuelingStation station && !station.tank.isEmpty()) {
          return 0xFF000000 | FluidSprites.tint(station.tank.getFluid());
        }
        return 0xFFFFFFFF;
      }
    };
    event.register(List.of(BlockTintSources.constant(0xFFFFFFFF), fuelingTint),
        FuelingStationRegistry.FUELING_STATION);
  }

  @SubscribeEvent
  public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
    for (ModFluids.FluidSet set : ModFluids.ALL) {
      event.registerFluidType(fluidExtensions(set.clientData()), set.fluidType());
    }

    event.registerItem(new IClientItemExtensions() {
      @Override
      public boolean applyForgeHandTransform(com.mojang.blaze3d.vertex.PoseStack poseStack,
          net.minecraft.client.renderer.state.level.PlayerRenderState playerRenderState,
          net.minecraft.world.entity.HumanoidArm arm, ItemStack itemInHand, float partialTick,
          float equipProcess, float swingProcess) {
        return com.faktocraft.client.render.ToolAimAnimation.apply(poseStack, arm, itemInHand, partialTick,
            equipProcess);
      }
    }, ModItems.MINING_DRILL, ModItems.DIAMOND_DRILL, ModItems.IRIDIUM_DRILL, ModItems.CHAINSAW,
        ModItems.DIAMOND_CHAINSAW, ModItems.IRIDIUM_CHAINSAW);

    event.registerItem(new IClientItemExtensions() {
      @Override
      @SuppressWarnings("rawtypes")
      public Model<?> getHumanoidArmorModel(ItemStack stack, EquipmentClientInfo.LayerType layerType,
          Model original) {
        return layerType == EquipmentClientInfo.LayerType.HUMANOID_LEGGINGS ? original
            : HazmatModel.get(slotOf(stack));
      }
    }, ModItems.HAZMAT_HELMET, ModItems.HAZMAT_CHESTPLATE, ModItems.HAZMAT_LEGGINGS, ModItems.HAZMAT_BOOTS);

    event.registerItem(new IClientItemExtensions() {
      @Override
      @SuppressWarnings("rawtypes")
      public Model<?> getHumanoidArmorModel(ItemStack stack, EquipmentClientInfo.LayerType layerType,
          Model original) {
        return JetpackModel.get();
      }
    }, ModItems.JETPACK, ModItems.ADVANCED_JETPACK);

    event.registerItem(new IClientItemExtensions() {
      @Override
      public boolean applyForgeHandTransform(PoseStack poseStack, PlayerRenderState playerState, HumanoidArm arm,
          ItemStack stack, float partialTick, float equipProcess, float swingProcess) {
        return DrillHandAnimation.apply(poseStack, playerState, arm, stack, partialTick, equipProcess);
      }
    }, ModItems.HOLE_DRILL);

    IClientBlockExtensions coverTints = new IClientBlockExtensions() {
      @Override
      public void collectDynamicTintValues(BlockState state, BlockAndTintGetter level, BlockPos pos,
          IntList tintValues) {
        BlockState cover = level.getBlockEntity(pos) instanceof ICoverHost host ? host.getCover() : null;
        if (cover == null) {
          return;
        }
        List<BlockTintSource> sources = Minecraft.getInstance().getBlockColors().getTintSources(cover);
        if (sources.isEmpty()) {
          IClientBlockExtensions.of(cover).collectDynamicTintValues(cover, level, pos, tintValues);
          return;
        }
        for (BlockTintSource source : sources) {
          tintValues.add(source.colorInWorld(cover, level, pos));
        }
      }
    };
    for (Block block : net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
      if ((block == ModBlocks.DRILLED_BLOCK || CoverSupport.isCoverable(block)) && !event.isBlockRegistered(block)) {
        event.registerBlock(coverTints, block);
      }
    }
  }

  private static EquipmentSlot slotOf(ItemStack stack) {
    if (stack.getItem() == ModItems.HAZMAT_HELMET) {
      return EquipmentSlot.HEAD;
    }
    if (stack.getItem() == ModItems.HAZMAT_CHESTPLATE) {
      return EquipmentSlot.CHEST;
    }
    if (stack.getItem() == ModItems.HAZMAT_LEGGINGS) {
      return EquipmentSlot.LEGS;
    }
    return EquipmentSlot.FEET;
  }

  private static IClientFluidTypeExtensions fluidExtensions(ModFluids.ClientData data) {
    return new IClientFluidTypeExtensions() {
      @Override
      public Identifier getRenderOverlayTexture(Minecraft mc) {
        return ModFluids.UNDERWATER_OVERLAY;
      }

      @Override
      public void modifyFogColor(Camera camera, float partialTick, ClientLevel level, int renderDistance,
          float darkenWorldAmount, Vector4f fluidFogColor) {
        fluidFogColor.set(data.fogRed(), data.fogGreen(), data.fogBlue(), fluidFogColor.w);
      }

      @Override
      public void modifyFogRender(Camera camera, @Nullable FogEnvironment environment, float renderDistance,
          float partialTick, FogData fogData) {
        fogData.environmentalStart = 0.0F;
        fogData.environmentalEnd = data.fogEnd();
        fogData.skyEnd = data.fogEnd();
        fogData.cloudEnd = data.fogEnd();
      }
    };
  }

  @SubscribeEvent
  public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
    event.registerCategory(KEY_CATEGORY);
    NIGHT_VISION_KEY = new KeyMapping("key.faktocraft.night_vision", InputConstants.KEY_C, KEY_CATEGORY);
    event.register(NIGHT_VISION_KEY);
    JETPACK_MODE_KEY = new KeyMapping("key.faktocraft.jetpack_mode", InputConstants.KEY_J, KEY_CATEGORY);
    event.register(JETPACK_MODE_KEY);
    SLOT_IDS_KEY = new KeyMapping("key.faktocraft.slot_ids", InputConstants.KEY_F9, KEY_CATEGORY);
    event.register(SLOT_IDS_KEY);
    VEIN_MINING_KEY = new KeyMapping("key.faktocraft.vein_mining", InputConstants.KEY_LALT, KEY_CATEGORY);
    event.register(VEIN_MINING_KEY);
  }

  @SubscribeEvent
  public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
    event.registerAboveAll(TeleportFxOverlay.ID, new TeleportFxOverlay());
  }

  @SubscribeEvent
  public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
    event.registerBlockEntityRenderer(ModBlockEntities.CABLE, context -> new CableRenderer());
    event.registerBlockEntityRenderer(PipeRegistry.FLUID_EXTRACTOR_PIPE_BLOCK_ENTITY,
        context -> new FluidExtractorPipeRenderer());
    event.registerBlockEntityRenderer(PipeRegistry.PUMP_BLOCK_ENTITY, context -> new PumpRenderer());
    event.registerBlockEntityRenderer(QuarryRegistry.QUARRY_BLOCK_ENTITY, context -> new GantryRenderer<>());
    event.registerBlockEntityRenderer(ForesterRegistry.FORESTER_BLOCK_ENTITY, context -> new GantryRenderer<>());
    event.registerBlockEntityRenderer(QuarryRegistry.LANDMARK_BLOCK_ENTITY, context -> new LandmarkRenderer());
    event.registerBlockEntityRenderer(PipeRegistry.TANK_BLOCK_ENTITY, context -> new TankRenderer());
    event.registerBlockEntityRenderer(PipeRegistry.ENDER_TANK_BLOCK_ENTITY, EnderTankRenderer::new);
    event.registerBlockEntityRenderer(MonitorRegistry.STATUS_MONITOR_BLOCK_ENTITY, StatusMonitorRenderer::new);
    StatusClientBridges.init();
    StatusSources.setFluidColorProvider(fluid -> 0xFF000000 | FluidSprites.tint(fluid));
    event.registerBlockEntityRenderer(PipeRegistry.FLUID_PIPE_BLOCK_ENTITY, context -> new FluidPipeRenderer());
    event.registerBlockEntityRenderer(M1Registry.WIND_GENERATOR_BE, context -> new WindRotorRenderer());
    event.registerBlockEntityRenderer(ModBlockEntities.BREAKER, context -> new BreakerRenderer());
    event.registerBlockEntityRenderer(M3Registry.URANIUM_CENTRIFUGE_BLOCK_ENTITY,
        context -> new UraniumCentrifugeRenderer());
  }

  @SubscribeEvent(priority = EventPriority.HIGH)
  public static void onRecipesReceived(RecipesReceivedEvent event) {
    RecipeUtil.setClientRecipes(event.getRecipeMap());
  }

  @SubscribeEvent
  public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
    UpdateNotice.onLogin();
  }

  @SubscribeEvent
  public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
    RecipeUtil.setClientRecipes(RecipeMap.EMPTY);
    ChunkBorderOverlay.reset();
    ToolWorkAnimation.clear();
    VeinMiningKey.clear();
  }

  @SubscribeEvent
  public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
    LogisticsGhosts.submit(event);
    ChunkBorderOverlay.submit(event);
    FluidFogVolume.submit(event);
    DrillCrackOverlay.submit(event);
  }

  @SubscribeEvent
  public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
    CoverBreakRestart.onLeftClickBlock(event);
  }

  @SubscribeEvent
  public static void onScreenOpening(ScreenEvent.Opening event) {
    ChunkBorderOverlay.onScreenOpening(event);
  }

  @SubscribeEvent
  public static void onRenderGui(RenderGuiEvent.Post event) {
    ChunkBorderOverlay.renderHud(event.getGuiGraphics());
  }

  @SubscribeEvent
  public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
    SlotIdOverlay.onKeyPressed(event, SLOT_IDS_KEY);
  }

  @SubscribeEvent
  public static void onScreenForeground(ScreenEvent.Render.Foreground event) {
    SlotIdOverlay.onRender(event);
  }

  @SubscribeEvent
  public static void onClientTick(ClientTickEvent.Post event) {
    Minecraft minecraft = Minecraft.getInstance();
    NanoSaberSoundHandler.tick(minecraft);
    JetpackSoundHandler.tick(minecraft);
    LogisticsGhosts.tick(minecraft);
    DrillCrackOverlay.tick(minecraft);
    ToolWorkAnimation.tick(minecraft);
    VeinMiningKey.tick(minecraft);
    LocalPlayer player = minecraft.player;
    UpdateNotice.tick(player);
    if (player != null && minecraft.level != null && !minecraft.isPaused()) {
      ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
      if (chest.getItem() instanceof JetpackItem jetpack) {
        jetpack.wornTick(chest, minecraft.level, player);
      }
    }
    if (NIGHT_VISION_KEY == null) {
      return;
    }
    while (NIGHT_VISION_KEY.consumeClick()) {
      if (player != null) {
        ModNetworking.sendToServer(PacketNightVision.INSTANCE);
      }
    }
    if (JETPACK_MODE_KEY != null) {
      while (JETPACK_MODE_KEY.consumeClick()) {
        if (player != null) {
          ModNetworking.sendToServer(PacketJetpackMode.INSTANCE);
        }
      }
    }
    if (player != null) {
      boolean thrust = minecraft.options.keyJump.isDown() && JetpackItem.isWearingJetpack(player);
      boolean prev = player.getPersistentData().getBooleanOr(JetpackItem.TAG_THRUST, false);
      if (thrust != prev) {
        player.getPersistentData().putBoolean(JetpackItem.TAG_THRUST, thrust);
        ModNetworking.sendToServer(new PacketJetpackInput(thrust));
      }
    }
  }
}
