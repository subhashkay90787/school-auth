package com.school.auth.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI centralAuthOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Central ERP Authorization Server API")
                        .version("1.0.0")
                        .description("API Documentation for Central OAuth2 Authorization Server, custom OTP grants, and remote ERP modules.")
                        .license(new License().name("Apache 2.0").url("https://springdoc.org")));
    }
}