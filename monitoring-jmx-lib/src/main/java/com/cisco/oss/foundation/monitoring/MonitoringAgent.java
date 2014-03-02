/*
 * Copyright (c) NDS Limited 2010.
 * All rights reserved.
 * No part of this program may be reproduced, translated or transmitted,
 * in any form or by any means, electronic, mechanical, photocopying,
 * recording or otherwise, or stored in any retrieval system of any nature,
 * without written permission of the copyright holder.
 */

package com.cisco.oss.foundation.monitoring;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;

import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.remoting.support.RemoteInvocation;

import com.cisco.oss.foundation.monitoring.component.config.MonitorAndManagementSettings;
import com.cisco.oss.foundation.monitoring.component.config.MonitorAndManagementSettingsMXBean;
import com.cisco.oss.foundation.monitoring.component.data.ComponentInfo;
import com.cisco.oss.foundation.monitoring.exception.AgentAlreadyRegisteredException;
import com.cisco.oss.foundation.monitoring.exception.AgentRegistrationException;
import com.cisco.oss.foundation.monitoring.exception.IncompatibleClassException;
import com.cisco.oss.foundation.monitoring.notification.NotificationMXBean;

/**
 * MonitoringAgent is the main class of NDSMXAgent library. It allows NDS CAB
 * Java components to plug into the CAB Monitoring infrastructure allowing them
 * to expose the monitoring information in the form of JMX.
 *
 * @author manojc
 * @see MonitoringMXBean
 */
public final class MonitoringAgent {
    private static final String COLON = ":";
    private static Map<String, MonitoringAgent> registeredAgents = new HashMap<String, MonitoringAgent>();
    static final Logger LOGGER = LoggerFactory.getLogger(MonitoringAgent.class.getName());
    static final Logger AUDITOR = LoggerFactory.getLogger("audit." + MonitoringAgent.class.getName());
    private MBeanServer mbs;
    private ServerInfo serverInfo;
    private ObjectName agentObjectName;
    private ObjectName appObjectName;
    private ObjectName servicesObjectName;
    private ObjectName connetctionsObjectName;
    private ObjectName monitorAndManagementSettingsObjectName = null;
    private ObjectName componentInfoObjectName = null;
    private JMXConnectorServer rmis;
    private boolean isRegistered = false;
    private boolean isNotificationRegistered = false;
    private Thread serverThread;
    private MonitoringMXBean exposedObject = null;
    private String exposedServiceURL = null;
    private String exposedObjectName = null;
    private Map<String, String> jmxEnvironmentMap = null;
    private JMXServiceURL jurl;
    private MXConfiguration configuration = null;
    private static ServiceInfo serviceInfo;
    private static ConnectionInfo connectionInfo;
    private static MonitoringAgent monitoringAgent = null;
    private boolean isComponentRegisted = false;
    private boolean isInfraRegisted = false;
    private ObjectName notificationObjectName;
    private static NotificationMXBean notificationDetails = null;

    /**
     * This is the default constructor for <code>MonitoringAgent</code>.
     * <code>register</code> method needs to be explicitly called in order to
     * initialize the monitoring infrastructure.
     *
     * @see #register(MonitoringMXBean)
     */
    private MonitoringAgent() {

    }

    /**
     * This constructor not just creates an instance of
     * <code>MonitoringAgent</code> but also initializes the monitoring
     * infrastructure and registers the mxBean object.
     *
     * @param mXBean
     *
     * @throws com.cisco.oss.foundation.monitoring.exception.AgentAlreadyRegisteredException
     * @throws com.cisco.oss.foundation.monitoring.exception.AgentRegistrationException
     * @throws com.cisco.oss.foundation.monitoring.exception.IncompatibleClassException
     *
     * @see MonitoringMXBean
     */
/*	public MonitoringAgent(MonitoringMXBean mXBean) throws AgentAlreadyRegisteredException, AgentRegistrationException,
            IncompatibleClassException {

		AppProperties.loadProperties();
		LOGGER.debug("New MonitoringAgent object getting created");
		register(mXBean);
	}*/

    /**
     * This constructor not just creates an instance of
     * <code>MonitoringAgent</code> but also initializes the monitoring
     * infrastructure with the custom configuration, and registers the mxBean
     * object.
     *
     * @param mXBean
     * @param configuration
     * @throws com.cisco.oss.foundation.monitoring.exception.AgentAlreadyRegisteredException
     * @throws com.cisco.oss.foundation.monitoring.exception.AgentRegistrationException
     * @throws com.cisco.oss.foundation.monitoring.exception.IncompatibleClassException
     * @see MonitoringMXBean
     * @see MXConfiguration
     * @see com.cisco.oss.foundation.monitoring.component.config.MonitorAndManagementSettingsMXBean
     */
	/*public MonitoringAgent(MonitoringMXBean mXBean, MXConfiguration configuration)
			throws AgentAlreadyRegisteredException, AgentRegistrationException, IncompatibleClassException {

		AppProperties.loadProperties(configuration);
		setConfiguration(configuration);
		LOGGER.debug("New MonitoringAgent object getting created");
		register(mXBean);
	}*/
    public static MonitoringAgent getInstance() {
        if (monitoringAgent == null) {
            monitoringAgent = new MonitoringAgent();
        }
        return monitoringAgent;
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            unregister();
            LOGGER.debug("MonitoringAgent object finalized");
        } catch (Exception e) {
            LOGGER.error("Could not finalize MonitoringAgent", e);
        } finally {
            super.finalize();
        }
    }

    /**
     * Two or more instance/modules of applications sharing same instance of JVM
     * can also share an instance of MonitoringAgent. One instance/module can
     * register into MonitoringAgent by supplying an authentication key. Other
     * instance/module can access already registered instance of MonitoringAgent
     * only if it knows the authentication key in addition to the name and
     * instance of the application.
     *
     * @param name     Registered application name
     * @param instance Registered instance
     * @param authKey  Authentication key supplied while registering
     * @return Instance of MonitoringAgent
     * @see com.cisco.oss.foundation.monitoring.MonitoringAgent.register()
     */
    public static MonitoringAgent getRegistedAgent(String name, String instance, String authKey) {
        LOGGER.debug("Getting MonitoringAgent instance for (" + name + ", " + instance + ")");

        return registeredAgents.get(name + COLON + instance + COLON + authKey);
    }

    /**
     * Returns true if MonitoringAgent is already registered, false otherwise
     *
     * @return true if MonitoringAgent is already registered, false otherwise
     * @see com.cisco.oss.foundation.monitoring.MonitoringAgent.register()
     */
    public synchronized boolean isRegistered() {
        return isRegistered;
    }

    /**
     * Returns JMX Service URL if MonitoringAgent is already registered, null
     * otherwise.
     *
     * @return JMX Service URL if MonitoringAgent is already registered, null
     * otherwise
     * @see com.cisco.oss.foundation.monitoring.MonitoringAgent.isRegistered()
     */
    public synchronized String getExposedServiceURL() {
        return exposedServiceURL;
    }

    /**
     * Returns ObjectName of the MBean if MonitoringAgent is already registered,
     * null otherwise.
     *
     * @return ObjectName of the MBean if MonitoringAgent is already registered,
     * null otherwise
     * @see com.cisco.oss.foundation.monitoring.MonitoringAgent.isRegistered()
     */
    public synchronized String getExposedObjectName() {
        return exposedObjectName;
    }

    /**
     * Returns MonitoringMXBean object passed to the register method.
     *
     * @return MonitoringMXBean object passed to the register method
     * @see com.cisco.oss.foundation.monitoring.MonitoringAgent.register()
     */
    public synchronized MonitoringMXBean getExposedObject() {
        return this.exposedObject;
    }

    /**
     * @return the configuration
     */
    public MXConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Sets the configuration to be used while registering the Monitoring Agent.
     * This method should be called before the agent is registered.
     *
     * @param configuration
     * @throws com.cisco.oss.foundation.monitoring.exception.AgentAlreadyRegisteredException if the agent is already registered
     */
    @SuppressWarnings("deprecation")
    public void setConfiguration(MXConfiguration configuration) throws AgentAlreadyRegisteredException {
        if (isRegistered()) {
            AUDITOR.warn("Attempt to set the configuration after MonitoringAgent is registered.");
            throw new AgentAlreadyRegisteredException("Configuration should be set before the agent is registered");
        }

        this.configuration = configuration;
        AppProperties.loadProperties(configuration);
        AUDITOR.info("Setting MonitoringAgent configuration. agentPort=" + configuration.getAgentPort()
                + ", exportedPort=" + configuration.getExportedPort() + ", monitorAndManagementSettings "
                + ((configuration.getMonitorAndManagementSettings() == null) ? "not " : "") + "set");
    }

    /**
     * Registers the mxBean object and exposes it to the outside world. Any
     * changes to this object will automatically be reflected and seen by the
     * monitoring applications.
     *
     * @param mxBean
     * @throws com.cisco.oss.foundation.monitoring.exception.AgentAlreadyRegisteredException
     * @throws com.cisco.oss.foundation.monitoring.exception.AgentRegistrationException
     * @throws com.cisco.oss.foundation.monitoring.exception.IncompatibleClassException
     * @see com.cisco.oss.foundation.monitoring.MonitoringAgent.unregister()
     */
    public synchronized void register(MonitoringMXBean mxBean) throws AgentAlreadyRegisteredException,
            AgentRegistrationException, IncompatibleClassException {
        AppProperties.loadProperties();
        if (AppProperties.isMonitoringEnabled() == false) {
            AUDITOR.info("Monitoring is disabled");
            return;
        }
        if (isComponentRegisted == true) {
            throw new AgentAlreadyRegisteredException();
        }
        register(mxBean, null);
        isComponentRegisted = true;
    }

    private static URL getSpringUrl() {

        final ProtectionDomain protectionDomain = RemoteInvocation.class.getProtectionDomain();
        String errorMessage = "creation of jar file failed for UNKNOWN reason";

        if (protectionDomain == null) {

            errorMessage = "class protection domain is null";

        } else {

            final CodeSource codeSource = protectionDomain.getCodeSource();

            if (codeSource == null) {

                errorMessage = "class code source is null";

            } else {

                final URL location = codeSource.getLocation();

                if (location == null) {

                    errorMessage = "class code source location is null";

                } else {

                    return location;

                }

            }

        }

        throw new UnsupportedOperationException(errorMessage);

    }

    public synchronized void register()

    {
        AppProperties.loadProperties();
        if (AppProperties.isMonitoringEnabled() == false) {
            LOGGER.info("Monitoring is disabled");
            return;
        }
        if (isRegistered == true) {
            return;
        }
        MonitoringMXBean defaultRegistration = new DefaultMonitoringMXBean();
        DefaultMonitoringMXBean.setEnvironment("Dummy", "Dummy");
        try {
            register(defaultRegistration, null);
            isInfraRegisted = true;
        } catch (IncompatibleClassException e) {
            LOGGER.error("Interface name is not according to JMX standard");
        } catch (AgentRegistrationException e) {
            LOGGER.trace("Infra is already register with monitoring", e);
        } catch (AgentAlreadyRegisteredException e) {
            LOGGER.trace("Infra is already register with monitoring", e);
        }


    }

    /**
     * Registers the mxBean object and exposes it to the outside world. Any
     * changes to this object will automatically be reflected and seen by the
     * monitoring applications.
     *
     * @param mxBean
     * @param authKey Authentication key, required in case MonitoringAgent instance
     *                needs to be shared
     * @throws com.cisco.oss.foundation.monitoring.exception.AgentAlreadyRegisteredException
     * @throws com.cisco.oss.foundation.monitoring.exception.AgentRegistrationException
     * @throws com.cisco.oss.foundation.monitoring.exception.IncompatibleClassException
     * @see com.cisco.oss.foundation.monitoring.MonitoringAgent.unregister()
     * @see com.cisco.oss.foundation.monitoring.MonitoringAgent.getInstance()
     */

    public synchronized void register(MonitoringMXBean mxBean, String authKey) throws AgentAlreadyRegisteredException,
            AgentRegistrationException, IncompatibleClassException {

        AUDITOR.info("Registering MonitoringAgent for " + AppProperties.getComponentInfo(mxBean).getName() + COLON + AppProperties.getComponentInfo(mxBean).getInstance());
        AppProperties.loadProperties();
        final URL jarUrl = getSpringUrl();

        System.setProperty("java.rmi.server.codebase", jarUrl.toString().replace(" ", "%20"));
        if (isRegistered == true && isComponentRegisted == false) {
            unregister();
        }

        try {
            AppProperties.determineHostDetails();

            Utility.validateJavaVersion();
            this.exposedObject = mxBean;
            Utility.validateGenericParams(this.exposedObject);

            String serviceURL = Utility.getServiceURL(this.exposedObject);
            String strAppObjectName = null;

            strAppObjectName = javaRegister(mxBean, serviceURL);
            Runtime.getRuntime().addShutdownHook(new ShutdownHookThread());

            registeredAgents.put(AppProperties.getComponentInfo(mxBean).getName() + COLON + AppProperties.getComponentInfo(mxBean).getInstance() + COLON + authKey, this);
            exposedServiceURL = serviceURL;
            exposedObjectName = strAppObjectName;
            AUDITOR.info("MonitoringAgent successfully registered. Java Version=" + AppProperties.getJavaVersion()
                    + ", URL=" + exposedServiceURL + ", ObjectName=" + exposedObjectName);
            TransactionMonitorThread.getInstance().startTread();
        } catch (MalformedURLException muEx) {
            String message = "Failed to register MonitoringAgent. Name/Instance attributes does not follow the naming standard.";
            AUDITOR.error(message, muEx);
            throw new AgentRegistrationException(message, muEx);
        } catch (MalformedObjectNameException monEx) {
            String message = "Failed to register MonitoringAgent. Name/Instance attributes does not follow the naming standard.";
            AUDITOR.error(message, monEx);
            throw new AgentRegistrationException(message, monEx);
        } catch (InstanceAlreadyExistsException existsEx) {
            String message = "Failed to register MonitoringAgent. There is an another instance of MonitoringAgent already registered for "
                    + AppProperties.getComponentInfo(mxBean).getName() + COLON + AppProperties.getComponentInfo(mxBean).getInstance();
            AUDITOR.error(message, existsEx);
            throw new AgentAlreadyRegisteredException(message, existsEx);
        } catch (NotCompliantMBeanException notComplEx) {
            String message = "Failed to register MonitoringAgent. The MonitoringMXBean object tried to register is not JMX compliant.";
            AUDITOR.error(message, notComplEx);
            throw new IncompatibleClassException(message, notComplEx);
        } catch (MBeanRegistrationException mrE) {
            String message = "Failed to register MonitoringAgent. Check the log for more details.";
            AUDITOR.error(message, mrE);
            throw new AgentRegistrationException(message, mrE);
        } catch (IOException ioEx) {
            String message = "Failed to register MonitoringAgent. Check the log for more details.";
            AUDITOR.error(message, ioEx);
            throw new AgentRegistrationException(message, ioEx);
        }
    }

    private String javaRegister(MonitoringMXBean mxBean, String serviceURL) throws MalformedObjectNameException,
            IOException, InstanceAlreadyExistsException, MBeanRegistrationException, NotCompliantMBeanException {
        serverInfo = new ServerInfo(mxBean);

        String strAgentObjectName = Utility.getObjectName("NDSMXAgent", this.exposedObject);
        String strAppObjectName = Utility.getObjectName("Application", this.exposedObject);

        jurl = new JMXServiceURL(serviceURL);
        appObjectName = new ObjectName(strAppObjectName);
        agentObjectName = new ObjectName(strAgentObjectName);

        jmxEnvironmentMap = null;

        if (!RMIRegistryManager.isRMIRegistryRunning(AppProperties.getAgentPort())) {
            RMIRegistryManager.startRMIRegistry(AppProperties.getAgentPort());
        } else {
            AUDITOR.info("rmiregistry is already running on port " + AppProperties.getAgentPort());
        }

        String serviceName = serviceURL.substring(serviceURL.indexOf("jmxrmi/"));
        if (isServiceExported(serviceName)) {
            MonitoringClient client = new MonitoringClient(serviceURL, strAppObjectName);
            if (client.connect()) {
                client.disconnect();
            } else {
                jmxEnvironmentMap = Utility.prepareJmxEnvironmentMap();
                AUDITOR.info("Found a stale entry for " + serviceName + " in rmiregistry , it will be overwritten");
            }
        }
        mbs = ManagementFactory.getPlatformMBeanServer();
        rmis = JMXConnectorServerFactory.newJMXConnectorServer(jurl, jmxEnvironmentMap, mbs);

        mbs.registerMBean(mxBean, appObjectName);
        mbs.registerMBean((MonitoringAgentMXBean) serverInfo, agentObjectName);
        registerComponentInfo();
        registerMonitoringConfiguration();
        registerServices();
        registerConnections();
        registerNotificationDetails();
        rmis.start();

        isRegistered = true;

        if (mxBean instanceof INotifier) {
            INotifier notifier = (INotifier) mxBean;
            notifier.setNotificationSender(serverInfo);
        }

        serverThread = new ServerRecoveryDaemon();
        serverThread.start();

        return strAppObjectName;
    }

    private void registerComponentInfo() throws MalformedObjectNameException, InstanceAlreadyExistsException,
            MBeanRegistrationException, NotCompliantMBeanException {
        ComponentInfo componentInfo = AppProperties.getComponentInfo(this.exposedObject);
        String strMonComponentObjectName = Utility.getObjectName("ComponentInfo", this.exposedObject);
        this.componentInfoObjectName = new ObjectName(strMonComponentObjectName);
        mbs.registerMBean(componentInfo, this.componentInfoObjectName);

    }

    private void registerMonitoringConfiguration() throws MalformedObjectNameException, InstanceAlreadyExistsException,
            MBeanRegistrationException, NotCompliantMBeanException {
        MonitorAndManagementSettings monitorAndManagementSettings = AppProperties.getMonitorAndManagementSettings();
        String strMonConfigObjectName = Utility.getObjectName("MonitorAndManagementSettings", this.exposedObject);
        this.monitorAndManagementSettingsObjectName = new ObjectName(strMonConfigObjectName);
        mbs.registerMBean(monitorAndManagementSettings, this.monitorAndManagementSettingsObjectName);

    }

    private void registerServices() {
        try {
            serviceInfo = CommunicationInfo.getCommunicationInfo().getServiceInfo();
            String strMonConfigObjectName = Utility.getObjectName("ServiceInfo", this.exposedObject);
            servicesObjectName = new ObjectName(strMonConfigObjectName);
            mbs.registerMBean(serviceInfo, servicesObjectName);
        } catch (MalformedObjectNameException e) {
            LOGGER.trace("Failed to register services" + e.getMessage());
        } catch (InstanceAlreadyExistsException e) {
            LOGGER.trace("Failed to register services" + e.getMessage());
        } catch (MBeanRegistrationException e) {
            LOGGER.trace("Failed to register services" + e.getMessage());
        } catch (NotCompliantMBeanException e) {
            LOGGER.trace("Failed to register services" + e.getMessage());
        }

    }

    public void sendNotification(NotificationInfoMXBean data) {
        try {
            if (!isNotificationRegistered) {

                registerNotificationDetails();
            }
            notificationDetails.sendNotification(data);
        } catch (Exception e) {
            LOGGER.trace("Failed to invoke sendNotification Method" + e.getMessage());
        }
    }

    private void registerNotificationDetails() {
        try {
            notificationDetails = new NotificationMXBean();
            String strNotifObjectName = Utility.getObjectName("NotificationMXBean", this.exposedObject);
            notificationObjectName = new ObjectName(strNotifObjectName);
            mbs.registerMBean(notificationDetails, notificationObjectName);
            isNotificationRegistered = true;
        } catch (MalformedObjectNameException e) {
            LOGGER.trace("Failed to register services" + e.getMessage());
        } catch (InstanceAlreadyExistsException e) {
            LOGGER.trace("Failed to register services" + e.getMessage());
        } catch (MBeanRegistrationException e) {
            LOGGER.trace("Failed to register services" + e.getMessage());
        } catch (NotCompliantMBeanException e) {
            LOGGER.trace("Failed to register services" + e.getMessage());
        } catch (IllegalArgumentException e) {
            LOGGER.trace("Failed to register services" + e.getMessage());
        }

    }


    private void registerConnections() {
        try {
            connectionInfo = CommunicationInfo.getCommunicationInfo().getConnetionInfo();
            String strMonConfigObjectName = Utility.getObjectName("ConnectionInfo", this.exposedObject);
            connetctionsObjectName = new ObjectName(strMonConfigObjectName);
            mbs.registerMBean(connectionInfo, connetctionsObjectName);
        } catch (MalformedObjectNameException e) {
            LOGGER.trace("Failed to register connetctions" + e.getMessage());
        } catch (InstanceAlreadyExistsException e) {
            LOGGER.trace("Failed to register connetctions" + e.getMessage());
        } catch (MBeanRegistrationException e) {
            LOGGER.trace("Failed to register connetctions" + e.getMessage());
        } catch (NotCompliantMBeanException e) {
            LOGGER.trace("Failed to register connetctions" + e.getMessage());
        }
    }

    private void unregisterMonitoringConfiguration() throws InstanceNotFoundException, MBeanRegistrationException {
        if (this.monitorAndManagementSettingsObjectName != null) {
            mbs.unregisterMBean(this.monitorAndManagementSettingsObjectName);
        }

    }

    private void unregisterServices() {
        try {
            if (this.servicesObjectName != null)
                mbs.unregisterMBean(this.servicesObjectName);
        } catch (InstanceNotFoundException e) {
            LOGGER.trace("Failed to unregister services" + e.getMessage());
        } catch (MBeanRegistrationException e) {
            LOGGER.trace("Failed to unregister services" + e.getMessage());
        }
    }

    private void unregisterComponentInfo() {
        try {
            if (this.connetctionsObjectName != null)
                mbs.unregisterMBean(this.componentInfoObjectName);
        } catch (InstanceNotFoundException e) {
            LOGGER.trace("Failed to unregister ComponentInfo" + e.getMessage());
        } catch (MBeanRegistrationException e) {
            LOGGER.trace("Failed to unregister ComponentInfo" + e.getMessage());
        }
    }

    private void unregisterConnetctions() {
        try {
            if (this.connetctionsObjectName != null)
                mbs.unregisterMBean(this.connetctionsObjectName);
        } catch (InstanceNotFoundException e) {
            LOGGER.trace("Failed to unregister server connetctions" + e.getMessage());
        } catch (MBeanRegistrationException e) {
            LOGGER.trace("Failed to unregister server connetctions" + e.getMessage());
        }
    }

    /**
     * Unregisters <code>MonitoringAgent</code> instance and makes the
     * application unavailable for monitoring. Calling unregister() on a
     * MonitoringAgent that is already unregistered will have no effect.
     *
     * @throws com.cisco.oss.foundation.monitoring.exception.AgentRegistrationException
     * @see com.cisco.oss.foundation.monitoring.MonitoringAgent.register()
     */
    public synchronized void unregister() throws AgentRegistrationException {
        if (!isRegistered) {
            return;
        }

        AUDITOR.info("Unregistering MonitoringAgent for " + AppProperties.getComponentInfo(this.exposedObject).getName() + COLON
                + AppProperties.getComponentInfo(this.exposedObject).getInstance());

        try {
            serverThread.interrupt();
            rmis.stop();
            mbs.unregisterMBean(agentObjectName);
            mbs.unregisterMBean(appObjectName);
            unregisterComponentInfo();
            unregisterMonitoringConfiguration();
            unregisterServices();
            unregisterConnetctions();

            if (notificationObjectName != null && mbs.isRegistered(notificationObjectName)) {
                mbs.unregisterMBean(notificationObjectName);
            }

            isRegistered = false;
            AUDITOR.info("MonitoringAgent successfully unregistered. Java Version=" + AppProperties.getJavaVersion()
                    + ", URL=" + exposedServiceURL + ", ObjectName=" + exposedObjectName);

            exposedServiceURL = null;
            exposedObjectName = null;
            isComponentRegisted = false;
            isInfraRegisted = false;
            isNotificationRegistered = false;
            AppProperties.clearComponentInfo();
        } catch (IOException ex) {
            String message = "Failed to unregister MonitoringAgent. Check the log for more details.";
            AUDITOR.error(message, ex);
            throw new AgentRegistrationException(message, ex);
        } catch (InstanceNotFoundException ex) {
            String message = "Failed to unregister MonitoringAgent. Check the log for more details.";
            AUDITOR.error(message, ex);
            throw new AgentRegistrationException(message, ex);
        } catch (MBeanRegistrationException ex) {
            String message = "Failed to unregister MonitoringAgent. Check the log for more details.";
            AUDITOR.error(message, ex);
            throw new AgentRegistrationException(message, ex);
        } catch (LinkageError ex) {

        }
    }

    public NotificationSender getNotificationSender() {
        return serverInfo;
    }

    public MonitoringAgentMXBean getAgentDetails() {
        return serverInfo;
    }

    private boolean isServiceExported(String serviceName) {
        boolean isJmxServiceExported = RMIRegistryManager.isServiceExported(AppProperties
                .getAgentPort(), serviceName);
        return isJmxServiceExported;
    }

    final class ShutdownHookThread extends Thread {
        public void run() {
            try {
                LOGGER.debug("ShutdownHookThread called.");
                unregister();
            } catch (AgentRegistrationException agentregEx) {
                LOGGER.debug("ShutdownHookThread failed to unregister MonitoringAgent.");
            }
        }
    }

    private class ServerRecoveryDaemon extends Thread {
        private long timeInterval = 20000;
        private static final String SERVER_RECOVERY_DAEMON_POLLING = "nds.mx.recoverydaemon.polling";

        public ServerRecoveryDaemon() {
            timeInterval = Long.parseLong(System.getProperty(SERVER_RECOVERY_DAEMON_POLLING, "20000"));
        }

        @Override
        public void run() {
            LOGGER.info("ServerRecoveryDaemon started.");
            while (true) {
                try {
                    Thread.sleep(timeInterval);
                    synchronized (MonitoringAgent.this) {
                        String serviceName = exposedServiceURL.substring(exposedServiceURL.indexOf("jmxrmi/"));
                        if (!isServiceExported(serviceName)) {
                            AUDITOR.warn("RMI Connector Server " + serviceName + " is found to be not running on port "
                                    + AppProperties.getAgentPort() + ", reregistering RMI Connector Server.");

                            try {
                                rmis.stop();
                            } catch (IOException e) {
                                LOGGER.error("Failed to stop JMX RMI Connector server.");
                            }

                            RMIRegistryManager.startRMIRegistry(AppProperties.getAgentPort());

                            rmis = JMXConnectorServerFactory.newJMXConnectorServer(jurl, jmxEnvironmentMap, mbs);
                            rmis.start();
                            AUDITOR.info("Recreated RMI Connector Server.");
                        }
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    AUDITOR.error("ServerRecoveryDaemon failed to reregister RMI Connector Server on port "
                            + AppProperties.getAgentPort() + ". Next attempt will be made after " + timeInterval
                            + "ms.", e);
                }
            }

            LOGGER.info("ServerRecoveryDaemon stopped.");
        }
    }

    public static ServiceInfo getServiceInfo() {
        return serviceInfo;
    }

    public static ConnectionInfo getConnectionInfo() {
        return connectionInfo;
    }

    public static void setConnectionInfo(ConnectionInfo connetionInfo) {
        MonitoringAgent.connectionInfo = connetionInfo;
    }

    public static void setServiceInfo(ServiceInfo serviceInfo) {
        MonitoringAgent.serviceInfo = serviceInfo;
    }

    public boolean isComponentRegisted() {
        return isComponentRegisted;
    }

    public void setComponentRegisted(boolean isComponentRegisted) {
        this.isComponentRegisted = isComponentRegisted;
    }

    public boolean isInfraRegisted() {
        return isInfraRegisted;
    }

    public void setInfraRegisted(boolean isInfraRegisted) {
        this.isInfraRegisted = isInfraRegisted;
    }
}