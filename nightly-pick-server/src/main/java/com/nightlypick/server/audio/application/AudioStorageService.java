package com.nightlypick.server.audio.application;

import com.nightlypick.server.audio.config.AudioProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class AudioStorageService {

    private final AudioProperties audioProperties;

    public AudioStorageService(AudioProperties audioProperties) {
        this.audioProperties = audioProperties;
    }

    public String store(String sessionId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "音频文件不能为空");
        }

        try {
            Path storageRoot = resolveStorageRoot();
            Files.createDirectories(storageRoot);

            String extension = resolveExtension(file.getOriginalFilename());
            String safeSessionId = sanitizeSegment(sessionId);
            String storedFileName = safeSessionId + "-" + UUID.randomUUID() + extension;
            Path target = storageRoot.resolve(storedFileName).normalize();

            if (!target.startsWith(storageRoot)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "非法音频文件路径");
            }

            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return buildPublicUrl(storedFileName);
        } catch (IOException error) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "保存音频文件失败", error);
        }
    }

    public Path resolveStorageRoot() {
        String configuredDir = StringUtils.hasText(audioProperties.storageDir()) ? audioProperties.storageDir() : "./data/audio";
        return Path.of(configuredDir).toAbsolutePath().normalize();
    }

    private String buildPublicUrl(String fileName) {
        String baseUrl = StringUtils.hasText(audioProperties.publicBaseUrl())
                ? audioProperties.publicBaseUrl().replaceAll("/+$", "")
                : "http://localhost:8080";
        return baseUrl + "/audio/files/" + URLEncoder.encode(fileName, StandardCharsets.UTF_8);
    }

    private String resolveExtension(String originalFilename) {
        if (!StringUtils.hasText(originalFilename) || !originalFilename.contains(".")) {
            return ".mp3";
        }
        String extension = originalFilename.substring(originalFilename.lastIndexOf('.')).toLowerCase();
        return extension.matches("\\.[a-z0-9]{1,8}") ? extension : ".mp3";
    }

    private String sanitizeSegment(String value) {
        if (!StringUtils.hasText(value)) {
            return "session";
        }
        return value.replaceAll("[^a-zA-Z0-9_-]", "-");
    }
}
