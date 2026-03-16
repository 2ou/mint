package com.ai.controller;

import com.ai.entity.PromptGlobalConfig;
import com.ai.entity.ScenePoseTemplate;
import com.ai.repository.PromptGlobalConfigRepository;
import com.ai.repository.ScenePoseTemplateRepository;
import com.ai.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
@CrossOrigin
public class TemplateController {

    private final ScenePoseTemplateRepository templateRepository;
    private final PromptGlobalConfigRepository configRepository;

    // 1. 获取所有模板
    @GetMapping("/list")
    public ApiResponse<List<ScenePoseTemplate>> listTemplates() {
        return ApiResponse.ok("ok", templateRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    // 2. 保存/更新模板
    @PostMapping("/save")
    public ApiResponse<ScenePoseTemplate> saveTemplate(@RequestBody ScenePoseTemplate template) {
        return ApiResponse.ok("ok", templateRepository.save(template));
    }

    // 3. 删除模板
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteTemplate(@PathVariable("id") Long id) {
        templateRepository.deleteById(id);
        return ApiResponse.ok("删除成功", null);
    }

    // 4. 获取全局配置 (如万能后缀)
    // 🔴 修复点：明确指定 RequestParam 的值为 "key"
    @GetMapping("/config")
    public ApiResponse<PromptGlobalConfig> getConfig(@RequestParam("key") String key) {
        PromptGlobalConfig config = configRepository.findByConfigKey(key);
        if (config == null) {
            config = new PromptGlobalConfig();
            config.setConfigKey(key);
            config.setConfigValue("");
        }
        return ApiResponse.ok("ok", config);
    }

    // 5. 保存全局配置
    @PostMapping("/config/save")
    public ApiResponse<PromptGlobalConfig> saveConfig(@RequestBody PromptGlobalConfig config) {
        PromptGlobalConfig existing = configRepository.findByConfigKey(config.getConfigKey());
        if (existing != null) {
            existing.setConfigValue(config.getConfigValue());
            existing.setDescription(config.getDescription());
            return ApiResponse.ok("ok", configRepository.save(existing));
        }
        return ApiResponse.ok("ok", configRepository.save(config));
    }
}