package com.hathoute.kubernetes.operator.openhands;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class OperatorSpringApplication {

  public static void main(final String[] args) {
    SpringApplication.run(OperatorSpringApplication.class, args);
  }

}
