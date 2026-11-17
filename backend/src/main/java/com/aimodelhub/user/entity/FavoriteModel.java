package com.aimodelhub.user.entity;

/**
 * 用户收藏的模型。通过 {@code FavoriteModelMapper} 持久化。
 * 唯一约束 {@code uk_user_model(user_id, model_id)} 由建表语句保证。
 */
public class FavoriteModel {

    private Long id;

    private Long userId;

    private Long modelId;

    public FavoriteModel() {
    }

    public FavoriteModel(Long userId, Long modelId) {
        this.userId = userId;
        this.modelId = modelId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public Long getModelId() { return modelId; }
    public void setModelId(Long modelId) { this.modelId = modelId; }
}
