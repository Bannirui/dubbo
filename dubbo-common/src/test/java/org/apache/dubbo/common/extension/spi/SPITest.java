package org.apache.dubbo.common.extension.spi;

import org.apache.dubbo.common.extension.ExtensionLoader;

/**
 * @author dingrui
 * @since 2021/12/27
 * @description dubbo中SPI机制
 */
public class SPITest {

    public static void main(String[] args) {
        ExtensionLoader<Animal> exLoader = ExtensionLoader.getExtensionLoader(Animal.class);
        Animal cat = exLoader.getExtension("cat");
        String catRet = cat.speak();
        System.out.println(catRet);
        Animal dog = exLoader.getExtension("dog");
        String dogRet = dog.speak();
        System.out.println(dogRet);
        System.out.println();
    }
}
