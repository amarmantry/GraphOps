package com.amarmantry.graphops.dto;

import java.util.List;

// Generic node used for the "discovery" dropdowns (Service, Database, ExternalAPI)
public record ComponentDto(String name, List<String> labels, String engineOrProvider) {}