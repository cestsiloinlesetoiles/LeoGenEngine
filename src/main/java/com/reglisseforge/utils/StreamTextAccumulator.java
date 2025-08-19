package com.reglisseforge.utils;

import com.anthropic.models.messages.RawMessageStreamEvent;
import java.util.function.Consumer;

public final class StreamTextAccumulator {
    private final StringBuilder buf = new StringBuilder();
    private final Consumer<String> chunkConsumer;

    public StreamTextAccumulator() {
        this.chunkConsumer = null;
    }
    
    public StreamTextAccumulator(Consumer<String> chunkConsumer) {
        this.chunkConsumer = chunkConsumer;
    }

    /** Appelé pour chaque chunk d'événement */
    public void onEvent(RawMessageStreamEvent event) {
        event.contentBlockDelta().stream()
            .flatMap(d -> d.delta().text().stream())
            .forEach(textBlock -> {
                String text = textBlock.text();
                buf.append(text);
                
                // Émettre le chunk tel quel si un consumer est configuré
                if (chunkConsumer != null) {
                    chunkConsumer.accept(text);
                }
            });
    }

    public String getText() {
        return buf.toString();
    }
}