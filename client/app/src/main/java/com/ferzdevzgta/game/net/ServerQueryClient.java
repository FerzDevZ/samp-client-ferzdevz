package com.ferzdevzgta.game.net;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.ferzdevzgta.game.servers.ServerQuery;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

/**
 * Sends one {@link ServerQuery} request over UDP and reports the answer on the main thread.
 *
 * <p>Every probe runs on its own thread with a hard timeout, so a server that never answers, or a
 * DNS name that does not resolve, cannot block the screen that listed it. The socket is closed in
 * a finally block and the callback always fires exactly once, with ok=false on any failure.
 */
public final class ServerQueryClient {

    /** What the screen shows for one server. */
    public static final class Result {
        public final boolean ok;
        public final int latencyMs;
        public final ServerQuery.Info info;
        public final String error;

        private Result(boolean ok, int latencyMs, ServerQuery.Info info, String error) {
            this.ok = ok;
            this.latencyMs = latencyMs;
            this.info = info;
            this.error = error;
        }

        static Result ok(int latencyMs, ServerQuery.Info info) {
            return new Result(true, latencyMs, info, null);
        }

        static Result failed(String reason) {
            return new Result(false, -1, null, reason);
        }
    }

    /** Called on the main thread, exactly once per probe. */
    public interface Callback {
        void onResult(Result result);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ServerQueryClient() {
    }

    /** Probes a server without blocking the caller. */
    public static void probe(String host, int port, Callback callback) {
        probe(host, port, ServerQuery.DEFAULT_TIMEOUT_MS, callback);
    }

    public static void probe(String host, int port, int timeoutMs, Callback callback) {
        if (host == null || host.trim().isEmpty()) {
            deliver(callback, Result.failed("empty host"));
            return;
        }
        Thread worker = new Thread(() -> {
            Result result;
            try {
                result = run(host, port, timeoutMs);
            } catch (Exception e) {
                result = Result.failed(String.valueOf(e.getMessage()));
            }
            deliver(callback, result);
        }, "server-query");
        worker.setDaemon(true);
        worker.start();
    }

    private static Result run(String host, int port, int timeoutMs) throws Exception {
        InetAddress address = InetAddress.getByName(host.trim());
        byte[] request = ServerQuery.request(address.getAddress(), port);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(timeoutMs);
            socket.send(new DatagramPacket(request, request.length, address, port));
            byte[] buffer = new byte[4096];
            DatagramPacket answer = new DatagramPacket(buffer, buffer.length);
            long started = System.nanoTime();
            socket.receive(answer);
            int latency = (int) ((System.nanoTime() - started) / 1_000_000L);
            ServerQuery.Info info = ServerQuery.parse(
                    java.util.Arrays.copyOfRange(buffer, 0, answer.getLength()));
            if (info == null) {
                Log.d("ServerQueryClient", "unexpected answer from " + host);
                return Result.failed("unexpected answer");
            }
            return Result.ok(latency, info);
        } catch (SocketTimeoutException timeout) {
            return Result.failed("timeout");
        }
    }

    private static void deliver(Callback callback, Result result) {
        if (callback == null) {
            return;
        }
        MAIN.post(() -> callback.onResult(result));
    }
}
