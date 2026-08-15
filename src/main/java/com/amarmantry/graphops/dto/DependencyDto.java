package com.amarmantry.graphops.dto;

public record DependencyDto(
        String source,
        String target,
        String relationshipType,
        String protocolOrOperation,
        Integer latencyMs,
        Boolean critical
) {}