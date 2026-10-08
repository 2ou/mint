# Garment and Reference Rules

## Reference Precedence

Resolve conflicts in this order:

1. Explicit user instructions
2. Named locked variables
3. The garment reference for the selected garment slots
4. The model reference for requested identity traits
5. The scene or style reference for visual language
6. Reasonable styling additions

Do not treat a model reference as a garment reference unless the user says that the model's outfit must also be preserved.

## Garment-Fact Capture

For every locked item, record only visible or user-supplied facts:

- category and layers
- color and print scale
- neckline, sleeves, closures, waistline, pockets, and trim
- fabric, finish, drape, and visible texture
- hem or inseam length, silhouette, and fit

Do not invent unseen back details, labels, fiber content, or size.

## Category-Safe Framing

| Product focus | Minimum framing |
| --- | --- |
| Top, blouse, knit, outerwear | Head to hip; neckline, sleeves, and hem visible |
| Bottom, skirt | Waist/hip to ankle; waistband and hem visible |
| Dress, jumpsuit | Head to ankle |
| Coordinated two-piece look | Head to knee or full body |
| Footwear-led look | Full body with both shoes visible |

Use 3:4 vertical by default. Preserve breathing room around the silhouette; do not crop product-defining seams, trim, hands, or footwear.

## Styling Permissions

Add a hat, bag, jewelry, socks, shoes, or prop only if it is not locked and supports the brief. Keep it visually secondary. If an accessory changes the garment's wearability or obscures a selling detail, omit it.

## Prompt Invariants

When garment fidelity matters, state:

```text
Use the garment reference as the strict source of truth for [selected slots]. Preserve the exact visible color, print, silhouette, construction, length, fabric behavior, and fit. Change only the requested model, pose, setting, camera, or styling variables.
```

When model identity matters, state separately:

```text
Use the model reference as the identity source of truth. Preserve the adult model's recognizable face, complexion, hair, and body proportions; do not transfer the source outfit unless it is explicitly locked.
```
