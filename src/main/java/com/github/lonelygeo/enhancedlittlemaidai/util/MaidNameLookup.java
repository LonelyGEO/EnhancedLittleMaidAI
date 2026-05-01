package com.github.lonelygeo.enhancedlittlemaidai.util;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 按女仆名称模糊查找实体。
 * 用于命令系统中将字符串名转换为 EntityMaid 引用。
 */
public final class MaidNameLookup {

    private MaidNameLookup() {}

    @Nullable
    public static EntityMaid find(String name, CommandSourceStack src) {
        if (StringUtils.isBlank(name)) return null;

        ServerLevel level = src.getLevel();
        Vec3 pos = src.getPosition();
        AABB box = new AABB(pos.x - 64, pos.y - 64, pos.z - 64,
                            pos.x + 64, pos.y + 64, pos.z + 64);
        List<EntityMaid> all = level.getEntitiesOfClass(
                EntityMaid.class, box,
                m -> m.isAlive() && !m.isRemoved());

        if (all.isEmpty()) return null;

        // 精确匹配
        for (EntityMaid m : all) {
            if (m.getDisplayName().getString().equals(name)) return m;
        }
        // 模糊匹配
        for (EntityMaid m : all) {
            if (m.getDisplayName().getString().contains(name)) return m;
        }
        final String lower = name.toLowerCase();
        for (EntityMaid m : all) {
            if (m.getDisplayName().getString().toLowerCase().contains(lower)) return m;
        }
        return null;
    }
}
