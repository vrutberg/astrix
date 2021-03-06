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
package com.avanza.astrix.beans.config;

import com.avanza.astrix.config.BooleanSetting;
import com.avanza.astrix.config.DynamicBooleanProperty;
import com.avanza.astrix.config.DynamicConfig;
import com.avanza.astrix.config.DynamicIntProperty;
import com.avanza.astrix.config.DynamicLongProperty;
import com.avanza.astrix.config.DynamicStringProperty;
import com.avanza.astrix.config.IntSetting;
import com.avanza.astrix.config.LongSetting;
import com.avanza.astrix.config.MapConfigSource;
import com.avanza.astrix.config.StringSetting;

/**
 * 
 * @author Elias Lindholm (elilin)
 *
 */
final class AstrixConfigImpl implements AstrixConfig {
	
	private final DynamicConfig config;
	private final MapConfigSource settings;

	public AstrixConfigImpl(DynamicConfig config, MapConfigSource settings) {
		this.config = config;
		this.settings = settings;
	}

	@Override
	public DynamicLongProperty get(LongSetting setting) {
		return setting.getFrom(config);
	}

	@Override
	public DynamicIntProperty get(IntSetting setting) {
		return setting.getFrom(config);
	}

	@Override
	public DynamicBooleanProperty get(BooleanSetting setting) {
		return setting.getFrom(config);
	}
	
	@Override
	public DynamicStringProperty get(StringSetting setting) {
		return setting.getFrom(config);
	}

	@Override
	public DynamicStringProperty getStringProperty(String name,
			String defaultValue) {
		return config.getStringProperty(name, defaultValue);
	}
	
	@Override
	public DynamicConfig getConfig() {
		return this.config;
	}
	
	@Override
	public void set(String setting, String value) {
		this.settings.set(setting, value);
	}
	
}	
