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
package com.avanza.astrix.beans.service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.avanza.astrix.beans.config.AstrixConfig;
import com.avanza.astrix.beans.core.AstrixSettings;
import com.avanza.astrix.beans.util.AstrixFrameworkThread;
/**
 * 
 * @author Elias Lindholm (elilin)
 *
 */
public class ServiceLeaseManager {
	
	private final Logger log = LoggerFactory.getLogger(ServiceLeaseManager.class);
	private final List<ServiceBeanInstance<?>> leasedServices = new CopyOnWriteArrayList<>();
	private final AstrixConfig config;
	private final ServiceLeaseRenewalThread leaseRenewalThread = new ServiceLeaseRenewalThread();
	private final ServiceBindThread serviceBindThread = new ServiceBindThread();
	private final AtomicBoolean isStarted = new AtomicBoolean(false);
	
	public ServiceLeaseManager(AstrixConfig config) {
		this.config = config;
	}
	
	public <T> void startManageLease(ServiceBeanInstance<T> serviceBeanInstance) {
		synchronized (isStarted) {
			if (!isStarted.get()) {
				start();
			}
		}
		log.info(String.format("Start managing service bean. currentState=%s bean=%s astrixBeanId=%s", serviceBeanInstance.getState(), serviceBeanInstance.getBeanKey(), serviceBeanInstance.getBeanId()));
		leasedServices.add(serviceBeanInstance);
	}
	
	private void start() {
		this.leaseRenewalThread.start();
		this.serviceBindThread.start();
		isStarted.set(true);
	}

	@PreDestroy
	public void destroy() {
		this.leaseRenewalThread.interrupt();
		this.serviceBindThread.interrupt();
		for (ServiceBeanInstance<?> leasedService : this.leasedServices) {
			try {
				leasedService.destroy();
			} catch (Exception e) {
				log.warn(String.format("Failed to release service bean: %s", leasedService.getBeanKey()), e);
			}
		}
	}

	private class ServiceBindThread extends AstrixFrameworkThread {
		
		public ServiceBindThread() {
			super("ServiceBind");
		}
		
		@Override
		public void run() {
			while (!interrupted()) {
				for (ServiceBeanInstance<?> leasedService : leasedServices) {
					if (!leasedService.isBound()) {
						bind(leasedService);
					}
				}
				try {
					Thread.sleep(config.get(AstrixSettings.BEAN_BIND_ATTEMPT_INTERVAL).get());
				} catch (InterruptedException e) {
					interrupt();
				}
			}
			log.info("Terminating thread=" + getName());
		}
		
		private void bind(ServiceBeanInstance<?> leasedService) {
			try {
				log.debug("Attempting to bind service={} beanId={}", leasedService.getBeanKey(), leasedService.getBeanId());
				leasedService.bind();
			} catch (Exception e) {
				log.warn("Failed to bind service: " + leasedService.getBeanKey(), e);
			}
		}
	}
	
	private class ServiceLeaseRenewalThread extends AstrixFrameworkThread {
		
		public ServiceLeaseRenewalThread() {
			super("ServiceLeaseRenewal");
		}
		
		@Override
		public void run() {
			while (!interrupted()) {
				for (ServiceBeanInstance<?> leasedService : leasedServices) {
					renewLease(leasedService);
				}
				try {
					Thread.sleep(config.get(AstrixSettings.SERVICE_LEASE_RENEW_INTERVAL).get());
				} catch (InterruptedException e) {
					interrupt();
				}
			}
			log.info("Terminating thread=" + getName());
		}
		
		private void renewLease(ServiceBeanInstance<?> leasedService) {
			try {
				leasedService.renewLease();
			} catch (Exception e) {
				log.warn("Failed to renew lease for service: " + leasedService.getBeanKey(), e);
			}
		}
	}
	
}