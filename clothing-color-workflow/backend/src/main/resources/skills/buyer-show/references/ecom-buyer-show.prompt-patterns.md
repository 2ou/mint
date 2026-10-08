# 买家秀提示词模块

## 组装顺序

提示词按以下顺序编译：

1. 服装强锁定；
2. 普通顾客与自然体型；
3. 完整脸部与安全裁剪区；
4. 拍摄模式和人物动作；
5. 服装与身体的物理交互；
6. 生活场景和光线；
7. 手机成像特征；
8. 一种受控瑕疵；
9. 负面约束。

## 服装强锁定模块

```text
Use the attached garment image as the strict clothing source of truth. Preserve the exact garment category, coordinated-set structure, color, print or solid-color treatment, waffle or other fabric texture, neckline, sleeves, pocket placement, waistband, drawstring, rise, leg shape, hem length, looseness, proportions, seams, trims, and every visible construction detail. Do not redesign, restyle, recolor, simplify, embellish, tighten, shorten, lengthen, or replace any garment component.
```

如果参考图里有四个颜色，只为当前一张图指定一个颜色，不能混色，也不能自行增加新色。

## 模特参考图姿势重置模块

当输入不是平铺图，而是人物穿着图时，将以下段落紧接在服装强锁定模块之后。它将服装保真与人物、动作解耦：

```text
Use the attached image only as the garment source of truth. Do not inherit, recreate, or closely echo the reference person's identity, face, body, hairstyle, expression, pose, hand placement, camera angle, crop, room, furniture, lighting, accessories, or composition. Create a new ordinary adult wearer in a different home setting with a clearly different body orientation, weight shift, arm placement, and capture angle. Only the garment facts may transfer from the reference image.
```

仅当服装明确有口袋且用户要求展示口袋时，才可加入以下可选动作；没有口袋或未要求展示时，不要加入这一模块：

```text
Show both side-seam pockets clearly without copying the reference pose: one hand lightly touches or enters one pocket, while the other hand is naturally outside the pocket holding a small everyday item, resting on a nearby surface, or hanging relaxed. Keep the opposite pocket opening unobstructed and visibly constructed.
```

## 普通顾客模块

默认普码、自然健康体型：

```text
One ordinary adult female customer with a natural, healthy, balanced build, realistic body proportions, relaxed shoulders, and a casual at-home try-on presence. She is not a professional fashion model, not an influencer, and not posing for a commercial campaign.
```

柔软自然体型：

```text
One ordinary adult female customer with a soft natural build, subtle real-life body variation, realistic waist and hip transitions, and relaxed posture.
```

曲线体型：

```text
One ordinary adult female customer with a naturally curvy build and realistic proportions, avoiding an exaggerated hourglass silhouette.
```

只有用户明确要求大码或参考服装本身为大码时，才使用：

```text
One ordinary adult plus-size female customer with balanced, realistic proportions and natural soft-tissue behavior. Keep the garment fit consistent with the specified plus size without slimming or exaggerating her body.
```

## 完整脸部模块

```text
Keep her entire face visible from the top of her head to her chin. Leave approximately 8–12% clear headroom above her hair for later cropping. Her eyes, nose, mouth, jawline, and chin remain unobstructed. If she holds a phone, place it beside her face, at chest height, or slightly lower; never cover the central facial features. Preserve natural facial asymmetry, subtle skin texture, and a relaxed everyday expression.
```

## 拍摄模式模块

### 镜前自拍

```text
A casual vertical mirror selfie in a real bedroom, dressing area, or bathroom. She holds a normal smartphone beside her face or at upper-chest height, keeps her full face visible, shifts her weight slightly to one leg, and lets the free hand rest naturally near the hip, pocket, hem, or waistband. The mirror reflection, phone orientation, fingers, room geometry, and reflected background are physically consistent.
```

### 门口或走廊随拍

```text
A family-member smartphone photo near a bedroom doorway or hallway. She stands naturally with one knee relaxed, shoulders slightly uneven, and a small unforced expression, as if showing how the outfit fits at home.
```

### 厨房或玄关轻倚

```text
A casual smartphone snapshot in a lived-in kitchen or entryway. She lightly leans against a counter or wall and turns her torso only a few degrees, creating believable folds at the waist, hip, elbow, and underarm.
```

### 沙发或床边坐姿

```text
A relaxed seated smartphone photo on the edge of a sofa or bed. Her posture is comfortable rather than editorial, with believable fabric compression, bunching, and gravity folds at the waist, lap, hip, knees, and seat.
```

### 窗边或阳台

```text
A casual vertical smartphone photo beside a window or on a modest residential balcony. Natural daylight comes from one side and mixes gently with the ambient indoor light; the pose remains ordinary and unperformed.
```

## 服装与身体交互模块

```text
Render physically believable garment-body interaction: correct fabric weight and drape, contact shadows, mild tension at real pressure points, natural bunching at bent joints, gravity-driven vertical folds, and print or texture that follows the changing fabric surface. The garment must sit on the body rather than float above it or appear pasted on.
```

居家服套装可补充：

```text
The top falls naturally over the waistband without becoming tunic-long or cropped. The elastic or drawstring waist sits at the intended rise. The lounge pants keep their specified straight or wide-leg silhouette, with realistic ease through the hip, thigh, knee, and hem.
```

## 家居环境模块

```text
A believable, modest, lived-in home with a few coherent everyday objects such as a folded blanket, bedside lamp, laundry basket, ordinary toiletries, simple cookware, shoes near the entry, or a neutral phone charger. Keep the room usable and visually calm: neither an immaculate luxury showroom nor a chaotic clutter pile.
```

每张图只选两到四种合理物件，且物件要符合所在房间的用途。

## 手机成像模块

```text
Captured as a contemporary smartphone portrait in a vertical 3:4 frame, using a standard or slightly wide phone-lens perspective around 24–28 mm equivalent. Deep depth of field, realistic auto exposure, slightly mixed household-and-window white balance, moderate phone sharpening, natural skin detail, ordinary dynamic range, and subtle JPEG character. No studio polish, no cinematic color grading, and no artificial portrait-mode bokeh.
```

## 受控瑕疵模块

每张图只选一种：

- `a very slight handheld camera tilt`
- `a small, believable amount of indoor phone noise`
- `a mild automatic-exposure bias near the window`
- `subtle consumer JPEG compression`
- `slightly imperfect off-center framing while keeping the full body and face readable`

不要在同一张图里同时加入模糊、重噪点、强欠曝、明显畸变和歪斜构图。

## 完整提示词骨架

```text
[GARMENT LOCK]

[POSE RESET — REQUIRED FOR WORN-MODEL REFERENCE IMAGES]

[ORDINARY CUSTOMER + BODY OPTION]

[FULL-FACE AND CROP-SAFETY LOCK]

[CAPTURE MODE + NATURAL ACTION]

[GARMENT-BODY INTERACTION]

[HOME CONTEXT + LIGHTING]

[SMARTPHONE IMAGING]

Add only this controlled imperfection: [ONE IMPERFECTION].

The final image is one standalone buyer-style apparel photo, not a collage, not a data card, and not a multi-view layout.

Avoid: [BASE NEGATIVE + SCENE-SPECIFIC NEGATIVE].
```

## 基础负面约束

```text
professional fashion campaign, runway pose, editorial pose, influencer styling, celebrity likeness, heavy glam makeup, beauty-filter face, plastic skin, waxy skin, exaggerated pores, exaggerated hourglass body, slimming, body distortion, studio seamless background, luxury showroom staging, cinematic lighting, dramatic rim light, shallow depth of field, artificial portrait-mode bokeh, 8K HDR look, oversharpening, excessive noise, intentional blur, extreme lens distortion, incorrect garment, changed color, changed print, changed texture, missing pocket, added trim, altered neckline, altered sleeve length, altered pant shape, floating fabric, pasted-on texture, impossible folds, body-garment mismatch, cropped face, cut-off head, cut-off chin, phone covering face, distorted hands, extra fingers, impossible mirror reflection, duplicated objects, warped furniture, unreadable room geometry, collage, split screen, contact sheet, data card, text, logo, watermark, star rating, review text, Verified Purchase badge, order screenshot, customer username, or invented testimonial
```

对于模特参考图，在上述基础负面约束后追加：

```text
copying the reference person, same face, same hairstyle, same pose, same both-hands-in-pockets gesture, same hand placement, same camera angle, same crop, same room, same furniture layout, same lighting direction, same background composition
```

## 场景专项负面约束

镜前自拍补充：

```text
phone blocking the eyes, nose, or mouth; mismatched phone and reflection; duplicated hands; impossible mirror seams; camera visible outside the reflection
```

坐姿补充：

```text
floating hips, missing seat compression, impossible knee bend, fabric passing through the body, pants turning into a skirt-like mass
```

门口、厨房或阳台补充：

```text
warped doorframe, bent counter edge, impossible railing, miniature furniture, luxury-hotel staging, fashion-set lighting
```

## 多图变化规则

同一组图片保持服装完全一致，同时至少改变以下五项：

- 人物身份与脸型；
- 发型、发色或日常妆感；
- 身高和自然体型；
- 房间类型或住宅风格；
- 拍摄者与手机位置；
- 站姿、坐姿或生活动作；
- 光线方向和时间感；
- 唯一受控瑕疵。

不要只改变墙面颜色，也不要把同一张脸重复放入不同住宅中。

## 输出前质量门槛

- 服装颜色、纹理、结构、长度、宽松度与参考图一致；
- 默认普码，只有用户明确要求时才切换大码；
- 完整脸部、头顶和下巴可见，并保留 8%–12% 头顶空间；
- 手机不遮五官；
- 人物像普通顾客，不像职业模特或广告主角；
- 身体压力点、褶皱、接触阴影和布料重力方向一致；
- 房间物件与空间用途吻合；
- 反射、手指、门框、家具透视合理；
- 一张图只有一个受控手机瑕疵；
- 没有拼贴、文字、星级、评论、购买徽章、用户名或虚构背书元素。
