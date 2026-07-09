package art.arcane.iris.client;

import art.arcane.iris.spi.protocol.IrisMessage;
import art.arcane.iris.spi.protocol.IrisMessageCodec;
import art.arcane.iris.spi.protocol.IrisProtocol;

public final class IrisClientSession {
    private static final long CLIENT_CAPABILITIES = IrisProtocol.CAPABILITY_PREGEN | IrisProtocol.CAPABILITY_VISION | IrisProtocol.CAPABILITY_CURSOR | IrisProtocol.CAPABILITY_STUDIO;

    private volatile State state;
    private volatile long serverCapabilities;
    private volatile boolean irisActive;
    private volatile String serverBrand;
    private volatile ClientPacketSink sink;

    public IrisClientSession() {
        this.state = State.IDLE;
        this.serverCapabilities = 0L;
        this.irisActive = false;
        this.serverBrand = "";
        this.sink = null;
    }

    public void bind(ClientPacketSink boundSink) {
        this.sink = boundSink;
    }

    public State state() {
        return state;
    }

    public boolean isReady() {
        return state == State.READY;
    }

    public boolean irisActive() {
        return irisActive;
    }

    public long serverCapabilities() {
        return serverCapabilities;
    }

    public String serverBrand() {
        return serverBrand;
    }

    public void sendHello() {
        ClientPacketSink activeSink = sink;
        if (activeSink == null) {
            return;
        }
        byte[] frame = IrisMessageCodec.encode(new IrisMessage.ClientHello(IrisProtocol.PROTOCOL_VERSION, CLIENT_CAPABILITIES));
        state = State.AWAITING_HELLO;
        activeSink.send(frame);
    }

    public void onServerHello(IrisMessage.ServerHello hello) {
        this.serverCapabilities = hello.capabilities();
        this.irisActive = hello.irisActive();
        this.serverBrand = hello.serverBrand();
        this.state = State.READY;
    }

    public void reset() {
        this.state = State.IDLE;
        this.serverCapabilities = 0L;
        this.irisActive = false;
        this.serverBrand = "";
    }

    public enum State {
        IDLE,
        AWAITING_HELLO,
        READY
    }
}
