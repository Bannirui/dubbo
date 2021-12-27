### DUBBO STUDY

> 工程结构
* dubbo-registry
  * 注册中心模块
  * 基于注册中心进行下发地址的集群方式 对各种注册中心的抽象
  * dubbo-registry-api
    * 抽象了注册中心的注册和发现
* dubbo-cluster
  * 集群模块
  * 将多个服务提供方伪装成一个提供方
    * 负载均衡
    * 容错
    * 路由
  * 解决出错情况采用的策略 封装了多种策略的实现方法 把多个invoker伪装成一个invoker 并且在伪装的过程中加入了容多的逻辑 失败了就重试下一个
  * configurator
    * 配置包 dubbo的基本设计原则就是采用URL作为配置信息的统一格式 所有拓展点都是通过传递URL携带的配置信息
  * directory
    * 代表了多个invoker
    * invoker是provider的一个调用service的抽象
    * invoker封装了provider地址以及service接口信息
  * loadbalance
    * 封装了负载均衡的实现 利用负载均衡从多个invoker中选出具体的一个invoker用于此次的调用 如果调用失败了则需要重新选择
  * merger
    * 合并返回结果
  * router
    * 路由规则
    * 路由规则决定了一次dubbo服务调用的目标服务器
    * 路由规则分为2种
      * 条件路由规则
      * 脚本路由规则
  * support
    * 封装了各类invoker和cluster
* dubbo-common
  * URL贯穿了整个项目
* dubbo-config
  * 配置模块
  * 4中配置方式
    * XML配置
    * 属性配置
    * API配置
    * 注解配置
  * dubbo-config-api
    * API配置
    * 属性配置
  * dubbo-config-spring
    * XML配置
    * 注解配置
* dubbo-rpc
  * 远程调用模块
  * 抽象各种协议以及动态代理 只包含一对一的调用 不关心集群的管理
  * 这个模块依赖dubbo-remoting
  * dubbo-rpc-api
    * 抽象了动态代理和各类协议 实现一对一的调用
* dubbo-remoting
  * 远程通信模块 dubbo的协议实现 除了用RMI协议规则不要依赖这个包之外 其他的协议实现都需要依赖
