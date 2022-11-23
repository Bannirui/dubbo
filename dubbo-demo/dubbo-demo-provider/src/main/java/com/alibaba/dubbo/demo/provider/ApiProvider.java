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
        // 服务提供者暴露服务配置 封装了与注册中心的连接
        ServiceConfig<DemoService> service = new ServiceConfig<DemoService>();
        service.setApplication(new ApplicationConfig("native-provider")); // 应用配置
        service.setRegistry(new RegistryConfig("zookeeper://localhost:2181")); // 注册中心
        service.setProtocol(new ProtocolConfig("dubbo", 20880)); // 协议
        service.setInterface(DemoService.class);
        service.setRef(new DemoServiceImpl()); // 提供的服务实现
        // 暴露及注册服务
        service.export();
        System.in.read();
    }
}
