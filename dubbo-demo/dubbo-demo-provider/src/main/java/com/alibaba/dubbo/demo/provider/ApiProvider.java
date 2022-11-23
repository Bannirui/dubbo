package com.alibaba.dubbo.demo.provider;

import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ProtocolConfig;
import com.alibaba.dubbo.config.RegistryConfig;
import com.alibaba.dubbo.config.ServiceConfig;
import com.alibaba.dubbo.demo.DemoService;

import java.io.IOException;

/**
 * <p>以dubbo api方式构建 整合spring的思路肯定是利用后置处理器封装api的构建方式 目前将关注集中在dubbo本身</p>
 * @since 2022/5/18
 * @author dingrui
 */
public class ApiProvider {

    public static void main(String[] args) throws IOException {
        // 服务实现
        DemoService demoService = new DemoServiceImpl();

        // 当前应用配置
        ApplicationConfig application = new ApplicationConfig();
        application.setName("demo-provider");

        // 连接注册中心配置
        RegistryConfig registry = new RegistryConfig();
        // multicast协议
        // registry.setAddress("multicast://224.5.6.7:1234");
        // zk协议
        registry.setAddress("zookeeper://localhost:2181");

        // 服务提供者协议配置
        ProtocolConfig protocol = new ProtocolConfig();
        protocol.setName("dubbo");
        protocol.setPort(20880);
        protocol.setThreads(200);

        // 服务提供者暴露服务配置 封装了与注册中心的连接
        ServiceConfig<DemoService> service = new ServiceConfig<DemoService>();
        service.setApplication(application);
        // 注册中心
        service.setRegistry(registry);
        // 协议
        service.setProtocol(protocol);
        service.setInterface(DemoService.class);
        service.setRef(demoService);

        // 暴露及注册服务
        service.export();

        System.in.read();
    }
}
