package com.docflow.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class GlobalSearchVO {
    private List<Item> documents = List.of();
    private List<Item> folders = List.of();
    private List<Item> users = List.of();

    @Data
    public static class Item {
        private String type;
        private Long id;
        private String title;
        private String subtitle;
        private String avatar;
        private Long parentId;
        private LocalDateTime updatedAt;
    }
}
