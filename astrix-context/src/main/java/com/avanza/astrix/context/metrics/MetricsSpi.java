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
package com.avanza.astrix.context.metrics;

import java.util.function.Supplier;

import com.avanza.astrix.core.function.CheckedCommand;

import rx.Observable;

public interface MetricsSpi {
	
	<T> CheckedCommand<T> timeExecution(CheckedCommand<T> execution, String group, String name);
	
	<T> Supplier<Observable<T>> timeObservable(Supplier<Observable<T>> observableFactory, String group, String name);
}
