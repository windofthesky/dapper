/**
 * <p>
 * Copyright (c) 2008 The Regents of the University of California<br>
 * All rights reserved.
 * </p>
 * <p>
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
 * following conditions are met:
 * </p>
 * <ul>
 * <li>Redistributions of source code must retain the above copyright notice, this list of conditions and the following
 * disclaimer.</li>
 * <li>Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 * following disclaimer in the documentation and/or other materials provided with the distribution.</li>
 * <li>Neither the name of the author nor the names of any contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.</li>
 * </ul>
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * </p>
 */

package org.dapper.client;

import static org.dapper.Constants.REQUEST_TIMEOUT_MILLIS;
import static org.dapper.codelet.Resource.ResourceType.OUTPUT_HANDLE;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.dapper.DapperBase;
import org.dapper.codelet.Codelet;
import org.dapper.codelet.CodeletUtilities;
import org.dapper.codelet.DataService;
import org.dapper.codelet.OutputHandleResource;
import org.dapper.codelet.Resource;
import org.dapper.codelet.StreamResource;
import org.dapper.event.ControlEvent;
import org.dapper.event.DataEvent;
import org.dapper.event.ExecuteAckEvent;
import org.dapper.event.ResetEvent;
import org.dapper.event.ResourceEvent;
import org.dapper.event.SourceType;
import org.dapper.server.flow.EmbeddingCodelet;
import org.dapper.server.flow.FlowNode;
import org.dapper.util.RequestFuture;
import org.shared.event.Source;
import org.shared.metaclass.RegistryClassLoader;
import org.shared.metaclass.ResourceRegistry;
import org.shared.net.SocketConnection;
import org.shared.net.handler.SynchronousHandler;
import org.shared.parallel.Handle;
import org.shared.util.Control;
import org.shared.util.IoBase;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * A client job thread class.
 * 
 * @author Roy Liu
 */
public class ClientJob extends Thread implements Closeable, DataService {

    final ResourceEvent event;
    final DapperBase base;

    final Map<String, StreamResource<?>> remaining;
    final Set<StreamResource<?>> connectResources;
    final Source<ControlEvent, SourceType> callback;

    Map<String, RequestFuture<byte[]>> pending;

    /**
     * Default constructor.
     */
    public ClientJob(ResourceEvent event, DapperBase base, Source<ControlEvent, SourceType> callback) {
        super("Job Thread");

        this.event = event;
        this.base = base;

        this.pending = new HashMap<String, RequestFuture<byte[]>>();
        this.remaining = new HashMap<String, StreamResource<?>>();
        this.connectResources = new HashSet<StreamResource<?>>();

        Set<Resource> allResources = new HashSet<Resource>();
        allResources.addAll(this.event.getIn());
        allResources.addAll(this.event.getOut());

        for (Resource resource : allResources) {

            switch (resource.getType()) {

            case INPUT_STREAM:
            case OUTPUT_STREAM:

                StreamResource<?> connectResource = (StreamResource<?>) resource;

                this.remaining.put(connectResource.getIdentifier(), connectResource);

                // This stream requires connecting to.
                if (connectResource.getAddress() != null) {
                    this.connectResources.add(connectResource);
                }

                break;
            }
        }

        this.callback = callback;
    }

    /**
     * Registers an input/output stream.
     */
    @SuppressWarnings("unchecked")
    protected void registerStream(String identifier, SynchronousHandler<? extends SocketConnection> handler) {

        StreamResource<?> res = this.remaining.remove(identifier);

        SocketConnection conn = handler.getConnection();

        if (res != null) {

            switch (res.getType()) {

            case INPUT_STREAM:
                ((Handle<InputStream>) res).set(handler.getInputStream());
                res.setAddress(conn.getRemoteAddress());
                break;

            case OUTPUT_STREAM:
                ((Handle<OutputStream>) res).set(handler.getOutputStream());
                res.setAddress(conn.getRemoteAddress());
                break;

            // Huh? How could this even happen?
            default:
                IoBase.close(conn);
                break;
            }

        } else {

            // If not found, then the connection must be erroneous.
            IoBase.close(conn);
        }
    }

    /**
     * Gets the set of {@link Resource}s that require connecting to.
     */
    public Set<StreamResource<?>> getConnectResources() {
        return this.connectResources;
    }

    /**
     * Gets whether this job is ready to execute.
     */
    public boolean isReady() {
        return this.remaining.isEmpty();
    }

    /**
     * Registers data requested from the server.
     */
    public void registerData(String pathname, byte[] data) {

        synchronized (this) {

            RequestFuture<byte[]> rf = this.pending.remove(pathname);

            if (rf != null) {
                rf.set(data);
            }
        }
    }

    /**
     * Closes all underlying {@link InputStream}s and {@link OutputStream}s.
     */
    @SuppressWarnings("unchecked")
    @Override
    public void close() {

        Set<Resource> allResources = new HashSet<Resource>();
        allResources.addAll(this.event.getIn());
        allResources.addAll(this.event.getOut());

        for (Resource resource : allResources) {

            // Close any streams we encounter.
            switch (resource.getType()) {

            case INPUT_STREAM:
            case OUTPUT_STREAM:
                IoBase.close(((StreamResource<? extends Closeable>) resource).get());
                break;
            }
        }

        synchronized (this) {

            for (RequestFuture<byte[]> future : this.pending.values()) {
                future.setException(new IllegalStateException("The client job has been stopped"));
            }

            this.pending = null;
        }
    }

    /**
     * Runs the job.
     */
    @Override
    public void run() {

        try {

            RegistryClassLoader rcl = new RegistryClassLoader();
            rcl.addRegistry(new ResourceRegistry() {

                @Override
                public URL getResource(String pathname) {
                    return null;
                }

                @SuppressWarnings("unchecked")
                @Override
                public Enumeration<URL> getResources(String pathname) {
                    return Collections.enumeration(Collections.EMPTY_LIST);
                }

                @Override
                public InputStream getResourceAsStream(String pathname) {

                    byte[] data = getData(String.format("cp:%s", pathname));
                    return (data != null) ? new ByteArrayInputStream(data) : null;
                }
            });

            Codelet codelet = (Codelet) rcl.loadClass(this.event.getClassName()).newInstance();

            List<Resource> outResources = this.event.getOut();

            CodeletUtilities.setDataService(this);

            try {

                codelet.run( //
                        Collections.unmodifiableList(this.event.getIn()), //
                        Collections.unmodifiableList(outResources), //
                        this.event.getParameters());

            } finally {

                CodeletUtilities.setDataService(null);
            }

            Node embeddingParameters = (codelet instanceof EmbeddingCodelet) ? ((EmbeddingCodelet) codelet)
                    .getEmbeddingParameters() : null;
            embeddingParameters = (embeddingParameters == null) ? FlowNode.emptyParameters : embeddingParameters;

            Control.checkTrue(embeddingParameters.getNodeName().equals("parameters"), //
                    "Invalid parameters node");

            Document doc = DapperBase.newDocument();
            Node edgeParameters = doc.createElement("edge_parameters");

            for (Resource outResource : outResources) {

                Node edgeParameterNode = edgeParameters.appendChild(doc.createElement("edge_parameter"));

                if (outResource.getType() == OUTPUT_HANDLE) {
                    ((OutputHandleResource) outResource).getContents(edgeParameterNode);
                }
            }

            ExecuteAckEvent executeAckEvent = new ExecuteAckEvent(embeddingParameters, edgeParameters, this.callback);
            executeAckEvent.set(this);

            this.callback.onLocal(executeAckEvent);

        } catch (Throwable t) {

            ResetEvent resetEvent = new ResetEvent("Execution encountered an unexpected exception", t, this.callback);
            resetEvent.set(this);

            this.callback.onLocal(resetEvent);
        }
    }

    @Override
    public byte[] getData(String pathname) {

        RequestFuture<byte[]> rf = new RequestFuture<byte[]>() {

            byte[] data = null;
            Throwable exception = null;

            @Override
            public void set(byte[] data) {

                Control.checkTrue(data != null, //
                        "Data cannot be null");

                synchronized (this) {

                    Control.checkTrue(this.data == null, //
                            "Data is already set");

                    this.data = data;
                    notifyAll();
                }
            }

            @Override
            public void setException(Throwable exception) {

                Control.checkTrue(exception != null, //
                        "Exception cannot be null");

                synchronized (this) {

                    Control.checkTrue(this.exception == null, //
                            "Exception is already set");

                    this.exception = exception;
                    notifyAll();
                }
            }

            @Override
            public byte[] get() throws InterruptedException, ExecutionException {

                synchronized (this) {

                    for (; !isDone();) {
                        wait();
                    }
                }

                if (this.exception != null) {
                    throw new ExecutionException(this.exception);
                }

                return this.data;
            }

            @Override
            public byte[] get(long timeout, TimeUnit unit) //
                    throws InterruptedException, ExecutionException, TimeoutException {

                long timeoutMillis = unit.toMillis(timeout);

                synchronized (this) {

                    for (long remaining = timeoutMillis, end = System.currentTimeMillis() + timeoutMillis; //
                    !isDone() && remaining > 0; //
                    remaining = end - System.currentTimeMillis()) {
                        wait(remaining);
                    }

                    if (!isDone()) {
                        throw new TimeoutException("Operation timed out");
                    }
                }

                if (this.exception != null) {
                    throw new ExecutionException(this.exception);
                }

                return this.data;
            }

            @Override
            public boolean isDone() {

                synchronized (this) {
                    return this.data != null || this.exception != null;
                }
            }

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return false;
            }
        };

        synchronized (this) {

            Control.checkTrue(this.pending != null && !this.pending.containsKey(pathname), //
                    "Request conditions violated");

            this.pending.put(pathname, rf);
        }

        this.callback.onLocal(new DataEvent(pathname, new byte[] {}, ClientJob.this.callback));

        try {

            return rf.get(REQUEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        } catch (Exception e) {

            return null;
        }
    }
}
