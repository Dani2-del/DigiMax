package com.Servicio.RecargasMovil;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RecargasMovilApplication {

	public static void main(String[] args) {
		SpringApplication.run(RecargasMovilApplication.class, args);
	}

}
