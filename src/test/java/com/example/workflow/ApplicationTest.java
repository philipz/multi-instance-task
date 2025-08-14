package com.example.workflow;

import com.example.workflow.tasks.RestServiceDelegate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "camunda.bpm.admin-user.id=test",
    "camunda.bpm.admin-user.password=test"
})
public class ApplicationTest {

    @Autowired
    private RestServiceDelegate restServiceDelegate;

    @Test
    public void testRestServiceDelegateIsLoaded() {
        assertNotNull(restServiceDelegate, "RestServiceDelegate should be loaded as a Spring bean");
    }
}