package com.permacore.iam.domain.vo;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

public class PageVO<T> {
    private long total;
    private List<T> records;

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public static <T> PageVO<T> of(Page<T> page) {
        PageVO<T> vo = new PageVO<>();
        vo.setTotal(page.getTotal());
        vo.setRecords(page.getRecords());
        return vo;
    }
}
