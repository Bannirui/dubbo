package com.alibaba.dubbo.demo.spi;

import com.alibaba.dubbo.common.extension.ExtensionFactory;
import com.alibaba.dubbo.common.extension.ExtensionLoader;

/**
 * <p>{@link ExtensionFactory}这个扩展点只在类上标识了{@link com.alibaba.dubbo.common.extension.SPI}注解 方法级别没有{@link com.alibaba.dubbo.common.extension.Adaptive}</p>
 * <p>因此在classpath配置文件里的扩展实现上一定有一个实现类是标注了{@link com.alibaba.dubbo.common.extension.Adaptive}的<ul>
 *     <li>adaptive=com.alibaba.dubbo.common.extension.factory.AdaptiveExtensionFactory</li>
 *     <li>spi=com.alibaba.dubbo.common.extension.factory.SpiExtensionFactory</li>
 * </ul></p>
 * <p>所以自适应扩展点的实现是{@link com.alibaba.dubbo.common.extension.factory.AdaptiveExtensionFactory}</p>
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
