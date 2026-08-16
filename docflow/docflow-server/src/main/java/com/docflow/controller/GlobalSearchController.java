package com.docflow.controller;

import com.docflow.common.Result;
import com.docflow.security.UserContext;
import com.docflow.service.GlobalSearchService;
import com.docflow.vo.GlobalSearchVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/search")
public class GlobalSearchController {
    @Autowired private GlobalSearchService globalSearchService;

    @GetMapping
    public Result<GlobalSearchVO> search(@RequestParam("q") String keyword) {
        return Result.success(globalSearchService.search(UserContext.getRequiredUserId(), keyword));
    }
}
