package com.sprint.mission.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfig {

    /** compose.yaml의 localstack 서비스와 같은 태그다. */
    private static final DockerImageName LOCALSTACK_IMAGE = DockerImageName.parse("localstack/localstack:4.14.0");
    private static final String INIT_SCRIPT = "/tmp/init-s3.sh";

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));
    }

    /** 컨테이너를 먼저 띄우고 로컬과 같은 init 스크립트로 버킷을 만든다. 스크립트는 버킷이 있으면 만들지 않는다. */
    @Bean
    LocalStackContainer localstack() throws Exception {
        LocalStackContainer container = new LocalStackContainer(LOCALSTACK_IMAGE)
                .withServices(LocalStackContainer.Service.S3)
                // 기본값은 서명 검사를 건너뛴다. 서명한 형식·크기를 S3가 강제하는지 확인하려고 켠다.
                .withEnv("S3_SKIP_SIGNATURE_VALIDATION", "0")
                .withCopyFileToContainer(
                        MountableFile.forHostPath("../scripts/localstack/init-s3.sh", 0755), INIT_SCRIPT);
        container.start();
        ExecResult result = container.execInContainer(INIT_SCRIPT);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("LocalStack 버킷 생성 실패: " + result.getStderr());
        }
        return container;
    }

    @Bean
    DynamicPropertyRegistrar storageProperties(LocalStackContainer localstack) {
        return registry -> {
            String endpoint = localstack.getEndpoint().toString();
            registry.add("app.storage.endpoint", () -> endpoint);
            registry.add("app.storage.public-base-url", () -> endpoint + "/dewy-images");
        };
    }
}
