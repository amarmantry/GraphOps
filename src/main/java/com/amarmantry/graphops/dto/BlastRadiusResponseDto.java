package com.amarmantry.graphops.dto;

import java.util.List;

public record BlastRadiusResponseDto(
        String root,
        List<AffectedNode> affected
) {
    public record AffectedNode(String name, List<String> labels, int hopDistance, boolean anyCriticalHop) {}
}