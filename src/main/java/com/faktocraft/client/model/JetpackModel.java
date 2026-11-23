package com.faktocraft.client.model;

import com.faktocraft.Faktocraft;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

public class JetpackModel extends HumanoidModel<LivingEntity> {

  public static final ModelLayerLocation LAYER = new ModelLayerLocation(
      new ResourceLocation(Faktocraft.MODID, "jetpack"), "main");

  private static JetpackModel instance;

  public JetpackModel(ModelPart root) {
    super(root);
  }

  public static JetpackModel get() {
    if (instance == null) {
      instance = new JetpackModel(Minecraft.getInstance().getEntityModels().bakeLayer(LAYER));
    }
    return instance;
  }

  public static LayerDefinition createLayer() {
    MeshDefinition mesh = HumanoidModel.createMesh(new CubeDeformation(1.0F), 0.0F);
    PartDefinition body = mesh.getRoot().getChild("body");
    for (int i = 0; i < 2; i++) {
      float x0 = i == 0 ? -3.5F : 0.5F;
      body.addOrReplaceChild("tank_" + i, CubeListBuilder.create()
          .texOffs(40, 0).addBox(x0, 1.5F, 3.2F, 3, 9, 3)
          .texOffs(52, 0).addBox(x0 + 0.5F, 0.5F, 3.7F, 2, 1, 2)
          .texOffs(56, 20).addBox(x0 + 0.5F, 10.5F, 3.5F, 2, 2, 2), PartPose.ZERO);
    }
    body.addOrReplaceChild("tank_bar", CubeListBuilder.create()
        .texOffs(52, 16).addBox(-1.0F, 3.5F, 3.4F, 2, 3, 1), PartPose.ZERO);
    return LayerDefinition.create(mesh, 64, 32);
  }
}
