package org.apache.dubbo.common.extension.spi;

/**
 * @author dingrui
 * @since 2021/12/27
 * @description
 */
public class Cat implements Animal{

    @Override
    public String speak() {
        return "this is cat";
    }
}
