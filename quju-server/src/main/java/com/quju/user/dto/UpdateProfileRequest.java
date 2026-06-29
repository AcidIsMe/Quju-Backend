package com.quju.user.dto;

import java.util.List;

public class UpdateProfileRequest {

    private String nickname;
    private String avatarUrl;
    private String gender;
    private String birthday;   // "1995-06-15"
    private String bio;
    private List<String> interestTags;

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getBirthday() { return birthday; }
    public void setBirthday(String birthday) { this.birthday = birthday; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    public List<String> getInterestTags() { return interestTags; }
    public void setInterestTags(List<String> interestTags) { this.interestTags = interestTags; }
}
