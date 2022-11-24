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
public class ExtensionLoader<T> {

    private static final Logger logger = LoggerFactory.getLogger(ExtensionLoader.class);

    private static final String SERVICES_DIRECTORY = "META-INF/services/";

    private static final String DUBBO_DIRECTORY = "META-INF/dubbo/";

    private static final String DUBBO_INTERNAL_DIRECTORY = DUBBO_DIRECTORY + "internal/";

    private static final Pattern NAME_SEPARATOR = Pattern.compile("\\s*[,]+\\s*");

    private static final ConcurrentMap<Class<?>, ExtensionLoader<?>> EXTENSION_LOADERS = new ConcurrentHashMap<Class<?>, ExtensionLoader<?>>(); // 缓存 扩展接口以及对应的扩展类加载器

    private static final ConcurrentMap<Class<?>, Object> EXTENSION_INSTANCES = new ConcurrentHashMap<Class<?>, Object>(); // 接口type实现类对象跟实例之间的映射关系

    // ==============================

    private final Class<?> type; // 扩展点(接口类型)

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

    private final ConcurrentMap<Class<?>, String> cachedNames = new ConcurrentHashMap<Class<?>, String>();

    /**
     * 封装了classpath文件里面所有的扩展接口实现 key=名称 value=实现实例
     */
    private final Holder<Map<String, Class<?>>> cachedClasses = new Holder<Map<String, Class<?>>>();

    private final Map<String, Activate> cachedActivates = new ConcurrentHashMap<String, Activate>();
    private final ConcurrentMap<String, Holder<Object>> cachedInstances = new ConcurrentHashMap<String, Holder<Object>>();
    /**
     * 扩展点的适配类 封装在Holder中
     */
    private final Holder<Object> cachedAdaptiveInstance = new Holder<Object>();
    /**
     * 在遍历SPI文件中扩展实现的第一优先级
     *     - 扩展点的扩展实现中第一个标注Adaptive注解的类对象
     */
    private volatile Class<?> cachedAdaptiveClass = null;
    /**
     * 接口type有多个实现
     * 通过在接口声明上打注解@SPI("xxx")的方式指定这个接口的多实现中xxx为默认的实现
     * 外界getExtension(...)获取type接口实现时如果没指定就使用默认的
     */
    private String cachedDefaultName;
    private volatile Throwable createAdaptiveInstanceError; // 标识扩展实现实例创建出现异常

    /**
     * 在遍历SPI文件中扩展实现的第二优先级
     *     - 扩展点的扩展实现是包装类型
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

    /**
     * 给定接口type的扩展类加载器ExtensionLoader
     */
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
     * 接口type的实现类缓存优先级
     *     - 实现类上第一个打上@Adaptive注解的缓存在cachedAdaptiveClass
     *     - 实现类是包装类的缓存在cachedWrapperClasses
     *     - 其他实现类缓存在hash表中
     *         - getExtension(...)就是从这份缓存中找指定名称的实现类
     *         - getDefaultExtension(...)是根据接口上@SPI的名称作为依据在这个缓存中找的实现类
     */
    @SuppressWarnings("unchecked")
    public T getExtension(String name) {
        if (name == null || name.length() == 0) throw new IllegalArgumentException("Extension name == null");
        if ("true".equals(name)) return this.getDefaultExtension(); // type接口注解@SPI标识的名称 对应的实现作为接口默认实现
        Holder<Object> holder = this.cachedInstances.get(name);
        if (holder == null) {
            cachedInstances.putIfAbsent(name, new Holder<Object>());
            holder = cachedInstances.get(name);
        }
        Object instance = holder.get();
        if (instance == null) {
            synchronized (holder) {
                instance = holder.get(); // 接口type扩展的实现
                if (instance == null) {
                    instance = this.createExtension(name); // 缓存里面没有就去扫描里面找
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
     * 指定路径
     *     - META-INF/dubbo/internal/
     *     - META-INF/dubbo/
     *     - META-INF/services/
     * 对上面3个路径进行扫描 找到需要扩展的接口的配置文件
     * 轮询配置文件里面的所有键值对(key=名称 value=扩展点的扩展实现)
     * 解析过程中
     *     - 优先级1 扩展实现上首个注有Adaptive注解 缓存在cachedAdaptiveClass
     *         - 这个实现类就是type这个扩展点的自适应扩展实现适配类 getAdaptiveExtension()方法用到
     *     - 优先级2 扩展实现是包装类 全部缓存到cachedWrapperClasses
     *     - 优先级3 其他扩展实现都缓存到ExtensionLoader的cachedClasses
     *         - getDefaultExtension()用到
     *         - getExtension(...)用到
     *
     * 到优先级3的cachedClasses找指定名字的实现类
     */
    @SuppressWarnings("unchecked")
    private T createExtension(String name) {
        Class<?> clazz = this.getExtensionClasses().get(name); // 扫描实现类对象
        if (clazz == null) throw findException(name);
        try {
            T instance = (T) EXTENSION_INSTANCES.get(clazz);
            if (instance == null) {
                EXTENSION_INSTANCES.putIfAbsent(clazz, clazz.newInstance()); // 反射创建实现类的实例
                instance = (T) EXTENSION_INSTANCES.get(clazz);
            }
            this.injectExtension(instance); // 通过AdaptiveExtensionFactory检查实现的实例有没有setter注入扩展的场景
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
     * instance是type接口众多实现中最终选取的唯一作为Dubbo中的实现
     * 解决扩展实现的循环依赖场景问题
     *     - 某个扩展点的扩展实现已经获取 其中存在一个setter方法 设置的属性本身又是一个扩展实现
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
     * 指定路径
     *     - META-INF/dubbo/internal/
     *     - META-INF/dubbo/
     *     - META-INF/services/
     * 对上面3个路径进行扫描 找到需要扩展的接口的配置文件
     * 轮询配置文件里面的所有键值对(key=名称 value=扩展点的扩展实现)
     * 解析过程中
     *     - 如果扩展实现上注有Adaptive注解 就把这个实现缓存在cachedAdaptiveClass
     *         - 这个实现就是type这个扩展点的自适应扩展实现适配类 getAdaptiveExtension()方法用到
     *     - 扩展实现是包装类 全部缓存到cachedWrapperClasses
     *     - 其他扩展实现都缓存到ExtensionLoader的cachedClasses
     *         - getDefaultExtension()用到
     *         - getExtension(...)用到
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
     * 3个classpath上为type接口加载具体实现
     *     - 内置路径+接口全限定名
     *         - META-INF/dubbo/internal/xxx
     *         - META-INF/dubbo/xxx
     *         - META-INF/services/xxx
     *     - 文件内容格式是
     *         - 实现1名称=实现1全限定名
     *         - 实现2名称=实现2全限定名
     * 扫描接口type的注解SPI
     *     - 指定了name缓存起来作为默认实现名称
     *         - getDefaultExtension()使用
     * 扫描到接口type的所有指定实现 按照不同场景缓存起来
     *     - 第一个@Adaptive注解标识的实现类缓存到cachedAdaptiveClass
     *         - 给getAdaptiveExtension()创建自适应扩展适配用
     *     - 实现类是包装类的缓存到cachedWrapperClasses
     *     - 其余实现类缓存到hash表中
     *         - getDefaultExtension()使用
     *         - getExtension(...)根据名称查找实现类
     *
     */
    private Map<String, Class<?>> loadExtensionClasses() {
        /**
         * 接口type存在多实现 每个实现通过名字作区别
         * 将来外界通过getExtension(...)来获取type接口的实现
         *     - 在多实现中缓存中找需要的名称
         *     - 使用指派的默认实现
         */
        final SPI defaultAnnotation = type.getAnnotation(SPI.class);
        if (defaultAnnotation != null) {
            String value = defaultAnnotation.value(); // 指派为type接口默认实现的名称
            if ((value = value.trim()).length() > 0) {
                String[] names = NAME_SEPARATOR.split(value);
                if (names.length > 1)
                    throw new IllegalStateException("more than 1 default extension name on extension " + type.getName() + ": " + Arrays.toString(names));
                if (names.length == 1) this.cachedDefaultName = names[0]; // type接口默认实现的名称
            }
        }

        // 缓存着type接口的多实现(第三优先级的实现类)
        Map<String, Class<?>> extensionClasses = new HashMap<String, Class<?>>();
        /**
         * 3个classpath上为type接口加载具体实现
         *     - 内置路径+接口全限定名
         *         - META-INF/dubbo/internal/xxx
         *         - META-INF/dubbo/xxx
         *         - META-INF/services/xxx
         *     - 文件内容格式是
         *         - 实现1名称=实现1全限定名
         *         - 实现2名称=实现2全限定名
         */
        this.loadDirectory(extensionClasses, DUBBO_INTERNAL_DIRECTORY); // META-INF/dubbo/internal/
        this.loadDirectory(extensionClasses, DUBBO_DIRECTORY); // META-INF/dubbo/
        this.loadDirectory(extensionClasses, SERVICES_DIRECTORY); // META-INF/services/
        return extensionClasses;
    }

    private void loadDirectory(Map<String, Class<?>> extensionClasses, String dir) { // 扫描结果缓存到map中
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
                    this.loadResource(extensionClasses, classLoader, resourceURL); // extensionClasses用于缓存第三优先级的实现类对象
                }
            }
        } catch (Throwable t) {
            logger.error("Exception when load extension class(interface: " + type + ", description file: " + fileName + ").", t);
        }
    }

    private void loadResource(Map<String, Class<?>> extensionClasses, ClassLoader classLoader, java.net.URL resourceURL) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(resourceURL.openStream(), "utf-8"));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    final int ci = line.indexOf('#');
                    if (ci >= 0) line = line.substring(0, ci);
                    line = line.trim();
                    if (line.length() > 0) {
                        try {
                            String name = null;
                            int i = line.indexOf('=');
                            if (i > 0) {
                                name = line.substring(0, i).trim(); // 给扩展实现起的名字
                                line = line.substring(i + 1).trim(); // 扩展实现的类路径
                            }
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
     * 轮询SPI文件 遍历到的所有扩展实现中 按照3个优先级缓存
     *     - 优先级1 第一个标注@Adaptive注解的实现类缓存到cachedAdaptiveClass
     *     - 优先级2 包装类型缓存到cachedWrapperClasses
     *     - 其余放到hash表中
     */
    private void loadClass(Map<String, Class<?>> extensionClasses, java.net.URL resourceURL, Class<?> clazz, String name) throws NoSuchMethodException {
        if (!type.isAssignableFrom(clazz))
            throw new IllegalStateException("Error when load extension class(interface: " + type + ", class line: " + clazz.getName() + "), class " + clazz.getName() + "is not subtype of interface.");
        if (clazz.isAnnotationPresent(Adaptive.class)) { // 扩展实现上注有@Adaptive注解
            if (this.cachedAdaptiveClass == null) // 将标注了@Adaptive注解的实现缓存起来 作为第一优先级
                this.cachedAdaptiveClass = clazz;
            else if (!cachedAdaptiveClass.equals(clazz)) // 扩展实现中只能存在一个标注了@Adaptive注解的实现
                throw new IllegalStateException("More than 1 adaptive class found: " + cachedAdaptiveClass.getClass().getName() + ", " + clazz.getClass().getName());
        } else if (this.isWrapperClass(clazz)) { // 扩展实现是包装类
            Set<Class<?>> wrappers = this.cachedWrapperClasses;
            if (wrappers == null) {
                this.cachedWrapperClasses = new ConcurrentHashSet<Class<?>>();
                wrappers = this.cachedWrapperClasses;
            }
            wrappers.add(clazz);
        } else { // 其他的扩展实现
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
     * 自适应扩展实现类的优先级
     *     - 缓存着的cachedAdaptiveClass 也就是扫描Dubbo SPI路径上实现类第一个@Adaptive注解标识的类对象
     *     - 编码技术实现
     */
    private Class<?> getAdaptiveExtensionClass() {
        this.getExtensionClasses(); // 加载所有扩展接口指定的实现方式
        if (this.cachedAdaptiveClass != null) // 扫描加载落站实现的时候会将@Adaptive注解标识的实现缓存起来作为扩展适配的第一优先级
            return this.cachedAdaptiveClass;
        return this.cachedAdaptiveClass = this.createAdaptiveExtensionClass(); // 没有@Adaptive注解指定默认的扩展实现 使用编码技术生成
    }

    /**
     * code生成的方式对扩展点中被{@link Adaptive}修饰的方法进行编码 字节码技术生成类
     */
    private Class<?> createAdaptiveExtensionClass() {
        String code = this.createAdaptiveExtensionClassCode(); // 硬编码扩展接口的实现类(方法标注@Adaptive()注解的)
        ClassLoader classLoader = findClassLoader(); // 当前类加载器
        com.alibaba.dubbo.common.compiler.Compiler compiler = ExtensionLoader.getExtensionLoader(com.alibaba.dubbo.common.compiler.Compiler.class).getAdaptiveExtension();
        return compiler.compile(code, classLoader);
    }

    /**
     * <p>将扩展点{@link ExtensionLoader#type}中所有被{@link Adaptive}修饰的方法 生成编码</p>
     */
    private String createAdaptiveExtensionClassCode() {
        StringBuilder codeBuilder = new StringBuilder();
        Method[] methods = type.getMethods();
        boolean hasAdaptiveAnnotation = false;
        // 扩展点接口完全没有@Adaptive标注的方法 就不需要生成扩展类
        for (Method m : methods) {
            if (m.isAnnotationPresent(Adaptive.class)) {
                hasAdaptiveAnnotation = true;
                break;
            }
        }
        // no need to generate adaptive class since there's no adaptive method found.
        if (!hasAdaptiveAnnotation)
            throw new IllegalStateException("No adaptive method on extension " + type.getName() + ", refuse to create the adaptive class!");

        codeBuilder.append("package ").append(type.getPackage().getName()).append(";");
        codeBuilder.append("\nimport ").append(ExtensionLoader.class.getName()).append(";");
        codeBuilder.append("\npublic class ").append(type.getSimpleName()).append("$Adaptive").append(" implements ").append(type.getCanonicalName()).append(" {");

        for (Method method : methods) { // 轮询扩展点里面所有的方法
            Class<?> rt = method.getReturnType(); // 方法的返回值类型
            Class<?>[] pts = method.getParameterTypes(); // 方法的入参类型
            Class<?>[] ets = method.getExceptionTypes(); // 方法的异常类型

            Adaptive adaptiveAnnotation = method.getAnnotation(Adaptive.class);
            StringBuilder code = new StringBuilder(512);
            if (adaptiveAnnotation == null) {
                code.append("throw new UnsupportedOperationException(\"method ").append(method.toString()).append(" of interface ").append(type.getName()).append(" is not adaptive method!\");");
            } else {
                int urlTypeIndex = -1; // 标识方法参数列表中是否有URL类型的行参 或者虽然行参不是直接URL类型但是这个类型有getxxx的方法可以返回URL -1表示不存在
                for (int i = 0; i < pts.length; ++i) { // 轮询方法参数列表 记下URL类型的参数数组脚标 因为URL封装了所有的配置信息
                    if (pts[i].equals(URL.class)) {
                        urlTypeIndex = i;
                        break;
                    }
                }
                // found parameter in URL type
                if (urlTypeIndex != -1) { // 有参数类型为URL的方法
                    // Null Point check
                    String s = String.format("\nif (arg%d == null) throw new IllegalArgumentException(\"url == null\");", urlTypeIndex);
                    code.append(s);

                    s = String.format("\n%s url = arg%d;", URL.class.getName(), urlTypeIndex);
                    code.append(s);
                }
                // did not find parameter in URL type
                else { // 没有参数类型是URL类型的方法
                    String attribMethod = null;

                    // find URL getter method // 轮询所有的参数类型 但凡找到一个getxxx的getter方法的返回值是URL类型就标识出来 如果压根不存在这样的一个获取URL的getter方法 那就无法创建自适应扩展实现的编码 因为所有的配置信息都封装在URL中 没有URL就无法在运行时进行自适应扩展
                    LBL_PTS:
                    for (int i = 0; i < pts.length; ++i) {
                        Method[] ms = pts[i].getMethods(); // 行参也是个类型 这个类型的方法列表
                        for (Method m : ms) {
                            String name = m.getName();
                            if ((name.startsWith("get") || name.length() > 3)
                                    && Modifier.isPublic(m.getModifiers())
                                    && !Modifier.isStatic(m.getModifiers())
                                    && m.getParameterTypes().length == 0
                                    && m.getReturnType() == URL.class) {
                                urlTypeIndex = i;
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

                    s = String.format("%s url = arg%d.%s();", URL.class.getName(), urlTypeIndex, attribMethod);
                    code.append(s);
                }
                // @Adaptive的value()属性
                String[] value = adaptiveAnnotation.value();
                // value is not set, use the value generated from class name as the key
                if (value.length == 0) {
                    char[] charArray = this.type.getSimpleName().toCharArray(); // 扩展点的名称
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
                String getNameCode = null;
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
                code.append("\nString extName = ").append(getNameCode).append(";");
                // check extName == null?
                String s = String.format("\nif(extName == null) " + "throw new IllegalStateException(\"Fail to get extension(%s) name from url(\" + url.toString() + \") use keys(%s)\");", type.getName(), Arrays.toString(value));
                code.append(s);

                // 核心 根据dubbo SPI扩展方式获取扩展点的自适应扩展实现
                s = String.format("\n%s extension = (%<s)%s.getExtensionLoader(%s.class).getExtension(extName);", type.getName(), ExtensionLoader.class.getSimpleName(), type.getName());
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