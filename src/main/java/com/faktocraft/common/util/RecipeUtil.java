package com.faktocraft.common.util;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.registries.ModRecipeType;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeMap;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.stream.Stream;

@EventBusSubscriber(modid = Faktocraft.MODID)
public final class RecipeUtil {
  private static volatile RecipeMap clientRecipes = RecipeMap.EMPTY;
  private static final Map<Recipe<?>, Identifier> KNOWN_IDS = Collections.synchronizedMap(new WeakHashMap<>());

  private RecipeUtil() {
  }

  public static <T extends Recipe<?>> RecipeHolder<T> remember(RecipeHolder<T> holder) {
    KNOWN_IDS.put(holder.value(), holder.id().identifier());
    return holder;
  }

  private static <T extends Recipe<?>> Optional<RecipeHolder<T>> remember(Optional<RecipeHolder<T>> holder) {
    holder.ifPresent(RecipeUtil::remember);
    return holder;
  }

  public static void setClientRecipes(RecipeMap recipes) {
    clientRecipes = recipes;
  }

  public static RecipeMap clientRecipes() {
    return clientRecipes;
  }

  @SubscribeEvent
  public static void onDatapackSync(OnDatapackSyncEvent event) {
    event.sendRecipes(ModRecipeType.ALL);
    event.sendRecipes(RecipeType.CRAFTING);
    event.sendRecipes(RecipeType.SMELTING);
  }

  @Nullable
  private static RecipeMap recipeMap(@Nullable Level level) {
    if (level instanceof ServerLevel serverLevel) {
      return serverLevel.recipeAccess().recipeMap();
    }
    if (level != null && level.isClientSide()) {
      return clientRecipes;
    }
    return null;
  }

  public static <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> getRecipeFor(
      @Nullable Level level, RecipeType<T> type, I input) {
    if (level instanceof ServerLevel serverLevel) {
      return remember(serverLevel.recipeAccess().getRecipeFor(type, input, level));
    }
    RecipeMap map = recipeMap(level);
    if (map == null) {
      return Optional.empty();
    }
    return remember(map.getRecipesFor(type, input, level).findFirst());
  }

  public static <I extends RecipeInput, T extends Recipe<I>> Optional<T> findRecipe(@Nullable Level level,
      RecipeType<T> type, I input) {
    return getRecipeFor(level, type, input).map(RecipeHolder::value);
  }

  public static <I extends RecipeInput, T extends Recipe<I>> List<RecipeHolder<T>> getAllRecipeHoldersFor(
      @Nullable Level level, RecipeType<T> type) {
    RecipeMap map = recipeMap(level);
    if (map == null) {
      return List.of();
    }
    List<RecipeHolder<T>> holders = List.copyOf(map.byType(type));
    holders.forEach(RecipeUtil::remember);
    return holders;
  }

  public static <I extends RecipeInput, T extends Recipe<I>> List<T> getAllRecipesFor(@Nullable Level level,
      RecipeType<T> type) {
    RecipeMap map = recipeMap(level);
    if (map == null) {
      return List.of();
    }
    Collection<RecipeHolder<T>> holders = map.byType(type);
    List<T> recipes = new ArrayList<>(holders.size());
    for (RecipeHolder<T> holder : holders) {
      recipes.add(remember(holder).value());
    }
    return recipes;
  }

  public static Stream<Holder<Item>> ingredientItems(Ingredient ingredient) {
    if (ingredient.isCustom()) {
      return ingredient.getCustomIngredient().items();
    }
    return ingredient.getValues().stream();
  }

  public static Optional<RecipeHolder<?>> byKey(@Nullable Level level, Identifier id) {
    RecipeMap map = recipeMap(level);
    if (map == null) {
      return Optional.empty();
    }
    RecipeHolder<?> holder = map.byKey(ResourceKey.create(Registries.RECIPE, id));
    if (holder != null) {
      remember(holder);
    }
    return Optional.ofNullable(holder);
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public static Optional<Identifier> idOf(@Nullable Level level, @Nullable Recipe<?> recipe) {
    if (recipe == null) {
      return Optional.empty();
    }
    Identifier known = KNOWN_IDS.get(recipe);
    if (known != null) {
      return Optional.of(known);
    }
    RecipeMap map = recipeMap(level);
    if (map == null) {
      return Optional.empty();
    }
    Collection<RecipeHolder<?>> holders = (Collection<RecipeHolder<?>>) (Collection) map
        .byType((RecipeType) recipe.getType());
    for (RecipeHolder<?> holder : holders) {
      if (holder.value() == recipe) {
        return Optional.of(remember(holder).id().identifier());
      }
    }
    return Optional.empty();
  }

  public static <I extends RecipeInput, T extends Recipe<I>> CachedCheck<I, T> createCheck(RecipeType<T> type) {
    return new CachedCheck<>(type);
  }

  public static final class CachedCheck<I extends RecipeInput, T extends Recipe<I>> {
    private final RecipeType<T> type;
    @Nullable
    private ResourceKey<Recipe<?>> lastRecipe;

    private CachedCheck(RecipeType<T> type) {
      this.type = type;
    }

    public Optional<RecipeHolder<T>> getHolderFor(I input, @Nullable Level level) {
      Optional<RecipeHolder<T>> result;
      if (level instanceof ServerLevel serverLevel) {
        result = RecipeUtil.remember(serverLevel.recipeAccess().getRecipeFor(type, input, level, lastRecipe));
      } else {
        result = RecipeUtil.getRecipeFor(level, type, input);
      }
      result.ifPresent(holder -> lastRecipe = holder.id());
      return result;
    }

    public Optional<T> getRecipeFor(I input, @Nullable Level level) {
      return getHolderFor(input, level).map(RecipeHolder::value);
    }
  }
}
