package com.amarmantry.graphops.controller;

import com.amarmantry.graphops.dto.*;
import com.amarmantry.graphops.repository.GraphOpsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class GraphOpsViewController {

    private static final Logger logger = LoggerFactory.getLogger(GraphOpsViewController.class);
    private static final int MAX_COMPONENT_NAME_LENGTH = 100;

    private final GraphOpsRepository repository;

    public GraphOpsViewController(GraphOpsRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/")
    public String dashboard(@RequestParam(required = false) String service, Model model) {
        model.addAttribute("pageTitle", "Dependencies");
        model.addAttribute("content", "dashboard :: content");

        List<ServiceDto> services = repository.findAllServices();
        model.addAttribute("services", services != null ? services : List.of());
        model.addAttribute("dbError", false);

        if (isValidComponentName(service)) {
            logger.info("Fetching direct dependencies for service: {}", service);
            List<DependencyDto> dependencies = repository.findDirectDependencies(service);
            model.addAttribute("selectedService", service);
            model.addAttribute("dependencies", dependencies != null ? dependencies : List.of());
        }

        return "layout";
    }

    @GetMapping("/blast-radius")
    public String blastRadius(@RequestParam(required = false) String service,
                              @RequestParam(defaultValue = "4") int maxHops,
                              Model model) {
        model.addAttribute("pageTitle", "Blast Radius");
        model.addAttribute("content", "blast-radius :: content");

        List<ServiceDto> services = repository.findAllServices();
        model.addAttribute("services", services != null ? services : List.of());
        model.addAttribute("dbError", false);

        if (isValidComponentName(service)) {
            logger.info("Analyzing blast radius for service: {} (maxHops: {})", service, maxHops);
            BlastRadiusResponseDto result = repository.findBlastRadius(service, maxHops);
            model.addAttribute("selectedService", service);
            if (result != null) {
                model.addAttribute("blastRadius", result);
            }
        }

        return "layout";
    }

    @GetMapping("/explore")
    public String explore(@RequestParam(required = false) String source,
                          @RequestParam(required = false) String target,
                          Model model) {
        model.addAttribute("pageTitle", "Explore");
        model.addAttribute("content", "explore :: content");

        List<ComponentDto> components = repository.findAllComponents();
        model.addAttribute("components", components != null ? components : List.of());

        List<SharedDependencyDto> bottlenecks = repository.findSharedDependencies(1);
        model.addAttribute("bottlenecks", bottlenecks != null ? bottlenecks : List.of());
        model.addAttribute("dbError", false);

        if (isValidComponentName(source) && isValidComponentName(target)) {
            logger.info("Tracing path from {} to {}", source, target);
            PathTraceResponseDto path = repository.findShortestPath(source, target);
            model.addAttribute("selectedSource", source);
            model.addAttribute("selectedTarget", target);
            if (path != null) {
                model.addAttribute("pathResult", path);
            }
        }

        return "layout";
    }

    /**
     * Validates component names to prevent injection attacks and ensure safety.
     * Names must be non-blank and under MAX_COMPONENT_NAME_LENGTH.
     */
    private boolean isValidComponentName(String name) {
        return name != null && !name.isBlank() && name.length() <= MAX_COMPONENT_NAME_LENGTH;
    }

    @GetMapping("/architecture")
    public String architecture(Model model) {
        model.addAttribute("pageTitle", "System Architecture");
        model.addAttribute("content", "architecture :: content");
        return "layout";
    }
}