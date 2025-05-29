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
package com.alibaba.dubbo.common.extension;

import com.alibaba.dubbo.common.Constants;
import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.extension.support.ActivateComparator;
import com.alibaba.dubbo.common.logger.Logger;
import com.alibaba.dubbo.common.logger.LoggerFactory;
import com.alibaba.dubbo.common.utils.ConcurrentHashSet;
import com.alibaba.dubbo.common.utils.ConfigUtils;
import com.alibaba.dubbo.common.utils.Holder;
import com.alibaba.dubbo.common.utils.StringUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

/**
 * Load dubbo extensions
 * <ul>
 * <li>auto inject dependency extension </li>
 * <li>auto wrap extension in wrapper </li>
 * <li>default extension is an adaptive instance</li>
 * </ul>
 *
 * @see <a href="http://java.sun.com/j2se/1.5.0/docs/guide/jar/jar.html#Service%20Provider">Service Provider in Java 5</a>
 * @see com.alibaba.dubbo.common.extension.SPI
 * @see com.alibaba.dubbo.common.extension.Adaptive
 * @see com.alibaba.dubbo.common.extension.Activate
 */
/**
 * 关于SPI的扫描策略
 * <ul>
 *     <li>自己写个实现类打上`@Adaptive`注解，SPI退化</li>
 *     <li>接口上打`@SPI`注解指定默认别名，方法打上`@Adaptive`注解<ul>
 *         <li>没指定别名 -> 解析接口名，比如MyInterfaceName就被解析成my.interface.name<ul>
 *             <li>protocol特殊处理，直接url.getProtocol()拿到别名，再拿着别名去找实现</li>
 *             <li>其他的用url.getParameter(xxx)拿到别名，再拿着别名去找实现</li>
 *         </ul></li>
 *         <li>指定了key -> 用这个key去`url.getParamter(key)`作别名去找实现，没找到再用`@SPI`注解指定的别名去找</li>
 *     </ul></li>
 * </ul>
 */
public class ExtensionLoader<T> {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionLoader.class);

    private static final String SERVICES_DIRECTORY = "META-INF/services/";

    private static final String DUBBO_DIRECTORY = "META-INF/dubbo/";

    private static final String DUBBO_INTERNAL_DIRECTORY = DUBBO_DIRECTORY + "internal/";

    private static final Pattern NAME_SEPARATOR = Pattern.compile("\\s*[,]+\\s*");

    private static final ConcurrentMap<Class<?>, ExtensionLoader<?>> EXTENSION_LOADERS = new ConcurrentHashMap<Class<?>, ExtensionLoader<?>>(); // 缓存 扩展接口以及对应的扩展类加载器

    /**
     * 接口{@link ExtensionLoader#type}接口的实现
     * <ul>
     *     <li>要么是用户自定义的类打上了{@link Adaptive}注解</li>
     *     <li>要么是dubbo编码生成的代理类</li>
     * </ul>
     * 缓存的维度是
     * <ul>
     *     <li>key 类</li>
     *     <li>val 实例</li>
     * </ul>
     */
    private static final ConcurrentMap<Class<?>, Object> EXTENSION_INSTANCES = new ConcurrentHashMap<Class<?>, Object>(); // 接口type实现类对象跟实例之间的映射关系

    // ==============================
    /**
     * SPI就是面向接口+动态发现实现
     * 这个type就是接口
     */
    private final Class<?> type;

    /**
     * 除了ExtensionFactory的ExtensionLoader这个属性为空 其他的扩展接口的扩展类加载器这个属性都是AdaptiveExtensionFactory的实例
     * ExtensionFactory本身也是一个扩展接口
     * classpath文件
     *     - adaptive=com.alibaba.dubbo.common.extension.factory.AdaptiveExtensionFactory
     *     - spi=com.alibaba.dubbo.common.extension.factory.SpiExtensionFactory
     * AdaptiveExtensionFactory实现上打上了注解@Adaptive 因此作为ExtensionFactory这个接口的默认实现
     *
     * objectFactory作用是为了解决可能存在的setter注入一个扩展
     */
    private final ExtensionFactory objectFactory;

    /**
     * 实现别名和实现的映射
     * <ul>
     *     <li>key=实现</li>
     *     <li>val=实现的别名</li>
     * </ul>
     */
    private final ConcurrentMap<Class<?>, String> cachedNames = new ConcurrentHashMap<Class<?>, String>();

    /**
     * 3级缓存
     * <ul>
     *     <li>1级缓存 {@link ExtensionLoader#cachedAdaptiveClass}</li>
     *     <li>2级缓存 {@link ExtensionLoader#cachedWrapperClasses}</li>
     *     <li>3级缓存 {@link ExtensionLoader#cachedClasses}</li>
     * </ul>
     * <ul>
     *     <li>键 给实现实例起的名字 比如ZookeeperRegistryFactory的名字叫zookeeper</li>
     *     <li>值 实现类的全限定路径名</li>
     * </ul>
     */
    private final Holder<Map<String, Class<?>>> cachedClasses = new Holder<Map<String, Class<?>>>();

    private final Map<String, Activate> cachedActivates = new ConcurrentHashMap<String, Activate>();
    /**
     * 缓存ExtensionLoader创建过的实现实例
     * <ul>
     *     <li>键 实例名 比如ZookeeperRegistryFactory的名称是zookeeper 就是在配置文件中的键</li>
     *     <li>值 实现的实例对象</li>
     * </ul>
     */
    private final ConcurrentMap<String, Holder<Object>> cachedInstances = new ConcurrentHashMap<String, Holder<Object>>();
    /**
     * 扩展点的适配类 封装在Holder中
     */
    private final Holder<Object> cachedAdaptiveInstance = new Holder<Object>();
    /**
     * 1级缓存
     * <ul>
     *     <li>1级缓存 {@link ExtensionLoader#cachedAdaptiveClass}</li>
     *     <li>2级缓存 {@link ExtensionLoader#cachedWrapperClasses}</li>
     *     <li>3级缓存 {@link ExtensionLoader#cachedClasses}</li>
     * </ul>
     * 找到的实现 这个实现的类上打了{@link Adaptive}注解 这种情况的语义就是让这个实现成为接口的默认实现 不需要再继续找动态实现了
     * 因此先缓存在这 将来找实现的时候肯定也是优先看看这个缓存有没有
     * 并且 不支持多个实现类都打了{@link Adaptive}注解
     * 如果不是用户自己实现了类用{@link Adaptive}标识就由dubbo创建代理类放在1级缓存{@link ExtensionLoader#cachedAdaptiveClass}
     */
    private volatile Class<?> cachedAdaptiveClass = null;
    /**
     * 接口实现的默认别名
     * 通过在接口声明上打注解@SPI("xxx")的方式指定这个接口的多实现中xxx为默认的实现
     * 为什么需要指定默认实现 比如@Adaptive({"protocol"})是从URL中获取protocol对应的配置值作为要找的实现名
     * 所以没找到期望的实现就用默认的
     */
    private String cachedDefaultName;
    private volatile Throwable createAdaptiveInstanceError; // 标识扩展实现实例创建出现异常

    /**
     * 2级缓存
     * <ul>
     *     <li>1级缓存 {@link ExtensionLoader#cachedAdaptiveClass}</li>
     *     <li>2级缓存 {@link ExtensionLoader#cachedWrapperClasses}</li>
     *     <li>3级缓存 {@link ExtensionLoader#cachedClasses}</li>
     * </ul>
     */
    private Set<Class<?>> cachedWrapperClasses;

    private Map<String, IllegalStateException> exceptions = new ConcurrentHashMap<String, IllegalStateException>();

    private ExtensionLoader(Class<?> type) {
        this.type = type; // 扩展的接口
        this.objectFactory = (type == ExtensionFactory.class ? null : ExtensionLoader.getExtensionLoader(ExtensionFactory.class).getAdaptiveExtension());
    }

    private static <T> boolean withExtensionAnnotation(Class<T> type) {
        return type.isAnnotationPresent(SPI.class);
    }

    @SuppressWarnings("unchecked")
    public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        // 必要的参数校验
        if (type == null) throw new IllegalArgumentException("Extension type == null");
        // 接口的扩展加载起ExtensionLoader实例都放在缓存中
        ExtensionLoader<T> loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        if (loader == null) {
            EXTENSION_LOADERS.putIfAbsent(type, new ExtensionLoader<T>(type));
            loader = (ExtensionLoader<T>) EXTENSION_LOADERS.get(type);
        }
        return loader;
    }

    private static ClassLoader findClassLoader() {
        return ExtensionLoader.class.getClassLoader();
    }

    public String getExtensionName(T extensionInstance) {
        return getExtensionName(extensionInstance.getClass());
    }

    public String getExtensionName(Class<?> extensionClass) {
        return cachedNames.get(extensionClass);
    }

    /**
     * This is equivalent to {@code getActivateExtension(url, key, null)}
     *
     * @param url url
     * @param key url parameter key which used to get extension point names
     * @return extension list which are activated.
     * @see #getActivateExtension(com.alibaba.dubbo.common.URL, String, String)
     */
    public List<T> getActivateExtension(URL url, String key) {
        return getActivateExtension(url, key, null);
    }

    /**
     * This is equivalent to {@code getActivateExtension(url, values, null)}
     *
     * @param url    url
     * @param values extension point names
     * @return extension list which are activated
     * @see #getActivateExtension(com.alibaba.dubbo.common.URL, String[], String)
     */
    public List<T> getActivateExtension(URL url, String[] values) {
        return getActivateExtension(url, values, null);
    }

    /**
     * This is equivalent to {@code getActivateExtension(url, url.getParameter(key).split(","), null)}
     *
     * @param url   url
     * @param key   url parameter key which used to get extension point names
     * @param group group
     * @return extension list which are activated.
     * @see #getActivateExtension(com.alibaba.dubbo.common.URL, String[], String)
     */
    public List<T> getActivateExtension(URL url, String key, String group) {
        String value = url.getParameter(key);
        return getActivateExtension(url, value == null || value.length() == 0 ? null : Constants.COMMA_SPLIT_PATTERN.split(value), group);
    }

    /**
     * Get activate extensions.
     *
     * @param url    url
     * @param values extension point names
     * @param group  group
     * @return extension list which are activated
     * @see com.alibaba.dubbo.common.extension.Activate
     */
    public List<T> getActivateExtension(URL url, String[] values, String group) {
        List<T> exts = new ArrayList<T>();
        List<String> names = values == null ? new ArrayList<String>(0) : Arrays.asList(values);
        if (!names.contains(Constants.REMOVE_VALUE_PREFIX + Constants.DEFAULT_KEY)) {
            getExtensionClasses();
            for (Map.Entry<String, Activate> entry : cachedActivates.entrySet()) {
                String name = entry.getKey();
                Activate activate = entry.getValue();
                if (isMatchGroup(group, activate.group())) {
                    if (!names.contains(name)
                            && !names.contains(Constants.REMOVE_VALUE_PREFIX + name)
                            && isActive(activate, url)) {
                        T ext = getExtension(name);
                        exts.add(ext);
                    }
                }
            }
            Collections.sort(exts, ActivateComparator.COMPARATOR);
        }
        List<T> usrs = new ArrayList<T>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            if (!name.startsWith(Constants.REMOVE_VALUE_PREFIX)
                    && !names.contains(Constants.REMOVE_VALUE_PREFIX + name)) {
                if (Constants.DEFAULT_KEY.equals(name)) {
                    if (!usrs.isEmpty()) {
                        exts.addAll(0, usrs);
                        usrs.clear();
                    }
                } else {
                    T ext = getExtension(name);
                    usrs.add(ext);
                }
            }
        }
        if (!usrs.isEmpty()) {
            exts.addAll(usrs);
        }
        return exts;
    }

    private boolean isMatchGroup(String group, String[] groups) {
        if (group == null || group.length() == 0) {
            return true;
        }
        if (groups != null && groups.length > 0) {
            for (String g : groups) {
                if (group.equals(g)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isActive(Activate activate, URL url) {
        String[] keys = activate.value();
        if (keys.length == 0) {
            return true;
        }
        for (String key : keys) {
            for (Map.Entry<String, String> entry : url.getParameters().entrySet()) {
                String k = entry.getKey();
                String v = entry.getValue();
                if ((k.equals(key) || k.endsWith("." + key))
                        && ConfigUtils.isNotEmpty(v)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get extension's instance. Return <code>null</code> if extension is not found or is not initialized. Pls. note
     * that this method will not trigger extension load.
     * <p>
     * In order to trigger extension load, call {@link #getExtension(String)} instead.
     *
     * @see #getExtension(String)
     */
    @SuppressWarnings("unchecked")
    public T getLoadedExtension(String name) {
        if (name == null || name.length() == 0)
            throw new IllegalArgumentException("Extension name == null");
        Holder<Object> holder = cachedInstances.get(name);
        if (holder == null) {
            cachedInstances.putIfAbsent(name, new Holder<Object>());
            holder = cachedInstances.get(name);
        }
        return (T) holder.get();
    }

    /**
     * Return the list of extensions which are already loaded.
     * <p>
     * Usually {@link #getSupportedExtensions()} should be called in order to get all extensions.
     *
     * @see #getSupportedExtensions()
     */
    public Set<String> getLoadedExtensions() {
        return Collections.unmodifiableSet(new TreeSet<String>(cachedInstances.keySet()));
    }

    /**
     * Find the extension with the given name. If the specified name is not found, then {@link IllegalStateException}
     * will be thrown.
     */
    /**
     * 根据别名找实现的实例对象
     * @param name 要找的实现名
     *             比如zk实现的注册中心registry是ZookeeperRegistry 它的创建由ZookeeperRegistryFactory负责
     *             RegistryFactory接口全限定名作文件名
     *             键=zookeeper
     *             值=ZookeeperRegistryFactory类全限定路径名
     */
    @SuppressWarnings("unchecked")
    public T getExtension(String name) {
        if (name == null || name.length() == 0) throw new IllegalArgumentException("Extension name == null");
        if ("true".equals(name)) return this.getDefaultExtension(); // type接口注解@SPI标识的名称 对应的实现作为接口默认实现
        // 先看看有没有现成的缓存
        Holder<Object> holder = this.cachedInstances.get(name);
        if (holder == null) {
            cachedInstances.putIfAbsent(name, new Holder<Object>());
            holder = cachedInstances.get(name);
        }
        Object instance = holder.get();
        if (instance == null) {
            synchronized (holder) {
                instance = holder.get();
                if (instance == null) {
                    instance = this.createExtension(name);
                    holder.set(instance);
                }
            }
        }
        return (T) instance;
    }

    /**
     * Return default extension, return <code>null</code> if it's not configured.
     */
    public T getDefaultExtension() { // 在hash表缓存的实现类对象找@SPI指定的名称
        /**
         * 扫描Dubbo SPI指定的3个classpath路径
         *     - 将接口type指定的实现缓存起来
         *         - 实现上有注解Adaptive的单独缓存到cachedAdaptiveClass
         *         - 实现类是包装类对象 缓存到cachedWrapperClasses
         *         - 其他实现类对象缓存到hash表
         *     - 将接口type注解SPI指定的默认实现名称缓存起来
         */
        this.getExtensionClasses();
        if (null == cachedDefaultName || cachedDefaultName.length() == 0 || "true".equals(cachedDefaultName))
            return null;
        return this.getExtension(cachedDefaultName);
    }

    public boolean hasExtension(String name) {
        if (name == null || name.length() == 0)
            throw new IllegalArgumentException("Extension name == null");
        try {
            this.getExtensionClass(name);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public Set<String> getSupportedExtensions() {
        Map<String, Class<?>> clazzes = getExtensionClasses();
        return Collections.unmodifiableSet(new TreeSet<String>(clazzes.keySet()));
    }

    /**
     * Return default extension name, return <code>null</code> if not configured.
     */
    public String getDefaultExtensionName() {
        getExtensionClasses();
        return cachedDefaultName;
    }

    /**
     * Register new extension via API
     *
     * @param name  extension name
     * @param clazz extension class
     * @throws IllegalStateException when extension with the same name has already been registered.
     */
    public void addExtension(String name, Class<?> clazz) {
        getExtensionClasses(); // load classes

        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Input type " +
                    clazz + "not implement Extension " + type);
        }
        if (clazz.isInterface()) {
            throw new IllegalStateException("Input type " +
                    clazz + "can not be interface!");
        }

        if (!clazz.isAnnotationPresent(Adaptive.class)) {
            if (StringUtils.isBlank(name)) {
                throw new IllegalStateException("Extension name is blank (Extension " + type + ")!");
            }
            if (cachedClasses.get().containsKey(name)) {
                throw new IllegalStateException("Extension name " +
                        name + " already existed(Extension " + type + ")!");
            }

            cachedNames.put(clazz, name);
            cachedClasses.get().put(name, clazz);
        } else {
            if (cachedAdaptiveClass != null) {
                throw new IllegalStateException("Adaptive Extension already existed(Extension " + type + ")!");
            }

            cachedAdaptiveClass = clazz;
        }
    }

    /**
     * Replace the existing extension via API
     *
     * @param name  extension name
     * @param clazz extension class
     * @throws IllegalStateException when extension to be placed doesn't exist
     * @deprecated not recommended any longer, and use only when test
     */
    @Deprecated
    public void replaceExtension(String name, Class<?> clazz) {
        getExtensionClasses(); // load classes

        if (!type.isAssignableFrom(clazz)) {
            throw new IllegalStateException("Input type " +
                    clazz + "not implement Extension " + type);
        }
        if (clazz.isInterface()) {
            throw new IllegalStateException("Input type " +
                    clazz + "can not be interface!");
        }

        if (!clazz.isAnnotationPresent(Adaptive.class)) {
            if (StringUtils.isBlank(name)) {
                throw new IllegalStateException("Extension name is blank (Extension " + type + ")!");
            }
            if (!cachedClasses.get().containsKey(name)) {
                throw new IllegalStateException("Extension name " +
                        name + " not existed(Extension " + type + ")!");
            }

            cachedNames.put(clazz, name);
            cachedClasses.get().put(name, clazz);
            cachedInstances.remove(name);
        } else {
            if (cachedAdaptiveClass == null) {
                throw new IllegalStateException("Adaptive Extension not existed(Extension " + type + ")!");
            }

            cachedAdaptiveClass = clazz;
            cachedAdaptiveInstance.set(null);
        }
    }

    /**
     * 当前扩展接口的自适应扩展实现
     *     - 取缓存
     *     - 创建扩展点的自适应扩展实现
     *         - 实现类反射创建实例对象
     *             - 扫文件路径过程中缓存着@Adaptive标识的实现类
     *             - 没有指定@Adaptive标识 编码实现
     *         - 对实例对象setter方法检查
     *     - 存缓存
     */
    @SuppressWarnings("unchecked")
    public T getAdaptiveExtension() {
        Object instance = this.cachedAdaptiveInstance.get();
        // 典型的synchronized DCL
        if (instance == null) {
            if (createAdaptiveInstanceError == null) {
                synchronized (cachedAdaptiveInstance) {
                    instance = cachedAdaptiveInstance.get(); // 双检查
                    if (instance == null) {
                        try {
                            instance = this.createAdaptiveExtension(); // 为扩展接口创建自适应扩展实现
                            cachedAdaptiveInstance.set(instance); // 放缓存
                        } catch (Throwable t) {
                            createAdaptiveInstanceError = t;
                            throw new IllegalStateException("fail to create adaptive instance: " + t.toString(), t);
                        }
                    }
                }
            } else {
                throw new IllegalStateException("fail to create adaptive instance: " + createAdaptiveInstanceError.toString(), createAdaptiveInstanceError);
            }
        }

        return (T) instance;
    }

    private IllegalStateException findException(String name) {
        for (Map.Entry<String, IllegalStateException> entry : exceptions.entrySet()) {
            if (entry.getKey().toLowerCase().contains(name.toLowerCase())) {
                return entry.getValue();
            }
        }
        StringBuilder buf = new StringBuilder("No such extension " + type.getName() + " by name " + name);


        int i = 1;
        for (Map.Entry<String, IllegalStateException> entry : exceptions.entrySet()) {
            if (i == 1) {
                buf.append(", possible causes: ");
            }

            buf.append("\r\n(");
            buf.append(i++);
            buf.append(") ");
            buf.append(entry.getKey());
            buf.append(":\r\n");
            buf.append(StringUtils.toString(entry.getValue()));
        }
        return new IllegalStateException(buf.toString());
    }

    /**
     * 在扫描到的实现中找到别名对应的实现 用反射创建实例
     * @param name 在配置中给实现配置的别名 比如ZookeeperRegistryFactory的别名是zookeeper
     * @return 别名对应的实现实例
     */
    @SuppressWarnings("unchecked")
    private T createExtension(String name) {
        /**
         * 拿到实现类
         * <ul>
         *     <li>可能是用户自定义的类打上了{@link Adaptive}注解</li>
         *     <li>可能是dubbo编码生成的代理类</li>
         * </ul>
         */
        Class<?> clazz = this.getExtensionClasses().get(name);
        if (clazz == null) throw findException(name);
        try {
            T instance = (T) EXTENSION_INSTANCES.get(clazz);
            if (instance == null) {
                // 反射创建实现的实例对象
                EXTENSION_INSTANCES.putIfAbsent(clazz, clazz.newInstance());
                instance = (T) EXTENSION_INSTANCES.get(clazz);
            }
            // 解决set循环注入场景
            this.injectExtension(instance);
            // 2级缓存
            Set<Class<?>> wrapperClasses = cachedWrapperClasses;
            if (wrapperClasses != null && !wrapperClasses.isEmpty()) {
                for (Class<?> wrapperClass : wrapperClasses)
                    instance = this.injectExtension((T) wrapperClass.getConstructor(type).newInstance(instance));
            }
            return instance;
        } catch (Throwable t) {
            throw new IllegalStateException("Extension instance(name: " + name + ", class: " + type + ")  could not be instantiated: " + t.getMessage(), t);
        }
    }

    /**
     *
     * 实现已经获取 其中存在一个setter方法 设置的属性本身又是自己接口的子类
     * @param instance <ul>
     *                   <li>要么是用户自己定义的类打上了{@link Adaptive}注解</li>
     *                   <li>要么是dubbo用编码技术生成了代理类</li>
     *                 </ul>
     * @return
     */
    private T injectExtension(T instance) {
        try {
            /**
             *  ExtensionFactory的ExtensionLoader的objectFactory为空 其他都不是空
             *      - ExtensionLoader<ExtensionFactory> objectFactory是空 直接返回的实例就是AdaptiveExtensionFactory的实现实例
             *      - ExtensionLoader<T> objectFactory指向了ExtensionLoader<ExtensionFactory>对象
             */
            if (objectFactory != null) {
                for (Method method : instance.getClass().getMethods()) {
                    // setter方法
                    if (method.getName().startsWith("set")
                            && method.getParameterTypes().length == 1
                            && Modifier.isPublic(method.getModifiers())) {
                        /**
                         * Check {@link DisableInject} to see if we need auto injection for this property
                         */
                        if (method.getAnnotation(DisableInject.class) != null)
                            continue;
                        Class<?> pt = method.getParameterTypes()[0]; // setxxx这个setter方法的形参 肯定只有一个参数
                        try {
                            // setxxx这个setter方法注入的属性名称xxx
                            String property = method.getName().length() > 3 ? method.getName().substring(3, 4).toLowerCase() + method.getName().substring(4) : "";
                            /**
                             * Duboo是否存在SPI实现
                             *     - name是property
                             *     - 接口类型是pt
                             * 获取setter参数的扩展实现 目的是为了解决扩展实现里面的setter属性注入的依赖
                             */
                            Object object = this.objectFactory.getExtension(pt, property);
                            if (object != null)
                                method.invoke(instance, object);
                        } catch (Exception e) {
                            logger.error("fail to inject via method " + method.getName()
                                    + " of interface " + type.getName() + ": " + e.getMessage(), e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return instance;
    }

    private Class<?> getExtensionClass(String name) {
        if (type == null)
            throw new IllegalArgumentException("Extension type == null");
        if (name == null)
            throw new IllegalArgumentException("Extension name == null");
        Class<?> clazz = getExtensionClasses().get(name);
        if (clazz == null)
            throw new IllegalStateException("No such extension \"" + name + "\" for " + type.getName() + "!");
        return clazz;
    }

    /**
     * <ul>
     *     <li>classpath:META-INF/dubbo/internal/</li>
     *     <li>classpath:META-INF/dubbo/</li>
     *     <li>classpath:META-INF/services/</li>
     * </ul>
     * 在配置目录下根据{@link ExtensionLoader#type}接口的全限定路径名找到具体的配置文件
     * 在配置文件中找到配置的实现缓存到合适的位置
     * <ul>
     *     <li>实现类上打了{@link Adaptive}注解的缓存在{@link ExtensionLoader#cachedAdaptiveClass}</li>
     *     <li>实现类有构造方法 参数类型是{@link ExtensionLoader#type} 这种实现类缓存在{@link ExtensionLoader#cachedWrapperClasses}</li>
     *     <li>除此之外的缓存在{@link ExtensionLoader#cachedClasses}</li>
     * </ul>
     */
    private Map<String, Class<?>> getExtensionClasses() {
        Map<String, Class<?>> classes = cachedClasses.get();
        if (classes == null) {
            synchronized (cachedClasses) {
                classes = cachedClasses.get();
                if (classes == null) {
                    classes = this.loadExtensionClasses(); // 从指定文件加载出type这个接口的实现
                    this.cachedClasses.set(classes);
                }
            }
        }
        return classes;
    }

    // synchronized in getExtensionClasses
    /**
     * <ul>
     *     <li>classpath:META-INF/dubbo/internal/</li>
     *     <li>classpath:META-INF/dubbo/</li>
     *     <li>classpath:META-INF/services/</li>
     * </ul>
     * 在配置目录下根据{@link ExtensionLoader#type}接口的全限定路径名找到具体的配置文件
     * 在配置文件中找到配置的实现缓存到合适的位置
     * <ul>
     *     <li>实现类上打了{@link Adaptive}注解的缓存在{@link ExtensionLoader#cachedAdaptiveClass}</li>
     *     <li>实现类有构造方法 参数类型是{@link ExtensionLoader#type} 这种实现类缓存在{@link ExtensionLoader#cachedWrapperClasses}</li>
     *     <li>除此之外的缓存在extensionClasses这个hash表</li>
     * </ul>
     * @return 配置文件中配置的常规的实现
     *         <ul>
     *             <li>key 实现的别名</li>
     *             <li>val 实现的实例</li>
     *         </ul>
     */
    private Map<String, Class<?>> loadExtensionClasses() {
        /**
         * 接口上打的{@link SPI}注解 用为作为实现别名的默认别名
         * 要是没用{@link Adaptive}指定从{@link URL}中解析参数作为别名 就用默认的别名找实现
         */
        final SPI defaultAnnotation = type.getAnnotation(SPI.class);
        if (defaultAnnotation != null) {
            String value = defaultAnnotation.value();
            if ((value = value.trim()).length() > 0) {
                String[] names = NAME_SEPARATOR.split(value);
                if (names.length > 1)
                    throw new IllegalStateException("more than 1 default extension name on extension " + type.getName() + ": " + Arrays.toString(names));
                if (names.length == 1) this.cachedDefaultName = names[0];
            }
        }

        /**
         * 缓存着接口的所有实现
         * <ul>
         *     <li>key=实现的别名</li>
         *     <li>val=实现的实例</li>
         * </ul>
         */
        Map<String, Class<?>> extensionClasses = new HashMap<String, Class<?>>();
        /**
         * 3个目录
         * <ul>
         *     <li>classpath:META-INF/dubbo/internal/</li>
         *     <li>classpath:META-INF/dubbo/</li>
         *     <li>classpath:META-INF/services/</li>
         * </ul>
         * 在配置目录下根据{@link ExtensionLoader#type}接口的全限定路径名找到具体的配置文件
         * 在配置文件中找到配置的实现缓存到合适的位置
         * <ul>
         *     <li>实现类上打了{@link Adaptive}注解的缓存在{@link ExtensionLoader#cachedAdaptiveClass}</li>
         *     <li>实现类有构造方法 参数类型是{@link ExtensionLoader#type} 这种实现类缓存在{@link ExtensionLoader#cachedWrapperClasses}</li>
         *     <li>除此之外的缓存在extensionClasses这个hash表</li>
         * </ul>
         */
        this.loadDirectory(extensionClasses, DUBBO_INTERNAL_DIRECTORY);
        this.loadDirectory(extensionClasses, DUBBO_DIRECTORY);
        this.loadDirectory(extensionClasses, SERVICES_DIRECTORY);
        return extensionClasses;
    }

    /**
     * 找到配置文件中配置的实现类 分门别类缓存到合适的地方
     * <ul>
     *     <li>实现类上打了{@link Adaptive}注解的缓存在{@link ExtensionLoader#cachedAdaptiveClass}</li>
     *     <li>实现类有构造方法 参数类型是{@link ExtensionLoader#type} 这种实现类缓存在{@link ExtensionLoader#cachedWrapperClasses}</li>
     *     <li>除此之外的缓存在extensionClasses这个hash表</li>
     * </ul>
     * @param extensionClasses 用来缓存找到的实现
     * @param dir 要找的资源路径 在配置文件中定义了实现及实现的别名
     *            比如zookeeper=com.alibaba.dubbo.registry.zookeeper.ZookeeperRegistryFactory
     */
    private void loadDirectory(Map<String, Class<?>> extensionClasses, String dir) {
        // 找到配置文件 接口的全限定路径名
        String fileName = dir + type.getName();
        try {
            Enumeration<java.net.URL> urls;
            ClassLoader classLoader = this.findClassLoader();
            if (classLoader != null)
                urls = classLoader.getResources(fileName);
            else
                urls = ClassLoader.getSystemResources(fileName);
            if (urls != null) {
                while (urls.hasMoreElements()) {
                    java.net.URL resourceURL = urls.nextElement();
                    this.loadResource(extensionClasses, classLoader, resourceURL);
                }
            }
        } catch (Throwable t) {
            logger.error("Exception when load extension class(interface: " + type + ", description file: " + fileName + ").", t);
        }
    }

    /**
     * 配置文件中的实现类分门别类缓存到合适的地方
     * <ul>
     *     <li>实现类上打了{@link Adaptive}注解的缓存在{@link ExtensionLoader#cachedAdaptiveClass}</li>
     *     <li>实现类有构造方法 参数类型是{@link ExtensionLoader#type} 这种实现类缓存在{@link ExtensionLoader#cachedWrapperClasses}</li>
     *     <li>除此之外的缓存在extensionClasses这个hash表</li>
     * </ul>
     * @param extensionClasses 用来缓存{@link ExtensionLoader#type}这个接口配置的实现及实现对应的别名
     * @param resourceURL 配置文件路径
     */
    private void loadResource(Map<String, Class<?>> extensionClasses, ClassLoader classLoader, java.net.URL resourceURL) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(resourceURL.openStream(), "utf-8"));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    final int ci = line.indexOf('#');
                    if (ci >= 0) line = line.substring(0, ci);
                    line = line.trim();
                    /**
                     * 在配置文件中的配置格式是
                     * 实现的别名=实现的全限定路径
                     * 比如 zookeeper=com.alibaba.dubbo.registry.zookeeper.ZookeeperRegistryFactory
                     * 把每个实现类都缓存到合适的地方
                     * <ul>
                     *     <li>实现类上打了{@link Adaptive}注解的缓存在{@link ExtensionLoader#cachedAdaptiveClass}</li>
                     *     <li>实现类有构造方法 参数类型是{@link ExtensionLoader#type} 这种实现类缓存在{@link ExtensionLoader#cachedWrapperClasses}</li>
                     *     <li>除此之外的缓存在extensionClasses这个hash表</li>
                     * </ul>
                     */
                    if (line.length() > 0) {
                        try {
                            String name = null;
                            int i = line.indexOf('=');
                            if (i > 0) {
                                // 实现的别名
                                name = line.substring(0, i).trim();
                                // 实现的全限定路径
                                line = line.substring(i + 1).trim();
                            }
                            // 找到的实现类缓存起来
                            if (line.length() > 0)
                                this.loadClass(extensionClasses, resourceURL, Class.forName(line, true, classLoader), name);
                        } catch (Throwable t) {
                            IllegalStateException e = new IllegalStateException("Failed to load extension class(interface: " + type + ", class line: " + line + ") in " + resourceURL + ", cause: " + t.getMessage(), t);
                            exceptions.put(line, e);
                        }
                    }
                }
            } finally {
                reader.close();
            }
        } catch (Throwable t) {
            logger.error("Exception when load extension class(interface: " + type + ", class file: " + resourceURL + ") in " + resourceURL, t);
        }
    }

    /**
     * 找到了一个实现 要把这个实现缓存起来
     * 对找到的实现类型 有3个不同的缓存地方
     * <ul>
     *     <li>实现类上打了{@link Adaptive}注解的缓存在{@link ExtensionLoader#cachedAdaptiveClass}</li>
     *     <li>实现类有构造方法 参数类型是{@link ExtensionLoader#type} 这种实现类缓存在{@link ExtensionLoader#cachedWrapperClasses}</li>
     *     <li>除此之外的缓存在extensionClasses这个hash表</li>
     * </ul>
     * @param extensionClasses 用来缓存找到的{@link ExtensionLoader#type}的一个实现类
     *                         key=实现的别名
     *                         val=实现的实例
     * @param clazz 在配置文件中给{@link ExtensionLoader#type}这个接口配置的一个实现 类的全限定路径
     * @param name 在配置文件中给实现起的别名
     */
    private void loadClass(Map<String, Class<?>> extensionClasses, java.net.URL resourceURL, Class<?> clazz, String name) throws NoSuchMethodException {
        if (!type.isAssignableFrom(clazz))
            throw new IllegalStateException("Error when load extension class(interface: " + type + ", class line: " + clazz.getName() + "), class " + clazz.getName() + "is not subtype of interface.");
        // 实现类上打了Adaptive注解 这就说明这个类被指定成了接口的实现 不需要再动态找实现了
        if (clazz.isAnnotationPresent(Adaptive.class)) {
            if (this.cachedAdaptiveClass == null)
                this.cachedAdaptiveClass = clazz;
            else if (!cachedAdaptiveClass.equals(clazz)) // 扩展实现中只能存在一个标注了@Adaptive注解的实现
                throw new IllegalStateException("More than 1 adaptive class found: " + cachedAdaptiveClass.getClass().getName() + ", " + clazz.getClass().getName());
        } else if (this.isWrapperClass(clazz)) {
            Set<Class<?>> wrappers = this.cachedWrapperClasses;
            if (wrappers == null) {
                this.cachedWrapperClasses = new ConcurrentHashSet<Class<?>>();
                wrappers = this.cachedWrapperClasses;
            }
            wrappers.add(clazz);
        } else {
            clazz.getConstructor();
            if (name == null || name.length() == 0) {
                name = findAnnotationName(clazz);
                if (name.length() == 0)
                    throw new IllegalStateException("No such extension name for the class " + clazz.getName() + " in the config " + resourceURL);
            }
            String[] names = NAME_SEPARATOR.split(name);
            if (names != null && names.length > 0) {
                Activate activate = clazz.getAnnotation(Activate.class);
                if (activate != null)
                    this.cachedActivates.put(names[0], activate);
                for (String n : names) {
                    if (!this.cachedNames.containsKey(clazz)) cachedNames.put(clazz, n);
                    Class<?> c = extensionClasses.get(n);
                    if (c == null) {
                        extensionClasses.put(n, clazz);
                    } else if (c != clazz)
                        throw new IllegalStateException("Duplicate extension " + type.getName() + " name " + n + " on " + c.getName() + " and " + clazz.getName());
                }
            }
        }
    }

    private boolean isWrapperClass(Class<?> clazz) {
        try {
            /**
             * 这种类被成为Wrapper
             * 实现有构造方法 并且这个构造方法只有一个参数 参数类型就是这个实现的接口类型
             */
            clazz.getConstructor(type);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    @SuppressWarnings("deprecation")
    private String findAnnotationName(Class<?> clazz) {
        com.alibaba.dubbo.common.Extension extension = clazz.getAnnotation(com.alibaba.dubbo.common.Extension.class);
        if (extension == null) {
            String name = clazz.getSimpleName();
            if (name.endsWith(type.getSimpleName())) {
                name = name.substring(0, name.length() - type.getSimpleName().length());
            }
            return name.toLowerCase();
        }
        return extension.value();
    }

    /**
     * 依赖ExtensionLoader这个扩展实现加载器 为扩展点type这个接口创建合适的实现
     *     - 先找到自适应扩展实现类对象
     *         - 扫描Dubbo SPI路径过程着缓存下找到的@Adaptive标识的实现类对象
     *         - 编码技术生成类
     *     - 根据找到的类对象反射创建实例
     *     - 用AdaptiveExtensionFactory检查实例的setter方法 注入循环扩展对象
     */
    @SuppressWarnings("unchecked")
    private T createAdaptiveExtension() {
        try {
            /**
             *     - getAdaptiveExtensionClass()加载出自适应实现
             *         - 优先级1 Dubbo SPI指定扫描路径上有@Adaptive标识的实现
             *         - 优先级2 编码技术生成
             *     - newInstance() 对选择出来的唯一的type接口实现 通过反射创建实现的实例
             *     - injectExtension()
             *         - 解决扩展实现的setter属性注入依赖的问题
             */
            return this.injectExtension((T) this.getAdaptiveExtensionClass().newInstance());
        } catch (Exception e) {
            throw new IllegalStateException("Can not create adaptive extension " + type + ", cause: " + e.getMessage(), e);
        }
    }

    /**
     * <ul>
     *     <li>要么是用户定义的类打上了{@link Adaptive}</li>
     *     <li>要么是dubbo用编码技术给接口创建了代理方法</li>
     * </ul>
     */
    private Class<?> getAdaptiveExtensionClass() {
        /**
         * 尝试扫描配置文件 把实现缓存起来
         */
        this.getExtensionClasses();
        /**
         * 在{@link ExtensionLoader#cachedAdaptiveClass}缓存着接口{@link ExtensionLoader#type}的用户侧通过在实现类上打{@link Adaptive}注解标识的实现
         */
        if (this.cachedAdaptiveClass != null)
            return this.cachedAdaptiveClass;
        /**
         * 用户没有指定实现方式 dubbo就要生成实现方式了
         */
        return this.cachedAdaptiveClass = this.createAdaptiveExtensionClass();
    }

    /**
     * 编码技术创建接口的代理类
     * @return 为接口{@link ExtensionLoader#type}创建的代理类
     */
    private Class<?> createAdaptiveExtensionClass() {
        /**
         * 给接口{@link ExtensionLoader#type}这个接口的方法编码实现代理方法
         */
        String code = this.createAdaptiveExtensionClassCode();
        ClassLoader classLoader = findClassLoader(); // 当前类加载器
        com.alibaba.dubbo.common.compiler.Compiler compiler = ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.common.compiler.Compiler.class).getAdaptiveExtension();
        // 根据源码生成类
        return compiler.compile(code, classLoader);
    }

    /**
     * 没有在接口{@link ExtensionLoader#type}的实现类上打{@link Adaptive}注解作为指定实现
     * 要通过编码方式生成实现 就是调用{@link ExtensionLoader#getExtension(String)}拿到对应接口的实现
     * 而实现的别名就是根据接口的注解拿
     * <ul>
     *     <li>接口只用了{@link SPI} 实现别名就是这个注解的方法指定的</li>
     *     <li>接口除了用{@link SPI}外 还用了{@link Adaptive}<ul>
     *         <li>{@link Adaptive}注解方法没有指定 就用{@link SPI}指定的值作key从{@link URL#getParameter(String)}拿到别名</li>
     *         <li>{@link Adaptive}注解方法指定了值就用指定的值作key从{}</li>
     *     </ul></li>
     * </ul>
     * @return dubbo生成的java源码 实现的接口中方法的代理
     */
    private String createAdaptiveExtensionClassCode() {
        StringBuilder codeBuilder = new StringBuilder();
        // 接口中声明的方法
        Method[] methods = type.getMethods();
        boolean hasAdaptiveAnnotation = false;
        /**
         * 找到{@link ExtensionLoader#type}接口中第一个被{@link Adaptive}修饰的要从{@link URL}中查询配置参数找真正实现方式
         */
        for (Method m : methods) {
            if (m.isAnnotationPresent(Adaptive.class)) { // 检测接口中方法是否有@Adaptive注解标识
                hasAdaptiveAnnotation = true;
                break;
            }
        }
        // no need to generate adaptive class since there's no adaptive method found.
        if (!hasAdaptiveAnnotation)
            throw new IllegalStateException("No adaptive method on extension " + type.getName() + ", refuse to create the adaptive class!");

        // package {type所在包};
        codeBuilder.append("package ").append(type.getPackage().getName()).append(";");
        // import {ExtensionLoader全限定名};
        codeBuilder.append("\nimport ").append(ExtensionLoader.class.getName()).append(";");
        // public class {type简单名称}$Adaptive implements {type全限定名} {
        codeBuilder.append("\npublic class ").append(type.getSimpleName()).append("$Adaptive").append(" implements ").append(type.getCanonicalName()).append(" {");

        /**
         * 扩展接口type中方法 每个方法的内部逻辑代码生成
         *     - 无@Adaptive标识
         *         - 方法内部逻辑就是抛个异常表明不支持该方法
         *     - 有@Adaptive标识
         */
        /**
         * 现在执行到这的场景是没有手动实现类标注{@link Adaptive} 也就是没有手动实现接口
         * 所以需要依赖dubbo去实现接口 那么也就意味着接口{@link ExtensionLoader#type}中方法一定是被{@link Adaptive}注解的 不然dubbo不知道要实现什么方法
         * 所以开始轮询接口的方法根据{@link Adaptive}标识找方法 为方法生成实现代码
         */
        for (Method method : methods) {
            /**
             * 方法签名必要的信息
             * <ul>
             *     <li>返回值</li>
             *     <li>形参</li>
             *     <li>上抛异常</li>
             * </ul>
             */
            Class<?> rt = method.getReturnType();
            Class<?>[] pts = method.getParameterTypes();
            Class<?>[] ets = method.getExceptionTypes();
            // 看看接口方法上有没有Adaptive注解
            Adaptive adaptiveAnnotation = method.getAnnotation(Adaptive.class);
            // 要实现的接口方法的代码块逻辑 最后在这个代码上套上方法签名就是完整的方法实现
            StringBuilder code = new StringBuilder(512);
            if (adaptiveAnnotation == null) {
                /**
                 * 方法没有被{@link Adaptive}标识
                 * throw new UnsupportedOperation(...);
                 */
                code.append("throw new UnsupportedOperationException(\"method ").append(method.toString()).append(" of interface ").append(type.getName()).append(" is not adaptive method!\");");
            } else {
                /**
                 * dubbo的SPI机制依赖{@link URL} 运行时从{@link URL}中找key对应的val作为实现别名
                 * 所以方法签名中必须依赖{@link URL}参数 没有这个参数就抛异常
                 * 参数列表中找{@link URL}类型 记录在参数列表中的脚标 找不到就-1标识
                 */
                int urlTypeIndex = -1;
                for (int i = 0; i < pts.length; ++i) {
                    if (pts[i].equals(URL.class)) {
                        urlTypeIndex = i;
                        break;
                    }
                }
                // found parameter in URL type
                if (urlTypeIndex != -1) {
                    // Null Point check
                    String s = String.format("\nif (arg%d == null) throw new IllegalArgumentException(\"url == null\");", urlTypeIndex);
                    code.append(s);
                    s = String.format("\n%s url = arg%d;", URL.class.getName(), urlTypeIndex);
                    code.append(s);
                }
                // did not find parameter in URL type
                else {
                    // 接口方法中没有直接URL的形参 但是能找到某个形参类有get方法可以间接拿到URL 缓存get方法
                    String attribMethod = null;

                    // find URL getter method
                    /**
                     * 没有直接的{@link URL}类型的形参 但是可能其他的参数可以通过get方法提供{@link URL}
                     * 比如在{@link Protocol}中 它的export方法打上了{@link Adaptive}
                     * 如果啥都没有
                     * <ul>
                     *     <li>既没有直接提供{@link URL}类型参数</li>
                     *     <li>又没有间接提供{@link URL}类型参数</li>
                     * </ul>
                     * 就没有办法生成实现代码 上抛异常
                     * 轮询所有的参数类型 但凡找到一个getter方法的返回值是URL类型就标识出来
                     * 如果压根不存在这样的一个获取URL的getter方法 那就无法创建实现
                     */
                    LBL_PTS:
                    for (int i = 0; i < pts.length; ++i) {
                        // 能找到接口方法中某个形参的类里面有get方法可以拿到URL
                        Method[] ms = pts[i].getMethods();
                        for (Method m : ms) {
                            String name = m.getName();
                            if ((name.startsWith("get") || name.length() > 3)
                                    && Modifier.isPublic(m.getModifiers())
                                    && !Modifier.isStatic(m.getModifiers())
                                    && m.getParameterTypes().length == 0
                                    && m.getReturnType() == URL.class) {
                                urlTypeIndex = i;
                                // get方法
                                attribMethod = name;
                                break LBL_PTS;
                            }
                        }
                    }
                    if (attribMethod == null)
                        throw new IllegalStateException("fail to create adaptive class for interface " + type.getName() + ": not found url parameter or url attribute in parameters of method " + method.getName());
                    // Null point check
                    String s = String.format("\nif (arg%d == null) throw new IllegalArgumentException(\"%s argument == null\");", urlTypeIndex, pts[urlTypeIndex].getName());
                    code.append(s);
                    s = String.format("\nif (arg%d.%s() == null) throw new IllegalArgumentException(\"%s argument %s() == null\");", urlTypeIndex, attribMethod, pts[urlTypeIndex].getName(), attribMethod);
                    code.append(s);
                    // get方法拿到URL
                    s = String.format("%s url = arg%d.%s();", URL.class.getName(), urlTypeIndex, attribMethod);
                    code.append(s);
                }
                /**
                 * 怎么去{@link URL}中拿配置 就得先有key
                 * <ul>
                 *     <li>可以通过{@link Adaptive}注解直接指定</li>
                 *     <li>{@link Adaptive}注解没有指定 就用接口中作<ul>
                 *         <li>类Protocol的key就是protocol</li>
                 *         <li>MyProtocol的key就是my.protocol</li>
                 *     </ul></li>
                 * </ul>
                 */
                String[] value = adaptiveAnnotation.value();
                // value is not set, use the value generated from class name as the key
                if (value.length == 0) {
                    char[] charArray = this.type.getSimpleName().toCharArray();
                    StringBuilder sb = new StringBuilder(128);
                    for (int i = 0; i < charArray.length; i++) {
                        if (Character.isUpperCase(charArray[i])) {
                            if (i != 0) sb.append(".");
                            sb.append(Character.toLowerCase(charArray[i]));
                        } else {
                            sb.append(charArray[i]);
                        }
                    }
                    value = new String[]{sb.toString()};
                }

                /**
                 * 找到方法形参中Invocation类型的参数
                 * if(arg0==null) throw new IllegalArgumentException("invocation==null");
                 * String methodName = arg0.getMethodName();
                 */
                boolean hasInvocation = false;
                for (int i = 0; i < pts.length; ++i) {
                    if (pts[i].getName().equals("com.alibaba.dubbo.rpc.Invocation")) {
                        // Null Point check
                        String s = String.format("\nif (arg%d == null) throw new IllegalArgumentException(\"invocation == null\");", i);
                        code.append(s);
                        s = String.format("\nString methodName = arg%d.getMethodName();", i);
                        code.append(s);
                        hasInvocation = true;
                        break;
                    }
                }

                String defaultExtName = cachedDefaultName;
                /**
                 * dubbo的SPI核心 本质就是在找实现的别名extName
                 * 比如RegistryFactory的zk实现就是ZookeeperRegistryFactory的别名是zookeeper
                 */
                String getNameCode = null;
                /**
                 * {@link Adaptive}注解指定的key从{@link URL#getParameter(String)}中拿到实现的别名
                 */
                for (int i = value.length - 1; i >= 0; --i) {
                    if (i == value.length - 1) {
                        if (null != defaultExtName) {
                            if (!"protocol".equals(value[i]))
                                if (hasInvocation)
                                    getNameCode = String.format("url.getMethodParameter(methodName, \"%s\", \"%s\")", value[i], defaultExtName);
                                else
                                    getNameCode = String.format("url.getParameter(\"%s\", \"%s\")", value[i], defaultExtName);
                            else
                                getNameCode = String.format("( url.getProtocol() == null ? \"%s\" : url.getProtocol() )", defaultExtName);
                        } else {
                            if (!"protocol".equals(value[i]))
                                if (hasInvocation)
                                    getNameCode = String.format("url.getMethodParameter(methodName, \"%s\", \"%s\")", value[i], defaultExtName);
                                else
                                    getNameCode = String.format("url.getParameter(\"%s\")", value[i]);
                            else
                                getNameCode = "url.getProtocol()";
                        }
                    } else {
                        if (!"protocol".equals(value[i]))
                            if (hasInvocation)
                                getNameCode = String.format("url.getMethodParameter(methodName, \"%s\", \"%s\")", value[i], defaultExtName);
                            else
                                getNameCode = String.format("url.getParameter(\"%s\", %s)", value[i], getNameCode);
                        else
                            getNameCode = String.format("url.getProtocol() == null ? (%s) : url.getProtocol()", getNameCode);
                    }
                }
                /**
                 * dubbo的SPI核心 本质就是在找实现的别名extName
                 * 比如RegistryFactory的zk实现就是ZookeeperRegistryFactory的别名是zookeeper
                 */
                code.append("\nString extName = ").append(getNameCode).append(";");
                // check extName == null?
                String s = String.format("\nif(extName == null) " + "throw new IllegalStateException(\"Fail to get extension(%s) name from url(\" + url.toString() + \") use keys(%s)\");",
                        type.getName(), Arrays.toString(value));
                code.append(s);

                /**
                 * dubbo的SPI核心 本质就是给要实现的方法套个代理方法 真正的实现在运行时候让代理去找
                 * 而代理方法就是在找实现的别名extName
                 * 比如
                 * RegistryFactory extension = (RegistryFactory)ExtensionLoader.getExtensionLoader(RegistryFactory.class).getExtension(zookeeper);
                 * return extension.当前的代理的这个方法;
                 */
                s = String.format("\n%s extension = (%<s)%s.getExtensionLoader(%s.class).getExtension(extName);",
                        type.getName(), ExtensionLoader.class.getSimpleName(), type.getName());
                code.append(s);
                // return statement
                if (!rt.equals(void.class)) {
                    code.append("\nreturn ");
                }

                s = String.format("extension.%s(", method.getName());
                code.append(s);
                for (int i = 0; i < pts.length; i++) {
                    if (i != 0)
                        code.append(", ");
                    code.append("arg").append(i);
                }
                code.append(");");
            }
            codeBuilder.append("\npublic ").append(rt.getCanonicalName()).append(" ").append(method.getName()).append("(");
            for (int i = 0; i < pts.length; i++) {
                if (i > 0) {
                    codeBuilder.append(", ");
                }
                codeBuilder.append(pts[i].getCanonicalName());
                codeBuilder.append(" ");
                codeBuilder.append("arg").append(i);
            }
            codeBuilder.append(")");
            if (ets.length > 0) {
                codeBuilder.append(" throws ");
                for (int i = 0; i < ets.length; i++) {
                    if (i > 0) {
                        codeBuilder.append(", ");
                    }
                    codeBuilder.append(ets[i].getCanonicalName());
                }
            }
            codeBuilder.append(" {");
            codeBuilder.append(code.toString());
            codeBuilder.append("\n}");
        }
        codeBuilder.append("\n}");
        if (logger.isDebugEnabled()) {
            logger.debug(codeBuilder.toString());
        }
        return codeBuilder.toString();
    }

    @Override
    public String toString() {
        return this.getClass().getName() + "[" + type.getName() + "]";
    }

}