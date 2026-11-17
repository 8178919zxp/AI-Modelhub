package com.aimodelhub.agent.mapper;

import com.aimodelhub.agent.entity.AgentModelProfile;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * {@link AgentModelProfile} 的 MyBatis Mapper。
 * <p>
 * 这里没有 user_id：模型输入规范是全局数据，不属于任何用户。
 */
@Mapper
public interface AgentModelProfileMapper {

    List<AgentModelProfile> findAll();

    AgentModelProfile findByModelId(@Param("modelId") Long modelId);
}
