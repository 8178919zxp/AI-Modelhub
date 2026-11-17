package com.aimodelhub.admin.controller;

import com.aimodelhub.admin.audit.AuditLog;
import com.aimodelhub.common.api.PageVO;
import com.aimodelhub.common.api.Result;
import com.aimodelhub.common.exception.BusinessException;
import com.aimodelhub.model.dto.ModelSaveDTO;
import com.aimodelhub.model.entity.ModelInfo;
import com.aimodelhub.model.service.ModelService;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型管理后台接口。
 * <p>
 * {@code /api/admin/**} 下的所有接口都由 Spring Security 的
 * {@code .hasRole("ADMIN")} 保护（见 {@link com.aimodelhub.security.SecurityConfig}）。
 * <p>
 * 每次增删改都会通过 {@link ModelService} 清理缓存：
 * <ul>
 *   <li>{@code models::list}</li>
 *   <li>{@code model_detail::{id}}</li>
 *   <li>{@code dashboard_overview::overview}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/models")
public class AdminModelController {

    private static final Logger log = LoggerFactory.getLogger(AdminModelController.class);

    private final ModelService modelService;

    public AdminModelController(ModelService modelService) {
        this.modelService = modelService;
    }

    @PostConstruct
    void logRegistration() {
        log.info("AdminModelController registered at /api/admin/models");
    }

    /**
     * 分页查询模型列表。
     * <ul>
     *   <li>不传 page/size：返回全部模型（兼容旧调用）。</li>
     *   <li>传 page/size：返回 {@link PageVO} 分页包装。</li>
     * </ul>
     * 结果包含被逻辑删除的记录（status = -1）。
     * 不做缓存——后台需要实时数据。
     */
    @GetMapping
    public Result<Object> listAll(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        if (page != null && size != null) {
            // 分页返回：手写 COUNT + LIMIT，按 id 升序
            int clampedSize = Math.min(Math.max(1, size), 100); // 把每页条数限制在 [1, 100]
            int pageIndex = Math.max(0, page);
            long total = modelService.getTotalModelCount();
            int offset = pageIndex * clampedSize;
            List<ModelInfo> content = modelService.getAllModelsIncludingDeleted(offset, clampedSize);
            int totalPages = (int) Math.ceil((double) total / clampedSize);

            log.info("Admin listAll: page={}/{}, total={} models (online={}, offline={}, deleted={})",
                    page, size, total,
                    content.stream().filter(m -> m.getStatus() == 1).count(),
                    content.stream().filter(m -> m.getStatus() == 0).count(),
                    content.stream().filter(m -> m.getStatus() == -1).count());

            PageVO<ModelInfo> pageVO = new PageVO<>(
                    content, total, totalPages, pageIndex, clampedSize);
            return Result.ok(pageVO);
        }

        // 兼容旧调用：返回完整列表
        List<ModelInfo> all = modelService.getAllModelsIncludingDeleted();
        log.info("Admin listAll: returning {} models (online={}, offline={}, deleted={})",
                all.size(),
                all.stream().filter(m -> m.getStatus() == 1).count(),
                all.stream().filter(m -> m.getStatus() == 0).count(),
                all.stream().filter(m -> m.getStatus() == -1).count());
        return Result.ok(all);
    }

    /**
     * 按 ID 查询单个模型，复用带缓存的公开查询方法。
     */
    @GetMapping("/{id}")
    public Result<ModelInfo> detail(@PathVariable Long id) {
        return Result.ok(modelService.getModelById(id));
    }

    /**
     * 新增模型。
     */
    @AuditLog(action = "CREATE_MODEL", detail = "Admin creates a new model")
    @PostMapping
    public Result<ModelInfo> create(@Valid @RequestBody ModelSaveDTO dto) {
        ModelInfo created = modelService.createModel(dto);
        log.info("Admin created model: id={}, name={}", created.getId(), created.getModelName());
        return Result.ok("模型创建成功", created);
    }

    /**
     * 全量更新已有模型。
     */
    @AuditLog(action = "UPDATE_MODEL", detail = "Admin updates a model")
    @PutMapping("/{id}")
    public Result<ModelInfo> update(@PathVariable Long id, @Valid @RequestBody ModelSaveDTO dto) {
        ModelInfo updated = modelService.updateModel(id, dto);
        log.info("Admin updated model: id={}", updated.getId());
        return Result.ok("模型更新成功", updated);
    }

    /**
     * 切换模型的上架 / 下架状态。
     * 请求体形如 {@code {"status": 0}} 或 {@code {"status": 1}}。
     */
    @AuditLog(action = "TOGGLE_STATUS", detail = "Admin toggles model online/offline status")
    @PatchMapping("/{id}/status")
    public Result<ModelInfo> updateStatus(@PathVariable Long id,
                                          @RequestBody Map<String, Integer> body) {
        Integer status = body.get("status");
        if (status == null || (status != 0 && status != 1)) {
            throw new BusinessException("状态值无效，仅支持 0(下架) 或 1(上架)");
        }

        ModelInfo updated = modelService.updateModelStatus(id, status);
        String action = status == 1 ? "上架" : "下架";
        log.info("Admin {} model: id={}", action, updated.getId());

        return Result.ok("模型已" + action, updated);
    }

    /**
     * 逻辑删除模型（把 status 置为 -1）。
     * 历史预测记录仍保留对 model_id 的引用。
     */
    @AuditLog(action = "DELETE_MODEL", detail = "Admin soft-deletes a model")
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        ModelInfo deleted = modelService.deleteModel(id);
        log.info("Admin deleted model: id={}, name={}", deleted.getId(), deleted.getModelName());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", deleted.getId());
        data.put("name", deleted.getModelName());
        data.put("status", deleted.getStatus());
        data.put("message", "模型已删除（软删除，历史预测记录保留）");
        return Result.ok("模型已删除", data);
    }
}
