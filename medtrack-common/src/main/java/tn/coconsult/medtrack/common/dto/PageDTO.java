package tn.coconsult.medtrack.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PageDTO<T>(
        List<T> content,
        int page,
        int size,
        int totalPages,
        long totalElements
) {

    public static <E, D> PageDTO<D> of(Page<E> pageResult, Function<E, D> mapper) {
        return new PageDTO<>(
                pageResult.getContent().stream().map(mapper).toList(),
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalPages(),
                pageResult.getTotalElements()
        );
    }
}
