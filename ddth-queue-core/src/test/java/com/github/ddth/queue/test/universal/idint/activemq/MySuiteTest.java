package com.github.ddth.queue.test.universal.idint.activemq;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)

@Suite.SuiteClasses({ TestActiveMqQueue.class, TestActiveMqQueueLong.class, TestActiveMqQueueMT.class })

/*
 * mvn test -DskipTests=false -Dtest=com.github.ddth.queue.test.universal.idint.activemq.MySuiteTest -DenableTestsActiveMq=true
 */

public class MySuiteTest {
}
