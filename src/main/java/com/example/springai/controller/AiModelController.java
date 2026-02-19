package com.example.springai.controller;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.audio.tts.TextToSpeechModel;
import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.moderation.Categories;
import org.springframework.ai.moderation.CategoryScores;
import org.springframework.ai.moderation.Moderation;
import org.springframework.ai.moderation.ModerationModel;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.moderation.ModerationResult;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/ai")
public class AiModelController {

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;
    private final SimpleVectorStore vectorStore;
    private final ImageModel imageModel;
    private final ModerationModel moderationModel;
    private final TextToSpeechModel textToSpeechModel;
    private final TranscriptionModel transcriptionModel;

    public AiModelController(ChatClient.Builder chatClientBuilder,
                             ObjectProvider<EmbeddingModel> embeddingModelProvider,
                             ObjectProvider<SimpleVectorStore> vectorStoreProvider,
                             ObjectProvider<ImageModel> imageModelProvider,
                             ObjectProvider<ModerationModel> moderationModelProvider,
                             ObjectProvider<TextToSpeechModel> textToSpeechModelProvider,
                             ObjectProvider<TranscriptionModel> transcriptionModelProvider) {
        this.chatClient = chatClientBuilder.build();
        this.embeddingModel = embeddingModelProvider.getIfAvailable();
        this.vectorStore = vectorStoreProvider.getIfAvailable();
        this.imageModel = imageModelProvider.getIfAvailable();
        this.moderationModel = moderationModelProvider.getIfAvailable();
        this.textToSpeechModel = textToSpeechModelProvider.getIfAvailable();
        this.transcriptionModel = transcriptionModelProvider.getIfAvailable();
    }

    @GetMapping("/features")
    public Map<String, Boolean> availableFeatures() {
        return Map.of(
                "chat", true,
                "embedding", this.embeddingModel != null,
                "vectorStore", this.vectorStore != null,
                "image", this.imageModel != null,
                "moderation", this.moderationModel != null,
                "textToSpeech", this.textToSpeechModel != null,
                "transcription", this.transcriptionModel != null
        );
    }

    @PostMapping("/embedding")
    public EmbeddingResult embedding(@RequestBody TextRequest request) {
        EmbeddingModel model = requireFeature(this.embeddingModel, "Embedding");
        String text = requireText(request == null ? null : request.text(), "text");

        float[] vector = model.embed(text);
        return new EmbeddingResult(text, vector.length, preview(vector, 12));
    }

    @PostMapping("/vector/index")
    public Map<String, Object> indexDocuments(@RequestBody VectorIndexRequest request) {
        SimpleVectorStore store = requireFeature(this.vectorStore, "Vector store");
        List<VectorDocumentInput> inputDocuments = request == null || request.documents() == null
                ? List.of()
                : request.documents();

        if (inputDocuments.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "documents list is required");
        }

        List<Document> documents = new ArrayList<>();
        for (VectorDocumentInput input : inputDocuments) {
            if (!StringUtils.hasText(input.text())) {
                continue;
            }

            String id = StringUtils.hasText(input.id()) ? input.id().trim() : UUID.randomUUID().toString();
            Map<String, Object> metadata = input.metadata() == null ? Map.of() : input.metadata();

            documents.add(Document.builder()
                    .id(id)
                    .text(input.text().trim())
                    .metadata(metadata)
                    .build());
        }

        if (documents.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no valid document text provided");
        }

        store.add(documents);

        return Map.of(
                "indexed", documents.size(),
                "ids", documents.stream().map(Document::getId).toList()
        );
    }

    @PostMapping("/vector/search")
    public List<VectorSearchItem> similaritySearch(@RequestBody VectorSearchRequest request) {
        SimpleVectorStore store = requireFeature(this.vectorStore, "Vector store");
        String query = requireText(request == null ? null : request.query(), "query");

        int topK = request == null || request.topK() == null || request.topK() <= 0 ? 4 : request.topK();

        SearchRequest.Builder searchBuilder = SearchRequest.builder()
                .query(query)
                .topK(topK);

        if (request == null || request.similarityThreshold() == null) {
            searchBuilder.similarityThresholdAll();
        }
        else {
            searchBuilder.similarityThreshold(request.similarityThreshold());
        }

        List<Document> foundDocuments = store.similaritySearch(searchBuilder.build());
        return foundDocuments.stream().map(this::toVectorSearchItem).toList();
    }

    @PostMapping("/rag/ask")
    public RagAnswer ragAsk(@RequestBody RagRequest request) {
        SimpleVectorStore store = requireFeature(this.vectorStore, "Vector store");
        String question = requireText(request == null ? null : request.question(), "question");
        int topK = request == null || request.topK() == null || request.topK() <= 0 ? 4 : request.topK();

        List<Document> contextDocuments = store.similaritySearch(
                SearchRequest.builder()
                        .query(question)
                        .topK(topK)
                        .similarityThresholdAll()
                        .build()
        );

        if (contextDocuments.isEmpty()) {
            return new RagAnswer(question, "Vector store ichida mos context topilmadi.", List.of());
        }

        String context = contextDocuments.stream()
                .map(document -> "Source[" + document.getId() + "]: " + document.getText())
                .collect(Collectors.joining("\n\n"));

        String answer = this.chatClient.prompt()
                .system("""
                        You are a RAG assistant.
                        Use only the provided context.
                        If context is not enough, clearly say it is not enough.
                        """)
                .user("Question: " + question + "\n\nContext:\n" + context)
                .call()
                .content();

        return new RagAnswer(
                question,
                answer,
                contextDocuments.stream().map(this::toVectorSearchItem).toList()
        );
    }

    @PostMapping("/image")
    public ImageResult generateImage(@RequestBody ImageRequest request) {
        ImageModel model = requireFeature(this.imageModel, "Image");
        String prompt = requireText(request == null ? null : request.prompt(), "prompt");

        OpenAiImageOptions.Builder optionsBuilder = OpenAiImageOptions.builder();
        if (request != null && StringUtils.hasText(request.model())) {
            optionsBuilder.model(request.model().trim());
        }
        if (request != null && StringUtils.hasText(request.quality())) {
            optionsBuilder.quality(request.quality().trim());
        }
        if (request != null && StringUtils.hasText(request.style())) {
            optionsBuilder.style(request.style().trim());
        }

        Image image = model.call(new ImagePrompt(prompt, optionsBuilder.build()))
                .getResult()
                .getOutput();

        return new ImageResult(prompt, image.getUrl(), image.getB64Json());
    }

    @PostMapping("/moderation")
    public ModerationPayload moderate(@RequestBody TextRequest request) {
        ModerationModel model = requireFeature(this.moderationModel, "Moderation");
        String text = requireText(request == null ? null : request.text(), "text");

        Moderation moderation = model.call(new ModerationPrompt(text)).getResult().getOutput();
        ModerationResult moderationResult = moderation.getResults().isEmpty()
                ? null
                : moderation.getResults().get(0);

        if (moderationResult == null) {
            return new ModerationPayload(text, false, Map.of(), Map.of());
        }

        return new ModerationPayload(
                text,
                moderationResult.isFlagged(),
                toCategoryMap(moderationResult.getCategories()),
                toScoreMap(moderationResult.getCategoryScores())
        );
    }

    @PostMapping(value = "/audio/speech", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> textToSpeech(@RequestBody SpeechRequest request) {
        TextToSpeechModel model = requireFeature(this.textToSpeechModel, "Text-to-speech");
        String text = requireText(request == null ? null : request.text(), "text");

        OpenAiAudioApi.SpeechRequest.AudioResponseFormat format = parseAudioFormat(
                request == null ? null : request.format()
        );

        OpenAiAudioSpeechOptions.Builder optionsBuilder = OpenAiAudioSpeechOptions.builder()
                .responseFormat(format);

        if (request != null && StringUtils.hasText(request.model())) {
            optionsBuilder.model(request.model().trim());
        }
        if (request != null && StringUtils.hasText(request.voice())) {
            optionsBuilder.voice(request.voice().trim());
        }
        if (request != null && request.speed() != null) {
            optionsBuilder.speed(request.speed());
        }

        byte[] audio = model.call(new TextToSpeechPrompt(text, optionsBuilder.build()))
                .getResult()
                .getOutput();

        String extension = format.getValue();
        return ResponseEntity.ok()
                .contentType(resolveAudioMediaType(format))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"speech." + extension + "\"")
                .body(audio);
    }

    @PostMapping(value = "/audio/transcription", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TranscriptionPayload transcribe(@RequestPart("file") MultipartFile file,
                                           @RequestParam(required = false) String language,
                                           @RequestParam(required = false) String prompt) {
        TranscriptionModel model = requireFeature(this.transcriptionModel, "Transcription");

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "audio file is required");
        }

        String originalFilename = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename().trim()
                : "audio.webm";

        try {
            ByteArrayResource resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return originalFilename;
                }
            };

            OpenAiAudioTranscriptionOptions.Builder optionsBuilder = OpenAiAudioTranscriptionOptions.builder();
            boolean hasCustomOptions = false;

            if (StringUtils.hasText(language)) {
                optionsBuilder.language(language.trim());
                hasCustomOptions = true;
            }

            if (StringUtils.hasText(prompt)) {
                optionsBuilder.prompt(prompt.trim());
                hasCustomOptions = true;
            }

            String transcript = hasCustomOptions
                    ? model.transcribe(resource, optionsBuilder.build())
                    : model.transcribe(resource);

            return new TranscriptionPayload(originalFilename, transcript);
        }
        catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "audio file cannot be read", ex);
        }
    }

    private VectorSearchItem toVectorSearchItem(Document document) {
        return new VectorSearchItem(
                document.getId(),
                document.getText(),
                document.getScore(),
                document.getMetadata()
        );
    }

    private List<Float> preview(float[] vector, int size) {
        int limit = Math.min(size, vector.length);
        List<Float> preview = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            preview.add(vector[i]);
        }
        return preview;
    }

    private OpenAiAudioApi.SpeechRequest.AudioResponseFormat parseAudioFormat(String format) {
        if (!StringUtils.hasText(format)) {
            return OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3;
        }
        try {
            return OpenAiAudioApi.SpeechRequest.AudioResponseFormat.valueOf(format.trim().toUpperCase());
        }
        catch (IllegalArgumentException ex) {
            return OpenAiAudioApi.SpeechRequest.AudioResponseFormat.MP3;
        }
    }

    private MediaType resolveAudioMediaType(OpenAiAudioApi.SpeechRequest.AudioResponseFormat format) {
        return switch (format) {
            case WAV -> MediaType.valueOf("audio/wav");
            case FLAC -> MediaType.valueOf("audio/flac");
            case AAC -> MediaType.valueOf("audio/aac");
            case OPUS -> MediaType.valueOf("audio/opus");
            case PCM -> MediaType.APPLICATION_OCTET_STREAM;
            case MP3 -> MediaType.valueOf("audio/mpeg");
        };
    }

    private Map<String, Boolean> toCategoryMap(Categories categories) {
        if (categories == null) {
            return Map.of();
        }

        Map<String, Boolean> map = new HashMap<>();
        map.put("sexual", categories.isSexual());
        map.put("hate", categories.isHate());
        map.put("harassment", categories.isHarassment());
        map.put("selfHarm", categories.isSelfHarm());
        map.put("violence", categories.isViolence());
        map.put("pii", categories.isPii());
        return map;
    }

    private Map<String, Double> toScoreMap(CategoryScores scores) {
        if (scores == null) {
            return Map.of();
        }

        Map<String, Double> map = new HashMap<>();
        map.put("sexual", scores.getSexual());
        map.put("hate", scores.getHate());
        map.put("harassment", scores.getHarassment());
        map.put("selfHarm", scores.getSelfHarm());
        map.put("violence", scores.getViolence());
        map.put("pii", scores.getPii());
        return map;
    }

    private <T> T requireFeature(T featureBean, String featureName) {
        if (featureBean == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_IMPLEMENTED,
                    featureName + " modeli bu konfiguratsiyada mavjud emas"
            );
        }
        return featureBean;
    }

    private String requireText(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value.trim();
    }

    public record TextRequest(String text) {
    }

    public record EmbeddingResult(String text, int dimensions, List<Float> preview) {
    }

    public record VectorDocumentInput(String id, String text, Map<String, Object> metadata) {
    }

    public record VectorIndexRequest(List<VectorDocumentInput> documents) {
    }

    public record VectorSearchRequest(String query, Integer topK, Double similarityThreshold) {
    }

    public record VectorSearchItem(String id, String text, Double score, Map<String, Object> metadata) {
    }

    public record RagRequest(String question, Integer topK) {
    }

    public record RagAnswer(String question, String answer, List<VectorSearchItem> sources) {
    }

    public record ImageRequest(String prompt, String model, String quality, String style) {
    }

    public record ImageResult(String prompt, String url, String b64Json) {
    }

    public record ModerationPayload(String text,
                                    boolean flagged,
                                    Map<String, Boolean> categories,
                                    Map<String, Double> scores) {
    }

    public record SpeechRequest(String text, String model, String voice, String format, Double speed) {
    }

    public record TranscriptionPayload(String filename, String transcript) {
    }
}
