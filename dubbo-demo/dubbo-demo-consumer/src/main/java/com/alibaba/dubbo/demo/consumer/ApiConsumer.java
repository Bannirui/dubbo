package com.alibaba.dubbo.demo.consumer;

import com.alibaba.dubbo.config.ApplicationConfig;
import com.alibaba.dubbo.config.ReferenceConfig;
import com.alibaba.dubbo.config.RegistryConfig;
import com.alibaba.dubbo.demo.DemoService;

/**
 * <p>以dubbo api方式构建</p>
 * @since 2022/5/18
 * @author dingrui
 */
public class ApiConsumer {

    public static void main(String[] args) {
        // 当前应用配置
        ApplicationConfig application = new ApplicationConfig();
        application.setName("demo-consumer");

        // 连接注册中心配置
        RegistryConfig registry = new RegistryConfig();
        registry.setAddress("multicast://224.5.6.7:1234");

        // 引用远程服务 此实例很重，封装了与注册中心的连接以及与提供者的连接
        ReferenceConfig<DemoService> reference = new ReferenceConfig<DemoService>();
        reference.setApplication(application); // 配置应用信息
        reference.setRegistry(registry); // 配置注册中心信息
        reference.setInterface(DemoService.class); // 引用的远程服务的接口抽象

        // 和本地bean一样使用service 此代理对象内部封装了所有通讯细节 服务端获得了远程服务的代理对象 像调用本地方法一样
        DemoService demoService = reference.get();
        String ret = demoService.sayHello("world");
        System.out.println("--------------");
        System.out.println(ret);
    }
}
