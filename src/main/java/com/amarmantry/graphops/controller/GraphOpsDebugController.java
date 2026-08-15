package com.amarmantry.graphops.controller;

import com.amarmantry.graphops.dto.*;
import com.amarmantry.graphops.repository.GraphOpsRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * TEMPORARY debug controller — exists only to sanity-check the repository
 * layer against CognoDB before building the real UI in Phase 3.
 * Delete or fold into GraphOpsController once verified.
 */
@RestController
@RequestMapping("/api/debug")
public class GraphOpsDebugController {

    private final GraphOpsRepository repository;

    public GraphOpsDebugController(GraphOpsRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/services")
    public List<ServiceDto> services() {
        return repository.findAllServices();
    }

    @GetMapping("/components")
    public List<ComponentDto> components() {
        return repository.findAllComponents();
    }

    @GetMapping("/dependencies")
    public List<DependencyDto> dependencies(@RequestParam String service) {
        return repository.findDirectDependencies(service);
    }

    @GetMapping("/blast-radius")
    public BlastRadiusResponseDto blastRadius(
            @RequestParam String service,
            @RequestParam(defaultValue = "4") int maxHops) {
        return repository.findBlastRadius(service, maxHops);
    }

    @GetMapping("/shortest-path")
    public PathTraceResponseDto shortestPath(
            @RequestParam String source,
            @RequestParam String target) {
        return repository.findShortestPath(source, target);
    }

    @GetMapping("/shared-dependencies")
    public List<SharedDependencyDto> sharedDependencies(
            @RequestParam(defaultValue = "1") int minFanIn) {
        return repository.findSharedDependencies(minFanIn);
    }
}