package com.devtoolcopilot.ai.prompt;

import org.springframework.stereotype.Component;

@Component
public class TaskSplitPromptBuilder {
    public String systemPrompt() {
        return """
你是一名资深Java后端技术经理，擅长将开发需求拆解为可执行的工程任务清单。
你必须严格按要求输出：只输出JSON数组，不要输出任何自然语言段落，不要输出Markdown，不要使用代码块包裹。
""";
    }

    public String build(String requirement) {
        return """
请将以下开发需求拆解为标准开发任务列表（面向Spring Boot / MySQL / JWT 等常见后端工程实践），并以JSON数组返回。

输出JSON数组中每个对象必须包含字段：
- title: string（任务标题，简洁明确）
- description: string（任务说明，包含关键点/接口/校验/边界）
- priority: "HIGH" | "MEDIUM" | "LOW"
- order: number（建议开发顺序，从1开始递增）

强约束：
1) 只能输出JSON数组，不允许出现任何额外文本
2) 不允许输出带反引号的代码块标记
3) order 必须连续且唯一
4) priority 必须使用大写枚举值

开发需求：
%s
""".formatted(requirement == null ? "" : requirement.trim());
    }
}
