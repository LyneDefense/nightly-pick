package com.nightlypick.server.conversation.application;

import java.util.regex.Pattern;

public final class ConversationTextSanitizer {
    private static final String HIDDEN_BLOCK_TAGS = "(?:think|analysis|reasoning|scratchpad|tool_call|minimax:tool_call|parameter)";
    private static final Pattern HIDDEN_BLOCK_PATTERN = Pattern.compile(
            "(?is)<" + HIDDEN_BLOCK_TAGS + "\\b[^>]*>[\\s\\S]*?</" + HIDDEN_BLOCK_TAGS + ">"
    );
    private static final Pattern UNTERMINATED_HIDDEN_BLOCK_PATTERN = Pattern.compile(
            "(?is)<" + HIDDEN_BLOCK_TAGS + "\\b[^>]*>[\\s\\S]*$"
    );
    private static final Pattern HIDDEN_TAG_PATTERN = Pattern.compile(
            "(?is)</?" + HIDDEN_BLOCK_TAGS + "\\b[^>]*>"
    );

    private ConversationTextSanitizer() {
    }

    public static String sanitizeAssistantText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "嗯，我在。";
        }
        String text = rawText.replace("\r\n", "\n").trim();
        text = HIDDEN_BLOCK_PATTERN.matcher(text).replaceAll("");
        text = UNTERMINATED_HIDDEN_BLOCK_PATTERN.matcher(text).replaceAll("");
        text = HIDDEN_TAG_PATTERN.matcher(text).replaceAll("");
        text = text.replaceAll("(?m)^\\s*$\\n", "");
        text = text.replaceAll("\\n{3,}", "\n\n").trim();
        return text.isBlank() ? "嗯，我在。" : text;
    }
}
