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
package com.avanza.asterix.context;

import java.lang.annotation.Annotation;
import java.util.List;


/**
 * An AsterixApiProviderPlugin is responsible for creating AsterixFactoryBeanPlugin for
 * all parts of a given "type" of api. By "type" in this context, we don't mean the
 * different api's that are hooked into asterix for consumption, but rather a mechanism
 * for an api-provider to use to make the different part's of the api available for consumption.
 * 
 * For instance, one type of api is "library", which is handled by the AsterixLibraryProviderPlugin. It
 * allows api-providers to export api's that require "a lot" of wiring on the client side by annotating
 * a class with @AsterixLibraryProvider an export different api-elements by annotating factory methods
 * for different api elements with @AsterixExport. 
 * 
 * Another type of api is a "service-registry" api, which binds to services using the service-registry
 * which typically also requires a server side component to respond to the service-invocation-request.
 * 
 * @author Elias Lindholm (elilin)
 *
 */
public interface AsterixApiProviderPlugin {
	
	List<AsterixFactoryBeanPlugin<?>> createFactoryBeans(AsterixApiDescriptor descriptor);
	
	List<Class<?>> getProvidedBeans(AsterixApiDescriptor descriptor);
	
	Class<? extends Annotation> getProviderAnnotationType();
	
	/**
	 * Whether this is a "service provider" or a "library provider". Services providers binds its
	 * bean types to services provided by other processes by contrast to library provider whose beans
	 * are implemented by in memory objects. 
	 * 
	 * @return
	 */
	boolean isLibraryProvider();
	
	
}