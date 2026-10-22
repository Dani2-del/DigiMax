package com.Servicio.Acceso;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"app.jwt.secret=test-secret-for-access-service-only-0000000000000000",
		"app.mail.api-key=test-mail-service-key",
		"spring.data.mongodb.auto-index-creation=false"
})
class AccesoApplicationTests {

	@Test
	void contextLoads() {
	}

}
