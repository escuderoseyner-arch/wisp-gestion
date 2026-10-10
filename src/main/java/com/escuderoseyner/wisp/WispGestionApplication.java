package com.escuderoseyner.wisp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // tareas programadas (ej: descuento mensual de la caja)
public class WispGestionApplication {

	public static void main(String[] args) {
		SpringApplication.run(WispGestionApplication.class, args);
	}

}
