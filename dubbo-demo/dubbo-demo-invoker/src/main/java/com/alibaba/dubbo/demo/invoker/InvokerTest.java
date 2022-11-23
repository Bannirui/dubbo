package com.alibaba.dubbo.demo.invoker;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.common.utils.StringUtils;
import com.alibaba.dubbo.demo.DemoService;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.Protocol;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @since 2022/5/20
 * @author dingrui
 */
public class InvokerTest {

    public static void main(String[] args) {
        // protocol
        Protocol protocol = ExtensionLoader.getExtensionLoader(Protocol.class).getAdaptiveExtension();
        Map<String, String> map = new HashMap<String,String>();
        map.put("side","consumer");
        map.put("application","demo-consumer");
        map.put("register.ip", "192.168.0.3");
        map.put("methods","sayHello");
        map.put("qos.port","33333");
        map.put("dubbo", "2.0.2");
        map.put("pid", "25943");
        map.put("interface","com.alibaba.dubbo.demo.DemoService");

        URL url = new URL("registry", "224.5.6.7", 1234, "com.alibaba.dubbo.registry.RegistryService");
        url.addParameterAndEncoded("refer", StringUtils.toQueryString(map));
        Invoker<DemoService> refer = protocol.refer(DemoService.class, url);
        System.out.println(refer);
    }
}
