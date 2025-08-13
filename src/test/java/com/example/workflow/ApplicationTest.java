package com.example.workflow;

import com.example.workflow.tasks.RestServiceDelegate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class ApplicationTest {

    @Autowired
    private RestServiceDelegate restServiceDelegate;

    @Test
    public void testRestServiceDelegateIsLoaded() {
        assertNotNull(restServiceDelegate, "RestServiceDelegate should be loaded as a Spring bean");
    }
}