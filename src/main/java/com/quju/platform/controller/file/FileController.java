package com.quju.platform.controller.file;

import com.quju.platform.config.QujuProperties;
import com.quju.platform.dto.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class FileController {

    private final QujuProperties qujuProperties;

    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> upload(@RequestParam MultipartFile file,
                                                   @RequestParam(required = false, defaultValue = "misc") String type) throws Exception {
        Path dir = Path.of(qujuProperties.getFiles().getUploadDir(), type).toAbsolutePath();
        Files.createDirectories(dir);
        String originalFilename = Objects.requireNonNullElse(file.getOriginalFilename(), "upload.bin");
        String filename = UUID.randomUUID() + "-" + originalFilename;
        Path target = dir.resolve(filename);
        file.transferTo(target);
        String key = type + "/" + filename;
        return ApiResponse.ok(Map.of("url", "/uploads/" + key, "key", key));
    }
}
