package com.company.company_clean_hub_be.cccd.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "openai.api")
public class OpenAiProperties {
    private String url = "https://api.openai.com/v1/responses";
    private String key = "";
    private String model = "gpt-5.4-mini";
    private boolean enabled = true;
    private int timeoutSeconds = 30;
}
