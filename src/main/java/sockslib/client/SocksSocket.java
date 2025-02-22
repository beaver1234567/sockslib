/*
 * Copyright 2015-2025 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package sockslib.client;

import sockslib.common.SocksException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sockslib.utils.InetUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The class <code>SocksSocket</code> is proxy class that help developers use {@link SocksProxy} as
 * same as a java.net.Socket.<br>
 * For example:<br>
 * <pre>
 * SocksProxy proxy = new Socks5(new InetSocketAddress(&quot;127.0.0.1&quot;, 1080));
 * // Setting proxy...
 * Socket socket = new SocksSocket(proxy, new InetSocketAddress(&quot;whois.internic.net&quot;,
 * 43));
 * InputStream inputStream = socket.getInputStream();
 * OutputStream outStream = socket.getOutputStream();
 * // Just use the socket as normal java.net.Socket now.
 * </pre>
 *
 * @author Youchao Feng
 * @version 1.0
 * @date Mar 18, 2015 5:02:31 PM
 */
public class SocksSocket extends Socket {

  protected static final Logger logger = LoggerFactory.getLogger(SocksSocket.class);

  private SocksProxy proxy;

  private String remoteServerHost;

  private int remoteServerPort;

  /**
   * Socket that will connect to SOCKS server.
   */
  private Socket proxySocket;

  private final boolean onlyIpV4;

  /**
   * Create a socket and connect SOCKS Server.
   *
   * @param proxy            Socks proxy.
   * @param remoteServerHost Remote sever host.
   * @param remoteServerPort Remote server port.
   * @throws SocksException If any errors about SOCKS protocol occurred.
   * @throws IOException    If any IO errors occurred.
   */
  public SocksSocket(SocksProxy proxy, String remoteServerHost, int remoteServerPort) throws
      SocksException, IOException {
    this(proxy, remoteServerHost, remoteServerPort, false);
  }

  public SocksSocket(SocksProxy proxy, InetAddress address, int port) throws SocksException,
          IOException {
    this(proxy, new InetSocketAddress(address, port), false);
  }

  public SocksSocket(SocksProxy proxy, SocketAddress socketAddress) throws SocksException,
          IOException {
    this(proxy, socketAddress, false);
  }

  public SocksSocket(SocksProxy proxy) throws IOException {
    this(proxy, false);
  }

  public SocksSocket(SocksProxy proxy, Socket proxySocket) {
    this(proxy, proxySocket, false);
  }

  public SocksSocket(SocksProxy proxy, String remoteServerHost, int remoteServerPort, boolean onlyIpV4) throws
          SocksException, IOException {
    this.onlyIpV4 = onlyIpV4;

    if (onlyIpV4) {
      InetAddress address = InetUtils.resolve4(remoteServerHost);
      remoteServerHost = address.getHostAddress();
    }

    this.proxy = checkNotNull(proxy, "Argument [proxy] may not be null").copy();
    this.proxy.setProxySocket(proxySocket);
    this.remoteServerHost =
            checkNotNull(remoteServerHost, "Argument [remoteServerHost] may not be null");
    this.remoteServerPort = remoteServerPort;
    this.proxy.buildConnection();
    proxySocket = this.getProxySocket();
    initProxyChain();
    this.proxy.requestConnect(remoteServerHost, remoteServerPort);
  }

  /**
   * Same as {@link #SocksSocket(SocksProxy, String, int)}
   *
   * @param proxy   Socks proxy.
   * @param address Remote server's IP address.
   * @param port    Remote server's port.
   * @throws SocksException If any error about SOCKS protocol occurs.
   * @throws IOException    If I/O error occurs.
   */
  public SocksSocket(SocksProxy proxy, InetAddress address, int port, boolean onlyIpV4) throws SocksException,
      IOException {
    this(proxy, new InetSocketAddress(address, port), onlyIpV4);
  }

  public SocksSocket(SocksProxy proxy, SocketAddress socketAddress, boolean onlyIpV4) throws SocksException,
      IOException {
    this.onlyIpV4 = onlyIpV4;

    if (onlyIpV4) {
      InetSocketAddress isa = (InetSocketAddress) socketAddress;
      socketAddress = new InetSocketAddress(InetUtils.resolve4(isa.getAddress()), isa.getPort());
    }

    checkNotNull(proxy, "Argument [proxy] may not be null");
    checkNotNull(socketAddress, "Argument [socketAddress] may not be null");
    checkArgument(socketAddress instanceof InetSocketAddress, "Unsupported address type");
    InetSocketAddress address = (InetSocketAddress) socketAddress;
    this.proxy = proxy.copy();
    this.remoteServerHost = address.getHostString();
    this.remoteServerPort = address.getPort();
    this.proxy.buildConnection();
    proxySocket = this.getProxySocket();
    initProxyChain();
    this.proxy.requestConnect(address.getAddress(), address.getPort());

  }

  /**
   * Creates an unconnected socket.
   *
   * @param proxy SOCKS proxy.
   * @throws IOException If an I/O error occurred.
   */
  public SocksSocket(SocksProxy proxy, boolean onlyIpV4) throws IOException {
    this(proxy, proxy.createProxySocket(), onlyIpV4);
  }

  /**
   * Creates a SocksSocket instance with a {@link SocksProxy} and a
   *
   * @param proxy       SOCKS proxy.
   * @param proxySocket a unconnected socket. it will connect SOCKS server later.
   */
  public SocksSocket(SocksProxy proxy, Socket proxySocket, boolean onlyIpV4) {
    checkNotNull(proxy, "Argument [proxy] may not be null");
    checkNotNull(proxySocket, "Argument [proxySocket] may not be null");
    checkArgument(!proxySocket.isConnected(), "Proxy socket should be unconnected");
    this.onlyIpV4 = onlyIpV4;
    this.proxySocket = proxySocket;
    this.proxy = proxy.copy();
    this.proxy.setProxySocket(proxySocket);
  }

  /**
   * Initialize proxy chain.
   *
   * @throws SocketException If a SOCKS protocol error occurred.
   * @throws IOException     If an I/O error occurred.
   */
  private void initProxyChain() throws SocketException, IOException {
    List<SocksProxy> proxyChain = new ArrayList<SocksProxy>();
    SocksProxy temp = proxy;
    while (temp.getChainProxy() != null) {
      temp.getChainProxy().setProxySocket(proxySocket);
      proxyChain.add(temp.getChainProxy());
      temp = temp.getChainProxy();
    }
    logger.debug("Proxy chain has:{} proxy", proxyChain.size());
    if (proxyChain.size() > 0) {
      SocksProxy pre = proxy;
      for (int i = 0; i < proxyChain.size(); i++) {
        SocksProxy chain = proxyChain.get(i);
        pre.requestConnect(chain.getInetAddress(), chain.getPort());
        proxy.getChainProxy().buildConnection();
        pre = chain;
      }
    }

  }

  /**
   * Connect to SOCKS Server and server will proxy remote server.
   *
   * @param host Remote server's host.
   * @param port Remote server's port.
   * @throws SocksException If any error about SOCKS protocol occurs.
   * @throws IOException    If I/O error occurs.
   */
  public void connect(String host, int port) throws SocksException, IOException {
    if (onlyIpV4) {
      InetAddress address = InetUtils.resolve4(host);
      host = address.getHostAddress();
    }

    this.remoteServerHost = checkNotNull(host, "Argument [host] may not be null");
    this.remoteServerPort = checkNotNull(port, "Argument [port] may not be null");
    proxy.buildConnection();
    initProxyChain();
    proxy.requestConnect(remoteServerHost, remoteServerPort);
  }


  @Override
  public void connect(SocketAddress endpoint) throws SocksException, IOException {
    if (onlyIpV4) {
      InetSocketAddress isa = (InetSocketAddress) endpoint;
      endpoint = new InetSocketAddress(InetUtils.resolve4(isa.getAddress()), isa.getPort());
    }
    connect(endpoint, 0);
  }


  @Override
  public void connect(SocketAddress endpoint, int timeout) throws SocksException, IOException {
    if (!(endpoint instanceof InetSocketAddress)) {
      throw new IllegalArgumentException("Unsupported address type");
    }

    if (onlyIpV4) {
      InetSocketAddress isa = (InetSocketAddress) endpoint;
      endpoint = new InetSocketAddress(InetUtils.resolve4(isa.getAddress()), isa.getPort());
    }

    remoteServerHost = ((InetSocketAddress) endpoint).getHostName();
    remoteServerPort = ((InetSocketAddress) endpoint).getPort();

    getProxySocket().setSoTimeout(timeout);
    proxy.buildConnection();
    initProxyChain();
    proxy.requestConnect(endpoint);
  }

  @Override
  public InputStream getInputStream() throws IOException {
    final Socket socket = getProxySocket();
    if (socket != null) {
      return socket.getInputStream();
    }
    return null;
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    final Socket socket = getProxySocket();
    if (socket != null) {
      return socket.getOutputStream();
    }
    return null;
  }

  @Override
  public void bind(SocketAddress bindpoint) throws IOException {
    final Socket socket = getProxySocket();
    if (socket != null) {
      socket.bind(bindpoint);
    }
  }

  @Override
  public InetAddress getInetAddress() {
    try {
      return InetAddress.getByName(remoteServerHost);
    } catch (UnknownHostException e) {
    }
    return null;
  }

  @Override
  public InetAddress getLocalAddress() {
    final Socket socket = getProxySocket();
    if (socket != null) {
      return socket.getLocalAddress();
    }
    return null;
  }

  @Override
  public int getPort() {
    return remoteServerPort;
  }

  @Override
  public int getLocalPort() {
    final Socket socket = getProxySocket();
    if (socket != null) {
      return socket.getLocalPort();
    }
    return 0;
  }

  @Override
  public SocketAddress getRemoteSocketAddress() {
    final Socket socket = getProxySocket();
    if (socket != null) {
      return socket.getRemoteSocketAddress();
    }
    return null;
  }

  @Override
  public SocketAddress getLocalSocketAddress() {
    final Socket socket = getProxySocket();
    if (socket != null) {
      return socket.getLocalSocketAddress();
    }
    return null;
  }

  @Override
  public SocketChannel getChannel() {
    final Socket socket = getProxySocket();
    if (socket != null) {
      return socket.getChannel();
    }
    return null;
  }

  @Override
  public boolean getTcpNoDelay() throws SocketException {
    final Socket socket = getProxySocket();
    if (socket != null) {
      return socket.getTcpNoDelay();
    }
    return false;
  }

  @Override
  public void setTcpNoDelay(boolean on) throws SocketException {
    final Socket socket = getProxySocket();
    if (socket != null) {
      socket.setTcpNoDelay(on);
    }
  }

  @Override
  public void setSoLinger(boolean on, int linger) throws SocketException {
    final Socket socket = getProxySocket();
    if (socket != null) {
      socket.setSoLinger(on, linger);
    }
  }

  @Override
  public int getSoLinger() throws SocketException {
    final Socket socket = getProxySocket();
    if (socket != null) {
      return socket.getSoLinger();
    }
    return 0;
  }

  @Override
  public void sendUrgentData(int data) throws IOException {
    final Socket socket = getProxySocket();
    if (socket != null) {
      socket.sendUrgentData(data);
    }
  }

  @Override
  public boolean getOOBInline() throws SocketException {
    final Socket socket = getProxySocket();
    if (socket != null) {
      return socket.getOOBInline();
    }
    return false;
  }

  @Override
  public void setOOBInline(boolean on) throws SocketException {
    final Socket socket = getProxySocket();
    if (socket != null) {
      socket.setOOBInline(on);
    }
  }

  @Override
  public synchronized int getSoTimeout() throws SocketException {
    final Socket socket = getProxySocket();
    if (socket != null) {
      return socket.getSoTimeout();
    }
    return 0;
  }

  @Override
  public synchronized void setSoTimeout(int timeout) throws SocketException {
    final Socket socket = getProxySocket();
    if (socket != null) {
      socket.setSoTimeout(timeout);
    }
  }

  @Override
  public synchronized int getSendBufferSize() throws SocketException {
    final Socket socket = getProxySocket();
    if (socket != null) {
      return socket.getSendBufferSize();
    }
    return 0;
  }

  @Override
  public synchronized void setSendBufferSize(int size) throws SocketException {
    final Socket socket = getProxySocket();
    if (socket != null) {
      socket.setSendBufferSize(size);
    }
  }

  @Override
  public synchronized int getReceiveBufferSize() throws SocketException {
    final Socket socket = getProxySocket();
    if (socket != null) {
      return socket.getReceiveBufferSize();
    }
    return 0;
  }

  @Override
  public synchronized void setReceiveBufferSize(int size) throws SocketException {
    final Socket socket = getProxySocket();
    if (socket != null) {
      socket.setReceiveBufferSize(size);
    }
  }

  @Override
  public boolean getKeepAlive() throws SocketException {
    final Socket socket = getProxySocket();
    if (socket != null) {
      return socket.getKeepAlive();
    }
    return false;
  }

  @Override
  public void setKeepAlive(boolean on) throws SocketException {
    final Socket socket = getProxySocket();
    if (socket != null) {
      socket.setKeepAlive(on);
    }
  }

  @Override
  public int getTrafficClass() throws SocketException {
    final Socket socket = getProxySocket();
    if (socket != null) {
      return socket.getTrafficClass();
    }
    return 0;
  }

  @Override
  public void setTrafficClass(int tc) throws SocketException {
    final Socket socket = getProxySocket();
    if (socket != null) {
      socket.setTrafficClass(tc);
    }
  }

  @Override
  public boolean getReuseAddress() throws SocketException {
    final Socket socket = getProxySocket();
    if (socket != null) {
      return socket.getReuseAddress();
    }
    return false;
  }

  @Override
  public void setReuseAddress(boolean on) throws SocketException {
    final Socket socket = getProxySocket();
    if (socket != null) {
      socket.setReuseAddress(on);
    }
  }

  @Override
  public synchronized void close() throws IOException {
    final Socket socket = getProxySocket();
    if (socket != null) {
      socket.close();
    }
    proxy.setProxySocket(null);
  }

  @Override
  public void shutdownInput() throws IOException {
    final Socket socket = getProxySocket();
    if (socket == null) return;
    socket.shutdownInput();
  }

  @Override
  public void shutdownOutput() throws IOException {
    final Socket socket = getProxySocket();
    if (socket == null) return;
    socket.shutdownOutput();
  }

  @Override
  public boolean isConnected() {
    final Socket socket = getProxySocket();
    if (socket == null) return false;
    return socket.isConnected();
  }

  @Override
  public boolean isBound() {
    final Socket socket = getProxySocket();
    if (socket == null) return false;
    return socket.isBound();
  }

  @Override
  public boolean isClosed() {
    final Socket socket = getProxySocket();
    if (socket == null) return true;
    return socket.isClosed();
  }

  @Override
  public boolean isInputShutdown() {
    final Socket socket = getProxySocket();
    if (socket == null) return true;
    return socket.isInputShutdown();
  }

  @Override
  public boolean isOutputShutdown() {
    final Socket socket = getProxySocket();
    if (socket == null) return true;
    return socket.isOutputShutdown();
  }

  @Override
  public void setPerformancePreferences(int connectionTime, int latency, int bandwidth) {
    final Socket socket = getProxySocket();
    if (socket != null) {
      socket.setPerformancePreferences(connectionTime, latency, bandwidth);
    }
  }

  public Socket getProxySocket() {
    if (proxy != null) {
      return proxy.getProxySocket();
    }
    return null;
  }

}
