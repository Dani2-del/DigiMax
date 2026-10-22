
package com.transcaribe.transcaribe;

import com.transcaribe.transcaribe.Repository.BusRepository;
import com.transcaribe.transcaribe.service.BusService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DigitransMaxApplication {

    public static void main(String[] args) {
        SpringApplication.run(DigitransMaxApplication.class, args);
    }

    @Bean
    CommandLineRunner init(BusService busService, BusRepository busRepository) {
        return args -> {
            if (busRepository.count() == 0) {
                busService.crearBusDePrueba();
                System.out.println("-----------------------------------------");
                System.out.println("SISTEMA: Base de datos de buses vacía.");
                System.out.println("SISTEMA: Bus de prueba creado automáticamente.");
                System.out.println("-----------------------------------------");
            } else {
                System.out.println("SISTEMA: Los buses ya están cargados en MongoDB.");
            }
        };
    }
}