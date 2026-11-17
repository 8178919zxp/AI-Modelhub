package com.aimodelhub.user.controller;

import com.aimodelhub.common.api.Result;
import com.aimodelhub.common.exception.UnauthorizedException;
import com.aimodelhub.predict.entity.PredictRecord;
import com.aimodelhub.predict.service.PredictRecordService;
import com.aimodelhub.user.entity.FavoriteModel;
import com.aimodelhub.user.entity.SysUser;
import com.aimodelhub.user.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final PredictRecordService predictRecordService;

    public UserController(UserService userService,
                          PredictRecordService predictRecordService) {
        this.userService = userService;
        this.predictRecordService = predictRecordService;
    }

    /** 返回当前登录用户的基本信息（不含密码与密保答案）。 */
    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        Long userId = getCurrentUserId();
        SysUser user = userService.getUserById(userId);

        return Result.ok(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "nickname", user.getNickname(),
                "role", user.getRole(),
                "avatar", user.getAvatar(),
                "createTime", user.getCreateTime() != null ? user.getCreateTime().toString() : ""
        ));
    }

    // 预测记录
    @GetMapping("/records")
    public Result<List<Map<String, Object>>> getRecords() {
        Long userId = getCurrentUserId();
        List<PredictRecord> records = predictRecordService.getUserRecords(userId);

        List<Map<String, Object>> result = records.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("modelId", r.getModelId());
            m.put("originalFilename", r.getOriginalFilename());
            m.put("fileUrl", r.getFileUrl());
            m.put("inputText", r.getInputText());
            m.put("predictResult", r.getPredictResult());
            m.put("confidence", r.getConfidence());
            m.put("duration", r.getDuration());
            m.put("createTime", r.getCreateTime() != null ? r.getCreateTime().toString() : "");
            return m;
        }).collect(Collectors.toList());

        return Result.ok(result);
    }

    // 收藏
    @GetMapping("/favorites")
    public Result<List<Long>> getFavorites() {
        Long userId = getCurrentUserId();
        List<Long> modelIds = userService.getUserFavorites(userId).stream()
                .map(FavoriteModel::getModelId)
                .collect(Collectors.toList());
        return Result.ok(modelIds);
    }

    /** 收藏指定模型；重复收藏不会报错（幂等）。 */
    @PostMapping("/favorites/{modelId}")
    public Result<String> addFavorite(@PathVariable Long modelId) {
        Long userId = getCurrentUserId();
        userService.addFavorite(userId, modelId);
        return Result.ok("收藏成功");
    }

    /** 取消收藏指定模型。 */
    @DeleteMapping("/favorites/{modelId}")
    public Result<String> removeFavorite(@PathVariable Long modelId) {
        Long userId = getCurrentUserId();
        userService.removeFavorite(userId, modelId);
        return Result.ok("取消收藏成功");
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long)) {
            throw new UnauthorizedException("未登录或登录已过期");
        }
        return (Long) auth.getPrincipal();
    }
}
