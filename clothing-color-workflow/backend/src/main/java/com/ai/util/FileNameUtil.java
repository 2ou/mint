package com.ai.util;

import java.text.Normalizer;
import java.util.UUID;

public class FileNameUtil {
    private FileNameUtil() {}

    public static String safeFileName(String original) {
        String name = original == null ? "file" : original;
        name = Normalizer.normalize(name, Normalizer.Form.NFKC)
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", "_");
        int dotIndex = name.lastIndexOf('.');
        String ext = dotIndex > 0 ? name.substring(dotIndex) : ".png";
        String prefix = dotIndex > 0 ? name.substring(0, dotIndex) : name;
        prefix = prefix.replaceAll("[^\\p{L}\\p{N}_-]", "_");
        if (prefix.length() > 50) {
            prefix = prefix.substring(0, 50);
        }
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "") + ext;
    }
}
