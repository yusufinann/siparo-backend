package com.siparo.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** Sayfalı yanıt sözleşmesi. */
public record PageDto<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <E, T> PageDto<T> of(Page<E> page, Function<List<E>, List<T>> mapper) {
        return new PageDto<>(mapper.apply(page.getContent()), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
