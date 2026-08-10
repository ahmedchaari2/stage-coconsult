package tn.coconsult.medtrack;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Dernier service du cahier des charges : agrège les statistiques du dashboard en interrogeant
 * les autres services via Feign, sans lire leurs tables directement. Placé au package racine
 * tn.coconsult.medtrack pour que le scan couvre aussi les entités/repos de medtrack-common
 * (User, utilisé par le header-trust), comme les autres services extraits.
 */
@SpringBootApplication
@EnableFeignClients
public class DashboardServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(DashboardServiceApplication.class, args);
	}

}
