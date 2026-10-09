package com.faktocraft.mixin;

import com.faktocraft.common.util.LegacyPersistentData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityLegacyDataMixin {
  @Inject(method = "load", at = @At("TAIL"))
  private void faktocraftLegacyForgeData(ValueInput input, CallbackInfo ci) {
    input.read(LegacyPersistentData.LEGACY_KEY, CompoundTag.CODEC)
        .ifPresent(legacy -> LegacyPersistentData.merge((Entity) (Object) this, legacy));
  }
}
