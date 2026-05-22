package com.fyp.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;

@SpringBootApplication
@OpenAPIDefinition(
    info = @Info(
        title = "Audit Service API",
        version = "1.0.0",
        description = "Blockchain-Anchored Audit Trail - FYP Banking System Phase 5",
        contact = @Contact(name = "FYP Team", email = "fyp@giki.edu.pk")
    )
)
public class AuditServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuditServiceApplication.class, args);
    }
}
