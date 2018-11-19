package com.github.howaric.docker_rapido.core;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.howaric.docker_rapido.exceptions.ContainerStartingFailedException;
import com.github.howaric.docker_rapido.utils.CommonUtil;
import com.github.howaric.docker_rapido.utils.LogUtil;

public abstract class DeployProcessor extends AbstractNodeProcessor {

    private static Logger logger = LoggerFactory.getLogger(DeployProcessor.class);

    protected static final String healthUrlTemplate = "http://%s:%s/health";

    protected void checkSpringActuatorHealthStatus(String ip, String port) {
        RestTemplate restTemplate = new RestTemplate();
        String url = String.format(healthUrlTemplate, ip, port);
        logger.info("Start to check if application is healthy from check-url: {}", url);
        boolean isReady = false;
        for (int i = 0; i < 20; i++) {
            try {
                @SuppressWarnings("rawtypes")
                ResponseEntity<Map> result = restTemplate.getForEntity(url, Map.class);
                if (result.getStatusCode() == HttpStatus.OK) {
                    logger.info("Query result: {}", result.getBody());
                    String status = (String) result.getBody().get("status");
                    if ("UP".equals(status)) {
                        logger.info("Service on port {} is ready!", port);
                        isReady = true;
                        break;
                    }
                } else {
                    logger.warn(result.getStatusCode() + ": " + result.getBody());
                }
            } catch (Exception e) {
                logger.warn("Not ready, continue to check...");
                CommonUtil.sleep(5000);
            }
        }
        logger.info("Check result: " + isReady);
        if (!isReady) {
            throw new ContainerStartingFailedException("Service on port " + port + " is not ready!");
        }
    }

    protected static final String consulCheckUrlTemplate = "http://%s:8500/v1/health/checks/%s";

    protected boolean isContainerRegisteredSuccessfullyInConsul(String containerId) {
        InspectContainerResponse inspectContainer = dockerProxy.inspectContainer(containerId);
        ContainerNetwork containerNetwork = inspectContainer.getNetworkSettings().getNetworks().get("bridge");
        if (containerNetwork != null) {
            String containerIp = containerNetwork.getIpAddress();
            logger.info("Get container IP: {}", containerIp);
            checkIfClientIpRegisteredSuccessfullyInConsul(containerIp);
            return true;
        } else {
            throw new ContainerStartingFailedException("Get container Ip from network 'bridge' failed");
        }
    }

    protected void checkIfClientIpRegisteredSuccessfullyInConsul(String clientIp) {
        String url = String.format(consulCheckUrlTemplate, node.getIp(), serviceName);
        logger.info("Start to check if application has been successfully registered in consul...");
        logger.info("Check url: {}", url);
        RestTemplate restTemplate = new RestTemplate();
        boolean isReady = false;
        outer: for (int i = 0; i < 20; i++) {
            try {
                @SuppressWarnings("rawtypes")
                ResponseEntity<List> result = restTemplate.getForEntity(url, List.class);
                if (result.getStatusCode() == HttpStatus.OK) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, String>> checkList = result.getBody();
                    for (Map<String, String> map : checkList) {
                        String output = map.get("Output");
                        if (output.contains(clientIp) && output.contains("UP")) {
                            logger.info("Consul result: {}", output);
                            logger.info("Container is ready!");
                            isReady = true;
                            break outer;
                        }
                    }
                    logger.warn("Not ready, continue to check...");
                } else {
                    logger.warn(result.getStatusCode() + ": " + result.getBody());
                }
            } catch (Exception e) {
                logger.warn("Contact with consul failed, continue to check...");
            }
            CommonUtil.sleep(5000);
        }
        if (!isReady) {
            throw new ContainerStartingFailedException("Container is not healthy, starting failed");
        }
    }

    protected void checkContainerStatus(String containerId) {
        CommonUtil.sleep(20 * 1000);
        String status = dockerProxy.inspectContainer(containerId).getState().getStatus();
        if (!isContainerUp(status)) {
            printContainerStartingLogs(containerId);
            throw new ContainerStartingFailedException(
                    "Container of service " + serviceName + " on node " + node.getIp() + " is down: " + containerId);
        }
    }

    protected boolean isContainerUp(String status) {
        if ("running".equals(status) || "healthy".equals(status)) {
            return true;
        }
        return false;
    }

    protected void printContainerStartingLogs(String containerId) {
        LogUtil.printEmptyLine();
        LogUtil.printInCentreWithStar("LOGS OF CONTAINER " + containerId);
        System.out.println("");
        dockerProxy.printLogs(containerId);
        System.out.println("");
        LogUtil.printInCentreWithStar("LOGS END");
        LogUtil.printEmptyLine();
    }
}