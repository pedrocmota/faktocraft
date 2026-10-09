package com.faktocraft.mixin;

import com.faktocraft.common.subscribers.ModEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityFireMixin {
  @Inject(method = "hurtServer", at = @At("HEAD"))
  private void faktocraftFireProtection(ServerLevel level, DamageSource source, float damage,
      CallbackInfoReturnable<Boolean> cir) {
    LivingEntity self = (LivingEntity) (Object) this;
    if (source.is(DamageTypeTags.IS_FIRE) && self.hasEffect(MobEffects.FIRE_RESISTANCE)
        && !self.isInvulnerableTo(level, source) && !self.isDeadOrDying()) {
      ModEvents.fireProtection(self, source);
    }
  }
}
