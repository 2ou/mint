package com.ai.controller;

import com.ai.entity.ScenePoseTemplate;
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
}