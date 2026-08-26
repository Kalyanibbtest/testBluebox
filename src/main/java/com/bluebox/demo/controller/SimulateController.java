package com.bluebox.demo.controller;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Random;

/**
 * Simulates a background workload so Dynatrace / OTel has rich telemetry
 * to display immediately after Bluebox account creation.
 */
@RestController
@RequestMapping("/api/simulate")
public class SimulateController {

    private static final Logger log = LoggerFactory.getLogger(SimulateController.class);
    private static final Random RANDOM = new Random();

    private final Tracer tracer;
    private final LongCounter simulationCounter;

    public SimulateController() {
        this.tracer = GlobalOpenTelemetry.getTracer("com.bluebox.demo.SimulateController", "1.0.0");

        Meter meter = GlobalOpenTelemetry.getMeter("com.bluebox.demo.SimulateController");
        this.simulationCounter = meter.counterBuilder("bluebox.simulation.runs")
                .setDescription("Number of simulation runs executed")
                .build();
    }

    /**
     * GET /api/simulate/load?iterations=20
     * Generates N spans with random latency to simulate real traffic.
     */
    @GetMapping("/load")
    public Map<String, Object> simulateLoad(@RequestParam(defaultValue = "10") int iterations) {
        Span parent = tracer.spanBuilder("simulate.load")
                .setAttribute("simulation.iterations", iterations)
                .startSpan();

        int succeeded = 0;
        int failed = 0;

        try (Scope parentScope = parent.makeCurrent()) {
            for (int i = 0; i < iterations; i++) {
                Span child = tracer.spanBuilder("simulate.task")
                        .setAttribute("task.index", i)
                        .startSpan();
                try (Scope childScope = child.makeCurrent()) {
                    // Simulate variable latency
                    Thread.sleep(RANDOM.nextInt(50) + 10);

                    // Simulate ~15% error rate
                    if (RANDOM.nextDouble() < 0.15) {
                        throw new RuntimeException("Simulated task failure at index " + i);
                    }
                    succeeded++;
                    child.setAttribute("task.status", "success");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    child.setAttribute("task.status", "interrupted");
                    failed++;
                } catch (RuntimeException e) {
                    child.setAttribute("task.status", "error");
                    child.recordException(e);
                    failed++;
                    log.warn("Simulated task error: {}", e.getMessage());
                } finally {
                    child.end();
                }
            }

            parent.setAttribute("simulation.succeeded", succeeded);
            parent.setAttribute("simulation.failed", failed);
            simulationCounter.add(1);

        } finally {
            parent.end();
        }

        log.info("Simulation complete: iterations={} succeeded={} failed={}", iterations, succeeded, failed);
        return Map.of(
            "iterations", iterations,
            "succeeded",  succeeded,
            "failed",     failed
        );
    }

    /**
     * GET /api/simulate/health
     * Simple liveness check that also emits a span.
     */
    @GetMapping("/health")
    public Map<String, String> health() {
        Span.current().setAttribute("bluebox.health", "ok");
        log.info("Health check called");
        return Map.of("status", "UP", "service", "testBluebox");
    }
}
