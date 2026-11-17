package com.aimodelhub.predict.service;

import com.aimodelhub.cache.CacheManagementService;
import com.aimodelhub.config.CacheConfig;
import com.aimodelhub.predict.entity.PredictRecord;
import com.aimodelhub.predict.mapper.PredictRecordMapper;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Service
public class PredictRecordService {

    private final PredictRecordMapper predictRecordMapper;
    private final CacheManagementService cacheManagementService;

    public PredictRecordService(PredictRecordMapper predictRecordMapper,
                                CacheManagementService cacheManagementService) {
        this.predictRecordMapper = predictRecordMapper;
        this.cacheManagementService = cacheManagementService;
    }

    /**
     * 保存一条新的预测记录，并清理该用户的记录缓存。
     */
    @Transactional
    public PredictRecord saveRecord(Long userId, Long modelId, String inputText,
                                     String originalFilename, String fileUrl,
                                     String predictResult, Double confidence, Integer duration) {
        PredictRecord record = new PredictRecord();
        record.setUserId(userId);
        record.setModelId(modelId);
        record.setInputText(inputText);
        record.setOriginalFilename(originalFilename);
        record.setFileUrl(fileUrl);
        record.setPredictResult(predictResult);
        record.setConfidence(confidence);
        record.setDuration(duration);
        record.onCreate();

        predictRecordMapper.insert(record);

        // 清掉该用户的记录缓存，使下一次 GET /api/users/records 拿到最新数据。
        // 必须在事务提交之后执行：如果在事务内清理，并发读会把提交前的旧列表
        // 重新写回缓存，反而造成脏数据。
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheManagementService.evictUserRecordsCache(userId);
                }
            });
        } else {
            cacheManagementService.evictUserRecordsCache(userId);
        }

        return record;
    }

    /**
     * 查询某个用户的预测记录（最新在前）。
     * 缓存在 {@code cache:user_records::{userId}}，TTL 较短；
     * 通过 {@link #saveRecord} 保存新记录时自动清理。
     */
    @Cacheable(value = CacheConfig.CACHE_USER_RECORDS, key = "#userId")
    public List<PredictRecord> getUserRecords(Long userId) {
        return predictRecordMapper.findByUserIdOrderByCreateTimeDesc(userId);
    }

    public long getTotalRecordCount() {
        return predictRecordMapper.count();
    }

    public long getUserRecordCount(Long userId) {
        return predictRecordMapper.countByUserId(userId);
    }
}
