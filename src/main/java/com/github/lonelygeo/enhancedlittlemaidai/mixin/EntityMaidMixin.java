package com.github.lonelygeo.enhancedlittlemaidai.mixin;

import com.github.lonelygeo.enhancedlittlemaidai.memory.MemoryCategory;
import com.github.lonelygeo.enhancedlittlemaidai.memory.MemoryItem;
import com.github.lonelygeo.enhancedlittlemaidai.memory.MindPalace;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.UUID;

/**
 * 女仆死亡时记录死亡记忆到 MindPalace；移除时清理全局 Map。
 */
@Mixin(value = EntityMaid.class, remap = false)
public abstract class EntityMaidMixin {

    /**
     * HEAD: 死亡时在 NBT 保存前写入死亡记忆。
     */
    @Inject(method = "remove", at = @At("HEAD"), remap = false)
    private void enhanced$onRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        if (reason != Entity.RemovalReason.KILLED) return;

        EntityMaid maid = (EntityMaid) (Object) this;
        MindPalace palace = MindPalace.get(maid.getUUID());
        if (palace == null) return;

        String cause = "未知原因";
        try {
            var ds = maid.getLastDamageSource();
            if (ds != null) {
                var entity = ds.getEntity();
                if (entity != null) {
                    cause = entity.getDisplayName().getString();
                } else {
                    cause = ds.getMsgId();
                }
            }
        } catch (Exception ignored) {
        }

        String heldItem = "空手";
        try {
            var mainHand = maid.getMainHandItem();
            if (mainHand != null && !mainHand.isEmpty()) {
                heldItem = mainHand.getDescriptionId();
            }
        } catch (Exception ignored) {
        }

        String task = "无";
        try {
            var currentTask = maid.getTask();
            if (currentTask != null) {
                task = currentTask.getUid().toString();
            }
        } catch (Exception ignored) {
        }

        String deathContent = String.format("被 %s 击杀 手持%s 任务:%s", cause, heldItem, task);

        palace.addMemory(new MemoryItem(
                UUID.randomUUID(),
                MemoryCategory.EVENT,
                deathContent,
                Optional.of(maid.blockPosition()),
                Optional.ofNullable(maid.level().dimension().location().toString()),
                maid.level().getGameTime(),
                0,
                5
        ));
    }

    /**
     * TAIL: 清理全局 Map，防止内存泄漏。
     */
    @Inject(method = "remove", at = @At("TAIL"), remap = false)
    private void enhanced$cleanupMindPalace(Entity.RemovalReason reason, CallbackInfo ci) {
        MindPalace.remove(((EntityMaid) (Object) this).getUUID());
    }
}
