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
package com.avanza.astrix.context.module;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import javax.annotation.PreDestroy;

import org.hamcrest.Matchers;
import org.junit.Test;

import com.avanza.astrix.beans.factory.AstrixBeanPostProcessor;
import com.avanza.astrix.beans.factory.AstrixBeans;
import com.avanza.astrix.beans.factory.CircularDependency;


public class ModuleTest {
	
	
	@Test
	public void exportedBeansAreAccessibleOutsideTheModule() throws Exception {
		ModulesConfigurer modulesConfigurer = new ModulesConfigurer();
		modulesConfigurer.register(new Module() {
			@Override
			public void prepare(ModuleContext context) {
				context.bind(Ping.class, PingWithInternalDriver.class);
				context.export(Ping.class);
			}
		});
		Modules modules = modulesConfigurer.configure();
		assertEquals(PingWithInternalDriver.class, modules.getInstance(Ping.class).getClass());
	}
	
	
	@Test
	public void createdBeansAreCached() throws Exception {
		ModulesConfigurer modulesConfigurer = new ModulesConfigurer();
		modulesConfigurer.register(new Module() {
			@Override
			public void prepare(ModuleContext context) {
				context.bind(Ping.class, PingWithInternalDriver.class);
				context.export(Ping.class);
			}
		});
		Modules modules = modulesConfigurer.configure();
		Ping ping1 = modules.getInstance(Ping.class);
		Ping ping2 = modules.getInstance(Ping.class);
		assertSame(ping1, ping2);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void itsNotAllowedToPullNonExportedInstancesFromAModule() throws Exception {
		ModulesConfigurer modulesConfigurer = new ModulesConfigurer();
		modulesConfigurer.register(new Module() {
			@Override
			public void prepare(ModuleContext context) {
				context.bind(Ping.class, PingWithInternalDriver.class);
				context.export(Ping.class);
			}
		});
		Modules modules = modulesConfigurer.configure();
		modules.getInstance(PingDriverImpl.class);
	}
	
	@Test
	public void itsPossibleToImportBeansExportedByOtherModules() throws Exception {
		ModulesConfigurer modulesConfigurer = new ModulesConfigurer();
		modulesConfigurer.register(new Module() {
			@Override
			public void prepare(ModuleContext context) {
				context.bind(Ping.class, PingWithImportedDriver.class);
				context.importType(PingDriver.class);
				context.export(Ping.class);
			}
		});
		modulesConfigurer.register(new Module() {
			@Override
			public void prepare(ModuleContext context) {
				context.bind(PingDriver.class, PingDriverImpl.class);
				context.export(PingDriver.class);
			}
		});
		Modules modules = modulesConfigurer.configure();
		assertEquals(PingWithImportedDriver.class, modules.getInstance(Ping.class).getClass());
	}
	
	@Test
	public void destroyingAModulesInstanceInvokesDestroyAnnotatedMethodsExactlyOnce() throws Exception {
		ModulesConfigurer modulesConfigurer = new ModulesConfigurer();
		modulesConfigurer.register(new Module() {
			@Override
			public void prepare(ModuleContext context) {
				context.bind(Ping.class, PingWithImportedDriver.class);
				
				context.importType(PingDriver.class);
				
				context.export(Ping.class);
			}
		});
		modulesConfigurer.register(new Module() {
			@Override
			public void prepare(ModuleContext context) {
				context.bind(PingDriver.class, PingDriverImpl.class);
				
				context.export(PingDriver.class);
			}
		});

		// NOTE: Create Ping to ensure that PingDriver is note destroyed twice.
		//       Once when module containing ping is destroyed, and once when drive 
		//       module is destroyed
		Modules modules = modulesConfigurer.configure();
		modules.getInstance(Ping.class); 
		PingDriver pingDriver = modules.getInstance(PingDriver.class);
		
		modules.destroy();
		
		assertEquals(1, pingDriver.destroyCount());
	}
	
	@Test
	public void multipleExportedBeansOfSameType_UsesFirstProvider() throws Exception {
		ModulesConfigurer modulesConfigurer = new ModulesConfigurer();
		modulesConfigurer.register(new Module() {
			@Override
			public void prepare(ModuleContext moduleContext) {
				moduleContext.bind(Ping.class, NormalPing.class);
				moduleContext.export(Ping.class);
			}
		});
		modulesConfigurer.register(new Module() {
			@Override
			public void prepare(ModuleContext moduleContext) {
				moduleContext.bind(Ping.class, ReversePing.class);
				moduleContext.export(Ping.class);
			}
		});
		
		Modules modules = modulesConfigurer.configure();
		Ping ping = modules.getInstance(Ping.class);
		assertEquals("not reversed", ping.ping("not reversed"));
	}
	
	@Test
	public void itsPossibleToImportAllExportedBeansOfAGivenType() throws Exception {
		ModulesConfigurer modulesConfigurer = new ModulesConfigurer();
		modulesConfigurer.register(new Module() {
			@Override
			public void prepare(ModuleContext moduleContext) {
				moduleContext.bind(PingCollector.class, PingCollectorImpl.class);
				moduleContext.importType(Ping.class);
				moduleContext.export(PingCollector.class);
			}
		});
		modulesConfigurer.register(new Module() {
			@Override
			public void prepare(ModuleContext moduleContext) {
				moduleContext.bind(Ping.class, NormalPing.class);
				moduleContext.export(Ping.class);
			}
		});
		modulesConfigurer.register(new Module() {
			@Override
			public void prepare(ModuleContext moduleContext) {
				moduleContext.bind(Ping.class, ReversePing.class);
				moduleContext.export(Ping.class);
			}
		});
		Modules modules = modulesConfigurer.configure();
		PingCollector pingPluginCollector = modules.getInstance(PingCollector.class);
		assertEquals(2, pingPluginCollector.pingInstanceCount());
	}
	
	@Test
	public void injectingAllBeansOfImportedTypesWithNoRegisteredProviders() throws Exception {
		ModulesConfigurer modulesConfigurer = new ModulesConfigurer();
		modulesConfigurer.register(new Module() {
			@Override
			public void prepare(ModuleContext moduleContext) {
				moduleContext.bind(PingCollector.class, PingCollectorImpl.class);
				moduleContext.importType(Ping.class);
				moduleContext.export(PingCollector.class);
			}
		});
		Modules modules = modulesConfigurer.configure();
		PingCollector pingPluginCollector = modules.getInstance(PingCollector.class);
		assertEquals(0, pingPluginCollector.pingInstanceCount());
	}
	
	@Test
	public void multipleExportedBeansOfImportedType_UsesFirstRegisteredProvider() throws Exception {
		ModulesConfigurer modulesConfigurer = new ModulesConfigurer();
		modulesConfigurer.register(new Module() {
			@Override
			public void prepare(ModuleContext moduleContext) {
				moduleContext.bind(PingCollector.class, SinglePingCollector.class);
				moduleContext.importType(Ping.class);
				moduleContext.export(PingCollector.class);
			}
		});
		modulesConfigurer.register(new Module() {
			@Override
			public void prepare(ModuleContext moduleContext) {
				moduleContext.bind(Ping.class, ReversePing.class);
				moduleContext.export(Ping.class);
			}
		});
		modulesConfigurer.register(new Module() {
			@Override
			public void prepare(ModuleContext moduleContext) {
				moduleContext.bind(Ping.class, NormalPing.class);
				moduleContext.export(Ping.class);
			}
		});
		Modules modules = modulesConfigurer.configure();
		PingCollector pingPluginCollector = modules.getInstance(PingCollector.class);
		assertEquals("oof", pingPluginCollector.getPing().ping("foo"));
	}
	
	@Test
	public void getBeansOfTypeReturnsAllExportedBeansOfAGivenType() throws Exception {
		ModulesConfigurer modulesConfigurer = new ModulesConfigurer();
		modulesConfigurer.register(new Module() {
			@Override
			public void prepare(ModuleContext moduleContext) {
				moduleContext.bind(PingCollector.class, SinglePingCollector.class);
				moduleContext.importType(Ping.class);
				moduleContext.export(PingCollector.class);
			}
		});
		modulesConfigurer.register(new Module() {
			@Override
			public void prepare(ModuleContext moduleContext) {
				moduleContext.bind(Ping.class, ReversePing.class);
				moduleContext.export(Ping.class);
			}
		});
		modulesConfigurer.register(new Module() {
			@Override
			public void prepare(ModuleContext moduleContext) {
				moduleContext.bind(Ping.class, NormalPing.class);
				moduleContext.export(Ping.class);
			}
		});
		Modules modules = modulesConfigurer.configure();
		Collection<Ping> pings = modules.getAll(Ping.class);
		assertEquals(2, pings.size());
		assertThat(pings, hasItem(Matchers.<Ping>instanceOf(NormalPing.class)));
		assertThat(pings, hasItem(Matchers.<Ping>instanceOf(ReversePing.class)));
	}
	
	@Test
	public void beanPostProcessorAreAppliedToAllCreatedBeansThatAreNotCreatedBeforeRegisteringThePostProcessor() throws Exception {
		ModulesConfigurer modulesConfigurer = new ModulesConfigurer();
		final BlockingQueue<Object> postProcessedBeans = new LinkedBlockingQueue<Object>();
		modulesConfigurer.register(new Module() {
			@Override
			public void prepare(ModuleContext moduleContext) {
				moduleContext.bind(Ping.class, ReversePing.class);
				moduleContext.export(Ping.class);
			}
		});
		modulesConfigurer.registerBeanPostProcessor(new AstrixBeanPostProcessor() {
			@Override
			public void postProcess(Object bean, AstrixBeans astrixBeans) {
				postProcessedBeans.add(bean);
			}
		});

		Modules modules = modulesConfigurer.configure();
		modules.getInstance(Ping.class); // trigger creation of Ping
		assertThat(postProcessedBeans.poll(), instanceOf(ReversePing.class));
	}
	
	@Test
	public void setterInjectedDependencies() throws Exception {
		ModulesConfigurer modulesConfigurer = new ModulesConfigurer();
		modulesConfigurer.register(new NamedModule() {
			@Override
			public void prepare(ModuleContext moduleContext) {
				moduleContext.bind(AType.class, A.class);
				
				moduleContext.importType(BType.class);
				
				moduleContext.export(AType.class);
			}
			@Override
			public String name() {
				return "A";
			}
		});
		modulesConfigurer.register(new NamedModule() {
			@Override
			public void prepare(ModuleContext moduleContext) {
				moduleContext.bind(BType.class, B.class);
				moduleContext.importType(CType.class);
				moduleContext.export(BType.class);
			}
			@Override
			public String name() {
				return "B";
			}
		});
		modulesConfigurer.register(new NamedModule() {
			@Override
			public void prepare(ModuleContext moduleContext) {
				moduleContext.bind(CType.class, C.class);
				moduleContext.export(CType.class);
			}
			@Override
			public String name() {
				return "C";
			}
		});
		
		Modules modules = modulesConfigurer.configure();

		assertEquals(A.class, modules.getInstance(AType.class).getClass());
		assertThat(modules.getInstance(AType.class).getB(), instanceOf(B.class));
	}
	
	@Test
	public void includesBoundComponentsInSameModuleWhenInjectingListOfAllInstancesOfGivenType() throws Exception {
		ModulesConfigurer modulesConfigurer = new ModulesConfigurer();
		modulesConfigurer.register(new Module() {
			@Override
			public void prepare(ModuleContext moduleContext) {
				moduleContext.bind(Ping.class, NormalPing.class);
				moduleContext.bind(PingCollector.class, PingCollectorImpl.class);
				
				moduleContext.export(PingCollector.class);
			}
		});
		
		Modules modules = modulesConfigurer.configure();
		assertEquals(1, modules.getInstance(PingCollector.class).pingInstanceCount());
	}
	
	@Test
	public void exportingMultipleInterfaceFromSameInstance() throws Exception {
		ModulesConfigurer modulesConfigurer = new ModulesConfigurer();
		modulesConfigurer.register(new NamedModule() {
			@Override
			public void prepare(ModuleContext moduleContext) {
				moduleContext.bind(Ping.class, SuperPing.class);
				moduleContext.bind(PingCollector.class, SuperPing.class);
				
				moduleContext.export(Ping.class);
				moduleContext.export(PingCollector.class);
			}
			@Override
			public String name() {
				return "ping";
			}
		});
		Modules modules = modulesConfigurer.configure();
		Ping ping = modules.getInstance(Ping.class);
		PingCollector pingCollector = modules.getInstance(PingCollector.class);
		assertNotNull(ping);
		assertSame(ping, pingCollector);
	}
	
//	@Test
	public void strategiesSupport() throws Exception {
		ModulesConfigurer modulesConfigurer = new ModulesConfigurer();
		modulesConfigurer.register(new NamedModule() {
			@Override
			public void prepare(ModuleContext moduleContext) {
				moduleContext.bind(Ping.class, PingWithImportedDriver.class);

				moduleContext.importType(PingDriver.class);
				
				moduleContext.export(Ping.class);
			}
			@Override
			public String name() {
				return "ping";
			}
		});
		Modules modules = modulesConfigurer.configure();
		
		Ping ping = modules.getInstance(Ping.class);
		PingCollector pingCollector = modules.getInstance(PingCollector.class);
		assertNotNull(ping);
		assertSame(ping, pingCollector);
	}
	
	
	
	
	// TODO: detect circular dependencies!
//	@Test(expected = CircularModuleDependency.class)
	public void circularDependenciesAreNotAllowedOnAModularLevel() throws Exception {
		/*
		 * Class dependencies contains no cycles:
		 * B 
		 * C -> D
		 * D -> B
		 * 
		 * But module dependencies contains cycle:
		 * 
		 * ModuleB -> ModuleD
		 * ModuleD -> ModuleB
		 */
		ModulesConfigurer modulesConfigurer = new ModulesConfigurer();
		modulesConfigurer.register(new NamedModule() {
			@Override
			public void prepare(ModuleContext moduleContext) {
				moduleContext.bind(BType.class, new BType() {
				});
				moduleContext.bind(CType.class, new CType() {
					@AstrixInject
					public void setDType(DType D) {
					}
				});
				moduleContext.importType(DType.class);
				moduleContext.export(BType.class);
			}
			@Override
			public String name() {
				return "ModuleB";
			}
		});
		modulesConfigurer.register(new NamedModule() {
			@Override
			public void prepare(ModuleContext moduleContext) {
				moduleContext.bind(DType.class, new DType() {
					@AstrixInject
					public void setBtType(BType b) {
					}
				});
				moduleContext.importType(BType.class);
				
				moduleContext.export(DType.class);
			}
			@Override
			public String name() {
				return "ModuleD";
			}
		});
		Modules modules = modulesConfigurer.configure();
		
		modules.getInstance(BType.class);
	}
	
//	@Test(expected = CircularDependency.class)
	public void circularDependenciesAreNotAllowedOnInsideAModule() throws Exception {
		/*
		 * Class dependencies contains cycle:
		 * B -> C, C -> B
		 * 
		 */
		ModulesConfigurer modulesConfigurer = new ModulesConfigurer();
		modulesConfigurer.register(new NamedModule() {
			@Override
			public void prepare(ModuleContext moduleContext) {
				moduleContext.bind(BType.class, new BType() {
					@AstrixInject
					public void setCType(CType b) {
					}
				});
				moduleContext.bind(CType.class, new CType() {
					@AstrixInject
					public void setBType(BType b) {
					}
				});
				moduleContext.export(BType.class);
			}
			@Override
			public String name() {
				return "b";
			}
		});
		Modules modules = modulesConfigurer.configure();
		modules.getInstance(BType.class);
	}
	
	public interface AType {
		BType getB();
	}
	
	public interface BType {
	}
	
	public interface CType {
	}
	
	public interface DType {
	}
	
	public static class A implements AType {
		private BType b;
		
		@AstrixInject
		public void setB(BType b) {
			this.b = b;
		}
		
		@Override
		public BType getB() {
			return this.b;
		}
	}
	
	public static class B implements BType  {
		private CType c;

		@AstrixInject
		public void setC(CType c) {
			this.c = c;
		}
	}
	
	public static class C implements CType  {
	}
	
	public static class SuperPing implements Ping, PingCollector {

		@Override
		public int pingInstanceCount() {
			return 1;
		}

		@Override
		public Ping getPing() {
			return this;
		}

		@Override
		public String ping(String msg) {
			return msg;
		}
		
	}
	

	public interface Ping {
		String ping(String msg);
	}
	
	public interface PingPlugin {
		String ping(String msg);
	}
	
	public interface PingDriver {
		String ping(String msg);
		int destroyCount();
	}
	
	public static class PingDriverImpl implements PingDriver {
		private int destroyCount = 0;
		public String ping(String msg) {
			return msg;
		}
		@Override
		public int destroyCount() {
			return destroyCount;
		}
		
		@PreDestroy
		public void destroy() {
			destroyCount++;
		}
	}
	
	public interface PingCollector {
		int pingInstanceCount();
		Ping getPing();
	}
	
	public static class PingCollectorImpl implements PingCollector {
		private final Collection<Ping> pingInstances;

		public PingCollectorImpl(List<Ping> pingPlugins) {
			this.pingInstances = pingPlugins;
		}
		
		public int pingInstanceCount() {
			return pingInstances.size();
		}
		
		@Override
		public Ping getPing() {
			throw new UnsupportedOperationException();
		}
	}
	
	public static class SinglePingCollector implements PingCollector {
		private final Ping ping;

		public SinglePingCollector(Ping ping) {
			this.ping = ping;
		}
		
		public Ping getPing() {
			return ping;
		}

		@Override
		public int pingInstanceCount() {
			return ping != null ? 1 : 0;
		}
		
	}
	
	public static class NormalPing implements Ping, PingPlugin {
		@Override
		public String ping(String msg) {
			return msg;
		}
	}
	
	public static class ReversePing implements Ping {
		@Override
		public String ping(String msg) {
			return new StringBuilder(msg).reverse().toString();
		}
	}
	
	public static class PingWithInternalDriver implements Ping {
		
		private PingDriverImpl pingDriver;
		
		public PingWithInternalDriver(PingDriverImpl pingDependency) {
			this.pingDriver = pingDependency;
		}

		@Override
		public String ping(String msg) {
			return pingDriver.ping(msg);
		}

	}
	
	public static class PingWithImportedDriver implements Ping {
		
		private PingDriver pingDriver;
		
		public PingWithImportedDriver(PingDriver pingdriver) {
			this.pingDriver = pingdriver;
		}

		@Override
		public String ping(String msg) {
			return pingDriver.ping(msg);
		}
	}
	
}
