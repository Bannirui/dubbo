package org.apache.dubbo.common.extension.spi;

import org.apache.dubbo.common.extension.SPI;

/**
 * @author dingrui
 * @since 2021/12/27
 * @description
 */
@SPI
public interface Animal {
    String speak();
}
