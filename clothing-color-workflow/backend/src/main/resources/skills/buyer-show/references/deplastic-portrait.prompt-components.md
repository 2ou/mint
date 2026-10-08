# 提示词组件与模板

## 使用原则

真人感来自可解释的微小变化。每个类别最多选 1–2 个词组；“所有瑕疵一次上齐”常会产生脏脸、过度疲惫或不稳定的五官。人像参考图存在时，不要用“不对称五官”强改人物，改用“preserve the subject's natural facial asymmetry”。

| 控制层 | 可选词组 | 选择规则 |
| --- | --- | --- |
| 皮肤 | `visible natural skin texture`, `subtle pores`, `slight T-zone sheen`, `faint under-eye shadows`, `subtle redness around the nostrils`, `a few faint freckles` | 选 1–2 项；肤色/年龄不明时避开明显色沉、严重黑眼圈或大量雀斑。 |
| 五官与头发 | `preserve natural facial asymmetry`, `slightly uneven eyebrow density`, `irregular natural lash lengths`, `softly defined lip edge`, `fine baby hairs`, `a few wind-tossed flyaway hairs` | 参考图优先保脸；发丝必须有风、湿度或动作原因。 |
| 肢体与手部 | `relaxed shoulders with a slight weight shift`, `anatomically correct hands, five fingers on each hand`, `clear knuckle creases`, `natural wrist folds` | 只在手入镜时加入；避免把血管、褶皱写得过度。 |
| 衣物与道具 | `natural fabric tension and uneven folds`, `slightly softened edges`, `minor scratches on metal hardware`, `subtle water marks on the vase` | 只为可见物体添加 1 项磨损；保持干净、可用。 |
| 成像 | `subtle film grain`, `gentle edge falloff`, `slight highlight bloom`, `natural lens distortion`, `soft motion in loose hair` | 选一套成像语汇；主体眼睛和脸仍应清晰。 |

## 按场景写光影

光线要包含光源、方向、遮挡/阴影和一种环境反射；不要全脸均匀补光。

- **林荫街拍**：`hard midday sunlight broken by leaf shadows across one side of the face, deeper eye-socket and under-nose shadows, subtle green foliage bounce along the cheek`。
- **白墙窗边**：`cool window side light, the window frame leaves a soft partial shadow across the jaw, faint neutral-white wall bounce on the shadow side`。
- **傍晚街头**：`low warm side-back light, one cheek catches muted orange pavement reflection, restrained shadow detail on the far side of the face`。
- **室内自然光**：`single large window as the key light, gradual falloff across the face, soft room-tone bounce, no beauty-dish fill`。

不要把树影、暖色反光或逆光轮廓光放到与场景不相符的画面。不要同时写 `iPhone original`, `Kodak Portra 400`, `RAW photo`；从下面选择一个结尾即可：

- 日常手机：`unfiltered smartphone photo, native white balance, subtle sensor noise`。
- 胶片街拍：`35mm candid portrait, Kodak Portra 400 color response, subtle film grain, gentle highlight roll-off`。
- 数码人像：`85mm portrait lens, shallow depth of field, natural lens falloff, restrained color grading`。

## 主提示词骨架

```text
[主体与保留项], [非模板化表情和动作], [少量皮肤细节],
[少量头发/五官细节], [正确手部或身体重心（入镜时）],
[场景中有因果的光影和环境反光], [衣物/道具的自然褶皱或轻微使用痕迹],
[非居中或带前景遮挡的构图], [只选一种成像语言].
```

### 可直接改写的示例

```text
Photorealistic candid portrait of an adult woman leaning lightly against a sunlit white wall, preserving her natural facial features and hairstyle; a relaxed, slightly unfocused gaze with lips gently parted; visible natural skin texture, subtle pores and a slight T-zone sheen, faint under-eye shadows; fine baby hairs and a few wind-tossed flyaway strands; relaxed shoulders with a slight weight shift, anatomically correct visible hand with five fingers and clear knuckle creases; cool window side light with gradual falloff, a soft partial jaw shadow and faint neutral-white wall bounce on the shadow side; cotton shirt with natural fabric tension and uneven folds; subject placed off-center with a softly blurred passerby partly entering the foreground; 85mm portrait lens, shallow depth of field, natural lens falloff, restrained color grading.
```

## 负向提示词

仅在模型支持时使用，并按实际问题删减：

```text
plastic skin, airbrushed skin, porcelain-smooth face, excessive skin glow, symmetrical doll-like facial features, beauty filter, CGI render, waxy texture, over-sharpened edges, unnaturally uniform lighting, stiff centered pose, extra fingers, missing fingers, fused fingers, malformed hands, perfectly flat fabric
```

## 生成后的轻量微调

1. 在非破坏性图层上添加细颗粒；醒图/PS 可从 5–12 起试，放大查看后停止，避免全屏脏噪点。
2. 只局部加深眼窝、鼻底和下颌的自然阴影，顺着已有光源，不要画出硬脏边。
3. 皮肤总体微降饱和度；在鼻翼或脸颊少量加入与肤色一致的泛红，避免片状红血丝。
4. 用细软笔补极少量碎发，方向遵循发根、风向和景深；远离轮廓的发丝可略虚。
5. 适度降低锐化或纹理清晰度，优先压低异常的边缘锐利感，不牺牲眼睛、睫毛和服装关键细节。

## 最终检查

- 主光方向能否同时解释鼻影、下颌影、衣物影和背景阴影？
- 环境反光颜色是否来自附近的墙、植被、路面或窗户？
- 手掌和每只可见手的五根手指是否解剖正确？
- 瑕疵是否只是在近看时增加质感，而非第一眼就显得脏、老或病态？
- 是否避免了居中对称、全脸同亮度、平整布料和无磨损道具同时出现？
