package com.devtoolcopilot;

import com.devtoolcopilot.common.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IndexController {
    @GetMapping("/")
    public R<String> index() {
        return R.ok("DevTool Copilot Backend is running");
    }
}
