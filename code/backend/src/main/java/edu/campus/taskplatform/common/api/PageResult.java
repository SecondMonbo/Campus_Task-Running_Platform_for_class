package edu.campus.taskplatform.common.api;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * 分页响应体，与《API约定.md》第 3 节的 {@code items/page/size/total} 结构一致。
 */
public record PageResult<T>(List<T> items, int page, int size, long total) {

	public static <S, T> PageResult<T> of(Page<S> source, Function<S, T> mapper) {
		return new PageResult<>(
				source.getContent().stream().map(mapper).toList(),
				source.getNumber(),
				source.getSize(),
				source.getTotalElements());
	}
}
