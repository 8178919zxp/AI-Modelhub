package com.aimodelhub.agent.mapper;

import com.aimodelhub.agent.entity.AgentAttachment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * {@link AgentAttachment} 的 Mapper。所有查询都带 user_id。
 */
@Mapper
public interface AgentAttachmentMapper {

    int insert(AgentAttachment attachment);

    AgentAttachment findByIdAndUser(@Param("id") Long id, @Param("userId") Long userId);

    int markUsed(@Param("id") Long id, @Param("userId") Long userId);
}
