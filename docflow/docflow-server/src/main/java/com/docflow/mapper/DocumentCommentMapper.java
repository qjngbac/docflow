package com.docflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docflow.entity.DocumentComment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DocumentCommentMapper extends BaseMapper<DocumentComment> {
}
