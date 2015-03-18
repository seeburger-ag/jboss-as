package org.jboss.as.ejb3.deployment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;


/**
 * Repository for information about deployed modules. This includes information on all the deployed EJB's in the module
 *
 * @author Stuart Douglas
 */
public class DeploymentRepository implements Service<DeploymentRepository> {

    public static final ServiceName SERVICE_NAME = ServiceName.JBOSS.append("ee", "deploymentRepository");

    private static final Logger logger = Logger.getLogger(DeploymentRepository.class);

    /**
     * All deployed modules. This is a copy on write map that is updated infrequently and read often.
     */
    private volatile Map<DeploymentModuleIdentifier, DeploymentHolder> modules;

    private final List<DeploymentRepositoryListener> listeners = new ArrayList<DeploymentRepositoryListener>();


    @Override
    public void start(StartContext context) throws StartException {
        modules = Collections.emptyMap();
    }

    @Override
    public void stop(StopContext context) {
        modules = null;
    }

    @Override
    public DeploymentRepository getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    public synchronized void add(DeploymentModuleIdentifier identifier, ModuleDeployment deployment) {
        final List<DeploymentRepositoryListener> listeners;
        synchronized (this) {
            final Map<DeploymentModuleIdentifier, DeploymentHolder> modules = new HashMap<DeploymentModuleIdentifier, DeploymentHolder>(this.modules);
            modules.put(identifier, new DeploymentHolder(deployment));
            this.modules = Collections.unmodifiableMap(modules);
            listeners = new ArrayList<DeploymentRepositoryListener>(this.listeners);
        }
        for(final DeploymentRepositoryListener listener : listeners) {
            try {
                listener.deploymentAvailable(identifier, deployment);
            } catch (Throwable t) {
                logger.error("Exception calling deployment added listener", t);
            }
        }
    }

    public void addListener(final DeploymentRepositoryListener listener) {
        synchronized (this) {
            listeners.add(listener);
        }
        listener.listenerAdded(this);
    }

    public synchronized void removeListener(final DeploymentRepositoryListener listener) {
        listeners.remove(listener);
    }

    public void remove(DeploymentModuleIdentifier identifier) {
        final List<DeploymentRepositoryListener> listeners;
        synchronized (this) {
            final Map<DeploymentModuleIdentifier, DeploymentHolder> modules = new HashMap<DeploymentModuleIdentifier, DeploymentHolder>(this.modules);
            modules.remove(identifier);
            this.modules = Collections.unmodifiableMap(modules);
            listeners = new ArrayList<DeploymentRepositoryListener>(this.listeners);
        }
        for(final DeploymentRepositoryListener listener : listeners) {
            try {
                listener.deploymentRemoved(identifier);
            } catch (Throwable t) {
                logger.error("Exception calling deployment removal listener", t);
            }
        }
    }

    public Map<DeploymentModuleIdentifier, ModuleDeployment> getModules() {
        Map<DeploymentModuleIdentifier, ModuleDeployment> modules = new HashMap<DeploymentModuleIdentifier, ModuleDeployment>();
        for(Map.Entry<DeploymentModuleIdentifier, DeploymentHolder> entry : this.modules.entrySet()) {
            modules.put(entry.getKey(), entry.getValue().deployment);
        }
        return modules;
    }

    private class DeploymentHolder {
        final ModuleDeployment deployment;
        volatile boolean started = false;

        private DeploymentHolder(ModuleDeployment deployment) {
            this.deployment = deployment;
        }
    }

}
