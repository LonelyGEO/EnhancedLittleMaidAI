package com.github.lonelygeo.enhancedlittlemaidai.memory;

import com.mojang.serialization.Codec;

public enum MemoryCategory {
    PLACE("地点"),
    PERSON("人物"),
    EVENT("事件"),
    PREFERENCE("偏好"),
    KNOWLEDGE("知识");

    private final String displayName;

    MemoryCategory(String displayName) { this.displayName = displayName; }

    public String displayName() { return displayName; }

    public static final Codec<MemoryCategory> CODEC =
            Codec.STRING.xmap(MemoryCategory::valueOf, MemoryCategory::name);
}
