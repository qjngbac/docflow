package com.docflow.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
/**
 * CRDT checkpoint、增量和客户端确认状态的底层 SQL 入口。
 * 这些记录共同构成服务重启后的恢复链。
 */
public interface CrdtDataMapper {

    @Delete("DELETE FROM crdt_update WHERE doc_id = #{docId}")
    int deleteUpdates(@Param("docId") Long docId);

    @Delete("DELETE FROM crdt_checkpoint_history WHERE doc_id = #{docId}")
    int deleteCheckpointHistory(@Param("docId") Long docId);

    @Delete("DELETE FROM crdt_checkpoint WHERE doc_id = #{docId}")
    int deleteCheckpoint(@Param("docId") Long docId);
}
