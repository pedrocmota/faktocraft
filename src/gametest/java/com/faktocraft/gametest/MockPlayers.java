package com.faktocraft.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.GameType;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.registration.ChannelAttributes;
import net.neoforged.neoforge.network.registration.NetworkRegistry;
import java.util.Map;

public final class MockPlayers {

  public record Mock(ServerPlayer player, EmbeddedChannel channel) {

    public List<Object> drainOutbound() {
      List<Object> out = new ArrayList<>();
      Object message;
      while ((message = channel.readOutbound()) != null) {
        out.add(message);
      }
      return out;
    }

    public List<Object> flushAndDrainOutbound() {
      channel.flushOutbound();
      return drainOutbound();
    }

    public <P extends Packet<?>> List<P> drainOutbound(Class<P> type) {
      List<P> out = new ArrayList<>();
      for (Object message : drainOutbound()) {
        if (type.isInstance(message)) {
          out.add(type.cast(message));
        }
      }
      return out;
    }

    public void walk(double x, double y, double z) {
      player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(x, y, z, true, false));
      player.connection.handleClientTickEnd(ServerboundClientTickEndPacket.INSTANCE);
    }

    @SuppressWarnings("unchecked")
    public void allowAllPayloads() {
      try {
        var field = NetworkRegistry.class.getDeclaredField("PAYLOAD_REGISTRATIONS");
        field.setAccessible(true);
        Map<ConnectionProtocol, Map<Identifier, ?>> registrations =
            (Map<ConnectionProtocol, Map<Identifier, ?>>) field.get(null);
        ChannelAttributes.getOrCreateAdHocChannels(player.connection.getConnection())
            .addAll(registrations.getOrDefault(ConnectionProtocol.PLAY, Map.of()).keySet());
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException(e);
      }
    }

    public void remove() {
      player.level().getServer().getPlayerList().remove(player);
    }
  }

  private MockPlayers() {
  }

  public static Mock survival(GameTestHelper helper, double x, double y, double z) {
    CommonListenerCookie cookie = CommonListenerCookie
        .createInitial(new GameProfile(UUID.randomUUID(), "mock-player"), false);
    ServerPlayer player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(),
        cookie.clientInformation()) {
      @Override
      public GameType gameMode() {
        return GameType.SURVIVAL;
      }
    };
    Connection connection = new Connection(PacketFlow.SERVERBOUND);
    EmbeddedChannel channel = new EmbeddedChannel(connection);
    helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
    player.connection.markClientLoaded();
    GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
    player.onUpdateAbilities();
    player.teleportTo(x, y, z);
    try {
      var field = ServerGamePacketListenerImpl.class.getDeclaredField("awaitingTeleport");
      field.setAccessible(true);
      int id = field.getInt(player.connection);
      player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(id, x, y, z, 0.0F, 0.0F));
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
    helper.onEachTick(() -> player.connection.tick());
    return new Mock(player, channel);
  }
}
