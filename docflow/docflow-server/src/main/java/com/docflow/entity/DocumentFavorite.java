package com.docflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("document_favorite")
public class DocumentFavorite {
    private Long userId;
    private Long docId;
}
