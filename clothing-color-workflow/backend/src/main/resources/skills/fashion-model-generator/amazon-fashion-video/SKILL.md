---
name: amazon-fashion-video
description: 亚马逊女装宣发视频制作技能。当用户需要制作女装电商视频脚本、故事板提示词，或配合Seedance 2.0生成女装宣发视频时使用此技能。适用于美国市场女装产品视频营销。
---

# 亚马逊女装宣发视频制作技能

## 概述

本技能专为亚马逊女装电商视频制作设计，融合了电影级分镜模板与Seedance 2.0最佳实践，输出可直接用于AI视频生成的脚本与故事板提示词。

## 使用场景

- 制作亚马逊女装产品宣发视频脚本
- 生成Seedance 2.0兼容的故事板提示词
- 创建面向美国市场的女装电商视频内容
- 设计15秒的产品展示视频（默认），可选30秒/60秒/90-120秒

## 工程参数红线

### 视频硬约束
- **总时长**：15秒（默认），可选30秒/60秒/90-120秒
- **单镜头时长**：不限制，只要总时长不超限即可
- 中文提示词字数上限：500字（含标点）
- 视频分辨率：1080P或2K
- 帧率：24fps
- 无音乐、无字幕：所有视频片段必须为纯画面，后期添加

### 画幅比例
- [ASPECT_RATIO: 16:9] — 标准横屏（亚马逊主图视频推荐）
- [ASPECT_RATIO: 9:16] — 竖屏（社交媒体短视频）

## 女装视频专用资产库

### 模特类型库（美国市场，30-55岁）

#### 族裔与风格匹配
1. **Caucasian Professional** - 职业女性，适合通勤装、商务休闲
2. **African American Bold** - 自信大胆，适合鲜艳色彩、大胆图案
3. **Latina Warm** - 温暖亲切，适合波西米亚风、度假装
4. **Asian American Minimal** - 极简风格，适合基础款、都市简约
5. **Mixed Heritage Modern** - 现代混血，适合时尚前卫、混搭风格

#### 年龄分层风格
- **30-35岁**：时尚前沿，适合快时尚、潮流款式
- **36-45岁**：成熟优雅，适合品质女装、职场装
- **46-55岁**：经典永恒，适合高端女装、舒适面料

### 服装品类关键词

#### 上装
- Blouse（衬衫）：silk, chiffon, cotton, linen, satin
- T-shirt（T恤）：crew neck, V-neck, scoop neck, oversized, fitted
- Sweater（毛衣）：cashmere, wool, cable knit, turtleneck, cardigan
- Jacket（夹克）：blazer, denim, leather, bomber, trench coat

#### 下装
- Pants（裤子）：wide-leg, straight, skinny, bootcut, palazzo
- Skirt（裙子）：A-line, pencil, maxi, mini, wrap
- Jeans（牛仔裤）：high-rise, mid-rise, distressed, dark wash, light wash

#### 连衣裙
- Dress（连衣裙）：wrap, shift, sheath, fit-and-flare, bodycon
- Maxi dress（长裙）：flowy, bohemian, floral, solid
- Cocktail dress（礼服裙）：sequin, lace, velvet, satin

### 面料材质词库

#### 天然面料
- Cotton（棉）：soft, breathable, casual, everyday
- Linen（亚麻）：lightweight, summer, relaxed, natural texture
- Silk（丝）：luxurious, elegant, drapes beautifully, sheen
- Wool（羊毛）：warm, structured, winter, cozy
- Cashmere（羊绒）：ultra-soft, premium, lightweight warmth

#### 合成面料
- Polyester（涤纶）：wrinkle-resistant, durable, easy care
- Nylon（尼龙）：lightweight, quick-dry, sporty
- Spandex（氨纶）：stretchy, form-fitting, activewear
- Rayon（人造丝）：soft, drapes well, breathable

#### 特殊面料
- Denim（牛仔布）：casual, durable, versatile
- Leather（皮革）：edgy, premium, structured
- Velvet（天鹅绒）：luxurious, textured, evening wear
- Lace（蕾丝）：feminine, delicate, romantic
- Sequin（亮片）：glamorous, party, statement

### 场景库

#### 生活场景
- **Home**：客厅、卧室、衣帽间、阳台
- **Office**：开放式办公区、会议室、前台
- **City**：街头、咖啡店、商场、艺术区
- **Outdoor**：公园、海滩、度假村、乡村

#### 氛围关键词
- Natural light（自然光）
- Golden hour（黄金时刻）
- Soft diffused light（柔和漫射光）
- Urban backdrop（城市背景）
- Minimalist interior（极简室内）

## 故事板生成流程

### 步骤1：产品分析
输入产品信息，提取：
- 服装品类与款式
- 面料材质
- 目标人群（年龄/族裔/风格）
- 核心卖点

### 步骤2：故事节拍设计

#### 15秒视频（默认）
- 总时长15秒，镜头数量不限制
- 根据产品特点灵活分配：
  - **简单款**：1-2个镜头（如：单镜头15秒展示）
  - **标准款**：2-3个镜头（如：特写+展示）
  - **复杂款**：3-4个镜头（如：特写+细节+展示+场景）
- 镜头时长根据内容重要性灵活调整，总时长控制在15秒内

#### 30秒视频（2-3条分镜链）
1. **BEAT_01**（5-8秒）：产品特写，展示面料质感
2. **BEAT_02**（10-15秒）：模特穿搭展示，多角度
3. **BEAT_03**（5-8秒）：场景化展示，生活方式呈现

#### 60秒视频（4-5条分镜链）
1. **BEAT_01**（8-10秒）：开场氛围，建立场景
2. **BEAT_02**（12-15秒）：产品细节特写
3. **BEAT_03**（15-20秒）：模特穿搭动态展示
4. **BEAT_04**（10-12秒）：场景化应用
5. **BEAT_05**（5-8秒）：品牌收尾

#### 90-120秒视频（5-6条分镜链）
完整叙事弧线，包含：
- 开场建立
- 产品展示
- 穿搭教程
- 场景应用
- 品牌收尾

### 步骤3：镜头设计

#### 运镜快捷键（女装专用）
- **[CAM-CU]** 产品特写：面料纹理、细节工艺
- **[CAM-MCU]** 半身展示：上身效果、搭配展示
- **[CAM-LS]** 全身展示：整体造型、动态走姿
- **[CAM-DETAIL]** 细节特写：纽扣、拉链、缝线、图案
- **[CAM-LIFESTYLE]** 生活场景：自然状态下的穿搭

#### 光影设置
- **[LIT-NATURAL]** 自然光：柔和、真实、亲切
- **[LIT-STUDIO]** 影棚光：专业、清晰、突出产品
- **[LIT-GOLDEN]** 黄金时刻：温暖、浪漫、高端
- **[LIT-MINIMAL]** 极简光：干净、现代、高级感

### 步骤4：Seedance 2.0提示词生成

#### 焦点模式原则
遵循[SEEDANCE_PROMPT_FOCUS]：
- 只描述动作、表情、物理效果、镜头运动
- 不描述已在参考图中锁定的外貌和场景
- 强化动态微瑕疵（自然不对称、呼吸起伏）
- 量化运动参数

## 输出格式

### Part 1：完整视频脚本
按电影感分镜格式输出中文完整视频脚本，所有镜头统一呈现：

```markdown
# [产品名称] 视频脚本

## 一、场景拆解

### 核心主体
- **产品**：[品类/款式/面料/颜色]
- **模特**：[年龄/族裔/身材/风格]
- **穿着效果**：[上身效果/显瘦/气质等]

### 环境与光线
- **场景**：[室内/室外，具体场景]
- **空间布局**：[前景/中景/背景描述]
- **光线**：[方向/质感/色温]
- **隐含时段**：[时间]
- **氛围关键词**：[3-5个词]

### 视觉锚点
- [色调]
- [标志性元素]
- [光线风格]
- [颜色一致性]

## 二、主题与故事

### 主题
[一句话概括视频核心卖点]

### 故事梗概
[预告片风格的一句话故事]

### 情感弧线
1. **铺垫**：[建立场景，引入产品]
2. **发展**：[展示细节，突出卖点]
3. **转折**：[动态展示，激发欲望]
4. **高潮**：[完美呈现，行动号召]

## 三、电影化创作思路

### 镜头递进策略
[从XX过渡到XX，服务于情感节拍]

### 镜头运动方案
[推镜/拉镜/摇镜/环绕等] — [选择原因]

### 镜头与曝光
- **焦距范围**：[18/24/35/50/85mm]
- **景深倾向**：[浅/中/深]
- **快门质感**：[电影感/纪录片感]

### 光线与色彩
- **对比度**：[描述]
- **主色调**：[色系]
- **材质呈现**：[优先级]

## 四、关键帧列表（KF#）

### KF 01 | X秒 | 运镜方式
- **首帧**：[分镜图描述]
- **画面**：[简洁画面描述]
- **动作**：[模特/产品动作]
- **对白**：[如有]
- **特效**：[如有]

**Seedance提示词**：
[直接复制到即梦AI的中文提示词]

---

### KF 02 | X秒 | 运镜方式
- **首帧**：
- **画面**：
- **动作**：
- **对白**：
- **特效**：

**Seedance提示词**：
[提示词]

[继续添加关键帧，5-7个镜头，每个2-3秒，总和15秒]
```

### Part 2：故事板图片感提示词

输出一个专业电影级故事板，用于Midjourney/DALL-E生成分镜网格图：

**重要：一致性解决方案**

在Midjourney中生成故事板网格图时，必须使用以下参数锁定角色和风格：

```
--cref [角色参考图URL] --cw 100 --sref [风格参考图URL] --sw 100
```

并在提示词中强调：
```
EXACT SAME character throughout all panels, EXACT SAME clothing, 
consistent appearance, identical outfit color and style, 
same person in every shot, no variation in character design
```

**完整提示词模板**：

```markdown
Professional cinematic storyboard production board, Amazon women's clothing main image video pitch deck, Hollywood production style, grid layout, high resolution 4K, professional film industry aesthetic, clean and modern design.

EXACT SAME character throughout all panels, EXACT SAME clothing, consistent appearance, identical outfit color and style, same person in every shot, no variation in character design.

Board structure strictly follows this layout:

【TOP: PROJECT HEADER BAR】
Large bold black title: [AMAZON + 产品品类大写 + | 15-SEC MAIN IMAGE VIDEO]
Subtitle: [BRAND + SEASON YEAR COLLECTION | HIGH-CONVERSION E-COMMERCE STORYBOARD]

【LEFT SIDE: CHARACTER REFERENCE】
Section title: CHARACTER REFERENCE
3 vertical thumbnails:
1. [模特年龄] year old [族裔] plus size female model, [体型描述], [表情], [妆容]
2. Full body shot of model in white basic t-shirt, showing real body shape
3. [细节特写描述，如手部、配饰、发型等]

【MIDDLE: ENVIRONMENT & SET DESIGN】
Section title: ENVIRONMENT + SET DESIGN
3 horizontal thumbnails:
1. [主场景描述，含光线、道具等]
2. [辅助场景描述，含环境特征]
3. [背景/角落描述，突出产品]

【RIGHT SIDE: 15-SEC STORYBOARD (TOTAL DURATION)】
Section title: 15-SEC STORYBOARD
Grid layout with exactly 5-7 shots
Each shot cell: Shot number + visual thumbnail + duration + brief English description
AI auto-calculates duration per shot, sum = exactly 15 seconds
Fast-paced editing, each shot 2-3 seconds max
Focus keywords: packaging, fabric texture, fit, movement, color showcase

Shot descriptions:
SHOT 01 (3s): [产品特写/包装展示]
SHOT 02 (3s): [面料细节/纹理]
SHOT 03 (3s): [模特上身/合身效果]
SHOT 04 (3s): [动态展示/走姿]
SHOT 05 (3s): [场景应用/完整造型]

【BOTTOM: PRODUCTION INFO CARDS】
White background, black text, 10 neatly arranged cards:
PROJECT: Amazon Main Image Video
PRODUCT: Plus Size Women's [品类]
TOTAL DURATION: 15 SECONDS EXACTLY
GENRE: E-Commerce Product Commercial
CINEMATOGRAPHY: Close-ups + full shots, dynamic movement
LIGHTING: Soft diffused natural light, no harsh shadows
COLOR PALETTE: [色调描述], true-to-life product colors
COMPOSITION: Center framing, product always in visual focus
ASPECT RATIO: 16:9 Horizontal
OUTPUT FORMAT: 1080P 30fps MP4, Amazon platform optimized

Overall style: Professional, premium, authentic, conversion-focused.
--cref [角色参考图URL] --cw 100 --sref [风格参考图URL] --sw 100
```

**替代方案**：如果一致性仍然不理想，改用以下工作流：
1. 单独生成每个镜头的首帧图片（用图生图，保持一致性）
2. 用拼接工具（Figma/Canva）手动组合成故事板网格图
3. 只用故事板提示词生成布局框架，首帧图片单独处理
Strictly adheres to Amazon platform guidelines.
All storyboard shot descriptions and labels in ENGLISH ONLY.
```

---

## 负向提示词（通用）
动画、卡通、塑料、光滑、完美对称、瓷白牙齿、手指畸形、眼神呆滞、完全静止、水印、文字、签名

## 生成参数
- 分辨率：1920x1080
- 时长：15秒（总时长）
- 运动幅度：中等
```

### 镜头时长分配原则
- **15秒视频**：总时长15秒，镜头数量和单镜头时长不限制，根据内容需要灵活分配
- **30秒视频**：总时长30秒，镜头数量不限制
- **60秒视频**：总时长60秒，镜头数量不限制
- 根据内容重要性灵活分配时长，重要分镜可适当延长

## 检查清单

### 资产与工程
- [ ] 模特三视图已生成且符合画幅比例
- [ ] 场景参考图已生成
- [ ] 风格关键帧已建立（至少3张）
- [ ] 总时长≤15秒（默认）
- [ ] 每个镜头提示词≤500字

### 视觉与表演
- [ ] 画面描述只写变化，不写静态外貌/场景
- [ ] 静止镜头注入呼吸感微动
- [ ] 表情描述包含自然瑕疵（非完美微笑）
- [ ] 避免英文标签，使用自然语言

### 节奏与连续性
- [ ] 快节奏后跟[BREATHE]呼吸镜头
- [ ] 每个镜头有KEYFRAME_START和KEYFRAME_END
- [ ] 场景切换考虑光色或动作衔接

### 安全与风格
- [ ] 中文负向提示词已填写
- [ ] 角色无故直视镜头已避免
- [ ] 风格符合写实电影感，禁止动画/卡通/塑料

## Seedance 2.0操作流程

### 准备阶段
1. 生成参考图：为模特（多角度）和场景生成高清图，生成3-5张风格关键帧
2. 上传参考图：打开即梦AI Seedance 2.0 → 图生视频 → 开启"全能参考"模式
3. 选择参考模式：根据需要选择"首帧"、"尾帧"或"主体参考"

### 生成阶段
1. 填写提示词：粘贴中文自然语言提示词（≤500字）
2. 绑定参考图：使用@符号绑定参考图
3. 设置参数：分辨率、时长、运动幅度
4. 设置负向词：填写中文负向提示词
5. 生成

## 失败回退策略

| 失败现象 | 回退策略 |
|---------|---------|
| 人脸漂移 | 强化表情描述；更换匹配角度的参考图 |
| 角色完全静止 | 显式写入"缓慢呼吸，身体微微晃动，眼皮自然眨动" |
| 手指畸形 | 裁剪构图避开手部；简化手势为握拳或自然下垂 |
| 表情对称 | 显式写"右侧嘴角先动，左侧幅度稍小" |
| 风格漂移 | 更换风格关键帧；后期叠加胶片颗粒 |
| 水印/文字出现 | 强化负向提示词；后期裁剪或AI修复 |

## 参考资料

本技能包含以下参考资料，可在需要时查阅：

### references/camera-styles.md
人像摄影相机风格参考，包含不同相机型号的视觉效果特点，适用于不同风格的女装视频拍摄。

### references/model-library.md
女装模特库，包含美国市场30-55岁不同族裔模特的风格特点、适用服装、场景搭配和提示词示例。

### references/scene-library.md
女装视频场景库，包含家庭、办公、城市、户外等场景的详细描述、氛围关键词和提示词示例。

### references/fabric-library.md
女装面料材质词库，包含天然面料、合成面料、特殊面料的特性、适用款式、质感关键词和提示词示例。

### references/storyboard-templates.md
女装视频故事板模板，包含30秒、60秒、90-120秒视频的分镜链模板和Seedance 2.0专用提示词模板。

## 使用流程

1. **输入产品信息**：提供服装品类、面料、目标人群、核心卖点
2. **选择视频时长**：默认15秒，可选30秒/60秒/90-120秒
3. **生成故事板**：根据模板生成分镜链和镜头
4. **生成提示词**：为每个镜头生成Seedance 2.0兼容的提示词
5. **输出交付物**：输出完整的故事板和提示词文档

## 注意事项

- **默认时长**：视频默认15秒，除非用户特别指定其他时长
- 所有提示词必须为中文，遵循Seedance 2.0焦点模式原则
- 模特年龄严格限定在30-55岁，符合美国市场定位
- 视频风格为写实电影感，禁止动画、卡通、塑料质感
- 参考图必须提前生成，确保角色、场景、风格一致性
- 每个镜头必须包含必要的防御词：真实皮肤质感、自然不对称、微微颤抖、呼吸起伏
