package com.reglisseforge.utils;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;

/**
 * Centralized factory for creating an {@link AnthropicClient} with a validated API key.
 *
 * Resolution order:
 * - Environment variable: ANTHROPIC_API_KEY
 * - JVM system property: ANTHROPIC_API_KEY
 * - JVM system property: anthropic.api.key
 *
 * If the key is missing, throws an IllegalStateException with a clear setup message.
 */
public final class AnthropicClientFactory {

    private AnthropicClientFactory() {}

    

    public static AnthropicClient create() {
       
        return AnthropicOkHttpClient.builder()
                .apiKey(resolveApiKey())
                .build();
    }

    /** Returns the API key from env/system properties, or falls back to the default embedded key. */
    public static String resolveApiKey() {
        String key = System.getenv("ANTHROPIC_API_KEY");
        if (key == null || key.isBlank()) {
            key = System.getProperty("ANTHROPIC_API_KEY");
        }
        if (key == null || key.isBlank()) {
            key = System.getProperty("anthropic.api.key");
        }
        return key.trim();
    }

    public static String loadApiKey() {
        String key = System.getenv("ANTHROPIC_API_KEY");
        if (key == null || key.isBlank()) {
            key = System.getProperty("ANTHROPIC_API_KEY");
        }
        if (key == null || key.isBlank()) {
            key = System.getProperty("anthropic.api.key");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalStateException(
                    "Anthropic API key not found. Set environment variable ANTHROPIC_API_KEY or pass " +
                    "-Danthropic.api.key=YOUR_KEY (or -DANTHROPIC_API_KEY=YOUR_KEY) to the JVM.");
        }
        return key.trim();
    }
}


