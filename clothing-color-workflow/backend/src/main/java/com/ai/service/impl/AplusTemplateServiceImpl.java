package com.ai.service.impl;

import com.ai.dto.*;
import com.ai.entity.AplusTemplate;
import com.ai.repository.AplusTemplateRepository;
import com.ai.service.AplusTemplateService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AplusTemplateServiceImpl implements AplusTemplateService {

    private final AplusTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AplusTemplateResponse saveTemplate(AplusTemplateSaveRequest request, String operator, String shopName) {
        AplusTemplate template = new AplusTemplate();
        template.setTemplateName(request.getTemplateName());
        template.setSpu(request.getSpu());
        template.setReferenceImageUrl(request.getReferenceImageUrl());
        template.setSellingPoints(request.getSellingPoints());
        template.setOperator(operator);
        template.setShopName(shopName);

        // 序列化 selectedModules 为 JSON
        try {
            template.setSelectedModules(objectMapper.writeValueAsString(request.getSelectedModules()));
        } catch (JsonProcessingException e) {
            template.setSelectedModules("[]");
        }

        // 序列化 moduleExtras 为 JSON
        try {
            template.setModuleExtras(objectMapper.writeValueAsString(
                    request.getModuleExtras() != null ? request.getModuleExtras() : Map.of()));
        } catch (JsonProcessingException e) {
            template.setModuleExtras("{}");
        }

        template = templateRepository.save(template);
        log.info("【A+ 模板】保存成功: id={}, name={}", template.getId(), template.getTemplateName());
        return AplusTemplateResponse.from(template);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AplusTemplateResponse> getTemplatePage(int page, int size, String templateName, String spu) {
        Pageable pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AplusTemplate> result;
        if (templateName != null && !templateName.isBlank() && spu != null && !spu.isBlank()) {
            result = templateRepository.findByTemplateNameContainingAndSpu(templateName, spu, pageable);
        } else if (templateName != null && !templateName.isBlank()) {
            result = templateRepository.findByTemplateNameContaining(templateName, pageable);
        } else if (spu != null && !spu.isBlank()) {
            result = templateRepository.findBySpu(spu, pageable);
        } else {
            result = templateRepository.findAll(pageable);
        }
        return result.map(AplusTemplateResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public AplusTemplateResponse getTemplateById(Long id) {
        AplusTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("A+ 模板不存在: " + id));
        return AplusTemplateResponse.from(template);
    }

    @Override
    @Transactional
    public void deleteTemplate(Long id) {
        AplusTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("A+ 模板不存在: " + id));
        templateRepository.delete(template);
        log.info("【A+ 模板】已删除: id={}", id);
    }

    @Override
    @Transactional(readOnly = true)
    public AplusProjectCreateRequest applyTemplate(Long id) {
        AplusTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("A+ 模板不存在: " + id));

        AplusProjectCreateRequest request = new AplusProjectCreateRequest();
        request.setProjectName(template.getTemplateName());
        request.setSpu(template.getSpu());
        request.setReferenceImageUrl(template.getReferenceImageUrl());
        request.setSellingPoints(template.getSellingPoints());

        // 反序列化 selectedModules
        try {
            if (template.getSelectedModules() != null) {
                List<String> modules = objectMapper.readValue(template.getSelectedModules(),
                        new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
                request.setSelectedModules(modules);
            }
        } catch (JsonProcessingException e) {
            log.warn("【A+ 模板】selectedModules 反序列化失败: id={}", id);
            request.setSelectedModules(List.of());
        }

        // 反序列化 moduleExtras
        try {
            if (template.getModuleExtras() != null && !template.getModuleExtras().equals("{}")) {
                Map<String, AplusModuleExtra> extras = objectMapper.readValue(template.getModuleExtras(),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, AplusModuleExtra>>() {});
                request.setModuleExtras(extras);
            }
        } catch (JsonProcessingException e) {
            log.warn("【A+ 模板】moduleExtras 反序列化失败: id={}", id);
        }

        return request;
    }
}
