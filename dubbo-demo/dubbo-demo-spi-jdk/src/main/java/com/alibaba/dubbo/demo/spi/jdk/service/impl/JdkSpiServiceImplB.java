package com.alibaba.dubbo.demo.spi.jdk.service.impl;

import com.alibaba.dubbo.demo.spi.jdk.service.JdkSpiService;

/**
 *
 * @since 2022/5/19
 * @author dingrui
 */
public class JdkSpiServiceImplB implements JdkSpiService {
    @Override
    public String sayHello(String s) {
        return "this is B, hello " + s;
    }
}
