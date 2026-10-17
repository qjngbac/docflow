package com.docflow.mapper;

import com.docflow.entity.StoredFile;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface StoredFileMapper {
    @Select("SELECT file_url, owner_id, file_kind FROM stored_file WHERE file_url=#{url}")
    StoredFile find(@Param("url") String url);

    @Insert("INSERT INTO stored_file(file_url,owner_id,file_kind) VALUES(#{url},#{owner},#{kind})")
    void insert(@Param("url") String url, @Param("owner") Long owner, @Param("kind") String kind);

    @Insert("INSERT IGNORE INTO stored_file_scope(file_url,scope_type,scope_id) VALUES(#{url},#{type},#{id})")
    void grant(@Param("url") String url, @Param("type") String type, @Param("id") Long id);

    @Select("SELECT COUNT(*) FROM stored_file_scope s JOIN document d ON d.id=s.scope_id "
            + "WHERE s.file_url=#{url} AND s.scope_type='DOCUMENT' AND d.is_deleted=0 AND "
            + "(d.owner_id=#{user} OR EXISTS(SELECT 1 FROM doc_permission p WHERE p.doc_id=d.id "
            + "AND p.user_id=#{user} AND p.permission IN ('READ','COMMENT','WRITE','ADMIN') "
            + "AND (p.expire_time IS NULL OR p.expire_time>#{now})))")
    int readableDocuments(@Param("url") String url, @Param("user") Long user, @Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM stored_file_scope s JOIN document_template t ON t.id=s.scope_id "
            + "WHERE s.file_url=#{url} AND s.scope_type='TEMPLATE' AND (t.owner_id IS NULL OR t.owner_id=#{user})")
    int readableTemplates(@Param("url") String url, @Param("user") Long user);

    @Select("SELECT COUNT(*) FROM stored_file_scope s JOIN document d ON d.id=s.scope_id "
            + "WHERE s.file_url=#{url} AND s.scope_type='DOCUMENT' AND s.scope_id=#{doc} AND d.is_deleted=0")
    int documentScope(@Param("url") String url, @Param("doc") Long doc);

    @Select("SELECT COUNT(*) FROM document d WHERE d.id=#{doc} AND d.is_deleted=0 AND "
            + "(d.owner_id=#{user} OR EXISTS(SELECT 1 FROM doc_permission p WHERE p.doc_id=d.id "
            + "AND p.user_id=#{user} AND p.permission IN ('READ','COMMENT','WRITE','ADMIN') "
            + "AND (p.expire_time IS NULL OR p.expire_time>#{now})))")
    int readableDocument(@Param("doc") Long doc, @Param("user") Long user, @Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM document_template WHERE id=#{template} AND (owner_id IS NULL OR owner_id=#{user})")
    int readableTemplate(@Param("template") Long template, @Param("user") Long user);

    @Select("SELECT COUNT(*) FROM doc_permission p JOIN document d ON d.id=p.doc_id "
            + "WHERE p.doc_id=#{doc} AND p.share_token=#{token} AND p.user_id IS NULL AND d.is_deleted=0 "
            + "AND p.permission IN ('READ','COMMENT','WRITE','ADMIN') "
            + "AND (p.expire_time IS NULL OR p.expire_time>#{now})")
    int activeShare(@Param("doc") Long doc, @Param("token") String token, @Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM `user` WHERE id=#{user} AND status=1 AND is_deleted=0 AND system_role='ADMIN'")
    int activeAdmin(@Param("user") Long user);

    @Select("SELECT file_url FROM stored_file_scope WHERE scope_type=#{type} AND scope_id=#{id}")
    List<String> scopeUrls(@Param("type") String type, @Param("id") Long id);

    @Select("SELECT COUNT(*) FROM stored_file_scope WHERE file_url=#{url}")
    int references(@Param("url") String url);

    @Delete("DELETE FROM stored_file_scope WHERE scope_type=#{type} AND scope_id=#{id}")
    void removeScope(@Param("type") String type, @Param("id") Long id);

    @Delete("DELETE FROM stored_file WHERE file_url=#{url} AND NOT EXISTS "
            + "(SELECT 1 FROM stored_file_scope s WHERE s.file_url=stored_file.file_url)")
    int removeUnreferencedFile(@Param("url") String url);
}
