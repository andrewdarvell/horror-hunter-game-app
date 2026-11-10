package ru.darvell.game.hhg.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Инфраструктурный smoke test обнаружения и выполнения JUnit Jupiter, без игровой логики. */
class JUnitPlatformSmokeTest {
    @Test
    void executesWithJupiter(TestInfo testInfo) {
        assertEquals(JUnitPlatformSmokeTest.class, testInfo.getTestClass().get());
    }
}
