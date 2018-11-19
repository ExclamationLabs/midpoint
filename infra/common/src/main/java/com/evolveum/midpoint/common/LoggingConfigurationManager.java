/*
 * Copyright (c) 2010-2017 Evolveum
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

package com.evolveum.midpoint.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

import com.evolveum.midpoint.common.configuration.api.MidpointConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang.StringUtils;
import org.slf4j.bridge.SLF4JBridgeHandler;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.util.StatusPrinter;

import com.evolveum.midpoint.schema.internals.InternalsConfig;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AppenderConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.AuditingConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ClassLoggerConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FileAppenderConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.LoggingConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SubSystemLoggerConfigurationType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.SyslogAppenderConfigurationType;

public class LoggingConfigurationManager {

	public static final String AUDIT_LOGGER_NAME = "com.evolveum.midpoint.audit.log";

	private final static Trace LOGGER = TraceManager.getTrace(LoggingConfigurationManager.class);

    private static final String REQUEST_FILTER_LOGGER_CLASS_NAME = "com.evolveum.midpoint.web.util.MidPointProfilingServletFilter";
    private static final String PROFILING_ASPECT_LOGGER = "com.evolveum.midpoint.util.aspect.ProfilingDataManager";
    private static final String IDM_PROFILE_APPENDER = "IDM_LOG";
	private static final String ALT_APPENDER_NAME = "ALT_LOG";

	private static String currentlyUsedVersion = null;

    public static void configure(LoggingConfigurationType config, String version,
		    MidpointConfiguration midpointConfiguration, OperationResult result) throws SchemaException {

		OperationResult res = result.createSubresult(LoggingConfigurationManager.class.getName()+".configure");
		
		if (InternalsConfig.isAvoidLoggingChange()) {
			LOGGER.info("IGNORING change of logging configuration (current config version: {}, new version {}) because avoidLoggingChange=true", currentlyUsedVersion, version);
			res.recordNotApplicableIfUnknown();
			return;
		}

        if (currentlyUsedVersion != null) {
		    LOGGER.info("Applying logging configuration (currently applied version: {}, new version: {})", currentlyUsedVersion, version);
        } else {
            LOGGER.info("Applying logging configuration (version {})", version);
        }
        currentlyUsedVersion = version;

        // JUL Bridge initialization was here. (SLF4JBridgeHandler)
        // But it was moved to a later phase as suggested by http://jira.qos.ch/browse/LOGBACK-740
        
		// Initialize JUL bridge
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        
		//Get current log configuration
		LoggerContext lc = (LoggerContext) TraceManager.getILoggerFactory();

		//Prepare configurator in current context
		JoranConfigurator configurator = new JoranConfigurator();
		configurator.setContext(lc);

		//Generate configuration file as string
		String configXml = prepareConfiguration(config, midpointConfiguration);

		if (LOGGER.isTraceEnabled()) {
			LOGGER.trace("New logging configuration:");
			LOGGER.trace(configXml);
		}

		InputStream cis = new ByteArrayInputStream(configXml.getBytes());
		LOGGER.info("Resetting current logging configuration");
		lc.getStatusManager().clear();
		//Set all loggers to error
		for (Logger l : lc.getLoggerList()) {
			LOGGER.trace("Disable logger: {}", l);
			l.setLevel(Level.ERROR);
		}
		// Reset configuration
		lc.reset();
		//Switch to new logging configuration
		lc.setName("MidPoint");
		try {
			configurator.doConfigure(cis);
			LOGGER.info("New logging configuration applied");
		} catch (JoranException | NumberFormatException e) {
			System.out.println("Error during applying logging configuration: " + e.getMessage());
			LOGGER.error("Error during applying logging configuration: " + e.getMessage(), e);
			result.createSubresult("Applying logging configuration.").recordFatalError(e.getMessage(), e);
		}

		//Get messages if error occurred;
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		StatusPrinter.setPrintStream(new PrintStream(baos));
		StatusPrinter.print(lc);

		String internalLog = null;
		try {
			internalLog = baos.toString("UTF8");
		} catch (UnsupportedEncodingException e) {
			// should never happen
			LOGGER.error("Woops?", e);
		}

		if (!StringUtils.isEmpty(internalLog)) {
			//Parse internal log
			res.recordSuccess();
			for (String internalLogLine : internalLog.split("\n")) {
				if (internalLogLine.contains("|-ERROR"))
					res.recordPartialError(internalLogLine);
				res.appendDetail(internalLogLine);
			}
			LOGGER.trace("LogBack internal log:\n{}",internalLog);
		} else {
			res.recordSuccess();
		}
		
		// Initialize JUL bridge
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
	}

	private static String prepareConfiguration(LoggingConfigurationType config, MidpointConfiguration midpointConfiguration)
			throws SchemaException {

		if (null == config) {
			throw new IllegalArgumentException("Configuration can't be null");
		}

		StringBuilder sb = new StringBuilder();
		sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		boolean debug = Boolean.TRUE.equals(config.isDebug());
		sb.append("<configuration scan=\"false\" debug=\"").append(debug).append("\">\n");

		//find and configure ALL logger and bring it to top of turbo stack
		for (SubSystemLoggerConfigurationType ss : config.getSubSystemLogger()) {
			if ("ALL".contentEquals(ss.getComponent().name())) {
				defineTurbo(sb, ss);
			}
		}

		//Generate subsystem logging quickstep
		for (SubSystemLoggerConfigurationType ss : config.getSubSystemLogger()) {
			if (null == ss.getComponent() || null == ss.getLevel()) {
				LOGGER.error("Subsystem ({}) or level ({})is null", ss.getComponent(), ss.getLevel());
				continue;
			}

			//skip disabled subsystem logger
			if ("OFF".equals(ss.getLevel().name())) {
				continue;
			}
			
			//All ready defined above
			if ("ALL".contentEquals(ss.getComponent().name())) {
				continue;
			}			
			defineTurbo(sb, ss);
		}

		boolean rootAppenderDefined = StringUtils.isNotEmpty(config.getRootLoggerAppender());
		boolean altAppenderEnabled = isAltAppenderEnabled(midpointConfiguration);
		boolean altAppenderCreated = false;

		//Generate appenders configuration
		for (AppenderConfigurationType appender : config.getAppender()) {
			boolean createAlt = altAppenderEnabled && rootAppenderDefined && config.getRootLoggerAppender().equals(appender.getName());
			prepareAppenderConfiguration(sb, appender, config, createAlt, midpointConfiguration);
			altAppenderCreated = altAppenderCreated || createAlt;
		}

		//define root appender if defined
		if (rootAppenderDefined) {
			sb.append("\t<root level=\"");
			sb.append(config.getRootLoggerLevel());
			sb.append("\">\n");
			sb.append("\t\t<appender-ref ref=\"");
			sb.append(config.getRootLoggerAppender());
			sb.append("\" />\n");
			if (altAppenderCreated) {
				sb.append("\t\t<appender-ref ref=\"" + ALT_APPENDER_NAME + "\"/>");
			}
			sb.append("\t</root>\n");
		}

		//Generate class based loggers
		for (ClassLoggerConfigurationType logger : config.getClassLogger()) {
			sb.append("\t<logger name=\"");
			sb.append(logger.getPackage());
			sb.append("\" level=\"");
			sb.append(logger.getLevel());
			sb.append("\"");
			//if logger specific appender is defined
			if (null != logger.getAppender() && !logger.getAppender().isEmpty()) {
				sb.append(" additivity=\"false\">\n");
				for (String appenderName : logger.getAppender()) {
					sb.append("\t\t<appender-ref ref=\"");
					sb.append(appenderName);
					sb.append("\"/>");
				}
				sb.append("\t</logger>\n");
			} else {
				sb.append("/>\n");
			}
		}
		
		generateAuditingLogConfig(config.getAuditing(), sb);

		if (null != config.getAdvanced()) {
			for (Object item : config.getAdvanced().getContent()) {
				sb.append(item.toString());
				sb.append("\n");
			}
		}
		
		// LevelChangePropagator to propagate log level changes to JUL
		// this keeps us from performance impact of disable JUL logging statements
		// WARNING: if deployed in Tomcat then this propagates only to the JUL loggers in current classloader.
		// It means that ICF connector loggers are not affected by this
		// MAGIC: moved to the end of the "file" as suggested in http://jira.qos.ch/browse/LOGBACK-740
		sb.append("\t<contextListener class=\"ch.qos.logback.classic.jul.LevelChangePropagator\">\n");
		sb.append("\t\t<resetJUL>true</resetJUL>\n");
		sb.append("\t</contextListener>\n");
		
		sb.append("</configuration>");
		return sb.toString();
	}

	private static void prepareAppenderConfiguration(StringBuilder sb, AppenderConfigurationType appender,
			LoggingConfigurationType config, boolean createAltForThisAppender,
			MidpointConfiguration midpointConfiguration) throws SchemaException {
		if (appender instanceof FileAppenderConfigurationType) {
			prepareFileAppenderConfiguration(sb, (FileAppenderConfigurationType) appender, config);
		} else if (appender instanceof SyslogAppenderConfigurationType) {
			prepareSyslogAppenderConfiguration(sb, (SyslogAppenderConfigurationType) appender, config);
		} else {
			throw new SchemaException("Unknown appender configuration "+appender);
		}
		if (createAltForThisAppender) {
			prepareAltAppenderConfiguration(sb, appender, midpointConfiguration);
		}
	}

	private static void prepareAltAppenderConfiguration(StringBuilder sb, AppenderConfigurationType appender,
			MidpointConfiguration midpointConfiguration) {
		String altFilename = getAltAppenderFilename(midpointConfiguration);
		String altPrefix = getAltAppenderPrefix(midpointConfiguration);
		if (StringUtils.isNotEmpty(altFilename)) {
			sb.append("<appender name=\"" + ALT_APPENDER_NAME + "\" class=\"ch.qos.logback.core.FileAppender\">\n")
					.append("\t\t\t\t<file>").append(altFilename).append("</file>\n")
					.append("\t\t\t\t<layout class=\"ch.qos.logback.classic.PatternLayout\">\n")
					.append("\t\t\t\t\t<pattern>").append(altPrefix).append(appender.getPattern()).append("</pattern>\n")
					.append("\t\t\t\t</layout>\n")
					.append("\t\t\t</appender>");
		} else {
			sb.append("<appender name=\"" + ALT_APPENDER_NAME + "\" class=\"ch.qos.logback.core.ConsoleAppender\">\n")
					.append("\t\t\t\t<layout class=\"ch.qos.logback.classic.PatternLayout\">\n")
					.append("\t\t\t\t\t<pattern>").append(altPrefix).append(appender.getPattern()).append("</pattern>\n")
					.append("\t\t\t\t</layout>\n")
					.append("\t\t\t</appender>");
		}
	}

	private static boolean isAltAppenderEnabled(MidpointConfiguration midpointConfiguration) {
		Configuration config = midpointConfiguration.getConfiguration();
		return config != null && "true".equals(config.getString(MidpointConfiguration.MIDPOINT_LOGGING_ALT_ENABLED_PROPERTY));
	}

	private static String getAltAppenderPrefix(MidpointConfiguration midpointConfiguration) {
		Configuration config = midpointConfiguration.getConfiguration();
		return config != null ? config.getString(MidpointConfiguration.MIDPOINT_LOGGING_ALT_PREFIX_PROPERTY, "") : "";
	}

	private static String getAltAppenderFilename(MidpointConfiguration midpointConfiguration) {
		Configuration config = midpointConfiguration.getConfiguration();
		return config != null ? config.getString(MidpointConfiguration.MIDPOINT_LOGGING_ALT_FILENAME_PROPERTY) : null;
	}

	private static void prepareFileAppenderConfiguration(StringBuilder sb, FileAppenderConfigurationType appender, LoggingConfigurationType config) {
		String fileName = appender.getFileName();
		String filePattern = appender.getFilePattern();
		boolean isPrudent = Boolean.TRUE.equals(appender.isPrudent());
		boolean isAppend = Boolean.TRUE.equals(appender.isAppend());

		boolean isRolling = false;
		String appenderClass = "ch.qos.logback.core.FileAppender";
		if (filePattern != null || (appender.getMaxHistory() != null && appender.getMaxHistory() > 0) || !StringUtils.isEmpty(appender.getMaxFileSize()) ) {
			isRolling = true;
			appenderClass = "ch.qos.logback.core.rolling.RollingFileAppender"; 
		}
		
		prepareCommonAppenderHeader(sb, appender, config, appenderClass);

		if (!isPrudent) {
			appendProp(sb, "file", fileName);
			appendProp(sb, "append", isAppend);
		} else {
			appendProp(sb, "prudent", true);
		}

		if (isRolling) {
			//rolling policy
			sb.append("\t\t<rollingPolicy class=\"ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy\">\n");
			sb.append("\t\t\t<fileNamePattern>");
			sb.append(filePattern);
			sb.append("</fileNamePattern>\n");
			if (appender.getMaxHistory() != null && appender.getMaxHistory() > 0) {
				sb.append("\t\t\t<maxHistory>");
				sb.append(appender.getMaxHistory());
				sb.append("</maxHistory>\n");
			}
			sb.append("\t\t\t<cleanHistoryOnStart>true</cleanHistoryOnStart>\n");

			if (!StringUtils.isEmpty(appender.getMaxFileSize())) {
				sb.append("\t\t\t<maxFileSize>");
				sb.append(appender.getMaxFileSize());
				sb.append("</maxFileSize>\n");
			}
			sb.append("\t\t</rollingPolicy>\n");
		}
		
		prepareCommonAppenderFooter(sb, appender);

	}
	
	private static void prepareSyslogAppenderConfiguration(StringBuilder sb, SyslogAppenderConfigurationType appender, LoggingConfigurationType config) {

		prepareCommonAppenderHeader(sb, appender, config, "ch.qos.logback.classic.net.SyslogAppender");

		appendProp(sb, "syslogHost", appender.getSyslogHost());
		appendProp(sb, "port", appender.getPort());
		appendProp(sb, "facility", appender.getFacility());
		appendProp(sb, "suffixPattern", appender.getSuffixPattern());
		appendProp(sb, "stackTracePattern", appender.getStackTracePattern());
		appendProp(sb, "throwableExcluded", appender.isThrowableExcluded());
		
		prepareCommonAppenderFooter(sb, appender);

	}

	private static void appendProp(StringBuilder sb, String name, Object value) {
		if (value != null) {
			sb.append("\t\t<").append(name).append(">");
			sb.append(value);
			sb.append("</").append(name).append(">\n");
		}
	}

	private static void prepareCommonAppenderHeader(StringBuilder sb,
			AppenderConfigurationType appender, LoggingConfigurationType config, String appenderClass) {
		sb.append("\t<appender name=\"").append(appender.getName()).append("\" class=\"").append(appenderClass).append("\">\n");

        //Apply profiling appender filter if necessary
        if(IDM_PROFILE_APPENDER.equals(appender.getName())){
            for(ClassLoggerConfigurationType cs: config.getClassLogger()){
                if(REQUEST_FILTER_LOGGER_CLASS_NAME.equals(cs.getPackage()) || PROFILING_ASPECT_LOGGER.endsWith(cs.getPackage())){
                    LOGGER.debug("Defining ProfilingLogbackFilter to {} appender.", appender.getName());
                    sb.append(defineProfilingLogbackFilter());
                }
            }
        }
	}
	
	private static void prepareCommonAppenderFooter(StringBuilder sb, AppenderConfigurationType appender) {
		sb.append("\t\t<encoder>\n");
		sb.append("\t\t\t<pattern>");
		sb.append(appender.getPattern());
		sb.append("</pattern>\n");
		sb.append("\t\t</encoder>\n");
		sb.append("\t</appender>\n");
	}

	private static void generateAuditingLogConfig(AuditingConfigurationType auditing, StringBuilder sb) {
		sb.append("\t<logger name=\"");
		sb.append(AUDIT_LOGGER_NAME);
		sb.append("\" level=\"");
		if (auditing != null && (auditing.isEnabled() == null || auditing.isEnabled())) {
			if (auditing.isDetails() != null && auditing.isDetails()) {
				sb.append("DEBUG");
			} else {
				sb.append("INFO");
			}
		} else {
			sb.append("OFF");
		}
		sb.append("\"");
		//if logger specific appender is defined
		if (auditing != null && auditing.getAppender() != null && !auditing.getAppender().isEmpty()) {
			sb.append(" additivity=\"false\">\n");
			for (String appenderName : auditing.getAppender()) {
				sb.append("\t\t<appender-ref ref=\"");
				sb.append(appenderName);
				sb.append("\"/>");
			}
			sb.append("\t</logger>\n");
		} else {
			sb.append("/>\n");
		}
	}

	private static void defineTurbo(StringBuilder sb, SubSystemLoggerConfigurationType ss) {
		sb.append("\t<turboFilter class=\"com.evolveum.midpoint.util.logging.MDCLevelTurboFilter\">\n");
		sb.append("\t\t<MDCKey>subsystem</MDCKey>\n");
		sb.append("\t\t<MDCValue>");
		sb.append(ss.getComponent().name());
		sb.append("</MDCValue>\n");
		sb.append("\t\t<level>");
		sb.append(ss.getLevel().name());
		sb.append("</level>\n");
		sb.append("\t\t<OnMatch>ACCEPT</OnMatch>\n");
		sb.append("\t</turboFilter>\n");
	}

    private static String defineProfilingLogbackFilter(){
        return ("\t<filter class=\"com.evolveum.midpoint.util.logging.ProfilingLogbackFilter\" />\n");
    }

    public static void dummy() {
    }

}
