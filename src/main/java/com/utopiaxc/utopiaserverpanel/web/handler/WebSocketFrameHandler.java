package com.utopiaxc.utopiaserverpanel.web.handler;

import com.utopiaxc.utopiaserverpanel.UtopiaServerPanel;
import com.utopiaxc.utopiaserverpanel.web.controller.WebSocketController;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.concurrent.GlobalEventExecutor;

/**
 * Handles WebSocket text frames after handshake completion.
 * Maintains a ChannelGroup for broadcasting.
 */
public class WebSocketFrameHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    public static ChannelGroup getChannels() { return channels; }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            channels.add(ctx.channel());
            WebSocketController.onConnect(ctx);
        }
        super.userEventTriggered(ctx, evt);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        WebSocketController.onMessage(ctx, frame.text());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        channels.remove(ctx.channel());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof java.io.IOException) {
            UtopiaServerPanel.LOGGER.debug("WS client disconnected: {}", cause.getMessage());
        } else {
            UtopiaServerPanel.LOGGER.error("WebSocket handler error", cause);
        }
        ctx.close();
    }
}
