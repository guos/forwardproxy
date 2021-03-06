/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.arloor.forwardproxy;

import com.arloor.forwardproxy.ssl.SslContextFactory;
import com.arloor.forwardproxy.vo.Config;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.ssl.SslContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

/**
 * An HTTP server that sends back the content of the received HTTP request
 * in a pretty plaintext form.
 */
public final class HttpProxyServer {

    private static final Logger log = LoggerFactory.getLogger(HttpProxyServer.class);


    public static void main(String[] args) throws Exception {
        String propertiesPath = null;
        if (args.length == 2 && args[0].equals("-c")) {
            propertiesPath = args[1];
        }
        Properties properties = new Properties();
        if (propertiesPath != null) {
            properties.load(new FileReader(new File(propertiesPath)));
        } else {
            properties.load(HttpProxyServer.class.getClassLoader().getResourceAsStream("proxy.properties"));
        }

        Config config = Config.parse(properties);
        Config.Ssl ssl = config.ssl();
        Config.Http http = config.http();

        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();


        try {
            if(ssl!=null &&http!=null){
                new Thread(()->{
                   startSSl(bossGroup,workerGroup,ssl);
                }).start();
                startHttp(bossGroup,workerGroup,http);
            }else if(ssl!=null){
                startSSl(bossGroup,workerGroup,ssl);
            }else if(http!=null){
                startHttp(bossGroup,workerGroup,http);
            }
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }


    private static void startHttp(EventLoopGroup bossGroup,EventLoopGroup workerGroup,Config.Http http){
        try {
            // Configure the server.
            ServerBootstrap b = new ServerBootstrap();
            b.option(ChannelOption.SO_BACKLOG, 1024);
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new HttpProxyServerInitializer(http));

            Channel httpChannel = b.bind(http.getPort()).sync().channel();
            log.info("http proxy@ port=" + http.getPort() + " auth=" + http.getAuth() + " reverseBit=" + http.getReverseBit());
            httpChannel.closeFuture().sync();
        } catch (Exception e) {
            log.error("??", e);
        }
    }

    private static void startSSl(EventLoopGroup bossGroup,EventLoopGroup workerGroup,Config.Ssl ssl){
        try {
            // Configure the server.
            ServerBootstrap b = new ServerBootstrap();
            b.option(ChannelOption.SO_BACKLOG, 1024);
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new HttpsProxyServerInitializer(ssl));

            Channel sslChannel = b.bind(ssl.getPort()).sync().channel();
            log.info("https proxy@ port=" + ssl.getPort() + " auth=" + ssl.getAuth());
            sslChannel.closeFuture().sync();
        } catch (Exception e) {
            log.error("??", e);
        }
    }
}
