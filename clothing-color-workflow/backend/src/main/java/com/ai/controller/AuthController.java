package com.ai.controller;

import com.ai.config.AppProperties;
import com.ai.dto.ApiResponse;
import com.ai.entity.SysUser;
import com.ai.repository.SysUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin
@Slf4j
public class AuthController {

    private final SysUserRepository sysUserRepository;
    private final AppProperties appProperties;
    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/login")
    public ApiResponse<SysUser> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String ticket  = body.get("ticket");
        String randstr = body.get("randstr");

        // 1. 校验腾讯验证码票据
        if (ticket == null || ticket.isEmpty()) {
            return ApiResponse.fail("请先完成滑块验证");
        }
        if (!verifyCaptcha(ticket, randstr)) {
            return ApiResponse.fail("验证失败，请重试");
        }

        // 2. 核对账号密码
        SysUser user = sysUserRepository.findByUsernameAndPassword(username, password);
        if (user == null) {
            return ApiResponse.fail("账号或密码错误");
        }

        // 3. 生成 Token
        String token = UUID.randomUUID().toString().replace("-", "");
        user.setToken(token);
        sysUserRepository.save(user);

        user.setPassword(null);
        return ApiResponse.ok("登录成功", user);
    }

    /**
     * 调用腾讯云验证码服务端票据校验 API
     * 文档：https://cloud.tencent.com/document/product/1110/36926
     */
    private boolean verifyCaptcha(String ticket, String randstr) {
        String appSecretKey = appProperties.getCaptcha().getAppSecretKey();
        String appId = appProperties.getCaptcha().getAppId();

        if (appSecretKey == null || appSecretKey.isEmpty() || appSecretKey.equals("YOUR_APP_SECRET_KEY")) {
            log.warn("腾讯验证码未配置 appSecretKey，跳过校验（开发模式）");
            return true; // 未配置时放行，方便本地开发
        }

        try {
            // 构建校验请求
            // https://captcha.qcloud.com/cap_union/verify 接口
            String url = String.format(
                    "https://captcha.qcloud.com/cap_union/verify?aid=%s&AppSecretKey=%s&Ticket=%s&Randstr=%s&UserIP=%s",
                    appId, appSecretKey, ticket, randstr, "auto"
            );

            Request request = new Request.Builder()
                    .url(url)
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String respBody = response.body() != null ? response.body().string() : "";
                log.info("腾讯验证码校验响应: {}", respBody.substring(0, Math.min(300, respBody.length())));

                JsonNode root = objectMapper.readTree(respBody);
                // 1: 验证成功, 0: 验证失败
                int errorCode = root.has("response") ? root.get("response").asInt(-1) : -1;
                if (errorCode == 1) {
                    return true;
                }
                log.warn("腾讯验证码校验失败: {}", respBody);
                return false;
            }
        } catch (Exception e) {
            log.error("腾讯验证码校验异常: {}", e.getMessage(), e);
            // 网络异常时放行，避免阻断正常用户登录（可按需调整策略）
            return true;
        }
    }

    /**
     * 修改个人资料（真实姓名、密码）- 带旧密码安全校验
     */
    @PostMapping("/update-profile")
    public ApiResponse<SysUser> updateProfile(@RequestBody Map<String, String> body, jakarta.servlet.http.HttpServletRequest request) {
        String token = request.getHeader("X-User-Token");
        SysUser user = sysUserRepository.findByToken(token);

        if (user == null) {
            return ApiResponse.fail("用户不存在或登录已失效");
        }

        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("password");
        String newRealName = body.get("realName");

        boolean updated = false;

        if (newPassword != null && !newPassword.trim().isEmpty()) {
            if (oldPassword == null || !oldPassword.trim().equals(user.getPassword())) {
                return ApiResponse.fail("修改失败：旧密码不正确！");
            }
            user.setPassword(newPassword.trim());
            updated = true;
        }

        if (newRealName != null && !newRealName.trim().isEmpty()) {
            user.setRealName(newRealName.trim());
            updated = true;
        }

        if (updated) {
            sysUserRepository.save(user);
        }

        user.setPassword(null);
        return ApiResponse.ok("修改成功", user);
    }
}
