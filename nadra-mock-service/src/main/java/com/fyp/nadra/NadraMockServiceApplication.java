package com.fyp.nadra;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;

@SpringBootApplication
@OpenAPIDefinition(
    info = @Info(
        title = "NADRA Mock Verification API",
        version = "1.0.0",
        description = "Mock NADRA CNIC Verification Service for KYC/AML - FYP Banking System",
        contact = @Contact(name = "FYP Team", email = "fyp@giki.edu.pk")
    )
)
public class NadraMockServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NadraMockServiceApplication.class, args);
    }
}
