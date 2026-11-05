package edu.campus.taskplatform.common.web;

import edu.campus.taskplatform.common.error.ApiException;
import edu.campus.taskplatform.common.error.ErrorCode;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 列表接口统一分页规则：page 从 0 开始，size 只接受 10、20、50，默认 0/10。
 */
public final class Pagination {

	public static final int DEFAULT_PAGE = 0;
	public static final int DEFAULT_SIZE = 10;

	private Pagination() {
	}

	public static Pageable of(int page, int size) {
		return of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
	}

	public static Pageable of(int page, int size, Sort sort) {
		if (page < 0) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "page 必须大于等于 0");
		}
		if (size != 10 && size != 20 && size != 50) {
			throw new ApiException(ErrorCode.VALIDATION_ERROR, "size 只允许 10、20 或 50");
		}
		return PageRequest.of(page, size, sort);
	}
}
