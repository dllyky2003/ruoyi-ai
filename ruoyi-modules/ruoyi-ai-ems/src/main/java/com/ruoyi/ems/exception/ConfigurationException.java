package com.ruoyi.ems.exception;


/**
 * 配置异常
 * 用于 YAML/JSON 配置加载、变量校验等场景
 */
public class ConfigurationException extends RuntimeException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
