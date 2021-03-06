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
package com.avanza.astrix.context;

import com.avanza.astrix.beans.config.AstrixConfig;
import com.avanza.astrix.beans.config.BeanConfiguration;
import com.avanza.astrix.beans.config.BeanConfigurations;
import com.avanza.astrix.beans.core.AstrixBeanKey;
import com.avanza.astrix.beans.core.AstrixSettings;
import com.avanza.astrix.beans.factory.BeanFactory;
import com.avanza.astrix.beans.factory.StandardFactoryBean;
import com.avanza.astrix.beans.publish.ApiProviderClass;
import com.avanza.astrix.beans.publish.BeanPublisher;
import com.avanza.astrix.beans.service.ServiceDefinition;
import com.avanza.astrix.beans.service.ServiceDefinitionSource;
import com.avanza.astrix.beans.service.StatefulAstrixBean;
import com.avanza.astrix.config.DynamicConfig;
import com.avanza.astrix.modules.Modules;
import com.avanza.astrix.serviceunit.AstrixApplicationDescriptor;
import com.avanza.astrix.serviceunit.ExportedServiceBeanDefinition;
import com.avanza.astrix.serviceunit.ServiceAdministrator;
import com.avanza.astrix.serviceunit.ServiceAdministratorVersioningConfigurer;
import com.avanza.astrix.serviceunit.ServiceExporter;
import com.avanza.astrix.versioning.core.ObjectSerializerDefinition;
/**
 * An AstrixContextImpl is the runtime-environment for the astrix-framework. It is used
 * both by consuming applications as well as server applications. AstrixContextImpl providers access
 * to different Astrix-plugins at runtime and is used as a factory to create Astrix-beans.
 * 
 * @author Elias Lindholm (elilin)
 */
final class AstrixContextImpl implements Astrix, AstrixApplicationContext {
	
	private final BeanFactory beanFactory;
	private final BeanConfigurations beanConfigurations;
	private final BeanPublisher beanPublisher;
	private final DynamicConfig dynamicConfig;
	private final Modules modules;
	private final AstrixApplicationDescriptor applicationDescriptor;
	
	public AstrixContextImpl(Modules modules, AstrixApplicationDescriptor applicationDescriptor) {
		this.modules = modules;
		this.applicationDescriptor = applicationDescriptor;
		this.dynamicConfig = modules.getInstance(AstrixConfig.class).getConfig();
		this.beanPublisher = modules.getInstance(BeanPublisher.class);
		this.beanFactory = modules.getInstance(BeanFactory.class);
		this.beanConfigurations = modules.getInstance(BeanConfigurations.class);
	}
	

	void register(ApiProviderClass apiProvider) {
		this.beanPublisher.publish(apiProvider);		
	}

	<T> void registerBeanFactory(StandardFactoryBean<T> beanFactory) {
		this.beanFactory.registerFactory(beanFactory);
	}
	
	@Override
	public void destroy() {
		this.modules.destroy();
	}

	@Override
	public void close() throws Exception {
		destroy();
	}
	
	public BeanConfiguration getBeanConfiguration(AstrixBeanKey<?> beanKey) {
		return this.beanConfigurations.getBeanConfiguration(beanKey);
	}
	
	@Override
	public <T> T getBean(Class<T> beanType) {
		return getBean(beanType, null);
	}
	
	@Override
	public <T> T getBean(Class<T> beanType, String qualifier) {
		return beanFactory.getBean(AstrixBeanKey.create(beanType, qualifier));
	}
	
	public <T> T getBean(AstrixBeanKey<T> beanKey) {
		return beanFactory.getBean(beanKey);
	}
	

	@Override
	public <T> T waitForBean(Class<T> beanType, long timeoutMillis) throws InterruptedException {
		return waitForBean(beanType, null, timeoutMillis);
	}
	
	@Override
	public <T> T waitForBean(Class<T> beanType, String qualifier, long timeoutMillis) throws InterruptedException {
		AstrixBeanKey<T> beanKey = AstrixBeanKey.create(beanType, qualifier);
		T bean = beanFactory.getBean(beanKey);
		for (AstrixBeanKey<?> dependencyKey : beanFactory.getDependencies(beanKey)) {
			Object dependency = beanFactory.getBean(dependencyKey);
			waitForBeanToBeBound(dependency, timeoutMillis);
		}
		waitForBeanToBeBound(bean, timeoutMillis);
		return bean;
	}
	private void waitForBeanToBeBound(Object bean, long timeoutMillis) throws InterruptedException {
		if (bean instanceof StatefulAstrixBean) {
			StatefulAstrixBean.class.cast(bean).waitUntilBound(timeoutMillis);
		}
	}

	/**
	 * Returns an instance of an internal framework class.
	 * @param classType
	 * @return
	 */
	public final <T> T getInstance(final Class<T> classType) {
		return this.modules.getInstance(classType);
	}
	
	@Override
	public DynamicConfig getConfig() {
		return dynamicConfig;
	}
	
	@Override
	public void startServicePublisher() {
		if (!isServer()) {
			throw new IllegalStateException("Server part not configured. Set AstrixConfigurer.setApplicationDescriptor to load server part of framework");
		}
		String applicationInstanceId = AstrixSettings.APPLICATION_INSTANCE_ID.getFrom(dynamicConfig).get();
		ServiceExporter serviceExporter = getInstance(ServiceExporter.class);
		
		serviceExporter.addServiceProvider(getInstance(ServiceAdministrator.class));
		ObjectSerializerDefinition serializer = ObjectSerializerDefinition.versionedService(1, ServiceAdministratorVersioningConfigurer.class);
		ServiceDefinition<ServiceAdministrator> serviceDefinition = new ServiceDefinition<>(ServiceDefinitionSource.create("FrameworkServices"),
																							AstrixBeanKey.create(ServiceAdministrator.class, applicationInstanceId), 
																							serializer, true);
		ExportedServiceBeanDefinition<ServiceAdministrator> serviceAdminDefintion = new ExportedServiceBeanDefinition<>(AstrixBeanKey.create(ServiceAdministrator.class, applicationInstanceId), 
																			    serviceDefinition, 
																			    true, // isVersioned  
																			    true, // alwaysActive
																			    AstrixSettings.SERVICE_ADMINISTRATOR_COMPONENT.getFrom(getConfig()).get());
		serviceExporter.exportService(serviceAdminDefintion);
		
		serviceExporter.startPublishServices();
	}


	private boolean isServer() {
		return this.applicationDescriptor != null;
	}
	
}
