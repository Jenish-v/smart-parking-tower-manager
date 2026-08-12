package com.jenish.smartparking;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"
})
class SmartParkingApplicationTest {

    @Test
    void applicationContextStartsWithoutInfrastructure() {
    }
}
