package com.devtoolcopilot.docgen.controller;

import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import com.devtoolcopilot.common.exception.ApiException;
import com.devtoolcopilot.docgen.dto.DocGenRequest;
import com.devtoolcopilot.docgen.dto.DocGenResponse;
import com.devtoolcopilot.docgen.service.DocGenService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/docgen")
public class DocGenController {
    private final DocGenService docGenService;

    public DocGenController(DocGenService docGenService) {
        this.docGenService = docGenService;
    }

    @PostMapping("/pptx")
    public R<DocGenResponse> pptx(@RequestBody DocGenRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new ApiException(401, "未登录");
        return R.ok(docGenService.generatePptx(userId, req));
    }

    @PostMapping("/docx")
    public R<DocGenResponse> docx(@RequestBody DocGenRequest req) {
        Long userId = UserContext.getUserId();
        if (userId == null) throw new ApiException(401, "未登录");
        return R.ok(docGenService.generateDocx(userId, req));
    }
}

