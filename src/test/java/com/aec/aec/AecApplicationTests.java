package com.aec.aec;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import com.aec.prodsrv.ProdServiceApplication;

// Le decimos explícitamente a Spring qué clase arrancar
@SpringBootTest(classes = ProdServiceApplication.class)
class AecApplicationTests {

    @Test
    void contextLoads() {
    }
}

