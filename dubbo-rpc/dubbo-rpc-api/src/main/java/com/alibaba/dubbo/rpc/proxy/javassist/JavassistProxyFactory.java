/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.dubbo.rpc.proxy.javassist;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.bytecode.Proxy;
import com.alibaba.dubbo.common.bytecode.Wrapper;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;
import com.alibaba.dubbo.rpc.proxy.AbstractProxyFactory;
import com.alibaba.dubbo.rpc.proxy.AbstractProxyInvoker;
import com.alibaba.dubbo.rpc.proxy.InvokerInvocationHandler;

/**
 * JavaassistRpcProxyFactory
 */
public class JavassistProxyFactory extends AbstractProxyFactory {

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getProxy(Invoker<T> invoker, Class<?>[] interfaces) {
        /**
         * Proxy.getProxy()通过字节码技术生成了一个代理对象 这个代理对象提供了newInstance()的方法
         * public Object newInstance(java.lang.reflect.InvocationHandler h){
         *     return new com.alibaba.dubbo.common.bytecode.Proxy0($1);
         * }
         *
         * 后续对代理对象进行操作
         * 实际是委托给了InvokerInvocationHandler进行处理
         */
        return (T) Proxy.getProxy(interfaces).newInstance(new InvokerInvocationHandler(invoker));
    }

    @Override
    public <T> Invoker<T> getInvoker(T proxy, Class<T> type, URL url) {
        // TODO Wrapper cannot handle this scenario correctly: the classname contains '$'
        /**
         * 编码方式创建代理对象
         * 为什么会判断$ 因为{@link com.alibaba.dubbo.common.extension.SPI}机制会创建默认的代理类 在{@link com.alibaba.dubbo.common.extension.ExtensionLoader}中能看到dubbo生成的Adaptive类命名方式
         * 接口名+$Adaptive
         * <ul>
         *     <li>所以这个地方发现已经是代理类了就对代理类再包一层代理</li>
         *     <li>不是代理类就直接对接口操作 基于接口创建代理</li>
         * </ul>
         */
        final Wrapper wrapper = Wrapper.getWrapper(proxy.getClass().getName().indexOf('$') < 0 ? proxy.getClass() : type);
        /**
         * 创建一个{@link Invoker}对象 典型的模板方法
         * 最终客户端拿到了{@link Invoker}实例调用{@link Invoker#invoke}会执行到这个匿名类对象的{@link AbstractProxyInvoker#doInvoke}方法
         * 而真正的执行逻辑又会委派给{@link Wrapper}执行
         */
        return new AbstractProxyInvoker<T>(proxy, type, url) {
            @Override
            protected Object doInvoke(T proxy, String methodName,
                                      Class<?>[] parameterTypes,
                                      Object[] arguments) throws Throwable {
                return wrapper.invokeMethod(proxy, methodName, parameterTypes, arguments); // 通过代理对象执行目标对象的方法
            }
        };
    }

}
