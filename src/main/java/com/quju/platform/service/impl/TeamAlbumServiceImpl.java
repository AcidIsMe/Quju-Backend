package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.entity.TeamAlbumEntity;
import com.quju.platform.entity.TeamMemberEntity;
import com.quju.platform.entity.TeamPhotoEntity;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.TeamAlbumMapper;
import com.quju.platform.mapper.TeamMemberMapper;
import com.quju.platform.mapper.TeamPhotoMapper;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.service.TeamAlbumService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class TeamAlbumServiceImpl implements TeamAlbumService {

    private final TeamAlbumMapper teamAlbumMapper;
    private final TeamPhotoMapper teamPhotoMapper;
    private final TeamMemberMapper teamMemberMapper;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public TeamAlbumEntity createAlbum(String teamId, String operatorId, String name, String description) {
        // 验证操作者是队长或管理员
        TeamMemberEntity member = teamMemberMapper.selectOne(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, teamId)
                .eq(TeamMemberEntity::getUserId, operatorId));
        if (member == null || (!"leader".equals(member.getRole()) && !"admin".equals(member.getRole()))) {
            throw new BusinessException(40300, "只有队长或管理员可以创建相册");
        }

        TeamAlbumEntity album = new TeamAlbumEntity();
        album.setTeamId(teamId);
        album.setName(name);
        album.setDescription(description);
        album.setPhotoCount(0);
        album.setCreatedBy(operatorId);
        teamAlbumMapper.insert(album);
        return album;
    }

    @Override
    public List<TeamAlbumEntity> listAlbums(String teamId) {
        return teamAlbumMapper.selectList(Wrappers.<TeamAlbumEntity>lambdaQuery()
                .eq(TeamAlbumEntity::getTeamId, teamId)
                .orderByDesc(TeamAlbumEntity::getCreatedAt));
    }

    @Override
    public Map<String, Object> getAlbumDetail(String teamId, String albumId) {
        TeamAlbumEntity album = teamAlbumMapper.selectOne(Wrappers.<TeamAlbumEntity>lambdaQuery()
                .eq(TeamAlbumEntity::getId, albumId)
                .eq(TeamAlbumEntity::getTeamId, teamId));
        if (album == null) {
            throw new BusinessException(40404, "相册不存在");
        }

        List<TeamPhotoEntity> photos = teamPhotoMapper.selectList(Wrappers.<TeamPhotoEntity>lambdaQuery()
                .eq(TeamPhotoEntity::getAlbumId, albumId)
                .eq(TeamPhotoEntity::getTeamId, teamId)
                .orderByAsc(TeamPhotoEntity::getSortOrder)
                .orderByDesc(TeamPhotoEntity::getCreatedAt));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", album.getId());
        result.put("team_id", album.getTeamId());
        result.put("name", album.getName());
        result.put("description", album.getDescription());
        result.put("cover_image_url", album.getCoverImageUrl());
        result.put("photo_count", album.getPhotoCount());
        result.put("created_by", album.getCreatedBy());
        result.put("created_at", album.getCreatedAt());

        List<Map<String, Object>> photoList = new ArrayList<>();
        for (TeamPhotoEntity p : photos) {
            UserEntity uploader = userMapper.selectById(p.getUploadedBy());
            Map<String, Object> pi = new LinkedHashMap<>();
            pi.put("id", p.getId());
            pi.put("album_id", p.getAlbumId());
            pi.put("team_id", p.getTeamId());
            pi.put("image_url", p.getImageUrl());
            pi.put("thumbnail_url", p.getThumbnailUrl());
            pi.put("description", p.getDescription());
            pi.put("uploaded_by", p.getUploadedBy());
            pi.put("uploader_nickname", uploader != null ? uploader.getNickname() : null);
            pi.put("uploader_avatar_url", uploader != null ? uploader.getAvatarUrl() : null);
            pi.put("sort_order", p.getSortOrder());
            pi.put("created_at", p.getCreatedAt());
            photoList.add(pi);
        }
        result.put("photos", photoList);

        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> uploadPhoto(String teamId, String albumId, String operatorId, String imageUrl, String thumbnailUrl, String description) {
        // 验证是小队成员
        TeamMemberEntity member = teamMemberMapper.selectOne(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, teamId)
                .eq(TeamMemberEntity::getUserId, operatorId));
        if (member == null) {
            throw new BusinessException(40404, "您不是小队成员");
        }

        // 验证相册存在
        TeamAlbumEntity album = teamAlbumMapper.selectOne(Wrappers.<TeamAlbumEntity>lambdaQuery()
                .eq(TeamAlbumEntity::getId, albumId)
                .eq(TeamAlbumEntity::getTeamId, teamId));
        if (album == null) {
            throw new BusinessException(40404, "相册不存在");
        }

        TeamPhotoEntity photo = new TeamPhotoEntity();
        photo.setAlbumId(albumId);
        photo.setTeamId(teamId);
        photo.setImageUrl(imageUrl);
        photo.setThumbnailUrl(thumbnailUrl);
        photo.setDescription(description);
        photo.setUploadedBy(operatorId);
        photo.setSortOrder(0);
        teamPhotoMapper.insert(photo);

        // 更新相册照片数量和封面（第一张照片作为封面）
        int newCount = (album.getPhotoCount() == null ? 0 : album.getPhotoCount()) + 1;
        album.setPhotoCount(newCount);
        if (album.getCoverImageUrl() == null) {
            album.setCoverImageUrl(thumbnailUrl != null ? thumbnailUrl : imageUrl);
        }
        teamAlbumMapper.updateById(album);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", photo.getId());
        result.put("image_url", photo.getImageUrl());
        result.put("thumbnail_url", photo.getThumbnailUrl());
        result.put("created_at", photo.getCreatedAt());
        return result;
    }

    @Override
    @Transactional
    public void deletePhoto(String teamId, String albumId, String photoId, String operatorId) {
        // 验证照片存在
        TeamPhotoEntity photo = teamPhotoMapper.selectOne(Wrappers.<TeamPhotoEntity>lambdaQuery()
                .eq(TeamPhotoEntity::getId, photoId)
                .eq(TeamPhotoEntity::getAlbumId, albumId)
                .eq(TeamPhotoEntity::getTeamId, teamId));
        if (photo == null) {
            throw new BusinessException(40404, "照片不存在");
        }

        // 验证权限：上传者 或 队长/管理员
        TeamMemberEntity member = teamMemberMapper.selectOne(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, teamId)
                .eq(TeamMemberEntity::getUserId, operatorId));
        boolean isLeaderOrAdmin = member != null && ("leader".equals(member.getRole()) || "admin".equals(member.getRole()));
        if (!operatorId.equals(photo.getUploadedBy()) && !isLeaderOrAdmin) {
            throw new BusinessException(40300, "只有照片上传者、队长或管理员可以删除照片");
        }

        teamPhotoMapper.deleteById(photoId);

        // 更新相册照片数量
        TeamAlbumEntity album = teamAlbumMapper.selectById(albumId);
        if (album != null) {
            int newCount = Math.max(0, (album.getPhotoCount() == null ? 0 : album.getPhotoCount()) - 1);
            album.setPhotoCount(newCount);
            // 如果删除的是封面，更新封面为下一张照片
            if (photo.getImageUrl() != null && photo.getImageUrl().equals(album.getCoverImageUrl())) {
                List<TeamPhotoEntity> remaining = teamPhotoMapper.selectList(Wrappers.<TeamPhotoEntity>lambdaQuery()
                        .eq(TeamPhotoEntity::getAlbumId, albumId)
                        .orderByAsc(TeamPhotoEntity::getSortOrder)
                        .orderByDesc(TeamPhotoEntity::getCreatedAt)
                        .last("LIMIT 1"));
                album.setCoverImageUrl(remaining.isEmpty() ? null : remaining.get(0).getThumbnailUrl() != null ? remaining.get(0).getThumbnailUrl() : remaining.get(0).getImageUrl());
            }
            teamAlbumMapper.updateById(album);
        }
    }

    @Override
    @Transactional
    public void deleteAlbum(String teamId, String albumId, String operatorId) {
        // 验证操作者是队长或管理员
        TeamMemberEntity member = teamMemberMapper.selectOne(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, teamId)
                .eq(TeamMemberEntity::getUserId, operatorId));
        if (member == null || (!"leader".equals(member.getRole()) && !"admin".equals(member.getRole()))) {
            throw new BusinessException(40300, "只有队长或管理员可以删除相册");
        }

        // 验证相册存在
        TeamAlbumEntity album = teamAlbumMapper.selectOne(Wrappers.<TeamAlbumEntity>lambdaQuery()
                .eq(TeamAlbumEntity::getId, albumId)
                .eq(TeamAlbumEntity::getTeamId, teamId));
        if (album == null) {
            throw new BusinessException(40404, "相册不存在");
        }

        // 删除相册下所有照片
        teamPhotoMapper.delete(Wrappers.<TeamPhotoEntity>lambdaQuery()
                .eq(TeamPhotoEntity::getAlbumId, albumId));

        // 删除相册
        teamAlbumMapper.deleteById(albumId);
    }

    @Override
    @Transactional
    public TeamAlbumEntity updateAlbum(String teamId, String albumId, String operatorId, String name, String description) {
        // 验证操作者是队长或管理员
        TeamMemberEntity member = teamMemberMapper.selectOne(Wrappers.<TeamMemberEntity>lambdaQuery()
                .eq(TeamMemberEntity::getTeamId, teamId)
                .eq(TeamMemberEntity::getUserId, operatorId));
        if (member == null || (!"leader".equals(member.getRole()) && !"admin".equals(member.getRole()))) {
            throw new BusinessException(40300, "只有队长或管理员可以修改相册");
        }

        // 验证相册存在
        TeamAlbumEntity album = teamAlbumMapper.selectOne(Wrappers.<TeamAlbumEntity>lambdaQuery()
                .eq(TeamAlbumEntity::getId, albumId)
                .eq(TeamAlbumEntity::getTeamId, teamId));
        if (album == null) {
            throw new BusinessException(40404, "相册不存在");
        }

        if (name != null) album.setName(name);
        if (description != null) album.setDescription(description);
        teamAlbumMapper.updateById(album);
        return album;
    }
}
