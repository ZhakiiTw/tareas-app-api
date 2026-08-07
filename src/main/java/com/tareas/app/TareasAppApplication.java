package com.tareas.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class TareasAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(TareasAppApplication.class, args);
    }

}
