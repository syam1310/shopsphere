package com.shopsphere.order_service.controller;

import com.shopsphere.order_service.client.CartClient;
import com.shopsphere.order_service.client.NotificationClient;
import com.shopsphere.order_service.client.PaymentClient;
import com.shopsphere.order_service.client.UserClient;
import com.shopsphere.order_service.security.JwtAuthenticationFilter;
import com.shopsphere.order_service.security.JwtService;
import com.shopsphere.order_service.service.CheckoutIdempotencyService;
import com.shopsphere.order_service.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.ConfigurableWebBindingInitializer;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({
        com.shopsphere.order_service.exception.GlobalExceptionHandler.class,
        OrderControllerValidationTest.TestSecurityConfig.class
})
class OrderControllerValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private CartClient cartClient;

    @MockitoBean
    private PaymentClient paymentClient;

    @MockitoBean
    private CheckoutIdempotencyService checkoutIdempotencyService;

    @MockitoBean
    private UserClient userClient;

    @MockitoBean
    private NotificationClient notificationClient;

    @Autowired
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private RequestMappingHandlerAdapter handlerAdapter;


    @Test
    void debugRegisteredValidator() {
        ConfigurableWebBindingInitializer initializer =
                (ConfigurableWebBindingInitializer)
                        handlerAdapter.getWebBindingInitializer();

        System.out.println("MVC VALIDATOR: " + initializer.getValidator());
        System.out.println("MVC VALIDATOR CLASS: "
                + (initializer.getValidator() == null
                ? "NULL"
                : initializer.getValidator().getClass().getName()));
    }

    @Test
    @WithMockUser(roles = "USER")
    void debugMockMvcRequest() throws Exception {
        System.out.println("HANDLER MAPPING BEAN: " + handlerMapping);
        System.out.println("REGISTERED MAPPINGS: "
                + handlerMapping.getHandlerMethods().keySet().stream()
                .filter(mapping -> mapping.toString().contains("/api/orders"))
                .toList());

        System.out.println(
                "DISPATCHER SERVLET: "
                        + mockMvc.getDispatcherServlet().getClass().getName()
        );

        System.out.println(
                "DISPATCHER HANDLER MAPPINGS: "
                        + mockMvc.getDispatcherServlet()
                        .getHandlerMappings()
                        .stream()
                        .map(mapping -> mapping.getClass().getName())
                        .toList()
        );

        System.out.println("MOCK MVC CLASS: " + mockMvc.getClass().getName());

        MockHttpServletRequest diagnosticRequest =
                new MockHttpServletRequest("POST", "/api/orders");

        diagnosticRequest.setServletPath("/api/orders");

        mockMvc.getDispatcherServlet()
                .getHandlerMappings()
                .forEach(mapping -> {
                    try {
                        System.out.println(
                                "MAPPING CHECK: "
                                        + mapping.getClass().getSimpleName()
                                        + " -> "
                                        + mapping.getHandler(diagnosticRequest)
                        );
                    } catch (Exception e) {
                        System.out.println(
                                "MAPPING ERROR: " + e.getMessage()
                        );
                    }
                });

        mockMvc.perform(
                        post("/api/orders")
                                .servletPath("/api/orders")
                                .contextPath("")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"items\":[]}")
                )

                .andDo(result -> {
                    var actualRequest = result.getRequest();

                    System.out.println("ACTUAL REQUEST URI: " + actualRequest.getRequestURI());
                    System.out.println("ACTUAL SERVLET PATH: " + actualRequest.getServletPath());
                    System.out.println("ACTUAL METHOD: " + actualRequest.getMethod());

                    mockMvc.getDispatcherServlet()
                            .getHandlerMappings()
                            .forEach(mapping -> {
                                try {
                                    System.out.println(
                                            "ACTUAL REQUEST MAPPING CHECK: "
                                                    + mapping.getClass().getSimpleName()
                                                    + " -> "
                                                    + mapping.getHandler(actualRequest)
                                    );
                                } catch (Exception e) {
                                    System.out.println(
                                            "ACTUAL REQUEST MAPPING ERROR: "
                                                    + mapping.getClass().getSimpleName()
                                                    + " -> "
                                                    + e
                                    );
                                }
                            });
                });

    }

    @Test
    void debugOrderControllerMappings() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/orders");

        request.setServletPath("/api/orders");

    }

    @Test
    void createOrder_whenItemsAreEmpty_returnsBadRequest() throws Exception {
        assertValidationFailure("""
                {"items":[]}
                """);
    }


    @Test
    void debugPostOrderMapping() throws Exception {
        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/orders");

        request.setRequestURI("/api/orders");
        request.setServletPath("/api/orders");
        request.setMethod("POST");

        handlerMapping.getHandlerMethods().forEach((mapping, method) -> {
            if (mapping.toString().contains("POST")) {
                System.out.println(mapping + " -> " + method);
            }
        });
    }

    @Test
    void debugRegisteredMappings() {
        System.out.println("REGISTERED CONTROLLER MAPPINGS:");

        handlerMapping.getHandlerMethods().forEach((mapping, method) -> {
            System.out.println(mapping + " -> " + method);
        });
    }

    @Test
    @WithMockUser(roles = "USER")
    void createOrder_whenProductIdIsMissing_returnsBadRequest() throws Exception {
        assertValidationFailure("""
                {"items":[{"quantity":1}]}
                """);
    }

    @Test
    @WithMockUser(roles = "USER")
    void createOrder_whenQuantityIsZero_returnsBadRequest() throws Exception {
        assertValidationFailure("""
                {"items":[{"productId":1,"quantity":0}]}
                """);
    }

    @Test
    @WithMockUser(roles = "USER")
    void createOrder_whenQuantityIsNegative_returnsBadRequest() throws Exception {
        assertValidationFailure("""
                {"items":[{"productId":1,"quantity":-1}]}
                """);
    }

    private void assertValidationFailure(String requestBody) throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));
        verify(orderService, never())
                .createOrder(any(), anyLong(), anyString());
    }


    @TestConfiguration
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

            return http.build();
        }
    }

}