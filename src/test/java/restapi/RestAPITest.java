/*
 * Copyright (c) 2015-2018 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package restapi;

import org.jberet.rest.client.BatchClient;
import org.jberet.rest.entity.JobEntity;
import org.jberet.rest.entity.JobExecutionEntity;
import org.jberet.rest.entity.JobInstanceEntity;
import org.jberet.rest.entity.StepExecutionEntity;
import org.junit.Assert;
import org.junit.Test;

import javax.batch.runtime.BatchStatus;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.Arrays;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * Tests to verify REST API defined in {@code jberet-rest-api} module.
 * This test class uses JAX-RS client API to access a test web app
 * ({@code restAPI.war}) deployed to WildFly or JBoss EAP 7.
 */
public class RestAPITest {
    private static final String jobWithParams = "restJobWithParams";

    // context-path: use war file base name as the default context root
    // rest api mapping url: configured in web.xml servlet-mapping
    private static final String restUrl = "http://localhost:8080/play-jberet-rest-api/api";
    private BatchClient batchClient = new BatchClient(restUrl);

    @Test
    public void start() throws Exception {
        final JobExecutionEntity data = batchClient.startJob(jobWithParams, null);
        System.out.printf("Response entity: %s%n", data);
        Assert.assertNotNull(data.getCreateTime());
    }

}
