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
        p.addLast("http-aggregator", new HttpObjectAggregator(524288));
        // Use 6-arg constructor (not WebSocketServerProtocolConfig) for Netty 4.1.9+ / 4.1.25+ dev compatibility (1.12.2 / 1.16.5)
        p.addLast("ws-protocol", new WebSocketServerProtocolHandler("/ws", null, true, 65536, false, true));
        p.addLast("ws-handler", new WebSocketFrameHandler());
        p.addLast("http-dispatcher", new HttpRequestDispatcher());
    }
}
