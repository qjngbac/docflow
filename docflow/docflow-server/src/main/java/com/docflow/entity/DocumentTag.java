package com.docflow.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("document_tag")
public class DocumentTag {
    private Long docId;
    private Long tagId;
}
