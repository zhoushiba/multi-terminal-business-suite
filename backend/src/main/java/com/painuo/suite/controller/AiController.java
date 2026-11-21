package com.painuo.suite.controller;

import com.painuo.suite.common.Result;
import com.painuo.suite.dto.AiDtos;
import com.painuo.suite.security.RequirePerm;
import com.painuo.suite.service.AiService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 助手接口。
 *
 * <p>三个动作分别对应运营后台的三种真实用法：
 * 编辑器里"生成摘要"、"优化标题"（返回 5 个候选），以及右下角侧栏的"问内容库"。
 * 所有接口在无 Key 时由 Mock Provider 兜底，返回体里的 {@code fallback} 字段告诉前端
 * 当前答案来自离线实现还是真实模型。</p>
 */
@RestController
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/api/ai/summary")
    @RequirePerm("ai:use")
    public Result<AiDtos.AiResultVO> summary(@Valid @RequestBody AiDtos.AiAssistRequest request) {
        return Result.ok(aiService.summarize(request));
    }

    @PostMapping("/api/ai/titles")
    @RequirePerm("ai:use")
    public Result<AiDtos.AiResultVO> titles(@Valid @RequestBody AiDtos.AiAssistRequest request) {
        return Result.ok(aiService.suggestTitles(request));
    }

    @PostMapping("/api/ai/chat")
    @RequirePerm("ai:use")
    public Result<AiDtos.AiResultVO> chat(@Valid @RequestBody AiDtos.ChatRequest request) {
        return Result.ok(aiService.chat(request));
    }
}
