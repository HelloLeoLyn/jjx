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

    /**
     * 完整分页构造（带 pageNum/pageSize/totalPages，修复分页信息缺失）
     */
    public static <T> PageResult<T> of(com.baomidou.mybatisplus.core.metadata.IPage<?> page, List<T> records) {
        PageResult<T> r = new PageResult<>();
        r.setRecords(records);
        r.setTotal(page.getTotal());
        r.setPageNum((int) page.getCurrent());
        r.setPageSize((int) page.getSize());
        r.setTotalPages((int) page.getPages());
        return r;
    }
}
