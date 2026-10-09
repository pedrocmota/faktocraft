package com.faktocraft.common.item.impl;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.item.base.BaseItem;
import com.faktocraft.common.recipe.impl.ScrapBoxRecipe;
import com.faktocraft.common.registries.ModRecipeType;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProvider;

public class ScrapBox extends BaseItem {
  public static final ResourceKey<ContextIntProvider> BURN_TIME = ResourceKey.create(Registries.CONTEXT_INT_PROVIDER,
      Identifier.fromNamespaceAndPath(Faktocraft.MODID, "cooking/time_scrap_box"));

  public ScrapBox(Properties properties) {
    super(properties.cookingFuel(BURN_TIME));
  }

  public static ItemStack openScrap(Level level) {
    if (!(level instanceof ServerLevel serverLevel)) {
      return ItemStack.EMPTY;
    }
    List<ScrapBoxRecipe> recipes = serverLevel.recipeAccess().recipeMap().byType(ModRecipeType.SCRAP_BOX).stream()
        .map(RecipeHolder::value)
        .toList();
    return ScrapBoxRecipe.rollDrop(recipes, level.getRandom());
  }

  @Override
  public InteractionResult use(Level level, Player player, InteractionHand hand) {
    if (!level.isClientSide()) {
      ItemStack drop = openScrap(level);
      if (!drop.isEmpty()) {
        ItemEntity item = new ItemEntity(level, player.getX(), player.getY(), player.getZ(), drop);
        level.addFreshEntity(item);
      }
      player.getItemInHand(hand).shrink(1);
    }
    return InteractionResult.SUCCESS;
  }

  private static final DispenseItemBehavior SCRAP_BOX_DISPENSE_BEHAVIOR = (BlockSource source, ItemStack stack) -> {
    Direction face = source.state().getValue(DispenserBlock.FACING);
    ServerLevel level = source.level();
    BlockPos pos = source.pos().relative(face);

    ItemStack dropStack = openScrap(level);
    if (!dropStack.isEmpty()) {
      ItemEntity item = new ItemEntity(level, pos.getX(), pos.getY(), pos.getZ(), dropStack);
      level.addFreshEntity(item);
    }
    level.playSound(null, pos, SoundEvents.DISPENSER_DISPENSE, SoundSource.BLOCKS, 1.0F, 1.0F);

    stack.shrink(1);
    level.gameEvent(null, GameEvent.ENTITY_PLACE, source.pos());
    return stack;
  };

  public static void registerDispenseBehavior(ScrapBox item) {
    DispenserBlock.registerBehavior(item, SCRAP_BOX_DISPENSE_BEHAVIOR);
  }
}
