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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.apache.commons.lang3.StringUtils;

import java.net.http.HttpRequest;
import java.util.*;

/**
 * 多女仆对话 LLM 回调。支持 2-N 人链式多轮。
 * 每轮 single LLM call → 替换思考气泡 → next round。
 */
public class InterMaidChatCallback extends LLMCallback {

    private final List<EntityMaid> participants;
    private int speakerIndex;
    private int roundCount;
    private final int totalRounds;
    private final Map<UUID, String> conversationHistory;
    private final MaidAIChatManager chatManager;
    private long waitingBubbleId = -1;

    public InterMaidChatCallback(
            MaidAIChatManager chatManager,
            List<LLMMessage> messages,
            List<EntityMaid> participants,
            int totalRounds
    ) {
        super(chatManager, messages, true);
        this.needAddTools = false;
        this.chatManager = chatManager;
        this.participants = participants;
        this.speakerIndex = 0;
        this.roundCount = 0;
        this.totalRounds = totalRounds;
        this.conversationHistory = new LinkedHashMap<>();
    }

    public void setWaitingBubbleId(long id) {
        this.waitingBubbleId = id;
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

            net.minecraft.server.MinecraftServer server = speaker.getServer();
            if (server != null) {
                server.submit(() -> {
                    if (waitingBubbleId >= 0) {
                        speaker.getChatBubbleManager().addLLMChatText(chatText, waitingBubbleId);
                    } else {
                        speaker.getChatBubbleManager().addTextChatBubble(chatText);
                    }
                });
            } else if (waitingBubbleId >= 0) {
                speaker.getChatBubbleManager().addLLMChatText(chatText, waitingBubbleId);
            } else {
                speaker.getChatBubbleManager().addTextChatBubble(chatText);
            }

            conversationHistory.put(speaker.getUUID(), chatText);
            roundCount++;

            if (roundCount >= totalRounds || participants.size() < 2) {
                finishConversation();
                return;
            }

            speakerIndex = pickNextSpeaker();
            sendNextRound();

            if (EnhancedConfig.debugLog()) {
                EnhancedLittleMaidAI.LOGGER.info(
                        "InterMaidChat: Round {} complete, speaker={}",
                        roundCount, speaker.getDisplayName().getString());
            }
        } catch (Exception e) {
            EnhancedLittleMaidAI.LOGGER.warn("InterMaidChat: onSuccess error", e);
            finishConversation();
        }
    }

    @Override
    public void onFailure(HttpRequest request, Throwable throwable, int errorCode) {
        EnhancedLittleMaidAI.LOGGER.warn("InterMaidChat: LLM call failed (round {})", roundCount);
        EntityMaid speaker = currentSpeaker();
        if (speaker != null && waitingBubbleId >= 0) {
            try {
                speaker.getChatBubbleManager().removeChatBubble(waitingBubbleId);
            } catch (Exception ignored) {
            }
        }
        finishConversation();
    }

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

        LLMClient client = chatManager.getLLMSite().client();
        InterMaidChatCallback nextCb = new InterMaidChatCallback(
                chatManager, msgs, participants, totalRounds);
        nextCb.speakerIndex = speakerIndex;
        nextCb.roundCount = roundCount;
        nextCb.conversationHistory.putAll(this.conversationHistory);

        int minSec = EnhancedConfig.INTER_MAID_ROUND_DELAY_MIN.get();
        int maxSec = EnhancedConfig.INTER_MAID_ROUND_DELAY_MAX.get();
        int delayMs = minSec * 1000 + (int)(Math.random() * (maxSec - minSec + 1) * 1000);

        new Thread(() -> {
            try { Thread.sleep(delayMs); } catch (InterruptedException ignored) {}
            net.minecraft.server.MinecraftServer server = speaker.getServer();
            if (server != null) {
                server.submit(() -> {
                    long bubbleId = speaker.getChatBubbleManager().addThinkingText("**少女们商量中...**");
                    nextCb.setWaitingBubbleId(bubbleId);
                    client.chat(nextCb);
                });
            } else {
                long bubbleId = speaker.getChatBubbleManager().addThinkingText("**少女们商量中...**");
                nextCb.setWaitingBubbleId(bubbleId);
                client.chat(nextCb);
            }
        }).start();
    }

    private void finishConversation() {
        if (participants.size() >= 2) {
            EntityMaid a = participants.get(0);
            EntityMaid b = participants.get(1);
            long gameTime = a.level().getGameTime();

            for (Map.Entry<UUID, String> entry : conversationHistory.entrySet()) {
                String speakerName = participants.stream()
                        .filter(m -> m.getUUID().equals(entry.getKey()))
                        .findFirst().map(m -> m.getDisplayName().getString())
                        .orElse("某女仆");

                String cleanText = stripDescription(entry.getValue());

                MindPalace pa = MindPalace.getOrCreate(a.getUUID());
                MindPalace pb = MindPalace.getOrCreate(b.getUUID());
                String memA = "和" + b.getDisplayName().getString() + "聊天，"
                        + speakerName + "说：" + cleanText;
                String memB = "和" + a.getDisplayName().getString() + "聊天，"
                        + speakerName + "说：" + cleanText;
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

    private int pickNextSpeaker() {
        int current = speakerIndex % participants.size();
        int size = participants.size();
        if (size <= 1) return current;
        int next;
        do {
            next = (int)(Math.random() * size);
        } while (next == current);
        return next;
    }

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
        sb.append(others.size() == 1 ? "她" : "她们");
        sb.append("聊几句。说一句简短自然的话主动发起对话。"
                + " " + PromptConstants.NO_ACTION_DESCRIPTION
                + " " + PromptConstants.TRANSLATE_ENGLISH_NAMES);
        return sb.toString();
    }

    private String buildResponsePrompt(String setting, EntityMaid speaker, List<EntityMaid> others) {
        StringBuilder sb = new StringBuilder(setting);
        sb.append("\n\n");
        for (Map.Entry<UUID, String> entry : conversationHistory.entrySet()) {
            String name = participants.stream()
                    .filter(m -> m.getUUID().equals(entry.getKey()))
                    .findFirst()
                    .map(m -> m.getDisplayName().getString())
                    .orElse("某女仆");
            sb.append(name).append("说：").append(entry.getValue()).append("\n");
        }
        sb.append("\n现在轮到你了。请简短自然地回应。"
                + " " + PromptConstants.NO_ACTION_DESCRIPTION
                + " " + PromptConstants.TRANSLATE_ENGLISH_NAMES);
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
            return custom;
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

    public static void startConversation(EntityMaid maidA, EntityMaid maidB, int maxRounds) {
        startConversation(List.of(maidA, maidB), maxRounds);
    }

    public static void startConversation(List<EntityMaid> participants, int maxRounds) {
        if (participants.size() < 2) return;
        participants = new ArrayList<>(participants);
        participants.sort(Comparator.comparingDouble(InterMaidChatCallback::nearestPlayerDist));
        EntityMaid first = participants.get(0);

        if (EnhancedConfig.debugLog()) {
            EnhancedLittleMaidAI.LOGGER.info(
                    "InterMaidChat: Starting conversation {} members, rounds={}",
                    participants.size(), maxRounds);
        }

        String prompt = buildGroupPrompt(first, participants);
        LLMMessage sysMsg = LLMMessage.systemChat(first, prompt);
        LLMMessage userMsg = LLMMessage.userChat(first, "（女仆间对话）");
        List<LLMMessage> msgs = List.of(sysMsg, userMsg);

        MaidAIChatManager mgr = first.getAiChatManager();
        InterMaidChatCallback cb = new InterMaidChatCallback(
                mgr, msgs, List.copyOf(participants), maxRounds);
        net.minecraft.server.MinecraftServer server = first.getServer();
        if (server != null) {
            server.submit(() -> {
                long bubbleId = first.getChatBubbleManager().addThinkingText("**少女们商量中...**");
                cb.setWaitingBubbleId(bubbleId);
                mgr.getLLMSite().client().chat(cb);
            });
        } else {
            long bubbleId = first.getChatBubbleManager().addThinkingText("**少女们商量中...**");
            cb.setWaitingBubbleId(bubbleId);
            mgr.getLLMSite().client().chat(cb);
        }
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
                + " " + PromptConstants.NO_ACTION_DESCRIPTION + "");
        return sb.toString();
    }

    /** 剥离描述文本（括号内容、动作描写），仅保留纯对话 */
    private static String stripDescription(String text) {
        if (StringUtils.isBlank(text)) return text;
        // 移除中文括号（...）
        text = text.replaceAll("（[^）]*）", "");
        // 移除英文括号 (...)
        text = text.replaceAll("\\([^)]*\\)", "");
        // 移除 *动作描写* 
        text = text.replaceAll("\\*[^*]*\\*", "");
        // 合并多余空格
        text = text.replaceAll("\\s{2,}", " ").trim();
        if (text.isEmpty()) return "...";
        return text;
    }

    private static double nearestPlayerDist(EntityMaid maid) {
        double dist = EnhancedConfig.INTER_MAID_PLAYER_DISTANCE.get();
        AABB box = maid.getBoundingBox().inflate(dist);
        return maid.level().getEntitiesOfClass(ServerPlayer.class, box, Player::isAlive)
                .stream().mapToDouble(p -> p.distanceToSqr(maid)).min().orElse(Double.MAX_VALUE);
    }
}
