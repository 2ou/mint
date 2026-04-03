package com.ai.creative.oss;

import java.io.InputStream;

public interface CreativeOssService {
    String uploadInputStream(String objectKey, InputStream inputStream);
    String uploadBytes(String objectKey, byte[] bytes);
    String generateObjectKey(String bizType, String fileName);
}
