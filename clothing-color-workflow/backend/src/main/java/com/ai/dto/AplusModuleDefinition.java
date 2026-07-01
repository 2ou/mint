package com.ai.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A+ module definitions used by copy and image generation prompts.
 */
public class AplusModuleDefinition {

    public static final Map<String, String> MODULES = new LinkedHashMap<>();
    static {
        MODULES.put("AD-01", "Brand Hero");
        MODULES.put("AD-02", "Fabric Story");
        MODULES.put("AD-03", "Design Details");
        MODULES.put("AD-04", "Scenario Styling");
        MODULES.put("AD-05", "Comfort Experience");
        MODULES.put("AD-06", "Fit And Size Guide");
        MODULES.put("AD-07", "Care And Closing");
    }

    public static final String STYLE_ANCHOR =
            "Use one consistent A+ visual system across all modules: same product identity, same garment fidelity, "
                    + "same lighting family, same premium e-commerce tone, same spacing rhythm, same border radius logic, "
                    + "same typography scale, and product-adaptive neutral color palette.";

    public static final Map<String, String> VISUAL_POSITIONS = new LinkedHashMap<>();
    static {
        VISUAL_POSITIONS.put("AD-01",
                "Wide hero banner with a strong product/model anchor, clear headline area, and premium first impression.");
        VISUAL_POSITIONS.put("AD-02",
                "Split fabric story: product cutout or folded product on one side, macro fabric/detail crop on the other, with concise material labels.");
        VISUAL_POSITIONS.put("AD-03",
                "Product-centered detail layout with 3-4 close-up inset panels, thin connector lines, and labels tied to real visible details.");
        VISUAL_POSITIONS.put("AD-04",
                "Three scenario cards showing consistent garment identity across everyday American lifestyle situations, each with a short caption.");
        VISUAL_POSITIONS.put("AD-05",
                "Comfort proof layout with model-worn or fit-focused image, one fabric/fit detail inset, and concise comfort labels.");
        VISUAL_POSITIONS.put("AD-06",
                "Technical fit guide with product view, measurement arrows, and a compact size chart only when exact size data is supplied.");
        VISUAL_POSITIONS.put("AD-07",
                "Care and closing module with folded product or still-life arrangement plus short care/quality explanation text.");
    }

    public static final Map<String, String> EXTRA_HINTS = new LinkedHashMap<>();
    static {
        EXTRA_HINTS.put("AD-01", "Brand tone, target buyer, hero headline direction, optional model preference.");
        EXTRA_HINTS.put("AD-02", "Fabric composition, hand feel, weight, stretch, breathability, print or texture notes.");
        EXTRA_HINTS.put("AD-03", "Specific visible details such as neckline, buttons, sleeve, hem, seam, pocket, or stitching.");
        EXTRA_HINTS.put("AD-04", "Scenario names and styling direction such as coffee run, workday casual, weekend errands, or travel.");
        EXTRA_HINTS.put("AD-05", "Comfort, coverage, drape, movement, fit, or body-type guidance.");
        EXTRA_HINTS.put("AD-06", "Exact size data. Do not provide approximate numbers unless they are approved product measurements.");
        EXTRA_HINTS.put("AD-07", "Care instructions, washing method, drying notes, quality promise, or final brand value points.");
    }
}
