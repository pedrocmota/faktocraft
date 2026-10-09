package com.faktocraft.common.network.packet;

import com.faktocraft.common.util.BufUtil;
import com.faktocraft.common.network.PacketContext;
import com.faktocraft.Faktocraft;
import net.minecraft.resources.Identifier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import com.faktocraft.common.block.impl.logistics.BlockEntityRecipePipe;
import com.faktocraft.common.block.impl.logistics.IoMode;
import com.faktocraft.common.block.impl.logistics.MenuRecipePipe;
import com.faktocraft.common.block.impl.logistics.RecipeImport;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;

public record PacketRecipePipeFill(BlockPos pipePos, List<RecipeImport.Entry> inputs,
    List<RecipeImport.Entry> outputs, int batch) implements CustomPacketPayload {

  private static final int MAX_TAG_LENGTH = 256;

  public static final Type<PacketRecipePipeFill> TYPE = new Type<>(
      Identifier.fromNamespaceAndPath(Faktocraft.MODID, "packet_recipe_pipe_fill"));
  public static final StreamCodec<RegistryFriendlyByteBuf, PacketRecipePipeFill> STREAM_CODEC = StreamCodec.of(
      (buf, msg) -> encode(msg, buf), PacketRecipePipeFill::decode);

  @Override
  public Type<PacketRecipePipeFill> type() {
    return TYPE;
  }

  public static void encode(PacketRecipePipeFill msg, FriendlyByteBuf buf) {
    buf.writeBlockPos(msg.pipePos);
    write(buf, msg.inputs, BlockEntityRecipePipe.MAX_INPUTS);
    write(buf, msg.outputs, BlockEntityRecipePipe.MAX_OUTPUTS);
    buf.writeVarInt(msg.batch);
  }

  private static void write(FriendlyByteBuf buf, List<RecipeImport.Entry> entries, int max) {
    int size = Math.min(entries.size(), max);
    buf.writeVarInt(size);
    for (int i = 0; i < size; i++) {
      RecipeImport.Entry entry = entries.get(i);
      BufUtil.writeItem(buf, entry.stack());
      buf.writeVarInt(entry.count());
      buf.writeUtf(entry.tag(), MAX_TAG_LENGTH);
      buf.writeByte(entry.mode().ordinal());
    }
  }

  private static List<RecipeImport.Entry> read(FriendlyByteBuf buf, int max) {
    int size = Math.min(buf.readVarInt(), max);
    List<RecipeImport.Entry> entries = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      entries.add(new RecipeImport.Entry(BufUtil.readItem(buf), buf.readVarInt(), buf.readUtf(MAX_TAG_LENGTH),
          IoMode.of(buf.readByte())));
    }
    return entries;
  }

  public static PacketRecipePipeFill decode(FriendlyByteBuf buf) {
    return new PacketRecipePipeFill(buf.readBlockPos(),
        read(buf, BlockEntityRecipePipe.MAX_INPUTS),
        read(buf, BlockEntityRecipePipe.MAX_OUTPUTS),
        buf.readVarInt());
  }

  public static void handle(PacketRecipePipeFill msg, PacketContext ctx) {
    ctx.enqueueWork(() -> {
      ServerPlayer player = ctx.getSender();
      if (player == null || !(player.containerMenu instanceof MenuRecipePipe menu)
          || !menu.getPipePos().equals(msg.pipePos)) {
        return;
      }
      if (!(player.level().getBlockEntity(msg.pipePos) instanceof BlockEntityRecipePipe pipe)) {
        return;
      }

      int index = menu.editIndex();
      if (index < 0) {
        index = pipe.addRecipe();
        if (index < 0) {
          return;
        }
        menu.serverEdit(index);
      }

      pipe.importRecipe(index, msg.inputs, msg.outputs, msg.batch);
      menu.broadcastChanges();
    });
    ctx.setPacketHandled(true);
  }
}
