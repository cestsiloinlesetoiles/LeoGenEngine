package com.reglisseforge.utils;

import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.TextBlock;

import java.util.stream.Collectors;

public final class AnthropicUtils {
    /** Concatène tous les TextBlock de la réponse en une seule String. */
    public static String getAssistantText(Message message) {
        return message.content().stream()                  // <-- pas getContent(), c'est content()
                .flatMap(cb -> cb.text().stream())         // Optional<TextBlock> -> Stream<TextBlock>
                .map(TextBlock::text)                      // TextBlock -> String
                .collect(Collectors.joining());
    }

    /** Récupère uniquement le premier TextBlock (pratique pour de petites réponses). */
    public static String getFirstText(Message message) {
        return message.content().stream()
                .flatMap(cb -> cb.text().stream())
                .map(TextBlock::text)
                .findFirst()
                .orElse("");
    }



}
