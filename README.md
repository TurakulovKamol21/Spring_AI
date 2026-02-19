# Spring AI Full Feature Demo

Spring Boot + Spring AI (OpenAI) demo with runnable endpoints for:

- chat
- streaming chat
- structured output
- chat memory
- tool calling
- embeddings
- vector index/search
- RAG
- image generation
- moderation
- text-to-speech
- audio transcription

## Requirements

- Java 17+
- Maven 3.9+
- OpenAI API key

## Setup

```bash
export OPENAI_API_KEY="your_api_key"
```

Optional model overrides:

```bash
export OPENAI_CHAT_MODEL="gpt-4o-mini"
export OPENAI_EMBEDDING_MODEL="text-embedding-3-small"
export OPENAI_IMAGE_MODEL="gpt-image-1"
export OPENAI_MODERATION_MODEL="omni-moderation-latest"
export OPENAI_SPEECH_MODEL="gpt-4o-mini-tts"
export OPENAI_TRANSCRIPTION_MODEL="gpt-4o-mini-transcribe"
```

## Run

```bash
mvn spring-boot:run
```

Server: `http://localhost:8080`

## Web UI

Browserda oching:

```text
http://localhost:8080
```

UI ichida barcha endpointlar uchun forma va natija panellari bor.

## 1) Chat

```bash
curl "http://localhost:8080/api/chat?message=Spring%20AI%20nima"
```

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message":"Spring AI bilan oddiy project tushuntir"}'
```

## 2) Streaming chat (SSE)

```bash
curl -N "http://localhost:8080/api/chat/stream?message=Streaming%20javob%20ber"
```

## 3) Structured output

```bash
curl -X POST http://localhost:8080/api/chat/structured \
  -H "Content-Type: application/json" \
  -d '{"topic":"Spring AI bilan RAG o\'rganish"}'
```

## 4) Chat memory

```bash
curl -X POST http://localhost:8080/api/chat/memory/demo-session \
  -H "Content-Type: application/json" \
  -d '{"message":"Mening ismim Aziz"}'
```

```bash
curl -X POST http://localhost:8080/api/chat/memory/demo-session \
  -H "Content-Type: application/json" \
  -d '{"message":"Ismim nima edi?"}'
```

Clear memory:

```bash
curl -X DELETE http://localhost:8080/api/chat/memory/demo-session
```

## 5) Tool calling

```bash
curl -X POST http://localhost:8080/api/chat/tool \
  -H "Content-Type: application/json" \
  -d '{"message":"UTC vaqti nechchi? keyin 12 km ni mile ga aylantir"}'
```

## 6) Embeddings

```bash
curl -X POST http://localhost:8080/api/ai/embedding \
  -H "Content-Type: application/json" \
  -d '{"text":"Spring AI semantic search demo"}'
```

## 7) Vector index/search

Index:

```bash
curl -X POST http://localhost:8080/api/ai/vector/index \
  -H "Content-Type: application/json" \
  -d '{
    "documents":[
      {"id":"doc1","text":"Spring AI RAG uchun vector store ishlatadi","metadata":{"topic":"rag"}},
      {"id":"doc2","text":"Embeddings matnni vektorga aylantiradi","metadata":{"topic":"embedding"}},
      {"id":"doc3","text":"Tool calling bilan AI tashqi funksiyani chaqiradi","metadata":{"topic":"tools"}}
    ]
  }'
```

Search:

```bash
curl -X POST http://localhost:8080/api/ai/vector/search \
  -H "Content-Type: application/json" \
  -d '{"query":"RAG nima", "topK":3}'
```

## 8) RAG

```bash
curl -X POST http://localhost:8080/api/ai/rag/ask \
  -H "Content-Type: application/json" \
  -d '{"question":"Embeddings nima uchun kerak?", "topK":3}'
```

## 9) Image generation

```bash
curl -X POST http://localhost:8080/api/ai/image \
  -H "Content-Type: application/json" \
  -d '{"prompt":"Minimalist spring flowers in flat illustration style"}'
```

## 10) Moderation

```bash
curl -X POST http://localhost:8080/api/ai/moderation \
  -H "Content-Type: application/json" \
  -d '{"text":"This is a sample text for moderation check."}'
```

## 11) Text-to-speech

```bash
curl -X POST http://localhost:8080/api/ai/audio/speech \
  -H "Content-Type: application/json" \
  -d '{"text":"Salom, bu Spring AI text to speech demo.", "voice":"alloy", "format":"mp3"}' \
  --output speech.mp3
```

## 12) Audio transcription

```bash
curl -X POST http://localhost:8080/api/ai/audio/transcription \
  -F "file=@/absolute/path/to/audio.mp3" \
  -F "language=en"
```

## 13) Feature availability

```bash
curl "http://localhost:8080/api/ai/features"
```

## Troubleshooting: 429 insufficient_quota

Agar quyidagi xatolik chiqsa:

```text
HTTP 429 / insufficient_quota
```

muammo kodda emas, OpenAI billing/quota holatida.

Tekshiruv:

1. [OpenAI Billing](https://platform.openai.com/settings/organization/billing)
2. API key to'g'ri org/projectga tegishli ekanini tekshirish
3. Zarur bo'lsa yangi key yaratib `OPENAI_API_KEY` ni yangilash
# Spring_AI
