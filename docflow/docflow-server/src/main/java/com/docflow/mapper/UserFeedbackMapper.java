package com.docflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docflow.entity.UserFeedback;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserFeedbackMapper extends BaseMapper<UserFeedback> {
}
