/*
 * Copyright 2014 Avanza Bank AB
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.avanza.astrix.ft.hystrix;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Before;
import org.junit.Test;

import com.avanza.astrix.beans.ft.CommandSettings;
import com.avanza.astrix.core.function.CheckedCommand;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandMetrics;
import com.netflix.hystrix.util.HystrixRollingNumberEvent;

public class HystrixFaultToleranceTest {
	
	private static final AtomicInteger counter = new AtomicInteger(0);
	private String commandKey;
	private String groupKey;
	private final HystrixFaultTolerance faultTolerance = new HystrixFaultTolerance();
	private CommandSettings commandSettings;

	@Before
	public void setup() {
		// Ensure each test runs with unique group/key
		counter.incrementAndGet();
		commandKey = getClass().getSimpleName() + "Command-" + counter.get();
		groupKey = getClass().getSimpleName() + "Group-"  + counter.get();
		commandSettings = new CommandSettings();
		commandSettings.setCommandName(commandKey);
		commandSettings.setGroupName(groupKey);
	}
	
	@Test
	public void usesHystrixFaultToleranceProxyProviderPluginToApplyFaultToleranceToLibraries() throws Throwable {
		assertEquals(0, getAppliedFaultToleranceCount(commandKey));

		assertEquals("foo", faultTolerance.execute(new PingCommand("foo"), commandSettings));
		assertEquals(1, getAppliedFaultToleranceCount(commandKey));
		
		assertEquals("foo", faultTolerance.execute(new PingCommand("foo"), commandSettings));
		assertEquals(2, getAppliedFaultToleranceCount(commandKey));
	}
	
	private static class PingCommand implements CheckedCommand<String> {

		private String msg;
		
		public PingCommand(String msg) {
			this.msg = msg;
		}

		@Override
		public String call() {
			return msg;
		}
		
	}
	
	private int getAppliedFaultToleranceCount(String commandKey) {
		return getEventCountForCommand(HystrixRollingNumberEvent.SUCCESS, commandKey);
	}
	
	private int getEventCountForCommand(HystrixRollingNumberEvent hystrixRollingNumberEvent, String commandKey) {
		HystrixCommandMetrics metrics = HystrixCommandMetrics.getInstance(HystrixCommandKey.Factory.asKey(commandKey));
		if (metrics == null) {
			return 0;
		}
		int currentConcurrentExecutionCount = (int) metrics.getCumulativeCount(hystrixRollingNumberEvent);
		return currentConcurrentExecutionCount;
	}
}