package com.amarmantry.graphops.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ModelAndView handleException(Exception e) {
        logger.error("Application error: {}", e.getMessage(), e);

        ModelAndView mav = new ModelAndView("layout");
        mav.addObject("dbError", true);
        mav.addObject("errorMessage", "Database connection failed. Please refresh the page.");
        mav.addObject("pageTitle", "Error");
        mav.addObject("content", "dashboard :: content");  // Set a safe default
        return mav;
    }
}