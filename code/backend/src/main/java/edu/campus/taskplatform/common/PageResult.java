package edu.campus.taskplatform.common;

import java.util.List;

/**
 * 分页结果，契约见 openapi.yaml 的 PageInfo：items / page / size / total。
 * 空列表是成功响应，返回空 items，不是错误。
 */
public final class PageResult<T> {

    private final List<T> items;
    private final int page;
    private final int size;
    private final long total;

    public PageResult(List<T> items, int page, int size, long total) {
        this.items = items;
        this.page = page;
        this.size = size;
        this.total = total;
    }

    public static <T> PageResult<T> of(List<T> items, int page, int size, long total) {
        return new PageResult<>(items, page, size, total);
    }

    public List<T> getItems() {
        return items;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotal() {
        return total;
    }
}
