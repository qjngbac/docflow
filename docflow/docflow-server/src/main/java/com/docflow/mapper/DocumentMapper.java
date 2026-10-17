package com.docflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docflow.entity.Document;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocumentMapper extends BaseMapper<Document> {
    @org.apache.ibatis.annotations.Select("SELECT id FROM document WHERE id = #{docId} FOR UPDATE")
    Long lockVersionSequence(@org.apache.ibatis.annotations.Param("docId") Long docId);
}
