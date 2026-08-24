package com.ai.dto;

import lombok.Data;

/**
 * A typed image input for an A+ module. The role is persisted so prompts and
 * KIE image ordering never have to infer what a reference image represents.
 */
@Data
public class AplusReferenceImage {
    public static final String PRODUCT_TRUTH = "PRODUCT_TRUTH";
    public static final String LAYOUT = "LAYOUT";
    public static final String SUPPLEMENTARY = "SUPPLEMENTARY";
    public static final String DETAIL = "DETAIL";
    public static final String FABRIC = "FABRIC";
    public static final String SIZE = "SIZE";
    public static final String SCENE = "SCENE";
    public static final String STYLE_ANCHOR = "STYLE_ANCHOR";

    private String role;
    private String url;
    private String note;
}
