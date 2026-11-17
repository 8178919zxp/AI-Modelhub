package com.aimodelhub.auth.vo;

public class LoginVO {
    public Long userId;
    public String username;
    public String nickname;
    public String role;
    public String avatar;
    public String token;

    public LoginVO() {
    }

    public LoginVO(Long userId, String username, String nickname, String role, String avatar, String token) {
        this.userId = userId;
        this.username = username;
        this.nickname = nickname;
        this.role = role;
        this.avatar = avatar;
        this.token = token;
    }
}
