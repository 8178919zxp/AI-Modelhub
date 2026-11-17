package com.aimodelhub.common.api;

import java.util.List;

/**
 * 通用分页响应包装。
 * 后台接口返回分页数据时用它携带分页元信息，
 * 前端据此渲染分页控件。
 */
public record PageVO<T>(
        // 分页内容
        List<T> content,
        // 总条数
        long totalElements,
        // 总页数
        int totalPages,
        // 当前页码
        int currentPage,
        // 每页大小
        int pageSize
) {}
