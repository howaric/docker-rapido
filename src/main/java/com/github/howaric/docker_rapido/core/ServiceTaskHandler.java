package com.github.howaric.docker_rapido.core;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.howaric.docker_rapido.core.RapidoDockerRunner.RapidoDockerRunnerFactory;
import com.github.howaric.docker_rapido.docker.DockerProxy;
import com.github.howaric.docker_rapido.docker.DockerProxyFactory;
import com.github.howaric.docker_rapido.yaml_model.Node;
import com.github.howaric.docker_rapido.yaml_model.RapidoTemplate;
import com.github.howaric.docker_rapido.yaml_model.Service;

public class ServiceTaskHandler {

    private static Logger logger = LoggerFactory.getLogger(ServiceTaskHandler.class);

    private RapidoTemplate rapidoTemplate;
    private String serviceName;
    private List<Node> targetNodes;
    private String imageTag;

    public static final String LATEST = ":latest";

    public ServiceTaskHandler(RapidoTemplate rapidoTemplate, String serviceName, List<Node> targetNodes, String imageTag) {
        super();
        this.rapidoTemplate = rapidoTemplate;
        this.serviceName = serviceName;
        this.targetNodes = targetNodes;
        this.imageTag = imageTag;
    }

    public void runTask() {
        logger.info("");
        logger.info("****************Start service task: {}****************", serviceName);
        logger.info("Get service task: {}, to build image-tag: {}, rapido will deploy this service to nodes: {}", serviceName, imageTag, targetNodes);
        // build image if need
        Service service = rapidoTemplate.getServices().get(serviceName);
        String image = service.getImage();
        String imageName = image;
        if (imageTag != null) {
            DockerProxy optDocker = DockerProxyFactory.getInstance(rapidoTemplate.getRemote_docker());
            imageName = rapidoTemplate.getRepo() + "/" + imageName + ":" + imageTag;
            logger.info("Start to build image: {}", imageName);
            String imageId = optDocker.buildImage(service.getBuild(), imageName);
            logger.info("Building successfully, imageId is {}", imageId);
        }

        imageName = imageName.contains(":") ? imageName : imageName + LATEST;
        logger.info("Rapido will use image {} to create containers of service {}", imageName, serviceName);

        // go to each node and do operation
        DeployPolicy deployPolicy = service.getDeploy().getDeployPolicy();
        logger.info("Deploy policy is {}", deployPolicy.name().toLowerCase());
        for (Node node : targetNodes) {
            RapidoDockerRunner runner = RapidoDockerRunnerFactory.getInstance(deployPolicy);
            runner.start(rapidoTemplate.getDeliver_type(), rapidoTemplate.getOwner(), node, serviceName, service, imageName);
        }
        logger.info("******************End service task******************", serviceName);
    }

}
