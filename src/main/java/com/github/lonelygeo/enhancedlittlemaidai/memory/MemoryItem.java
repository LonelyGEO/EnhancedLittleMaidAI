package com.github.lonelygeo.enhancedlittlemaidai.memory;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Optional;
import java.util.UUID;

/**
 * 思维宫殿中的一条记忆。
 * 使用 Java record => equals/hashCode 自动生成，便于 Set 去重。
 */
public record MemoryItem(
        UUID id,
        MemoryCategory category,
        String content,
        Optional<BlockPos> location,
        Optional<String> dimension,
        long gameTime,
        int accessCount,
        int importance
) {
    public static final Codec<MemoryItem> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.STRING_CODEC.fieldOf("id").forGetter(MemoryItem::id),
            MemoryCategory.CODEC.fieldOf("category").forGetter(MemoryItem::category),
            Codec.STRING.fieldOf("content").forGetter(MemoryItem::content),
            BlockPos.CODEC.optionalFieldOf("location").forGetter(MemoryItem::location),
            Codec.STRING.optionalFieldOf("dimension").forGetter(MemoryItem::dimension),
            Codec.LONG.fieldOf("game_time").forGetter(MemoryItem::gameTime),
            Codec.INT.fieldOf("access_count").forGetter(MemoryItem::accessCount),
            Codec.INT.fieldOf("importance").forGetter(MemoryItem::importance)
    ).apply(instance, MemoryItem::new));

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("category", category.name());
        tag.putString("content", content);
        location.ifPresent(pos -> tag.putLong("location", pos.asLong()));
        dimension.ifPresent(dim -> tag.putString("dimension", dim));
        tag.putLong("gameTime", gameTime);
        tag.putInt("accessCount", accessCount);
        tag.putInt("importance", importance);
        return tag;
    }

    public static MemoryItem fromTag(CompoundTag tag) {
        return new MemoryItem(
                tag.getUUID("id"),
                MemoryCategory.valueOf(tag.getString("category")),
                tag.getString("content"),
                tag.contains("location") ? Optional.of(BlockPos.of(tag.getLong("location"))) : Optional.empty(),
                tag.contains("dimension") ? Optional.of(tag.getString("dimension")) : Optional.empty(),
                tag.getLong("gameTime"),
                tag.getInt("accessCount"),
                tag.getInt("importance")
        );
    }

    public MemoryItem incrementAccess() {
        return new MemoryItem(id, category, content, location, dimension,
                gameTime, accessCount + 1, importance);
    }

    /**
     * 淘汰评分：重要性 × 0.4 + 新鲜度 × 0.3 + 频率 × 0.3
     * score 越高越应**保留**；分数最低的优先淘汰。
     */
    public double evictionScore(long currentTime, long maxAgeInTicks) {
        double recency = 1.0 - Math.min(1.0, (double) (currentTime - gameTime) / maxAgeInTicks);
        double frequency = Math.min(1.0, accessCount / 10.0);
        return importance * 0.4 + recency * 0.3 + frequency * 0.3;
    }
}
