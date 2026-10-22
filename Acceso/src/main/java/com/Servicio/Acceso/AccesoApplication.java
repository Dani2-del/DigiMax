package com.Servicio.Acceso;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class AccesoApplication {

	public static void main(String[] args) {
		SpringApplication.run(AccesoApplication.class, args);
	}

}
