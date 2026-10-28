package com.faktocraft.integration.rei;

import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.Optional;

public class MachineDisplay extends BasicDisplay {

  public static final class Info {

    public static final Info EMPTY = new Info();

    private int duration = 200;
    private int powerCost;
    private float experience;
    private int temperature;
    private int matterCost;
    private int energyCost;
    private float chance;
    private String chanceText = "";
    private boolean dual;

    public static Info of(int duration, int powerCost, float experience) {
      Info info = new Info();
      info.duration = Math.max(1, duration);
      info.powerCost = powerCost;
      info.experience = experience;
      return info;
    }

    public Info temperature(int value) {
      temperature = value;
      return this;
    }

    public Info matterCost(int value) {
      matterCost = value;
      return this;
    }

    public Info energyCost(int value) {
      energyCost = value;
      return this;
    }

    public Info chance(float value) {
      chance = value;
      return this;
    }

    public Info chanceText(String value) {
      chanceText = value;
      return this;
    }

    public Info dual(boolean value) {
      dual = value;
      return this;
    }

    public int duration() {
      return duration;
    }

    public int powerCost() {
      return powerCost;
    }

    public float experience() {
      return experience;
    }

    public int temperature() {
      return temperature;
    }

    public int matterCost() {
      return matterCost;
    }

    public int energyCost() {
      return energyCost;
    }

    public float chance() {
      return chance;
    }

    public String chanceText() {
      return chanceText;
    }

    public boolean dual() {
      return dual;
    }
  }

  private final CategoryIdentifier<MachineDisplay> category;
  private final Info info;
  private final List<Float> outputChances;

  public MachineDisplay(CategoryIdentifier<MachineDisplay> category, List<EntryIngredient> inputs,
      List<EntryIngredient> outputs, Optional<ResourceLocation> location, Info info) {
    this(category, inputs, outputs, location, info, List.of());
  }

  public MachineDisplay(CategoryIdentifier<MachineDisplay> category, List<EntryIngredient> inputs,
      List<EntryIngredient> outputs, Optional<ResourceLocation> location, Info info, List<Float> outputChances) {
    super(inputs, outputs, location);
    this.category = category;
    this.info = info == null ? Info.EMPTY : info;
    this.outputChances = List.copyOf(outputChances);
  }

  public boolean chanceOutput(int index) {
    float chance = index < outputChances.size() ? outputChances.get(index) : 0.0F;
    if (chance > 0.0F) {
      return chance < (category.equals(ReiCategories.RECYCLING) ? 1.0F : 100.0F);
    }
    return category.equals(ReiCategories.SCRAP_BOX)
        || (category.equals(ReiCategories.DISTILLING) && index > 0 && info.chance() < 1.0F);
  }

  @Override
  public CategoryIdentifier<MachineDisplay> getCategoryIdentifier() {
    return category;
  }

  public Info info() {
    return info;
  }

  public EntryIngredient in(int index) {
    return index < inputs.size() ? inputs.get(index) : EntryIngredient.empty();
  }

  public EntryIngredient out(int index) {
    return index < outputs.size() ? outputs.get(index) : EntryIngredient.empty();
  }

  public int inputCount() {
    return inputs.size();
  }

  public int outputCount() {
    return outputs.size();
  }
}
