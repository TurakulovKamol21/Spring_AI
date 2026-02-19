const byId = (id) => document.getElementById(id);

function setOutput(id, data, isError = false) {
    const el = byId(id);
    el.classList.toggle("error", isError);
    if (typeof data === "string") {
        el.textContent = data;
        return;
    }
    el.textContent = JSON.stringify(data, null, 2);
}

async function parseResponse(response) {
    const text = await response.text();
    let payload = text;
    try {
        payload = JSON.parse(text);
    } catch (ignore) {
    }
    if (!response.ok) {
        const message = typeof payload === "object" && payload?.message
            ? payload.message
            : `${response.status} ${response.statusText}`;
        throw new Error(message);
    }
    return payload;
}

async function jsonRequest(url, method = "GET", body) {
    const response = await fetch(url, {
        method,
        headers: body ? {"Content-Type": "application/json"} : undefined,
        body: body ? JSON.stringify(body) : undefined
    });
    return parseResponse(response);
}

function wireForm(formId, outputId, handler) {
    byId(formId).addEventListener("submit", async (event) => {
        event.preventDefault();
        setOutput(outputId, "Running...");
        try {
            const result = await handler();
            setOutput(outputId, result);
        } catch (error) {
            setOutput(outputId, error.message, true);
        }
    });
}

async function loadFeatures() {
    const holder = byId("featureBadges");
    holder.textContent = "Loading features...";
    try {
        const features = await jsonRequest("/api/ai/features");
        holder.textContent = "";
        Object.entries(features).forEach(([name, enabled]) => {
            const badge = document.createElement("span");
            badge.className = `badge ${enabled ? "on" : "off"}`;
            badge.textContent = `${name}: ${enabled ? "on" : "off"}`;
            holder.appendChild(badge);
        });
    } catch (error) {
        holder.textContent = `Feature check failed: ${error.message}`;
    }
}

wireForm("chatForm", "chatOutput", () =>
    jsonRequest("/api/chat", "POST", {message: byId("chatMessage").value})
);

byId("streamForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const outputId = "streamOutput";
    setOutput(outputId, "Streaming...");
    try {
        const message = encodeURIComponent(byId("streamMessage").value || "");
        const response = await fetch(`/api/chat/stream?message=${message}`);
        if (!response.ok || !response.body) {
            throw new Error(`${response.status} ${response.statusText}`);
        }
        const reader = response.body.getReader();
        const decoder = new TextDecoder("utf-8");
        let content = "";
        while (true) {
            const {value, done} = await reader.read();
            if (done) {
                break;
            }
            content += decoder.decode(value, {stream: true});
            setOutput(outputId, content);
        }
    } catch (error) {
        setOutput(outputId, error.message, true);
    }
});

wireForm("structuredForm", "structuredOutput", () =>
    jsonRequest("/api/chat/structured", "POST", {topic: byId("structuredTopic").value})
);

wireForm("memoryForm", "memoryOutput", () => {
    const id = encodeURIComponent(byId("memoryConversationId").value || "default");
    return jsonRequest(`/api/chat/memory/${id}`, "POST", {message: byId("memoryMessage").value});
});

byId("memoryClearBtn").addEventListener("click", async () => {
    const outputId = "memoryOutput";
    setOutput(outputId, "Clearing...");
    try {
        const id = encodeURIComponent(byId("memoryConversationId").value || "default");
        const result = await jsonRequest(`/api/chat/memory/${id}`, "DELETE");
        setOutput(outputId, result);
    } catch (error) {
        setOutput(outputId, error.message, true);
    }
});

wireForm("toolForm", "toolOutput", () =>
    jsonRequest("/api/chat/tool", "POST", {message: byId("toolMessage").value})
);

wireForm("embeddingForm", "embeddingOutput", () =>
    jsonRequest("/api/ai/embedding", "POST", {text: byId("embeddingText").value})
);

wireForm("vectorIndexForm", "vectorIndexOutput", () => {
    const doc = {
        id: byId("vectorDocId").value || null,
        text: byId("vectorDocText").value,
        metadata: {
            topic: byId("vectorDocTopic").value || "general"
        }
    };
    return jsonRequest("/api/ai/vector/index", "POST", {documents: [doc]});
});

wireForm("vectorSearchForm", "vectorSearchOutput", () =>
    jsonRequest("/api/ai/vector/search", "POST", {
        query: byId("vectorQuery").value,
        topK: Number(byId("vectorTopK").value || 3)
    })
);

wireForm("ragForm", "ragOutput", () =>
    jsonRequest("/api/ai/rag/ask", "POST", {
        question: byId("ragQuestion").value,
        topK: Number(byId("ragTopK").value || 3)
    })
);

wireForm("imageForm", "imageOutput", async () => {
    const result = await jsonRequest("/api/ai/image", "POST", {
        prompt: byId("imagePrompt").value
    });
    const imageLink = byId("imageLink");
    if (result?.url) {
        imageLink.href = result.url;
        imageLink.textContent = "Open generated image";
    } else {
        imageLink.removeAttribute("href");
        imageLink.textContent = "";
    }
    return result;
});

wireForm("moderationForm", "moderationOutput", () =>
    jsonRequest("/api/ai/moderation", "POST", {text: byId("moderationText").value})
);

wireForm("speechForm", "speechOutput", async () => {
    const response = await fetch("/api/ai/audio/speech", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({
            text: byId("speechText").value,
            voice: byId("speechVoice").value || "alloy",
            format: "mp3"
        })
    });
    if (!response.ok) {
        throw new Error(`${response.status} ${response.statusText}`);
    }
    const blob = await response.blob();
    const objectUrl = URL.createObjectURL(blob);
    const player = byId("speechAudio");
    const download = byId("speechDownload");
    player.src = objectUrl;
    download.href = objectUrl;
    return {status: "ok", bytes: blob.size};
});

byId("transcriptionForm").addEventListener("submit", async (event) => {
    event.preventDefault();
    const outputId = "transcriptionOutput";
    setOutput(outputId, "Uploading...");
    try {
        const fileInput = byId("transcriptionFile");
        if (!fileInput.files || !fileInput.files[0]) {
            throw new Error("Audio file tanlanmagan");
        }

        const formData = new FormData();
        formData.append("file", fileInput.files[0]);

        const lang = byId("transcriptionLanguage").value;
        if (lang) {
            formData.append("language", lang);
        }

        const response = await fetch("/api/ai/audio/transcription", {
            method: "POST",
            body: formData
        });

        const payload = await parseResponse(response);
        setOutput(outputId, payload);
    } catch (error) {
        setOutput(outputId, error.message, true);
    }
});

loadFeatures();
