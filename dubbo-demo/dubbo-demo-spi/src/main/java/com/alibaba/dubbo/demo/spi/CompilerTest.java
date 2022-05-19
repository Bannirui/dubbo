package com.alibaba.dubbo.demo.spi;

import com.alibaba.dubbo.common.compiler.Compiler;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.common.extension.SPI;

/**
 * <p>{@link Compiler}扩展点类上标注了{@link sun.security.provider.ConfigFile.Spi} 并且注解的{@link SPI#value()}的值是javassist 方法级别没有{@link com.alibaba.dubbo.common.extension.Adaptive}</p>
 * <p>classpath配置了3个实现 并且有注有{@link com.alibaba.dubbo.common.extension.Adaptive}的实现 因此这个实现被选为扩展实现<ul>
 *     <li>adaptive=com.alibaba.dubbo.common.compiler.support.AdaptiveCompiler</li>
 *     <li>jdk=com.alibaba.dubbo.common.compiler.support.JdkCompiler</li>
 *     <li>javassist=com.alibaba.dubbo.common.compiler.support.JavassistCompiler</li>
 * </ul></p>
 * <p>扩展点的实现为{@link com.alibaba.dubbo.common.compiler.support.AdaptiveCompiler}</p>
 * @since 2022/5/19
 * @author dingrui
 */
public class CompilerTest {

    public static void main(String[] args) {
        ExtensionLoader<Compiler> extensionLoader = ExtensionLoader.getExtensionLoader(Compiler.class);
        Compiler adaptiveExtension = extensionLoader.getAdaptiveExtension();
        System.out.println(adaptiveExtension);
        Class<?> ret = adaptiveExtension.compile("", CompilerTest.class.getClassLoader());
    }
}
