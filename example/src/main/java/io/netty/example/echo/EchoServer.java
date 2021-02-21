/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.example.echo;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.SelfSignedCertificate;

/**
 * Echoes back any received data from a client.
 */
public final class EchoServer {

    static final boolean SSL = System.getProperty("ssl") != null;
    static final int PORT = Integer.parseInt(System.getProperty("port", "8007"));

    public static void main(String[] args) throws Exception {
        // Configure SSL.
        final SslContext sslCtx;
        if (SSL) {
            SelfSignedCertificate ssc = new SelfSignedCertificate();
            sslCtx = SslContextBuilder.forServer(ssc.certificate(), ssc.privateKey()).build();
        } else {
            sslCtx = null;
        }
        /*
            fixme jiangkui 我们从这个方法来看源码

            EventLoopGroup：本质跟 java 线程池差不多，有执行器、有队列、有拒绝策略
            EventLoop：本质类似java 的一个线程，不过是自己一直在轮询事件任务，并且可以处理新提交的任务。
            Pipeline：是存放编码器、解码器、业务处理器的一个双向链表。
            Channel：本质包了 Java Nio 的 Channel，并提供 Pipeline 模型，方便编码操作。

            ServerBootstrap：
                1. 反射创建了一个 NioServerSocketChannel，内部包含 JDK 的 Channel
                2. init 初始化 NioServerSocketChannel，设置 option、attr等
                3. 通过 ServerBootstrap 的 bossGroup 注册 NioServerSocketChannel
                4. 返回异步执行的占位符即 Future

            最终会执行到 io.netty.channel.nio.NioEventLoop.run 方法，无限循环 io.netty.channel.SelectStrategy 的事件
         */

        // Configure the server.
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        final EchoServerHandler serverHandler = new EchoServerHandler();
        try {
            ServerBootstrap b = new ServerBootstrap();

            // parentGroup：bossGroup
            // childGroup：workerGroup
            b.group(bossGroup, workerGroup)
             // 创建对应的 channel 对象
             .channel(NioServerSocketChannel.class)
             // 传入TCP相关参数，放在 LinkedHashMap 内
             .option(ChannelOption.SO_BACKLOG, 100)
             // 属于 ServerSocketChannel
             .handler(new LoggingHandler(LogLevel.INFO))
             // 属于 SocketChannel 使用，即：workerGroup
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 public void initChannel(SocketChannel ch) throws Exception {
                     ChannelPipeline p = ch.pipeline();
                     if (sslCtx != null) {
                         p.addLast(sslCtx.newHandler(ch.alloc()));
                     }
                     //p.addLast(new LoggingHandler(LogLevel.INFO));
                     p.addLast(serverHandler);
                 }
             });

            // Start the server.
            ChannelFuture f = b.bind(PORT).sync();

            // Wait until the server socket is closed.
            f.channel().closeFuture().sync();
        } finally {
            // Shut down all event loops to terminate all threads.
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
