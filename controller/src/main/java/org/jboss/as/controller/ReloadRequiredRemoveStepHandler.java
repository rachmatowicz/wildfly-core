/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller;

import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

/**
 * A handler for the "remove" operation that always puts the process in "reload-required" state.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class ReloadRequiredRemoveStepHandler extends AbstractRemoveStepHandler {

    public static final ReloadRequiredRemoveStepHandler INSTANCE = new ReloadRequiredRemoveStepHandler();

    private final String[] unavailableCapabilities;

    /**
     * Creates a new {@code ReloadRequiredRemoveStepHandler} that will
     * {@link #recordCapabilitiesAndRequirements(OperationContext, org.jboss.dmr.ModelNode, org.jboss.as.controller.registry.Resource) deregister}
     * a list of capabilities as part of execution.
     *
     * @param unavailableCapabilities capabilities to deregister
     */
    public ReloadRequiredRemoveStepHandler(String... unavailableCapabilities) {
        this.unavailableCapabilities = unavailableCapabilities;
    }

    /**
     * Creates a new {@code ReloadRequiredRemoveStepHandler}
     */
    public ReloadRequiredRemoveStepHandler() {
        this.unavailableCapabilities = null;
    }

    /**
     * {@link org.jboss.as.controller.OperationContext#deregisterCapability(String) Deregisters} any capabilities
     * whose names were passed to {@link #ReloadRequiredRemoveStepHandler(String...) the constructor}.
     *
     * {@inheritDoc}
     */
    @Override
    protected void recordCapabilitiesAndRequirements(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        super.recordCapabilitiesAndRequirements(context, operation, resource);
        if (unavailableCapabilities != null) {
            for (String unavailable : unavailableCapabilities) {
                context.deregisterCapability(unavailable);
            }
        }
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        context.reloadRequired();
    }

    @Override
    protected void recoverServices(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        context.revertReloadRequired();
    }
}
