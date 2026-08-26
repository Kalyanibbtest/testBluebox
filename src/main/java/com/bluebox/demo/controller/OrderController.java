package com.bluebox.demo.controller;

import com.bluebox.demo.model.Order;
import com.bluebox.demo.service.OrderService;
import io.opentelemetry.api.trace.Span;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * POST /api/orders
     * Create a new order. Generates a custom OTel span with order metadata.
     */
    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody Map<String, Object> body) {
        String product  = (String) body.getOrDefault("product", "unknown");
        int    quantity = (int)    body.getOrDefault("quantity", 1);
        double price    = ((Number) body.getOrDefault("unitPrice", 9.99)).doubleValue();

        // Enrich the active server-side span with business attributes
        Span.current()
            .setAttribute("bluebox.product", product)
            .setAttribute("bluebox.quantity", quantity);

        Order order = orderService.createOrder(product, quantity, price);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    /**
     * GET /api/orders
     * List all orders.
     */
    @GetMapping
    public ResponseEntity<List<Order>> listOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }

    /**
     * GET /api/orders/{orderId}
     * Fetch a single order by ID.
     */
    @GetMapping("/{orderId}")
    public ResponseEntity<Order> getOrder(@PathVariable String orderId) {
        return orderService.getOrder(orderId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/orders/{orderId}/fulfill
     * Mark an order as fulfilled.
     */
    @PostMapping("/{orderId}/fulfill")
    public ResponseEntity<?> fulfillOrder(@PathVariable String orderId) {
        try {
            Order fulfilled = orderService.fulfillOrder(orderId);
            return ResponseEntity.ok(fulfilled);
        } catch (NoSuchElementException e) {
            log.warn("Fulfill failed - order not found: {}", orderId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
