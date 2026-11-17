package com.aimodelhub.model.mapper;

import com.aimodelhub.model.entity.ModelInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@link ModelInfo} 的 MyBatis Mapper。
 * <p>
 * 由 JPA 迁移到 MyBatis 后，取代原来的 {@code ModelInfoRepository}。
 * SQL 写在 {@code mapper/ModelInfoMapper.xml} 中，
 * 借助 {@code map-underscore-to-camel-case} 把下划线列名映射到驼峰字段。
 */
@Mapper
public interface ModelInfoMapper {

    int insert(ModelInfo model);

    int update(ModelInfo model);
    //@Param 注解用于指定参数名称，避免参数混淆
    ModelInfo findById(@Param("id") Long id);

    List<ModelInfo> findByStatus(@Param("status") Integer status);

    /** 排除被逻辑删除的模型，按 id 升序返回。 */
    List<ModelInfo> findByStatusNotOrderByIdAsc(@Param("status") Integer status);

    /** 返回全部模型（含逻辑删除），按 id 升序。 */
    List<ModelInfo> findAll();

    /** {@link #findAll()} 的分页版本。 */
    List<ModelInfo> findAllOrderByIdAsc(@Param("offset") int offset,
                                        @Param("limit") int limit);

    long count();

    long countByStatus(@Param("status") Integer status);
}
