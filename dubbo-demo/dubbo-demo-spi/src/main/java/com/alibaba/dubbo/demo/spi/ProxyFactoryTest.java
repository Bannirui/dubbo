package com.alibaba.dubbo.demo.spi;

import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.common.extension.SPI;
import com.alibaba.dubbo.rpc.ProxyFactory;

/**
 * <p>{@link ProxyFactory}扩展点被{@link com.alibaba.dubbo.common.extension.SPI}标注 {@link SPI#value()}属性为javassist 该扩展点的方法都被{@link com.alibaba.dubbo.common.extension.Adaptive}修饰</p>
 * <p>扩展实现候选为<ul>
 *     <li>stub=com.alibaba.dubbo.rpc.proxy.wrapper.StubProxyFactoryWrapper</li>
 *     <li>jdk=com.alibaba.dubbo.rpc.proxy.jdk.JdkProxyFactory</li>
 *     <li>javassist=com.alibaba.dubbo.rpc.proxy.javassist.JavassistProxyFactory</li>
 * </ul></p>
 * <p>自适应扩展实现的选取步骤为<ul>
 *     <li>首先候选列表中没有标注{@link com.alibaba.dubbo.common.extension.Adaptive}的类</li>
 *     <li>生成code</li>
 * </ul></p>
 * @since 2022/5/19
 * @author dingrui
 */
public class ProxyFactoryTest {

    public static void main(String[] args) {
        ExtensionLoader<ProxyFactory> extensionLoader = ExtensionLoader.getExtensionLoader(ProxyFactory.class);
        ProxyFactory proxyFactory = extensionLoader.getAdaptiveExtension();
        System.out.println(proxyFactory);

        ProxyFactory extension = (ProxyFactory) extensionLoader.getExtension("javassist");
        System.out.println(extension);
    }
}

/**
 * 生成的code
 */
// package com.alibaba.dubbo.rpc;
// import com.alibaba.dubbo.common.extension.ExtensionLoader;
// public class ProxyFactory$Adaptive implements com.alibaba.dubbo.rpc.ProxyFactory {
//     public java.lang.Object getProxy(com.alibaba.dubbo.rpc.Invoker arg0) throws com.alibaba.dubbo.rpc.RpcException {
//         if (arg0 == null) throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument == null");
//         if (arg0.getUrl() == null) throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument getUrl() == null");com.alibaba.dubbo.common.URL url = arg0.getUrl();
//         String extName = url.getParameter("proxy", "javassist");
//         if(extName == null) throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.ProxyFactory) name from url(" + url.toString() + ") use keys([proxy])");
//         com.alibaba.dubbo.rpc.ProxyFactory extension = (com.alibaba.dubbo.rpc.ProxyFactory)ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.rpc.ProxyFactory.class).getExtension(extName);
//         return extension.getProxy(arg0);
//     }
//     public java.lang.Object getProxy(com.alibaba.dubbo.rpc.Invoker arg0, boolean arg1) throws com.alibaba.dubbo.rpc.RpcException {
//         if (arg0 == null) throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument == null");
//         if (arg0.getUrl() == null) throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument getUrl() == null");com.alibaba.dubbo.common.URL url = arg0.getUrl();
//         String extName = url.getParameter("proxy", "javassist");
//         if(extName == null) throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.ProxyFactory) name from url(" + url.toString() + ") use keys([proxy])");
//         com.alibaba.dubbo.rpc.ProxyFactory extension = (com.alibaba.dubbo.rpc.ProxyFactory)ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.rpc.ProxyFactory.class).getExtension(extName);
//         return extension.getProxy(arg0, arg1);
//     }
//     public com.alibaba.dubbo.rpc.Invoker getInvoker(java.lang.Object arg0, java.lang.Class arg1, com.alibaba.dubbo.common.URL arg2) throws com.alibaba.dubbo.rpc.RpcException {
//         if (arg2 == null) throw new IllegalArgumentException("url == null");
//         com.alibaba.dubbo.common.URL url = arg2;
//         String extName = url.getParameter("proxy", "javassist");
//         if(extName == null) throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.ProxyFactory) name from url(" + url.toString() + ") use keys([proxy])");
//         com.alibaba.dubbo.rpc.ProxyFactory extension = (com.alibaba.dubbo.rpc.ProxyFactory)ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.rpc.ProxyFactory.class).getExtension(extName);
//         return extension.getInvoker(arg0, arg1, arg2);
//     }
// }
