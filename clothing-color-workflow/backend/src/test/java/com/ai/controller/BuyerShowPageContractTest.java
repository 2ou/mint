package com.ai.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuyerShowPageContractTest {

    @Test
    void imageTasksKeepTheirReferenceImageAndPollModelLibraryFields() throws IOException {
        String html = loadBuyerShowPage();

        assertTrue(html.contains("imageUrl: sourceFile?.ossUrl || sourceFile?.url"));
        assertTrue(html.contains("clothingImageUrl: prompt.imageUrl"));
        assertTrue(html.contains("dbId: task.id"));
        assertTrue(html.contains("status: task.taskStatus || t.status"));
        assertTrue(html.contains("task?.resultUrl"));
    }

    @Test
    void gptImage25PreventsUnsupportedFourByFiveRatio() throws IOException {
        String html = loadBuyerShowPage();

        assertTrue(html.contains(":disabled=\"imageModel==='gpt-image-2-5-sunburst'\""));
        assertTrue(html.contains("GPT Image 2.5 不支持 4:5，已自动切换为 3:4"));
    }

    private String loadBuyerShowPage() throws IOException {
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("static/buyer-show.html")) {
            assertNotNull(stream, "buyer-show.html should be available on the test classpath");
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
