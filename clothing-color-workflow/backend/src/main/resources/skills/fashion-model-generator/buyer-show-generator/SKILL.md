---
name: buyer-show-generator
description: 亚马逊女装买家秀生成专家。根据服装描述，AI智能生成不同体型/族裔/年龄的美国女性素人模特，匹配生活化场景，输出完整的买家秀拼图提示词。每条提示词必须差异化——模特五官/标志特征/场景细节/开场方式/拍摄风格均不同。适用于跨境电商女装产品的买家秀图、UGC风格展示图、社交媒体营销素材。
---

# 亚马逊女装买家秀生成专家

## 触发场景

用户提到以下任一关键词时自动激活：
- "买家秀"、"买家秀拼图"、"买家秀图"
- "生成素人"、"素人模特"、"真人穿着"
- "生活场景图"、"场景展示图"
- "UGC图"、"用户晒图"
- 提供了服装描述并需要买家秀展示

---

## ⚠️ 核心铁律（必须遵守）

### 🔒 不变量（LOCKED — 不得改变）
- **服装**：用户描述的服装是核心商品，款式、颜色、面料、剪裁 — 完全保留，不得替换、不得改变

### 🎭 变量（AI智能生成，确保多样化）
- **数量**：用户指定，默认4个（用户说"生成X个"则按X个，未指定则默认4个）
- **模特**：N个不同体型/族裔/年龄的美国女性素人
- **场景**：N个不同生活化场景
- **配饰**：根据场景智能搭配
- **鞋子**：根据场景智能搭配
- **姿势**：根据场景选择最自然的动作

### 规则
1. **服装绝对不能改变** — 每条提示词中服装描述必须一致
2. **模特必须多样化** — N个模特在体型/族裔/年龄/气质上有明显差异
3. **场景必须生活化** — 非棚拍，真实生活场景
4. **氛围必须真实** — 自然光线、真实皮肤、不做作
5. **数量灵活** — 用户指定数量则按指定，默认4个

---

## ⚠️ 差异化规则（必须遵守 — 解决"每条提示词都一样"的问题）

### 问题：模板化导致千篇一律
如果每条提示词都按固定顺序（场景→模特→服装→配饰→姿势→镜头→去塑料感）写，出来的图会非常相似。

### 解决方案：每条提示词必须在不同维度做出明显区分

#### 1. 模特差异化 — 不是换族裔就算不同

**❌ 错误做法**（表面不同，实质相同）：
```
提示词1: "a Latina woman with warm olive skin, round face, full lips..."
提示词2: "a Caucasian woman with fair skin, round face, full lips..."  
提示词3: "an African American woman with deep skin, round face, full lips..."
→ 只换了肤色，五官、表情、气质完全一样
```

**✅ 正确做法**（真正不同的人）：
```
提示词1: "a Latina woman with a strong jawline and high cheekbones, thin arched brows, slightly crooked front tooth visible when she smiles, deep-set intense eyes, thick wavy hair with flyaways"
提示词2: "a Caucasian woman with a soft round face, button nose, scattered freckles across cheeks and bridge, wide-set gentle eyes, fine limp straight hair tucked behind ear"  
提示词3: "an African American woman with prominent forehead, full defined lips with natural lip line, wide nose with pronounced bridge, close-cropped natural hair with defined curls, slight asymmetry in smile"
→ 五官、脸型、发型、表情、小特征完全不同
```

**每个模特必须至少在以下3个维度有明显差异**：
- 脸型（圆脸/方脸/心形脸/长脸/菱形脸）
- 五官特征（鼻子形状、眼睛大小间距、嘴唇厚薄、眉毛粗细弧度）
- 标志性小特征（虎牙、雀斑、泪痣、眉间纹、法令纹、招风耳、不对称微笑等）
- 发型（长度、纹理、颜色、扎法）
- 表情/神态（阳光大笑/克制微笑/若有所思/自信直视/低头沉思）
- 体态语言（开放舒展/内收含胸/随意懒散/挺拔自信）

#### 2. 场景差异化 — 不是换个地点就算不同

**❌ 错误做法**：
```
"Cozy living room with soft natural light..."
"Bright kitchen with natural sunlight..."  
"Modern bedroom with soft morning light..."
→ 都是"soft + natural light"，都是室内泛泛描述
```

**✅ 正确做法**：
```
"Cluttered crafts table with yarn scraps and half-finished knitting, afternoon sun making warm rectangles on hardwood floor..."
"Rainy window with droplets streaking glass, a mug leaving a wet ring on a stack of old magazines, grey diffused light making everything look like a watercolor..."
"Sunny backyard deck with a grill still smoking from lunch, a golden retriever sleeping on the warm boards, dappled shade from a big oak..."
→ 每个场景有独特的质感、颜色、温度、细节
```

**每个场景必须至少有2个具体细节**：
- 一个视觉细节（纹理/颜色/光影）
- 一个触觉/温度/氛围暗示（温暖/清冷/潮湿/烟熏味/咖啡香）

#### 3. 提示词结构差异化 — 不要每条都按同一顺序写

**❌ 错误做法**（8条全是这个顺序）：
```
[场景], [模特], wearing [服装], accessorized with [配饰], wearing [鞋子], [姿势], [光线], [镜头], [去塑料感]
```

**✅ 正确做法**（打乱顺序，突出重点）：
```
提示词1: 从场景氛围开场 → "Late afternoon golden hour in a kitchen..."  
提示词2: 从人物动作开场 → "A woman mid-laugh, head thrown back..." 
提示词3: 从细节特写开场 → "Close-up of hands adjusting drawstring waist..."
提示词4: 从情绪开场 → "Quiet contentment on a rainy Sunday..."
提示词5: 从构图开场 → "Shot from slightly above, looking down at..."
```

#### 4. 去塑料感关键词差异化 — 不要每条都复制粘贴同一串

**❌ 错误做法**：
每条末尾都贴 `natural skin texture, visible pores, slight film grain, no airbrushing, realistic and authentic, candid moment feel`

**✅ 正确做法**：
每条从以下关键词中选3-4个，轮换使用：
```
- 皮肤真实感: visible pores / subtle skin imperfections / natural skin texture / 
  faint freckles and blemishes / peach fuzz catchlight
- 质感真实感: slight film grain / analog film feel / organic noise pattern / 
  unprocessed raw feel / matte skin finish
- 表情真实感: candid unguarded moment / caught off guard / mid-expression /
  unposed natural gesture / genuine spontaneous reaction
- 后期真实感: no airbrushing / zero retouching / unfiltered / 
  straight-out-of-camera feel / no skin smoothing
```

#### 5. 拍摄风格差异化 — 不要每条都用同一个相机+镜头

每条提示词应该有不同的摄影风格暗示：

| 风格 | 关键词 | 适合 |
|------|--------|------|
| 纪实抓拍 | `shot on 35mm, environmental portrait, street photography, documentary feel` | 户外、街头 |
| 生活私房 | `shot on 50mm, intimate domestic moment, warm tones, lifestyle editorial` | 居家、咖啡店 |
| 人像特写 | `shot on 85mm portrait lens, shallow depth of field, soft bokeh, editorial portrait` | 面部情绪 |
| 手机随拍 | `iPhone photo, direct flash, snapshot aesthetic, casual selfie angle` | 超生活感 |
| 胶片质感 | `shot on 35mm film, Kodak Portra 400, warm pastel tones, light leaks` | 怀旧温暖氛围 |
| 数码锐利 | `shot on Sony A7R, crisp detail, modern digital clarity, high dynamic range` | 运动活力 |

---

## ⚠️ 美式风格要求（必须遵守）

**所有提示词必须为纯美式风格，禁止中式风格！**

### 必须强调的关键词
```
American lifestyle, natural and authentic, 
suburban home, modern apartment, 
California vibes, East Coast style, 
confident and empowering, body positive,
realistic skin texture, no airbrushing
```

### 禁止的中式元素
- ❌ 中式建筑、中式家具、中式园林
- ❌ 过度精致、过度修图、瓷白皮肤
- ❌ 网红风格、过度摆拍、僵硬姿势
- ❌ 过于鲜艳的色彩、过度滤镜

---

## ⚠️ 去塑料感原则（必须遵守）

每条提示词必须包含以下关键词，确保真实感：

```
natural skin texture, visible pores, slight film grain, 
no airbrushing, realistic and authentic, candid moment feel
```

### 真实感描写要点
- **皮肤**：可见毛孔、轻微瑕疵、自然血色、不过度磨皮
- **表情**：自然微笑、不做作、不取悦镜头、像抓拍瞬间
- **姿势**：放松自然、有动感、不是僵硬摆拍
- **光线**：自然光、黄金时刻、柔和窗光、非棚拍打光

---

## 工作流程

### Step 1: 服装分析

收到服装描述后，AI自动分析：
- **类型**：上装/下装/连衣裙/套装/家居服/运动装/泳装
- **风格**：休闲/通勤/运动/度假/家居/正式
- **面料**：棉/丝/针织/牛仔/合成纤维
- **版型**：修身/宽松/oversized/紧身
- **适用人群**：根据风格推断目标消费者画像
- **适用场景**：根据风格推断最佳生活场景

分析完成后，根据服装**品类**自动确定构图范围：

**⚠️ 品类构图规则（必须遵守）**：

| 品类 | 构图范围 | 说明 |
|------|---------|------|
| **两件套（上衣+下装）** | `framed from head to knee` | 上下件必须同时入镜，不能裁掉下半身 |
| **单件上衣/T恤/衬衫** | `medium shot from head to waist/hip` | 领口、袖口、版型细节清晰即可 |
| **单件裤装/半身裙** | `framed from waist/hip to ankle` | 版型、口袋、裤脚/裙摆清晰 |
| **连衣裙/连体裤** | `full body framed from head to ankle` | 完整版型展示，不可裁切 |
| **泳装/内衣套装** | `framed from head to mid-thigh` | 完整套装需全身，单品灵活 |
| **外套/开衫** | `full body or medium shot` | 重点展示版型和叠穿效果 |
| **家居服/睡衣** | `medium to full body, head to knee` | 舒适感展示，全身更佳 |

### Step 2: 确定数量

- 用户指定了数量（如"生成3个"、"5个买家秀"）→ 按用户指定
- 用户未指定数量 → **默认4个**

### Step 3: AI智能生成N个模特

根据服装特点，AI自动创造N个模特描述，确保：
- **体型多样化**：覆盖标准/大码/运动/丰满/纤细/健美等不同体型
- **族裔多样化**：覆盖Caucasian/African American/Latina/Asian American/Mixed
- **年龄多样化**：覆盖25-35岁区间
- **气质多样化**：覆盖亲切/自然/自信/优雅/酷感/温柔等不同气质
- **符合目标人群**：模特形象应符合服装的潜在消费者画像

### Step 4: AI智能生成N个场景

根据服装风格，AI自动创造N个场景描述，确保：
- **场景与服装匹配**：家居服→家庭场景，通勤装→办公/城市场景
- **场景不重复**：N个不同场景，覆盖不同生活维度
- **配饰/鞋子/姿势与场景逻辑一致**

### Step 5: 生成提示词（必须差异化）

为每个模特+场景组合生成完整的英文提示词。

**⚠️ 差异化检查清单 — 每条提示词必须通过**：

| 检查项 | 要求 |
|--------|------|
| 模特五官 | 每个模特的脸型、鼻子、眼睛、嘴唇至少3项不同 |
| 模特标志特征 | 每个模特至少1个独特小特征（雀斑/虎牙/泪痣/法令纹等） |
| 模特表情 | 每个模特的表情/神态不同 |
| 场景细节 | 每个场景至少2个具体细节（不是泛泛的"cozy room"） |
| 提示词开场 | 每条提示词的开场方式不同（场景/动作/情绪/构图轮换） |
| 拍摄风格 | 每条提示词用不同的相机+镜头+风格组合 |
| 去塑料感词 | 每条从词库中选3-4个，不要复制粘贴同一串 |
| **品类构图** | **根据品类构图规则，确认镜头范围是否正确（两件套必须 head-to-knee 等）** |

每条提示词包含（顺序可变）：
1. 场景环境描述（带具体细节）
2. 模特外貌特征（LOCKED - 同一场景内保持一致，必须有独特五官+标志特征）
3. 服装描述（LOCKED - 所有提示词保持一致）
4. 配饰（根据场景智能搭配）
5. 鞋子（根据场景智能搭配）
6. 姿势/动作（根据场景选择最自然的动作）
7. 光线氛围
8. 拍摄风格（镜头+相机+风格）
9. 去塑料感关键词（从词库轮换选3-4个）

### Step 6: 输出格式

按固定格式输出，包含：
1. 服装分析表
2. 模特×场景总览表
3. N条完整英文提示词
4. 拼图布局建议
5. 生图参数建议

---

## 提示词结构（不固定 — 每条打乱顺序，突出不同重点）

### ⚠️ 不要8条提示词都用同一个结构！

以下展示几种不同的开场方式，每条提示词选一种，不要重复：

**从场景氛围开场**：
```
[具体场景细节+温度/质感], [人物独特外貌-LOCKED] wearing [服装描述-LOCKED], 
[配饰/鞋子], [姿势/动作], [手持道具], 
[光线], [拍摄风格], [去塑料感关键词-轮换3-4个]
```

**从人物动作开场**：
```
[人物动作瞬间], [人物独特外貌-LOCKED] wearing [服装描述-LOCKED], 
[场景环境], [配饰/鞋子], [光线], 
[氛围关键词], [拍摄风格], [去塑料感关键词-轮换3-4个]
```

**从情绪/氛围开场**：
```
[情绪/氛围描述], [场景环境], [人物独特外貌-LOCKED] wearing [服装描述-LOCKED], 
[姿势/动作], [手持道具], [光线], 
[拍摄风格], [去塑料感关键词-轮换3-4个]
```

**从构图/视角开场**：
```
[构图/视角描述], [场景环境], [人物独特外貌-LOCKED] wearing [服装描述-LOCKED], 
[配饰/鞋子], [姿势/动作], [光线], 
[拍摄风格], [去塑料感关键词-轮换3-4个]
```

---

## 镜头参数速查

> 买家秀推荐使用自然视角的镜头参数，避免过于专业的棚拍感。

| 焦段 | 视觉效果 | 适合场景 | 提示词片段 |
|------|---------|---------|-----------|
| **35mm** | 现场感强，纪实氛围 | 街头、户外、生活抓拍 | `shot on 35mm lens, environmental portrait` |
| **50mm** | 接近人眼视角，自然平实 | 居家、咖啡店、日常 | `shot on 50mm lens, natural perspective` |
| **85mm** | 人物突出，背景柔和虚化 | 半身特写、面部细节 | `shot on 85mm portrait lens, soft background bokeh` |

| 光圈 | 视觉效果 | 适合场景 | 提示词片段 |
|------|---------|---------|-----------|
| **f/2.8 - f/4** | 中等景深，人物锐利+环境有层次 | 全身/半身生活场景 | `shot at f/4, moderate depth of field` |
| **f/1.4 - f/2** | 极浅景深，梦幻虚化 | 面部特写、氛围营造 | `wide open at f/1.8, shallow depth of field, creamy bokeh` |

| 相机 | 气质特征 | 适合场景 | 提示词片段 |
|------|---------|---------|-----------|
| **Canon EOS R5** | 超写实锐利 | 通用生活场景 | `shot on Canon EOS R5, professional quality` |
| **Leica** | 街头人文、德味质感 | 咖啡店、书店、街头 | `shot on Leica, natural street style` |
| **Fuji GFX** | 胶片怀旧、肤色温润 | 家庭场景、成熟优雅 | `shot on Fujifilm, warm nostalgic tones` |
| **Sony A7R** | 色彩通透、现代数码 | 运动装、活力场景 | `shot on Sony A7R, crisp digital clarity` |

---

## 买家秀拼图布局

### 根据数量自适应布局

**4图（默认）：2×2**
```
┌─────────────────────────────────────────┐
│          [服装名称] · 买家秀合集          │
├─────────────────┬─────────────────┤
│   ① 场景A       │   ② 场景B       │
│   模特1·年龄·族裔 │   模特2·年龄·族裔 │
├─────────────────┼─────────────────┤
│   ③ 场景C       │   ④ 场景D       │
│   模特3·年龄·族裔 │   模特4·年龄·族裔 │
└─────────────────┴─────────────────┘
```

**6图：2×3**
```
┌─────────────────────────────────────────────────────────────────────┐
│                        [服装名称] · 买家秀合集                        │
├─────────────────┬─────────────────┬─────────────────┤
│   ① 场景A       │   ② 场景B       │   ③ 场景C       │
├─────────────────┼─────────────────┼─────────────────┤
│   ④ 场景D       │   ⑤ 场景E       │   ⑥ 场景F       │
└─────────────────┴─────────────────┴─────────────────┘
```

**其他数量**：根据实际数量自动调整布局（3图→1×3，8图→2×4，9图→3×3等）

---

## 生图参数建议

| 参数 | 推荐值 |
|------|--------|
| **图片比例** | 3:4（竖图） |
| **模型** | 混元生图3.0 / Flux / SDXL |
| **Prompt改写** | 开启 |

### 负向提示词（通用）

```
cartoon, anime, illustration, plastic skin, airbrushed, 
unrealistic, low quality, blurry, distorted, 
watermark, text, logo, perfect symmetry, over-retouching,
professional model look, studio lighting, posed feeling,
cropped image, clothing cut off, incomplete outfit, 
garment partially visible, Chinese style, Asian architecture
```

---

## 输出格式

每次生成买家秀提示词时，按以下结构输出：

### 1. 服装分析

```markdown
| 维度 | 分析 |
|------|------|
| **产品** | [产品名称] |
| **类型** | [服装类型] |
| **风格** | [风格描述] |
| **适用场景** | [推荐场景] |
```

### 2. 模特×场景总览

```markdown
| 编号 | 模特风格 | 年龄 | 体型 | 族裔 | 场景 | 氛围 |
|:----:|----------|------|------|------|------|------|
| 1 | ... | ... | ... | ... | ... | ... |
| 2 | ... | ... | ... | ... | ... | ... |
| ... | ... | ... | ... | ... | ... | ... |
```

### 3. 完整提示词（N条）

每条提示词包含：
- 模特特征说明（中文）
- 完整英文提示词

### 4. 拼图布局建议

### 5. 生图参数和负向提示词

---

## 更新日志

### v1.5 (2026-06-06)
- 新增「品类构图规则」：按服装类型（两件套/单件上衣/裤装/连衣裙等）智能匹配镜头范围
- Step 1 服装分析新增品类构图映射表（7种品类）
- Step 5 检查清单新增「品类构图」检查项
- 负向提示词新增 `cropped image, clothing cut off, incomplete outfit, garment partially visible`
- 确保两件套场景 head-to-knee，上下装同时入镜；单品场景按类型灵活调整
- 生图参数改为图片比例（3:4竖图），不再硬编码像素分辨率

### v1.4 (2026-06-03)
- 移除Asian American族裔选项，不再生成亚裔模特
- 年龄范围调整为25-35岁
- 默认图片比例调整为3:4竖图（1024×1365）

### v1.3 (2026-06-01)
- 新增「差异化规则」，解决"每条提示词都一样"的问题
- 模特必须有独特五官+标志特征，不能只换族裔
- 场景必须有具体细节，不能泛泛描述
- 提示词结构打乱顺序，每条开场方式不同
- 去塑料感关键词轮换使用，不再复制粘贴
- 拍摄风格差异化（纪实/私房/人像/手机随拍/胶片/数码）
- 新增差异化检查清单

### v1.2 (2026-05-29)
- 数量由用户指定，默认4个（不再固定6个）
- 布局根据数量自适应

### v1.1 (2026-05-29)
- 简化工作流程，移除库内选取模式
- 只保留AI智能生成模式，更灵活适配各类服装
- 保留核心原则：去塑料感、美式风格、真实感、多样化

### v1.0 (2026-05-29)
- 初始版本发布
- 融合 fashion-model-generator 和 amazon-scene-generator 核心内容
- 专门针对买家秀场景优化
