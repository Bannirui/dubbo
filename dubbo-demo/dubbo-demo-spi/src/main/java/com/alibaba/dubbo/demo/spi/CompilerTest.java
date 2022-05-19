package com.alibaba.dubbo.demo.spi;

import com.alibaba.dubbo.common.compiler.Compiler;
import com.alibaba.dubbo.common.extension.ExtensionLoader;

/**
 *
 * @since 2022/5/19
 * @author dingrui
 */
public class CompilerTest {

    public static void main(String[] args) {
        ExtensionLoader<Compiler> extensionLoader = ExtensionLoader.getExtensionLoader(Compiler.class);
        Compiler adaptiveExtension = extensionLoader.getAdaptiveExtension();
        System.out.println(adaptiveExtension);
    }
}
