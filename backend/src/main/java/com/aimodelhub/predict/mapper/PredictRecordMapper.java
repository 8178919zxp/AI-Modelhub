package com.aimodelhub.predict.mapper;

import com.aimodelhub.predict.entity.PredictRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@link PredictRecord} 的 MyBatis Mapper。
 * <p>
 * 由 JPA 迁移到 MyBatis 后，取代原来的 {@code PredictRecordRepository}。
 */
@Mapper
public interface PredictRecordMapper {

    int insert(PredictRecord record);

    /**
     * 查询某个用户的预测记录，按 create_time 倒序。
     * 保留原先原生查询的语义：create_time 为 NULL 的记录
     * 通过 COALESCE 的哨兵日期排到最后。
     */
    List<PredictRecord> findByUserIdOrderByCreateTimeDesc(@Param("userId") Long userId);

    long count();

    long countByUserId(@Param("userId") Long userId);

    /** 用哨兵日期回填为 NULL 的 create_time（历史数据修复）。 */
    int fixNullCreateTimes();
}
