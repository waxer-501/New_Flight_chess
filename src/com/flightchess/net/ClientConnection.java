package com.flightchess.net;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.function.Consumer;

/**
 * 客户端与房主之间的连接。
 *
 * 连接建立逻辑采用同步方式，保证在返回后 out/in 已经就绪，
 * 这样 UI 可以立即安全地调用 send 发送首个 JOIN_ROOM_REQ。
 * 监听循环在单独的后台线程中运行，不会阻塞 UI。
 */
public class ClientConnection {

    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;

    private volatile boolean running = false;

    /** 收到消息后的回调，由 UI 或控制器设置。 */
    private Consumer<Message> messageHandler;

    public void setMessageHandler(Consumer<Message> handler) {
        this.messageHandler = handler;
    }

    /**
     * 建立到房主的连接。
     * 若连接失败，会抛出 IOException。
     */
    public void connect(String host, int port) throws IOException {
        socket = new Socket(host, port);
        out = new ObjectOutputStream(socket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(socket.getInputStream());
        running = true;

        Thread listener = new Thread(this::listenLoop, "ClientConnection-Listener");
        listener.setDaemon(true);
        listener.start();
    }

    private void listenLoop() {
        try {
            while (running) {
                Object obj = in.readObject();
                if (!(obj instanceof Message)) {
                    continue;
                }
                Message msg = (Message) obj;
                Consumer<Message> handler = messageHandler;
                if (handler != null) {
                    handler.accept(msg);
                }
            }
        } catch (EOFException eof) {
            // 连接正常关闭
        } catch (IOException | ClassNotFoundException e) {
            if (running) {
                e.printStackTrace();
            }
        } finally {
            running = false;
            try {
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException ignored) {
            }
        }
    }

    public synchronized void send(Message msg) {
        if (out == null) {
            return;
        }
        try {
            out.writeObject(msg);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        running = false;
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }
}

