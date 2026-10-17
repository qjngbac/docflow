package com.docflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docflow.entity.UserNotification;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserNotificationMapper extends BaseMapper<UserNotification> {
    @org.apache.ibatis.annotations.Insert({"<script>",
            "INSERT INTO user_notification(user_id,actor_id,type,content,is_read,created_at) VALUES",
            "<foreach collection='userIds' item='userId' separator=','>",
            "(#{userId},#{adminId},'ADMIN_MESSAGE',#{content},0,#{createdAt})",
            "</foreach></script>"})
    int insertAdminMessages(@org.apache.ibatis.annotations.Param("userIds") java.util.List<Long> userIds,
                            @org.apache.ibatis.annotations.Param("adminId") Long adminId,
                            @org.apache.ibatis.annotations.Param("content") String content,
                            @org.apache.ibatis.annotations.Param("createdAt") java.time.LocalDateTime createdAt);
}
