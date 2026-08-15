package com.amarmantry.graphops.dto;

import java.util.List;

// Fan-in: a dependency (DB/API) used by more than one service
public record SharedDependencyDto(
        String dependencyName,
        String dependencyType,
        List<String> dependentServices,
        int fanIn
) {}