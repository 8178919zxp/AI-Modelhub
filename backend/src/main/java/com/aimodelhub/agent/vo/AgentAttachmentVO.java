package com.aimodelhub.agent.vo;

import com.aimodelhub.agent.entity.AgentAttachment;

/**
 * 附件上传结果。前端拿到 {@code id} 后，在对话里告诉 Agent
 * 「已上传，附件 id 是 N」，Agent 再调用 prepareInference 引用它。
 */
public class AgentAttachmentVO {

    private Long id;
    private String filename;
    private Long size;
    private String contentType;

    public static AgentAttachmentVO from(AgentAttachment attachment) {
        AgentAttachmentVO vo = new AgentAttachmentVO();
        vo.id = attachment.getId();
        vo.filename = attachment.getOriginalFilename();
        vo.size = attachment.getFileSize();
        vo.contentType = attachment.getContentType();
        return vo;
    }

    public Long getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public Long getSize() {
        return size;
    }

    public String getContentType() {
        return contentType;
    }
}
