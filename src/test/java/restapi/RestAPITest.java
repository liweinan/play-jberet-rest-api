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
    private static final String jobNameBad = "xxxxxxx";

    private static final int initialDelayMinute = 1;
    private static final int intervalMinute = 1;
    private static final long sleepTimeMillis = initialDelayMinute * 60 * 1000 + 1000;

    // context-path: use war file base name as the default context root
    // rest api mapping url: configured in web.xml servlet-mapping
    private static final String restUrl = "http://localhost:8080/play-jberet-rest-api/api";
    private BatchClient batchClient = new BatchClient(restUrl);

    private final String jobAsJson = "{\n" +
            "  \"job\": {\n" +
            "    \"id\": \"submitted\",\n" +
            "    \"step\": {\n" +
            "      \"id\": \"submitted.step1\",\n" +
            "      \"chunk\": {\n" +
            "        \"reader\": {\n" +
            "          \"ref\": \"arrayItemReader\",\n" +
            "          \"properties\": {\n" +
            "            \"property\": [\n" +
            "              {\n" +
            "                \"name\": \"resource\",\n" +
            "                \"value\": \"#{jobParameters['resource']}\"\n" +
            "              },\n" +
            "              {\n" +
            "                \"name\": \"beanType\",\n" +
            "                \"value\": \"java.lang.Integer\"\n" +
            "              },\n" +
            "              {\n" +
            "                \"name\": \"skipBeanValidation\",\n" +
            "                \"value\": \"true\"\n" +
            "              }\n" +
            "            ]\n" +
            "          }\n" +
            "        },\n" +
            "        \"writer\": { \"ref\": \"mockItemWriter\" }\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}";

    static final String submittedJobResource = "[10, 11, 12, 13, 14, 15]";
    static final String submittedJobResourceInvalid = "[10, 11, 12, 13, 14, X]";

    @Test
    public void start() throws Exception {
        final JobExecutionEntity data = batchClient.startJob(jobWithParams, null);
        System.out.printf("Response entity: %s%n", data);
        Assert.assertNotNull(data.getCreateTime());
    }

    @Test
    public void startWithJobParams() throws Exception {
        final URI uri = batchClient.getJobUriBuilder("start").
                resolveTemplate("jobXmlName", jobWithParams).build();
        final WebTarget target = batchClient.target(uri)
                .queryParam("jobParam1", "jobParam1 value")
                .queryParam("jobParam2", "jobParam2 value");
        System.out.printf("uri: %s%n", uri);

        //accepts XML to test XML response content type
        final Response response = target.request().accept(MediaType.APPLICATION_XML_TYPE).post(Entity.entity(null, MediaType.APPLICATION_JSON_TYPE));

        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
    }

    @Test
    public void startWithJobParamsInBody() throws Exception {
        final Properties jobParams = new Properties();
        jobParams.setProperty("jobParam1", "jobParam1 value");
        jobParams.setProperty("jobParam2", "jobParam2 value");

        final URI uri = batchClient.getJobUriBuilder("start").
                resolveTemplate("jobXmlName", jobWithParams).build();
        final WebTarget target = batchClient.target(uri);
        System.out.printf("uri: %s%n", uri);
        final Response response = target.request().post(Entity.entity(jobParams, MediaType.APPLICATION_JSON_TYPE));

        assertEquals(Response.Status.CREATED.getStatusCode(), response.getStatus());
        final String location = response.getHeaderString("Location");
        System.out.printf("location of the created: %s%n", location);
        response.close();

        final WebTarget target2 = batchClient.target(new URI(location));
        final JobExecutionEntity data = target2.request().get(JobExecutionEntity.class);
        assertEquals(jobParams, data.getJobParameters());
    }

    @Test
    public void startWithBadJobXmlName() throws Exception {
        final URI uri = batchClient.getJobUriBuilder("start").resolveTemplate("jobXmlName", jobNameBad).build();
        final WebTarget target = batchClient.target(uri);
        System.out.printf("uri: %s%n", uri);
        final Response response = target.request().post(Entity.entity(null, MediaType.APPLICATION_JSON_TYPE));

        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), response.getStatus());
    }

    @Test
    public void getJobNames() throws Exception {
        batchClient.startJob(jobWithParams, null);

        final URI uri = batchClient.getJobUriBuilder(null).build();
        final WebTarget target = batchClient.target(uri);
        System.out.printf("uri: %s%n", uri);

        //accepts XML to test XML response content type
        final JobEntity[] response = target.request().accept(MediaType.APPLICATION_XML_TYPE).get(JobEntity[].class);

        boolean foundJobName1 = false;
        for (final JobEntity e : response) {
            if (jobWithParams.equals(e.getJobName()) && (e.getNumberOfJobInstances() > 0)) {
                foundJobName1 = true;
            }
        }
        assertEquals(true, foundJobName1);
    }


    @Test
    public void getJobInstances() throws Exception {
        final JobExecutionEntity jobExecutionData = batchClient.startJob(jobWithParams, null);// to have at least 1 job instance
        final URI uri = batchClient.getJobInstanceUriBuilder(null).build();
        final WebTarget target = batchClient.target(uri)
                .queryParam("jobName", jobExecutionData.getJobName())
                .queryParam("start", 0)
                .queryParam("count", 99999999);
        System.out.printf("uri: %s%n", uri);

        //accepts XML to test XML response content type
        final JobInstanceEntity[] data = target.request().accept(MediaType.APPLICATION_XML_TYPE).get(JobInstanceEntity[].class);

        //all JobInstanceEntity data may be too long, so just display its length
        System.out.printf("Got JobInstanceData[]: %s items%n", data.length);
        assertEquals(jobWithParams, data[0].getJobName());
    }


    @Test
    public void getJobInstanceCountMissingJobName() throws Exception {
        final URI uri = batchClient.getJobInstanceUriBuilder("getJobInstanceCount").build();
        final WebTarget target = batchClient.target(uri);
        System.out.printf("uri: %s%n", uri);
        final Response response = target.request().get();

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    public void getJobInstance() throws Exception {
        final JobExecutionEntity jobExecutionData = batchClient.startJob(jobWithParams, null);// to have at least 1 job instance
        final URI uri = batchClient.getJobInstanceUriBuilder(null).build();
        final WebTarget target = batchClient.target(uri).queryParam("jobExecutionId", jobExecutionData.getExecutionId());
        System.out.printf("uri: %s%n", uri);
        final JobInstanceEntity data = target.request().get(JobInstanceEntity.class);

        System.out.printf("Got JobInstanceData: %s%n", data);
        assertEquals(jobWithParams, data.getJobName());
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////


    @Test
    public void getJobExecution() throws Exception {
        final JobExecutionEntity jobExecutionData = batchClient.startJob(jobWithParams, null);
        final URI uri = batchClient.getJobExecutionUriBuilder(null).path(String.valueOf(jobExecutionData.getExecutionId())).build();
        System.out.printf("uri: %s%n", uri);
        final WebTarget target = batchClient.target(uri);
        final JobExecutionEntity data = target.request().get(JobExecutionEntity.class);

        System.out.printf("Got JobExecutionData: %s%n", data);
        assertEquals(jobWithParams, data.getJobName());
    }

    @Test
    public void abandon() throws Exception {
        final Properties queryParams = new Properties();
        queryParams.setProperty("fail", String.valueOf(true));
        final JobExecutionEntity jobExecutionData = batchClient.startJob(jobWithParams, queryParams);

        Thread.sleep(500);
        final URI uri = batchClient.getJobExecutionUriBuilder("abandon")
                .resolveTemplate("jobExecutionId", String.valueOf(jobExecutionData.getExecutionId())).build();
        System.out.printf("uri: %s%n", uri);
        final WebTarget target = batchClient.target(uri);
        final Response response = target.request().post(null);
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());

        //abandon it again (should be idempotent)
        final Response response2 = batchClient.target(uri).request().post(null);
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response2.getStatus());

        final URI abandonedJobExecutionUri = batchClient.getJobExecutionUriBuilder(null).
                path(String.valueOf(jobExecutionData.getExecutionId())).build();
        System.out.printf("uri: %s%n", abandonedJobExecutionUri);
        final WebTarget jobExecutionTarget = batchClient.target(abandonedJobExecutionUri);
        final JobExecutionEntity data = jobExecutionTarget.request().get(JobExecutionEntity.class);
        assertEquals(BatchStatus.ABANDONED, data.getBatchStatus());
    }

    @Test
    public void restart() throws Exception {
        final Properties queryParams = new Properties();
        queryParams.setProperty("fail", String.valueOf(true));
        JobExecutionEntity jobExecution = batchClient.startJob(jobWithParams, queryParams);
        assertEquals(queryParams, jobExecution.getJobParameters());

        Thread.sleep(500);
        queryParams.setProperty("fail", String.valueOf(false));
        final JobExecutionEntity restartJobExecution = batchClient.restartJobExecution(jobExecution.getExecutionId(), queryParams);
        assertEquals(queryParams, restartJobExecution.getJobParameters());

        Thread.sleep(500);
        jobExecution = batchClient.getJobExecution(restartJobExecution.getExecutionId());
        assertEquals(BatchStatus.COMPLETED, jobExecution.getBatchStatus());
        assertEquals(jobWithParams, jobExecution.getJobName());
    }

    @Test
    public void stop() throws Exception {
        final Properties queryParams = new Properties();
        queryParams.setProperty("sleepMillis", String.valueOf(1000));
        queryParams.setProperty("fail", String.valueOf(false));
        JobExecutionEntity jobExecution = batchClient.startJob(jobWithParams, queryParams);
        assertEquals(queryParams, jobExecution.getJobParameters());

        final URI uri = batchClient.getJobExecutionUriBuilder("stop").resolveTemplate("jobExecutionId", jobExecution.getExecutionId()).build();
        final WebTarget target = batchClient.target(uri);
        System.out.printf("uri: %s%n", uri);
        final Response response = target.request().post(Entity.entity(null, MediaType.APPLICATION_JSON_TYPE));
        assertEquals(Response.Status.NO_CONTENT.getStatusCode(), response.getStatus());

        Thread.sleep(1000);
        final JobExecutionEntity jobExecutionStopped = batchClient.getJobExecution(jobExecution.getExecutionId());
        assertEquals(BatchStatus.STOPPED, jobExecutionStopped.getBatchStatus());
        assertEquals(jobWithParams, jobExecutionStopped.getJobName());
    }

    @Test
    public void getRunningExecutions() throws Exception {
        final Properties queryParams = new Properties();
        queryParams.setProperty("sleepMillis", String.valueOf(2000));
        JobExecutionEntity jobExecution1 = batchClient.startJob(jobWithParams, queryParams);
        JobExecutionEntity jobExecution2 = batchClient.startJob(jobWithParams, queryParams);

        final URI uri = batchClient.getJobExecutionUriBuilder("getRunningExecutions").build();
        queryParams.clear();
        queryParams.setProperty("jobName", jobWithParams);
        WebTarget target = batchClient.target(uri, queryParams);
        System.out.printf("uri: %s%n", uri);
        final JobExecutionEntity[] jobExecutionData = target.request().get(JobExecutionEntity[].class);
        assertEquals(2, jobExecutionData.length);
    }

    @Test
    public void getRunningExecutionsEmpty() throws Exception {
        final Properties queryParams = new Properties();
        JobExecutionEntity jobExecution1 = batchClient.startJob(jobWithParams, null);
        JobExecutionEntity jobExecution2 = batchClient.startJob(jobWithParams, null);

        Thread.sleep(500);
        final URI uri = batchClient.getJobExecutionUriBuilder("getRunningExecutions").build();
        queryParams.setProperty("jobName", jobWithParams);
        WebTarget target = batchClient.target(uri, queryParams);
        System.out.printf("uri: %s%n", uri);
        final JobExecutionEntity[] data = target.request().get(JobExecutionEntity[].class);
        assertEquals(0, data.length);
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @Test
    public void getStepExecutions() throws Exception {
        JobExecutionEntity jobExecution1 = batchClient.startJob(jobWithParams, null);

        Thread.sleep(500);
        final StepExecutionEntity[] data = batchClient.getStepExecutions(jobExecution1.getExecutionId());
        assertEquals(1, data.length);
        assertEquals(BatchStatus.COMPLETED, data[0].getBatchStatus());
        assertEquals(jobWithParams + ".step1", data[0].getStepName());
        System.out.printf("Got step metrics: %s%n", Arrays.toString(data[0].getMetrics()));
    }

    @Test
    public void getStepExecution() throws Exception {
        JobExecutionEntity jobExecution1 = batchClient.startJob(jobWithParams, null);

        Thread.sleep(500);
        final StepExecutionEntity[] data = batchClient.getStepExecutions(jobExecution1.getExecutionId());
        final long stepExecutionId = data[0].getStepExecutionId();
        final URI uri = batchClient.getJobExecutionUriBuilder("getStepExecution")
                .resolveTemplate("jobExecutionId", jobExecution1.getExecutionId())
                .resolveTemplate("stepExecutionId", stepExecutionId).build();
        WebTarget target = batchClient.target(uri);
        System.out.printf("uri: %s%n", uri);
        final StepExecutionEntity stepExecutionData = target.request().get(StepExecutionEntity.class);
        assertEquals(BatchStatus.COMPLETED, stepExecutionData.getBatchStatus());
        assertEquals(jobWithParams + ".step1", stepExecutionData.getStepName());
        System.out.printf("Got step metrics: %s%n", Arrays.toString(stepExecutionData.getMetrics()));
    }

    @Test
    public void getStepExecutionBadStepExecutionId() throws Exception {
        JobExecutionEntity jobExecution1 = batchClient.startJob(jobWithParams, null);

        Thread.sleep(500);
        final long stepExecutionId = Long.MAX_VALUE;
        final URI uri = batchClient.getJobExecutionUriBuilder("getStepExecution")
                .resolveTemplate("jobExecutionId", jobExecution1.getExecutionId())
                .resolveTemplate("stepExecutionId", stepExecutionId).build();
        WebTarget target = batchClient.target(uri, null);
        System.out.printf("uri: %s%n", uri);

        try {
            target.request().get(StepExecutionEntity.class);
        } catch (final WebApplicationException e) {
            System.out.printf("Got expected exception: %s%n", e);
        }
    }

}
