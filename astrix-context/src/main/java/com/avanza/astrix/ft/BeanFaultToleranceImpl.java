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
package com.avanza.astrix.ft;

import com.avanza.astrix.beans.core.AstrixBeanSettings;
import com.avanza.astrix.beans.core.AstrixSettings;
import com.avanza.astrix.beans.factory.BeanConfiguration;
import com.avanza.astrix.config.DynamicBooleanProperty;
import com.avanza.astrix.config.DynamicConfig;
import com.avanza.astrix.core.function.Supplier;

import rx.Observable;
/**
 * 
 * @author Elias Lindholm (elilin)
 *
 */
final class BeanFaultToleranceImpl implements BeanFaultTolerance {

	private final DynamicBooleanProperty faultToleranceEnabledForBean;
	private final DynamicBooleanProperty faultToleranceEnabled;
	private final FaultToleranceSpi beanFaultToleranceSpi;
	private final CommandSettings commandSettings;
	
	BeanFaultToleranceImpl(BeanConfiguration beanConfiguration, DynamicConfig config, FaultToleranceSpi beanFaultToleranceSpi,
			CommandSettings commandSettings) {
		this.beanFaultToleranceSpi = beanFaultToleranceSpi;
		this.commandSettings = commandSettings;
		this.faultToleranceEnabledForBean = beanConfiguration.get(AstrixBeanSettings.FAULT_TOLERANCE_ENABLED);
		this.faultToleranceEnabled = AstrixSettings.ENABLE_FAULT_TOLERANCE.getFrom(config);
	}
	
	@Override
	public <T> Observable<T> observe(Supplier<Observable<T>> observable) {
		if (!faultToleranceEnabled()) {
			return observable.get();
		}
		return beanFaultToleranceSpi.observe(observable, commandSettings);
	}
	
	@Override
	public <T> T execute(final CheckedCommand<T> command) throws Throwable {
		if (!faultToleranceEnabled()) {
			return command.call();
		}
		return beanFaultToleranceSpi.execute(command, commandSettings);
	}
	
	
	private <T> boolean faultToleranceEnabled() {
		return faultToleranceEnabled.get() && faultToleranceEnabledForBean.get();
	}

}