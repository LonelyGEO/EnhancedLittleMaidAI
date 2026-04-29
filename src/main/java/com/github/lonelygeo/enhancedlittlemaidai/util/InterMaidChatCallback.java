package com.github.lonelygeo.enhancedlittlemaidai.util;

import com.github.lonelygeo.enhancedlittlemaidai.EnhancedLittleMaidAI;
import com.github.lonelygeo.enhancedlittlemaidai.config.EnhancedConfig;
import com.github.lonelygeo.enhancedlittlemaidai.memory.MindPalace;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.LLMCallback;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.entity.MaidAIChatManager;
import com.github.tartaricacid.touhoulittlemaid.ai.manager.response.ResponseChat;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMClient;
import com.github.tartaricacid.touhoulittlemaid.ai.service.llm.LLMMessage;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.apache.commons.lang3.StringUtils;

import java.net.http.HttpRequest;
import java.util.*;

/**
 * 多女仆对话 LLM 回调。支持 2-N 人链式多轮。
 * 每轮 single LLM call → display bubble + broadcast → next round。
 */
public class InterMaidChatCallback extends LLMCallback {

    private final List<EntityMaid> participants;
    private int speakerIndex;
    private int roundCount;
    private final int totalRounds;
    private final Map<UUID, String> conversationHistory;
    private final MaidAIChatManager chatManager;
    private final List<LLMMessage> baseMessages;

    /**
     * 2 人构造器。
     */
    public InterMaidChatCallback(
            MaidAIChatManager chatManager,
            List<LLMMessage> messages,
            EntityMaid maidA,
            EntityMaid maidB,
            int totalRounds
    ) {
        this(chatManager, messages, List.of(maidA, maidB), totalRounds);
    }

    /**
     * N 人构造器（未来 2+ 版本直接使用）。
     */
    private InterMaidChatCallback(
            MaidAIChatManager chatManager,
            List<LLMMessage> messages,
            List<EntityMaid> participants,
            int totalRounds
    ) {
        super(chatManager, messages, true);
        this.needAddTools = false;
        this.chatManager = chatManager;
        this.baseMessages = messages;
        this.participants = participants;
        this.speakerIndex = 0;
        this.roundCount = 0;
        this.totalRounds = totalRounds;
        this.conversationHistory = new LinkedHashMap<>();
    }

    @Override
    public void onSuccess(ResponseChat responseChat) {
        try {
            String chatText = responseChat.getChatText();
            if (StringUtils.isBlank(chatText)) {
                finishConversation();
                return;
            }

            EntityMaid speaker = currentSpeaker();
            if (speaker == null || speaker.isRemoved()) {
                finishConversation();
                return;
            }

            // 1. 显示气泡
            speaker.getChatBubbleManager().addTextChatBubble(chatText);

            // 2. 推送聊天栏给附近玩家
            double playerDist = EnhancedConfig.INTER_MAID_PLAYER_DISTANCE.get();
            broadcastToNearbyPlayers(speaker, chatText, playerDist);

            // 3. 记录对话历史
            conversationHistory.put(speaker.getUUID(), chatText);
            roundCount++;

            // 4. 检查是否继续
            if (roundCount >= totalRounds || participants.size() < 2) {
                finishConversation();
                return;
            }

            // 5. 发起下一轮
            speakerIndex++;
            sendNextRound();

            if (EnhancedConfig.debugLog()) {
                EnhancedLittleMaidAI.LOGGER.info(
                        "InterMaidChat: Round {} complete, speaker={}, next={}",
                        roundCount, speaker.getDisplayName().getString(),
                        currentSpeaker() != null ? currentSpeaker().getDisplayName().getString() : "none");
            }
        } catch (Exception e) {
            EnhancedLittleMaidAI.LOGGER.warn("InterMaidChat: onSuccess error", e);
            finishConversation();
        }
    }

    @Override
    public void onFailure(HttpRequest request, Throwable throwable, int errorCode) {
        EnhancedLittleMaidAI.LOGGER.warn("InterMaidChat: LLM call failed (round {})", roundCount);
        finishConversation();
    }

    /** 发起下一轮 LLM 调用 */
    private void sendNextRound() {
        EntityMaid speaker = currentSpeaker();
        if (speaker == null) {
            finishConversation();
            return;
        }
        String prompt = buildPromptForSpeaker(speaker);
        LLMMessage sysMsg = LLMMessage.systemChat(speaker, prompt);
        LLMMessage userMsg = LLMMessage.userChat(speaker, "（女仆间对话第" + (roundCount + 1) + "轮）");
        List<LLMMessage> msgs = List.of(sysMsg, userMsg);

        long bubbleId = speaker.getChatBubbleManager().addThinkingText("...");
        LLMClient client = chatManager.getLLMSite().client();
        InterMaidChatCallback nextCb = new InterMaidChatCallback(
                chatManager, msgs, participants, totalRounds);
        nextCb.speakerIndex = speakerIndex;
        nextCb.roundCount = roundCount;
        nextCb.conversationHistory.putAll(conversationHistory);
        client.chat(nextCb);
    }

    /** 对话结束清理 */
    private void finishConversation() {
        if (participants.size() >= 2) {
            EntityMaid a = participants.get(0);
            EntityMaid b = participants.get(1);
            long gameTime = a.level().getGameTime();

            // 写入社交记忆
            for (Map.Entry<UUID, String> entry : conversationHistory.entrySet()) {
                String speakerName = participants.stream()
                        .filter(m -> m.getUUID().equals(entry.getKey()))
                        .findFirst().map(m -> m.getDisplayName().getString())
                        .orElse("某女仆");

                MindPalace pa = MindPalace.getOrCreate(a.getUUID());
                MindPalace pb = MindPalace.getOrCreate(b.getUUID());
                String memA = "和" + b.getDisplayName().getString() + "聊天，"
                        + speakerName + "说：" + entry.getValue();
                String memB = "和" + a.getDisplayName().getString() + "聊天，"
                        + speakerName + "说：" + entry.getValue();
                pa.addSocialMemory(memA, gameTime);
                pb.addSocialMemory(memB, gameTime);
            }

            InterMaidChatManager.releaseBusy(a.getUUID(), b.getUUID());
            InterMaidChatManager.markTriggered(a.getUUID(), b.getUUID(), gameTime);

            if (EnhancedConfig.debugLog()) {
                EnhancedLittleMaidAI.LOGGER.info(
                        "InterMaidChat: Conversation finished {}↔{}, rounds completed={}/{}",
                        a.getUUID(), b.getUUID(), roundCount, totalRounds);
            }
        }
    }

    private EntityMaid currentSpeaker() {
        if (participants.isEmpty()) return null;
        return participants.get(speakerIndex % participants.size());
    }

    // ==================== Prompt 构建 ====================

    private String buildPromptForSpeaker(EntityMaid speaker) {
        String setting = getCharacterSetting(speaker);
        if (StringUtils.isBlank(setting)) return "";

        List<EntityMaid> others = participants.stream()
                .filter(m -> m != speaker).toList();

        return roundCount == 0
                ? buildOpeningPrompt(setting, speaker, others)
                : buildResponsePrompt(setting, speaker, others);
    }

    private String buildOpeningPrompt(String setting, EntityMaid speaker, List<EntityMaid> others) {
        StringBuilder sb = new StringBuilder(setting);
        sb.append("\n\n你现在和");
        for (int i = 0; i < others.size(); i++) {
            if (i > 0) sb.append("、");
            sb.append(others.get(i).getDisplayName().getString());
        }
        sb.append("在一起。请和");
        if (others.size() == 1) {
            sb.append("她");
        } else {
            sb.append("她们");
        }
        sb.append("聊几句。说一句简短自然的话主动发起对话。"
                + "直接说话即可，不要加动作描写、括号注释或任何格式标记。");
        return sb.toString();
    }

    private String buildResponsePrompt(String setting, EntityMaid speaker, List<EntityMaid> others) {
        StringBuilder sb = new StringBuilder(setting);
        sb.append("\n\n");
        // 累积的对话上下文
        for (Map.Entry<UUID, String> entry : conversationHistory.entrySet()) {
            String name = participants.stream()
                    .filter(m -> m.getUUID().equals(entry.getKey()))
                    .findFirst()
                    .map(m -> m.getDisplayName().getString())
                    .orElse("某女仆");
            sb.append(name).append("说：").append(entry.getValue()).append("\n");
        }
        sb.append("\n现在轮到你了。请简短自然地回应。"
                + "直接说话即可，不要加动作描写、括号注释或任何格式标记。");
        return sb.toString();
    }

    private static String getCharacterSetting(EntityMaid maid) {
        String mode = EnhancedConfig.INTER_MAID_PROMPT_MODE.get();
        if ("MINIMAL".equals(mode)) {
            return maid.getDisplayName().getString() + "，一位女仆。";
        }

        String custom = maid.getAiChatManager().customSetting;
        if (StringUtils.isNotBlank(custom)) {
            if ("SUMMARY".equals(mode)) {
                return custom.substring(0, Math.min(200, custom.length()));
            }
            return custom; // FULL
        }

        try {
            var optSetting = maid.getAiChatManager().getSetting();
            if (optSetting.isPresent()) {
                String raw = optSetting.get().getSetting(maid, "zh_cn");
                if ("SUMMARY".equals(mode)) {
                    return raw.substring(0, Math.min(200, raw.length()));
                }
                return raw;
            }
        } catch (Exception ignored) {
        }

        return maid.getDisplayName().getString() + "，一位女仆。";
    }

    // ==================== 聊天栏推送 ====================

    private static void broadcastToNearbyPlayers(EntityMaid speaker, String text, double range) {
        AABB box = speaker.getBoundingBox().inflate(range);
        List<ServerPlayer> players = speaker.level().getEntitiesOfClass(
                ServerPlayer.class, box, Player::isAlive);
        Component msg = Component.literal("<" + speaker.getDisplayName().getString() + "> " + text);
        for (ServerPlayer p : players) {
            p.sendSystemMessage(msg);
        }
    }

    // ==================== 发起对话入口 ====================

    /**
     * 启动两女仆对话。由 EntityMaidMixin 调用。
     */
    public static void startConversation(EntityMaid maidA, EntityMaid maidB, int maxRounds) {
        if (EnhancedConfig.debugLog()) {
            EnhancedLittleMaidAI.LOGGER.info(
                    "InterMaidChat: Starting conversation {} ↔ {}, rounds={}",
                    maidA.getUUID(), maidB.getUUID(), maxRounds);
        }
        List<EntityMaid> sorted = sortByPlayerDistance(maidA, maidB);
        InterMaidChatManager.markBusy(sorted.get(0).getUUID(), sorted.get(1).getUUID());

        EntityMaid first = sorted.get(0);
        String prompt = buildInitPrompt(first, sorted.get(1));
        LLMMessage sysMsg = LLMMessage.systemChat(first, prompt);
        LLMMessage userMsg = LLMMessage.userChat(first, "（女仆间对话）");
        List<LLMMessage> msgs = List.of(sysMsg, userMsg);

        MaidAIChatManager mgr = first.getAiChatManager();
        long bubbleId = first.getChatBubbleManager().addThinkingText("...");

        InterMaidChatCallback cb = new InterMaidChatCallback(
                mgr, msgs, sorted, maxRounds);
        mgr.getLLMSite().client().chat(cb);
    }

    /**
     * 启动多人女仆对话。参与者按距玩家距离排序，最近者先开口。
     */
    public static void startConversation(List<EntityMaid> participants, int maxRounds) {
        if (participants.size() < 2) return;
        participants = new ArrayList<>(participants);
        participants.sort(Comparator.comparingDouble(InterMaidChatCallback::nearestPlayerDist));
        EntityMaid first = participants.get(0);

        if (EnhancedConfig.debugLog()) {
            EnhancedLittleMaidAI.LOGGER.info(
                    "InterMaidChat: Starting group conversation {} members, rounds={}",
                    participants.size(), maxRounds);
        }

        String prompt = buildGroupPrompt(first, participants);
        LLMMessage sysMsg = LLMMessage.systemChat(first, prompt);
        LLMMessage userMsg = LLMMessage.userChat(first, "（女仆间对话）");
        List<LLMMessage> msgs = List.of(sysMsg, userMsg);

        MaidAIChatManager mgr = first.getAiChatManager();
        mgr.getLLMSite().client().chat(
                new InterMaidChatCallback(mgr, msgs, List.copyOf(participants), maxRounds));
    }

    private static String buildGroupPrompt(EntityMaid speaker, List<EntityMaid> all) {
        List<EntityMaid> others = all.stream().filter(m -> m != speaker).toList();
        StringBuilder sb = new StringBuilder(getCharacterSetting(speaker));
        sb.append("\n\n你现在和");
        for (int i = 0; i < others.size(); i++) {
            if (i > 0) sb.append("、");
            sb.append(others.get(i).getDisplayName().getString());
        }
        sb.append("在一起。请主动和她们聊几句。说一句简短自然的话。"
                + "直接说话即可，不要加动作描写、括号注释或任何格式标记。");
        return sb.toString();
    }

    private static double nearestPlayerDist(EntityMaid maid) {
        double dist = EnhancedConfig.INTER_MAID_PLAYER_DISTANCE.get();
        AABB box = maid.getBoundingBox().inflate(dist);
        return maid.level().getEntitiesOfClass(ServerPlayer.class, box, Player::isAlive)
                .stream().mapToDouble(p -> p.distanceToSqr(maid)).min().orElse(Double.MAX_VALUE);
    }

    /** 按距附近玩家距离排序，最近的先发言 */
    private static List<EntityMaid> sortByPlayerDistance(EntityMaid a, EntityMaid b) {
        double aDist = nearestPlayerDist(a);
        double bDist = nearestPlayerDist(b);
        return aDist <= bDist ? List.of(a, b) : List.of(b, a);
    }

    private static String buildInitPrompt(EntityMaid speaker, EntityMaid other) {
        String setting = getCharacterSetting(speaker);
        return setting + "\n\n你看到了" + other.getDisplayName().getString()
                + "。请和她聊几句。说一句简短自然的话主动发起对话。"
                + "直接说话即可，不要加动作描写、括号注释或任何格式标记。";
    }
}
