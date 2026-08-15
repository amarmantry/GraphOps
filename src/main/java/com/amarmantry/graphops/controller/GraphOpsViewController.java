package com.amarmantry.graphops.controller;

import com.amarmantry.graphops.dto.*;
import com.amarmantry.graphops.repository.GraphOpsRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class GraphOpsViewController {

    private final GraphOpsRepository repository;

    public GraphOpsViewController(GraphOpsRepository repository) {
        this.repository = repository;
    }

    // ---------- Page 1: Dashboard / Dependencies ----------
    @GetMapping("/")
    public String dashboard(@RequestParam(required = false) String service, Model model) {
        model.addAttribute("pageTitle", "Dependencies");
        model.addAttribute("content", "dashboard :: content");

        try {
            List<ServiceDto> services = repository.findAllServices();
            model.addAttribute("services", services);
            model.addAttribute("dbError", false);

            if (service != null && !service.isBlank()) {
                List<DependencyDto> dependencies = repository.findDirectDependencies(service);
                model.addAttribute("selectedService", service);
                model.addAttribute("dependencies", dependencies);
            }
        } catch (Exception e) {
            model.addAttribute("dbError", true);
        }

        return "layout";
    }

    // ---------- Page 2: Blast Radius ----------
    @GetMapping("/blast-radius")
    public String blastRadius(@RequestParam(required = false) String service,
                               @RequestParam(defaultValue = "4") int maxHops,
                               Model model) {
        model.addAttribute("pageTitle", "Blast Radius");
        model.addAttribute("content", "blast-radius :: content");

        try {
            List<ServiceDto> services = repository.findAllServices();
            model.addAttribute("services", services);
            model.addAttribute("dbError", false);

            if (service != null && !service.isBlank()) {
                BlastRadiusResponseDto result = repository.findBlastRadius(service, maxHops);
                model.addAttribute("selectedService", service);
                model.addAttribute("blastRadius", result);
            }
        } catch (Exception e) {
            model.addAttribute("dbError", true);
        }

        return "layout";
    }

    // ---------- Page 3: Explore (path trace + bottlenecks) ----------
    @GetMapping("/explore")
    public String explore(@RequestParam(required = false) String source,
                           @RequestParam(required = false) String target,
                           Model model) {
        model.addAttribute("pageTitle", "Explore");
        model.addAttribute("content", "explore :: content");

        try {
            List<ComponentDto> components = repository.findAllComponents();
            model.addAttribute("components", components);

            List<SharedDependencyDto> bottlenecks = repository.findSharedDependencies(1);
            model.addAttribute("bottlenecks", bottlenecks);
            model.addAttribute("dbError", false);

            if (source != null && !source.isBlank() && target != null && !target.isBlank()) {
                PathTraceResponseDto path = repository.findShortestPath(source, target);
                model.addAttribute("selectedSource", source);
                model.addAttribute("selectedTarget", target);
                model.addAttribute("pathResult", path);
            }
        } catch (Exception e) {
            model.addAttribute("dbError", true);
        }

        return "layout";
    }
}
