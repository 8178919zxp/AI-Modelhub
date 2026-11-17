package com.aimodelhub.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class RegisterDTO {
    @NotBlank(message = "用户名不能为空")
    public String username;

    @NotBlank(message = "密码不能为空")
    public String password;

    @NotBlank(message = "昵称不能为空")
    public String nickname;

    public String securityQuestion;

    public String securityAnswer;
}
