package com.bluebox.demo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TestBlueboxApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
        // Verifies the Spring context starts correctly
    }

    @Test
    void healthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/api/simulate/health"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void createAndRetrieveOrder() throws Exception {
        // Create an order
        String responseBody = mockMvc.perform(
                post("/api/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"product": "WidgetX", "quantity": 3, "unitPrice": 19.99}
                    """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.product").value("WidgetX"))
                .andExpect(jsonPath("$.quantity").value(3))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extract orderId via simple string search (no extra deps needed)
        String orderId = responseBody.split("\"orderId\":\"")[1].split("\"")[0];

        // Retrieve the same order
        mockMvc.perform(get("/api/orders/" + orderId))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.orderId").value(orderId));
    }

    @Test
    void fulfillNonExistentOrderReturns404() throws Exception {
        mockMvc.perform(post("/api/orders/NONEXISTENT/fulfill"))
               .andExpect(status().isNotFound());
    }

    @Test
    void simulateLoadEndpointReturnsStats() throws Exception {
        mockMvc.perform(get("/api/simulate/load?iterations=5"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.iterations").value(5));
    }
}
