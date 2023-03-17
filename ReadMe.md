# telepathy

这是一个反向代理的工具，基于nio，连接上后自带负载均衡。主要用来将有公网ip的服务器转发到无公网ip且与公网服务器不在一个内网的服务器上（后称内网服务器）。暂时只支持tcp连接的转发。

## 用法

公网服务器上直接new一个NewServerToServerConnector对象，公网服务器上就能开启转发功能。构造器参数分别是是外部连接端口号和内网服务器端口号。无参数形式构造器默认参数为内网服务器端口号10520和外部连接端口号10521。

~~~ java
//server:10520, client:10521
new NewServerToServerConnector();

//server:a, client:b
new NewServerToServerConnector(a, b);
~~~

内网服务器上也和公网服务器一样，创建ClientToClientConnector对象。参数有

1、公网服务器ip地址

2、公网服务器分发给内网服务器的端口号

3、内网服务器ip地址（一般为本机）

4、真实希望转发的端口号

5、最大连接数

~~~ java
//b为真实代理端口，c为最大连接数量
new ClientToClientConnector("公网服务器ip", 10520, "127.0.0.1", b, c);
~~~

## 仍需改进问题

1、压力测试时若连接数量远大于最大连接数时发生未知错误且未报错，一段时间后会使代理失效

2、代理效率低（也可能是压力测试端的并发性低）

## 先把坑挖下，看心情做吧

1、支持sockets5代理

2、外网和内网端整合成一个，发布可执行文件，通过配置文件配置转发类型。

3、支持正向代理（将ServerComponent和ClientComponent做Connector）

4、协议和数据加密


# telepathy
