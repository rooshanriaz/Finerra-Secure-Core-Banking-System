package com.fyp.kyc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;

@SpringBootApplication
@OpenAPIDefinition(
    info = @Info(
        title = "KYC/AML Service API",
        version = "1.0.0",
        description = "KYC/AML with Decentralized Identity (DID) - FYP Banking System",
        contact = @Contact(name = "FYP Team", email = "fyp@giki.edu.pk")
    )
)
public class KycAmlServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(KycAmlServiceApplication.class, args);
    }
}
