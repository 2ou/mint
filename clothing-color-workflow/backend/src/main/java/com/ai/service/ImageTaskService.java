package com.ai.service;

import com.ai.dto.TaskCreateResponse;
import com.ai.entity.ImageTask;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

public interface ImageTaskService {
    TaskCreateResponse create(String spu, String prompt, String resolution, MultipartFile inputFile, MultipartFile colorFile);
    ImageTask refresh(Long id);
    ImageTask detail(Long id);
    Page<ImageTask> list(int page, int size);
    byte[] download(Long id);
}
