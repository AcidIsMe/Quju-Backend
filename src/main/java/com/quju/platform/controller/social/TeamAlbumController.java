package com.quju.platform.controller.social;

import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.entity.TeamAlbumEntity;
import com.quju.platform.service.TeamAlbumService;
import com.quju.platform.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/teams")
@RequiredArgsConstructor
public class TeamAlbumController {

    private final TeamAlbumService teamAlbumService;

    @PostMapping("/{id}/albums")
    public ApiResponse<TeamAlbumEntity> createAlbum(@PathVariable String id,
                                                     @RequestBody Map<String, String> body) {
        String name = body.get("name");
        String description = body.get("description");
        return ApiResponse.ok(teamAlbumService.createAlbum(id, SecurityUtil.requireCurrentUserId(), name, description));
    }

    @GetMapping("/{id}/albums")
    public ApiResponse<List<TeamAlbumEntity>> listAlbums(@PathVariable String id) {
        return ApiResponse.ok(teamAlbumService.listAlbums(id));
    }

    @GetMapping("/{id}/albums/{aid}")
    public ApiResponse<Map<String, Object>> getAlbumDetail(@PathVariable String id,
                                                            @PathVariable String aid) {
        return ApiResponse.ok(teamAlbumService.getAlbumDetail(id, aid));
    }

    @PostMapping("/{id}/albums/{aid}/photos")
    public ApiResponse<Map<String, Object>> uploadPhoto(@PathVariable String id,
                                                         @PathVariable String aid,
                                                         @RequestBody Map<String, String> body) {
        String imageUrl = body.get("image_url");
        String thumbnailUrl = body.get("thumbnail_url");
        String description = body.get("description");
        return ApiResponse.ok(teamAlbumService.uploadPhoto(id, aid, SecurityUtil.requireCurrentUserId(), imageUrl, thumbnailUrl, description));
    }

    @DeleteMapping("/{id}/albums/{aid}/photos/{pid}")
    public ApiResponse<Void> deletePhoto(@PathVariable String id,
                                          @PathVariable String aid,
                                          @PathVariable String pid) {
        teamAlbumService.deletePhoto(id, aid, pid, SecurityUtil.requireCurrentUserId());
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}/albums/{aid}")
    public ApiResponse<Void> deleteAlbum(@PathVariable String id,
                                          @PathVariable String aid) {
        teamAlbumService.deleteAlbum(id, aid, SecurityUtil.requireCurrentUserId());
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/albums/{aid}")
    public ApiResponse<TeamAlbumEntity> updateAlbum(@PathVariable String id,
                                                     @PathVariable String aid,
                                                     @RequestBody Map<String, String> body) {
        String name = body.get("name");
        String description = body.get("description");
        return ApiResponse.ok(teamAlbumService.updateAlbum(id, aid, SecurityUtil.requireCurrentUserId(), name, description));
    }
}
