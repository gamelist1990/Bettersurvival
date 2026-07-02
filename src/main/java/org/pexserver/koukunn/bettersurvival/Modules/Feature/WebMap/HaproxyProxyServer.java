package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * HAProxy PROXY protocol v2 を受け入れる TCP リレー。
 *
 * 公開ポートで待ち受けて PROXY v2 ヘッダーから実クライアント IP を取り出し、
 * ループバックにバインドした内部 HTTP サーバーへそのまま転送する。
 * 内部接続のローカルポート番号 -> 実クライアント IP の対応表を持つため、
 * HTTP ハンドラー側は {@link #lookupClientIp(int)} で実 IP を解決できる
 * (レート制限・表示回数クールダウンに使用)。
 *
 * HAProxy 側は backend の server 行に send-proxy-v2 を指定する。
 */
public class HaproxyProxyServer {

    /** PROXY protocol v2 の署名 (12 bytes) */
    private static final byte[] SIGNATURE = {
            0x0D, 0x0A, 0x0D, 0x0A, 0x00, 0x0D, 0x0A, 0x51, 0x55, 0x49, 0x54, 0x0A
    };
    private static final int HEADER_LENGTH = 16;

    private final Logger logger;
    private final Map<Integer, String> clientIpByLocalPort = new ConcurrentHashMap<>();
    private final AtomicInteger threadCounter = new AtomicInteger();

    private volatile boolean running;
    private ServerSocket serverSocket;
    private ExecutorService pool;
    private int backendPort;

    public HaproxyProxyServer(Logger logger) {
        this.logger = logger;
    }

    public synchronized void start(String host, int port, int backendPort) throws IOException {
        stop();
        this.backendPort = backendPort;
        this.serverSocket = new ServerSocket();
        this.serverSocket.setReuseAddress(true);
        this.serverSocket.bind(new InetSocketAddress(host, port));
        this.pool = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "BS-HaproxyRelay-" + threadCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        this.running = true;
        pool.execute(this::acceptLoop);
        logger.info("[WebService] HAProxy PROXY protocol v2 リレーを起動しました (" + host + ":" + port
                + " -> 127.0.0.1:" + backendPort + ")");
    }

    public synchronized void stop() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
            serverSocket = null;
        }
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
        clientIpByLocalPort.clear();
    }

    public boolean isRunning() {
        return running;
    }

    /** 内部 HTTP サーバーから見たリモートポートを実クライアント IP へ解決する。 */
    public String lookupClientIp(int backendRemotePort) {
        return clientIpByLocalPort.get(backendRemotePort);
    }

    private void acceptLoop() {
        ServerSocket socket = serverSocket;
        while (running && socket != null && !socket.isClosed()) {
            try {
                Socket client = socket.accept();
                pool.execute(() -> handleConnection(client));
            } catch (IOException error) {
                if (running) {
                    logger.log(Level.WARNING, "[WebService] HAProxyリレー accept 失敗: " + error.getMessage());
                }
                return;
            }
        }
    }

    private void handleConnection(Socket client) {
        Socket backend = null;
        int localPort = -1;
        try {
            client.setTcpNoDelay(true);
            InputStream clientIn = client.getInputStream();

            String clientIp = readProxyHeader(clientIn, client);
            if (clientIp == null) {
                // PROXY v2 ヘッダーが無い接続はプロキシ経由ではないため拒否する
                closeQuietly(client);
                return;
            }

            backend = new Socket();
            backend.setTcpNoDelay(true);
            backend.connect(new InetSocketAddress("127.0.0.1", backendPort), 5000);
            localPort = backend.getLocalPort();
            clientIpByLocalPort.put(localPort, clientIp);

            Socket finalBackend = backend;
            Socket finalClient = client;
            pool.execute(() -> pipe(finalBackend, finalClient));
            pipe(client, backend);
        } catch (IOException ignored) {
            closeQuietly(client);
            closeQuietly(backend);
        } finally {
            if (localPort > 0) {
                clientIpByLocalPort.remove(localPort);
            }
        }
    }

    /**
     * PROXY protocol v2 ヘッダーを読み取り、実クライアント IP を返す。
     * ヘッダーが不正な場合は null（接続拒否）。
     */
    private String readProxyHeader(InputStream in, Socket client) throws IOException {
        byte[] header = readFully(in, HEADER_LENGTH);
        if (header == null || !Arrays.equals(Arrays.copyOfRange(header, 0, 12), SIGNATURE)) {
            return null;
        }
        int versionCommand = header[12] & 0xFF;
        int familyProtocol = header[13] & 0xFF;
        int addressLength = ((header[14] & 0xFF) << 8) | (header[15] & 0xFF);
        if ((versionCommand >> 4) != 0x2) {
            return null;
        }
        byte[] addresses = addressLength > 0 ? readFully(in, addressLength) : new byte[0];
        if (addressLength > 0 && addresses == null) {
            return null;
        }
        int command = versionCommand & 0x0F;
        if (command == 0x0) {
            // LOCAL (ヘルスチェック等) は接続元アドレスをそのまま使用
            return client.getInetAddress() == null ? "unknown" : client.getInetAddress().getHostAddress();
        }
        int family = familyProtocol >> 4;
        try {
            if (family == 0x1 && addresses.length >= 12) {
                return InetAddress.getByAddress(Arrays.copyOfRange(addresses, 0, 4)).getHostAddress();
            }
            if (family == 0x2 && addresses.length >= 36) {
                return InetAddress.getByAddress(Arrays.copyOfRange(addresses, 0, 16)).getHostAddress();
            }
        } catch (IOException ignored) {
        }
        // UNSPEC など: 接続元アドレスで代用
        return client.getInetAddress() == null ? "unknown" : client.getInetAddress().getHostAddress();
    }

    private byte[] readFully(InputStream in, int length) throws IOException {
        byte[] buffer = new byte[length];
        int read = 0;
        while (read < length) {
            int count = in.read(buffer, read, length - read);
            if (count < 0) {
                return null;
            }
            read += count;
        }
        return buffer;
    }

    /** 片方向のデータ転送。どちらかが閉じたら両方閉じる。 */
    private void pipe(Socket from, Socket to) {
        byte[] buffer = new byte[8192];
        try {
            InputStream in = from.getInputStream();
            OutputStream out = to.getOutputStream();
            int count;
            while ((count = in.read(buffer)) >= 0) {
                out.write(buffer, 0, count);
                out.flush();
            }
        } catch (IOException ignored) {
        } finally {
            closeQuietly(from);
            closeQuietly(to);
        }
    }

    private void closeQuietly(Socket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
