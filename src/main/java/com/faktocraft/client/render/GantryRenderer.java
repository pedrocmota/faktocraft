package com.faktocraft.client.render;

import com.faktocraft.Faktocraft;
import com.faktocraft.common.block.impl.quarry.BlockEntityGantry;
import com.faktocraft.common.block.impl.quarry.GantryHeadMotion;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.function.IntUnaryOperator;

public class GantryRenderer<T extends BlockEntityGantry> implements BlockEntityRenderer<T> {

  private static final ResourceLocation GANTRY_SPRITE = new ResourceLocation(Faktocraft.MODID,
      "block/misc/quarry_gantry");
  private static final ResourceLocation CARRIAGE_SPRITE = new ResourceLocation(Faktocraft.MODID,
      "block/misc/quarry_carriage");
  private static final ResourceLocation WHEEL_SPRITE = new ResourceLocation(Faktocraft.MODID,
      "block/misc/quarry_wheel");
  private static final float BEAM_HALF = 0.125F;
  private static final int WHITE = 0xFFFFFFFF;
  private static final int HEAD_TINT = 0xFF9A9A9A;

  private static final int AXIS_X = 0;
  private static final int AXIS_Y = 1;
  private static final int AXIS_Z = 2;

  private static final float PX = 1.0F / 16.0F;
  private static final float[] STRIP_TOP = { 0.0F, 4.0F };
  private static final float[] STRIP_SIDE = { 4.0F, 9.0F };
  private static final float[] STRIP_CABLE = { 9.0F, 13.0F };

  private static final float BOGIE_HALF = 5.0F * PX;
  private static final float BOGIE_LENGTH_HALF = 4.0F * PX;
  private static final float WHEEL_HALF = 3.0F * PX;
  private static final float WHEEL_THICK = 0.5F * PX;
  private static final float WHEEL_ALONG = BOGIE_LENGTH_HALF + WHEEL_THICK + 0.5F * PX;
  private static final float WHEEL_SIDE = 4.8F * PX;
  private static final float WHEEL_TEX = 6.0F;
  private static final float TREAD_V0 = 7.0F;
  private static final float TREAD_V1 = 8.0F;

  @Override
  public void render(T quarry, float partialTick, PoseStack poseStack, MultiBufferSource buffer,
      int packedLight, int packedOverlay) {
    Level level = quarry.getLevel();
    if (level == null || !quarry.hasArea() || quarry.stage() < BlockEntityGantry.STAGE_WORK) {
      return;
    }

    double now = level.getGameTime() + partialTick;
    if (quarry.headX != quarry.clientSyncedX || quarry.headY != quarry.clientSyncedY
        || quarry.headZ != quarry.clientSyncedZ) {
      quarry.clientSyncedX = quarry.headX;
      quarry.clientSyncedY = quarry.headY;
      quarry.clientSyncedZ = quarry.headZ;
      quarry.clientSyncTick = level.getGameTime();
    }
    double[] predicted = predictedHead(quarry, now);
    if (Double.isNaN(quarry.clientHeadLastTime)) {
      quarry.clientHeadX = predicted[0];
      quarry.clientHeadY = predicted[1];
      quarry.clientHeadZ = predicted[2];
    } else {
      double elapsed = Math.min(3.0, Math.max(0.0, now - quarry.clientHeadLastTime));
      double[] current = { quarry.clientHeadX, quarry.clientHeadY, quarry.clientHeadZ };
      GantryHeadMotion.follow(current, predicted, quarry.armSpeed(), elapsed);
      quarry.clientHeadX = current[0];
      quarry.clientHeadY = current[1];
      quarry.clientHeadZ = current[2];
    }
    float railTarget = quarry.frameHeightAbove();
    if (Float.isNaN(quarry.clientRailY) || Double.isNaN(quarry.clientHeadLastTime)) {
      quarry.clientRailY = railTarget;
    } else {
      float elapsed = Math.min(3.0F, Math.max(0.0F, (float) (now - quarry.clientHeadLastTime)));
      float step = quarry.armSpeed() * elapsed;
      float diff = railTarget - quarry.clientRailY;
      quarry.clientRailY = Math.abs(diff) <= step ? railTarget : quarry.clientRailY + Math.signum(diff) * step;
    }
    quarry.clientHeadLastTime = now;

    var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
    TextureAtlasSprite gantry = atlas.apply(GANTRY_SPRITE);
    TextureAtlasSprite carriage = atlas.apply(CARRIAGE_SPRITE);
    TextureAtlasSprite wheel = atlas.apply(WHEEL_SPRITE);
    if (gantry == null || carriage == null || wheel == null) {
      return;
    }
    VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
    PoseStack.Pose pose = poseStack.last();

    BlockPos origin = quarry.getBlockPos();
    float headX = (float) (quarry.clientHeadX - origin.getX());
    float headY = (float) (quarry.clientHeadY - origin.getY());
    float headZ = (float) (quarry.clientHeadZ - origin.getZ());
    float gantryY = quarry.clientRailY;
    float innerX0 = quarry.areaMinX() + 1 - origin.getX();
    float innerX1 = quarry.areaMaxX() - origin.getX();
    float innerZ0 = quarry.areaMinZ() + 1 - origin.getZ();
    float innerZ1 = quarry.areaMaxZ() - origin.getZ();

    BlockPos headPos = BlockPos.containing(quarry.clientHeadX, quarry.clientHeadY + 0.5, quarry.clientHeadZ);
    int light = LevelRenderer.getLightColor(level,
        headPos.getY() >= level.getMaxBuildHeight() ? headPos.below() : headPos);
    int gantryLight = LevelRenderer.getLightColor(level,
        new BlockPos(headPos.getX(), origin.getY() + Mth.floor(gantryY), headPos.getZ()));

    float clampedX = Mth.clamp(headX, innerX0 + BEAM_HALF, innerX1 - BEAM_HALF);
    float clampedZ = Mth.clamp(headZ, innerZ0 + BEAM_HALF, innerZ1 - BEAM_HALF);

    float railY0 = gantryY + 5.5F / 16.0F;
    float railY1 = gantryY + 10.5F / 16.0F;
    float westEnd = innerX0 - 0.5F;
    float eastEnd = innerX1 + 0.5F;
    float northEnd = innerZ0 - 0.5F;
    float southEnd = innerZ1 + 0.5F;
    float ringY = gantryY + 0.5F;

    beam(pose, vc, gantry, WHITE, gantryLight, AXIS_X,
        westEnd, eastEnd, railY0, railY1, clampedZ - BEAM_HALF, clampedZ + BEAM_HALF, STRIP_TOP, STRIP_SIDE);
    beam(pose, vc, gantry, WHITE, gantryLight, AXIS_Z,
        northEnd, southEnd, clampedX - BEAM_HALF, clampedX + BEAM_HALF, railY0, railY1, STRIP_SIDE, STRIP_TOP);

    movingBox(pose, vc, carriage, WHITE, gantryLight,
        clampedX - 0.3125F, gantryY + 3 / 16.0F, clampedZ - 0.3125F,
        clampedX + 0.3125F, gantryY + 13 / 16.0F, clampedZ + 0.3125F);

    float travelZ = origin.getZ() + clampedZ;
    float travelX = origin.getX() + clampedX;
    bogie(pose, vc, carriage, wheel, lightAt(level, origin, westEnd, ringY, clampedZ),
        Direction.Axis.Z, westEnd, ringY, clampedZ, travelZ);
    bogie(pose, vc, carriage, wheel, lightAt(level, origin, eastEnd, ringY, clampedZ),
        Direction.Axis.Z, eastEnd, ringY, clampedZ, travelZ);
    bogie(pose, vc, carriage, wheel, lightAt(level, origin, clampedX, ringY, northEnd),
        Direction.Axis.X, clampedX, ringY, northEnd, travelX);
    bogie(pose, vc, carriage, wheel, lightAt(level, origin, clampedX, ringY, southEnd),
        Direction.Axis.X, clampedX, ringY, southEnd, travelX);

    int stringX = Mth.floor(origin.getX() + clampedX);
    int stringZ = Mth.floor(origin.getZ() + clampedZ);
    IntUnaryOperator columnLight = yRel -> LevelRenderer.getLightColor(level,
        new BlockPos(stringX, Mth.clamp(origin.getY() + yRel, level.getMinBuildHeight(),
            level.getMaxBuildHeight() - 1), stringZ));
    if (headY < railY0) {
      beam(pose, vc, gantry, WHITE, columnLight, AXIS_Y,
          headY - 0.15F, railY0, clampedX - BEAM_HALF, clampedX + BEAM_HALF,
          clampedZ - BEAM_HALF, clampedZ + BEAM_HALF, STRIP_CABLE, STRIP_CABLE);
      movingBox(pose, vc, carriage, HEAD_TINT, light,
          clampedX - 0.22F, headY - 0.3F, clampedZ - 0.22F,
          clampedX + 0.22F, headY, clampedZ + 0.22F);
    } else if (headY > railY1 + 0.5F) {
      beam(pose, vc, gantry, WHITE, columnLight, AXIS_Y,
          railY1, headY - 0.15F, clampedX - BEAM_HALF, clampedX + BEAM_HALF,
          clampedZ - BEAM_HALF, clampedZ + BEAM_HALF, STRIP_SIDE, STRIP_SIDE);
      movingBox(pose, vc, carriage, HEAD_TINT, light,
          clampedX - 0.22F, headY - 0.3F, clampedZ - 0.22F,
          clampedX + 0.22F, headY, clampedZ + 0.22F);
    }
  }

  private static int lightAt(Level level, BlockPos origin, float x, float y, float z) {
    BlockPos pos = BlockPos.containing(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
    return LevelRenderer.getLightColor(level,
        pos.getY() >= level.getMaxBuildHeight() ? pos.below() : pos);
  }

  private static void bogie(PoseStack.Pose pose, VertexConsumer vc, TextureAtlasSprite plate,
      TextureAtlasSprite wheel, int light, Direction.Axis along, float cx, float cy, float cz, float travel) {
    float halfX = along == Direction.Axis.X ? BOGIE_LENGTH_HALF : BOGIE_HALF;
    float halfZ = along == Direction.Axis.X ? BOGIE_HALF : BOGIE_LENGTH_HALF;
    movingBox(pose, vc, plate, WHITE, light,
        cx - halfX, cy - BOGIE_HALF, cz - halfZ, cx + halfX, cy + BOGIE_HALF, cz + halfZ);

    float angle = travel / WHEEL_HALF;
    for (int a = -1; a <= 1; a += 2) {
      for (int s = -1; s <= 1; s += 2) {
        float offAlong = a * WHEEL_ALONG;
        float offSide = s * WHEEL_SIDE;
        if (along == Direction.Axis.X) {
          wheel(pose, vc, wheel, light, along, cx + offAlong, cy, cz + offSide, a * angle);
        } else {
          wheel(pose, vc, wheel, light, along, cx + offSide, cy, cz + offAlong, a * angle);
        }
      }
    }
  }

  private static void wheel(PoseStack.Pose pose, VertexConsumer vc, TextureAtlasSprite sprite, int light,
      Direction.Axis along, float cx, float cy, float cz, float angle) {
    float reach = WHEEL_HALF * Mth.SQRT_OF_TWO;
    float[] ca = new float[4];
    float[] cy4 = new float[4];
    for (int k = 0; k < 4; k++) {
      float theta = angle + (float) (Math.PI / 4.0 + k * Math.PI / 2.0);
      ca[k] = reach * Mth.cos(theta);
      cy4[k] = reach * Mth.sin(theta);
    }
    float[] cu = { 0.0F, WHEEL_TEX, WHEEL_TEX, 0.0F };
    float[] cv = { 0.0F, 0.0F, WHEEL_TEX, WHEEL_TEX };

    for (int side = -1; side <= 1; side += 2) {
      for (int k = 0; k < 4; k++) {
        int i = side > 0 ? k : 3 - k;
        wheelVertex(pose, vc, along, cx, cy, cz, ca[i], cy4[i], side * WHEEL_THICK,
            sprite.getU(cu[i]), sprite.getV(cv[i]), light, 0.0F, 0.0F, side);
      }
    }

    float u0 = sprite.getU(0.0F);
    float u1 = sprite.getU(WHEEL_TEX);
    float v0 = sprite.getV(TREAD_V0);
    float v1 = sprite.getV(TREAD_V1);
    for (int k = 0; k < 4; k++) {
      int n = (k + 1) & 3;
      float na = (ca[k] + ca[n]) * 0.5F;
      float ny = (cy4[k] + cy4[n]) * 0.5F;
      float len = Mth.sqrt(na * na + ny * ny);
      na /= len;
      ny /= len;
      wheelVertex(pose, vc, along, cx, cy, cz, ca[k], cy4[k], -WHEEL_THICK, u0, v0, light, na, ny, 0.0F);
      wheelVertex(pose, vc, along, cx, cy, cz, ca[n], cy4[n], -WHEEL_THICK, u1, v0, light, na, ny, 0.0F);
      wheelVertex(pose, vc, along, cx, cy, cz, ca[n], cy4[n], WHEEL_THICK, u1, v1, light, na, ny, 0.0F);
      wheelVertex(pose, vc, along, cx, cy, cz, ca[k], cy4[k], WHEEL_THICK, u0, v1, light, na, ny, 0.0F);
    }
  }

  private static void wheelVertex(PoseStack.Pose pose, VertexConsumer vc, Direction.Axis along,
      float cx, float cy, float cz, float a, float y, float lateral, float u, float v, int light,
      float na, float ny, float nLateral) {
    if (along == Direction.Axis.X) {
      CuboidRenderer.vertex(pose, vc, cx + a, cy + y, cz + lateral, u, v, WHITE, light, na, ny, nLateral);
    } else {
      CuboidRenderer.vertex(pose, vc, cx + lateral, cy + y, cz + a, u, v, WHITE, light, nLateral, ny, na);
    }
  }

  private static void beam(PoseStack.Pose pose, VertexConsumer vc, TextureAtlasSprite sprite, int color,
      int light, int axis, float a0, float a1, float b0, float b1, float c0, float c1,
      float[] stripB, float[] stripC) {
    beam(pose, vc, sprite, color, yRel -> light, axis, a0, a1, b0, b1, c0, c1, stripB, stripC);
  }

  private static void beam(PoseStack.Pose pose, VertexConsumer vc, TextureAtlasSprite sprite, int color,
      IntUnaryOperator lightFn, int axis, float a0, float a1, float b0, float b1,
      float c0, float c1, float[] stripB, float[] stripC) {
    float vb0 = sprite.getV(stripC[0]);
    float vb1 = sprite.getV(stripC[1]);
    float vc0 = sprite.getV(stripB[0]);
    float vc1 = sprite.getV(stripB[1]);

    float s = a0;
    while (s < a1) {
      float n = (float) Math.floor(s);
      float e = Math.min(a1, n + 1 > s ? n + 1 : s + 1);
      float u0 = sprite.getU((s - n) * 16.0F);
      float u1 = sprite.getU((e - n) * 16.0F);
      int light = lightFn.applyAsInt((int) n);

      vert(pose, vc, axis, s, b0, c0, u0, vc0, color, light, 0, -1, 0);
      vert(pose, vc, axis, e, b0, c0, u1, vc0, color, light, 0, -1, 0);
      vert(pose, vc, axis, e, b0, c1, u1, vc1, color, light, 0, -1, 0);
      vert(pose, vc, axis, s, b0, c1, u0, vc1, color, light, 0, -1, 0);

      vert(pose, vc, axis, s, b1, c0, u0, vc0, color, light, 0, 1, 0);
      vert(pose, vc, axis, e, b1, c0, u1, vc0, color, light, 0, 1, 0);
      vert(pose, vc, axis, e, b1, c1, u1, vc1, color, light, 0, 1, 0);
      vert(pose, vc, axis, s, b1, c1, u0, vc1, color, light, 0, 1, 0);

      vert(pose, vc, axis, s, b0, c0, u0, vb0, color, light, 0, 0, -1);
      vert(pose, vc, axis, e, b0, c0, u1, vb0, color, light, 0, 0, -1);
      vert(pose, vc, axis, e, b1, c0, u1, vb1, color, light, 0, 0, -1);
      vert(pose, vc, axis, s, b1, c0, u0, vb1, color, light, 0, 0, -1);

      vert(pose, vc, axis, s, b0, c1, u0, vb0, color, light, 0, 0, 1);
      vert(pose, vc, axis, e, b0, c1, u1, vb0, color, light, 0, 0, 1);
      vert(pose, vc, axis, e, b1, c1, u1, vb1, color, light, 0, 0, 1);
      vert(pose, vc, axis, s, b1, c1, u0, vb1, color, light, 0, 0, 1);

      s = e;
    }

    float ub0 = sprite.getU(0.0F);
    float ub1 = sprite.getU((b1 - b0) * 16.0F);
    int lightA0 = lightFn.applyAsInt(Mth.floor(a0));
    int lightA1 = lightFn.applyAsInt(Mth.floor(a1 - 1.0E-4F));
    vert(pose, vc, axis, a0, b0, c0, ub0, vc0, color, lightA0, -1, 0, 0);
    vert(pose, vc, axis, a0, b1, c0, ub1, vc0, color, lightA0, -1, 0, 0);
    vert(pose, vc, axis, a0, b1, c1, ub1, vc1, color, lightA0, -1, 0, 0);
    vert(pose, vc, axis, a0, b0, c1, ub0, vc1, color, lightA0, -1, 0, 0);

    vert(pose, vc, axis, a1, b0, c0, ub0, vc0, color, lightA1, 1, 0, 0);
    vert(pose, vc, axis, a1, b1, c0, ub1, vc0, color, lightA1, 1, 0, 0);
    vert(pose, vc, axis, a1, b1, c1, ub1, vc1, color, lightA1, 1, 0, 0);
    vert(pose, vc, axis, a1, b0, c1, ub0, vc1, color, lightA1, 1, 0, 0);
  }

  private static void vert(PoseStack.Pose pose, VertexConsumer vc, int axis, float a, float b, float c,
      float u, float v, int color, int light, float na, float nb, float nc) {
    float x;
    float y;
    float z;
    float nx;
    float ny;
    float nz;
    switch (axis) {
      case AXIS_Y -> {
        x = b;
        y = a;
        z = c;
        nx = nb;
        ny = na;
        nz = nc;
      }
      case AXIS_Z -> {
        x = b;
        y = c;
        z = a;
        nx = nb;
        ny = nc;
        nz = na;
      }
      default -> {
        x = a;
        y = b;
        z = c;
        nx = na;
        ny = nb;
        nz = nc;
      }
    }
    CuboidRenderer.vertex(pose, vc, x, y, z, u, v, color, light, nx, ny, nz);
  }

  private static void movingBox(PoseStack.Pose pose, VertexConsumer vc, TextureAtlasSprite sprite, int color,
      int light, float x0, float y0, float z0, float x1, float y1, float z1) {
    float sx = (16.0F - (x1 - x0) * 16.0F) / 2.0F;
    float sy = (16.0F - (y1 - y0) * 16.0F) / 2.0F;
    float sz = (16.0F - (z1 - z0) * 16.0F) / 2.0F;
    float ux0 = sprite.getU(sx);
    float ux1 = sprite.getU(sx + (x1 - x0) * 16.0F);
    float uz0 = sprite.getU(sz);
    float uz1 = sprite.getU(sz + (z1 - z0) * 16.0F);
    float vz0 = sprite.getV(sz);
    float vz1 = sprite.getV(sz + (z1 - z0) * 16.0F);
    float vyTop = sprite.getV(sy);
    float vyBottom = sprite.getV(sy + (y1 - y0) * 16.0F);

    CuboidRenderer.vertex(pose, vc, x0, y0, z0, ux0, vz0, color, light, 0, -1, 0);
    CuboidRenderer.vertex(pose, vc, x1, y0, z0, ux1, vz0, color, light, 0, -1, 0);
    CuboidRenderer.vertex(pose, vc, x1, y0, z1, ux1, vz1, color, light, 0, -1, 0);
    CuboidRenderer.vertex(pose, vc, x0, y0, z1, ux0, vz1, color, light, 0, -1, 0);

    CuboidRenderer.vertex(pose, vc, x0, y1, z1, ux0, vz1, color, light, 0, 1, 0);
    CuboidRenderer.vertex(pose, vc, x1, y1, z1, ux1, vz1, color, light, 0, 1, 0);
    CuboidRenderer.vertex(pose, vc, x1, y1, z0, ux1, vz0, color, light, 0, 1, 0);
    CuboidRenderer.vertex(pose, vc, x0, y1, z0, ux0, vz0, color, light, 0, 1, 0);

    CuboidRenderer.vertex(pose, vc, x1, y0, z0, ux1, vyBottom, color, light, 0, 0, -1);
    CuboidRenderer.vertex(pose, vc, x1, y1, z0, ux1, vyTop, color, light, 0, 0, -1);
    CuboidRenderer.vertex(pose, vc, x0, y1, z0, ux0, vyTop, color, light, 0, 0, -1);
    CuboidRenderer.vertex(pose, vc, x0, y0, z0, ux0, vyBottom, color, light, 0, 0, -1);

    CuboidRenderer.vertex(pose, vc, x0, y0, z1, ux0, vyBottom, color, light, 0, 0, 1);
    CuboidRenderer.vertex(pose, vc, x0, y1, z1, ux0, vyTop, color, light, 0, 0, 1);
    CuboidRenderer.vertex(pose, vc, x1, y1, z1, ux1, vyTop, color, light, 0, 0, 1);
    CuboidRenderer.vertex(pose, vc, x1, y0, z1, ux1, vyBottom, color, light, 0, 0, 1);

    CuboidRenderer.vertex(pose, vc, x0, y0, z0, uz0, vyBottom, color, light, -1, 0, 0);
    CuboidRenderer.vertex(pose, vc, x0, y1, z0, uz0, vyTop, color, light, -1, 0, 0);
    CuboidRenderer.vertex(pose, vc, x0, y1, z1, uz1, vyTop, color, light, -1, 0, 0);
    CuboidRenderer.vertex(pose, vc, x0, y0, z1, uz1, vyBottom, color, light, -1, 0, 0);

    CuboidRenderer.vertex(pose, vc, x1, y0, z1, uz1, vyBottom, color, light, 1, 0, 0);
    CuboidRenderer.vertex(pose, vc, x1, y1, z1, uz1, vyTop, color, light, 1, 0, 0);
    CuboidRenderer.vertex(pose, vc, x1, y1, z0, uz0, vyTop, color, light, 1, 0, 0);
    CuboidRenderer.vertex(pose, vc, x1, y0, z0, uz0, vyBottom, color, light, 1, 0, 0);
  }

  private static double[] predictedHead(BlockEntityGantry quarry, double now) {
    double[] synced = { quarry.clientSyncedX, quarry.clientSyncedY, quarry.clientSyncedZ };
    double[] goal = new double[3];
    if (!quarry.headGoal(goal)) {
      return synced;
    }
    double[] out = new double[3];
    GantryHeadMotion.predict(synced, goal, quarry.armSpeed(), now - quarry.clientSyncTick, out);
    return out;
  }

  @Override
  public boolean shouldRenderOffScreen(T quarry) {
    return true;
  }

  @Override
  public boolean shouldRender(T quarry, Vec3 cameraPos) {
    double d = getViewDistance() * 4;
    return quarry.getRenderBoundingBox().distanceToSqr(cameraPos) < d * d;
  }
}
