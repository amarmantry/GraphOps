package com.amarmantry.graphops.exception;

import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ServiceUnavailableException.class)
    public ModelAndView handleServiceUnavailable(ServiceUnavailableException e) {
        logger.error("Database service unavailable", e);

        ModelAndView mav = new ModelAndView("layout");
        mav.addObject("dbError", true);
        mav.addObject("errorMessage", "Database is currently unavailable. Please check your connection and try again.");
        mav.addObject("pageTitle", "Error");
        mav.addObject("content", "dashboard :: content");
        return mav;
    }

    @ExceptionHandler(Exception.class)
    public ModelAndView handleGeneralException(Exception e) {
        logger.error("Unexpected application error", e);

        ModelAndView mav = new ModelAndView("layout");
        mav.addObject("dbError", true);
        mav.addObject("errorMessage", "An unexpected error occurred. Please try again later.");
        mav.addObject("pageTitle", "Error");
        mav.addObject("content", "dashboard :: content");
        return mav;
    }
}