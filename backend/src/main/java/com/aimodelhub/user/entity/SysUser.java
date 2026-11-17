package com.aimodelhub.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/**
 * 系统用户。通过 {@code SysUserMapper} 持久化，
 * 列名在 {@code mapper/SysUserMapper.xml} 中显式映射。
 */
public class SysUser {

    private Long id;

    private String username;

    @JsonIgnore
    private String password;

    private String nickname;

    private String avatar;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String role = "user";

    private LocalDateTime createTime;

    private String securityQuestion;

    @JsonIgnore
    private String securityAnswer;

    private LocalDateTime updateTime;

    public SysUser() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAvatar() { return avatar; }
    public void setAvatar(String avatar) { this.avatar = avatar; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getSecurityQuestion() { return securityQuestion; }
    public void setSecurityQuestion(String securityQuestion) { this.securityQuestion = securityQuestion; }

    public String getSecurityAnswer() { return securityAnswer; }
    public void setSecurityAnswer(String securityAnswer) { this.securityAnswer = securityAnswer; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }

    /** 插入前由 Service 调用，填充 createTime 与 updateTime。 */
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (this.createTime == null) {
            this.createTime = now;
        }
        if (this.updateTime == null) {
            this.updateTime = now;
        }
    }

    /** 更新前由 Service 调用，刷新 updateTime。 */
    public void onUpdate() {
        this.updateTime = LocalDateTime.now();
    }
}
