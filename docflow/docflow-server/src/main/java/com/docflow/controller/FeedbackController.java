package com.docflow.controller;

import com.docflow.common.Result;
import com.docflow.entity.UserFeedback;
import com.docflow.security.UserContext;
import com.docflow.service.FeedbackService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/feedback")
public class FeedbackController {
    @Autowired private FeedbackService feedbackService;

    @PostMapping
    public Result<UserFeedback> create(@RequestParam(defaultValue = "BUG") String type,
                                       @RequestParam String description,
                                       @RequestParam(required = false) Long docId,
                                       @RequestParam(required = false, name = "images") MultipartFile[] images) {
        return Result.success(feedbackService.create(
                UserContext.getRequiredUserId(), docId, type, description, images));
    }

    @GetMapping("/mine")
    public Result<List<UserFeedback>> mine() {
        return Result.success(feedbackService.listMine(UserContext.getRequiredUserId()));
    }
}
