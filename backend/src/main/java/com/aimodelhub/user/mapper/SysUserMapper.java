package com.aimodelhub.user.mapper;

import com.aimodelhub.user.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@link SysUser} 的 MyBatis Mapper。
 * 由 JPA 迁移到 MyBatis 后，取代原来的 {@code SysUserRepository}。
 */
@Mapper
public interface SysUserMapper {

    int insert(SysUser user);

    /** 更新所有可变列（create_time 在插入后不再变更）。 */
    int update(SysUser user);

    SysUser findById(@Param("id") Long id);

    SysUser findByUsername(@Param("username") String username);

    int countByUsername(@Param("username") String username);

    long count();

    List<SysUser> findAll();

    List<SysUser> findAllOrderByIdAsc(@Param("offset") int offset,
                                      @Param("limit") int limit);

    /** 用哨兵日期回填为 NULL 的 create_time（历史数据修复）。 */
    int fixNullCreateTimes();

    /** 用哨兵日期回填为 NULL 的 update_time（历史数据修复）。 */
    int fixNullUpdateTimes();
}
