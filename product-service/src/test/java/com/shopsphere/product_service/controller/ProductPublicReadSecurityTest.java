package com.shopsphere.product_service.controller;

import com.shopsphere.product_service.config.SecurityConfig;
import com.shopsphere.product_service.security.JwtAuthenticationFilter;
import com.shopsphere.product_service.security.JwtService;
import com.shopsphere.product_service.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = ProductController.class, properties = "jwt.secret=shopsphere-test-secret-value-which-is-long-enough")
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, ProductPublicReadSecurityTest.TestBeans.class, JwtService.class})
@AutoConfigureMockMvc
class ProductPublicReadSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void guestsCanBrowseProducts() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk());
    }

    @Test
    void guestProductWritesRemainProtected() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        ProductService productService() {
            return new ProductService(null) {
                @Override
                public List<com.shopsphere.product_service.dto.ProductResponse> getAllProducts() {
                    return List.of();
                }
            };
        }
    }
}
