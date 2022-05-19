package com.alibaba.dubbo.demo.spi;

import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.common.extension.SPI;
import com.alibaba.dubbo.rpc.Protocol;

/**
 * <p>{@link Protocol}扩展点标注有{@link com.alibaba.dubbo.common.extension.SPI}注解 并且{@link SPI#value()}属性为dubbo 而且扩展点有两个方法被{@link com.alibaba.dubbo.common.extension.Adaptive}标注</p>
 * <p>classpath配置的扩展实现候选有<ul>
 *     <li>filter=com.alibaba.dubbo.rpc.protocol.ProtocolFilterWrapper</li>
 *     <li>listener=com.alibaba.dubbo.rpc.protocol.ProtocolListenerWrapper</li>
 *     <li>mock=com.alibaba.dubbo.rpc.support.MockProtocol</li>
 * </ul>
 * 这3个实现不存在被{@link com.alibaba.dubbo.common.extension.Adaptive}标注的类 有2个是包装类</p>
 * 因此这个扩展点的自适应扩展实现是通过字节码编码反射方式创建出来的对象
 * @since 2022/5/19
 * @author dingrui
 */
public class ProtocolTest {

    public static void main(String[] args) {
        ExtensionLoader<Protocol> extensionLoader = ExtensionLoader.getExtensionLoader(Protocol.class);
        Protocol refprotocol = extensionLoader.getAdaptiveExtension();
        System.out.println(refprotocol);
    }
}

/**
 * 生成的code
 */
// package com.alibaba.dubbo.rpc;
// import com.alibaba.dubbo.common.extension.ExtensionLoader;
// public class Protocol$Adaptive implements com.alibaba.dubbo.rpc.Protocol {
//     public void destroy() {throw new UnsupportedOperationException("method public abstract void com.alibaba.dubbo.rpc.Protocol.destroy() of interface com.alibaba.dubbo.rpc.Protocol is not adaptive method!");
//     }
//     public int getDefaultPort() {throw new UnsupportedOperationException("method public abstract int com.alibaba.dubbo.rpc.Protocol.getDefaultPort() of interface com.alibaba.dubbo.rpc.Protocol is not adaptive method!");
//     }
//     public com.alibaba.dubbo.rpc.Exporter export(com.alibaba.dubbo.rpc.Invoker arg0) throws com.alibaba.dubbo.rpc.RpcException {
//         if (arg0 == null) throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument == null");
//         if (arg0.getUrl() == null) throw new IllegalArgumentException("com.alibaba.dubbo.rpc.Invoker argument getUrl() == null");com.alibaba.dubbo.common.URL url = arg0.getUrl();
//         String extName = ( url.getProtocol() == null ? "dubbo" : url.getProtocol() );
//         if(extName == null) throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.Protocol) name from url(" + url.toString() + ") use keys([protocol])");
//         com.alibaba.dubbo.rpc.Protocol extension = (com.alibaba.dubbo.rpc.Protocol)ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.rpc.Protocol.class).getExtension(extName);
//         return extension.export(arg0);
//     }
//     public com.alibaba.dubbo.rpc.Invoker refer(java.lang.Class arg0, com.alibaba.dubbo.common.URL arg1) throws com.alibaba.dubbo.rpc.RpcException {
//         if (arg1 == null) throw new IllegalArgumentException("url == null");
//         com.alibaba.dubbo.common.URL url = arg1;
//         String extName = ( url.getProtocol() == null ? "dubbo" : url.getProtocol() );
//         if(extName == null) throw new IllegalStateException("Fail to get extension(com.alibaba.dubbo.rpc.Protocol) name from url(" + url.toString() + ") use keys([protocol])");
//         com.alibaba.dubbo.rpc.Protocol extension = (com.alibaba.dubbo.rpc.Protocol)ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.rpc.Protocol.class).getExtension(extName);
//         return extension.refer(arg0, arg1);
//     }
// }
