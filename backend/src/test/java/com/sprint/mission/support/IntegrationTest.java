package com.sprint.mission.support;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import({TestcontainersConfig.class, DatabaseCleaner.class})
public abstract class IntegrationTest {

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @AfterEach
    void cleanDatabase() {
        databaseCleaner.clean();
    }
}
