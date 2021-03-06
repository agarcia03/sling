/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.event.impl.jobs.queues;

import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.sling.event.impl.jobs.JobHandler;
import org.apache.sling.event.jobs.Job;
import org.apache.sling.event.jobs.consumer.JobExecutionContext;
import org.apache.sling.event.jobs.consumer.JobExecutionResult;

public class JobExecutionContextImpl implements JobExecutionContext {

    public interface ASyncHandler {
        void finished(Job.JobState state);
    }

    private volatile boolean hasInit = false;

    private final JobHandler handler;

    private final Object lock;

    private final AtomicBoolean isAsync;

    private final ASyncHandler asyncHandler;

    public JobExecutionContextImpl(final JobHandler handler,
            final Object syncLock,
            final AtomicBoolean isAsync,
            final ASyncHandler asyncHandler) {
        this.handler = handler;
        this.lock = syncLock;
        this.isAsync = isAsync;
        this.asyncHandler = asyncHandler;
    }

    @Override
    public void initProgress(final int steps,
            final long eta) {
        if ( !hasInit ) {
            handler.persistJobProperties(handler.getJob().startProgress(steps, eta));
            hasInit = true;
        }
    }

    @Override
    public void incrementProgressCount(final int steps) {
        if ( hasInit ) {
            handler.persistJobProperties(handler.getJob().setProgress(steps));
        }
    }

    @Override
    public void updateProgress(final long eta) {
        if ( hasInit ) {
            handler.persistJobProperties(handler.getJob().update(eta));
        }
    }

    @Override
    public void log(final String message, Object... args) {
        handler.persistJobProperties(handler.getJob().log(message, args));
    }

    @Override
    public boolean isStopped() {
        return handler.isStopped();
    }

    @Override
    public void asyncProcessingFinished(final JobExecutionResult result) {
        synchronized ( lock ) {
            if ( isAsync.compareAndSet(true, false) ) {
                Job.JobState state = null;
                if ( result.succeeded() ) {
                    state = Job.JobState.SUCCEEDED;
                } else if ( result.failed() ) {
                    state = Job.JobState.QUEUED;
                } else if ( result.cancelled() ) {
                    if ( handler.isStopped() ) {
                        state = Job.JobState.STOPPED;
                    } else {
                        state = Job.JobState.ERROR;
                    }
                }
                asyncHandler.finished(state);
            } else {
                throw new IllegalStateException("Job is not processed async " + handler.getJob().getId());
            }
        }
    }

    @Override
    public ResultBuilder result() {
        return new ResultBuilderImpl();
    }
}
