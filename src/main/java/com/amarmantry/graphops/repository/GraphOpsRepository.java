package com.amarmantry.graphops.repository;

import com.amarmantry.graphops.dto.*;

import java.util.List;

public interface GraphOpsRepository {

    List<ServiceDto> findAllServices();

    List<ComponentDto> findAllComponents();

    List<DependencyDto> findDirectDependencies(String serviceName);

    BlastRadiusResponseDto findBlastRadius(String serviceName, int maxHops);

    PathTraceResponseDto findShortestPath(String sourceName, String targetName);

    List<SharedDependencyDto> findSharedDependencies(int minFanIn);
}