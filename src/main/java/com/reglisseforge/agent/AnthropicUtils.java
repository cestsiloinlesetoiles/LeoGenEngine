package com.reglisseforge.agent;

import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.TextBlock;

import java.util.stream.Collectors;

public final class AnthropicUtils {
    /** Concatenates all TextBlocks from the response into a single String. */
    public static String getAssistantText(Message message) {
        return message.content().stream()                  // <-- not getContent(), it's content()
                .flatMap(cb -> cb.text().stream())         // Optional<TextBlock> -> Stream<TextBlock>
                .map(TextBlock::text)                      // TextBlock -> String
                .collect(Collectors.joining());
    }

    /** Gets only the first TextBlock (useful for small responses). */
    public static String getFirstText(Message message) {
        return message.content().stream()
                .flatMap(cb -> cb.text().stream())
                .map(TextBlock::text)
                .findFirst()
                .orElse("");
    }



}
