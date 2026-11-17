package com.aimodelhub.user.mapper;

import com.aimodelhub.user.entity.FavoriteModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@link FavoriteModel} 的 MyBatis Mapper。
 * 由 JPA 迁移到 MyBatis 后，取代原来的 {@code FavoriteModelRepository}。
 * 唯一约束 {@code uk_user_model(user_id, model_id)} 仍由表结构保证；
 * {@link #countByUserIdAndModelId} 对应原先的 {@code existsByUserIdAndModelId} 语义。
 */
@Mapper
public interface FavoriteModelMapper {

    int insert(FavoriteModel favorite);

    List<FavoriteModel> findByUserId(@Param("userId") Long userId);

    FavoriteModel findByUserIdAndModelId(@Param("userId") Long userId,
                                         @Param("modelId") Long modelId);

    int countByUserIdAndModelId(@Param("userId") Long userId,
                                @Param("modelId") Long modelId);

    int deleteByUserIdAndModelId(@Param("userId") Long userId,
                                 @Param("modelId") Long modelId);

    long countByModelId(@Param("modelId") Long modelId);
}
