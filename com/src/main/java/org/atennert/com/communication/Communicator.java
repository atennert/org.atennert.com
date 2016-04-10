/*******************************************************************************
 * Copyright 2016 Andreas Tennert
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *******************************************************************************/

package org.atennert.com.communication;

import org.atennert.com.interpretation.InterpreterManager;
import org.atennert.com.registration.INodeRegistration;
import org.atennert.com.util.CommunicationException;
import org.atennert.com.util.DataContainer;
import org.springframework.beans.factory.annotation.Required;
import rx.Scheduler;
import rx.Single;
import rx.schedulers.Schedulers;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

/**
 * Adapter to access package functions from program.
 */
public class Communicator implements ICommunicatorAccess {
    private SenderManager senderManager;
    private INodeRegistration nodeRegistration;

    private Set<AbstractReceiver> receivers;

    private InterpreterManager interpreterManager;

    private Scheduler scheduler;

    @Required
    public void setSenderManager(final SenderManager senderManager) {
        this.senderManager = senderManager;
    }

    @Required
    public void setNodeRegistration(final INodeRegistration nodeRegistration) {
        this.nodeRegistration = nodeRegistration;
    }

    @Required
    public void setReceivers(final Set<AbstractReceiver> receivers) {
        this.receivers = receivers;
    }

    @Required
    public void setInterpreterManager(final InterpreterManager interpreterManager) {
        this.interpreterManager = interpreterManager;
    }

    @Required
    public void setExecutor(final ExecutorService executor) {
        this.scheduler = Schedulers.from(executor);
    }


    /**
     * Initializes the communicator. (Spring function)
     */
    public void init() {
        initializeReceivers();
    }

    /**
     * Stop the Communicator. (Spring function)
     */
    public synchronized void dispose() {
        scheduler = null;
        senderManager = null;
        interpreterManager = null;
        nodeRegistration = null;
        receivers = null;
    }

    private void initializeReceivers(){
        for (AbstractReceiver r: receivers) {
            r.setScheduler(scheduler);
            r.start();
        }
    }

    @Override
    public void send(final String hostName, final DataContainer data) {
        final String address = getHostAddress(hostName);
        final String protocol = nodeRegistration.getNodeReceiveProtocol(address);
        final String interpreter = getInterpreterForProtocol(hostName, protocol);

        Single.just(data)
                .subscribeOn(scheduler)
                .map(interpreterManager.encode(interpreter))
                .map(senderManager.send(address, protocol))
                .map(interpreterManager.decode())
                .subscribe(data.subscriber);
    }

    /**
     * Returns the address of a given host name (node name).
     *
     * @param hostname Node name
     * @return Node address
     */
    private String getHostAddress(final String hostname) {
        final Iterator<String> iter = nodeRegistration.getNodeReceiveAddresses(hostname).iterator();
        if (iter.hasNext()) {
            return iter.next();
        }
        return null;
    }

    /**
     * Returns an interpreter that can be used with the given protocol.
     *
     * @param protocol communication protocol
     * @return interpreter ID
     */
    private String getInterpreterForProtocol(final String hostName, final String protocol) {
        Set<String> nodeInterpreters = nodeRegistration.getNodeInterpreters(hostName);
        for (String interpreter : nodeRegistration.getInterpretersForProtocol(protocol)) {
            if (nodeInterpreters.contains(interpreter)) {
                return interpreter;
            }
        }
        return null;
    }
}
