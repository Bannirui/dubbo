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
package com.alibaba.dubbo.common.compiler.support;


import com.alibaba.dubbo.common.compiler.Compiler;
import com.alibaba.dubbo.common.extension.Adaptive;
import com.alibaba.dubbo.common.extension.ExtensionLoader;
import com.alibaba.dubbo.common.extension.SPI;

/**
 * AdaptiveCompiler. (SPI, Singleton, ThreadSafe)
 */
@Adaptive
public class AdaptiveCompiler implements Compiler {

    private static volatile String DEFAULT_COMPILER;

    public static void setDefaultCompiler(String compiler) {
        DEFAULT_COMPILER = compiler;
    }

    /**
     * <p>{@link Compiler}扩展点类上标注了{@link sun.security.provider.ConfigFile.Spi} 并且注解的{@link SPI#value()}的值是javassist 方法级别没有{@link com.alibaba.dubbo.common.extension.Adaptive}</p>
     * <p>classpath配置了3个实现 并且有注有{@link com.alibaba.dubbo.common.extension.Adaptive}的实现 因此这个实现被选为扩展实现<ul>
     *  <li>adaptive=com.alibaba.dubbo.common.compiler.support.AdaptiveCompiler</li>
     *  <li>jdk=com.alibaba.dubbo.common.compiler.support.JdkCompiler</li>
     *  <li>javassist=com.alibaba.dubbo.common.compiler.support.JavassistCompiler</li>
     * </ul></p>
     * <p>扩展点的自适应实现为{@link com.alibaba.dubbo.common.compiler.support.AdaptiveCompiler}</p>
     *
     * <p>但是调用这个方法的时候并不是直接使用某个解析器进行解析 而是实现了类似工厂模式的工厂类 根据扩展名称<tt>name</tt>获取真正的扩展实现{@link Compiler}</p> 这个name开放了api赋值可以从外部设置进来 也可以使用默认的(扩展点上{@link Compiler}的注解属性{@link SPI#value()}就是默认扩展名)
     */
    @Override
    public Class<?> compile(String code, ClassLoader classLoader) {
        Compiler compiler;
        ExtensionLoader<Compiler> loader = ExtensionLoader.getExtensionLoader(Compiler.class);
        String name = DEFAULT_COMPILER; // copy reference
        if (name != null && name.length() > 0) {
            compiler = loader.getExtension(name);
        } else {
            compiler = loader.getDefaultExtension();
        }
        return compiler.compile(code, classLoader);
    }

}
