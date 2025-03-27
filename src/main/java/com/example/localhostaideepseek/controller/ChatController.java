package com.example.localhostaideepseek.controller;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@RestController
public class ChatController {

    private final OllamaChatModel chatModel;
    private final String baseStoragePath;

    @Autowired
    public ChatController(OllamaChatModel chatModel,
                          @Value("${ai.content.storage.path:D:\\AI-Generate-Content}") String baseStoragePath) {
        this.chatModel = chatModel;
        this.baseStoragePath = baseStoragePath;
        createBaseDirectoryIfNotExists();
    }

    private void createBaseDirectoryIfNotExists() {
        try {
            Path path = Paths.get(baseStoragePath);
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to create base directory", e);
        }
    }

    private Path createContentDirectory(String message) throws IOException {
        // Determine folder name based on message content
        String folderName = getFolderNameFromMessage(message);
        Path contentPath = Paths.get(baseStoragePath, folderName);

        if (!Files.exists(contentPath)) {
            Files.createDirectories(contentPath);
        }
        return contentPath;
    }

    private String getFolderNameFromMessage(String message) {
        // Clean the message to create a valid folder name
        String cleaned = message.toLowerCase()
                .replaceAll("[^a-z0-9]", "-")  // Replace special chars with hyphens
                .replaceAll("-+", "-")          // Replace multiple hyphens with single
                .replaceAll("^-|-$", "");       // Remove leading/trailing hyphens

        // Truncate if too long and add prefix
        cleaned = cleaned.substring(0, Math.min(cleaned.length(), 50));
        return "query-" + cleaned;
    }

    private void saveContentToFile(String content, Path directory, String fileName) throws IOException {
        Path filePath = directory.resolve(fileName + ".txt");
        Files.write(filePath, content.getBytes());
    }

    @GetMapping("/ai/generate")
    public Map<String, String> generate(@RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
        String response = this.chatModel.call(message);
        try {
            Path directory = createContentDirectory(message);
            String fileName = "response-" + UUID.randomUUID().toString().substring(0, 8);
            saveContentToFile(response, directory, fileName);
            return Map.of(
                    "generation", response,
                    "filePath", directory.resolve(fileName + ".txt").toString()
            );
        } catch (IOException e) {
            return Map.of(
                    "generation", response,
                    "error", "Failed to save file: " + e.getMessage()
            );
        }
    }

    @GetMapping("/ai/message")
    public String generateAiResponse(@RequestParam(value = "message", defaultValue = "Tell me a joke") String message) {
        String response = this.chatModel.call(message);
        try {
            Path directory = createContentDirectory(message);
            String fileName = "response-" + UUID.randomUUID().toString().substring(0, 8);
            saveContentToFile(response, directory, fileName);
            return response + "\n\n[Saved to: " + directory.resolve(fileName + ".txt") + "]";
        } catch (IOException e) {
            return response + "\n\n[Failed to save file: " + e.getMessage() + "]";
        }
    }

    @GetMapping("/ai/generateStream")
    public Flux<ChatResponse> generateStream(@RequestParam(value = "message",
            defaultValue = "Tell me a joke") String message) {
        Prompt prompt = new Prompt(new UserMessage(message));
        return this.chatModel.stream(prompt);
    }
}