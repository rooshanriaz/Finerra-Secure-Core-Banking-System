package com.fyp.cbc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.fyp.cbc.config.FineractProperties;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;

@SpringBootApplication
@EnableConfigurationProperties({FineractProperties.class})
@OpenAPIDefinition(
    info = @Info(
        title = "Core Banking Connector API",
        version = "1.0.0",
        description = "REST API wrapper for Apache Fineract - FYP Banking System with Blockchain Integration",
        contact = @Contact(
            name = "FYP Team",
            email = "fyp@giki.edu.pk"
        ),
        license = @License(
            name = "MIT License",
            url = "https://opensource.org/licenses/MIT"
        )
    )
)
public class CoreBankingConnectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoreBankingConnectorApplication.class, args);
    }
}
