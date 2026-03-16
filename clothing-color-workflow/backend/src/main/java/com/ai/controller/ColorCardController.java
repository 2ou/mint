package com.ai.controller;

import com.ai.entity.ColorCard;
import com.ai.repository.ColorCardRepository;
import com.ai.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
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

    // 2. 新增或修改色卡 (有 id 就是修改，没 id 就是新增)
    @PostMapping("/save")
    public ApiResponse<ColorCard> save(@RequestBody ColorCard colorCard) {
        ColorCard saved = colorCardRepository.save(colorCard);
        return ApiResponse.ok("ok", saved);
    }

    // 3. 删除色卡
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable("id") Long id) {
        colorCardRepository.deleteById(id);
        return ApiResponse.ok("删除成功", null);
    }
}