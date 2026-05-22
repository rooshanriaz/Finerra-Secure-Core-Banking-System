package com.fyp.txn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;

@SpringBootApplication
@EnableAsync
@OpenAPIDefinition(
    info = @Info(
        title = "Transaction Service API",
        version = "1.0.0",
        description = "Transaction Processing with Blockchain Audit - FYP Banking System Phase 5",
        contact = @Contact(name = "FYP Team", email = "fyp@giki.edu.pk")
    )
)
public class TransactionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionServiceApplication.class, args);
    }
}
