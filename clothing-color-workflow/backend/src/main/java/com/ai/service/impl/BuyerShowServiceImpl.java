package com.ai.service.impl;

import com.ai.config.AppProperties;
import com.ai.exception.BusinessException;
import com.ai.service.BuyerShowService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 买家秀生成服务实现
 *
 * <p>系统提示词由「技能库」驱动：启动时从 classpath {@code skills/buyer-show/} 读取
 * 四份技能文档（电商买家秀 / 模特生成 / 去塑料人像 / women-lookbook-styling）
 * 及其 references，按固定顺序拼成 Skill Library，再叠加本模块的硬性要求与 JSON 输出契约。
 *
 * <p>技能之间是<b>叠加</b>关系，叠加口径见 {@link #buildSystemPrompt} 中的 Skill Stack Contract。
 * 改提示词只需替换 {@code resources/skills/buyer-show/} 下的 md 文件后重新打包，不必改 Java。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BuyerShowServiceImpl implements BuyerShowService {

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    private static final String GPT_API_URL = KieGptModels.RESPONSES_API_URL;

    /**
     * KIE {@code /codex/v1/responses} 近期高频抽风（随机 500 "Server exception"，
     * 流式还会回 503 "server is currently being maintained"）。
     * 实测：与请求体大小无关、与是否走代理无关、静默几分钟也不恢复，
     * 单次成功率约三成，且坏窗口能持续 1~3 分钟。
     * 因此这里做「拉长到约 85 秒的退避重试」，用覆盖面换成功率，而不是把上游抖动直接抛给用户。
     */
    private static final int GPT_MAX_ATTEMPTS = 12;
    private static final long GPT_RETRY_BASE_DELAY_MS = 1000L;
    private static final long GPT_RETRY_MAX_DELAY_MS = 8000L;

    /**
     * 技能文件清单：{标签, classpath 路径}。
     * 数组顺序 = 注入顺序，主技能在最前，各技能的 references 紧随其后。
     */
    private static final String[][] SKILL_FILES = {
            {"电商买家秀（主干技能）", "skills/buyer-show/ecom-buyer-show.md"},
            {"参考：买家秀提示词模式库", "skills/buyer-show/references/ecom-buyer-show.prompt-patterns.md"},
            {"参考：买家秀真实性机制发现", "skills/buyer-show/references/ecom-buyer-show.sample-findings.md"},
            {"模特生成（人物与服装维度技能）", "skills/buyer-show/model-generator.md"},
            {"去塑料人像（真实感常驻模块）", "skills/buyer-show/deplastic-portrait.md"},
            {"参考：去塑料提示词组件", "skills/buyer-show/references/deplastic-portrait.prompt-components.md"},
            {"women-lookbook-styling（场景与镜头技能）", "skills/buyer-show/lookbook-styling.md"},
            {"参考：服装锁定与参考图优先级", "skills/buyer-show/references/lookbook-styling.garment-and-reference-rules.md"},
            {"参考：lookbook 场景配方", "skills/buyer-show/references/lookbook-styling.lookbook-recipes.md"},
            {"参考：lookbook 视觉验收清单", "skills/buyer-show/references/lookbook-styling.quality-checklist.md"},
    };

    /** KIE 白名单比例（与 KieClientServiceImpl.KIE_ALLOWED_ASPECT_RATIOS 保持一致）。 */
    private static final Set<String> ALLOWED_ASPECT_RATIOS = Set.of(
            "1:1", "16:9", "9:16", "4:3", "3:4", "4:5", "5:4", "3:2", "2:3", "21:9", "9:21");

    /** 买家秀默认竖版（手机自拍/试穿的自然比例）。 */
    private static final String DEFAULT_ASPECT_RATIO = "3:4";

    /** 启动时加载一次，之后只读。 */
    private volatile String skillLibrary = "";

    @PostConstruct
    void initSkillLibrary() {
        StringBuilder sb = new StringBuilder();
        int loaded = 0;
        int missing = 0;
        for (String[] entry : SKILL_FILES) {
            String content = readClasspathText(entry[1]);
            if (content.isBlank()) {
                missing++;
                log.warn("[买家秀] 技能文件缺失或为空: {}", entry[1]);
                continue;
            }
            sb.append("\n\n===== SKILL: ").append(entry[0]).append(" =====\n\n").append(content);
            loaded++;
        }
        this.skillLibrary = sb.toString().trim();
        log.info("[买家秀] 技能库加载完成：{}/{} 个文件，共 {} 字符",
                loaded, SKILL_FILES.length, this.skillLibrary.length());
    }

    private String readClasspathText(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                return "";
            }
            try (InputStream in = resource.getInputStream()) {
                return stripFrontMatter(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            log.warn("[买家秀] 读取技能文件失败 {}: {}", path, e.getMessage());
            return "";
        }
    }

    /**
     * 剥掉技能文件开头的 YAML frontmatter（name/description 等给宿主平台看的元数据，
     * 对生成提示词无信息量，去掉可省 token）。
     */
    private static String stripFrontMatter(String raw) {
        String text = raw == null ? "" : raw.strip();
        if (text.startsWith("---")) {
            int close = text.indexOf("\n---", 3);
            if (close >= 0) {
                int lineEnd = text.indexOf('\n', close + 1);
                text = lineEnd >= 0 ? text.substring(lineEnd + 1).strip() : "";
            }
        }
        return text;
    }

    @Override
    public String generateBuyerShow(String spu, String clothingDesc, List<String> imageUrls,
                                     String scenePreference, int countPerImage,
                                     String aspectRatio, String textModel) {
        if (spu == null || spu.trim().isEmpty()) {
            throw new BusinessException("请填写SPU款号");
        }
        if (clothingDesc == null || clothingDesc.trim().isEmpty()) {
            throw new BusinessException("请提供服装描述");
        }
        if (imageUrls == null || imageUrls.isEmpty()) {
            throw new BusinessException("请上传至少一张产品图");
        }
        if (countPerImage < 1) countPerImage = 1;
        if (countPerImage > 5) countPerImage = 5;

        String ratio = normalizeAspectRatio(aspectRatio);

        int totalPrompts = imageUrls.size() * countPerImage;
        String systemPrompt = buildSystemPrompt(totalPrompts, imageUrls.size(), countPerImage,
                scenePreference, ratio);
        String userPrompt = buildUserPrompt(spu, clothingDesc, imageUrls, scenePreference, countPerImage, ratio);

        log.info("[买家秀] 生成开始: spu={}, 图片={}, 每图={}, 比例={}, systemPrompt={} 字符",
                spu, imageUrls.size(), countPerImage, ratio, systemPrompt.length());

        try {
            return callGpt(systemPrompt, userPrompt, textModel);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("生成买家秀失败: {}", e.getMessage(), e);
            throw new BusinessException("生成买家秀失败: " + e.getMessage());
        }
    }

    private String normalizeAspectRatio(String aspectRatio) {
        if (aspectRatio == null || aspectRatio.trim().isEmpty()) {
            return DEFAULT_ASPECT_RATIO;
        }
        String ar = aspectRatio.trim();
        return ALLOWED_ASPECT_RATIOS.contains(ar) ? ar : DEFAULT_ASPECT_RATIO;
    }

    /**
     * 构建系统提示词：角色 → 技能叠加契约 → 技能库 → 模块硬性要求 → JSON 输出契约。
     */
    private String buildSystemPrompt(int totalPrompts, int imageCount, int countPerImage,
                                     String scenePreference, String aspectRatio) {
        StringBuilder sb = new StringBuilder();

        // ---------- 1. 角色与任务 ----------
        sb.append("# Role\n");
        sb.append("You are an expert Amazon / Walmart women's fashion e-commerce BUYER SHOW ")
          .append("(买家秀 / UGC) prompt engineer.\n");
        sb.append("Deliver ").append(totalPrompts).append(" finished, copy-paste-ready English image prompts for ")
          .append(imageCount).append(" product images (").append(countPerImage).append(" prompt(s) per image).\n");
        sb.append("Every prompt must be complete on its own — no placeholders, no \"same as above\".\n\n");

        sb.append("# Output canvas\n");
        sb.append("Aspect ratio ").append(aspectRatio)
          .append(" (vertical portrait framing unless the ratio is wide). ")
          .append("This ratio governs framing and composition, not pixel resolution.\n\n");

        // ---------- 2. 技能叠加契约 ----------
        sb.append("# Skill Stack Contract (read this before the skill library below)\n\n");
        sb.append("The four skills in the library below are designed to be LAYERED, not chosen between. ")
          .append("Apply them with this exact precedence:\n\n");

        sb.append("1. **电商买家秀 — PRIMARY / 主干.**\n");
        sb.append("   The subject is an ORDINARY CONSUMER (`ordinary adult customer` / `real everyday wearer` / ")
          .append("`casual at-home try-on`), NOT a professional fashion model and NOT an influencer.\n");
        sb.append("   One single complete casual phone photo per prompt. No studio, no seamless backdrop, ")
          .append("no data card, no three-view composite, no collage, no text/watermark inside the image.\n");
        sb.append("   Mirror selfie / doorway / sofa / kitchen / balcony modes are the default vocabulary.\n\n");

        sb.append("2. **模特生成 — BORROW the identity machinery, NOT the model aura.**\n");
        sb.append("   Borrow: garment-fidelity rule, Model DNA dimensions 7–12 (face shape / eye / nose / lip / ")
          .append("brow / cheekbone-jaw) to make every person physically different, Tier-1 real skin texture, ")
          .append("body-archetype and size-expression rules, ethnicity pool, lens/camera reference, ")
          .append("fabric & material vocabulary, negative-prompt rotation groups.\n");
        sb.append("   **OVERRIDE the parts that conflict with skill 1:** do NOT write the Model Baseline ")
          .append("professional-posture / camera-aware-presence / model-magnetism lines as a professional aura — ")
          .append("keep only what survives on an ordinary person (photogenic but unposed, weight shifted, ")
          .append("comfortable in her own skin). Do NOT output the measurement data card, do NOT use ")
          .append("studio or three-view layouts, do NOT use the `fashion model` subject noun.\n\n");

        sb.append("3. **去塑料人像 — apply as a RESIDENT MODULE, not as a report.**\n");
        sb.append("   Do not emit its five-section diagnosis format. Instead bake its anti-plastic requirements ")
          .append("into EVERY prompt as a fixed block: real skin texture + natural asymmetry + ONE single camera ")
          .append("language (never mix iPhone, film and RAW in one prompt) + exactly one controlled imperfection.\n\n");

        sb.append("4. **women-lookbook-styling — BORROW scene, shot rhythm, camera language and physical integration.**\n");
        sb.append("   Keep its garment-locking discipline and its \"no readable text / no watermark / no logo\" rule. ")
          .append("Ignore its 3:4 default when it differs from the canvas ratio above.\n\n");

        sb.append("**Conflict resolution order:** user's explicit input > skill 1 (电商买家秀) > skill 2 > skill 3 > skill 4. ")
          .append("When skill 1 and skill 2 disagree, skill 1 wins.\n");

        // ---------- 3. 技能库 ----------
        sb.append("\n# Skill Library\n");
        sb.append("The following are the verbatim skill documents. Treat them as binding methodology, ")
          .append("except where the Skill Stack Contract above overrides them.\n");
        sb.append(skillLibrary.isBlank()
                ? "\n(技能库未加载：请检查 resources/skills/buyer-show/ 下的技能文件是否随包发布。)\n"
                : skillLibrary);
        sb.append("\n");

        // ---------- 4. 模块硬性要求 ----------
        sb.append("\n# Module Hard Requirements\n\n");
        sb.append("- **Quantity (exact):** return EXACTLY ").append(imageCount).append(" groups, each with EXACTLY ")
          .append(countPerImage).append(" prompts — ").append(totalPrompts).append(" prompts in total.\n");
        sb.append("- **Per-image garment color:** every product image carries its OWN color. ")
          .append("Each prompt MUST match the color of the image it belongs to. Never swap colors between images.\n");
        sb.append("- **Garment lock:** repeat the FULL garment description (color, print scale & placement, ")
          .append("neckline, sleeve length, closures, waistband, pant/skirt shape, hem length, fabric, pockets, ")
          .append("ease) in every prompt. Never write \"same as above\".\n");
        sb.append("- **Pose reset:** use each reference image ONLY as the garment source of truth. ")
          .append("Never inherit the reference person's identity, face, hairstyle, body, pose, hand placement, ")
          .append("camera angle, room, furniture, lighting or composition. If the reference is a model wearing ")
          .append("the product, the output MUST change identity, hair, shooting mode, camera position, room, ")
          .append("torso direction, hand action, weight distribution and light — at least 5 of these 9.\n");
        sb.append("- **Full face:** keep the whole head, forehead, both eyes, nose, mouth, chin and both cheek ")
          .append("contours inside the frame, with roughly 8–12% headroom above the crown. ")
          .append("Never crop or hide the face as an anti-AI trick — the user crops afterwards.\n");
        sb.append("- **Diversity:** every prompt must differ from the others in at least 5 dimensions chosen from ")
          .append("age / ethnicity / body type / hair / room / shooting mode / pose / lighting / phone position / ")
          .append("framing tilt. Never reuse the same face twice — vary Model DNA dimensions 7–12 in every prompt.\n");
        sb.append("- **Ordinary-consumer honesty:** random awkward framing, natural body proportions, ")
          .append("visible garment strain and folds at shoulder / bust / waist / hip / lap, relaxed arms, ")
          .append("slight postural asymmetry. Living-space background with signs of use but not messy.\n");
        sb.append("- **Anti-plastic, controlled:** exactly ONE phone-photography imperfection per prompt ")
          .append("(natural JPEG compression / slight phone noise / mild framing tilt). Never stack them. ")
          .append("Never turn \"real\" into low quality, dirty or blurry.\n");
        sb.append("- **One camera language per prompt:** choose one shooting style and stay inside it — ")
          .append("do not mix iPhone snapshot, film emulation and clean digital RAW in the same prompt. ")
          .append("Rotate the style across prompts.\n");
        sb.append("- **Negative constraints live INSIDE the prompt:** the downstream image model receives ")
          .append("ONE prompt string only. Embed the avoid-list at the END of each `prompt` field ")
          .append("(plastic/porcelain/airbrushed skin, beauty-filter or influencer face, professional studio look, ")
          .append("posed mannequin stance, wrong garment, altered color or print, cropped or hidden face, ")
          .append("extra fingers, deformed hands, readable text, watermark, logo). ")
          .append("Never invent a separate negative-prompt field — anything outside `prompt` is discarded.\n");
        sb.append("- **American lifestyle only:** never Chinese architecture, furniture, or styling. ")
          .append("Never porcelain / over-retouched skin, influencer face, or stiff posed demeanour.\n");
        sb.append("- **No faked proof:** never render star ratings, review text, usernames, order screenshots, ")
          .append("Verified Purchase badges, or any \"real buyer\" proof inside the image.\n");

        if (scenePreference != null && !scenePreference.trim().isEmpty()) {
            sb.append("- **User scene preference (HIGHEST priority):** ")
              .append(scenePreference.trim())
              .append(" — distribute these scenes across the prompts; if there are fewer scenes than prompts, ")
              .append("supplement with lifestyle scenes that match the garment, keeping the requested ones first.\n");
        }

        // ---------- 5. 输出契约 ----------
        sb.append("\n# Output Contract (CRITICAL)\n");
        sb.append("Return ONE JSON object, grouped by image index. No markdown fence, no commentary, PURE JSON.\n\n");
        sb.append("{\n");
        sb.append("  \"groups\": [\n");
        sb.append("    {\n");
        sb.append("      \"imageIndex\": 0,\n");
        sb.append("      \"colorNote\": \"该图服装颜色的英文简述\",\n");
        sb.append("      \"prompts\": [\n");
        sb.append("        {\n");
        sb.append("          \"index\": 0,\n");
        sb.append("          \"modelDesc\": \"模特描述(中文，含年龄/族裔/体型/脸型五官差异点)\",\n");
        sb.append("          \"sceneDesc\": \"场景描述(中文，含至少2个具体细节)\",\n");
        sb.append("          \"prompt\": \"完整的英文买家秀提示词（单条可独立使用，含服装锁定、姿势重置、完整脸部、真实肤质、单一相机语言、1个受控瑕疵、负向约束）\"\n");
        sb.append("        }\n");
        sb.append("      ]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n\n");
        sb.append("Rules:\n");
        sb.append("- \"groups\" MUST contain EXACTLY ").append(imageCount).append(" elements\n");
        sb.append("- each \"prompts\" array MUST contain EXACTLY ").append(countPerImage).append(" elements\n");
        sb.append("- total prompts = ").append(totalPrompts).append("\n");
        sb.append("- \"imageIndex\" starts at 0 and matches the product-image order given by the user\n");
        sb.append("- \"prompt\" MUST be a COMPLETE English buyer-show prompt matching that image's garment color\n");
        sb.append("- write all `modelDesc` / `sceneDesc` in Chinese, and the `prompt` field in English\n");

        return sb.toString();
    }

    /**
     * 构建用户提示词：服装事实 + 图片清单 + 场景偏好 + 数量与比例要求。
     */
    private String buildUserPrompt(String spu, String clothingDesc, List<String> imageUrls,
                                    String scenePreference, int countPerImage, String aspectRatio) {
        StringBuilder sb = new StringBuilder();

        sb.append("## SPU 款号\n").append(spu.trim()).append("\n\n");

        sb.append("## 服装描述\n").append(clothingDesc.trim()).append("\n\n");

        sb.append("## 产品图列表（每张图对应一个不同颜色，顺序即 imageIndex）\n");
        for (int i = 0; i < imageUrls.size(); i++) {
            sb.append("图片").append(i).append(" (imageIndex=").append(i).append("): ")
              .append(imageUrls.get(i)).append("\n");
        }
        sb.append("\n");

        if (scenePreference != null && !scenePreference.trim().isEmpty()) {
            sb.append("## 场景偏好（优先使用）\n").append(scenePreference.trim()).append("\n\n");
        }

        sb.append("## 要求\n");
        sb.append("共 ").append(imageUrls.size()).append(" 张图，每张图生成 ")
          .append(countPerImage).append(" 条买家秀提示词，画布比例 ").append(aspectRatio).append("。\n");
        sb.append("每条提示词的服装颜色必须与对应图片一致；每条必须使用不同的模特（五官/脸型/年龄/族裔/体型均不同）、")
          .append("不同的生活场景与拍摄方式，且保留完整脸部。\n");
        sb.append("直接返回 JSON 对象，不要任何其他文字。");

        return sb.toString();
    }

    /**
     * 调用 GPT API
     */
    private String callGpt(String systemPrompt, String userPrompt, String textModel) throws IOException {
        ObjectMapper om = objectMapper;
        String model = KieGptModels.normalizeTextModel(textModel);
        ObjectNode rootNode = om.createObjectNode();
        rootNode.put("model", model);
        rootNode.put("stream", false);

        ObjectNode reasoning = om.createObjectNode();
        reasoning.put("effort", "medium");
        rootNode.set("reasoning", reasoning);

        ArrayNode input = om.createArrayNode();

        ObjectNode systemMsg = om.createObjectNode();
        systemMsg.put("role", "system");
        ArrayNode systemContent = om.createArrayNode();
        ObjectNode systemText = om.createObjectNode();
        systemText.put("type", "input_text");
        systemText.put("text", systemPrompt);
        systemContent.add(systemText);
        systemMsg.set("content", systemContent);
        input.add(systemMsg);

        ObjectNode userMsg = om.createObjectNode();
        userMsg.put("role", "user");
        ArrayNode userContent = om.createArrayNode();
        ObjectNode userText = om.createObjectNode();
        userText.put("type", "input_text");
        userText.put("text", userPrompt);
        userContent.add(userText);
        userMsg.set("content", userContent);
        input.add(userMsg);

        rootNode.set("input", input);

        String jsonBody = om.writeValueAsString(rootNode);
        log.info("调用 GPT API (买家秀生成)，model={}，请求体大小: {} bytes", model, jsonBody.length());

        RequestBody body = RequestBody.create(jsonBody, MediaType.parse("application/json"));

        for (int attempt = 1; ; attempt++) {
            Request request = new Request.Builder()
                    .url(GPT_API_URL)
                    .addHeader("Authorization", "Bearer " + appProperties.getKie().getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            String failure;
            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                if (response.isSuccessful()) {
                    log.info("GPT API 响应(买家秀): HTTP {}，{} 字符",
                            response.code(), responseBody.length());
                    return GptResponseParser.parseTextOrThrow(om, responseBody, "无法解析 GPT 响应");
                }

                failure = "HTTP " + response.code() + " " + truncate(responseBody, 300);
                log.warn("GPT API 响应(买家秀) 失败: {}", failure);

                // 4xx（除 429）多为请求本身有问题，重试无意义，直接抛出
                if (response.code() < 500 && response.code() != 429) {
                    throw new RuntimeException("GPT API 调用失败: " + failure);
                }
            } catch (IOException e) {
                failure = e.getClass().getSimpleName() + ": " + e.getMessage();
                log.warn("GPT API 调用异常(买家秀): {}", failure);
            }

            if (attempt >= GPT_MAX_ATTEMPTS) {
                throw new RuntimeException("KIE 文本模型暂时不可用（" + failure
                        + "，已自动重试 " + attempt + " 次），请稍后重试");
            }

            long delay = Math.min(GPT_RETRY_BASE_DELAY_MS * attempt, GPT_RETRY_MAX_DELAY_MS);
            log.warn("[买家秀] GPT API 第 {}/{} 次失败（{}），{} ms 后重试",
                    attempt, GPT_MAX_ATTEMPTS, failure, delay);
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("GPT API 调用被中断", ie);
            }
        }
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
