package com.quju.platform.service;

import com.quju.platform.entity.TeamAlbumEntity;

import java.util.List;
import java.util.Map;

public interface TeamAlbumService {
    TeamAlbumEntity createAlbum(String teamId, String operatorId, String name, String description);
    List<TeamAlbumEntity> listAlbums(String teamId);
    Map<String, Object> getAlbumDetail(String teamId, String albumId);
    Map<String, Object> uploadPhoto(String teamId, String albumId, String operatorId, String imageUrl, String thumbnailUrl, String description);
    void deletePhoto(String teamId, String albumId, String photoId, String operatorId);
    void deleteAlbum(String teamId, String albumId, String operatorId);
    TeamAlbumEntity updateAlbum(String teamId, String albumId, String operatorId, String name, String description);
}
