package edu.campus.taskplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CampusTaskPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(CampusTaskPlatformApplication.class, args);
	}

}
