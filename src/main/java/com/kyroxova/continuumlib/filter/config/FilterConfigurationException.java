package com.kyroxova.continuumlib.filter.config;

public class FilterConfigurationException extends RuntimeException {
    public FilterConfigurationException(String message) {
        super(message);
    }

    public FilterConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
