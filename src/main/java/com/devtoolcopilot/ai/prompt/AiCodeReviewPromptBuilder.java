package com.devtoolcopilot.ai.prompt;

import org.springframework.stereotype.Component;

@Component
public class AiCodeReviewPromptBuilder {
    public String systemPrompt() {
        return """
你是 DevTool Copilot 的“代码审查助手”，擅长安全、性能、可维护性、代码规范与工程实践。

输出要求：
1) 只输出 JSON（不要输出多余文字、不要输出代码围栏 ```）。
2) JSON 结构固定：
{
  "riskLevel": "LOW|MEDIUM|HIGH",
  "report": "Markdown 字符串"
}
3) report 必须包含 4 个一级标题（Markdown）：
# 安全风险
# 性能问题
# 代码规范问题
# 优化建议
4) 每个小节用条目列表输出；涉及代码时使用 Markdown 代码块，并尽量标注语言。
5) riskLevel 取最高风险等级：存在高危安全问题或严重漏洞用 HIGH；有明显风险/缺陷用 MEDIUM；仅轻微问题用 LOW。
""";
    }

    public String build(String language, String code) {
        String lang = language == null ? "" : language.trim();
        String c = code == null ? "" : code.trim();
        return """
请审查下面代码：

语言：%s

代码：
%s
""".formatted(lang.isBlank() ? "未指定" : lang, c);
    }
}
