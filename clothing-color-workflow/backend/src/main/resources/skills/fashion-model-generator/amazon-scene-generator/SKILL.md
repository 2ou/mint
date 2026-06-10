---
name: amazon-scene-generator
description: 亚马逊女装场景图生成技能。当用户需要为女装产品生成场景图、背景替换、或创建生活方式展示图时使用此技能。支持指定场景或AI智能匹配，保持人物一致性。
---

# 亚马逊女装场景图生成技能

## ⚠️ 核心生成铁律（必须遵守）

### 🔒 不变量（LOCKED — 不得改变）
- **模特人物**：面部特征、发型、肤色、体型 — 与原图保持一致，不得改动
- **服装**：用户上传的女装是核心商品，款式、颜色、面料、剪裁 — 完全保留，不得替换、不得改变

### ⚠️ 品类构图规则（必须遵守）

根据服装品类自动确定镜头范围，确保产品完整展示：

| 品类 | 构图范围 | 说明 |
|------|---------|------|
| **两件套（上衣+下装）** | `framed from head to knee` | 上下件必须同时入镜，不能裁掉下半身 |
| **单件上衣/T恤/衬衫** | `medium shot from head to waist/hip` | 领口、袖口、版型细节清晰即可 |
| **单件裤装/半身裙** | `framed from waist/hip to ankle` | 版型、口袋、裤脚/裙摆清晰 |
| **连衣裙/连体裤** | `full body framed from head to ankle` | 完整版型展示，不可裁切 |
| **泳装/内衣套装** | `framed from head to mid-thigh` | 完整套装需全身，单品灵活 |
| **外套/开衫** | `full body or medium shot` | 重点展示版型和叠穿效果 |
| **家居服/睡衣** | `medium to full body, head to knee` | 舒适感展示，全身更佳 |

### 🎭 变量（随场景自动适配）
- **配饰**：包袋、首饰、墨镜、帽子、围巾、手表等 — 根据场景智能搭配
- **鞋子**：高跟鞋、运动鞋、凉鞋、靴子、拖鞋等 — 根据场景智能搭配
- **模特姿势与动作**：站立、行走、坐姿、倚靠、转身等 — 根据场景选择最自然的动作
- **手持道具**：咖啡杯、手机、书本、鲜花等 — 增强场景真实感

### 规则
1. 除非用户特别说明，**服装和模特绝对不能改变**
2. 配饰和鞋子必须与场景匹配（海滩→草编包+凉鞋，办公室→手提包+高跟鞋）
3. 姿势必须与场景匹配（咖啡店→倚靠桌边喝咖啡，街头→行走抓拍）
4. 配饰不要喧宾夺主，以衬托服装为目的，保持简洁得体
5. **镜头构图必须遵循品类构图规则**（两件套 head-to-knee，连衣裙 head-to-ankle 等）

---

## ⚠️ 核心风格要求（必须遵守）

**所有提示词必须为纯美式风格，禁止中式风格！**

### 美式风格特征
- **场景**：美式郊区住宅、现代美式公寓、加州阳光、纽约街头、迈阿密海滩
- **人物**：美国女性气质、自然自信、阳光健康、真实不做作
- **生活方式**：美式生活场景（BBQ、后院、车库、门廊、壁炉）
- **审美**：大气、舒适、实用、自然光线、真实质感
- **色彩**：温暖自然色调、大地色、柔和粉彩、高对比但不艳丽

### 禁止的中式元素
- ❌ 中式建筑、中式家具、中式园林
- ❌ 过度精致、过度修图、瓷白皮肤
- ❌ 网红风格、过度摆拍、僵硬姿势
- ❌ 过于鲜艳的色彩、过度滤镜
- ❌ 中式审美（瘦弱、白皙、幼态）

### 必须强调的关键词
```
American lifestyle, natural and authentic, 
suburban home, modern apartment, 
California vibes, East Coast style, 
confident and empowering, body positive,
realistic skin texture, no airbrushing
```

---

## 概述

本技能专为亚马逊女装电商场景图设计，能够根据模特上身女装图片，生成适合的产品展示场景图。支持用户指定场景类型或AI智能识别最佳场景，保持人物与服装的一致性。

**重要：所有场景和提示词必须为美式风格，面向美国市场。**

---

## 🇺🇸 美式风格完整指南

### 美式场景特征

#### 美式家庭场景
| 元素 | 美式特征 | 避免的中式元素 |
|------|----------|----------------|
| **住宅** | 独栋别墅、郊区住宅、联排别墅 | 中式园林、四合院 |
| **客厅** | 壁炉、地毯、落地窗、开放式厨房 | 中式家具、屏风 |
| **卧室** | King-size床、地毯、窗帘、床头柜 | 中式床品、红木家具 |
| **后院** | 草坪、烧烤架、泳池、门廊 | 假山、亭子 |
| **厨房** | 中岛台、不锈钢电器、大理石台面 | 中式厨具 |

#### 美式城市场景
| 元素 | 美式特征 | 避免的中式元素 |
|------|----------|----------------|
| **纽约** | 时代广场、中央公园、SoHo、布鲁克林 | 中式建筑 |
| **洛杉矶** | 好莱坞、圣莫尼卡、威尼斯海滩 | — |
| **迈阿密** | 南海滩、Art Deco、热带风情 | — |
| **旧金山** | 金门大桥、渔人码头、维多利亚式建筑 | — |
| **芝加哥** | 密歇根大道、千禧公园 | — |

#### 美式户外场景
| 元素 | 美式特征 | 避免的中式元素 |
|------|----------|----------------|
| **海滩** | 加州海滩、佛罗里达、汉普顿 | 中式海滨 |
| **山区** | 落基山脉、阿巴拉契亚 | 中式山景 |
| **国家公园** | 黄石、大峡谷、优胜美地 | — |
| **郊区** | 草坪、邮箱、门廊、车库 | — |

### 美式生活方式关键词

#### 日常生活
```
American suburban home, front porch, backyard BBQ,
garage sale, lemonade stand, picket fence,
driveway, mailbox, lawn mower, patio furniture
```

#### 城市生活
```
New York City street, Manhattan skyline, Brooklyn Bridge,
Los Angeles sunshine, Miami Beach, San Francisco hills,
coffee to go, subway, yellow taxi, Central Park jogger
```

#### 休闲度假
```
Hamptons weekend, Cape Cod, Napa Valley wine country,
Lake Tahoe, Aspen ski resort, Key West sunset,
road trip, RV camping, national parks
```

### 美式审美特征

#### 人物气质
```
Confident American woman, natural beauty, 
healthy and active lifestyle, body positive,
sun-kissed skin, genuine smile, relaxed posture,
empowered and independent, approachable
```

#### 视觉风格
```
Natural lighting, warm golden tones, 
authentic textures, lived-in feel,
editorial but approachable, magazine quality,
aspirational but relatable, real and honest
```

#### 色彩偏好
```
Earth tones: beige, tan, olive, rust, terracotta
Neutral palette: white, cream, gray, navy, black
Soft pastels: blush, sage, lavender, sky blue
Warm metallics: gold, brass, copper
```

### 美式vs中式对比

| 维度 | 美式风格 ✅ | 中式风格 ❌ |
|------|------------|-------------|
| **肤色** | 健康小麦色、自然肤色 | 瓷白、过度美白 |
| **身材** | 多元化、曲线美、body positive | 纤细、瘦弱 |
| **表情** | 自然自信、真诚微笑 | 嘟嘴、卖萌、过度甜美 |
| **姿势** | 放松自然、有动感 | 僵硬摆拍、过度优雅 |
| **场景** | 真实生活场景、自然环境 | 精致布景、过度修饰 |
| **光线** | 自然光、黄金时刻 | 过度打光、均匀无影 |
| **后期** | 自然真实、轻微修饰 | 过度磨皮、滤镜厚重 |

---

## 使用场景

- 为女装产品生成生活方式展示图
- 将模特图替换为特定场景背景
- 创建亚马逊A+页面的场景化图片
- 生成社交媒体营销素材
- 制作产品详情页的场景搭配图

## 核心功能

### 1. 场景选择模式

#### 手动指定场景
用户可从以下场景类别中选择：
- **家庭场景**：客厅、卧室、衣帽间、阳台、厨房、餐厅、浴室、花园庭院
- **办公场景**：开放式办公区、会议室、前台、休息室、接待大堂
- **城市场景**：街头、咖啡店、商场、艺术区、书店、餐厅、酒吧、剧院、美术馆、健身房
- **户外场景**：公园、海滩、度假村、乡村、山区、湖泊、薰衣草田、葡萄园、沙漠
- **特殊场景**：婚礼现场、派对、音乐会、节日庆典、游艇、马场

#### AI智能匹配
根据服装类型自动推荐最佳场景：
- 职业装 → 办公场景、城市街头
- 休闲装 → 咖啡店、公园、家居
- 晚装/派对装 → 高端餐厅、酒吧、城市夜景
- 度假装 → 海滩、度假村、热带风光
- 运动休闲装 → 健身房、公园、城市街头

### 2. 场景配饰 / 鞋子 / 姿势 速查表

> **使用规则**：每个场景生成提示词时，必须从下表中选取对应的配饰、鞋子和姿势融入提示词。配饰和鞋子以简洁得体为原则，不要喧宾夺主。

#### 家庭场景

| 场景 | 配饰 | 鞋子 | 模特姿势/动作 | 手持道具 |
|------|------|------|-------------|---------|
| **客厅** | 简约手链、小耳钉 | 赤脚 / 毛绒拖鞋 | 侧坐在沙发上、双腿蜷缩、靠在抱枕上 | 咖啡杯、书本、手机 |
| **卧室** | 无配饰 / 细项链 | 赤脚 | 坐在床边、伸懒腰、回头看镜头 | 枕头、晨间咖啡 |
| **衣帽间** | 项链、手镯、耳环（展示搭配） | 高跟鞋 / 短靴 | 站在衣架前挑选、对镜整理、转身看衣橱 | 手提包、围巾 |
| **厨房** | 简约手表、小耳钉 | 赤脚 / 平底鞋 | 倚靠中岛台、搅拌碗中食材、切水果 | 咖啡杯、水果、食谱书 |
| **餐厅** | 精致耳环、细手链 | 平底鞋 / 低跟鞋 | 坐在餐桌前、起身盛菜、举杯 | 酒杯、鲜花、蜡烛 |
| **浴室** | 无配饰 | 赤脚 | 靠在洗手台旁、照镜子、裹浴袍 | 护肤品、香薰蜡烛 |
| **花园庭院** | 草帽、编织手链、太阳镜 | 凉鞋 / 赤脚 | 坐在藤椅上、弯腰赏花、漫步小径 | 鲜花、园艺篮、柠檬水 |

#### 办公场景

| 场景 | 配饰 | 鞋子 | 模特姿势/动作 | 手持道具 |
|------|------|------|-------------|---------|
| **开放式办公区** | 简约手表、细项链、小耳钉 | 尖头高跟鞋 / 乐福鞋 | 坐在工位前敲键盘、站立翻阅文件、倚靠桌边 | 笔记本电脑、文件夹、咖啡杯 |
| **会议室** | 精致耳钉、手表 | 高跟鞋 | 站在白板前讲解、坐在会议桌前、双手交叉自信姿态 | 笔记本、激光笔、文件 |
| **前台/接待大堂** | 精致项链、耳环 | 高跟鞋 | 站立微笑迎接、双手交叠身前、优雅行走 | 文件夹、平板电脑 |
| **休息室** | 手链、简约耳饰 | 平底鞋 / 低跟鞋 | 侧坐在沙发、靠在吧台、端杯站立 | 咖啡杯、杂志、手机 |

#### 城市场景

| 场景 | 配饰 | 鞋子 | 模特姿势/动作 | 手持道具 |
|------|------|------|-------------|---------|
| **纽约街头** | 大号手提包、太阳镜、围巾 | 尖头高跟鞋 / 切尔西靴 | 大步行走、回头看、过马路 | 外卖咖啡杯、购物袋 |
| **洛杉矶街头** | 太阳镜、编织包、叠戴手链 | 凉鞋 / 运动鞋 | 悠闲行走、靠在墙边、转身甩发 | 冰咖啡、手机 |
| **咖啡店** | 简约手表、小耳钉 | 平底鞋 / 乐福鞋 | 倚靠桌边喝咖啡、坐在窗边看书、双手捧杯 | 咖啡杯、书本、笔记本 |
| **商场** | 太阳镜、手提包、精致耳环 | 高跟鞋 / 时尚平底鞋 | 提购物袋行走、橱窗前驻足、手扶扶梯 | 购物袋、手机 |
| **书店** | 细框眼镜、简约项链 | 平底鞋 / 乐福鞋 | 坐在角落阅读、踮脚取书、翻阅书页 | 书本、帆布袋 |
| **餐厅** | 精致耳环、手链、小型手拿包 | 高跟鞋 | 坐在桌前、举杯、侧身交谈 | 酒杯、菜单 |
| **酒吧** | 闪亮耳环、手链、小型手拿包 | 高跟鞋 | 坐在吧台、侧身靠墙、举杯微醺 | 鸡尾酒杯、手机 |
| **剧院** | 珍珠项链、精致耳环、丝绸围巾 | 高跟鞋 | 端坐、侧身交谈、优雅入场 | 手拿包、节目单 |
| **美术馆** | 极简金属耳环、细手链 | 平底鞋 / 低跟鞋 | 站立欣赏画作、双手背后、侧身思考 | 帆布袋 |
| **健身房** | 运动手表、运动发带 | 运动鞋 | 拉伸、跑步、举哑铃 | 水壶、毛巾、瑜伽垫 |

#### 户外场景

| 场景 | 配饰 | 鞋子 | 模特姿势/动作 | 手持道具 |
|------|------|------|-------------|---------|
| **中央公园** | 太阳镜、编织包、草帽 | 凉鞋 / 小白鞋 | 坐在长椅上、散步、倚靠栏杆 | 冰咖啡、书本 |
| **加州海滩** | 太阳镜、贝壳项链、编织手链、宽檐草帽 | 赤脚 / 人字拖 | 走在浪花边、撩头发、蹲在沙滩上、伸展双臂 | 沙滩包、椰子饮料 |
| **度假村** | 太阳镜、精致手链、宽檐帽 | 凉鞋 / 人字拖 | 躺在躺椅上、池边站立、端鸡尾酒 | 鸡尾酒、防晒霜、杂志 |
| **乡村** | 编织帽、棉麻围巾、皮手环 | 靴子 / 帆布鞋 | 倚靠木栅栏、漫步花田、弯腰摘花 | 花篮、野餐篮 |
| **山区** | 棒球帽、登山手表 | 登山靴 / 运动鞋 | 站在山顶眺望、行走小径、坐在岩石上 | 登山水壶、背包 |
| **湖泊** | 草帽、简约项链 | 凉鞋 / 赤脚 | 坐在码头边、弯腰触水、站在船头 | 书本、野餐毯 |
| **薰衣草田** | 草帽、编织手链、小花耳钉 | 凉鞋 / 赤脚 | 漫步花田、转身甩发、闭眼深呼吸 | 花束、草编篮 |
| **葡萄园** | 太阳镜、简约金饰 | 乐福鞋 / 低跟凉鞋 | 穿行葡萄藤间、手持酒杯品酒、倚靠木桶 | 红酒杯、葡萄 |
| **沙漠** | 波西米亚头巾、层叠手链、大号太阳镜 | 靴子 / 凉鞋 | 站立远眺、风吹裙摆、行走沙丘 | 流苏包 |

#### 特殊场景

| 场景 | 配饰 | 鞋子 | 模特姿势/动作 | 手持道具 |
|------|------|------|-------------|---------|
| **婚礼现场** | 精致珍珠项链、钻石耳环、丝绸手拿包 | 高跟鞋 | 端坐观礼、侧身交谈、鼓掌微笑 | 香槟杯、鲜花 |
| **派对** | 闪亮耳环、手链、小型手拿包 | 高跟鞋 | 举杯社交、靠墙站立、旋转裙摆 | 香槟杯、气球 |
| **音乐会** | 层叠项链、手链、耳钉 | 靴子 / 运动鞋 | 随音乐摇摆、举手欢呼、侧身站立 | 手机、荧光棒 |
| **节日庆典** | 节日主题配饰、金色耳环 | 平底鞋 / 短靴 | 微笑行走、驻足观赏、与人群互动 | 灯笼、小吃、热饮 |
| **游艇** | 太阳镜、简约金饰、丝巾 | 赤脚 / 平底鞋 | 倚靠船舷、坐在甲板上、迎风站立 | 香槟杯、防晒霜 |
| **马场** | 皮手套、丝巾、马术帽 | 马靴 / 靴子 | 牵马行走、倚靠围栏、骑马姿态 | 马鞭、缰绳 |

---

### 2.1 场景库详解

#### 家庭场景（Home）- 美式住宅

**客厅**
- 氛围：温馨、舒适、放松、美式家庭
- 光线：自然光从落地窗洒入，柔和温暖
- 道具：布艺沙发、抱枕、地毯、绿植、咖啡杯、壁炉
- 适用服装：家居服、休闲装、针织衫、睡衣
- 英文提示词模板：
  ```
  American suburban home living room with soft natural light from large windows, 
  woman relaxing on comfortable sectional sofa wearing cozy knit sweater, 
  warm and inviting atmosphere, plush textures, 
  fireplace in background, family photos on wall, peaceful morning mood,
  authentic American lifestyle, realistic and relatable
  ```

**卧室**
- 氛围：私密、舒适、浪漫、美式主卧
- 光线：柔和的晨光或黄昏光
- 道具：King-size床、地毯、窗帘、梳妆台、床头灯
- 适用服装：睡衣、内衣、晨袍、家居服
- 英文提示词模板：
  ```
  American master bedroom with soft morning light filtering through curtains, 
  woman sitting on king-size bed wearing comfortable pajama set, 
  intimate and relaxed atmosphere, plush bedding and pillows, 
  dresser mirror reflection, gentle and peaceful mood,
  authentic American home, natural and inviting
  ```

**衣帽间**
- 氛围：时尚、有序、奢华
- 光线：均匀的室内光，重点照明
- 道具：衣架、鞋架、包包、配饰
- 适用服装：各类服装展示、搭配展示
- 英文提示词模板：
  ```
  Organized walk-in closet with elegant lighting, 
  woman selecting outfit, surrounded by neatly arranged clothes, 
  fashion-forward atmosphere, mirror reflection, 
  luxurious and stylish mood
  ```

**厨房**
- 氛围：温馨、生活化、活力
- 光线：自然光从窗户洒入，暖色调
- 道具：大理石台面、厨具、鲜花、水果
- 适用服装：家居服、休闲装、围裙搭配
- 英文提示词模板：
  ```
  Bright modern kitchen with marble countertops and natural light, 
  woman preparing food wearing casual yet stylish outfit, 
  warm and inviting atmosphere, fresh flowers and fruit, 
  lifestyle photography, authentic and relatable mood
  ```

**餐厅**
- 氛围：温馨、家庭、社交
- 光线：柔和的吊灯光，自然光补充
- 道具：餐桌、餐具、蜡烛、鲜花
- 适用服装：休闲装、轻正装、家居服
- 英文提示词模板：
  ```
  Elegant dining room with soft chandelier lighting, 
  woman seated at table wearing casual chic outfit, 
  warm and intimate atmosphere, candles and fresh flowers, 
  family gathering mood, lifestyle photography
  ```

**浴室**
- 氛围：私密、放松、奢华
- 光线：柔和的室内光，镜面反射
- 道具：浴缸、毛巾、香薰、绿植
- 适用服装：浴袍、晨袍、家居服
- 英文提示词模板：
  ```
  Luxurious bathroom with freestanding tub and soft lighting, 
  woman in plush robe relaxing by mirror, 
  spa-like atmosphere, candles and plants, 
  intimate and serene mood, editorial lifestyle
  ```

**花园庭院**
- 氛围：自然、浪漫、休闲
- 光线：自然光，斑驳树影
- 道具：花卉、藤椅、喷泉、石径
- 适用服装：度假装、连衣裙、休闲装
- 英文提示词模板：
  ```
  Beautiful garden courtyard with blooming flowers and stone path, 
  woman enjoying sunshine wearing flowing summer dress, 
  romantic and natural atmosphere, dappled sunlight, 
  peaceful and serene mood, outdoor lifestyle photography
  ```

---

#### 办公场景（Office）

**开放式办公区**
- 氛围：现代、专业、活力
- 光线：均匀的室内照明，自然光补充
- 道具：办公桌、电脑、绿植、文件
- 适用服装：通勤装、商务休闲、衬衫、西装
- 英文提示词模板：
  ```
  Modern open-plan office with natural light, 
  professional woman working at desk wearing tailored blazer, 
  focused yet approachable expression, contemporary workspace, 
  plants and minimal decor, productive atmosphere
  ```

**会议室**
- 氛围：专业、正式、自信
- 光线：柔和的室内光，投影仪光
- 道具：会议桌、白板、笔记本电脑
- 适用服装：商务正装、西装套装、衬衫
- 英文提示词模板：
  ```
  Professional meeting room with soft lighting, 
  confident woman presenting at whiteboard wearing elegant suit, 
  authoritative yet approachable expression, modern conference room, 
  professional and empowering atmosphere
  ```

**前台/接待大堂**
- 氛围：高端、专业、现代
- 光线：明亮的室内照明，自然光
- 道具：接待台、沙发、绿植、艺术品
- 适用服装：商务正装、通勤装、西装
- 英文提示词模板：
  ```
  Sleek corporate lobby with modern design and high ceilings, 
  professional woman standing confidently wearing tailored suit, 
  bright and sophisticated atmosphere, contemporary art and plants, 
  executive and polished mood
  ```

**休息室**
- 氛围：放松、舒适、社交
- 光线：柔和的室内光，暖色调
- 道具：沙发、茶几、咖啡机、杂志
- 适用服装：商务休闲、针织衫、衬衫
- 英文提示词模板：
  ```
  Comfortable office lounge with soft lighting and modern furniture, 
  woman relaxing on sofa wearing business casual outfit, 
  warm and inviting atmosphere, coffee and magazines, 
  professional yet relaxed mood
  ```

---

#### 城市场景（City）- 美式都市

**纽约街头**
- 氛围：时尚、活力、纽约都市感
- 光线：自然光，城市光影，黄色出租车
- 道具：纽约建筑、人行道、咖啡杯、购物袋
- 适用服装：街头时尚、休闲装、牛仔、外套
- 英文提示词模板：
  ```
  New York City street with classic brownstone buildings, 
  stylish woman walking confidently wearing street fashion, 
  urban NYC energy, natural movement, yellow taxi in background, 
  fashion-forward and dynamic mood, Manhattan vibes
  ```

**洛杉矶街头**
- 氛围：阳光、时尚、加州活力
- 光线：强烈的加州阳光，棕榈树影
- 道具：棕榈树、复古汽车、涂鸦墙、滑板
- 适用服装：休闲装、运动风、波西米亚
- 英文提示词模板：
  ```
  Los Angeles street with palm trees and sunshine, 
  stylish woman walking confidently wearing casual California outfit, 
  sunny LA vibes, natural movement, palm tree shadows, 
  laid-back and fashion-forward mood, West Coast style
  ```

**咖啡店**
- 氛围：休闲、文艺、社交
- 光线：温暖的室内光，窗户自然光
- 道具：咖啡杯、书本、笔记本、绿植
- 适用服装：休闲装、针织衫、连衣裙
- 英文提示词模板：
  ```
  Cozy coffee shop with warm ambient lighting, 
  woman enjoying coffee wearing casual yet stylish outfit, 
  relaxed and artistic atmosphere, books and plants, 
  soft background blur, intimate and inviting mood
  ```

**商场**
- 氛围：时尚、现代、购物
- 光线：明亮的室内照明
- 道具：橱窗、展示架、购物袋
- 适用服装：时尚装、连衣裙、外套
- 英文提示词模板：
  ```
  Modern shopping mall with elegant interior design, 
  fashionable woman browsing store wearing trendy outfit, 
  bright and contemporary atmosphere, reflective surfaces, 
  shopping bags, stylish and aspirational mood
  ```

**书店**
- 氛围：文艺、知性、安静
- 光线：温暖的室内光，阅读灯
- 道具：书架、书籍、阅读角、绿植
- 适用服装：休闲装、针织衫、文艺风连衣裙
- 英文提示词模板：
  ```
  Charming bookstore with warm lighting and floor-to-ceiling shelves, 
  woman browsing books wearing intellectual casual outfit, 
  cozy and artistic atmosphere, books and reading nook, 
  literary and refined mood
  ```

**餐厅**
- 氛围：精致、浪漫、社交
- 光线：柔和的烛光，暖色调
- 道具：餐桌、餐具、酒杯、鲜花
- 适用服装：晚装、小礼服、时尚休闲装
- 英文提示词模板：
  ```
  Upscale restaurant with elegant decor and soft candlelight, 
  woman dining wearing sophisticated evening outfit, 
  romantic and luxurious atmosphere, wine glasses and flowers, 
  fine dining and glamorous mood
  ```

**酒吧**
- 氛围：时尚、夜生活、放松
- 光线：霓虹灯、调酒灯光、暗色调
- 道具：吧台、酒杯、灯光装置
- 适用服装：派对装、时尚晚装、小黑裙
- 英文提示词模板：
  ```
  Stylish cocktail bar with ambient lighting and modern decor, 
  woman enjoying evening drink wearing chic party outfit, 
  sophisticated nightlife atmosphere, neon accents and cocktails, 
  glamorous and social mood
  ```

**剧院**
- 氛围：高雅、艺术、正式
- 光线：舞台灯光、暖色调
- 道具：红丝绒座椅、幕布、水晶灯
- 适用服装：晚装、正装、优雅连衣裙
- 英文提示词模板：
  ```
  Elegant theater interior with red velvet seats and crystal chandelier, 
  woman attending performance wearing formal evening wear, 
  sophisticated and cultural atmosphere, warm stage lighting, 
  refined and glamorous mood
  ```

**美术馆**
- 氛围：艺术、现代、文化
- 光线：均匀的展览灯光，白色空间
- 道具：艺术品、画作、雕塑
- 适用服装：时尚装、设计师款式、简约风
- 英文提示词模板：
  ```
  Contemporary art gallery with white walls and exhibition lighting, 
  woman admiring artwork wearing minimalist designer outfit, 
  artistic and intellectual atmosphere, modern sculptures and paintings, 
  cultured and sophisticated mood
  ```

**健身房**
- 氛围：活力、健康、运动
- 光线：明亮的室内照明
- 道具：健身器材、瑜伽垫、镜子
- 适用服装：运动装、瑜伽服、运动休闲装
- 英文提示词模板：
  ```
  Modern fitness studio with natural light and mirrors, 
  woman exercising wearing stylish activewear, 
  energetic and healthy atmosphere, gym equipment, 
  motivational and empowering mood
  ```

---

#### 户外场景（Outdoor）- 美式自然

**中央公园（纽约）**
- 氛围：自然、都市绿洲、纽约经典
- 光线：自然光，树荫，城市天际线
- 道具：长椅、湖泊、树木、慢跑者、狗
- 适用服装：休闲装、运动装、连衣裙
- 英文提示词模板：
  ```
  Central Park New York with dappled sunlight through trees, 
  woman enjoying leisure time wearing casual summer dress, 
  natural and refreshing atmosphere, NYC skyline in background, 
  peaceful and relaxed mood, golden hour lighting, iconic American park
  ```

**加州海滩**
- 氛围：度假、浪漫、加州自由
- 光线：强烈的加州阳光，黄金时刻
- 道具：沙滩、海浪、冲浪板、救生塔、棕榈树
- 适用服装：泳装、度假装、波西米亚风
- 英文提示词模板：
  ```
  California beach at golden hour, woman walking along shoreline 
  wearing flowing bohemian dress, romantic and carefree atmosphere, 
  Pacific Ocean waves, warm sunset light, palm trees silhouette,
  dreamy and serene mood, SoCal vibes
  ```

**度假村**
- 氛围：奢华、放松、享受
- 光线：柔和的自然光，热带光线
- 道具：泳池、躺椅、热带植物、鸡尾酒
- 适用服装：度假装、泳装、晚装
- 英文提示词模板：
  ```
  Luxurious resort setting with tropical plants and pool, 
  woman relaxing on lounge chair wearing elegant resort wear, 
  indulgent and serene atmosphere, soft tropical light, 
  cocktail in hand, vacation and luxury mood
  ```

**乡村**
- 氛围：质朴、自然、田园
- 光线：自然光，金色阳光
- 道具：木栅栏、花草、谷仓、田野
- 适用服装：波西米亚风、棉麻装、碎花连衣裙
- 英文提示词模板：
  ```
  Rustic countryside with rolling hills and wooden fences, 
  woman walking through wildflowers wearing bohemian dress, 
  natural and pastoral atmosphere, golden sunlight, 
  peaceful and authentic mood
  ```

**山区**
- 氛围：壮丽、清新、冒险
- 光线：高山光线，云层
- 道具：山峰、松林、木屋、小径
- 适用服装：户外装、休闲装、针织衫
- 英文提示词模板：
  ```
  Majestic mountain landscape with pine forests and clear sky, 
  woman standing on scenic overlook wearing casual outdoor outfit, 
  fresh and adventurous atmosphere, mountain views, 
  inspiring and serene mood
  ```

**湖泊**
- 氛围：宁静、浪漫、自然
- 光线：水面反射，柔和光线
- 道具：湖水、小船、芦苇、夕阳
- 适用服装：度假装、连衣裙、休闲装
- 英文提示词模板：
  ```
  Serene lake with calm water reflecting sunset colors, 
  woman sitting on dock wearing flowing summer dress, 
  romantic and peaceful atmosphere, boats and reeds, 
  tranquil and dreamy mood
  ```

**薰衣草田**
- 氛围：浪漫、梦幻、法式
- 光线：黄金时刻，暖紫色调
- 道具：薰衣草花田、远处农庄、蓝天
- 适用服装：连衣裙、度假装、波西米亚风
- 英文提示词模板：
  ```
  Beautiful lavender field at golden hour with purple flowers stretching to horizon, 
  woman walking through lavender wearing flowing white dress, 
  romantic and dreamy atmosphere, warm purple and gold tones, 
  French countryside and magical mood
  ```

**葡萄园**
- 氛围：优雅、丰收、田园
- 光线：午后阳光，金色调
- 道具：葡萄藤、酒庄、木桶、阳光
- 适用服装：休闲装、度假装、连衣裙
- 英文提示词模板：
  ```
  Picturesque vineyard with grapevines and rustic winery, 
  woman strolling through vines wearing elegant casual outfit, 
  warm afternoon light, golden and green tones, 
  sophisticated and pastoral mood
  ```

**沙漠**
- 氛围：壮阔、神秘、异域
- 光线：强烈阳光，长阴影
- 道具：沙丘、仙人掌、骆驼、夕阳
- 适用服装：波西米亚风、度假装、民族风
- 英文提示词模板：
  ```
  Stunning desert landscape with sand dunes and clear sky, 
  woman standing in golden light wearing bohemian outfit, 
  dramatic and exotic atmosphere, long shadows and warm tones, 
  adventurous and mysterious mood
  ```

---

#### 特殊场景（Special）

**婚礼现场**
- 氛围：浪漫、温馨、庄重
- 光线：柔和的自然光，暖色调
- 道具：鲜花拱门、白色椅子、丝带、蜡烛
- 适用服装：礼服、伴娘装、优雅连衣裙
- 英文提示词模板：
  ```
  Elegant wedding venue with floral arch and white chairs, 
  woman attending ceremony wearing sophisticated formal wear, 
  romantic and heartfelt atmosphere, soft natural light, 
  celebration and love mood
  ```

**派对**
- 氛围：活力、欢乐、社交
- 光线：彩色灯光，霓虹
- 道具：气球、彩带、香槟、DJ台
- 适用服装：派对装、亮片裙、时尚晚装
- 英文提示词模板：
  ```
  Vibrant party venue with colorful lights and decorations, 
  woman celebrating wearing glamorous party outfit, 
  energetic and festive atmosphere, balloons and champagne, 
  fun and social mood
  ```

**音乐会**
- 氛围：激情、艺术、沉浸
- 光线：舞台灯光，聚光灯
- 道具：舞台、乐器、观众、灯光秀
- 适用服装：摇滚风、时尚装、派对装
- 英文提示词模板：
  ```
  Concert venue with dramatic stage lighting and crowd, 
  woman enjoying music wearing edgy fashionable outfit, 
  energetic and immersive atmosphere, spotlights and stage, 
  passionate and artistic mood
  ```

**节日庆典**
- 氛围：欢乐、传统、热闹
- 光线：节日灯光，烟花
- 道具：灯笼、装饰、美食、人群
- 适用服装：节日装、民族风、红色系
- 英文提示词模板：
  ```
  Festive celebration with lanterns and traditional decorations, 
  woman participating in festival wearing festive attire, 
  joyful and cultural atmosphere, colorful lights and crowds, 
  happy and traditional mood
  ```

**游艇**
- 氛围：奢华、自由、海洋
- 光线：强烈阳光，海面反射
- 道具：游艇甲板、大海、夕阳、香槟
- 适用服装：度假装、泳装、白色连衣裙
- 英文提示词模板：
  ```
  Luxury yacht on calm ocean waters at sunset, 
  woman relaxing on deck wearing elegant resort wear, 
  glamorous and free atmosphere, golden sunset light, 
  nautical and luxurious mood
  ```

**马场**
- 氛围：优雅、运动、田园
- 光线：自然光，草地
- 道具：马匹、围栏、马厩、草地
- 适用服装：马术装、休闲装、乡村风
- 英文提示词模板：
  ```
  Elegant equestrian estate with horses and green paddocks, 
  woman posing near horse wearing stylish equestrian outfit, 
  sophisticated and sporty atmosphere, natural daylight, 
  refined and countryside mood
  ```

---

### 3. 氛围关键词库

#### 光线氛围
- **Natural light**（自然光）：真实、亲切、柔和
- **Golden hour**（黄金时刻）：温暖、浪漫、高端
- **Soft diffused light**（柔和漫射光）：均匀、舒适、无阴影
- **Dramatic lighting**（戏剧性光线）：强烈对比、艺术感、时尚
- **Backlight**（逆光）：轮廓光、梦幻、浪漫
- **Studio lighting**（影棚光）：专业、清晰、可控

#### 空间氛围
- **Minimalist**（极简）：干净、现代、高级
- **Cozy**（温馨）：舒适、亲切、放松
- **Urban**（都市）：时尚、活力、现代
- **Rustic**（乡村）：自然、质朴、温馨
- **Luxurious**（奢华）：高端、精致、品质
- **Artistic**（艺术）：创意、独特、有格调

#### 情感氛围
- **Confident**（自信）：力量、专业、有魅力
- **Relaxed**（放松）：舒适、自然、无压力
- **Romantic**（浪漫）：温柔、梦幻、有情感
- **Playful**（俏皮）：活力、有趣、年轻
- **Elegant**（优雅）：高贵、精致、有品位
- **Edgy**（前卫）：大胆、时尚、有态度

---

## 提示词写作最佳实践

### 基础四要素

#### 1. 主体描述（Subject）
- **明确**：清晰描述人物特征、服装细节
- **具体**：避免模糊词汇，使用精确描述
- **示例**：
  ```
  A confident woman in her mid-30s, wearing a tailored navy blazer 
  with matching wide-leg trousers, natural makeup, styled hair
  ```

#### 2. 环境设定（Environment）
- **场景**：具体的地点和空间
- **氛围**：光线、色调、情绪
- **道具**：相关物品增强真实感
- **示例**：
  ```
  Modern office with floor-to-ceiling windows, natural light flooding in, 
  minimalist desk with laptop and coffee cup, green plants
  ```

#### 3. 构图与视角（Composition）
- **镜头距离**：特写（CU）、半身（MCU）、全身（LS）
- **角度**：平视、俯视、仰视、侧面
- **景深**：浅景深（背景虚化）、深景深（全景清晰）
- **示例**：
  ```
  Medium shot, eye-level angle, shallow depth of field with 
  bokeh background, focus on subject
  ```

#### 4. 风格与质感（Style）
- **摄影风格**：时尚摄影、生活摄影、商业摄影
- **后期风格**：自然、复古、高对比、柔和
- **质感**：真实皮肤、自然光影、细腻纹理
- **示例**：
  ```
  Professional fashion photography, natural skin texture, 
  soft diffused lighting, high-end editorial style
  ```

---

### 镜头语言参考

#### 镜头距离
- **Extreme Close-up (ECU)**：极特写，突出细节（眼睛、嘴唇、配饰）
- **Close-up (CU)**：特写，面部或产品细节
- **Medium Close-up (MCU)**：半身，上半身展示
- **Medium Shot (MS)**：中景，腰部以上
- **Medium Long Shot (MLS)**：中远景，膝盖以上
- **Long Shot (LS)**：全身，完整人物
- **Extreme Long Shot (ELS)**：远景，人物在环境中

#### 镜头角度
- **Eye level**：平视，最自然、亲切
- **Low angle**：仰视，显得高大、有气势
- **High angle**：俯视，显得小巧、脆弱
- **Dutch angle**：倾斜，增加动感、戏剧性
- **Over the shoulder**：过肩，增加对话感

#### 光影方向
- **Front lighting**：正面光，均匀照亮
- **Side lighting**：侧光，增加立体感
- **Backlight**：逆光，轮廓光、梦幻感
- **Rembrandt lighting**：伦勃朗光，经典人像光
- **Butterfly lighting**：蝴蝶光，时尚人像

---

### 去塑料感技巧

#### 1. 真实皮肤质感
```
Natural skin texture, visible pores, subtle skin imperfections, 
realistic skin tone, no airbrushing
```

#### 2. 自然光影
```
Soft natural lighting, realistic shadows, light falloff, 
no harsh edges, natural color temperature
```

#### 3. 环境细节
```
Realistic environment details, natural wear and tear, 
authentic textures, lived-in feel
```

#### 4. 人物姿态
```
Natural pose, relaxed posture, genuine expression, 
candid moment, not overly posed
```

---

## 生成流程

### 步骤1：图片分析

用户提供模特上身女装图片后，AI分析：
- **服装类型**：上装/下装/连衣裙/套装
- **服装风格**：职业/休闲/晚装/度假/运动
- **颜色与图案**：主色调、图案类型
- **面料质感**：棉/丝/针织/牛仔等
- **目标人群**：根据风格推断年龄和场合

### 步骤2：场景推荐

#### 手动模式
列出适合的场景选项，用户选择：
```
根据您上传的[职业西装]，推荐以下场景：
1. 办公室 - 专业、现代
2. 会议室 - 正式、自信
3. 城市街头 - 时尚、活力
4. 咖啡店 - 休闲商务

请选择场景编号，或输入自定义场景描述：
```

#### 自动模式
AI根据服装分析自动选择最佳场景，并说明理由：
```
自动匹配：现代办公室场景
理由：职业西装最适合展示专业形象，办公室场景能突出通勤场景的实用性，
自然光照明能真实呈现面料质感。
```

### 步骤3：提示词生成

基于选择的场景，生成完整的英文提示词。

**重要：必须返回固定JSON格式，不要返回纯文本！**

#### 提示词生成规则

每条提示词必须包含以下元素（参考"场景配饰速查表"）：

1. **服装描述**（LOCKED）：完全保留用户上传的服装，不得修改款式/颜色/面料
2. **配饰**：从速查表中选取该场景对应的 1-3 件配饰，自然融入提示词
3. **鞋子**：从速查表中选取该场景对应的鞋子类型
4. **姿势/动作**：从速查表中选取该场景最自然的 1-2 个动作
5. **手持道具**：可选，增强场景真实感

**提示词结构模板**：
```
[场景环境描述], [人物外貌描述-保持原图一致] wearing [服装描述-原样保留], 
accessorized with [场景配饰], wearing [场景鞋子], 
[姿势/动作描述], holding [手持道具], 
[光线], [氛围关键词], [构图], [风格要求]
```

**示例（海滩场景）**：
```
California beach at golden hour, a woman in her mid-30s with long wavy brown hair 
wearing a flowing floral maxi dress (exact same dress as original photo), 
accessorized with a straw tote bag and gold shell necklace and oversized sunglasses, 
wearing tan leather sandals, 
walking along the shoreline with dress flowing in the breeze, holding a coconut drink, 
warm golden sunset light, dreamy and serene atmosphere, 
medium full body shot, fashion editorial style, natural and authentic
```

#### JSON返回格式定义

**AI智能推荐模式**（推荐N个场景，每个1条提示词）：
```json
{
  "mode": "recommend",
  "scenes": [
    {
      "name": "场景名称（英文）",
      "name_cn": "场景名称（中文）",
      "description": "场景详细描述",
      "prompts": [
        "完整的英文提示词1"
      ]
    }
  ]
}
```

**指定场景模式**（1个场景，生成M条不同角度提示词）：
```json
{
  "mode": "specify",
  "scenes": [
    {
      "name": "场景名称（英文）",
      "name_cn": "场景名称（中文）",
      "description": "场景详细描述",
      "prompts": [
        "完整英文提示词-角度1",
        "完整英文提示词-角度2",
        "完整英文提示词-角度3"
      ]
    }
  ]
}
```

#### 格式要求

1. **必须返回 valid JSON**，可以被JSON.parse()解析
2. **prompts数组**：每个元素是一条第完整的英文提示词
3. **提示词格式**：`[场景描述], [人物描述] wearing [服装描述], [光线描述], [氛围关键词], [构图建议], [风格要求]`
4. **不要添加```json```标记**，直接返回JSON字符串
5. **scene数量**：recommend模式返回N个scene，specify模式返回1个scene
6. **prompts数量**：recommend模式每个scene 1条，specify模式每个scene M条

#### 示例输出（AI智能推荐3个场景）

```json
{
  "mode": "recommend",
  "scenes": [
    {
      "name": "Modern Office",
      "name_cn": "现代办公室",
      "description": "现代企业办公室，落地窗自然光，专业氛围",
      "prompts": [
        "Modern open-plan office with floor-to-ceiling windows and natural light, a confident woman in her mid-30s wearing a tailored navy blazer with matching wide-leg trousers, accessorized with a minimalist gold watch and small stud earrings, wearing pointed-toe nude heels, standing at desk reviewing documents with confident posture, natural makeup, styled medium-length hair, holding a leather portfolio, soft diffused lighting, contemporary workspace with plants and minimal decor, medium shot, shallow depth of field, professional fashion photography, realistic skin texture, high-end editorial style, warm and productive atmosphere"
      ]
    },
    {
      "name": "Coffee Shop",
      "name_cn": "咖啡店",
      "description": "温馨咖啡店，暖色灯光，文艺休闲氛围",
      "prompts": [
        "Cozy coffee shop with warm ambient lighting, a friendly woman in her early 30s wearing an oversized cream knit sweater and high-waisted dark wash jeans, accessorized with a simple gold pendant necklace and leather-strapped watch, wearing brown leather loafers, leaning against wooden table sipping coffee with relaxed genuine smile, natural makeup, loose wavy hair, books and plants on table, full body shot, natural light from window, lifestyle photography, warm and inviting mood, authentic and relatable atmosphere"
      ]
    },
    {
      "name": "City Street",
      "name_cn": "城市街头",
      "description": "纽约街头，时尚都市感，自然光线",
      "prompts": [
        "New York City street with classic brownstone buildings, a stylish woman in her mid-30s walking confidently wearing street fashion, urban NYC energy, natural movement, yellow taxi in background, fashion-forward and dynamic mood, Manhattan vibes, bright afternoon light, editorial street photography, confident and empowered atmosphere"
      ]
    }
  ]
}
```

#### 调用示例

**AI智能推荐**（推荐3个场景）：
```
用户上传了职业女装图片，请推荐3个最适合的场景，每个场景生成1条提示词。
必须返回JSON格式，包含scenes数组，每个scene包含name/name_cn/description/prompts。
```

**指定场景**（生成3条不同角度提示词）：
```
用户选择"现代办公室"场景，请生成3条不同角度/构图的专业提示词。
必须返回JSON格式，scenes数组只有1个元素，prompts数组包含3条提示词。
```

### 步骤4：图片生成

使用文生图能力生成场景图：

```bash
# 调用多模态生成技能
echo -n "<token>" | python3 <SKILL_DIR>/scripts/buddy-cloud.py image "<prompt>" --aspect-ratio 3:4 --token-stdin
```

### 步骤5：优化与迭代

如果生成结果不理想，可调整：
- **光线**：改变光源方向和质感
- **构图**：调整镜头距离和角度
- **氛围**：增强或减弱特定情绪
- **细节**：添加或移除道具元素

---

## 输出格式

### 完整输出示例

```markdown
# 场景图生成结果

## 产品分析
- **服装类型**：职业西装套装
- **风格**：商务休闲
- **主色调**：深海军蓝
- **面料**：羊毛混纺
- **适用场合**：通勤、商务会议

## 场景选择
- **场景类型**：现代办公室
- **选择理由**：突出专业形象，展示通勤场景实用性
- **氛围关键词**：Professional, Confident, Modern, Natural Light

## 生成提示词

### 主提示词
```
Modern open-plan office with floor-to-ceiling windows and natural light, 
a confident woman in her mid-30s wearing a tailored navy blazer 
with matching wide-leg trousers, professional yet approachable expression, 
natural makeup, styled medium-length hair, 
soft diffused lighting, contemporary workspace with plants and minimal decor, 
medium shot, shallow depth of field, 
professional fashion photography, realistic skin texture, 
high-end editorial style, warm and productive atmosphere
```

### 负向提示词
```
cartoon, anime, illustration, painting, drawing, 
plastic skin, airbrushed, unrealistic, 
low quality, blurry, distorted, 
watermark, text, logo, cropped image, 
clothing cut off, incomplete outfit, 
garment partially visible
```

## 生成参数
- 图片比例：3:4（竖图）
- 模型：混元生图3.0
- Prompt改写：开启

## 备选场景提示词

### 场景B：城市街头
```
Vibrant city street with modern architecture, 
stylish woman walking confidently wearing navy blazer suit, 
urban energy, natural movement, city lights in background, 
golden hour lighting, fashion-forward and dynamic mood, 
full body shot, professional fashion photography
```

### 场景C：咖啡店
```
Cozy coffee shop with warm ambient lighting, 
woman taking a break wearing professional yet stylish navy suit, 
relaxed atmosphere, coffee cup on table, books and plants, 
soft background blur, intimate and inviting mood, 
medium close-up, editorial style photography
```
```

---

## 检查清单

### 输入验证
- [ ] 图片清晰，人物和服装可见
- [ ] 服装类型已识别
- [ ] 目标场景已确定（手动或自动）

### 提示词质量
- [ ] 包含主体描述（人物特征、服装细节）
- [ ] 包含环境设定（场景、光线、道具）
- [ ] 包含构图建议（镜头距离、角度）
- [ ] 包含风格要求（摄影风格、质感）
- [ ] 使用英文，语法正确
- [ ] 无模糊词汇，描述具体

### 生成参数
- [ ] 图片比例适合用途（3:4竖图等）
- [ ] 负向提示词已填写
- [ ] Prompt改写已开启（如适用）

### 结果验证
- [ ] 人物特征与原图一致
- [ ] 服装细节准确呈现
- [ ] 场景氛围符合预期
- [ ] 光影自然真实
- [ ] 无明显AI痕迹（塑料感、畸形等）

---

## 高级技巧

### 1. 保持人物一致性

当需要生成同一人物的多张场景图时：

**方法A：详细描述锁定**
在每张图的提示词中使用完全相同的人物描述：
```
EXACT SAME woman: mid-30s, medium-length brown hair, 
natural makeup, specific facial features...
```

**方法B：参考图模式**
使用图生图能力，以原图为参考：
```bash
echo -n "<token>" | python3 <SKILL_DIR>/scripts/buddy-cloud.py image "<prompt>" --reference-image "path/to/reference.jpg" --token-stdin
```

### 2. 场景氛围增强

**增加真实感**：
```
Lived-in space, natural wear and tear, 
authentic details, not staged, candid moment
```

**增强情绪**：
```
Warm and inviting, cozy atmosphere, 
sense of comfort and relaxation, 
emotional connection with viewer
```

**突出产品**：
```
Clothing as focal point, 
natural draping of fabric, 
realistic texture and movement, 
garment details clearly visible
```

### 3. 批量生成策略

为同一产品生成多场景图时：
1. 保持人物描述一致
2. 变化场景和构图
3. 调整光线和氛围
4. 确保风格统一

---

## 参考资料

本技能包含以下参考资源：

### 场景库
涵盖家庭、办公、城市、户外四大类场景，每个场景包含：
- 氛围描述
- 光线建议
- 推荐道具
- 适用服装
- 提示词模板

### 氛围关键词库
- 光线氛围：Natural light, Golden hour, Soft diffused等
- 空间氛围：Minimalist, Cozy, Urban等
- 情感氛围：Confident, Relaxed, Romantic等

### 镜头语言参考
- 镜头距离：ECU, CU, MCU, MS, MLS, LS, ELS
- 镜头角度：Eye level, Low angle, High angle等
- 光影方向：Front, Side, Back, Rembrandt, Butterfly

---

## 常见问题

### Q: 如何避免AI生成的塑料感？
A: 在提示词中加入以下关键词：
```
Natural skin texture, visible pores, realistic lighting, 
no airbrushing, authentic feel, lived-in atmosphere
```

### Q: 如何确保服装细节准确？
A: 在提示词中详细描述服装特征：
```
Tailored navy blazer with notch lapel, two-button closure, 
front flap pockets, matching wide-leg trousers with pressed crease
```

### Q: 生成的人物与原图不一致怎么办？
A: 使用更详细的人物描述，或尝试图生图模式：
- 增加面部特征描述
- 指定发型、妆容细节
- 使用参考图模式

### Q: 场景看起来不真实怎么办？
A: 增加环境细节和真实感关键词：
```
Realistic environment, natural imperfections, 
authentic textures, not overly staged, 
environmental details that tell a story
```

---

## 注意事项

- **图片比例选择**：产品详情页建议3:4竖图或1:1方图，社交媒体可选多种比例
- **Prompt语言**：使用英文获得最佳效果，中文也可但质量可能略低
- **迭代优化**：首次生成不完美是正常的，通过调整提示词迭代优化
- **版权注意**：生成的图片可用于商业用途，但建议检查平台具体要求
- **真实感优先**：亚马逊买家更喜欢真实、自然的产品展示图

---

## 使用示例

### 示例1：职业装 - 办公场景

**输入**：模特穿职业西装的半身照

**输出提示词**：
```
Modern executive office with large windows overlooking city skyline, 
natural light streaming in, a professional woman in her late 30s 
wearing a charcoal gray tailored blazer and matching pencil skirt, 
confident and poised expression, minimal jewelry, 
styled hair in a sleek updo, soft diffused lighting, 
clean and sophisticated workspace with dark wood furniture, 
medium shot, shallow depth of field with cityscape bokeh, 
high-end fashion photography, realistic skin texture, 
editorial style, empowering and elegant atmosphere
```

### 示例2：休闲装 - 咖啡店场景

**输入**：模特穿针织衫和牛仔裤的全身照

**输出提示词**：
```
Cozy artisan coffee shop with exposed brick walls and warm lighting, 
a friendly woman in her early 30s wearing an oversized cream knit sweater 
and high-waisted dark wash jeans, relaxed and genuine smile, 
natural makeup, loose wavy hair, 
holding a ceramic coffee mug, wooden table with books and plants, 
full body shot, natural light from window, 
lifestyle photography, warm and inviting mood, 
authentic and relatable atmosphere
```

### 示例3：度假装 - 海滩场景

**输入**：模特穿波西米亚连衣裙的半身照

**输出提示词**：
```
Stunning tropical beach at golden hour, soft pink and orange sunset sky, 
a beautiful woman in her mid-30s wearing a flowing bohemian maxi dress 
with floral pattern (exact same dress as source image), 
accessorized with a wide-brimmed straw hat and layered gold shell anklet, 
wearing tan leather flat sandals, 
walking barefoot at water's edge with dress flowing in ocean breeze, 
sun-kissed skin, long wavy hair blowing naturally, 
relaxed and joyful expression, ocean waves gently lapping in background, 
warm golden lighting, medium shot with shallow depth of field, 
romantic and carefree atmosphere, dreamy and serene mood, 
fashion editorial style, natural and authentic feel
```

---

## 更新日志

### v1.2 (2026-06-06)
- 新增「品类构图规则」：按服装类型智能匹配镜头范围（7种品类）
- 核心规则新增第5条：镜头构图必须遵循品类构图规则
- 负向提示词新增 `cropped image, clothing cut off, incomplete outfit, garment partially visible`
- 生图参数改为图片比例（3:4竖图），不再硬编码像素分辨率
- 确保两件套 head-to-knee、连衣裙 head-to-ankle 等按品类自动适配

### v1.1 (2026-05-27)
- 新增"核心生成铁律"：服装和模特为不变量，配饰/鞋子/姿势为变量
- 新增"场景配饰速查表"：覆盖 30+ 场景的配饰、鞋子、姿势、道具推荐
- 更新提示词模板格式：强制包含配饰、鞋子、姿势描述
- 更新所有示例输出，融入场景适配元素
- 强调"服装原样保留"原则，配饰以衬托为目的不喧宾夺主

### v1.0 (2026-05-23)
- 初始版本发布
- 整合四大类场景库
- 提供完整的提示词写作指南
- 包含氛围关键词库和镜头语言参考
- 支持手动和自动场景匹配模式
