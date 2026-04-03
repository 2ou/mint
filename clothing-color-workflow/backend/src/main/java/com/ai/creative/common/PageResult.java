package com.ai.creative.common;

import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class PageResult<T> {
    private long pageNo;
    private long pageSize;
    private long total;
    private List<T> records;

    public static <T> PageResult<T> of(long pageNo, long pageSize, long total, List<T> records) {
        PageResult<T> result = new PageResult<>();
        result.setPageNo(pageNo);
        result.setPageSize(pageSize);
        result.setTotal(total);
        result.setRecords(records == null ? Collections.emptyList() : records);
        return result;
    }
}
