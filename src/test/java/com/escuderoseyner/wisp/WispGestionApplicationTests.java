package com.escuderoseyner.wisp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

// Arranca la aplicación completa contra MySQL (incluye la validación del esquema).
// Solo corre si están las variables de entorno; sin ellas (ej: ./mvnw test en una terminal
// sin configurar) se omite en lugar de fallar.
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@EnabledIfEnvironmentVariable(named = "JWT_SECRET", matches = ".+")
class WispGestionApplicationTests {

	@Test
	void contextLoads() {
	}

}
