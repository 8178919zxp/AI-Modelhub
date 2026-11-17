package com.aimodelhub.model.controller;

import com.aimodelhub.common.api.Result;
import com.aimodelhub.common.exception.NotFoundException;
import com.aimodelhub.model.entity.ModelInfo;
import com.aimodelhub.model.service.ModelService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/models")
public class ModelController {

    private final ModelService modelService;

    public ModelController(ModelService modelService) {
        this.modelService = modelService;
    }

    /**
     * 列出公共模型中心内处于上线状态（status=1）的模型。
     * 下线和删除的模型将被排除。
     */
    @GetMapping
    public Result<List<ModelInfo>> list() {

        return Result.ok(modelService.getOnlineModels());
    }

    /**
     * 根据ID获取模型详情。
     * 公共用户只能查看上线状态的模型（status = 1）。
     * 下线（status = 0）和删除（status = -1）的模型将返回404。
     * 管理员用户可以查看所有模型，包括下线和删除的模型。
     */
    @GetMapping("/{id}")
    public Result<ModelInfo> detail(@PathVariable Long id) {
        ModelInfo model = modelService.getModelById(id);
        // 普通用户只能看到已上线的模型，管理员可以看到全部
        if (!Integer.valueOf(ModelService.STATUS_ONLINE).equals(model.getStatus()) && !isAdmin()) {
            throw new NotFoundException("模型不存在");
        }
        return Result.ok(model);
    }

    private boolean isAdmin() {
        try {
            // 1. 从当前线程的 SecurityContext 中取出认证信息（Authentication）
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            // 2. 检查用户是否已登录并且通过了认证
            if (auth != null && auth.isAuthenticated()) {
                // 3. 遍历用户拥有的所有角色/权限（Authorities）
                for (GrantedAuthority ga : auth.getAuthorities()) {
                    // 4. 检查是否包含管理员角色标签 "ROLE_ADMIN"
                    if ("ROLE_ADMIN".equals(ga.getAuthority())) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false; // 没登录、或登录了但没有 ROLE_ADMIN 权限，都算普通用户
    }
}
