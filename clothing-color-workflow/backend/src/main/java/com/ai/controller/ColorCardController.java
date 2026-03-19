package com.ai.controller;

import com.ai.entity.ColorCard;
import com.ai.repository.ColorCardRepository;
import com.ai.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/color-cards")
@RequiredArgsConstructor
@CrossOrigin
public class ColorCardController {

    private final ColorCardRepository colorCardRepository;

    // 1. 获取所有色卡列表 (按创建时间倒序)
    @GetMapping("/list")
    public ApiResponse<List<ColorCard>> list() {
        List<ColorCard> list = colorCardRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        return ApiResponse.ok("ok", list);
    }

    // 2. 新增或修改色卡 (包含排重校验)
    @PostMapping("/save")
    public ApiResponse<?> save(@RequestBody ColorCard colorCard) {
        // 获取前端传来的面料和花型（去除前后空格以防误判）
        String fabricType = colorCard.getFabricType() != null ? colorCard.getFabricType().trim() : "";
        String patternName = colorCard.getPatternName() != null ? colorCard.getPatternName().trim() : "";

        boolean isDuplicate;

        if (colorCard.getId() == null) {
            // 【新增模式】：直接查全库是否存在该组合
            isDuplicate = colorCardRepository.existsByFabricTypeAndPatternName(fabricType, patternName);
        } else {
            // 【编辑模式】：查全库是否存在该组合，但要排除掉当前正在编辑的这个 ID 本身
            isDuplicate = colorCardRepository.existsByFabricTypeAndPatternNameAndIdNot(fabricType, patternName, colorCard.getId());
        }

        // 如果发现重复，直接返回错误信息，阻断保存
        if (isDuplicate) {
            // 💡 注意：如果你之前统一定义的是 ApiResponse.fail，这里就改成 .fail
            return ApiResponse.fail("保存失败：系统中已存在相同的【" + fabricType + " - " + patternName + "】数据！");
        }

        // 校验通过，执行落库
        ColorCard saved = colorCardRepository.save(colorCard);
        return ApiResponse.ok("ok", saved);
    }

    // 3. 删除色卡
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        colorCardRepository.deleteById(id);
        return ApiResponse.ok("删除成功", null);
    }

    // 🔴 1. 新增：分页与条件查询接口
    @GetMapping("/page")
    public ApiResponse<Page<ColorCard>> page(
            @RequestParam(value = "current", defaultValue = "1") int current,
            @RequestParam(value = "size", defaultValue = "10") int size,
            @RequestParam(value = "fabricType", required = false) String fabricType,
            @RequestParam(value = "printType", required = false) Integer printType,
            @RequestParam(value = "patternName", required = false) String patternName) {

        Pageable pageable = PageRequest.of(current - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ColorCard> pageResult = colorCardRepository.searchColorCards(fabricType, printType, patternName, pageable);
        return ApiResponse.ok("ok", pageResult);
    }

    // 🔴 2. 新增：批量删除接口
    @PostMapping("/batch-delete")
    public ApiResponse<Void> batchDelete(@RequestBody java.util.List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return ApiResponse.fail("请选择要删除的数据");
        }
        colorCardRepository.deleteAllById(ids);
        return ApiResponse.ok("批量删除成功", null);
    }
}