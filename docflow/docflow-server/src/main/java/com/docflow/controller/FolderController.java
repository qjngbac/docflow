package com.docflow.controller;

import com.docflow.common.Result;
import com.docflow.dto.FolderRequest;
import com.docflow.entity.Folder;
import com.docflow.security.UserContext;
import com.docflow.service.FolderService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/folders")
public class FolderController {

    @Autowired
    private FolderService folderService;

    @GetMapping
    public Result<List<Folder>> list() {
        return Result.success(folderService.list(UserContext.getRequiredUserId()));
    }

    @PostMapping
    public Result<Folder> create(@Valid @RequestBody FolderRequest request) {
        return Result.success(folderService.create(UserContext.getRequiredUserId(), request));
    }

    @PutMapping("/{id}")
    public Result<Folder> update(@PathVariable Long id, @Valid @RequestBody FolderRequest request) {
        return Result.success(folderService.update(id, UserContext.getRequiredUserId(), request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        folderService.delete(id, UserContext.getRequiredUserId());
        return Result.success();
    }
}
