package com.alibaba.dubbo.demo.spi.jdk;

import com.alibaba.dubbo.demo.spi.jdk.service.JdkSpiService;

import java.util.ServiceLoader;

/**
 *
 * @since 2022/5/19
 * @author dingrui
 */
public class JdkSpiClient {

    public static void main(String[] args) {
        ServiceLoader<JdkSpiService> services = ServiceLoader.load(JdkSpiService.class);
        /**
         * jdk内置工具类扫描出指定文件配置的所有实现
         * 扫描过程中将所有的实现都通过反射的技术实现实例化缓存起来
         */
        for (JdkSpiService s : services) {
            String ret = s.sayHello("world");
            System.out.println(ret);
        }
    }
}
