package com.example.springai.controller;

import java.util.List;
import java.util.Map;

import com.example.springai.tool.DemoTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final DemoTools demoTools;

    public ChatController(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory, DemoTools demoTools) {
        this.chatClient = chatClientBuilder.build();
        this.chatMemory = chatMemory;
        this.demoTools = demoTools;
    }

    @GetMapping
    public Map<String, String> chatWithQuery(@RequestParam(defaultValue = "Salom, Spring AI haqida qisqa yozing.")
                                             String message) {
        String prompt = normalizeMessage(message, "Salom");
        String response = this.chat(prompt);
        return Map.of("message", prompt, "response", response);
    }

    @PostMapping
    public Map<String, String> chatWithBody(@RequestBody ChatRequest request) {
        String prompt = normalizeMessage(request == null ? null : request.message(), "Salom");
        String response = this.chat(prompt);
        return Map.of("message", prompt, "response", response);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@RequestParam(defaultValue = "Spring AI stream javob bering.") String message) {
        String prompt = normalizeMessage(message, "Salom");
        return this.chatClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .onErrorResume(ex -> Flux.just(streamFriendlyError(ex)));
    }

    @PostMapping("/structured")
    public StudyPlan structured(@RequestBody StructuredRequest request) {
        String topic = normalizeMessage(request == null ? null : request.topic(), "Spring AI");
        return this.chatClient.prompt()
                .system("""
                        You are a planner assistant.
                        Return concise output matching the JSON schema exactly.
                        """)
                .user("Mavzu: " + topic + ". 4 ta qadamli study-plan tuzib ber.")
                .call()
                .entity(StudyPlan.class);
    }

    @PostMapping("/memory/{conversationId}")
    public Map<String, String> chatWithMemory(@PathVariable String conversationId, @RequestBody ChatRequest request) {
        String prompt = normalizeMessage(request == null ? null : request.message(), "Salom");

        MessageChatMemoryAdvisor memoryAdvisor = MessageChatMemoryAdvisor.builder(this.chatMemory)
                .conversationId(normalizeMessage(conversationId, ChatMemory.DEFAULT_CONVERSATION_ID))
                .build();

        String response = this.chatClient.prompt()
                .advisors(memoryAdvisor)
                .user(prompt)
                .call()
                .content();

        return Map.of(
                "conversationId", normalizeMessage(conversationId, ChatMemory.DEFAULT_CONVERSATION_ID),
                "message", prompt,
                "response", response
        );
    }

    @DeleteMapping("/memory/{conversationId}")
    public Map<String, String> clearConversation(@PathVariable String conversationId) {
        String normalizedConversationId = normalizeMessage(conversationId, ChatMemory.DEFAULT_CONVERSATION_ID);
        this.chatMemory.clear(normalizedConversationId);
        return Map.of("conversationId", normalizedConversationId, "status", "cleared");
    }

    @PostMapping("/tool")
    public Map<String, String> chatWithTool(@RequestBody ChatRequest request) {
        String prompt = normalizeMessage(request == null ? null : request.message(), "Hozirgi vaqtni ayt.");

        String response = this.chatClient.prompt()
                .system("Agar savolga yordam bersa, tool'lardan foydalan.")
                .tools(this.demoTools)
                .user(prompt)
                .call()
                .content();

        return Map.of("message", prompt, "response", response);
    }

    private String chat(String message) {
        return this.chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    private String normalizeMessage(String message, String fallback) {
        return StringUtils.hasText(message) ? message.trim() : fallback;
    }

    private String streamFriendlyError(Throwable throwable) {
        String msg = throwable == null ? "" : String.valueOf(throwable.getMessage()).toLowerCase();

        if (msg.contains("insufficient_quota") || msg.contains("current quota")) {
            return "Xatolik: OpenAI quota tugagan. Billing/plan ni tekshiring.";
        }

        if (msg.contains("invalid_api_key") || msg.contains("incorrect api key") || msg.contains("unauthorized")) {
            return "Xatolik: API key noto'g'ri yoki ruxsat yo'q.";
        }

        return "Xatolik: Stream chaqiruvda muammo bo'ldi. Keyinroq qayta urinib ko'ring.";
    }

    public record ChatRequest(String message) {
    }

    public record StructuredRequest(String topic) {
    }

    public record StudyPlan(String title, List<String> steps, String riskLevel, String firstAction) {
    }
}
