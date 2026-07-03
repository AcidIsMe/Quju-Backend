package com.quju.platform.controller.file;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.config.QujuProperties;
import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.entity.TeamMemberEntity;
import com.quju.platform.mapper.TeamMemberMapper;
import com.quju.platform.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class FileController {

    private final QujuProperties qujuProperties;
    private final TeamMemberMapper teamMemberMapper;

    @PostMapping("/upload")
    public ApiResponse<Map<String, Object>> upload(@RequestParam MultipartFile file,
                                                   @RequestParam(required = false, defaultValue = "misc") String type,
                                                   @RequestParam(required = false) String teamId) throws Exception {
        String subDir = type;
        if ("team_album".equals(type) && teamId != null && !teamId.isBlank()) {
            subDir = "team_album/" + teamId;
        }
        Path dir = Path.of(qujuProperties.getFiles().getUploadDir(), subDir).toAbsolutePath();
        Files.createDirectories(dir);
        String originalFilename = Objects.requireNonNullElse(file.getOriginalFilename(), "upload.bin");
        String filename = UUID.randomUUID() + "-" + originalFilename;
        Path target = dir.resolve(filename);
        file.transferTo(target);
        String key = subDir + "/" + filename;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("url", "/uploads/" + key);
        result.put("key", key);
        if (teamId != null) {
            result.put("team_id", teamId);
        }
        return ApiResponse.ok(result);
    }

    @GetMapping("/team-album/{teamId}")
    public ApiResponse<List<Map<String, Object>>> listTeamAlbum(@PathVariable String teamId) {
        Path albumDir = Path.of(qujuProperties.getFiles().getUploadDir(), "team_album", teamId).toAbsolutePath();
        if (!Files.exists(albumDir) || !Files.isDirectory(albumDir)) {
            return ApiResponse.ok(List.of());
        }
        try (Stream<Path> files = Files.list(albumDir)) {
            List<Map<String, Object>> items = files
                    .filter(p -> !Files.isDirectory(p))
                    .map(p -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        String filename = p.getFileName().toString();
                        item.put("url", "/uploads/team_album/" + teamId + "/" + filename);
                        item.put("key", "team_album/" + teamId + "/" + filename);
                        item.put("name", filename);
                        try {
                            item.put("size", Files.size(p));
                            item.put("last_modified", Files.getLastModifiedTime(p).toMillis());
                        } catch (Exception ignored) {
                        }
                        return item;
                    })
                    .sorted((a, b) -> Long.compare(
                            (Long) b.getOrDefault("last_modified", 0L),
                            (Long) a.getOrDefault("last_modified", 0L)))
                    .collect(Collectors.toList());
            return ApiResponse.ok(items);
        } catch (Exception e) {
            return ApiResponse.ok(List.of());
        }
    }

    @DeleteMapping("/team-album/{teamId}")
    public ApiResponse<Void> deleteTeamAlbumFile(@PathVariable String teamId,
                                                  @RequestParam String key) throws Exception {
        // US34: 仅小队队长/管理员可删除相册照片
        String userId = SecurityUtil.requireCurrentUserId();
        TeamMemberEntity member = teamMemberMapper.selectOne(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, teamId)
                .eq(TeamMemberEntity::getUserId, userId));
        if (member == null || (!"leader".equals(member.getRole()) && !"admin".equals(member.getRole()))) {
            return ApiResponse.fail(403, "仅小队长或管理员可删除相册照片");
        }
        Path filePath = Path.of(qujuProperties.getFiles().getUploadDir(), key).toAbsolutePath().normalize();
        Path albumDir = Path.of(qujuProperties.getFiles().getUploadDir(), "team_album", teamId).toAbsolutePath().normalize();
        // 确保删除的文件在 team_album 目录下，防止路径穿越
        if (!filePath.startsWith(albumDir)) {
            return ApiResponse.fail(403, "不允许删除其他目录的文件");
        }
        Files.deleteIfExists(filePath);
        return ApiResponse.ok();
    }
}
