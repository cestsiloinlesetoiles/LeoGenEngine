package com.reglisseforge.utils;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCountTokensParams;
import com.anthropic.models.messages.Model;

public class TokenCounter {

    public static long countTokens(String text, Model model, AnthropicClient client ) {
        MessageCountTokensParams countTokensParams = MessageCountTokensParams.builder()
                .model(model)
                .addUserMessage(text)
                .build();

        return client.messages().countTokens(countTokensParams).inputTokens();
    }
}
