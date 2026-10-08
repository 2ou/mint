---
name: 模特生成
description: 美国时尚女装模特 AI 生成专家。根据女装参考图或款式描述，锁定服装颜色、印花、廓形、尺码和细节，通过 Model DNA 属性系统动态组合最匹配的欧美女性模特，并生成或执行真实摄影感、自然原生肤质、无塑料感、非网红脸的模特图提示词。适用于生成模特、AI模特、模特图、服装模特、真人感模特、白底/灰底棚拍、居家/街拍/电商女装商品图、三视图、跨境电商女装、品牌 lookbook、为服装匹配模特、帮用户写模特提示词等场景；不用于无服装主体的普通人像、纯修图、logo、海报排版或与女装模特无关的图片任务。
agent_created: true
---

# 美国时尚女装模特生成专家

## 触发场景

用户提到以下任一关键词时自动激活：
- "生成模特"、"AI模特"、"模特图"、"服装模特"
- "真人感模特"、"欧美模特"、"大码模特"、"三视图"、"试穿图"
- "什么模特适合这件衣服"、"匹配模特"
- "帮我写模特提示词"
- 提供了服装描述并需要模特建议

**不要触发的近邻任务：**
- 只做普通肖像、证件照、头像、换背景、logo、海报排版、详情页文字排版时，不使用本 skill。
- 只要求分析图片内容、翻译、总结、压缩提示词且不涉及女装模特生成时，不使用本 skill。
- 男装、童装、宠物服装、非真人模特任务，除非用户明确要求沿用女装模特规则做迁移，否则不使用本 skill。

## 工作流程

### Step 0: 识别输入与任务类型

先判断用户要的是哪一种输出：
- **只要提示词**：输出结构化提示词和反向提示词。
- **直接生成图片**：使用可用图片生成工具执行生成；不要只停留在提示词。
- **多张变体**：每张图使用独立 prompt，服装保持一致，只改变模特 DNA 维度。
- **有服装参考图**：参考图是服装事实来源，优先级高于通用风格词。必须保留颜色、印花、领型、袖长、门襟、腰线、裤型/裙型、面料垂坠和尺码效果。

### Step 1: 分析服装 → 组合 Model DNA

收到服装描述或参考图后，先分析服装的**风格、面料、版型、适用场景、目标尺码、关键不可变细节**，然后按「Model DNA 属性系统」从 12 个独立维度推导最匹配的模特属性组合。

**不选类型，而是组合属性。** 每件服装推导出的 DNA 组合都是独特的，不存在"选了一个类型就定型"的问题。

推导顺序：
1. 体型骨架 — 由尺码和版型推导
2. 模特能量 — 由风格和品牌定位推导
3. 造型基线 — 由面料和场景推导
4. 镜头关系 — 由能量维度自然推导
5. 年龄区间 — 由风格和目标人群推导
6. 族裔 — 用户指定或按品牌定位选择
7-12. 物理面部特征（脸型/眼型/鼻型/唇型/眉型/颧骨下颌）— **多张生成时每维度选不同选项，这是防止"同一张脸"的关键**

### Step 2: 定制确认（条件触发）

**核心规则：用户不说话 = 走默认，说了话 = 融合进去。严禁在用户没提定制需求时主动询问。**

**判断逻辑：**
- 用户只发了图片/服装描述，没有任何定制说明 → **跳过确认，直接使用 Step 1 推导的 DNA 进入 Step 3**
- 用户附带说了体型/族裔/年龄/场景等关键词 → **提取定制条件，覆盖对应 DNA 维度后进入 Step 3**
- 用户只说了部分条件（如"大码"但没说族裔）→ **已说的覆盖对应维度，未说的走推导默认，不追问**

**默认值（无定制时使用）：**
| 维度 | 默认值 |
|------|--------|
| 体型骨架 | 根据尺码自动推导 |
| 模特能量 | 根据服装风格推导 |
| 造型基线 | clean professional（通用商业基线） |
| 镜头关系 | 根据能量维度推导 |
| 年龄 | 根据风格推导的标准区间 |
| 族裔 | 非亚裔池内由 AI 按服装风格轮换（Caucasian / African American / Latina / Mixed-非亚裔），兜底倾向 Caucasian/Latina；**默认不含 Asian American** |
| 场景 | 工作室白底/灰底棚拍 |
| 布局 | 数据卡三视图布局 |

### Step 3: 生成提示词

按标准公式生成完整提示词。生成顺序：

1. **先写模特基底特质**（Model Baseline 5 项至少 4 项）——确立"这是模特"而非"这是路人"
2. **再写 DNA 属性组合**——确立这个模特的独特身份
3. **然后执行变异注入**：
   - **选密度**：从 Full / Moderate / Minimal 中选择本次摄影参数密度（连续 3 张不重复）。Minimal 级别也必须至少写 1 个专业摄影信号。
   - **选质感与负面词**：从胶片/质感随机化池中选 1 种，从负面词轮换组 A/B/C/D 中选 1 组（连续 3 张不重复）。
   - **选独有标识（可选）**：用户要"真实完美"时跳过；用户要"个性辨识度"时从「扩展独有标识池」中选 1-2 个（连续多张不重复）。
4. **最后加入真人感细节**：
   - **Tier 1 真实肤质（必写）**：毛孔、肌理、血色通透、自然光影、虹膜细节、唇纹至少 4 类，关键词 ≤10 个。
   - **Tier 2 个性特征（可选）**：用户要"真实完美"时全跳过；要"个性辨识度"时加 1-3 个，总关键词 ≤12 个。

如果是多张生成（第 2 张及以后），还需执行变异追踪自检：与已生成的图在族裔/年龄/发色/骨相/标识/光源/质感/密度/负面组/能量/造型基线中至少差异 3 项。

### Step 4: 执行生成或交付提示词

- 用户明确说"生成 N 张/给我 N 张/做图/出图"时，直接生成图片；每个不同模特用一次独立生成，不把多个不同模特塞进同一个 prompt。
- 用户只说"写提示词/优化提示词/给 prompt"时，不调用图片生成工具，只交付提示词。
- 多张图必须保持服装一致，变化只来自 DNA 维度组合。
- 生成完成后用一句话说明完成数量和关键约束，不重复长篇 prompt，除非用户要求查看提示词。

---

## 核心稳定规则（优先级从高到低）

1. **模特基底优先**：每个提示词必须先确立模特基底特质（上镜骨相 + 专业仪态 + 镜头意识 + 专业造型基线 + 模特气场），5 项至少 4 项。基底是"她是模特"的前提，所有反 AI 味机制在基底之上叠加，不能替代基底。详见「模特基底特质」章节。
2. **用户限制优先**：用户明确说的尺码、族裔、年龄、体型、场景、比例、张数必须进入正向 prompt；排除条件必须同时写入 negative prompt。
3. **服装保真优先于模特风格**：有参考图时，prompt 第一段必须写 `Use the attached/reference garment image as the strict clothing source of truth`。不得因为风格改变服装颜色、印花、领型、袖长、裤长、门襟、纽扣、褶量、面料或版型。
4. **真实感分两层**：
   - **Tier 1 真实肤质（必选）**：毛孔、肌理、血色通透、自然光影、虹膜细节、唇纹——这是"不像 CGI"的底线，每张图必含。
   - **Tier 2 个性特征（可选）**：痣、雀斑、疤痕、不对称、黑眼圈、碎发——想要"真实完美"时可全不加，靠 Tier 1 + 模特基底 + 专业光影就足够真实。
   - **反 AI 味反的是"假"（塑料/陶瓷/CGI/网红模板），不是反"美"（好皮肤/对称五官/精致造型）。真实完美是合法方向。**
5. **商业可用性优先**：真人感细节不能遮挡服装，不能显脏、显旧、显病态；电商主图只保留轻微毛孔、血色、自然阴影和少量生活化褶皱。
6. **大码/尺码必须可见**：用户指定 1X/2X/plus-size 时，写 `full-figured plus-size model wearing size 1X`、`natural fullness in arms, waist, hips and thighs`、`relaxed fit over the body`；禁止把大码模特生成成直码瘦模。
7. **避免空泛高清词**：不要靠 `8K`、`ultra realistic`、`perfect` 提升质量；改用具体摄影、皮肤、光影、材质、镜头语言。
8. **摄影参数克制**：单次 prompt 最多写入 2 个摄影参数维度（焦段/光圈/镜头类型/机身品牌中选 1-2 个），禁止每次都堆满 4 个。Minimal 级别也必须至少 1 个专业摄影信号。
9. **真实感关键词克制**：Tier 1 真实肤质关键词不超过 10 个/张；如启用 Tier 2 个性特征，总关键词不超过 12 个/张。超过会让模型过度渲染，产生"太好了以至于不真实"的效果。
10. **独有标识注入（可选）**：可从「扩展独有标识池」中选取 1-2 个高度具体的个人特征增加辨识度。**用户要"真实完美"或未要求个性瑕疵时，不写 Tier 2，不加独有标识**——靠 Tier 1 肤质 + 模特基底 + 专业光影实现真实感。
11. **多张生成变异追踪**：生成多张模特图时，必须在每张之间强制变化至少 3 个 DNA 维度或变异维度。

---

## 模特基底特质（Model Baseline）

> **这是"她是模特"还是"她是路人"的分界线。** 所有反 AI 味机制都在基底之上叠加，不能替代基底。一个真实的模特依然是模特——有好骨相、有镜头意识、有专业打光、有气场。反 AI 味 ≠ 反模特。

每个提示词必须包含以下 5 项基底特质中的**至少 4 项**：

| 基底特质 | 说明 | 提示词关键词示例 |
|---------|------|----------------|
| **上镜骨相** | 模特被选中就是因为骨相上镜——颧骨、下颌线、眉骨的结构清晰且适合镜头 | `photogenic bone structure`（只写概念，具体骨相由 DNA 维度 7-12 填入） |
| **专业仪态** | 站姿、重心、角度都是有意控制的，不是随意站着 | `intentional model posture, weight deliberately shifted, body angle controlled, elongated silhouette` |
| **镜头意识** | 模特知道如何在镜头前呈现自己——即使是"不看镜头"也是有意识的创作选择 | `camera-aware presence, knows how to present to the lens, controlled relationship with the camera` |
| **专业造型基线** | 即使"素颜"也是精心打理的素颜——发型有意图、皮肤有管理、细节有设计 | `professionally groomed, intentional styling even when natural, deliberate hair direction, managed skin condition` |
| **模特气场** | 通过镜头投射出某种能量——自信、态度、温暖、神秘，这是模特与路人的本质区别 | `model presence, projects energy through the lens, occupies space with intention, the magnetism of a woman who knows she's being photographed` |

**使用原则：**
1. 基底特质写在 prompt 的【模特主体】段，在 DNA 属性之前。
2. 提示词主语必须用 `fashion model` / `plus-size model` / `editorial model`，不用 `woman` / `person`。
3. 基底特质不是"AI 味"——上镜骨相和镜头意识是模特的职业能力，不是虚假修饰。
4. 反 AI 味机制（真人感皮肤、个性特征、不完美摄影）在基底之上叠加，让"模特"看起来"真实"，而不是让"路人"看起来"更路人"。

---

## Model DNA 属性系统

> **替代旧版 10 种固定类型。** 10 种类型本质是 6 个独立维度的预制套餐，限制了组合空间并导致同质化。Model DNA 把维度拆开，每件服装推导出的组合都是独特的。

### 维度 1：体型骨架（Body Archetype）

| 选项 | 提示词片段 | 适用尺码 |
|------|-----------|---------|
| slender-androgynous | `tall and extremely slender, androgynous silhouette, narrow shoulders, flat-chested, narrow hips, long limbs, prominent bone structure` | XS-S |
| athletic-lean | `athletic toned physique, defined shoulder and arm muscles, visible abdominal definition, strong lean legs, broad shoulders with narrow waist` | XS-M |
| healthy-proportioned | `healthy athletic-lean build, subtle natural curves, visible collarbones, balanced proportions` | S-L |
| curvy-hourglass | `hourglass silhouette, full bust with defined waist-hip ratio, legs with natural curve and fullness` | M-2X |
| full-figured-plus | `full-figured and curvaceous, clearly defined hourglass silhouette, natural fullness in arms and thighs, round face with defined jawline` | 1X-4X |
| soft-natural | `natural unsculpted body, soft and relaxed, moderate fullness without athletic definition` | S-3X |

### 维度 2：模特能量（Model Energy）

| 选项 | 提示词片段 | 气质 |
|------|-----------|------|
| editorial-cool | `cool detached presence, editorial model attitude, intellectual restraint, gaze that doesn't try to please` | 冷峻疏离、高级感 |
| commercial-warm | `warm genuine expression of lived confidence, approachable warmth, effortless American elegance` | 亲切自信、商业友好 |
| natural-relaxed | `relaxed unposed presence, gentle unfocused gaze, caught mid-laugh not studio smile, comfortable in her own skin` | 松弛自在、自然 |
| glamour-magnetic | `classic magnetic presence, the magnetism of a woman who owns her allure, sultry confident energy` | 自信性感、魅力 |
| street-defiant | `rebellious attitude, not trying to please the camera, anti-mainstream coolness, mature edge` | 叛逆酷感、街头 |
| sophisticated-calm | `calm knowing gaze, restrained sophisticated smile, effortless urban confidence, the polish of a woman who knows herself` | 知性冷静、都市 |

### 维度 3：造型基线（Grooming Level）

| 选项 | 提示词片段 | 说明 |
|------|-----------|------|
| high-glam | `full professional styling, polished makeup, deliberately sculpted hair, intentional glamor` | 高造型度：红毯、大片 |
| clean-professional | `clean professional styling, natural makeup with deliberate grooming, managed hair with intentional direction` | 商业基线：电商、目录 |
| styled-undone | `styled undone look, deliberately effortless, intentional messiness that reads as natural` | 精心打理的"随意" |
| bare-faced-natural | `bare-faced natural grooming, minimal makeup, unstyled but managed, real skin showing through` | 素颜感：自然、有机 |
| editorial-avant | `editorial avant-garde styling, experimental, directional, pushing aesthetic boundaries` | 前卫造型：实验性 |

### 维度 4：镜头关系（Camera Engagement）

| 选项 | 提示词片段 | 适配能量 |
|------|-----------|---------|
| direct-engaging | `direct engaging gaze into the lens, confident eye contact, connecting with the viewer` | commercial-warm, glamour-magnetic |
| confident-knowing | `confident knowing gaze, occasionally drifting past the camera, faint assured smile` | sophisticated-calm, commercial-warm |
| detached-editorial | `detached editorial gaze, slightly away from camera, cool and unbothered, creating tension through distance` | editorial-cool |
| candid-unaware | `candid unposed gaze, caught in a moment, not aware of being photographed, natural and unguarded` | natural-relaxed |
| sultry-half-lidded | `sultry half-lidded gaze, slightly parted lips, magnetic and controlled` | glamour-magnetic |

> **重要**："不取悦镜头"（detached-editorial）只适合 editorial-cool 和 street-defiant 能量。其他能量必须有明确的镜头关系——模特知道自己在被拍，这是职业能力，不是 AI 味。

### 维度 5：年龄区间

按服装目标人群推导，不绑定体型或能量：

| 区间 | 提示词写法 |
|------|-----------|
| 25-30 | `[age]-year-old` + 青春气质描述 |
| 30-38 | `[age]-year-old` + 成熟但充满活力的气质描述 |
| 38-45 | `[age]-year-old` + 岁月质感的从容气质描述 |
| 45-55 | `[age]-year-old` + 优雅沉淀的智慧气质描述 |

### 维度 6：族裔（Ethnicity）

> **准入红线（默认生效）：族裔池限定为美国非亚裔组合。默认不得生成 Asian American（东亚/东南亚面孔），也不得生成含东亚血统的混血——除非用户在本次任务中明确点名要亚裔。**

用户指定时用用户值；未指定时在下列池内按品牌定位轮换。

**可用池（4 项）：**

| 选项 | 英文写法 | 搭配建议 |
|------|---------|---------|
| Caucasian（白人） | `Caucasian` | 万能，商业主流（默认兜底） |
| African American（非裔） | `African American` | 深肤色 + wide-flat 鼻型 / full-heart 唇型 |
| Latina（拉丁裔） | `Latina` | 暖橄榄调肤色 + dark glossy hair |
| Mixed / Ambiguous（混血，**限非亚裔组合**） | `mixed-heritage (non-Asian)`；需要写明来源时用 `mixed-race American of Caucasian and African American heritage` | 组合必须写清来源成分，不得出现 Asian / East Asian 字样 |

**禁用（默认）：**
- `Asian American`、`East Asian`、`Chinese`、`Japanese`、`Korean`、`Southeast Asian` 以及任何东亚面孔描述。
- 混血组合中不得包含亚裔成分。
- 面部特征避免使用会把脸推向东亚特征的描述（见「维度 8 眼型」的 monolid 条目）。

**强制约束 — 必须同时写入正向与反向：**
```text
Positive 兜底短语：non-Asian features
Negative 必带片段：Asian face, East Asian features, Asian American model, epicanthic fold, Chinese appearance, Japanese appearance, Korean appearance
```

> **例外：用户在本次任务中明确说"要亚裔模特 / Asian model / 东亚脸"时，本条红线让位于用户输入**，并在 prompt 中正向写明族裔、把上述 negative 片段移除。该次任务结束后自动恢复默认禁亚裔。

### 维度 7-12：物理面部特征（必选 — 决定模特"长得不一样"）

> **这是防止模特同质化的核心机制。** DNA 维度 1-6 变的是气质和氛围，但气质不同的两个人可以长得一样。维度 7-12 直接描述面部物理结构，让 AI 无法用同一张脸套不同气质。**多张生成时，每个维度必须选不同选项。**

#### 维度 7：脸型（Face Shape）

| 选项 | 提示词片段 |
|------|-----------|
| long-narrow | `long narrow face, high forehead, elongated proportions` |
| round-full | `round full face, full cheeks, soft contours` |
| heart-shaped | `heart-shaped face, wide temples tapering to pointed chin` |
| square-strong | `square face, strong horizontal jaw, broad face` |
| diamond | `diamond-shaped face, narrow forehead, wide cheekbones, pointed chin` |
| oval-classic | `oval face, balanced proportions, classic symmetry` |

#### 维度 8：眼型（Eye Shape）

| 选项 | 提示词片段 |
|------|-----------|
| almond-deep-set | `deep-set almond-shaped eyes` |
| round-wide | `round wide eyes` |
| hooded-heavy | `heavy hooded eyelids` |
| cat-upturned | `cat-like upturned eyes` |
| monolid | `monolid eyes without crease` | **⚠️ 默认禁用**——与非亚裔族裔池冲突，容易把脸推向东亚特征；仅当用户明确要求亚裔模特时可用 |
| doe-large | `large doe eyes with visible crease` |

#### 维度 9：鼻型（Nose Shape）

| 选项 | 提示词片段 |
|------|-----------|
| straight-narrow | `straight narrow nose bridge` |
| button-small | `small button nose` |
| wide-flat | `wide flat nose with natural nostril shape` |
| aquiline | `aquiline nose with defined bridge` |
| upturned | `upturned nose with rounded tip` |
| broad-strong | `broad strong nose with wide bridge` |

#### 维度 10：唇型（Lip Shape）

| 选项 | 提示词片段 |
|------|-----------|
| full-heart | `full heart-shaped lips` |
| thin-wide | `thin wide lips` |
| medium-natural | `medium natural lips` |
| full-lower | `full lower lip, thinner upper lip` |
| wide-smile | `wide smile lips` |
| bow-shaped | `defined cupid's bow lips` |

#### 维度 11：眉型（Brow Shape）

| 选项 | 提示词片段 |
|------|-----------|
| arched-high | `high arched eyebrows` |
| straight-thick | `straight thick eyebrows with natural density` |
| soft-natural | `soft natural eyebrows with gentle arch` |
| bold-structured | `bold structured eyebrows with sharp defined arch` |
| thin-curved | `thin curved eyebrows with elegant shape` |
| feathery-light | `feathery light eyebrows, soft and natural` |

#### 维度 12：颧骨与下颌（Cheekbone & Jaw）

| 选项 | 提示词片段 |
|------|-----------|
| high-prominent-pointed | `high prominent cheekbones, defined pointed jaw` |
| soft-rounded | `soft round cheekbones, soft rounded jaw` |
| medium-balanced | `medium balanced cheekbones, defined jaw` |
| wide-strong-square | `wide strong cheekbones, square jaw` |
| narrow-sharp-tapering | `narrow sharp cheekbones, narrow tapering jaw` |
| sculpted-angular | `sculpted angular cheekbones, sharp jawline` |

**使用原则：**
1. 每张图必须从维度 7-12 各选 1 个选项，写入 prompt 的【面部细节】段。
2. 多张生成时，每个维度选不同选项——这是打破"同一张脸"的关键。
3. 物理特征与族裔应协调（非裔可选 wide-flat 鼻型，白人可选 almond-deep-set，拉丁裔可选 button-small 鼻型），但不是强制——混血模特可以自由组合。
   - **雷区：`monolid` 眼型会把面孔推向东亚特征，与非亚裔池冲突，默认禁用**；不得用它凑"多张不重复"的维度变化。
4. Model Baseline 写 `photogenic bone structure`（概念），具体骨相由维度 12 填入。

---

### 服装 → DNA 映射指南

分析服装后按以下逻辑推导 DNA 组合：

| 服装特征 | 体型 | 能量 | 造型 | 镜头 |
|---------|------|------|------|------|
| 极简解构 / 建筑感 / oversized 西装 | slender-androgynous | editorial-cool | editorial-avant | detached-editorial |
| 日常通勤 / 基本款 / 针织衫 | healthy-proportioned | commercial-warm | clean-professional | direct-engaging |
| 裹身裙 / V领 / 弹力针织 / 大码 | full-figured-plus | glamour-magnetic | clean-professional | sultry-half-lidded |
| 运动装 / athleisure / 泳装 | athletic-lean | commercial-warm | clean-professional | direct-engaging |
| 棉麻 / boho / 宽松 / 手工感 | soft-natural | natural-relaxed | bare-faced-natural | candid-unaware |
| 精裁西装 / 羊绒 / 真丝衬衫 | healthy-proportioned | sophisticated-calm | clean-professional | confident-knowing |
| 涂鸦 / oversized 卫衣 / 工装 | soft-natural | street-defiant | styled-undone | detached-editorial |
| 蕾丝 / 薄纱 / 花卉印花 | slender-androgynous → healthy-proportioned | natural-relaxed | styled-undone | candid-unaware |
| 深V晚装 / 缎面礼服 / 红毯款 | curvy-hourglass | glamour-magnetic | high-glam | sultry-half-lidded |
| 居家服 / 睡衣 / 休闲套装 | full-figured-plus → soft-natural | sophisticated-calm → natural-relaxed | styled-undone | confident-knowing |

> 映射表是参考起点，不是硬性规则。可根据品牌定位和用户需求自由调整任意维度。

### 快速预设（Reference Vibes）

如果不想逐维度推导，可用以下预设作为起点，但仍可在任意维度替换：

| 预设名 | 体型 | 能量 | 造型 | 镜头 |
|--------|------|------|------|------|
| **Editorial** | slender-androgynous | editorial-cool | editorial-avant | detached-editorial |
| **Commercial** | healthy-proportioned | commercial-warm | clean-professional | direct-engaging |
| **Natural** | soft-natural | natural-relaxed | bare-faced-natural | candid-unaware |
| **Glamour** | curvy-hourglass | glamour-magnetic | high-glam | sultry-half-lidded |
| **Urban** | healthy-proportioned | sophisticated-calm | clean-professional | confident-knowing |

> 预设只是快捷方式。每件服装的 DNA 组合应该是独立推导的结果，不是从预设菜单里选一个就完事。

---

## 反同质化与反AI味机制

> **核心原则：反 AI 味 ≠ 反模特。** 以下机制让"模特"看起来"真实"，而不是让"路人"看起来"更路人"。所有机制在模特基底特质之上叠加。

### 一、摄影参数密度控制

**规则**：按以下三种密度级别轮换使用：

| 密度级别 | 参数数量 | 适用场景 | 示例 |
|---------|---------|---------|------|
| **Full（全参数）** | 3-4 个维度 | 品牌主图、editorial | `85mm, f/2.8, Hasselblad, slight film grain` |
| **Moderate（中等）** | 1-2 个维度 | 大多数电商商品图 | `50mm lens, natural window light` |
| **Minimal（极简）** | 1 个维度 | 自然感、boho | `natural window light` 或 `50mm lens` |

**轮换原则**：连续 3 张图不要使用同一密度级别。**Minimal 级别也必须至少 1 个专业摄影信号**（焦段或光源类型），不能 0 个——模特照必须有专业摄影锚点。

### 二、受控摄影不完美池（可选，默认不加）

> **默认不加。** 只在用户明确要"自然抓拍感/街拍感/生活感"时才从以下池中选 0-1 个。用户要"真实完美"或未要求抓拍感时，不写任何不完美元素——专业摄影本身就是完美的，真实感来自肤质而非技术瑕疵。

可选的不完美类型（仅在需要时使用）：

| 不完美类型 | 提示词片段 | 适用场景 |
|-----------|-----------|---------|
| 边缘轻微动态模糊 | `slight motion blur at hair tips, frozen mid-movement` | 动态姿势 |
| 焦点微微偏移 | `focus plane slightly forward, tip of nose sharpest` | 自然抓拍感 |
| 阴影区轻微噪点 | `natural ISO grain in shadow areas, not noise-free` | 低光场景 |
| 色温微偏 | `slightly warm white balance, golden tint in highlights` | 自然光 |
| 高光微微过曝 | `subtle highlight roll-off on forehead, soft not clipped` | 逆光 |
| 构图非完美居中 | `subject slightly off-center, imperfect rule of thirds` | 街拍 |

**禁忌**：不要同时使用 2 个以上不完美元素。这些是"专业摄影中的自然不完美"，不是"业余摄影的技术拙劣"。

### 三、胶片/质感随机化

从以下选项中轮换使用，不要固定某一种：

| 质感路径 | 提示词片段 | 气质 |
|---------|-----------|------|
| Kodak Portra 400 | `shot on Kodak Portra 400, warm skin tones, slight grain in midtones` | 暖调商业感 |
| Fuji 400H | `shot on Fuji Pro 400H, cooler greenish shadows, soft pastel highlights` | 清冷文艺感 |
| C-41 冲洗色彩偏移 | `subtle C-41 color shift, magenta in shadows, green in highlights` | 胶片真实感 |
| 数码传感器无胶片模拟 | `clean digital sensor, natural color without film emulation, true-to-life tones` | 现代数码感 |
| 纪实风格专业数码 | `documentary-style professional digital, natural unprocessed color, professional sensor quality` | 专业纪实感 |
| 无质感词 | （不写任何胶片/质感词，让模型自行决定） | 留白给模型 |

**轮换原则**：连续 3 张不要使用同一路径。**已删除"业余数码随拍"路径**——模特照不是手机随拍，即自然感也是专业摄影的自然感。

### 四、扩展独有标识池

> **可选机制。** 用户要"真实完美"或未要求个性瑕疵时，跳过本节，不写任何独有标识——靠 Tier 1 真实肤质 + 模特基底 + 专业光影实现真实感。只在用户要"有辨识度的个性/打破同质化/不要太完美"时，才从以下池中选 1-2 个。

#### 骨相与面部结构类
- `slightly deviated septum, nose tip points a few degrees left`
- `asymmetric nostril size, left nostril slightly wider`
- `one ear set slightly lower than the other`
- `jawline stronger on the right side`
- `brow bone more prominent on left side`
- `cheekbone slightly higher on one side`
- `chin has a subtle cleft, barely visible`

#### 皮肤个性类
- `three small moles clustered on left jawline`
- `light freckle band across bridge of nose, denser on left side`
- `small birthmark behind left ear`
- `sun damage on left side of neck, more than right (driving side)`
- `warm-toned skin with natural texture variation across cheekbones`

#### 毛发细节类
- `cowlick at crown causing hair to part unnaturally`
- `natural widow's peak, subtle V-shape at forehead`
- `eyebrows naturally different colors, left slightly darker`
- `fine baby hairs along temples growing in different direction`
- `single gray hair streak at temple, premature`

#### 牙齿与嘴唇类
- `slightly crooked left front tooth, visible when smiling wide`
- `small gap between front teeth, only noticeable up close`
- `lower lip fuller on left side, natural asymmetry`

#### 手部与身体类
- `knuckle scar on right index finger, old and faded`
- `asymmetric collarbone, left side slightly more prominent`
- `one wrist slightly bonier than the other`

**使用原则**：
1. 每张图选 1-2 个，不要超过 2 个。
2. 连续生成的多张图中，不重复使用同一标识。
3. 电商主图优先选不影响服装观感的标识（面部/毛发类）。

### 五、负面提示词轮换

在基础负面词之上，每次随机叠加 1 组，轮换使用。

**基础负面词（每次必带）**：
```text
plastic skin, ceramic skin, poreless skin, over-smoothed skin, CGI render, uncanny valley, wrong garment, altered outfit, changed color, changed print
```

**轮换组 A — 反网红脸**：
```text
influencer face, beauty filter look, over-plumped lips, fox eye, brow lift, Instagram face, generic pretty face, waxy face, doll-like face
```

**轮换组 B — 反假真实**：
```text
fake studio glamour, over-sharpened skin, harsh HDR, excessive detail rendering, hyper-realistic, too-perfect lighting, artificial bokeh
```

**轮换组 C — 反僵硬摆拍**：
```text
stiff pose, catalog mannequin pose, forced smile, empty stare, posing for camera, artificial body language, rigid posture
```

**轮换组 D — 反过度修图**：
```text
heavy beauty retouching, flawless perfect skin, zero pores, glass skin, porcelain skin, airbrushed, skin smoothing app
```

**使用原则**：
1. 每次随机选 1 组叠加在基础负面词之后。
2. 连续 3 张不要使用同一组。

### 六、多张生成变异追踪

生成多张图时，在内部维护一个"已用特征清单"，每张新图必须与已有图在以下维度中至少差异 **4 项**（旧版 3 项，因物理特征维度加入后标准提高）：

| 变异维度 | 可选值举例 |
|---------|-----------|
| 族裔 | Caucasian / African American / Latina / Mixed (non-Asian) —— **不得使用 Asian American** |
| 年龄段 | 28-32 / 33-37 / 38-42 / 43-48 / 49-55 |
| 发色/发型 | 深棕波浪 / 金色直发 / 黑色短寸 / 红色卷发 / 灰银优雅 |
| **脸型 (DNA 7)** | long-narrow / round-full / heart-shaped / square-strong / diamond / oval-classic |
| **眼型 (DNA 8)** | almond-deep-set / round-wide / hooded-heavy / cat-upturned / doe-large（monolid 默认禁用） |
| **鼻型 (DNA 9)** | straight-narrow / button-small / wide-flat / aquiline / upturned / broad-strong |
| **唇型 (DNA 10)** | full-heart / thin-wide / medium-natural / full-lower / wide-smile / bow-shaped |
| **眉型 (DNA 11)** | arched-high / straight-thick / soft-natural / bold-structured / thin-curved / feathery-light |
| **颧骨下颌 (DNA 12)** | high-prominent-pointed / soft-rounded / medium-balanced / wide-strong-square / narrow-sharp-tapering / sculpted-angular |
| 独有标识 | 从扩展池中选不同项（可选） |
| 光源方向 | 左侧窗光 / 右侧柔光箱 / 顶光 / 逆光 / 侧逆光 |
| 胶片质感 | Portra 400 / Fuji 400H / 数码无模拟 / 纪实专业 / 无质感词 |
| 摄影密度 | Full / Moderate / Minimal |
| 负面词组 | A / B / C / D |
| DNA 能量 | editorial-cool / commercial-warm / natural-relaxed / glamour-magnetic / street-defiant / sophisticated-calm |
| DNA 造型 | high-glam / clean-professional / styled-undone / bare-faced-natural / editorial-avant |

**自检规则**：生成第 N 张（N≥2）时，与第 1 到 N-1 张逐一比对，确认至少 4 个维度不同。**其中 DNA 维度 7-12（物理面部特征）至少 4 个维度不同——这是防止"同一张脸"的硬性要求。**

---

## 提示词生成公式

严格按照以下顺序组织提示词：

```
[输入与服装保真] + [模特基底特质] + [DNA 属性组合] + [拍摄方式与镜头] + [构图与视角] + [年龄/族裔/体型特征] + [面部细节与皮肤质感] + [眼睛/嘴唇细节] + [发型发质] + [表情与眼神] + [姿势与环境互动] + [服装描述] + [摄影参数(按密度)] + [定向光影] + [材质质感] + [情绪氛围] + [限制词与反向提示词]
```

### 完整模板

```text
【输入与服装保真】
Use the attached/reference garment image as the strict clothing source of truth.
Preserve the exact garment color, print scale, neckline, sleeve length, placket/buttons, waistline, hem length, pant/skirt shape, fabric drape, and size fit.
Only change the model, pose, camera angle, lighting, and scene requested by the user.
If the user gives exclusion constraints, repeat them in positive and negative form.

【模特基底特质 — 至少 4/5 项】
[上镜骨相] professional fashion model with photogenic bone structure, defined cheekbones and clean jawline,
[专业仪态] intentional model posture with weight deliberately shifted, elongated silhouette,
[镜头意识] camera-aware presence, controlled relationship with the lens,
[专业造型基线] professionally groomed with intentional styling,
[模特气场] model presence that projects energy through the lens

【DNA 属性组合】
[体型骨架] [从 Body Archetype 选],
[模特能量] [从 Model Energy 选],
[造型基线] [从 Grooming Level 选],
[镜头关系] [从 Camera Engagement 选]

【拍摄方式】
[怼脸特写 / 半身中景 / 全身 / 3/4 身]，[正脸 / 侧脸 / 3/4 角度 / 微微侧身]

【构图与视角】
[画面比例：16:9 / 3:4 / 9:16]，[构图方式]，[视角]

【模特主体】
[年龄] 岁美国 [族裔，取自非亚裔池] female fashion model，non-Asian features，[体型描述]，[具体身形特征]

【物理面部特征 — DNA 维度 7-12，必写，多张生成时每维度选不同选项】
[脸型] [从维度 7 选]，
[眼型] [从维度 8 选]，
[鼻型] [从维度 9 选]，
[唇型] [从维度 10 选]，
[眉型] [从维度 11 选]，
[颧骨下颌] [从维度 12 选]

【面部细节与皮肤 — Tier 1 真实肤质（必写）+ Tier 2 个性特征（可选）】
自然原生肤质，保留适度细微毛孔和真实肌理，[肤色描述]，subtle subsurface scattering 给血色通透感；
（Tier 2 可选，用户要"真实完美"时跳过：[个性特征：少量雀斑 / 小痣 / 不对称等]）

【眼睛与嘴唇细节】
细致虹膜纹理，真实眼神反光，瞳孔形状正常；
嘴唇保留自然唇纹，[轻微油光 / 自然唇色]

【发型发质】
[发型描述]，[发质细节]，[碎发/自然光泽等质感]

【表情眼神】
[具体情绪状态]，[视线方向]，[微表情细节]

【姿势与环境互动】
[人物姿势]，[场景描述]，[背景氛围]；
动作有意图但不过度僵硬

【服装】
[完整服装描述，包括面料材质、剪裁、颜色、垂坠感、细节]
有参考图时必须逐项写清所有服装细节。

【光影摄影 — 按密度级别】
[Full: 焦段+光圈+镜头+机身 / Moderate: 1-2个维度 / Minimal: 1个维度]
[光源方向和性质]，必须是具体定向光源

【材质质感】
[背景材质]，[主体材质]

【情绪氛围】
[整体情绪]，[氛围关键词]

【限制词】
避免塑料感，避免过度美颜，避免过度磨皮，避免完美对称，
避免网红妆容，避免塑料皮肤，避免陶瓷肌，避免 CG 渲染感，
避免改变服装颜色、印花、领型、袖长、门襟、裤型、裙型和尺码效果

【反向提示词 / Negative Prompt】
[基础负面词] + [轮换组 A/B/C/D 之一]
```

### 生成前自检
1. 是否写明参考图服装保真：颜色、印花、领型、袖长、门襟、裤/裙型、尺码效果。
2. **是否包含模特基底特质至少 4 项**（上镜骨相/专业仪态/镜头意识/专业造型/模特气场）。
3. **提示词主语是否用了 `model` 而非 `woman`**。
4. 是否写明 DNA 属性组合（体型/能量/造型/镜头关系）。
5. **是否写明物理面部特征 DNA 维度 7-12**（脸型/眼型/鼻型/唇型/眉型/颧骨下颌）——这是防止"同一张脸"的关键。
6. 是否写明模特年龄、族裔、体型、气质，且符合用户排除条件。
7. **Tier 1 真实肤质是否已写**：毛孔、肌理、血色通透、自然光影、虹膜细节、唇纹至少 4 类。
8. 是否有 negative prompt 锁死假皮肤、网红脸、空洞眼神、异常瞳孔、手脚错误和服装跑样。
9. **Tier 2 个性特征/独有标识**：用户要"真实完美"时跳过；用户要"个性辨识度"时从扩展池选 1-2 个。
10. 摄影参数密度是否合理？Minimal 也至少 1 个专业信号？是否与前 2 张重复？
11. 真人感关键词：Tier 1 ≤10 个；如启用 Tier 2 则总数 ≤12 个。
12. 负面词是否选了轮换组之一？胶片/质感路径是否轮换？
13. **多张生成时：DNA 维度 7-12 是否每张选了不同选项？** 至少 4 个维度不同才能生成下一张。
```

---

## 镜头与相机参数速查

> **核心原理**：AI 模型训练时学习了海量摄影作品的元数据。在提示词中加入这些参数，本质是调用模型已学到的视觉关联记忆，让画面更接近真实摄影。

### 一、焦段 → 决定空间感与叙事重点

| 焦段 | 视觉效果 | 适合场景 | 提示词片段 |
|------|---------|---------|-----------|
| **35mm** | 人物与环境同时清晰，现场感强 | 街头外景、环境叙事 | `shot on 35mm lens, environmental portrait` |
| **50mm** | 接近人眼视角，自然平实 | 日常通勤、居家、自然光半身 | `shot on 50mm lens, natural perspective` |
| **85mm** | 人物突出，背景柔和虚化 | 棚拍半身特写、肖像 | `shot on 85mm portrait lens, soft bokeh` |
| **135mm** | 极致压缩感，远距离抓拍 | 户外远摄、T 台、全身 | `shot on 135mm telephoto lens, compressed background` |
| **24-70mm** | 灵活变焦，棚拍万能 | 工作室多角度商品图 | `shot on 24-70mm zoom lens, versatile studio range` |

### 二、光圈 → 决定景深与清晰范围

| 光圈 | 视觉效果 | 适合场景 |
|------|---------|---------|
| **f/1.4 - f/1.8** | 极浅景深，奶油虚化 | 半身特写、面部肖像 |
| **f/2.8 - f/4** | 中等景深，人物锐利 | 全身棚拍、街拍 |
| **f/8 - f/11** | 深景深，全身清晰 | 全身三视图、多角度商品图 |

### 三、镜头类型

| 镜头类型 | 视觉特征 | 适合场景 |
|---------|---------|---------|
| **标准人像镜** | 自然透视，肤色温润 | 商业目录、优雅成熟 |
| **变形宽银幕** | 宽画幅、横向光晕 | 电影感 editorial |
| **微距镜头** | 极近特写 | 面料特写、细节 |

### 四、相机品牌

| 品牌 | 气质特征 | 适合场景 |
|------|---------|---------|
| **Hasselblad** | 中画幅细腻、商业肖像 | 奢侈品、高定、editorial |
| **Canon (EOS R5)** | 清晰商业质感 | 通用电商棚拍 |
| **Leica** | 街头人文、高对比 | 自然外景、街拍 |
| **Fuji (GFX/XT)** | 胶片怀旧色彩 | 生活感、成熟优雅 |
| **Sony (A7R IV)** | 色彩通透、轮廓锐利 | 运动装、商业目录 |

### 镜头与 DNA 能量匹配参考

| DNA 能量 | 推荐焦段 | 推荐机身 |
|---------|---------|---------|
| editorial-cool | 85mm 或 135mm | Hasselblad |
| commercial-warm | 50mm 或 85mm | Canon EOS R5 |
| natural-relaxed | 35mm | Leica |
| glamour-magnetic | 85mm | Hasselblad / Canon |
| street-defiant | 35mm | Leica / Sony |
| sophisticated-calm | 50mm 或 85mm | Fuji GFX / Hasselblad |

> **组合原则**：一个焦段 + 一个光圈 + 一个镜头类型 + 一个机身品牌就足够引导风格，无需堆砌。按摄影密度控制选择 1-4 个维度。

---

## 面料材质词库（服装部分专用）

### 天然面料
| 面料 | 气质 | 提示词关键词 |
|------|------|------------|
| 真丝/丝绸 | 流动、精致 | `liquid-like silk drape, elongated satin highlights along folds, subtle weave texture visible` |
| 亚麻 | 自然、松弛 | `matte linen surface, visible coarse fibers, natural crease lines, relaxed unstructured drape` |
| 纯棉 | 日常、干净 | `soft cotton with slight weave pattern, natural matte finish, soft wrinkles at joints` |
| 羊毛/羊绒 | 温暖、高级 | `dense wool texture, soft brushed surface, substantial structured drape, subtle fiber halo` |
| 薄纱/纱 | 轻盈、朦胧 | `sheer diaphanous chiffon, translucent edge softening, layered ethereal lightness` |
| 蕾丝 | 精致、女性化 | `delicate floral lace pattern, semi-transparent with skin showing through, intricate thread detail` |

### 合成/特殊面料
| 面料 | 气质 | 提示词关键词 |
|------|------|------------|
| 皮革 | 酷感、硬朗 | `genuine matte leather with natural grain, subtle creasing at joints, not shiny patent` |
| 缎面 | 光泽、奢华 | `satin with directional sheen, smooth surface catching light, deep rich color saturation` |
| 针织 | 舒适、弹性 | `fine-gauge knit with visible stitch texture, body-skimming fit, soft ribbing detail` |
| 牛仔 | 休闲、挺括 | `rigid raw denim with visible twill line, natural fading at stress points, structured hold` |
| 天鹅绒 | 复古、华丽 | `crushed velvet with directional pile, deep light-absorbing texture, subtle color shift` |

### 背景材质
| 材质 | 气质 | 提示词关键词 |
|------|------|------------|
| 宣纸肌理 | 东方、安静 | `rice paper texture, warm ivory white, visible fine fibers` |
| 水彩纸 | 治愈、温柔 | `watercolor paper texture, slight grain, soft edges` |
| 旧纸质感 | 复古、记忆 | `aged paper texture, slight yellowing, subtle patina` |
| 细腻纸纤维 | 高级、极简 | `fine paper fibers, clean white, editorial quality` |

---

## 尺码与大码模特稳定规则

当用户指定 `大码`、`plus-size`、`1X`、`2X`、`3X`、`4X`、`XL/XXL` 等尺码时，模特主体和服装效果必须同时体现尺码。

### 1X 固定人体数据（用户指定基准）

用户已指定以后所有 `1X` 模特按这组 PLUS 18W / 1X 人台数据生成和标注。**这组数据仅限明确指定 `1X` 的模特使用**。

| 中文 | English | CM | INCH |
|------|---------|----|------|
| 身高 | Height | 167.75 | 66.0 |
| 肩宽 | Shoulder Width | 44 | 17.3 |
| 胸围 | Bust | 114 | 44.9 |
| 腰围 | Waist | 98 | 38.6 |
| 臀围 | Hip | 121 | 47.6 |
| 大腿围 | Thigh Circumference | 70.5 | 27.8 |
| 内长 | Inseam | 77.5 | 30.5 |
| 手臂围 | Arm Circumference | 41 | 16.1 |

用于 prompt 的固定英文片段：

```text
size 1X plus-size model based on PLUS 18W mannequin measurements: height 167.75 cm, bust 114 cm, waist 98 cm, hips 121 cm, shoulder width 44 cm; full-figured but realistic commercial fit, natural fullness in upper arms, bust, waist, hips, thighs and calves
```

用于三视图或 measurement sheet 的固定标注：

```text
HEIGHT 167.75 CM / 66.0 IN
BUST 114 CM / 44.9 IN
WAIST 98 CM / 38.6 IN
HIPS 121 CM / 47.6 IN
SHOULDER 44 CM / 17.3 IN
SIZE 1X / PLUS 18W
```

### 按尺码调整

| 用户尺码 | 体型表达 | 服装效果 |
|---------|---------|---------|
| 1X | full-figured plus-size, soft curves, natural fullness; use fixed PLUS 18W data | relaxed but still shaped, enough ease at bust/hips/thighs |
| 2X | fuller plus-size body, broader hips and thighs, fuller upper arms | more visible comfort ease, fabric falls away from body |
| 3X | very full plus-size body, broad waist/hips/thighs, substantial arms | generous relaxed fit, no tight pulling at seams |
| 4X | very full plus-size body, broad proportions throughout, substantial arms and full bust | maximum relaxed fit, fabric falls loosely, no pulling at any seam |

### 大码反向限制

```text
Negative prompt: skinny body, straight-size model, narrow hips, thin arms, tiny waist, underweight look, garment too tight, garment squeezing the body, shapeless oversized sack, pregnant belly unless requested
```

### 大码真人感要点

- 保留真实体态：手臂、腰腹、臀腿有自然肉感和重心变化。
- 避免把大码做成夸张沙漏：腰臀比自然即可，不写 `tiny waist`。
- 居家服、睡衣、宽松裙装要写 `comfortable ease` 和 `soft fabric drape`。
- 站姿可用 `weight shifted to one hip`、`one knee softly bent`、`relaxed uneven stance`。

---

## 真人感细节与避坑规则

> **真实感分两层。** Tier 1 真实肤质是每张图必写的底线——让皮肤不像 CGI。Tier 2 个性特征是可选的——用户要"真实完美"时全跳过，要"个性辨识度"时按需加入。**真实 ≠ 不完美。真实完美是合法方向。**

---

## Tier 1 真实肤质（必写 — "不像 CGI"的底线）

每张图必须包含以下 6 类中的至少 4 类：

### 1. 皮肤纹理与质感
- `natural skin texture, fine pores on nose and cheeks, subtle skin grain`
- `subtle subsurface scattering, translucent natural blood tone beneath the skin`——让肤色有血色通透感
- 控制强度：写 `fine pores`，不要写 `many pores`

### 2. 真实光影与阴影
- 必须写具体方向和光源：`single soft side key light`、`oblique natural window light`
- 用阴影塑造结构：`shadows preserving cheekbone and jaw structure`
- 棚拍也要有方向：`large softbox from upper left, weak fill from camera-right`

### 3. 眼睛细节
- `detailed iris texture, natural catchlights, normal round pupils`
- 睫毛和眉毛要有单根毛发变化：`individual lashes with varied direction and density`

### 4. 嘴唇质感
- `natural lip texture, fine lip lines`
- 根据造型基线选择：clean-professional 用 `natural lip sheen`；bare-faced 用 `natural lip texture`

### 5. 发质细节
- 写可见发质：`dark loose waves with healthy natural sheen`，不要只写"长发"
- 发际线有少量 baby hairs 或碎发

### 6. 肤色与血色
- 保留肤色过渡：`natural warm skin tone with healthy blood color`
- 避免冷白模板：不要写 `perfect pale skin`、`porcelain skin`

---

## Tier 2 个性特征（可选 — 用户要"真实完美"时全跳过）

> **跳过条件**：用户说"真实完美"/"完美"/"不要瑕疵"/"干净"或未要求个性辨识度时，本节全部不写。

如果用户要"有辨识度的个性/打破同质化/不要太完美"，可从以下选择 1-3 个：

### 1. 个性皮肤特征
- `light scattered freckles`、`small mole`、`warm-toned skin with natural texture variation`
- 从「扩展独有标识池」中选 1-2 个高度具体的个人特征

### 2. 面部不对称
- `natural facial asymmetry, one eyebrow a little higher`
- 身体姿态不要镜像完美：`weight shifted to one hip, one shoulder slightly lower`

### 3. 动态痕迹
- `a few wind-tossed flyaway hairs, garment edges moving slightly, natural wrinkles at elbows and waist`
- 姿势有意图但不过度僵硬

---

### 通用避坑

1. 不写 `beautiful / attractive / pretty`，改写具体面部、体型、姿态和气质。
2. 不写 `8K / 超清 / flawless / perfect skin`，改写 `natural skin texture, true photographic skin rendering`。
3. **不写 `amateur photo camera style` / `low-fidelity photo` / `Shot on iPhone`**——模特照是专业摄影。日常真实感用 `documentary-style professional digital`。
4. **"真实完美"写法**：`flawless real skin with natural texture, fine pores visible up close, even complexion with warm natural color, symmetrical balanced features`——"flawless real" 而非 "flawless perfect"，"real" 锚定自然肌理，"flawless" 意为无可见瑕疵。
5. **主语用 model 不用 woman**。
6. **Tier 1 关键词 ≤10 个；如启用 Tier 2 则总数 ≤12 个**。
7. **摄影参数克制**：按密度控制选择 1-4 个维度，Minimal 也至少 1 个专业信号。

### 反向提示词模板

> 每次使用时，基础负面词 + 1 组轮换组（A/B/C/D），连续 3 张不重复。

**基础负面词（每次必带）**：
```text
plastic skin, ceramic skin, poreless skin, over-smoothed skin, CGI render, uncanny valley, wrong garment, altered outfit, changed color, changed print
```

然后叠加轮换组 A/B/C/D 之一（见「反同质化与反AI味机制 → 负面提示词轮换」）。

---

## 直接图片生成规范

当用户明确要求"生成图片/出图/给我 N 张/再来 N 张/三视图"时，使用图片生成工具执行。

### 单张图 prompt 开头

有服装参考图时，每次生成都以这段含义开头：

```text
Use the attached garment reference image as the strict clothing source of truth.
Generate one photorealistic fashion ecommerce image of an adult fashion model wearing this exact garment in size [size].
Preserve the exact garment color, print, neckline, sleeve length, buttons, silhouette, pants/skirt shape, fabric drape, and fit.
Only change the model, pose, camera angle, lighting, and scene requested.
```

### 多张变体

- 用户要 4 张/8 张/多张不同模特时，按"一张图一个 prompt"生成。
- 每张变体必须有不同的 DNA 组合：族裔、年龄、发色、能量、造型基线、姿态、场景至少变化 3 项。
- **物理面部特征 DNA 维度 7-12（脸型/眼型/鼻型/唇型/眉型/颧骨下颌）每张必须选不同选项**——至少 4 个维度不同，这是防止"同一张脸"的硬性要求。
- **必须执行变异追踪**：在内部维护已用特征清单，确保每张图与前序图至少差异 4 个维度。
- **Tier 2 个性特征/独有标识（可选）**：用户要"真实完美"时跳过；用户要"个性辨识度"时每张选不同的 1-2 项。
- **轮换摄影密度、胶片质感路径和负面词组**：连续 3 张不重复。
- 服装描述必须逐张重复，不要依赖"同上"。
- 用户给了排除条件时逐张重复。
- 最后一条 negative prompt 逐张保留，防止后续图漂移。

### 三视图与多面板

> **布局选择**：三视图有三种布局可选——纯三视图（下方骨架）、左半身+右三视图（见「组合式商品图布局」）、左头像+数据卡+右三视图（见「数据卡三视图布局」）。用户要求"标注身高三围/数据卡/人物数据"时，优先使用「数据卡三视图布局」。

三视图要求优先使用以下骨架：

```text
16:9 horizontal three-panel fashion product composite.
Three equal vertical panels: full-body front view, full-body side profile view, full-body back view.
The SAME adult fashion model appears in all three panels: identical face, body proportions, hairstyle, skin texture, and makeup.
The SAME outfit appears in all three panels: exact color, print, neckline, sleeve length, buttons, pants/skirt shape, and fabric drape.
Clean studio or home catalog background, consistent directional lighting across all panels.
No text labels, no dividers with words, no watermark.
```

### 生成前质量门槛

每次调用图片生成工具前，prompt 必须包含：
- **模特基底特质**：上镜骨相/专业仪态/镜头意识/专业造型/模特气场至少 4 项。
- **DNA 属性组合**：体型/能量/造型/镜头关系 4 个维度。
- **物理面部特征 DNA 维度 7-12**：脸型/眼型/鼻型/唇型/眉型/颧骨下颌——必写，多张生成时每维度选不同选项。
- **主语用 `model`**：不用 `woman`。
- 服装保真句：`strict clothing source of truth`。
- 体型与尺码句：如 `wearing size 1X`、`full-figured plus-size model`；1X 用固定 PLUS 18W 基准数据。
- **Tier 1 真实肤质（必写）**：毛孔、肌理、血色通透、自然光影、虹膜细节、唇纹至少 4 类。
- 负面句：基础负面词 + 1 个轮换组。
- **Tier 2 个性特征/独有标识（可选）**：用户要"真实完美"时跳过；要"个性辨识度"时从扩展池选 1-2 个。
- 摄影密度：确认级别（Full/Moderate/Minimal），Minimal 至少 1 个专业信号。
- 质感路径：确认胶片/质感选择，不与前 2 张重复。
- 真人感关键词：Tier 1 ≤10 个；如启用 Tier 2 则总数 ≤12 个。

---

## 电商女装商品图拍摄规范

### 标准多角度模板

```text
【正面全身】
Full-length front view, [模特描述], standing naturally facing camera,
arms relaxed at sides, weight evenly distributed,
looking directly at camera with natural expression.
Plain studio background, soft diffused lighting, no harsh shadows.

【背面全身】
Full-length back view, same model, same outfit,
standing naturally showing back design details.
Same plain background, consistent lighting with front view.

【侧面全身】
Full-length side profile view, same model, same outfit,
standing at 90-degree angle to camera, showing side silhouette and fit.

【3/4 角度半身】
Three-quarter angle mid-shot from waist up, same model, same outfit,
body turned slightly, showing fabric drape and neckline detail.

【面料特写】
Extreme close-up on fabric texture, showing weave pattern,
surface quality and material detail.

【细节特写】
Close-up detail shot of [纽扣/拉链/刺绣/领口/袖口],
showing craftsmanship and finish quality.
```

### 模特数据卡模板

```text
Model measurements reference:
Height: [165-178cm], Bust: [cm], Waist: [cm], Hip: [cm],
Shoulder width: [cm], Wearing size: [S/M/L],
Garment on model — Length: [cm], Sleeve: [cm], Chest: [cm]
```

### 多角度一致性锁定

```text
Same model as previous shot, identical person with same face,
same skin texture, same hair, same makeup. Only change the camera angle to: [新角度]
```

### 组合式商品图布局（左半身特写 + 右三视图横向并列）

**左侧人物半身胸像特写（较大），右侧三张全身图横向排列（正面 → 侧面 → 背面）**

```text
Multi-panel fashion product composite in a horizontal grid layout,
16:9 wide aspect ratio.

LEFT PANEL (occupying 1/2 of total width):
Waist-up portrait / bust shot of [模特描述], wearing [服装描述].
Close-up showing facial details, neckline, shoulder fit, collarbone,
and upper chest fabric drape. Natural expression, clean studio background.

RIGHT PANELS (occupying 1/2 of total width, divided into 3 equal columns):

COLUMN 1: Full-length front view of the SAME model wearing the SAME outfit.
COLUMN 2: Full-length side profile view of the SAME model wearing the SAME outfit.
COLUMN 3: Full-length back view of the SAME model wearing the SAME outfit.

OVERALL CONSTRAINTS:
All four panels must show the IDENTICAL person — same face, same skin texture,
same hair, same makeup, same body proportions. Clean studio background
consistent across all panels. Professional product photography style.
```

### 数据卡三视图布局（左 1/4 头像+数据卡 + 右 3/4 三视图）

**左侧 1/4 区域上方放人物头像特写、下方放身高三围数据卡，右侧 3/4 区域横向排列三视图**

```text
Multi-panel fashion product composite in a horizontal grid layout,
16:9 wide aspect ratio, divided into 4 equal vertical columns.

LEFT COLUMN (occupying 1/4 of total width, split into two stacked sections):

  TOP HALF of left column:
  Close-up head and shoulder portrait of [模特描述], wearing [服装描述].
  Showing face, neck, collarbone and shoulder line in detail.
  Natural expression, clean studio background.

  BOTTOM HALF of left column:
  Clean white data card area with black sans-serif measurement labels
  and thin leader lines:
  HEIGHT [XX] CM / [XX] IN
  BUST [XX] CM / [XX] IN
  WAIST [XX] CM / [XX] IN
  HIPS [XX] CM / [XX] IN
  SHOULDER [XX] CM / [XX] IN
  SIZE [size label]
  No random numbers, no extra text, no logo, no watermark.

COLUMN 2: Full-length front view of the SAME model wearing the SAME outfit.
COLUMN 3: Full-length side profile view of the SAME model wearing the SAME outfit.
COLUMN 4: Full-length back view of the SAME model wearing the SAME outfit.

OVERALL CONSTRAINTS:
All four columns must show the IDENTICAL person. The SAME outfit appears in all
columns. Clean studio background consistent across all columns.
No text overlay on model photos, text only in the bottom-left data card area.
Professional product photography style.
```

**数据卡尺码生成规则：**

> 1X 使用固定 PLUS 18W 数据；其他尺码根据体型骨架和尺码生成合理数据。

**1X（固定值，不可更改）：**

```text
HEIGHT 167.75 CM / 66.0 IN
BUST 114 CM / 44.9 IN
WAIST 98 CM / 38.6 IN
HIPS 121 CM / 47.6 IN
SHOULDER 44 CM / 17.3 IN
SIZE 1X / PLUS 18W
```

**其他尺码生成方法：**

Step 1：根据体型骨架取身高区间：

| 体型骨架 | 身高区间 |
|---------|---------|
| slender-androgynous | 175-183cm |
| athletic-lean | 170-178cm |
| healthy-proportioned | 168-175cm |
| curvy-hourglass | 168-175cm |
| full-figured-plus | 168-178cm |
| soft-natural | 165-175cm |

Step 2：根据尺码查基础三围表：

**直码基础三围表（US 标准）：**

| 尺码 | Bust | Waist | Hips | Shoulder |
|------|------|-------|------|----------|
| XS (0/2) | 82-84 | 62-64 | 87-89 | 36-37 |
| S (4/6) | 86-89 | 66-69 | 90-94 | 38-39 |
| M (8/10) | 90-94 | 71-76 | 95-99 | 39-40 |
| L (12/14) | 96-101 | 79-84 | 101-106 | 40-42 |
| XL (16) | 106-109 | 90-93 | 112-115 | 42-43 |

**大码基础三围表：**

| 尺码 | Bust | Waist | Hips | Shoulder |
|------|------|-------|------|----------|
| 1X (18W) | 114（固定） | 98（固定） | 121（固定） | 44（固定） |
| 2X (20W) | 119-122 | 104-107 | 127-130 | 45-46 |
| 3X (22W) | 127-130 | 109-112 | 135-138 | 47-48 |
| 4X (24W) | 134-137 | 115-118 | 142-145 | 49-50 |

Step 3：根据 DNA 能量微调三围（在基础值上）：

| DNA 能量 | 修正方向 |
|---------|---------|
| editorial-cool | Bust -3~5cm, Waist -2~4cm, Hips -3~5cm（纤细） |
| commercial-warm | 不修正 |
| natural-relaxed | Waist +1~2cm（自然柔软） |
| glamour-magnetic | Bust +3~5cm, Waist -1~2cm, Hips +2~3cm（沙漏） |
| street-defiant | 不修正 |
| sophisticated-calm | Shoulder +1cm, Waist -1cm（利落） |
| athletic-lean | Shoulder +2~3cm, Waist -1~2cm（宽肩窄腰） |

Step 4：填入数据卡模板，四舍五入到整数 cm，inch 按 ÷2.54 换算保留一位小数。

**生成工具适配提示：**

| 工具类型 | 策略 |
|---------|------|
| 支持多面板布局 | 直接使用 Multi-panel 指令一次性生成 |
| 不支持多面板 | 分 5 次生成（头像+正面+侧面+背面+数据卡），用一致性锁定，后拼合 |
| Flux / SDXL 等 | 分次生成 + 后拼合；数据卡文字建议后期叠加 |

**分次生成顺序：**
```text
1. 先生成头像特写
2. 生成正面全身（加一致性锁定）
3. 生成侧面全身（加一致性锁定）
4. 生成背面全身（加一致性锁定）
5. 数据卡：纯白背景 + 黑色无衬线字体，或后期叠加
6. 全部生成后按布局拼合
```

---

## 换装不换脸秘诀

```text
Keep the exact same person, face, skin texture, expression, and pose, only change the outfit to: [新服装描述]
```

---

## 输出格式

### 用户要提示词时

1. **服装分析**：简要说明该服装的风格、面料、适用场景
2. **DNA 推导**：列出本次推导的 12 个 DNA 维度（体型/能量/造型/镜头/年龄/族裔 + 脸型/眼型/鼻型/唇型/眉型/颧骨下颌），说明推导理由
3. **模特基底**：列出本次选用的基底特质（5 项中选了哪 4-5 项）
4. **定制摘要**：列出用户定制参数（如有），方便核对
5. **完整提示词**：可直接使用的英文提示词，必须包含模特基底 + DNA(含物理特征) + Tier 1 真实肤质；如启用 Tier 2 个性特征则标注
6. **反向提示词**：基础负面词 + 轮换组
7. **数据卡（如适用）**：三视图/数据卡布局时附上尺码数据
8. **中文说明**：关键参数的中文解释，含物理特征矩阵和 Tier 1/Tier 2 选用情况

> **真实完美模式**：用户说"真实完美/完美/干净/不要瑕疵"时，跳过 Tier 2 个性特征和独有标识池，只写 Tier 1 真实肤质 + 模特基底 + 专业光影。用 `flawless real skin with natural texture` 而非 `flawless perfect skin`——"real" 锚定自然肌理，"flawless" 意为无可见瑕疵。
> **布局选项**：三视图可选纯三视图、左半身+右三视图、左头像+数据卡+右三视图。用户提到"数据卡/身高三围/measurement"时默认使用数据卡三视图布局。

### 用户要直接生成图片时

1. 直接调用图片生成工具，每张图使用独立 prompt。
2. 生成前不要把长 prompt 全量发给用户确认，除非用户要求。
3. 多张图生成完成后，用简短中文说明：完成数量、DNA 组合、尺码/族裔/场景/比例等关键约束。
4. 如果某张图明显服装跑样、假皮肤、脸像网红模板或不符合用户排除条件，优先重生该张。
