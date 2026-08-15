package com.amarmantry.graphops.dto;

import java.util.List;

public record PathTraceResponseDto(
        String source,
        String target,
        boolean pathExists,
        List<String> nodeNamesInOrder,
        List<String> relationshipTypesInOrder,
        int hopCount
) {}