package com.faktocraft.integration.rei;

import com.faktocraft.Faktocraft;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.DisplaySerializer;
import me.shedaniel.rei.api.common.display.basic.BasicDisplay;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import java.util.List;
import java.util.Optional;

public class MachineDisplay extends BasicDisplay {

  public static final Identifier SERIALIZER_ID = Identifier.fromNamespaceAndPath(Faktocraft.MODID, "machine");

  public static final class Info {

    public static final Info EMPTY = new Info();

    public static final Codec<Info> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Codec.INT.fieldOf("duration").forGetter(Info::duration),
        Codec.INT.fieldOf("power_cost").forGetter(Info::powerCost),
        Codec.FLOAT.fieldOf("experience").forGetter(Info::experience),
        Codec.INT.fieldOf("temperature").forGetter(Info::temperature),
        Codec.INT.fieldOf("matter_cost").forGetter(Info::matterCost),
        Codec.INT.fieldOf("energy_cost").forGetter(Info::energyCost),
        Codec.FLOAT.fieldOf("chance").forGetter(Info::chance),
        Codec.STRING.fieldOf("chance_text").forGetter(Info::chanceText),
        Codec.BOOL.fieldOf("dual").forGetter(Info::dual)).apply(instance, Info::new));

    public static final StreamCodec<FriendlyByteBuf, Info> STREAM_CODEC = StreamCodec.of(Info::write, Info::read);

    private int duration = 200;
    private int powerCost;
    private float experience;
    private int temperature;
    private int matterCost;
    private int energyCost;
    private float chance;
    private String chanceText = "";
    private boolean dual;

    private Info() {
    }

    private Info(int duration, int powerCost, float experience, int temperature, int matterCost, int energyCost,
        float chance, String chanceText, boolean dual) {
      this.duration = duration;
      this.powerCost = powerCost;
      this.experience = experience;
      this.temperature = temperature;
      this.matterCost = matterCost;
      this.energyCost = energyCost;
      this.chance = chance;
      this.chanceText = chanceText;
      this.dual = dual;
    }

    private static void write(FriendlyByteBuf buf, Info info) {
      buf.writeVarInt(info.duration);
      buf.writeVarInt(info.powerCost);
      buf.writeFloat(info.experience);
      buf.writeVarInt(info.temperature);
      buf.writeVarInt(info.matterCost);
      buf.writeVarInt(info.energyCost);
      buf.writeFloat(info.chance);
      buf.writeUtf(info.chanceText);
      buf.writeBoolean(info.dual);
    }

    private static Info read(FriendlyByteBuf buf) {
      return new Info(buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(),
          buf.readVarInt(), buf.readFloat(), buf.readUtf(), buf.readBoolean());
    }

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

  public static final DisplaySerializer<MachineDisplay> SERIALIZER = DisplaySerializer.of(
      RecordCodecBuilder.mapCodec(instance -> instance.group(
          Identifier.CODEC.fieldOf("category").forGetter(display -> display.category.getIdentifier()),
          EntryIngredient.codec().listOf().fieldOf("inputs").forGetter(display -> display.inputs),
          EntryIngredient.codec().listOf().fieldOf("outputs").forGetter(display -> display.outputs),
          Identifier.CODEC.optionalFieldOf("location").forGetter(display -> display.location),
          Info.CODEC.fieldOf("info").forGetter(display -> display.info),
          Codec.FLOAT.listOf().optionalFieldOf("output_chances", List.of())
              .forGetter(display -> display.outputChances))
          .apply(instance, MachineDisplay::of)),
      StreamCodec.composite(
          Identifier.STREAM_CODEC, display -> display.category.getIdentifier(),
          EntryIngredient.streamCodec().apply(ByteBufCodecs.list()), display -> display.inputs,
          EntryIngredient.streamCodec().apply(ByteBufCodecs.list()), display -> display.outputs,
          ByteBufCodecs.optional(Identifier.STREAM_CODEC), display -> display.location,
          Info.STREAM_CODEC, display -> display.info,
          ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list()), display -> display.outputChances,
          MachineDisplay::of));

  private final CategoryIdentifier<MachineDisplay> category;
  private final Info info;
  private final List<Float> outputChances;

  public MachineDisplay(CategoryIdentifier<MachineDisplay> category, List<EntryIngredient> inputs,
      List<EntryIngredient> outputs, Optional<Identifier> location, Info info) {
    this(category, inputs, outputs, location, info, List.of());
  }

  public MachineDisplay(CategoryIdentifier<MachineDisplay> category, List<EntryIngredient> inputs,
      List<EntryIngredient> outputs, Optional<Identifier> location, Info info, List<Float> outputChances) {
    super(inputs, outputs, location);
    this.category = category;
    this.info = info == null ? Info.EMPTY : info;
    this.outputChances = List.copyOf(outputChances);
  }

  private static MachineDisplay of(Identifier category, List<EntryIngredient> inputs, List<EntryIngredient> outputs,
      Optional<Identifier> location, Info info, List<Float> outputChances) {
    return new MachineDisplay(CategoryIdentifier.of(category), inputs, outputs, location, info, outputChances);
  }

  @Override
  public CategoryIdentifier<MachineDisplay> getCategoryIdentifier() {
    return category;
  }

  @Override
  public DisplaySerializer<? extends MachineDisplay> getSerializer() {
    return SERIALIZER;
  }

  public Info info() {
    return info;
  }

  public EntryIngredient in(int index) {
    return index < inputs.size() ? inputs.get(index) : EntryIngredient.empty();
  }

  public EntryIngredient out(int index) {
    if (index >= outputs.size()) {
      return EntryIngredient.empty();
    }
    float chance = index < outputChances.size() ? outputChances.get(index) : 0.0F;
    return chance > 0.0F ? ReiDisplays.withChance(outputs.get(index), chance) : outputs.get(index);
  }

  public boolean chanceOutput(int index) {
    float chance = index < outputChances.size() ? outputChances.get(index) : 0.0F;
    if (chance > 0.0F) {
      return chance < (category.equals(ReiCategories.RECYCLING) ? 1.0F : 100.0F);
    }
    return category.equals(ReiCategories.SCRAP_BOX)
        || (category.equals(ReiCategories.DISTILLING) && index > 0 && info.chance() < 1.0F);
  }

  public int inputCount() {
    return inputs.size();
  }

  public int outputCount() {
    return outputs.size();
  }
}
