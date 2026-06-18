package br.com.salome.core;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "salome.legacy.datasource.enabled=false",
        "salome.manifesto.export.enabled=false"
})
class SalomeCoreApplicationTests {

    @Test
    void contextLoads() {
    }
}
