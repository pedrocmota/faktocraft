package com.faktocraft.client;

import com.faktocraft.Faktocraft;
import com.faktocraft.client.model.ChargeRatioProperty;
import com.faktocraft.client.model.FluidTintSource;
import com.faktocraft.client.render.FluidPipeRenderer;
import com.faktocraft.client.render.PumpRenderer;
import com.faktocraft.client.render.TankRenderer;
import com.faktocraft.client.screens.M1Screens;
import com.faktocraft.client.screens.M2Screens;
import com.faktocraft.client.screens.M3Screens;
import com.faktocraft.client.screens.M4Screens;
import com.faktocraft.common.block.impl.machines.fueling_station.BlockEntityFuelingStation;
import com.faktocraft.common.block.impl.machines.fueling_station.FuelingStationRegistry;
import com.faktocraft.common.fluid.ModFluids;
import com.faktocraft.common.network.ModNetworking;
import com.faktocraft.common.network.packet.PacketNightVision;
import com.faktocraft.common.registries.ModBlocks;
import com.faktocraft.common.registries.ModComponents;
import com.faktocraft.common.registries.ModComponentsFluids;
import com.faktocraft.common.registries.ModItems;
import com.faktocraft.common.registries.PipeRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.FoliageColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = Faktocraft.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class FaktocraftClient {

  public static KeyMapping NIGHT_VISION_KEY;
  public static KeyMapping JETPACK_MODE_KEY;
  public static KeyMapping SLOT_IDS_KEY;
  public static KeyMapping VEIN_MINING_KEY;

  @SubscribeEvent
  public static void onModifyBakingResult(net.minecraftforge.client.event.ModelEvent.ModifyBakingResult event) {
    com.faktocraft.client.model.CoverQuads.clear();
    java.util.Map<ResourceLocation, net.minecraft.client.resources.model.BakedModel> models = event.getModels();
    java.util.List<ResourceLocation> keys = new java.util.ArrayList<>(models.keySet());
    for (ResourceLocation key : keys) {
      if (!(key instanceof net.minecraft.client.resources.model.ModelResourceLocation location)
          || "inventory".equals(location.getVariant())) {
        continue;
      }
      net.minecraft.world.level.block.Block block = net.minecraftforge.registries.ForgeRegistries.BLOCKS
          .getValue(new ResourceLocation(location.getNamespace(), location.getPath()));
      if (block == null) {
        continue;
      }
      boolean drilled = block == ModBlocks.DRILLED_BLOCK;
      if (drilled || com.faktocraft.common.cover.CoverSupport.isCoverable(block)) {
        models.put(key, new com.faktocraft.client.model.CoverBakedModel(models.get(key), drilled));
      }
    }
  }

  @SubscribeEvent
  public static void onRegisterAdditionalModels(net.minecraftforge.client.event.ModelEvent.RegisterAdditional event) {
    event.register(com.faktocraft.client.render.PipeSupportRenderer.CLAMP_MODEL);
    event.register(com.faktocraft.client.render.ExtractorSocketRenderer.SOCKET_MODEL);
    event.register(com.faktocraft.client.render.ExtractorRingRenderer.BAR_MODEL);
    event.register(com.faktocraft.client.render.ExtractorRingRenderer.CORNERS_MODEL);
    event.register(com.faktocraft.client.render.UraniumCentrifugeRenderer.DRUM_MODEL);
    event.register(com.faktocraft.client.render.UraniumCentrifugeRenderer.DRUM_ACTIVE_MODEL);
    event.register(com.faktocraft.client.render.UraniumCentrifugeRenderer.SOCKET_CABLE_MODEL);
  }

  @SubscribeEvent
  public static void onClientSetup(FMLClientSetupEvent event) {

    net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(
        net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
        () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
            (mc, parent) -> new com.faktocraft.client.screens.BasicConfigScreen(parent)));
    event.enqueueWork(() -> {
      MenuScreens.register(PipeRegistry.EXTRACTOR_PIPE_MENU,
          com.faktocraft.common.block.impl.pipe.ScreenExtractorPipe::new);
      MenuScreens.register(PipeRegistry.PUMP_MENU,
          com.faktocraft.common.block.impl.pipe.ScreenPump::new);
      MenuScreens.register(PipeRegistry.ENDER_TANK_MENU,
          com.faktocraft.common.block.impl.pipe.ScreenEnderTank::new);
      MenuScreens.register(com.faktocraft.common.block.impl.quarry.QuarryRegistry.QUARRY_MENU,
          com.faktocraft.common.block.impl.quarry.ScreenQuarry::new);
      MenuScreens.register(com.faktocraft.common.block.impl.forester.ForesterRegistry.FORESTER_MENU,
          com.faktocraft.common.block.impl.forester.ScreenForester::new);
      MenuScreens.register(com.faktocraft.common.block.impl.logistics.LogisticsRegistry.CHASSIS_MENU,
          com.faktocraft.common.block.impl.logistics.ScreenChassis::new);
      MenuScreens.register(com.faktocraft.common.block.impl.logistics.LogisticsRegistry.MODULE_MENU,
          com.faktocraft.common.block.impl.logistics.ScreenModule::new);
      MenuScreens.register(com.faktocraft.common.block.impl.logistics.LogisticsRegistry.LOGISTICS_CONTROLLER_MENU,
          com.faktocraft.common.block.impl.logistics.ScreenLogisticsController::new);
      MenuScreens.register(com.faktocraft.common.block.impl.logistics.LogisticsRegistry.REQUEST_TABLE_MENU,
          com.faktocraft.common.block.impl.logistics.ScreenRequestTable::new);
      MenuScreens.register(com.faktocraft.common.block.impl.logistics.LogisticsRegistry.ASSEMBLY_TABLE_MENU,
          com.faktocraft.common.block.impl.logistics.ScreenAssemblyTable::new);
      MenuScreens.register(com.faktocraft.common.block.impl.logistics.LogisticsRegistry.CRAFT_PIPE_MENU,
          com.faktocraft.common.block.impl.logistics.ScreenCraftPipe::new);
      MenuScreens.register(com.faktocraft.common.block.impl.logistics.LogisticsRegistry.RECIPE_PIPE_MENU,
          com.faktocraft.common.block.impl.logistics.ScreenRecipePipe::new);
      MenuScreens.register(com.faktocraft.common.block.impl.logistics.LogisticsRegistry.CORE_TASKS_MENU,
          com.faktocraft.common.block.impl.logistics.ScreenCoreTasks::new);
      M1Screens.register();
      M2Screens.register();
      M3Screens.register();
      M4Screens.register();

      ChargeRatioProperty.register();
      registerItemProperties();

      registerFluidRenderLayers();
    });
  }

  private static void registerItemProperties() {
    ItemProperties.register(ModItems.NANO_SABER, new ResourceLocation(Faktocraft.MODID, "active"),
        (stack, level, entity, seed) -> ModComponents.getActive(stack, false) ? 1.0f : 0.0f);

    ItemProperties.register(ModItems.FLUID_CELL, new ResourceLocation(Faktocraft.MODID, "filled"),
        (stack, level, entity, seed) -> ModComponentsFluids.hasFluid(stack) ? 1.0f : 0.0f);

    ItemProperties.register(ModItems.GEIGER_COUNTER, new ResourceLocation(Faktocraft.MODID, "dose"),
        (stack, level, entity, seed) -> com.faktocraft.common.item.impl.tools.GeigerCounter.doseLevel(stack));

    ResourceLocation working = new ResourceLocation(Faktocraft.MODID, "working");
    for (net.minecraft.world.item.Item tool : new net.minecraft.world.item.Item[] { ModItems.MINING_DRILL,
        ModItems.DIAMOND_DRILL, ModItems.IRIDIUM_DRILL, ModItems.CHAINSAW, ModItems.DIAMOND_CHAINSAW,
        ModItems.IRIDIUM_CHAINSAW }) {
      ItemProperties.register(tool, working,
          (stack, level, entity, seed) -> ToolWorkAnimation.working(stack, entity));
    }

    ItemProperties.register(ModItems.PLUNGER, new ResourceLocation(Faktocraft.MODID, "plunging"),
        (stack, level, entity, seed) -> {
          if (entity == null || !entity.isUsingItem() || entity.getUseItem().getItem() != stack.getItem()) {
            return 0.0f;
          }
          return entity.getTicksUsingItem() % 8 < 4 ? 1.0f : 0.5f;
        });
  }

  private static void registerFluidRenderLayers() {
    for (ModFluids.FluidSet set : new ModFluids.FluidSet[] {
        ModFluids.COOLANT,
        ModFluids.BIOGAS,
        ModFluids.BIOMASS,
        ModFluids.MATTER,
        ModFluids.SULFURIC_ACID }) {
      ItemBlockRenderTypes.setRenderLayer(set.still(), RenderType.translucent());
      ItemBlockRenderTypes.setRenderLayer(set.flowing(), RenderType.translucent());
    }
  }

  @SubscribeEvent
  public static void onRegisterTooltipFactories(
      net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent event) {
    event.register(com.faktocraft.common.item.impl.tools.ToolboxTooltip.class, ClientToolboxTooltip::new);
  }

  @SubscribeEvent
  public static void onRegisterLayerDefinitions(
      net.minecraftforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions event) {
    event.registerLayerDefinition(com.faktocraft.client.model.JetpackModel.LAYER,
        com.faktocraft.client.model.JetpackModel::createLayer);
    event.registerLayerDefinition(com.faktocraft.client.model.HazmatModel.LAYER,
        com.faktocraft.client.model.HazmatModel::createLayer);
  }

  @SubscribeEvent
  public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
    NIGHT_VISION_KEY = new KeyMapping("key.faktocraft.night_vision", GLFW.GLFW_KEY_C,
        "key.categories.faktocraft.main");
    event.register(NIGHT_VISION_KEY);
    JETPACK_MODE_KEY = new KeyMapping("key.faktocraft.jetpack_mode", GLFW.GLFW_KEY_J,
        "key.categories.faktocraft.main");
    event.register(JETPACK_MODE_KEY);
    SLOT_IDS_KEY = new KeyMapping("key.faktocraft.slot_ids", GLFW.GLFW_KEY_F9,
        "key.categories.faktocraft.main");
    event.register(SLOT_IDS_KEY);
    VEIN_MINING_KEY = new KeyMapping("key.faktocraft.vein_mining", GLFW.GLFW_KEY_LEFT_ALT,
        "key.categories.faktocraft.main");
    event.register(VEIN_MINING_KEY);
  }

  @SubscribeEvent
  public static void onRegisterGuiOverlays(RegisterGuiOverlaysEvent event) {
    event.registerAboveAll(TeleportFxOverlay.ID, new TeleportFxOverlay());
  }

  @SubscribeEvent
  public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
    event.registerBlockEntityRenderer(com.faktocraft.common.registries.ModBlockEntities.CABLE,
        context -> new com.faktocraft.client.render.CableRenderer());
    event.registerBlockEntityRenderer(PipeRegistry.FLUID_EXTRACTOR_PIPE_BLOCK_ENTITY,
        context -> new com.faktocraft.client.render.FluidExtractorPipeRenderer());
    event.registerBlockEntityRenderer(PipeRegistry.PUMP_BLOCK_ENTITY, context -> new PumpRenderer());
    event.registerBlockEntityRenderer(com.faktocraft.common.block.impl.quarry.QuarryRegistry.QUARRY_BLOCK_ENTITY,
        context -> new com.faktocraft.client.render.GantryRenderer<>());
    event.registerBlockEntityRenderer(
        com.faktocraft.common.block.impl.forester.ForesterRegistry.FORESTER_BLOCK_ENTITY,
        context -> new com.faktocraft.client.render.GantryRenderer<>());
    event.registerBlockEntityRenderer(com.faktocraft.common.block.impl.quarry.QuarryRegistry.LANDMARK_BLOCK_ENTITY,
        context -> new com.faktocraft.client.render.LandmarkRenderer());
    event.registerBlockEntityRenderer(PipeRegistry.TANK_BLOCK_ENTITY, context -> new TankRenderer());
    event.registerBlockEntityRenderer(PipeRegistry.ENDER_TANK_BLOCK_ENTITY,
        com.faktocraft.client.render.EnderTankRenderer::new);
    event.registerBlockEntityRenderer(
        com.faktocraft.common.block.impl.monitor.MonitorRegistry.STATUS_MONITOR_BLOCK_ENTITY,
        com.faktocraft.client.render.StatusMonitorRenderer::new);
    com.faktocraft.client.render.StatusClientBridges.init();
    com.faktocraft.common.block.impl.monitor.StatusSources.setFluidColorProvider(fluid -> 0xFF000000
        | net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid).getTintColor());
    event.registerBlockEntityRenderer(PipeRegistry.FLUID_PIPE_BLOCK_ENTITY, context -> new FluidPipeRenderer());
    event.registerBlockEntityRenderer(com.faktocraft.common.registries.machines.M1Registry.WIND_GENERATOR_BE,
        context -> new com.faktocraft.client.render.WindRotorRenderer());
    event.registerBlockEntityRenderer(com.faktocraft.common.registries.ModBlockEntities.BREAKER,
        context -> new com.faktocraft.client.render.BreakerRenderer());
    event.registerBlockEntityRenderer(
        com.faktocraft.common.registries.machines.M3Registry.URANIUM_CENTRIFUGE_BLOCK_ENTITY,
        context -> new com.faktocraft.client.render.UraniumCentrifugeRenderer());
  }

  @SubscribeEvent
  public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
    event.register(FluidTintSource.INSTANCE, ModItems.FLUID_CELL);
    event.register((stack, tintIndex) -> tintIndex > 0 ? -1
        : ((net.minecraft.world.item.DyeableLeatherItem) stack.getItem()).getColor(stack),
        ModItems.HAZMAT_HELMET, ModItems.HAZMAT_CHESTPLATE, ModItems.HAZMAT_LEGGINGS, ModItems.HAZMAT_BOOTS);
  }

  @SubscribeEvent
  public static void onRegisterBlockColors(RegisterColorHandlersEvent.Block event) {
    BlockColor coverColor = (state, getter, pos, tintIndex) -> {
      if (getter == null || pos == null) {
        return -1;
      }
      net.minecraft.world.level.block.state.BlockState cover = getter
          .getBlockEntity(pos) instanceof com.faktocraft.common.cover.ICoverHost host ? host.getCover() : null;
      return cover == null ? -1 : Minecraft.getInstance().getBlockColors().getColor(cover, getter, pos, tintIndex);
    };
    net.minecraft.world.level.block.Block[] coverBlocks = net.minecraftforge.registries.ForgeRegistries.BLOCKS
        .getValues().stream()
        .filter(block -> block == ModBlocks.DRILLED_BLOCK
            || com.faktocraft.common.cover.CoverSupport.isCoverable(block))
        .toArray(net.minecraft.world.level.block.Block[]::new);
    event.register(coverColor, coverBlocks);
    BlockColor foliage = (state, level, pos, tintIndex) -> level != null && pos != null
        ? BiomeColors.getAverageFoliageColor(level, pos)
        : FoliageColor.getDefaultColor();
    event.register(foliage, ModBlocks.RUBBER_LEAVES);
    event.register((state, getter, pos, tintIndex) -> {
      if (tintIndex == 1 && getter != null && pos != null
          && getter.getBlockEntity(
              pos) instanceof BlockEntityFuelingStation station
          && !station.tank.isEmpty()) {
        int tint = net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions
            .of(station.tank.getFluid()).getTintColor();
        return 0xFF000000 | tint;
      }
      return 0xFFFFFFFF;
    }, FuelingStationRegistry.FUELING_STATION);
  }

  @Mod.EventBusSubscriber(modid = Faktocraft.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
  public static class ForgeEvents {

    private static net.minecraft.client.gui.components.AbstractSliderButton machineSlider;

    @SubscribeEvent
    public static void onScreenInit(net.minecraftforge.client.event.ScreenEvent.Init.Post event) {
      machineSlider = null;
      if (!(event.getScreen() instanceof net.minecraft.client.gui.screens.SoundOptionsScreen)) {
        return;
      }
      double initial = ClientSoundConfig.machineVolume();
      machineSlider = new net.minecraft.client.gui.components.AbstractSliderButton(
          0, -1000, 150, 20, machineVolumeLabel(initial), initial) {
        @Override
        protected void updateMessage() {
          setMessage(machineVolumeLabel(this.value));
        }

        @Override
        protected void applyValue() {
          ClientSoundConfig.setMachineVolume((float) this.value);
        }
      };
      machineSlider.visible = false;
      event.addListener(machineSlider);
    }

    @SubscribeEvent
    public static void onScreenRender(net.minecraftforge.client.event.ScreenEvent.Render.Pre event) {
      if (machineSlider == null
          || !(event.getScreen() instanceof net.minecraft.client.gui.screens.SoundOptionsScreen screen)) {
        return;
      }
      net.minecraft.client.gui.components.AbstractSliderButton anchor = null;
      for (var child : screen.children()) {
        if (child instanceof net.minecraft.client.gui.components.OptionsList list) {
          for (net.minecraft.client.gui.components.events.GuiEventListener row : list.children()) {
            if (!(row instanceof net.minecraft.client.gui.components.events.ContainerEventHandler container)) {
              continue;
            }
            for (var w : container.children()) {
              if (w instanceof net.minecraft.client.gui.components.AbstractSliderButton slider
                  && slider.getX() < screen.width / 2
                  && (anchor == null || slider.getY() > anchor.getY())) {
                anchor = slider;
              }
            }
          }
        }
      }
      if (anchor == null || anchor.getY() <= 0) {
        machineSlider.visible = false;
        return;
      }
      machineSlider.setX(anchor.getX() + 160);
      machineSlider.setY(anchor.getY());
      machineSlider.visible = true;
    }

    private static net.minecraft.network.chat.Component machineVolumeLabel(double value) {
      String pct = value <= 0 ? net.minecraft.client.resources.language.I18n.get("options.off")
          : (int) (value * 100) + "%";
      return net.minecraft.network.chat.Component
          .translatable("options." + Faktocraft.MODID + ".machine_volume")
          .append(": " + pct);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(net.minecraftforge.client.event.RenderLevelStageEvent event) {
      LogisticsGhosts.render(event);
      com.faktocraft.client.render.ChunkBorderOverlay.render(event);
      com.faktocraft.client.render.FluidFogVolume.render(event);
      com.faktocraft.client.render.DrillCrackOverlay.render(event);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(
        net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock event) {
      CoverBreakRestart.onLeftClickBlock(event);
    }

    @SubscribeEvent
    public static void onScreenOpening(net.minecraftforge.client.event.ScreenEvent.Opening event) {
      com.faktocraft.client.render.ChunkBorderOverlay.onScreenOpening(event);
    }

    @SubscribeEvent
    public static void onLoggingIn(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
      UpdateNotice.onLogin();
    }

    @SubscribeEvent
    public static void onLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
      com.faktocraft.client.render.ChunkBorderOverlay.reset();
      ToolWorkAnimation.clear();
      VeinMiningKey.clear();
    }

    @SubscribeEvent
    public static void onRenderGui(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
      com.faktocraft.client.render.ChunkBorderOverlay.renderHud(event.getGuiGraphics());
    }

    @SubscribeEvent
    public static void onScreenKeyPressed(net.minecraftforge.client.event.ScreenEvent.KeyPressed.Pre event) {
      SlotIdOverlay.onKeyPressed(event, SLOT_IDS_KEY);
    }

    @SubscribeEvent
    public static void onContainerForeground(
        net.minecraftforge.client.event.ContainerScreenEvent.Render.Foreground event) {
      SlotIdOverlay.onRender(event);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase != TickEvent.Phase.END) {
        return;
      }
      NanoSaberSoundHandler.tick(Minecraft.getInstance());
      JetpackSoundHandler.tick(Minecraft.getInstance());
      LogisticsGhosts.tick(Minecraft.getInstance());
      com.faktocraft.client.render.DrillCrackOverlay.tick(Minecraft.getInstance());
      ToolWorkAnimation.tick(Minecraft.getInstance());
      VeinMiningKey.tick(Minecraft.getInstance());
      UpdateNotice.tick(Minecraft.getInstance().player);
      if (NIGHT_VISION_KEY == null) {
        return;
      }
      Minecraft minecraft = Minecraft.getInstance();
      while (NIGHT_VISION_KEY.consumeClick()) {
        if (minecraft.player != null) {
          ModNetworking.sendToServer(PacketNightVision.INSTANCE);
        }
      }
      if (JETPACK_MODE_KEY != null) {
        while (JETPACK_MODE_KEY.consumeClick()) {
          if (minecraft.player != null) {
            ModNetworking.sendToServer(
                com.faktocraft.common.network.packet.PacketJetpackMode.INSTANCE);
          }
        }
      }
      if (minecraft.player != null) {
        boolean thrust = minecraft.options.keyJump.isDown()
            && com.faktocraft.common.item.impl.armor.JetpackItem.isWearingJetpack(minecraft.player);
        boolean prev = minecraft.player.getPersistentData()
            .getBoolean(com.faktocraft.common.item.impl.armor.JetpackItem.TAG_THRUST);
        if (thrust != prev) {
          minecraft.player.getPersistentData()
              .putBoolean(com.faktocraft.common.item.impl.armor.JetpackItem.TAG_THRUST, thrust);
          ModNetworking.sendToServer(
              new com.faktocraft.common.network.packet.PacketJetpackInput(thrust));
        }
      }
    }
  }
}
