package com.k8s.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(excludeName = {
        "net.devh.boot.grpc.server.autoconfigure.GrpcServerAutoConfiguration",
        "net.devh.boot.grpc.server.autoconfigure.GrpcServerFactoryAutoConfiguration",
        "net.devh.boot.grpc.server.autoconfigure.GrpcServerSecurityAutoConfiguration"
})
@EnableJpaRepositories(basePackages = {
        "com.k8s.platform.domain.repository",
        "com.k8s.platform.repository"
})
@EnableAsync
@EnableScheduling
public class K8sPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(K8sPlatformApplication.class, args);
    }
}
