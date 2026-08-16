package com.docflow.config;

import com.docflow.websocket.DocWebSocketHandler;
import com.docflow.websocket.JwtHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private DocWebSocketHandler docWebSocketHandler;

    @Autowired
    private JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(docWebSocketHandler, "/ws/doc/{docId}")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins("*");  // 生产环境应该限制来源
    }
}
