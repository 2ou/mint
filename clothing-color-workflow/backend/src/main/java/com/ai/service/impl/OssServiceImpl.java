package com.ai.service.impl;

import com.ai.config.AppProperties;
import com.ai.exception.BusinessException;
import com.ai.service.OssService;
import com.ai.util.FileNameUtil;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.OSSObject;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class OssServiceImpl implements OssService {
    private final OSS oss;
    private final AppProperties appProperties;
    private final OkHttpClient okHttpClient;

    @Override
    public String uploadInput(String spu, String type, MultipartFile file) {
        String safeName = FileNameUtil.safeFileName(file.getOriginalFilename());
        String objectKey = spu + "/" + type + "/" + safeName;
        try (InputStream is = file.getInputStream()) {
            oss.putObject(appProperties.getOss().getInputBucket(), objectKey, is);
            return appProperties.getOss().getInputPublicHost() + "/" + objectKey;
        } catch (IOException e) {
            throw new BusinessException("上传OSS失败: " + e.getMessage());
        }
    }

    @Override
    public String transferResultToOss(String spu, String sourceUrl) {
        String fileName = FileNameUtil.safeFileName("result.png");
        String objectKey = "result/" + spu + "/" + fileName;
        byte[] bytes = downloadByUrl(sourceUrl);
        oss.putObject(appProperties.getOss().getResultBucket(), objectKey, new java.io.ByteArrayInputStream(bytes));
        return appProperties.getOss().getResultPublicHost() + "/" + objectKey;
    }

    @Override
    public byte[] downloadByUrl(String url) {
        Request request = new Request.Builder().url(url).build();
        try (Response response = okHttpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new BusinessException("下载图片失败");
            }
            return response.body().bytes();
        } catch (IOException e) {
            throw new BusinessException("下载图片失败: " + e.getMessage());
        }
    }
}
