package io.github.jianfeitu.stackarium.assistant;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AssistantModel {
    private final String baseUrl;
    private final String apiKey;
    private final String modelName;
    private volatile StreamingChatModel model;

    public AssistantModel(@Value("${STACKARIUM_AI_BASE_URL:}") String baseUrl,
                          @Value("${STACKARIUM_AI_API_KEY:}") String apiKey,
                          @Value("${STACKARIUM_AI_MODEL:}") String modelName) {
        this.baseUrl = baseUrl.trim();
        this.apiKey = apiKey.trim();
        this.modelName = modelName.trim();
    }

    public boolean configured() { return !baseUrl.isBlank() && !apiKey.isBlank() && !modelName.isBlank(); }

    public StreamingChatModel get() {
        if (!configured()) throw new io.github.jianfeitu.stackarium.common.ApiException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "AI_NOT_CONFIGURED", "AI 模型尚未配置");
        if (model == null) synchronized (this) {
            if (model == null) model = OpenAiStreamingChatModel.builder().baseUrl(baseUrl).apiKey(apiKey)
                    .modelName(modelName).timeout(Duration.ofSeconds(35)).build();
        }
        return model;
    }
}
