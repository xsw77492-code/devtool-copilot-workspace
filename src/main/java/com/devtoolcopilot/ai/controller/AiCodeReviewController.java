package com.devtoolcopilot.ai.controller;

import com.devtoolcopilot.ai.dto.AiCodeReviewRequestDTO;
import com.devtoolcopilot.ai.dto.AiCodeReviewResponseDTO;
import com.devtoolcopilot.ai.service.AiCodeReviewService;
import com.devtoolcopilot.common.R;
import com.devtoolcopilot.common.auth.UserContext;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ai/code")
public class AiCodeReviewController {
    private final AiCodeReviewService codeReviewService;

    public AiCodeReviewController(AiCodeReviewService codeReviewService) {
        this.codeReviewService = codeReviewService;
    }

    @PostMapping("/review")
    public R<AiCodeReviewResponseDTO> review(@RequestBody AiCodeReviewRequestDTO req) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            return R.fail(401, "未登录");
        }
        try {
            String lang = req == null ? null : req.getLanguage();
            String code = req == null ? null : req.getCode();
            return R.ok(codeReviewService.review(userId, lang, code));
        } catch (IllegalArgumentException e) {
            if ("CODE_REQUIRED".equals(e.getMessage())) {
                return R.fail(400, "code不能为空");
            }
            return R.fail(400, "请求参数错误");
        } catch (IllegalStateException e) {
            String msg = e.getMessage();
            if ("DEEPSEEK_UNAUTHORIZED".equals(msg)) {
                return R.fail(502, "DeepSeek API Key无效或无权限");
            }
            if ("DEEPSEEK_RATE_LIMIT".equals(msg)) {
                return R.fail(502, "DeepSeek触发限流或余额不足");
            }
            if ("DEEPSEEK_REQUEST_FAILED".equals(msg)) {
                return R.fail(502, "DeepSeek网络错误或超时");
            }
            if ("AI_JSON_PARSE_ERROR".equals(msg) || "AI_JSON_NOT_FOUND".equals(msg)) {
                return R.fail(502, "AI输出解析失败");
            }
            return R.fail(502, "AI服务调用失败");
        }
    }
}

