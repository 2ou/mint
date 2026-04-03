package com.ai.creative.common;

import com.ai.exception.BusinessException;

public final class CreativeAsserts {
    private CreativeAsserts() {
    }

    public static void notNull(Object obj, String msg) {
        if (obj == null) {
            throw new BusinessException(msg);
        }
    }

    public static void isTrue(boolean val, String msg) {
        if (!val) {
            throw new BusinessException(msg);
        }
    }
}
