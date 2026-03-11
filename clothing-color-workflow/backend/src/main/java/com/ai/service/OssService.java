package com.ai.service;

import org.springframework.web.multipart.MultipartFile;

public interface OssService {
    String uploadInput(String spu, String type, MultipartFile file);
    String transferResultToOss(String spu, String sourceUrl);
    byte[] downloadByUrl(String url);
}
