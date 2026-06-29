package com.quju.auth.service;

public class RefreshTokenResponse {
    private String accessToken;
    private String refreshToken;
    private int expiresIn;

    public RefreshTokenResponse(String accessToken, String refreshToken, int expiresIn) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
    }

    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }
    public int getExpiresIn() { return expiresIn; }
}
