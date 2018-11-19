/**
 * Copyright (c) 2018 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evolveum.midpoint.web.boot;

import javax.servlet.Servlet;

import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.UpgradeProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureOrder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.SearchStrategy;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration.BeanPostProcessorsRegistrar;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;

import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;

/**
 * Custom configuration (factory) for embedded tomcat factory.
 * This is necessary, as the tomcat factory is hacking tomcat setup.
 * @see MidPointTomcatServletWebServerFactory
 * 
 * @author semancik
 */
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@Configuration
@ConditionalOnWebApplication
@Import(BeanPostProcessorsRegistrar.class)
public class EmbeddedTomcatAutoConfiguration {

	private static final Trace LOGGER = TraceManager.getTrace(EmbeddedTomcatAutoConfiguration.class);
	
	@Configuration
	@ConditionalOnClass({ Servlet.class, Tomcat.class, UpgradeProtocol.class })
	@ConditionalOnMissingBean(value = TomcatServletWebServerFactory.class, search = SearchStrategy.CURRENT)
	public static class EmbeddedTomcat {
		
		@Value( "${server.tomcat.ajp.enabled:false}" )
		private boolean enableAjp;
		
		@Value( "${server.tomcat.ajp.port:9090}" )
		private int port;
		
		@Bean
		public TomcatServletWebServerFactory tomcatEmbeddedServletContainerFactory() {
			MidPointTomcatServletWebServerFactory tomcat = new MidPointTomcatServletWebServerFactory();
			
			if(enableAjp) {
				Connector ajpConnector = new Connector("AJP/1.3");
				ajpConnector.setPort(port);
				ajpConnector.setSecure(false);
				ajpConnector.setScheme("http");
				ajpConnector.setAllowTrace(false);
				tomcat.addAdditionalTomcatConnectors(ajpConnector);
			}
			
			return tomcat;
		}

	}

}