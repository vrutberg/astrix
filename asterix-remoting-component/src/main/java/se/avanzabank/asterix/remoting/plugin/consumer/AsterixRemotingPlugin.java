/*
 * Copyright 2014-2015 Avanza Bank AB
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
package se.avanzabank.asterix.remoting.plugin.consumer;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import org.kohsuke.MetaInfServices;

import se.avanzabank.asterix.context.AsterixApiProviderPlugin;
import se.avanzabank.asterix.context.AsterixFactoryBean;
import se.avanzabank.asterix.provider.core.AsterixServiceRegistryApi;
import se.avanzabank.asterix.provider.remoting.AsterixRemoteApiDescriptor;

@MetaInfServices(AsterixApiProviderPlugin.class)
public class AsterixRemotingPlugin implements AsterixApiProviderPlugin {
	
	@Override
	public List<AsterixFactoryBean<?>> createFactoryBeans(Class<?> descriptorHolder) {
		AsterixRemoteApiDescriptor remoteApiDescriptor = descriptorHolder.getAnnotation(AsterixRemoteApiDescriptor.class);
		final String targetSpace = remoteApiDescriptor.targetSpaceName();
		if (targetSpace.isEmpty()) {
			throw new IllegalArgumentException("No space name found on: " + descriptorHolder);
		}
		Class<?>[] exportedApis = remoteApiDescriptor.exportedApis();
		List<AsterixFactoryBean<?>> result = new ArrayList<>();
		for (Class<?> api : exportedApis) {
			result.add(
					new AsterixRemotingServiceFactory<>(api, targetSpace, descriptorHolder));
		}
		return result;
	}

	@Override
	public Class<? extends Annotation> getProviderAnnotationType() {
		return AsterixRemoteApiDescriptor.class;
	}

	@Override
	public boolean consumes(Class<?> descriptorHolder) {
		return descriptorHolder.isAnnotationPresent(getProviderAnnotationType()) 
				&& !descriptorHolder.isAnnotationPresent(AsterixServiceRegistryApi.class);
	}
	
}
