package dev.infrai.checkout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CheckoutAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(CheckoutAgentApplication.class, args);
    }
}
