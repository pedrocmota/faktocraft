package com.faktocraft.common.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class BasicConfig {

  public static final Client CLIENT;
  public static final ForgeConfigSpec CLIENT_SPEC;

  public static final Server SERVER;
  public static final ForgeConfigSpec SERVER_SPEC;

  static {
    Pair<Client, ForgeConfigSpec> client = new ForgeConfigSpec.Builder().configure(Client::new);
    CLIENT = client.getLeft();
    CLIENT_SPEC = client.getRight();

    Pair<Server, ForgeConfigSpec> server = new ForgeConfigSpec.Builder().configure(Server::new);
    SERVER = server.getLeft();
    SERVER_SPEC = server.getRight();
  }

  private BasicConfig() {
  }

  public static final class Client {

    public final ForgeConfigSpec.BooleanValue showPipeSupports;
    public final ForgeConfigSpec.BooleanValue notifyUpdates;

    private Client(ForgeConfigSpec.Builder builder) {
      builder.push("visual");
      showPipeSupports = builder
          .comment("Draw the small legs that hold pipes and cables against nearby blocks.",
              "Purely cosmetic: it changes nothing about how they work.")
          .define("showPipeSupports", true);
      builder.pop();
      builder.push("updates");
      notifyUpdates = builder
          .comment("Show a chat message when joining a world if a newer Faktocraft version is available.",
              "Has no effect when Forge's global version check is disabled.")
          .define("notifyUpdates", true);
      builder.pop();
    }
  }

  public static final class Server {

    public final ForgeConfigSpec.BooleanValue teleportAnchorEnabled;
    public final ForgeConfigSpec.BooleanValue chunkLoaderEnabled;
    public final ForgeConfigSpec.BooleanValue nukeEnabled;
    public final ForgeConfigSpec.BooleanValue tankBreakPlacesFluid;
    public final ForgeConfigSpec.BooleanValue veinMiningEnabled;
    public final ForgeConfigSpec.IntValue veinMiningMaxBlocks;
    public final ForgeConfigSpec.IntValue veinMiningEnergyMultiplier;

    private Server(ForgeConfigSpec.Builder builder) {
      builder.push("tank");
      tankBreakPlacesFluid = builder
          .comment("Whether breaking a tank holding at least 1000mB places one source block of its fluid.",
              "Disabled, the stored fluid is simply discarded when the tank breaks.")
          .define("breakPlacesFluidSource", true);
      builder.pop();
      builder.push("content");
      teleportAnchorEnabled = builder
          .comment("Whether the teleport anchor exists.",
              "Disabled it is hidden from the creative tabs and from JEI.")
          .define("teleportAnchor", true);
      chunkLoaderEnabled = builder
          .comment("Whether the chunk loader exists.",
              "Disabled it is hidden from the creative tabs and from JEI.")
          .define("chunkLoader", true);
      nukeEnabled = builder
          .comment("Whether the nuke can be placed and detonated.",
              "Disabled it is hidden from the creative tabs and from JEI, and placed nukes will not go off.")
          .define("nuke", true);
      builder.pop();
      builder.push("tools");
      veinMiningEnabled = builder
          .comment("Whether the iridium drill and iridium chainsaw break a whole ore vein or tree",
              "when the player holds Alt while mining.")
          .define("veinMining", true);
      veinMiningMaxBlocks = builder
          .comment("Maximum number of blocks broken in one vein, counting the block the player hit.")
          .defineInRange("veinMiningMaxBlocks", 64, 2, 512);
      veinMiningEnergyMultiplier = builder
          .comment("Energy cost of each extra block of the vein, as a multiple of the tool's normal mining cost.")
          .defineInRange("veinMiningEnergyMultiplier", 3, 1, 20);
      builder.pop();
    }
  }

  private static boolean read(ForgeConfigSpec spec, ForgeConfigSpec.BooleanValue value) {
    return !spec.isLoaded() || value.get();
  }

  public static boolean showPipeSupports() {
    return read(CLIENT_SPEC, CLIENT.showPipeSupports);
  }

  public static boolean notifyUpdates() {
    return read(CLIENT_SPEC, CLIENT.notifyUpdates);
  }

  public static boolean teleportAnchorEnabled() {
    return read(SERVER_SPEC, SERVER.teleportAnchorEnabled);
  }

  public static boolean chunkLoaderEnabled() {
    return read(SERVER_SPEC, SERVER.chunkLoaderEnabled);
  }

  public static boolean nukeEnabled() {
    return read(SERVER_SPEC, SERVER.nukeEnabled);
  }

  public static boolean tankBreakPlacesFluid() {
    return read(SERVER_SPEC, SERVER.tankBreakPlacesFluid);
  }

  public static boolean veinMiningEnabled() {
    return read(SERVER_SPEC, SERVER.veinMiningEnabled);
  }

  public static int veinMiningMaxBlocks() {
    return SERVER_SPEC.isLoaded() ? SERVER.veinMiningMaxBlocks.get() : SERVER.veinMiningMaxBlocks.getDefault();
  }

  public static int veinMiningEnergyMultiplier() {
    return SERVER_SPEC.isLoaded() ? SERVER.veinMiningEnergyMultiplier.get()
        : SERVER.veinMiningEnergyMultiplier.getDefault();
  }
}
