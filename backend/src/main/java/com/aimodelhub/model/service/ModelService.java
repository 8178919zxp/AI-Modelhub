package com.aimodelhub.model.service;

import com.aimodelhub.cache.CacheManagementService;
import com.aimodelhub.common.exception.BusinessException;
import com.aimodelhub.common.exception.NotFoundException;
import com.aimodelhub.config.CacheConfig;
import com.aimodelhub.model.dto.ModelSaveDTO;
import com.aimodelhub.model.entity.ModelInfo;
import com.aimodelhub.model.mapper.ModelInfoMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ModelService {

    private static final Logger log = LoggerFactory.getLogger(ModelService.class);

    /** 逻辑删除状态的取值（对公开接口不可见）。 */
    public static final int STATUS_DELETED = -1;
    public static final int STATUS_OFFLINE = 0;
    public static final int STATUS_ONLINE = 1;

    private final ModelInfoMapper modelInfoMapper;
    private final CacheManagementService cacheManagementService;

    public ModelService(ModelInfoMapper modelInfoMapper,
                        CacheManagementService cacheManagementService) {
        this.modelInfoMapper = modelInfoMapper;
        this.cacheManagementService = cacheManagementService;
    }

    // ================================================================
    // 公共读取方法（含缓存）
    // ================================================================

    /**
     * 获取在线模型（status=1）。
     * 缓存在 {@code cache:models::online}，模型的增删改都会清理该 key，
     * 因此公开列表接口不必每次都查 MySQL。
     */
    @Cacheable(value = CacheConfig.CACHE_MODELS, key = "'online'")
    public List<ModelInfo> getOnlineModels() {
        return modelInfoMapper.findByStatus(STATUS_ONLINE);
    }

    /**
     * 根据 ID 获取单个模型。
     * 缓存在 {@code cache:model_detail::{id}}。
     */
    @Cacheable(value = CacheConfig.CACHE_MODEL_DETAIL, key = "#id")
    public ModelInfo getModelById(Long id) {
        ModelInfo model = modelInfoMapper.findById(id);
        if (model == null) {
            throw new NotFoundException("模型不存在");
        }
        return model;
    }

    /**
     * 获取模型总数。
     */
    public long getTotalModelCount() {
        return modelInfoMapper.count();
    }

    /**
     * 获取在线模型数量。
     */
    public long getOnlineModelCount() {
        return modelInfoMapper.countByStatus(STATUS_ONLINE);
    }

    // ================================================================
    // 后台：查询全部模型（含已删除）
    // ================================================================

    /**
     * 仅后台使用：返回所有模型，包含被逻辑删除的记录。
     * 不做缓存——后台需要实时数据。
     */
    public List<ModelInfo> getAllModelsIncludingDeleted() {
        return modelInfoMapper.findAll();
    }

    /**
     * 仅后台使用：{@link #getAllModelsIncludingDeleted()} 的分页版本。
     * 不做缓存——后台需要实时数据。
     *
     * @param offset 起始行号（从 0 开始，等于 page * size）
     * @param limit  每页条数
     */
    public List<ModelInfo> getAllModelsIncludingDeleted(int offset, int limit) {
        return modelInfoMapper.findAllOrderByIdAsc(offset, limit);
    }

    // ================================================================
    // 后台：增删改查（写操作同时清理缓存）
    // ================================================================

    /**
     * 新增模型：写入数据库，并清理在线模型列表与统计概览缓存。
     */
    @Transactional
    @CacheEvict(value = CacheConfig.CACHE_MODELS, key = "'online'")
    public ModelInfo createModel(ModelSaveDTO dto) {
        ModelInfo model = new ModelInfo();
        applyDtoToModel(dto, model);
        model.setStatus(dto.status != null ? dto.status : STATUS_ONLINE);
        model.onCreate();

        modelInfoMapper.insert(model);
        log.info("Model created: id={}, name={}", model.getId(), model.getModelName());

        // 统计概览会聚合模型数量，必须一并刷新
        cacheManagementService.evictDashboardCache();

        return model;
    }

    /**
     * 更新模型信息：按 ID 查不到时抛 404，成功后清理在线列表、详情与统计缓存。
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CacheConfig.CACHE_MODELS, key = "'online'"),
        @CacheEvict(value = CacheConfig.CACHE_MODEL_DETAIL, key = "#id")
    })
    public ModelInfo updateModel(Long id, ModelSaveDTO dto) {
        ModelInfo model = modelInfoMapper.findById(id);
        if (model == null) {
            throw new NotFoundException("模型不存在");
        }

        applyDtoToModel(dto, model);
        model.onUpdate();

        modelInfoMapper.update(model);
        log.info("Model updated: id={}, name={}", model.getId(), model.getModelName());

        cacheManagementService.evictDashboardCache();

        return model;
    }

    /**
     * 上架 / 下架模型：只允许 0（下线）与 1（上线）；
     * 成功后清理在线列表、详情与统计概览缓存。
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CacheConfig.CACHE_MODELS, key = "'online'"),
        @CacheEvict(value = CacheConfig.CACHE_MODEL_DETAIL, key = "#id")
    })
    public ModelInfo updateModelStatus(Long id, int newStatus) {
        if (newStatus != STATUS_ONLINE && newStatus != STATUS_OFFLINE) {
            throw new BusinessException("状态值无效，仅支持 0(下架) 或 1(上架)");
        }

        ModelInfo model = modelInfoMapper.findById(id);
        if (model == null) {
            throw new NotFoundException("模型不存在");
        }

        model.setStatus(newStatus);
        model.onUpdate();

        modelInfoMapper.update(model);
        log.info("Model status updated: id={}, status={}", model.getId(), model.getStatus());

        cacheManagementService.evictDashboardCache();

        return model;
    }

    /**
     * 软删除模型：把 status 置为 -1。
     * <p>
     * <b>删除策略：</b>逻辑删除。预测记录仍保留 {@code model_id} 引用，
     * 历史数据不会丢失。该模型对所有公开接口不可见，但仍会出现在后台列表中。
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = CacheConfig.CACHE_MODELS, key = "'online'"),
        @CacheEvict(value = CacheConfig.CACHE_MODEL_DETAIL, key = "#id")
    })
    public ModelInfo deleteModel(Long id) {
        ModelInfo model = modelInfoMapper.findById(id);
        if (model == null) {
            throw new NotFoundException("模型不存在");
        }

        if (model.getStatus() == STATUS_DELETED) {
            throw new BusinessException("模型已删除，无需重复操作");
        }

        model.setStatus(STATUS_DELETED);
        model.onUpdate();

        modelInfoMapper.update(model);
        log.info("Model soft-deleted: id={}, name={}", model.getId(), model.getModelName());

        cacheManagementService.evictDashboardCache();

        return model;
    }

    // ================================================================
    // 内部辅助方法
    // ================================================================

    /** 把 DTO 字段映射到实体（新增与更新共用）。 */
    private void applyDtoToModel(ModelSaveDTO dto, ModelInfo model) {
        model.setModelName(dto.modelName);
        model.setModelType(dto.modelType);
        model.setAlgorithm(dto.algorithm);
        model.setAccuracy(dto.accuracy);
        model.setDescription(dto.description);
        model.setCover(dto.cover);
        model.setModelApi(dto.modelApi);
        // status 单独处理（新增时用默认值，更新走专门的状态切换接口）
    }
}
