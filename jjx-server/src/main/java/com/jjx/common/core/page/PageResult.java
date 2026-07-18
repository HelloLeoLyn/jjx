package com.jjx.common.core.page;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 分页数据封装
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private long total;
    private List<T> records;
    private Integer pageNum;
    private Integer pageSize;
    private Integer totalPages;

    public PageResult(List<T> list, long total) {
        this.records = list;
        this.total = total;
    }

    public static <T> PageResult<T> build(List<T> list, long total) {
        return new PageResult<>(list, total);
    }

    public static <T> PageResult<T> build() {
        return new PageResult<>();
    }
}
