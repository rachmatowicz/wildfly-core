/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.jboss.as.cli.gui;

import java.awt.Cursor;
import java.io.File;
import java.io.IOException;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.OperationMessageHandler;
import org.jboss.as.controller.client.OperationResponse;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;

/**
 * This class takes a command-line cli command and submits it to the server.
 *
 * @author Stan Silvert ssilvert@redhat.com (C) 2012 Red Hat Inc.
 */
public class CommandExecutor {

    private final CliGuiContext cliGuiCtx;
    private final ModelControllerClient client;
    private final CommandContext cmdCtx;

    public CommandExecutor(CliGuiContext cliGuiCtx) {
        this.cliGuiCtx = cliGuiCtx;
        this.cmdCtx = cliGuiCtx.getCommmandContext();
        this.client = cmdCtx.getModelControllerClient();
        Runtime.getRuntime().addShutdownHook(new Thread(new ClientCloserShutdownHook()));
    }

    private class ClientCloserShutdownHook implements Runnable {
        @Override
        public void run() {
            try {
                CommandExecutor.this.client.close();
            } catch (IOException ioe) {
                // Do nothing.  The close() method has given its best shot.
            }
        }
    }

    /**
     * Submit a command to the server.
     *
     * @param command The CLI command
     * @return The DMR response as a ModelNode
     * @throws CommandFormatException
     * @throws IOException
     */
    public synchronized ModelNode doCommand(String command) throws CommandFormatException, IOException {
        ModelNode request = cmdCtx.buildRequest(command);
        return execute(request, isSlowCommand(command)).getResponseNode();
    }

    /**
     * User-initiated commands use this method.
     *
     * @param command The CLI command
     * @return A Response object containing the command line, DMR request, and DMR response
     * @throws CommandFormatException
     * @throws IOException
     */
    public synchronized Response doCommandFullResponse(String command) throws CommandFormatException, IOException {
        ModelNode request = cmdCtx.buildRequest(command);
        boolean replacedBytes = replaceFilePathsWithBytes(request);
        OperationResponse response = execute(request, isSlowCommand(command) || replacedBytes);
        return new Response(command, request, response);
    }

    // For any request params that are of type BYTES, replace the file path with the bytes from the file
    private boolean replaceFilePathsWithBytes(ModelNode request) throws CommandFormatException, IOException {
        boolean didReplacement = false;
        ModelNode opDesc = request.clone();
        String opName = opDesc.get("operation").asString();
        opDesc.get("operation").set("read-operation-description");
        opDesc.get("name").set(opName);
        ModelNode response = execute(opDesc, false).getResponseNode();
        ModelNode requestProps = response.get("result", "request-properties");
        for (Property prop : requestProps.asPropertyList()) {
            ModelNode typeDesc = prop.getValue().get("type");
            if (typeDesc.getType() == ModelType.TYPE &&
                    typeDesc.asType() == ModelType.BYTES &&
                    request.hasDefined(prop.getName())) {
                String filePath = request.get(prop.getName()).asString();
                File localFile = new File(filePath);
                if (!localFile.exists()) continue;
                try {
                    request.get(prop.getName()).set(Util.readBytes(localFile));
                    didReplacement = true;
                } catch (OperationFormatException e) {
                    throw new CommandFormatException(e);
                }
            }
        }
        return didReplacement;
    }

    private OperationResponse execute(ModelNode request, boolean useWaitCursor) throws IOException {
        try {
            if (useWaitCursor) {
                cliGuiCtx.getMainWindow().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            }
            return client.executeOperation(OperationBuilder.create(request).build(), OperationMessageHandler.DISCARD);
        } finally {
            if (useWaitCursor) {
                cliGuiCtx.getMainWindow().setCursor(Cursor.getDefaultCursor());
            }
        }
    }

    private boolean isSlowCommand(String command) {
        return command.startsWith("deploy") ||
               command.contains(":read-log-file");
    }

    public static class Response {
        private final OperationResponse response;
        private final String command;
        private final ModelNode dmrRequest;

        Response(String command, ModelNode dmrRequest, OperationResponse response) {
            this.command = command;
            this.dmrRequest = dmrRequest.clone();
            this.response = response;
        }

        public String getCommand() {
            return command;
        }

        public ModelNode getDmrRequest() {
            return dmrRequest;
        }

        public ModelNode getDmrResponse() {
            return response.getResponseNode();
        }

        public OperationResponse getOperationResponse() {
            return response;
        }

    }


}
