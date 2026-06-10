---
name: fashion-model-generator
description: 美国时尚女装模特 AI 生成专家。根据女装款式/风格匹配最适合的美国女性模特类型（年龄、体型、气质、面部特征），生成超写实、无塑料感、非网红脸的专业 AI 提示词。适用于跨境电商女装商品图、时尚 editorial、品牌 lookbook 等场景。
agent_created: true
---

# 美国时尚女装模特生成专家

## 触发场景

用户提到以下任一关键词时自动激活：
- "生成模特"、"AI模特"、"模特图"、"服装模特"
- "什么模特适合这件衣服"、"匹配模特"
- "帮我写模特提示词"
- 提供了服装描述并需要模特建议

## 工作流程

### Step 1: 理解服装 → 匹配模特类型

收到服装描述后，先分析服装的**风格、面料、版型、适用场景**，然后从下方模特类型库中推荐最匹配的 1-3 种模特类型。

### Step 2: 定制确认（条件触发）

**核心规则：用户不说话 = 走默认，说了话 = 融合进去。严禁在用户没提定制需求时主动询问。**

**判断逻辑：**
- 用户只发了图片/服装描述，没有任何定制说明 → **跳过确认，直接使用下方默认值进入 Step 3**
- 用户附带说了体型/族裔/年龄/场景等关键词 → **提取定制条件，与自动匹配的类型融合后进入 Step 3**
- 用户只说了部分条件（如"大码"但没说族裔）→ **已说的融合，未说的走默认，不追问**

**默认值（无定制时使用）：**
| 维度 | 默认值 |
|------|--------|
| 体型/类型 | 根据服装自动匹配的 10 种类型中首选 |
| 年龄 | 匹配类型定义的标准区间 |
| 族裔 | 不设限，由 AI 按服装风格自行选择（兜底倾向于 Caucasian/Latina 混搭） |
| 肤色 | 按类型默认（Commercial→medium, Athletic→tan, Romantic→fair 等） |
| 场景 | 工作室白底/灰底棚拍 |
| 相机 | 柔光 + 轻微胶片颗粒（通用底） |
| 发色 | 按类型默认匹配 |
| 布局 | 组合式商品图（左半身特写 + 右三视图横向并列） |

**定制简写规则**（用户一句话说完，Skill 自动解析）：

### Step 3: 生成提示词

按标准公式生成完整提示词。

---

## 美国女装模特类型库

> **美国模特覆盖**：以下 10 种类型均可覆盖 Caucasian（白人）、African American（非裔）、Latina（拉丁裔）、Asian American（亚裔）、Mixed/Ambiguous（混血）等美国主流族裔。生成提示词时根据服装风格和品牌定位选择合适的族裔标签叠加使用。

### 1. High Fashion / Editorial（高级时装 / 社论风）

| 维度 | 描述 |
|------|------|
| **年龄** | 30-38 岁 |
| **身高/体型** | 175-183cm，极度纤细瘦削，平胸，窄髋，四肢修长，骨架分明 |
| **面部特征** | 高颧骨突出，下颌线棱角分明，眼窝深陷，眉骨高耸，鼻梁细直，嘴唇偏薄，颧骨下方有自然阴影凹陷，五官有明显不对称感 |
| **气质/眼神** | 冷峻疏离、不取悦镜头、轻微厌世感、强势独立、知性克制——眼神不直视或微微俯视，嘴角无笑意 |
| **发质/发型** | 贴头皮 wet look / 极简低马尾 / 凌乱短寸 / slick back，发丝有自然毛躁感和碎发 |
| **适合服装** | 解构主义剪裁、oversized 西装、极简线条、高定礼服、实验性廓形、暗黑先锋、建筑感设计 |
| **不适合** | 甜美碎花、蕾丝花边、过度装饰、紧身性感、粉嫩色系 |

**提示词片段：**
```
tall and extremely slender American woman, androgynous silhouette, prominent high cheekbones, sharp angular jawline, deep-set eyes with slight hollow underneath, strong brow bone, narrow straight nose, thin lips, natural facial asymmetry, subtle early signs of maturity in expression, cool detached gaze slightly away from camera, no smile, editorial model presence, high fashion attitude
```

---

### 2. Commercial / Catalog（商业目录 / 大众时尚）

| 维度 | 描述 |
|------|------|
| **年龄** | 30-40 岁 |
| **身高/体型** | 168-175cm，健康匀称，有适度曲线但不夸张，肩颈线条优美，锁骨清晰 |
| **面部特征** | 五官端正但不完美对称，自然双眼皮或浅内双，鼻梁挺直但不过分高耸，嘴唇厚度适中自然色，牙齿轻微不齐更显真实，眼角有轻微自然细纹，面颊有自然血色 |
| **气质/眼神** | 亲切友好、松弛自然、自信但不强势、像你认识的漂亮姐姐——眼神温暖，嘴角有浅笑，有生活阅历的笃定 |
| **发质/发型** | 自然微卷长发 / 慵懒低马尾 / loose beach waves，头发有自然光泽但不过度精致 |
| **适合服装** | 日常通勤装、牛仔裤、针织衫、连衣裙、休闲西装、基本款、轻奢品牌、运动休闲 |
| **不适合** | 过于前卫的解构设计、极端性感暴露、暗黑风格 |

**提示词片段：**
```
healthy athletic-lean American build, subtle natural curves, visible collarbones, naturally proportioned face with slight asymmetry, faint natural fine lines at eye corners, almond-shaped eyes with natural crease, straight natural nose, medium-full lips in natural tone, warm genuine expression of lived confidence, relaxed knowing smile, effortless American elegance with life experience, approachable warmth
```

---

### 3. Curve / Plus-Size（大码曲线）

| 维度 | 描述 |
|------|------|
| **年龄** | 30-42 岁 |
| **身高/体型** | 168-178cm，丰满圆润，明显胸腰臀曲线，手臂和腿部有自然肉感但不松弛，背部线条饱满 |
| **面部特征** | 圆润脸型但不浮肿，有清晰下颌轮廓，双颊饱满，眼睛明亮有神，鼻梁适中，嘴唇丰满有型 |
| **气质/眼神** | 自信张扬、热烈性感、不遮掩身材、身体积极——眼神直接有力，笑容大方灿烂 |
| **发质/发型** | 丰盈大卷 / voluminous blowout / 自信的短发 / 高马尾，发量多且有动感 |
| **适合服装** | 裹身裙、弹力针织、高腰牛仔裤、V领设计、垂坠面料、印花长裙、bodysuit |
| **不适合** | 过于紧窄的剪裁、横向条纹图案、oversized 但无腰线设计 |

**提示词片段：**
```
full-figured and curvaceous American woman, clearly defined hourglass silhouette, natural fullness in arms and thighs, round face with defined jawline, bright confident eyes, full shapely lips, powerful body-positive presence, direct confident gaze, radiant genuine smile, celebrating her curves naturally, confidence that comes with maturity
```

---

### 4. Athletic / Fit（运动健美）

| 维度 | 描述 |
|------|------|
| **年龄** | 30-38 岁 |
| **身高/体型** | 170-178cm，肌肉线条清晰但不夸张，宽肩窄腰，明显的腹肌轮廓，紧实的手臂和腿部肌肉线条，低体脂 |
| **面部特征** | 骨骼结构清晰，颧骨和下颌线突出，肤色偏小麦色或日晒色，眼睛炯炯有神，鼻梁挺直 |
| **气质/眼神** | 能量充沛、自律坚韧、动态力量感——眼神专注有力，表情自信但不僵硬 |
| **发质/发型** | 高马尾 / 拳击辫 / 利落短发 / sleek ponytail，头发紧贴头皮显得干练 |
| **适合服装** | 运动内衣、leggings、骑行裤、运动套装、功能性外套、泳装、athleisure |
| **不适合** | 过于柔美飘逸的面料、繁复层叠设计、紧身晚礼服 |

**提示词片段：**
```
athletic toned American physique, defined shoulder and arm muscles, visible abdominal definition, strong lean legs, broad shoulders with narrow waist, sun-kissed skin tone, prominent bone structure, intense focused gaze, disciplined powerful presence, high ponytail tight to scalp, active lifestyle energy of a woman in her prime
```

---

### 5. Natural / Organic（自然有机 / 素颜感）

| 维度 | 描述 |
|------|------|
| **年龄** | 30-40 岁 |
| **身高/体型** | 165-175cm，自然未雕塑的体型，有适度柔软感，非健身房的天然身材 |
| **面部特征** | 明显的雀斑分布鼻梁和双颊，轻微肤色不均，眉毛未经精细修剪有自然杂毛，嘴唇可能有轻微干裂，眼角有自然浅纹，鼻梁旁有小痣 |
| **气质/眼神** | 松弛自在、未刻意摆拍、像旅途中被抓拍的瞬间——眼神温柔略飘忽，笑容不是标准微笑而是被什么逗到的浅笑 |
| **发质/发型** | 略有毛躁的自然卷 / 松散 messy bun / undone loose hair，碎发自然飘散，发际线有碎发可见 |
| **适合服装** | 棉麻天然面料、宽松衬衫、手工编织、素色亚麻裙、禅意风格、环保品牌、boho 风 |
| **不适合** | 亮片面料、紧身晚装、过于结构化剪裁、人造皮革 |

**提示词片段：**
```
natural unretouched American body, soft unfitness look, visible freckles across nose bridge and cheeks, subtle uneven skin tone, untrimmed natural eyebrows, slight lip dryness, fine lines at eye corners, tiny mole beside nose bridge, relaxed unposed presence, gentle unfocused gaze catching light, caught mid-laugh not studio smile, messy undone hair with natural flyaways, a woman comfortable in her own skin
```

---

### 6. Mature / Sophisticated（成熟优雅）

| 维度 | 描述 |
|------|------|
| **年龄** | 40-55 岁 |
| **身高/体型** | 168-175cm，保养极佳的身材有岁月痕迹，轻微颈纹和手部细纹反而是加分项，体态优雅挺拔 |
| **面部特征** | 清晰的法令纹但不夸张，眼角有自然的笑纹（鱼尾纹），皮肤有自然光泽但保留纹理，颈部有轻微横向纹路，眉骨和颧骨结构愈发分明 |
| **气质/眼神** | 岁月沉淀的从容优雅、知性智慧、不争不抢的笃定——眼神深邃温和，微笑克制而有内容 |
| **发质/发型** | 优雅的低发髻 / 银灰自然发色 / 精致波波头 / sleek bob，发丝有银丝光泽 |
| **适合服装** | 精裁西装、羊绒大衣、真丝衬衫、珍珠配饰、极简奢华、质感优先的设计师品牌 |
| **不适合** | 过于少女的蝴蝶结装饰、荧光色系、街头潮流、超短裙 |

**提示词片段：**
```
gracefully aging American woman, elegant posture, visible natural smile lines around eyes, subtle neck texture, skin with natural glow and visible texture, refined bone structure becoming more defined with age, deep knowing gaze, restrained sophisticated smile, silver-gray natural hair color in elegant low chignon, timeless American elegance, wisdom in expression
```

---

### 7. Street Style / Edgy（街头潮流 / 个性酷感）

| 维度 | 描述 |
|------|------|
| **年龄** | 30-38 岁 |
| **身高/体型** | 165-175cm，各种体型均可，重点是个性态度，可能有纹身、穿孔、染发 |
| **面部特征** | 可以有不完美的牙齿（小牙缝）、眉毛有刮痕或染浅、鼻环或耳骨钉、面部有不夸张的穿孔，眼妆偏烟熏或色彩试验 |
| **气质/眼神** | 叛逆不羁、酷但不刻意、有自己的态度不随主流——眼神略带挑衅或完全无视镜头 |
| **发质/发型** | 彩色染发 / 寸头 / 不对称剪裁 / 凌乱undercut / 脏辫，发质可以有受损的毛躁质感 |
| **适合服装** |  oversized 卫衣、工装裤、解构牛仔、金属配件、涂鸦印花、叠穿混搭、vintage |
| **不适合** | 甜美名媛风、蕾丝公主裙、商务正装、经典珍珠配饰 |

**提示词片段：**
```
slightly rebellious American attitude, not trying to please the camera, visible small tooth gap, partly shaved eyebrow with bleached tint, subtle nose ring, edgy smoky eye makeup, defiant or completely ignoring gaze, brightly dyed hair with grown-out roots visible, distressed texture in hair, urban American street presence, anti-mainstream coolness, mature edge without trying too hard
```

---

### 8. Romantic / Ethereal（浪漫空灵 / 仙气）

| 维度 | 描述 |
|------|------|
| **年龄** | 30-38 岁 |
| **身高/体型** | 167-175cm，纤细但不骨感，有柔和的女性曲线，肩部线条柔软，手腕脚踝纤细 |
| **面部特征** | 鹅蛋脸或心形脸，五官柔和无攻击性，眼神清澈，睫毛自然纤长不浓密，嘴唇有自然粉色调，皮肤白皙透亮但有纹理，眼角有极浅细纹添柔美 |
| **气质/眼神** | 温柔梦幻、飘渺不真实、像从古典油画中走出——眼神朦胧眺望远方，微启双唇，是不老的诗意感而非少女感 |
| **发质/发型** | 松散的大波浪长发 / 半扎公主头 / loose romantic waves，发丝柔软有微光 |
| **适合服装** | 蕾丝连衣裙、薄纱长裙、荷叶边、缎面吊带、花卉印花、褶皱设计、维多利亚风 |
| **不适合** | 硬朗西装、运动街头、暗黑风格、金属铆钉 |

**提示词片段：**
```
soft feminine American silhouette, slender without sharp angles, gentle shoulder line, delicate wrists and ankles, heart-shaped face with rounded contours, soft non-aggressive features, faint fine lines at corners adding gentle character, clear dreamy eyes gazing into distance, natural light lashes, slightly parted lips in natural rose tone, fair translucent skin with visible texture, loose romantic waves with soft sheen, timeless ethereal elegance, ageless rather than youthful
```

---

### 9. Sophisticated Minimalist（极简都市女性）

| 维度 | 描述 |
|------|------|
| **年龄** | 32-42 岁 |
| **身高/体型** | 168-175cm，身型利落挺拔，适度曲线不夸张，肩背线条干净，整体呈修长 H 型或轻微 X 型，体态自信舒展 |
| **面部特征** | 五官锐利但不具攻击性，颧骨和下颌角分明但线条偏柔和，鼻梁细直，眉形自然微挑，唇形清晰但不厚重，皮肤呈现健康的中性调或冷调，轻微法令纹增加质感 |
| **气质/眼神** | 知性冷静、不刻意讨好、有自己的节奏和判断力——眼神平静直视或略过镜头看向远方，唇角有极淡的笃定笑意 |
| **发质/发型** | 利落 lob 头 / sleek 低马尾 / 自然垂顺中长发 / clean blunt bob，发丝有健康光泽但非油亮，碎发自然不凌乱 |
| **适合服装** | 精裁西装马甲、直筒阔腿裤、极简衬衫裙、羊绒针织、建筑感半裙、同色系叠穿、质感优先的基本款 |
| **不适合** | 过度甜美碎花、夸张荷叶边、荧光色系、大面积亮片、低俗暴露 |

**提示词片段：**
```
clean sophisticated American silhouette, tall lean H-frame with subtle feminine curve, poised upright posture, defined cheekbones and jaw softened at edges, narrow straight nose, naturally arched brows, subtle nasolabial lines adding character, clear-shaped lips, healthy neutral-to-cool skin tone, calm knowing gaze that occasionally drifts past the camera, faint assured smile barely touching the lips, sleek lob or clean blunt bob with healthy natural sheen, effortless American urban confidence, quality over quantity aesthetic, the polish of a woman who knows herself
```

---

### 10. Glamour / Bombshell（魅力炸弹 / 好莱坞式性感）

| 维度 | 描述 |
|------|------|
| **年龄** | 30-42 岁 |
| **身高/体型** | 168-175cm，沙漏身材，丰满胸部，明显腰臀比，腿部线条饱满有弧度 |
| **面部特征** | 丰满嘴唇（自然色或正红），深邃大眼带轻微烟熏，高挑弯眉，心形脸，肤色偏暖或橄榄色 |
| **气质/眼神** | 经典好莱坞式魅力、自信性感但不低俗、成熟女性的强大吸引力——眼神半垂（sultry gaze），红唇微启 |
| **发质/发型** | 丰盈复古大波浪 / old Hollywood waves / 高蓬松 blowout，发丝有丝缎光泽 |
| **适合服装** | 裹身裙、深V晚装、缎面礼服、高开叉、紧身针织、皮草、红毯礼服 |
| **不适合** | 过于宽松无型、运动休闲、中性剪裁、暗黑风格 |

**提示词片段：**
```
hourglass American silhouette, full bust with defined waist-hip ratio, legs with natural curve and fullness, full shapely lips in classic red, deep-set large eyes with subtle smokey shadow, high arched brows, heart-shaped face, warm olive skin tone, sultry half-lidded gaze, slightly parted red lips, voluminous old Hollywood waves with silk sheen, classic American bombshell presence, the magnetism of a woman who owns her allure
```

---

## 提示词生成公式

严格按照以下顺序组织提示词：

```
[拍摄方式与镜头] + [构图与视角] + [模特年龄/美国族裔/体型特征] + [面部细节与皮肤质感] + [发型发质] + [表情与眼神] + [姿势与场景] + [服装描述] + [焦段+光圈+镜头类型+机身] + [光影与摄影风格] + [材质质感] + [情绪氛围] + [限制词与排除]
```

### 完整模板

```text
【拍摄方式】
[怼脸特写 / 半身中景 / 全身 / 3/4 身]，[正脸 / 侧脸 / 3/4 角度 / 微微侧身]

【构图与视角】
[画面比例：3:4 / 9:16 / 16:9]，[构图方式：非对称构图 / 三分法 / S型动线 / 大面积留白]，[视角：平视 / 仰拍 / 俯拍 / 低机位 / 高机位]

【模特主体】
[年龄] 岁美国女性模特，[族裔]，[体型描述]，[具体身形特征]

【面部细节】
[骨骼结构描述]，[五官特征]，五官有轻微不对称，避免标准化网红脸；
自然皮肤纹理，保留细微毛孔，[肤色描述]，脸颊有真实血色；
[辨识细节：雀斑 / 小痣 / 眼角浅纹 / 日晒痕迹等]

【发型发质】
[发型描述]，[发质细节]，[碎发/毛躁/自然光泽等质感]

【表情眼神】
[具体情绪状态]，[视线方向]，[微表情细节]，不做夸张表情，不取悦镜头

【姿势场景】
[人物姿势]，[场景描述]，[背景氛围]

【服装】
[完整服装描述，包括面料材质、剪裁、颜色、垂坠感、细节]

【光影摄影】
[摄影类型]，[焦段]，[光圈]，[镜头类型]，[相机机身]；
[光源方向和性质]；阴影保留面部结构，皮肤不过度磨皮，轻微胶片颗粒，真实镜头质感

【材质质感】
[背景材质]，[主体材质]，[关键元素材质]，[表面质感]

【情绪氛围】
[整体情绪：安静/自信/疏离/温暖/神秘]，[氛围关键词]

【限制词】
避免塑料感，避免过度美颜，避免过度磨皮，避免完美对称，
避免夸张大眼，避免尖下巴，避免网红妆容，避免棚拍写真感，
避免标准网红脸，避免塑料皮肤，避免 CG 渲染感

【清场】
[如需纯色背景或排除杂物，添加：纯色背景 / no equipment in the frame / 画面里没有多余杂物]
```

---

## 镜头与相机参数速查

> **核心原理**：AI 模型训练时学习了海量摄影作品的元数据（焦段、光圈、镜头类型）。在提示词中加入这些参数，本质是调用模型已学到的视觉关联记忆，让画面更接近真实摄影，减少随机 CG 感。

### 一、焦段 → 决定空间感与叙事重点

| 焦段 | 视觉效果 | 适合场景 | 提示词片段 |
|------|---------|---------|-----------|
| **35mm** | 人物与环境同时清晰，现场感强，纪实氛围 | 街头外景、环境叙事、生活抓拍 | `shot on 35mm lens, environmental portrait, subject and background both in context` |
| **50mm** | 接近人眼视角，自然平实，无夸张透视 | 日常通勤、居家、自然光半身 | `shot on 50mm lens, natural perspective, true-to-eye proportion` |
| **85mm** | 人物突出，背景柔和虚化，面部无畸变 | 棚拍半身特写、封面肖像、商品细节 | `shot on 85mm portrait lens, soft background bokeh, facial features compression-flattering` |
| **135mm** | 极致压缩感，远距离抓拍，背景彻底虚化 | 户外远摄、T 台抓拍、全身人像 | `shot on 135mm telephoto lens, compressed background, subject isolation` |
| **24-70mm** | 灵活变焦，棚拍万能焦段 | 工作室多角度商品图 | `shot on 24-70mm zoom lens, versatile studio range` |

### 二、光圈 → 决定景深与清晰范围

| 光圈 | 视觉效果 | 适合场景 | 提示词片段 |
|------|---------|---------|-----------|
| **f/1.4 - f/1.8** | 极浅景深，奶油虚化背景，梦幻光斑 | 半身特写、面部肖像、面料细节 | `wide open at f/1.4, shallow depth of field, creamy bokeh background, soft light circles` |
| **f/2.8 - f/4** | 中等景深，人物锐利 + 环境有层次 | 全身棚拍、街拍、环境人像 | `shot at f/4, moderate depth of field, subject sharp with layered background` |
| **f/8 - f/11** | 深景深，全身从头发丝到脚跟都清晰 | 全身三视图、多角度商品图、大场景 | `stopped down to f/8, deep depth of field, sharp from foreground to background` |

### 三、镜头类型 → 强化画面气质

| 镜头类型 | 视觉特征 | 适合场景 | 提示词片段 |
|---------|---------|---------|-----------|
| **标准人像镜** | 自然透视，肤色温润，焦外奶油 | 商业目录、日常通勤、优雅成熟 | `standard portrait prime lens, natural rendering, smooth skin tones` |
| **变形宽银幕** | 宽画幅、横向光晕、椭圆形虚化 | 电影感 editorial、雨夜、霓虹都市 | `anamorphic lens, wide cinematic frame, horizontal lens flare, oval bokeh` |
| **微距镜头** | 极近特写、纹理纤毫毕现 | 面料特写、纽扣拉链、刺绣细节 | `macro lens, extreme close-up, fine texture visible, shallow focus plane` |
| **鱼眼镜头** | 边缘扭曲、空间夸张、冲击力强 | 潮流街拍、音乐节、滑板运动装 | `fisheye lens, edge distortion, exaggerated perspective, dynamic energy` |

### 四、相机品牌 → 画面气质锚点

| 品牌 | 气质特征 | 适合场景 | 提示词片段 |
|------|---------|---------|-----------|
| **Hasselblad** | 中画幅细腻、商业肖像、极致细节 | 奢侈品、高定、editorial | `shot on Hasselblad, medium format, exquisite detail, luxury commercial aesthetic` |
| **Canon (EOS R5)** | 超写实锐利、白棚商业标准 | 通用电商棚拍 | `shot on Canon EOS R5, ultra-sharp, professional studio quality` |
| **Leica** | 街头人文、高对比暗角、德味质感 | 自然外景、boho、街拍 | `shot on Leica, street documentary style, high contrast, subtle vignette` |
| **Fuji (GFX/XT)** | 胶片怀旧色彩、肤色温润、故事感 | 生活感、成熟优雅、自然光 | `shot on Fujifilm, film-like color rendition, warm nostalgic tones` |
| **Sony (A7R IV)** | 色彩通透、轮廓锐利、现代数码感 | 运动装、athleisure、商业目录 | `shot on Sony A7R, crisp digital clarity, vibrant color separation` |

### 五、镜头与模特类型匹配表

| 模特类型 | 推荐焦段 | 推荐光圈 | 推荐镜头类型 | 推荐机身 |
|---------|---------|---------|------------|---------|
| High Fashion / Editorial | 85mm 或 135mm | f/2.8 - f/4 | 标准人像 / 变形宽银幕 | Hasselblad |
| Commercial / Catalog | 50mm 或 85mm | f/2.8 - f/5.6 | 标准人像 | Canon EOS R5 |
| Curve / Plus-Size | 50mm | f/2.8 - f/4 | 标准人像 | Canon EOS R5 |
| Athletic / Fit | 35mm 或 50mm | f/4 - f/8 | 标准 / 鱼眼 | Sony A7R |
| Natural / Organic | 35mm | f/2.8 - f/4 | 徕卡风格 | Leica |
| Mature / Sophisticated | 85mm | f/2.8 - f/4 | 标准人像 | Fuji GFX |
| Street Style / Edgy | 35mm 或鱼眼 | f/4 - f/8 | 鱼眼 / 变形宽银幕 | Leica / Sony |
| Romantic / Ethereal | 85mm | f/1.4 - f/2 | 标准人像(大光圈) | Fuji GFX |
| Sophisticated Minimalist | 50mm 或 85mm | f/4 - f/8 | 标准人像 | Hasselblad |
| Glamour / Bombshell | 85mm | f/2.8 | 变形宽银幕 | Hasselblad / Canon |

> **组合原则**：**一个焦段 + 一个光圈 + 一个镜头类型 + 一个机身品牌**就足够引导风格，无需堆砌。镜头参数是引导工具，不能替代构图、光影、人物设定等核心描述。

---

## 面料材质词库（服装部分专用）

### 天然面料
| 面料 | 气质 | 提示词关键词 |
|------|------|------------|
| 真丝/丝绸 | 流动、精致、柔软 | `liquid-like silk drape, elongated satin highlights along folds, subtle weave texture visible` |
| 亚麻 | 自然、松弛、手工 | `matte linen surface, visible coarse fibers, natural crease lines, relaxed unstructured drape` |
| 纯棉 | 日常、干净、温和 | `soft cotton with slight weave pattern, natural matte finish, soft wrinkles at joints` |
| 羊毛/羊绒 | 温暖、高级、厚重 | `dense wool texture, soft brushed surface, substantial structured drape, subtle fiber halo` |
| 薄纱/纱 | 轻盈、朦胧、梦幻 | `sheer diaphanous chiffon, translucent edge softening, layered ethereal lightness` |
| 蕾丝 | 精致、女性化、通透 | `delicate floral lace pattern, semi-transparent with skin showing through, intricate thread detail` |

### 合成/特殊面料
| 面料 | 气质 | 提示词关键词 |
|------|------|------------|
| 皮革 | 酷感、硬朗 | `genuine matte leather with natural grain, subtle creasing at joints, not shiny patent` |
| 缎面 | 光泽、奢华 | `satin with directional sheen, smooth surface catching light, deep rich color saturation` |
| 针织 | 舒适、弹性、日常感 | `fine-gauge knit with visible stitch texture, body-skimming fit, soft ribbing detail` |
| 牛仔 | 休闲、挺括 | `rigid raw denim with visible twill line, natural fading at stress points, structured hold` |
| 天鹅绒 | 复古、华丽 | `crushed velvet with directional pile, deep light-absorbing texture, subtle color shift` |

### 背景材质（新增）
| 材质 | 气质 | 提示词关键词 |
|------|------|------------|
| 宣纸肌理 | 东方、文化、安静 | `rice paper texture, warm ivory white, visible fine fibers, subtle ink wash edges` |
| 水彩纸 | 治愈、温柔、手绘 | `watercolor paper texture, slight grain, natural pigment settling, soft edges` |
| 旧纸质感 | 复古、年代感、记忆 | `aged paper texture, slight yellowing, natural edge wear, subtle patina` |
| 细腻纸纤维 | 高级、编辑感、极简 | `fine paper fibers, clean white, subtle texture, editorial quality` |

---

## 服装 → 模特匹配速查表

| 服装特征 | 首选模特类型 | 备选 |
|---------|------------|------|
| 极简解构 / 建筑感 / oversized 西装 | High Fashion / Editorial | Sophisticated Minimalist |
| 日常通勤 / 基本款 / 针织衫 | Commercial / Catalog | Natural / Organic |
| 裹身裙 / V领 / 弹力针织 / 大码 | Curve / Plus-Size | Glamour / Bombshell |
| 运动装 / athleisure / 泳装 | Athletic / Fit | Commercial / Catalog |
| 棉麻 / boho / 宽松 / 手工感 | Natural / Organic | Romantic / Ethereal |
| 精裁西装 / 羊绒 / 真丝衬衫 / 极简奢华 | Mature / Sophisticated | Commercial / Catalog |
| 涂鸦 / oversized 卫衣 / 工装 / 解构牛仔 | Street Style / Edgy | Sophisticated Minimalist |
| 蕾丝 / 薄纱 / 花卉印花 / 褶皱 | Romantic / Ethereal | Natural / Organic |
| 精裁马甲 / 阔腿裤 / 极简衬衫裙 / 质感基本款 | Sophisticated Minimalist | Commercial / Catalog |
| 深V晚装 / 缎面礼服 / 红毯款 | Glamour / Bombshell | Curve / Plus-Size |

---

## 避免塑料感的核心原则

1. **不要写 "beautiful / attractive / pretty / 完美"** → 改用具体面部特征描述
2. **不要写 "8K / 超清 / flawless / perfect skin"** → 改用皮肤纹理、毛孔、血色
3. **不要写 "Shot on iPhone" / "Selfie"** → 改用 `amateur photo camera style` / `low-fidelity photo`
4. **不要写 "长发" → 写 "深色大波浪长发,发尾有轻微分叉和毛躁"**
5. **不要写 "微笑" → 写 "嘴角有极浅的笑意,像刚想到什么好笑的事"**
6. **始终加入不对称细节**：五官轻微不对称、眉毛不完全对称、一颗小痣、几颗雀斑
7. **始终加入视线描述**：不直视镜头、看向画面外侧、微微俯视
8. **始终加入限制词**：避免塑料感、避免过度美颜、避免网红脸、避免CG渲染感

### 🔑 生理级「不完美」增强技巧（ai模特.pdf 融合）

真实感来自「不完美」而非「完美」。以下细节能直接打破 CG 感：

| 部位 | 关键词 | 效果 |
|------|--------|------|
| 皮肤 | `visible pores on nose and cheeks` | 可见毛孔，打破磨皮质感 |
| 嘴唇 | `slightly chapped lips, natural lip texture` | 嘴唇微干裂，真实生活感 |
| 雀斑 | `light scattered freckles across nose bridge` | 散落雀斑，非均匀分布 |
| 眉毛 | `natural untrimmed brows, slight stray hairs` | 未经精修的眉毛有杂毛 |
| 眼下 | `subtle under-eye darkness, not concealed` | 轻微黑眼圈未被遮瑕 |
| 发际线 | `visible baby hairs along hairline` | 发际线碎发可见 |
| 颈纹 | `subtle natural neck lines` | 自然颈纹可见 |

### 🎞️ 胶片质感替代方案

降低 CG 感的两种胶片路径：
- **日常真实感**：`amateur photo camera style` / `low-fidelity photo` — 模拟手机/业余相机随拍
- **高级电影感**：`shot on Kodak Portra 400` / `analog film grain` — 柯达胶片色调，暖调怀旧但保留细节

---

## 电商女装商品图拍摄规范

### 标准多角度模板

跨境电商女装商品图通常需要以下角度组合：

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
surface quality and material detail. Natural lighting to reveal true color.

【细节特写】
Close-up detail shot of [纽扣/拉链/刺绣/领口/袖口], 
showing craftsmanship and finish quality.
```

### 模特数据卡模板

生成商品图时可附加模特身材参考：

```text
Model measurements reference:
Height: [165-178cm], Bust: [cm], Waist: [cm], Hip: [cm],
Shoulder width: [cm], Wearing size: [S/M/L],
Garment on model — Length: [cm], Sleeve: [cm], Chest: [cm]
```

### 多角度一致性锁定

多角度组图需要保持同一个人，每张图的提示词开头统一加：

```
Same model as previous shot, identical person with same face, 
same skin texture, same hair, same makeup. Only change the camera angle to: [新角度]
```

### 🖼️ 组合式商品图布局（左半身特写 + 右三视图横向并列）

这是最常见的电商女装详情页布局格式：**左侧人物半身胸像特写（较大），右侧三张全身图从左到右横向排列（正面 → 侧面 → 背面）**，一张图完整呈现服装细节与多角度结构。

**布局结构图解：**
```
┌──────────────────────────┬─────────┬─────────┬─────────┐
│                          │         │         │         │
│   人物半身胸像特写        │ 正面    │ 侧面    │ 背面    │
│   (面部+领口+肩部细节)    │ 全身    │ 全身    │ 全身    │
│                          │         │         │         │
│      占 1/2 宽度          │   1/6   │   1/6   │   1/6   │
└──────────────────────────┴─────────┴─────────┴─────────┘
```

**布局规则（AI 生图工具通用指令）：**

```text
Multi-panel fashion product composite in a horizontal grid layout, 
16:9 wide aspect ratio.

LEFT PANEL (occupying 1/2 of total width):
Waist-up portrait / bust shot of [模特描述], wearing [服装描述].
Close-up showing facial details, neckline, shoulder fit, collarbone,
and upper chest fabric drape. Large scale, detailed focus on face 
and upper garment. Natural expression, soft diffused lighting, 
clean studio background.

RIGHT PANELS (occupying 1/2 of total width, divided into 3 equal columns):

COLUMN 1 (leftmost of right side):
Full-length front view of the SAME model wearing the SAME outfit. 
Standing naturally facing camera, arms relaxed at sides, 
showing complete garment from neck to hem.

COLUMN 2 (center of right side):
Full-length side profile view of the SAME model wearing the SAME outfit. 
Standing at 90-degree angle to camera, showing side silhouette, 
body fit, and garment drape from side angle.

COLUMN 3 (rightmost of right side):
Full-length back view of the SAME model wearing the SAME outfit. 
Standing naturally with back to camera, showing back design, 
collar back, closure details, and fabric fall from behind.

OVERALL CONSTRAINTS:
All four panels must show the IDENTICAL person — same face, same skin texture, 
same hair, same makeup, same body proportions. Shot on 50mm lens at f/5.6, 
standard portrait prime, Canon EOS R5. Clean light gray or white studio 
background consistent across all panels. Soft diffused lighting from same direction. 
Slight analog film grain. No text overlay, no distracting elements, 
professional product photography style.
```

**生成工具适配提示：**

不同 AI 生图工具对多面板布局的支持程度不同，可按以下策略选择：

| 工具类型 | 策略 |
|---------|------|
| 支持多面板布局的模型 | 直接使用上述 `Multi-panel` 指令一次性生成 |
| 不支持多面板的模型 | 分 4 次生成（半身 + 正面 + 侧面 + 背面），用一致性锁定指令确保是同一人，然后拼合 |
| Flux / SDXL 等 | 推荐分次生成 + 后拼合，每张图开头加一致性锁定 |

**分次生成顺序（推荐）：**

```text
1. 先生成全图最重要的那张（通常是正面全身或半身特写）
2. 后续每张图的开头加：
   Same model as the first shot — identical face, identical skin texture, 
   identical hair, identical makeup, identical body. 
   Same outfit. Only change: camera now shooting from [新角度]
3. 全部生成后拼合为组合布局
```

---

## 换装不换脸秘诀

当已有满意模特需换服装时，提示词开头第一句写：

```
Keep the exact same person, face, skin texture, expression, and pose, only change the outfit to: [新服装描述]
```

---

## 输出格式

每次为用户生成模特提示词时，按以下结构输出：

1. **服装分析**：简要说明该服装的风格、面料、适用场景
2. **模特匹配建议**：推荐 1-3 种最匹配的模特类型，说明理由
3. **定制摘要**：列出本次采用的定制参数（体型/年龄/族裔/肤色/场景等），方便核对
4. **完整提示词**：生成可直接使用的英文提示词（AI 生图工具通用）
5. **中文说明**：关键参数的中文解释