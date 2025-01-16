/**
 * Copyright (c) 2016-2021 Pinterest, Inc.
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
package com.pinterest.deployservice.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pinterest.deployservice.ServiceContext;
import com.pinterest.deployservice.bean.DeployGoalBean;
import com.pinterest.deployservice.bean.DeployPriority;
import com.pinterest.deployservice.bean.DeployStage;
import com.pinterest.deployservice.bean.DeployType;
import com.pinterest.deployservice.bean.EnvType;
import com.pinterest.deployservice.bean.EnvironBean;
import com.pinterest.deployservice.bean.KnoxStatus;
import com.pinterest.deployservice.bean.MultiGoalResponseItemBean;
import com.pinterest.deployservice.bean.NormandieStatus;
import com.pinterest.deployservice.bean.OpCode;
import com.pinterest.deployservice.bean.PingRequestBean;
import com.pinterest.deployservice.bean.PingResponseBean;
import com.pinterest.deployservice.bean.PingResult;
import com.pinterest.deployservice.dao.AgentDAO;
import com.pinterest.deployservice.dao.EnvironDAO;
import com.pinterest.deployservice.dao.HostAgentDAO;
import com.pinterest.deployservice.dao.HostDAO;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class PingHandlerTest {
    private PingHandler pingHandler;
    private PingRequestBean pingRequest;
    private EnvironDAO environDAO;
    private HostAgentDAO hostAgentDAO;
    private HostDAO hostDAO;
    private AgentDAO agentDAO;
    private EnvironBean mainEnvBean;

    @BeforeEach
    public void setUpTest() throws Exception {
        // Set up test EnvironBeans
        mainEnvBean = genEnvBean("mainEnv");
        EnvironBean sidecarEnvBean1 = genEnvBean("sidecarEnv1");
        sidecarEnvBean1.setSystem_priority(1);
        EnvironBean sidecarEnvBean2 = genEnvBean("sidecarEnv2");
        sidecarEnvBean2.setSystem_priority(2);

        // Set up mock database calls
        Set<String> groups = new HashSet<>();
        groups.add("testMainGroup");
        groups.add("testSidecarGroup1");
        groups.add("testSidecarGroup2");

        environDAO = mock(EnvironDAO.class);
        when(environDAO.getByCluster(eq("testAsg"))).thenReturn(mainEnvBean);
        List<EnvironBean> envBeans = new ArrayList<>();
        envBeans.add(mainEnvBean);
        envBeans.add(sidecarEnvBean1);
        envBeans.add(sidecarEnvBean2);
        when(environDAO.getEnvsByGroups(argThat(groupsArg -> groupsArg.containsAll(groups))))
                .thenReturn(envBeans);
        hostAgentDAO = mock(HostAgentDAO.class);
        hostDAO = mock(HostDAO.class);
        when(hostDAO.getGroupNamesByHost(eq("testHostName"))).thenReturn(new ArrayList<>(groups));
        agentDAO = mock(AgentDAO.class);

        // Set up test PingRequestBean
        pingRequest = new PingRequestBean();
        pingRequest.setHostId("testHostId");
        pingRequest.setHostName("testHostName");
        pingRequest.setHostIp("testHostIp");
        pingRequest.setAutoscalingGroup("testAsg");
        pingRequest.setAccountId("testAccountId");
        pingRequest.setEc2Tags("{\"testTagKey\": \"testTagVal\"}");
        pingRequest.setAgentVersion("testAgentVersion");
        pingRequest.setNormandieStatus(NormandieStatus.OK);
        pingRequest.setKnoxStatus(KnoxStatus.OK);
        pingRequest.setGroups(groups);

        // Set up test PingHandler
        ServiceContext serviceContext = new ServiceContext();
        serviceContext.setEnvironDAO(environDAO);
        serviceContext.setHostAgentDAO(hostAgentDAO);
        serviceContext.setHostDAO(hostDAO);
        serviceContext.setAgentDAO(agentDAO);
        pingHandler = new PingHandler(serviceContext);
    }

    private EnvironBean genEnvBean(String prefix) {
        EnvironBean envBean = new EnvironBean();
        envBean.setEnv_id(String.format("%s-testEnvId", prefix));
        envBean.setEnv_name(String.format("%s-testEnvName", prefix));
        envBean.setStage_name(String.format("%s-testStageName", prefix));
        envBean.setStage_type(EnvType.PRODUCTION);
        envBean.setDeploy_id(String.format("%s-testDeployId", prefix));
        envBean.setDeploy_type(DeployType.REGULAR);
        envBean.setPriority(DeployPriority.NORMAL);
        return envBean;
    }

    @Test
    public void testSingleGoalResponse() throws Exception {
        // Make a ping call
        PingResult pingResult = pingHandler.ping(pingRequest, false);

        // Verify ping response
        PingResponseBean pingResponse = pingResult.getResponseBean();
        assertNull(pingResponse.getMultiGoalResponse());
        assertEquals(OpCode.DEPLOY, pingResponse.getOpCode());

        // Verify deploy goal response
        // The highest priority sidecar comes first
        DeployGoalBean deployGoal = pingResponse.getDeployGoal();
        assertEquals("sidecarEnv1-testDeployId", deployGoal.getDeployId());
        assertEquals("sidecarEnv1-testEnvId", deployGoal.getEnvId());
        assertEquals("sidecarEnv1-testEnvName", deployGoal.getEnvName());
        assertEquals("sidecarEnv1-testStageName", deployGoal.getStageName());
        assertEquals(DeployStage.PRE_DOWNLOAD, deployGoal.getDeployStage());
    }

    @Test
    public void testMultiGoalResponse() throws Exception {
        // Enable multi goal response for the env
        mainEnvBean.setMulti_goal(true);

        // Make a ping call
        PingResult pingResult = pingHandler.ping(pingRequest, false);

        // Verify ping response
        PingResponseBean pingResponse = pingResult.getResponseBean();
        assertEquals(OpCode.DEPLOY, pingResponse.getOpCode());

        // Verify backward-compatible deploy goal response
        // The highest priority sidecar comes first
        DeployGoalBean deployGoal = pingResponse.getDeployGoal();
        assertEquals("sidecarEnv1-testDeployId", deployGoal.getDeployId());
        assertEquals("sidecarEnv1-testEnvId", deployGoal.getEnvId());
        assertEquals("sidecarEnv1-testEnvName", deployGoal.getEnvName());
        assertEquals("sidecarEnv1-testStageName", deployGoal.getStageName());
        assertEquals(1, deployGoal.getSystemPriority());
        assertEquals(DeployStage.PRE_DOWNLOAD, deployGoal.getDeployStage());

        // Verify multi goal response
        List<MultiGoalResponseItemBean> multiGoalResponse = pingResponse.getMultiGoalResponse();
        assertEquals(3, multiGoalResponse.size());

        // Verify sidecar 1 is in response
        MultiGoalResponseItemBean multiGoalItem1 = multiGoalResponse.get(0);
        assertEquals(OpCode.DEPLOY, multiGoalItem1.getOpCode());

        DeployGoalBean deployGoal1 = multiGoalItem1.getDeployGoal();
        assertEquals("sidecarEnv1-testDeployId", deployGoal1.getDeployId());
        assertEquals("sidecarEnv1-testEnvId", deployGoal1.getEnvId());
        assertEquals("sidecarEnv1-testEnvName", deployGoal1.getEnvName());
        assertEquals("sidecarEnv1-testStageName", deployGoal1.getStageName());
        assertEquals(1, deployGoal1.getSystemPriority());
        assertEquals(DeployStage.PRE_DOWNLOAD, deployGoal1.getDeployStage());

        // Verify sidecar 2 is in response
        MultiGoalResponseItemBean multiGoalItem2 = multiGoalResponse.get(1);
        assertEquals(OpCode.DEPLOY, multiGoalItem2.getOpCode());

        DeployGoalBean deployGoal2 = multiGoalItem2.getDeployGoal();
        assertEquals("sidecarEnv2-testDeployId", deployGoal2.getDeployId());
        assertEquals("sidecarEnv2-testEnvId", deployGoal2.getEnvId());
        assertEquals("sidecarEnv2-testEnvName", deployGoal2.getEnvName());
        assertEquals("sidecarEnv2-testStageName", deployGoal2.getStageName());
        assertEquals(2, deployGoal2.getSystemPriority());
        assertEquals(DeployStage.PRE_DOWNLOAD, deployGoal2.getDeployStage());

        // Verify main env is in response
        MultiGoalResponseItemBean multiGoalItem3 = multiGoalResponse.get(2);
        assertEquals(OpCode.DEPLOY, multiGoalItem3.getOpCode());

        DeployGoalBean deployGoal3 = multiGoalItem3.getDeployGoal();
        assertEquals("mainEnv-testDeployId", deployGoal3.getDeployId());
        assertEquals("mainEnv-testEnvId", deployGoal3.getEnvId());
        assertEquals("mainEnv-testEnvName", deployGoal3.getEnvName());
        assertEquals("mainEnv-testStageName", deployGoal3.getStageName());
        assertNull(deployGoal3.getSystemPriority());
        assertEquals(DeployStage.PRE_DOWNLOAD, deployGoal3.getDeployStage());
    }

    @Test
    public void getGetFinalMaxParallelCount() throws Exception {
        EnvironBean bean = new EnvironBean();
        // Always return 1 when nothing set
        assertEquals(1, PingHandler.getFinalMaxParallelCount(bean, 1));
        assertEquals(1, PingHandler.getFinalMaxParallelCount(bean, 10));
        assertEquals(1, PingHandler.getFinalMaxParallelCount(bean, 100));

        // Only hosts set
        bean.setMax_parallel(10);
        assertEquals(1, PingHandler.getFinalMaxParallelCount(bean, 1));
        assertEquals(10, PingHandler.getFinalMaxParallelCount(bean, 10));
        assertEquals(10, PingHandler.getFinalMaxParallelCount(bean, 100));

        // Only percentage set
        bean.setMax_parallel(null);
        bean.setMax_parallel_pct(20);
        assertEquals(1, PingHandler.getFinalMaxParallelCount(bean, 1));
        assertEquals(2, PingHandler.getFinalMaxParallelCount(bean, 10));
        assertEquals(20, PingHandler.getFinalMaxParallelCount(bean, 100));

        // Both set, pick the smaller one
        bean.setMax_parallel(10);
        assertEquals(1, PingHandler.getFinalMaxParallelCount(bean, 1));
        assertEquals(2, PingHandler.getFinalMaxParallelCount(bean, 10));
        assertEquals(10, PingHandler.getFinalMaxParallelCount(bean, 100));

        // Context maxParallelThershold set
        assertEquals(1, PingHandler.calculateParallelThreshold(bean, 2, 1), 1);
        assertEquals(10, PingHandler.calculateParallelThreshold(bean, 2, 1), 10);
        assertEquals(10, PingHandler.calculateParallelThreshold(bean, 2, 1), 100);
    }
}
