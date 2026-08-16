package com.docflow.controller;

import com.docflow.common.Result;
import com.docflow.entity.DocumentTemplate;
import com.docflow.security.UserContext;
import com.docflow.service.TemplateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/templates")
public class TemplateController {
    @Autowired private TemplateService templateService;

    @GetMapping
    public Result<List<DocumentTemplate>> list() {
        return Result.success(templateService.list(UserContext.getRequiredUserId()));
    }

    @PostMapping
    public Result<DocumentTemplate> create(@RequestBody DocumentTemplate request) {
        return Result.success(templateService.create(UserContext.getRequiredUserId(), request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        templateService.delete(id, UserContext.getRequiredUserId());
        return Result.success();
    }
}
