package com.kyroxova.continuumlib.cir;

import java.util.Objects;

public final class CirNetworkPacketDefinition implements CirSemanticNode {

    private final String channelName;
    private final String packetClassName;
    private final int packetId;
    private final String direction;
    private final String encoder;
    private final String decoder;
    private final String handler;

    public CirNetworkPacketDefinition(String channelName, String packetClassName, int packetId,
                                      String direction, String encoder, String decoder, String handler) {
        this.channelName = Objects.requireNonNull(channelName, "channelName cannot be null");
        this.packetClassName = Objects.requireNonNull(packetClassName, "packetClassName cannot be null");
        this.packetId = packetId;
        this.direction = direction != null ? direction : "PLAY_TO_SERVER";
        this.encoder = encoder != null ? encoder : (packetClassName + "::encode");
        this.decoder = decoder != null ? decoder : (packetClassName + "::decode");
        this.handler = handler != null ? handler : (packetClassName + "::handle");
    }

    @Override
    public String getSemanticType() {
        return "REGISTER_NETWORK_PACKET";
    }

    public String getChannelName() {
        return channelName;
    }

    public String getPacketClassName() {
        return packetClassName;
    }

    public int getPacketId() {
        return packetId;
    }

    public String getDirection() {
        return direction;
    }

    public String getEncoder() {
        return encoder;
    }

    public String getDecoder() {
        return decoder;
    }

    public String getHandler() {
        return handler;
    }
}
