package com.ai.dto;

import lombok.Data;
import java.util.List;

@Data
public class BatchIdRequest {
    private List<Long> ids;
}