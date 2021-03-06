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
package com.avanza.astrix.remoting.server;


import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.avanza.astrix.context.metrics.Metrics;
import com.avanza.astrix.core.ServiceInvocationException;
import com.avanza.astrix.core.function.Command;
import com.avanza.astrix.core.util.ReflectionUtil;
import com.avanza.astrix.remoting.client.AstrixServiceInvocationRequest;
import com.avanza.astrix.remoting.client.AstrixServiceInvocationResponse;
import com.avanza.astrix.remoting.client.MissingServiceMethodException;
import com.avanza.astrix.versioning.core.AstrixObjectSerializer;
/**
 * Server side component used to invoke exported services. <p> 
 * 
 * @author Elias Lindholm (elilin)
 *
 */
class AstrixServiceActivatorImpl implements AstrixServiceActivator {
	
	private static final Logger logger = LoggerFactory.getLogger(AstrixServiceActivatorImpl.class);
	private final ConcurrentMap<String, PublishedService<?>> serviceByType = new ConcurrentHashMap<>();
	private final Metrics metrics;
	
	public AstrixServiceActivatorImpl(Metrics metrics) {
		this.metrics = metrics;
	}

	static class PublishedService<T> {

		private final T service;
		private final Map<String, Method> methodBySignature = new HashMap<>();
		private final AstrixObjectSerializer objectSerializer;

		public PublishedService(T service, AstrixObjectSerializer serializer, Class<?>... providedApis) {
			this.service = service;
			this.objectSerializer = serializer;
			for (Class<?> api : providedApis) {
				for (Method m : api.getMethods()) {
					methodBySignature.put(ReflectionUtil.methodSignatureWithoutReturnType(m), m);
				}
			}
		}
		
		public T getService() {
			return service;
		}
		
		private AstrixServiceInvocationResponse invoke(AstrixServiceInvocationRequest request, int version, String serviceApi) {
			try {
				return invokeService(request, version, serviceApi);
			} catch (Exception e) {
				Throwable exceptionThrownByService = resolveException(e);
				AstrixServiceInvocationResponse invocationResponse = new AstrixServiceInvocationResponse();
				invocationResponse.setExceptionMsg(exceptionThrownByService.getMessage());
				invocationResponse.setCorrelationId(UUID.randomUUID().toString());
				if (exceptionThrownByService instanceof ServiceInvocationException) {
					invocationResponse.setException(this.objectSerializer.serialize(exceptionThrownByService, version));
				} else {
					invocationResponse.setThrownExceptionType(exceptionThrownByService.getClass().getName());
				}
				logger.info(String.format("Service invocation ended with exception. request=%s correlationId=%s", request, invocationResponse.getCorrelationId()), exceptionThrownByService);
				return invocationResponse;
			}
		}

		private AstrixServiceInvocationResponse invokeService(
				AstrixServiceInvocationRequest request, int version,
				String serviceApi) throws IllegalAccessException,
				InvocationTargetException {
			String serviceMethodSignature = request.getHeader("serviceMethodSignature");
			Method serviceMethod = methodBySignature.get(serviceMethodSignature);
			if (serviceMethod == null) {
				throw new MissingServiceMethodException(String.format("Missing service method: service=%s method=%s", serviceApi, serviceMethodSignature));
			}
			Object[] arguments = unmarshal(request.getArguments(), serviceMethod.getGenericParameterTypes(), version);
			Object result = serviceMethod.invoke(service, arguments);
			AstrixServiceInvocationResponse invocationResponse = new AstrixServiceInvocationResponse();
			if (!serviceMethod.getReturnType().equals(Void.TYPE)) {
				invocationResponse.setResponseBody(objectSerializer.serialize(result, version));
			}
			return invocationResponse;
		}

		private Object[] unmarshal(Object[] elements, Type[] types, int version) {
			Object[] result = new Object[elements.length];
			for (int i = 0; i < result.length; i++) {
				result[i] = objectSerializer.deserialize(elements[i], types[i], version);
			}
			return result;
		}
		
	}
	
	@Override
	public void register(Object provider, AstrixObjectSerializer objectSerializer, Class<?> publishedApi) {
		if (!publishedApi.isAssignableFrom(provider.getClass())) {
			throw new IllegalArgumentException("Provider: " + provider.getClass() + " does not implement: " + publishedApi);
		}
		PublishedService<?> publishedService = new PublishedService<>(provider, objectSerializer, publishedApi);
		this.serviceByType.put(publishedApi.getName(), publishedService);
	}
	
	/**
	 * @param request
	 * @return
	 */
	@Override
	public AstrixServiceInvocationResponse invokeService(final AstrixServiceInvocationRequest request) {
		final int version = Integer.parseInt(request.getHeader("apiVersion"));
		final String serviceApi = request.getHeader("serviceApi");
		final PublishedService<?> publishedService = this.serviceByType.get(serviceApi);
		if (publishedService == null) {
			/*
			 * Service not available. This might happen in rare conditions when a processing unit
			 * is restarted and old clients connects to the space before the framework is fully initialized. 
			 */
			AstrixServiceInvocationResponse invocationResponse = new AstrixServiceInvocationResponse();
			invocationResponse.setServiceUnavailable(true);;
			invocationResponse.setExceptionMsg("Service not available in service activator: " + serviceApi);
			invocationResponse.setCorrelationId(UUID.randomUUID().toString());
			logger.info(String.format("Service not available. request=%s correlationId=%s", request, invocationResponse.getCorrelationId()));
			return invocationResponse;
		}
		return this.metrics.timeExecution(
				(Command<AstrixServiceInvocationResponse>) () -> publishedService.invoke(request, version, serviceApi), "ServiceActivator", serviceApi).call();
	}

	private static Throwable resolveException(Exception e) {
		if (e instanceof InvocationTargetException) {
			// Invoked service threw an exception
			return InvocationTargetException.class.cast(e).getTargetException();
		}
		return e;
	}

}