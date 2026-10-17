package com.docflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docflow.entity.AttachmentUploadSession;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AttachmentUploadSessionMapper extends BaseMapper<AttachmentUploadSession> {
    @org.apache.ibatis.annotations.Select("SELECT * FROM attachment_upload_session WHERE id = #{id} FOR UPDATE")
    AttachmentUploadSession lockById(@org.apache.ibatis.annotations.Param("id") String id);
}
