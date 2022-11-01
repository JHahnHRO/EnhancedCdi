package io.github.jhahn.enhancedcdi.messaging.impl.producers;

import com.rabbitmq.client.*;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Map;
import java.util.Optional;

abstract class ForwardingConnection implements Connection {
    public abstract Connection getDelegate();


    @Override
    public InetAddress getAddress() {
        return getDelegate().getAddress();
    }

    @Override
    public int getPort() {
        return getDelegate().getPort();
    }

    @Override
    public int getChannelMax() {
        return getDelegate().getChannelMax();
    }

    @Override
    public int getFrameMax() {
        return getDelegate().getFrameMax();
    }

    @Override
    public int getHeartbeat() {
        return getDelegate().getHeartbeat();
    }

    @Override
    public Map<String, Object> getClientProperties() {
        return getDelegate().getClientProperties();
    }

    @Override
    public String getClientProvidedName() {
        return getDelegate().getClientProvidedName();
    }

    @Override
    public Map<String, Object> getServerProperties() {
        return getDelegate().getServerProperties();
    }

    @Override
    public Channel createChannel() throws IOException {
        return getDelegate().createChannel();
    }

    @Override
    public Channel createChannel(int channelNumber) throws IOException {
        return getDelegate().createChannel(channelNumber);
    }

    @Override
    public Optional<Channel> openChannel() throws IOException {
        return getDelegate().openChannel();
    }

    @Override
    public Optional<Channel> openChannel(int channelNumber) throws IOException {
        return getDelegate().openChannel(channelNumber);
    }

    @Override
    public void close() throws IOException {
        getDelegate().close();
    }

    @Override
    public void close(int closeCode, String closeMessage) throws IOException {
        getDelegate().close(closeCode, closeMessage);
    }

    @Override
    public void close(int timeout) throws IOException {
        getDelegate().close(timeout);
    }

    @Override
    public void close(int closeCode, String closeMessage, int timeout) throws IOException {
        getDelegate().close(closeCode, closeMessage, timeout);
    }

    @Override
    public void abort() {
        getDelegate().abort();
    }

    @Override
    public void abort(int closeCode, String closeMessage) {
        getDelegate().abort(closeCode, closeMessage);
    }

    @Override
    public void abort(int timeout) {
        getDelegate().abort(timeout);
    }

    @Override
    public void abort(int closeCode, String closeMessage, int timeout) {
        getDelegate().abort(closeCode, closeMessage, timeout);
    }

    @Override
    public void addBlockedListener(BlockedListener listener) {
        getDelegate().addBlockedListener(listener);
    }

    @Override
    public BlockedListener addBlockedListener(BlockedCallback blockedCallback, UnblockedCallback unblockedCallback) {
        return getDelegate().addBlockedListener(blockedCallback, unblockedCallback);
    }

    @Override
    public boolean removeBlockedListener(BlockedListener listener) {
        return getDelegate().removeBlockedListener(listener);
    }

    @Override
    public void clearBlockedListeners() {
        getDelegate().clearBlockedListeners();
    }

    @Override
    public ExceptionHandler getExceptionHandler() {
        return getDelegate().getExceptionHandler();
    }

    @Override
    public String getId() {
        return getDelegate().getId();
    }

    @Override
    public void setId(String id) {
        getDelegate().setId(id);
    }

    @Override
    public void addShutdownListener(ShutdownListener listener) {
        getDelegate().addShutdownListener(listener);
    }

    @Override
    public void removeShutdownListener(ShutdownListener listener) {
        getDelegate().removeShutdownListener(listener);
    }

    @Override
    public ShutdownSignalException getCloseReason() {
        return getDelegate().getCloseReason();
    }

    @Override
    public void notifyListeners() {
        getDelegate().notifyListeners();
    }

    @Override
    public boolean isOpen() {
        return getDelegate().isOpen();
    }
}
