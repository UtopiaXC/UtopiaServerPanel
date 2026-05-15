package com.utopiaxc.utopiaserverpanel.web;

import com.utopiaxc.utopiaserverpanel.web.handler.HttpRequestDispatcher;
import com.utopiaxc.utopiaserverpanel.web.handler.WebSocketFrameHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

/**
 * Configures the Netty channel pipeline for each incoming connection.
 * Uses standard Netty HTTP/WebSocket codecs.
 */
public class WebServerInitializer extends ChannelInitializer<SocketChannel> {

    @Override
    protected void initChannel(SocketChannel ch) {
        ChannelPipeline p = ch.pipeline();
        p.addLast("http-codec", new HttpServerCodec());
        p.addLast("http-aggregator", new HttpObjectAggregator(65536));
        p.addLast("ws-protocol", new WebSocketServerProtocolHandler("/ws", null, true, 65536));
        p.addLast("ws-handler", new WebSocketFrameHandler());
        p.addLast("http-dispatcher", new HttpRequestDispatcher());
    }
}
