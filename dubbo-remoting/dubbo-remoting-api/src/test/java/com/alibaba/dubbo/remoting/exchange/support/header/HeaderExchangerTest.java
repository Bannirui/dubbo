package com.alibaba.dubbo.remoting.exchange.support.header;

import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.remoting.exchange.Exchanger;
import junit.framework.TestCase;
import org.junit.Test;

/**
 *
 * @since 2022/11/27
 * @author dingrui
 */
public class HeaderExchangerTest {

    @Test
    public void test00(){
        Exchanger adaptiveExtension = ExtensionLoader.getExtensionLoader(Exchanger.class).getAdaptiveExtension();
        System.out.println();
    }
}