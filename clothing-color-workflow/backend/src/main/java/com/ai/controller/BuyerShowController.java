package com.ai.controller;

import com.ai.dto.ApiResponse;
import com.ai.service.BuyerShowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 买家秀生成控制器
 */
@RestController
@RequestMapping("/api/buyer-show")
@RequiredArgsConstructor
@CrossOrigin
public class BuyerShowController {

    private final BuyerShowService buyerShowService;

    /**
     * 生成买家秀提示词
     * @param request spu, clothingDesc, imageUrls, scenePreference, countPerImage, textModel
     */
    @PostMapping("/generate")
    @SuppressWarnings("unchecked")
    public ApiResponse<String> generateBuyerShow(@RequestBody Map<String, Object> request) {
        String spu = (String) request.getOrDefault("spu", "");
        if (spu.trim().isEmpty()) {
            return ApiResponse.fail("请填写SPU款号");
        }

        String clothingDesc = (String) request.getOrDefault("clothingDesc", "");
        if (clothingDesc.trim().isEmpty()) {
            return ApiResponse.fail("请提供服装描述");
        }

        List<String> imageUrls = (List<String>) request.get("imageUrls");
        if (imageUrls == null || imageUrls.isEmpty()) {
            return ApiResponse.fail("请上传至少一张产品图");
        }

        String scenePreference = (String) request.getOrDefault("scenePreference", "");

        int countPerImage = 1;
        if (request.containsKey("countPerImage")) {
            try {
                countPerImage = Integer.parseInt(request.get("countPerImage").toString());
            } catch (NumberFormatException ignored) {}
        }

        String textModel = (String) request.getOrDefault("textModel", "gpt");

        String result = buyerShowService.generateBuyerShow(spu, clothingDesc, imageUrls, scenePreference, countPerImage, textModel);
        return ApiResponse.ok("生成成功", result);
    }
}
