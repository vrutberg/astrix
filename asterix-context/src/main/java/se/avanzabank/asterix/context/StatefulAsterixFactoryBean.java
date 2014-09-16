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
package se.avanzabank.asterix.context;

/**
 * 
 * @author Elias Lindholm (elilin)
 *
 * @param <T>
 */
final class StatefulAsterixFactoryBean<T> implements AsterixFactoryBean<T>, AsterixDecorator, AsterixEventBusAware {

	private final AsterixFactoryBean<T> targetFactory;
	private AsterixEventBus eventBus;
	
	public StatefulAsterixFactoryBean(AsterixFactoryBean<T> targetFactory) {
		if (!targetFactory.getBeanType().isInterface()) {
			throw new IllegalArgumentException("Can only create stateful asterix beans if bean is exported using an interface." +
											   " targetBeanType=" + targetFactory.getBeanType().getName() + 
											   " beanFactoryType=" + targetFactory.getClass().getName());
		}
		this.targetFactory = targetFactory;
	}

	@Override
	public T create(String optionalQualifier) {
		return StatefulAsterixBean.create(targetFactory, optionalQualifier, eventBus);
	}

	@Override
	public Class<T> getBeanType() {
		return targetFactory.getBeanType();
	}

	@Override
	public Object getTarget() {
		return targetFactory;
	}

	@Override
	public void setEventBus(AsterixEventBus eventBus) {
		this.eventBus = eventBus;
	}

}