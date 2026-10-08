---
name: women-lookbook-styling
description: Plan and generate women's fashion lookbooks, outfit styling, shot lists, image prompts, and lifestyle fashion images from garment, model, scene, or style references. Use when users ask for women's fashion lookbooks, ecommerce lifestyle imagery, campaign outfit styling, fashion scene prompts, or a set of coordinated fashion shots. Preserve supplied garment details and model identity when requested. Do not use for ID photos, generic portraits without a fashion objective, simple background removal, or unrelated product imagery.
---

# Women's Lookbook Styling

Create coherent, product-faithful women's fashion lookbooks. The default canvas is vertical **3:4**; change it only when the user explicitly requests another ratio.

## Modes

Choose the smallest requested mode:

- `prompt-only`: create a lookbook plan, one standalone prompt per shot, and targeted avoid terms.
- `shot-plan`: create the plan without generation prompts unless the user asks for them.
- `generate-images`: create the plan, generate one image per approved shot with the built-in image-generation tool, then inspect each output.

## Input Roles and Locked Variables

Classify every supplied image before planning:

- **Garment reference**: source of truth for the specified clothing slots only.
- **Model reference**: source of truth for identity, hair, complexion, and body proportions only when the user asks to preserve the model.
- **Scene/style reference**: source of truth for composition, mood, light, and environment; do not copy readable text, logos, or watermarks.
- **Edit target**: preserve everything except the user-named change.

With a garment reference, read [garment-and-reference-rules.md](references/garment-and-reference-rules.md) before writing prompts. List the locked garment facts first. Do not change a locked garment's silhouette, color, print, neckline, sleeve length, closures, hem length, fabric, or fit. User instructions resolve conflicts between references.

## Workflow

1. Extract the brief: product category, target audience, season, platform, count, scene, and requested ratio. Default to one 3:4 vertical hero image when count is unspecified.
2. Record the reference roles and locked variables. Ask one concise question only if the requested change conflicts with an unclear reference role.
   - When a multi-image brief requires the same model but no model reference exists, define a short, neutral adult model profile and repeat it verbatim in every prompt as a consistency target. State that strict identity consistency requires a supplied model reference, or a generated hero image reused as the model reference for later shots.
3. Select a lookbook story and shots. Read [lookbook-recipes.md](references/lookbook-recipes.md) only when suggesting scenes or building a multi-image set.
4. Apply category-safe framing. Keep the selling details and relevant footwear inside the frame. Use the default 3:4 vertical canvas unless overridden.
5. Write one complete, independent prompt per image: scene -> adult fashion model -> locked garment -> styling -> pose -> composition -> lighting -> materials -> constraints. Do not use "same outfit as above."
6. For `generate-images`, use the built-in image-generation tool. Generate each distinct shot separately and label every input image by role in the prompt.
7. Before delivery, read [quality-checklist.md](references/quality-checklist.md) and verify the garment, identity, anatomy, framing, materials, and image-set variety. Make at most one targeted correction per failed shot before reporting the limitation.

## Prompt Rules

- Use `adult fashion model`, `editorial fashion model`, or another specific fashion-model subject; never reduce the subject to a generic "woman."
- For people in real scenes, specify physical integration: feet on the surface, contact shadows, shared light direction, matching perspective, and realistic depth of field.
- Include concrete material behavior: knit ribbing, woven cotton, satin highlights, lace openings, leather sheen, or fabric drape as relevant.
- Keep accessories subordinate to the garment. Add them only when they support the setting or user brief.
- Use natural skin texture and believable light; avoid plastic skin, mannequin poses, generic beauty-filter faces, unreadable text, watermarks, and brand logos.
- For multiple images, vary scene, pose, camera angle, or lighting while keeping all locked product facts unchanged.

## Output Contract

For plans or prompts, return:

```json
{
  "format": "3:4 vertical",
  "brief": "...",
  "reference_roles": ["..."],
  "locked_variables": ["..."],
  "shots": [
    {
      "name": "...",
      "purpose": "...",
      "composition": "...",
      "prompt": "...",
      "avoid": "..."
    }
  ],
  "quality_check": ["..."]
}
```

For generated images, also return the saved image path and a short note on any unresolved visual limitation. When a multi-image set has no model reference, note that repeated model traits are a consistency target rather than a guarantee. Keep the response concise when the user asks to generate images directly.

## Resources

- [garment-and-reference-rules.md](references/garment-and-reference-rules.md): garment locking, reference precedence, and category framing.
- [lookbook-recipes.md](references/lookbook-recipes.md): reusable lookbook narratives and shot rhythms.
- [quality-checklist.md](references/quality-checklist.md): final visual acceptance checks.
