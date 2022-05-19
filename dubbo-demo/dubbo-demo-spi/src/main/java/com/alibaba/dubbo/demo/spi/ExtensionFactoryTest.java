package com.alibaba.dubbo.demo.spi;

import com.alibaba.dubbo.common.extension.ExtensionFactory;
import com.alibaba.dubbo.common.extension.ExtensionLoader;

/**
 *
 * @since 2022/5/19
 * @author dingrui
 */
public class ExtensionFactoryTest {

    public static void main(String[] args) {
        ExtensionLoader<ExtensionFactory> extensionLoader = ExtensionLoader.getExtensionLoader(ExtensionFactory.class);
        ExtensionFactory obj = extensionLoader.getAdaptiveExtension();
        System.out.println(obj);
    }
}
