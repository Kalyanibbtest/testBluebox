package com.bluebox.demo.service;

import com.bluebox.demo.model.Order;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final Tracer tracer;
    private final LongCounter ordersCreatedCounter;
    private final LongCounter ordersFulfilledCounter;
    private final LongCounter ordersFailedCounter;

    private final Map<String, Order> orderStore = new ConcurrentHashMap<>();

    public OrderService() {
        this.tracer = GlobalOpenTelemetry.getTracer("com.bluebox.demo.OrderService", "1.0.0");

        Meter meter = GlobalOpenTelemetry.getMeter("com.bluebox.demo.OrderService");
        this.ordersCreatedCounter = meter.counterBuilder("bluebox.orders.created")
                .setDescription("Total number of orders created")
                .setUnit("orders")
                .build();
        this.ordersFulfilledCounter = meter.counterBuilder("bluebox.orders.fulfilled")
                .setDescription("Total number of orders fulfilled")
                .setUnit("orders")
                .build();
        this.ordersFailedCounter = meter.counterBuilder("bluebox.orders.failed")
                .setDescription("Total number of orders that failed processing")
                .setUnit("orders")
                .build();
    }

    public Order createOrder(String product, int quantity, double unitPrice) {
        Span span = tracer.spanBuilder("OrderService.createOrder")
                .setAttribute("order.product", product)
                .setAttribute("order.quantity", quantity)
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            String orderId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            double total = quantity * unitPrice;

            Order order = new Order(orderId, product, quantity, total, "CREATED");
            orderStore.put(orderId, order);

            span.setAttribute("order.id", orderId);
            span.setAttribute("order.total_price", total);

            ordersCreatedCounter.add(1,
                io.opentelemetry.api.common.Attributes.of(
                    io.opentelemetry.api.common.AttributeKey.stringKey("product"), product
                )
            );

            log.info("Order created: id={} product={} qty={} total={}", orderId, product, quantity, total);
            return order;

        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            ordersFailedCounter.add(1);
            log.error("Failed to create order for product={}", product, e);
            throw e;
        } finally {
            span.end();
        }
    }

    public Optional<Order> getOrder(String orderId) {
        Span span = tracer.spanBuilder("OrderService.getOrder")
                .setAttribute("order.id", orderId)
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            Optional<Order> order = Optional.ofNullable(orderStore.get(orderId));
            span.setAttribute("order.found", order.isPresent());
            log.info("Order lookup: id={} found={}", orderId, order.isPresent());
            return order;
        } finally {
            span.end();
        }
    }

    public List<Order> getAllOrders() {
        Span span = tracer.spanBuilder("OrderService.getAllOrders").startSpan();
        try (Scope scope = span.makeCurrent()) {
            List<Order> orders = new ArrayList<>(orderStore.values());
            span.setAttribute("orders.count", orders.size());
            return orders;
        } finally {
            span.end();
        }
    }

    public Order fulfillOrder(String orderId) {
        Span span = tracer.spanBuilder("OrderService.fulfillOrder")
                .setAttribute("order.id", orderId)
                .startSpan();
        try (Scope scope = span.makeCurrent()) {
            Order order = orderStore.get(orderId);
            if (order == null) {
                span.setStatus(StatusCode.ERROR, "Order not found");
                throw new NoSuchElementException("Order not found: " + orderId);
            }
            order.setStatus("FULFILLED");
            ordersFulfilledCounter.add(1,
                io.opentelemetry.api.common.Attributes.of(
                    io.opentelemetry.api.common.AttributeKey.stringKey("product"), order.getProduct()
                )
            );
            log.info("Order fulfilled: id={}", orderId);
            return order;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
