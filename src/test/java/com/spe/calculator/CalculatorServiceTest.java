package com.spe.calculator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CalculatorServiceTest {

    private final CalculatorService service = new CalculatorService();

    @Test
    void add() {
        assertEquals(5, service.add(2, 3));
        assertEquals(-1, service.add(2, -3));
    }

    @Test
    void subtract() {
        assertEquals(-1, service.subtract(2, 3));
        assertEquals(7.5, service.subtract(10, 2.5));
    }

    @Test
    void multiply() {
        assertEquals(6, service.multiply(2, 3));
        assertEquals(0, service.multiply(5, 0));
    }

    @Test
    void divide() {
        assertEquals(2.5, service.divide(5, 2));
        assertEquals(-3, service.divide(9, -3));
    }

    @Test
    void divideByZeroThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.divide(1, 0));
        assertEquals("Division by zero is not allowed", ex.getMessage());
    }
}
