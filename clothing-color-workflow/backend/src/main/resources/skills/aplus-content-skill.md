# A+ Content Image Brief Skill

Use this skill for women's fashion A+ or detail-page module image planning.

## Core Objective

Create a coherent 7-module A+ visual system that helps shoppers understand:

- What the garment is.
- Why the fabric, print, fit, and details matter.
- Where and how the customer can wear it.
- How the garment should be cared for.

The output is not a final image prompt. It is a production brief that will be
combined with the reference image and sent to an image-to-image model.

Preferred primary reference: product-only flat-lay, ghost-mannequin, or
white-background product image. This is more reliable than cropped model photos
for A+ module generation.

## Stable Layout Reference Mode

When a full A+ page, competitor module, or long infographic is provided as a
layout reference, treat it as structure guidance only:

- Generate one standalone 21:9 web AD module at a time. Do not generate the full
  long page in a single image.
- Use the layout reference only for section hierarchy, card rhythm, rounded
  panels, image crop style, text placement, and information density.
- Never copy the reference page's garment, model gender, faces, body type,
  exact pose, product color, brand logo, background identity, or exact photos.
- The primary product reference image always wins for garment appearance.
- AD-01 maps to the hero block, AD-04 maps to multi-scenario cards, AD-03 maps
  to detail cards, AD-06 maps to the size chart, and AD-07 maps to styling/care
  or brand closing.

## Product Fidelity Rules

- Preserve the reference garment exactly: category, silhouette, print, fabric,
  color family, neckline, sleeve, hem, waist, trims, and visible construction.
- Never invent a new pattern, new fabric, new garment type, logo, label, zipper,
  pocket, button, or accessory unless the user explicitly provided it.
- If the reference image conflicts with the selling points, the reference image
  wins for visual appearance.
- If the only reference is a cropped model/product photo, preserve the visible
  garment crop and avoid inventing unavailable product areas unless the user
  provided enough supplemental references.
- If the primary reference is a product-only flat-lay or white-background image,
  treat it as the garment source of truth, not as a product-only output
  restriction. Model-worn modules must dress the model in the exact same
  garment; product-detail modules should stay product-focused.

## Model And Scene Rules

- AD-01, AD-04, and AD-05 may generate realistic American models and lifestyle
  scenes when useful.
- For plus-size, flowy, V-neck, tunic, babydoll, or coverage-focused tops,
  default to Curve / Plus-Size or Commercial / Catalog American women.
- Use mature adult models, usually age 30-42, with natural skin texture,
  body-positive confidence, and approachable American styling.
- Use authentic American scenes: suburban home, modern apartment, coffee shop,
  casual office, NYC/LA street, garden/patio, weekend errands.
- Avoid Chinese architecture, Chinese furniture, influencer poses, porcelain
  skin, stiff studio glamour, over-retouching, and ultra-thin model bodies for
  plus-size garments.

## Module Roles

- AD-01 Brand hero: premium first impression with a realistic American model or
  clean catalog hero. If the source is flat-lay or white-background, generate a
  model wearing the exact garment in a restrained studio/lifestyle banner.
- AD-02 Fabric and print story: split composition with product view and macro
  texture/print detail.
- AD-03 Design details: product-centered layout with controlled close-up inset
  panels for real details. For flat-lay references, use product-only detail
  crops.
- AD-04 Multi-scenario styling: create three authentic American lifestyle panels
  with a consistent model type wearing the exact garment in different everyday
  scenarios. Use product-only panels only if the user asks to avoid models.
- AD-05 Comfort and wearability: model-worn comfort proof plus fabric/fit detail;
  emphasize drape, coverage, movement, and flattering fit.
- AD-06 Fit guide: front garment presentation, measurement arrows, and readable
  size chart. Use exact numeric measurements when the user provides them in the
  product information or AD-06 module instructions, usually with columns such as
  Size, Bust, Length, and Sleeve. If measurements are missing, use size labels
  and fit guidance instead of fake numbers.
- AD-07 Care and closing: folded/static product composition, calm brand finish,
  and readable care/quality explanation text.

## Quality Standard

Every module should feel like a finished premium e-commerce detail-page image:
clean composition, realistic fabric, commercial lighting, controlled spacing,
clear hierarchy, and useful buyer information. Avoid generic fashion editorial
images that do not explain a product benefit.

## Text Rules

- Every final A+ image should include useful short English text explaining that
  module's buyer benefit.
- All visible text inside final A+ images must be English only. Do not render
  Chinese characters, Chinese labels, bilingual captions, or untranslated
  Chinese user text.
- AD-01 needs a headline and subline.
- AD-02 needs fabric/feel labels.
- AD-03 needs design-detail labels.
- AD-04 needs short scenario captions.
- AD-05 needs comfort/fit labels.
- AD-06 needs a size chart when size data is provided; otherwise it needs
  size/fit guidance.
- AD-07 needs care/quality explanation text.
- Keep image-rendered text short, high-contrast, and easy to read. Prefer labels
  and short bullet lines over paragraphs.
- Do not invent numeric size data. If the user provides measurements, reproduce
  them exactly. If the user did not provide measurements, write fit guidance
  instead of a fake size table.
