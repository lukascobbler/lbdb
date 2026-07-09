package com.luka.lbdb.network;

import com.luka.lbdb.db.LBDB;
import com.luka.lbdb.network.protocol.Protocol;
import com.luka.lbdb.network.protocol.response.ErrorResponse;
import com.luka.lbdb.network.protocol.response.Response;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/// The main entry point for receiving packets from a client.
/// Handles each client in a separate thread. Threads are managed
/// by a thread pool.
@SuppressWarnings("InfiniteLoopStatement")
public class Server {
    private final LBDB db;
    private volatile boolean isShutdown = false;
    private volatile boolean isDraining = false;
    private final ReentrantLock checkpointLock = new ReentrantLock();
    private final int port;
    private ServerSocket serverSocket;

    private final ExecutorService threadPool;
    private final ScheduledExecutorService checkpointScheduler;

    /// A LBDB server needs a port to work on (host is localhost), and the path
    /// representing a directory where the database will operate.
    public Server(int port, Path dbPath) {
        db = new LBDB(dbPath);
        this.port = port;
        this.threadPool = Executors.newFixedThreadPool(8);
        this.checkpointScheduler = Executors.newSingleThreadScheduledExecutor();
    }

    /// Starts an infinite loop of accepting user connections, and
    /// sends their packets to a handler thread that is chosen from the
    /// thread pool. Starts the checkpoint scheduler, that runs every so
    /// often.
    public void start() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));

        checkpointScheduler.scheduleWithFixedDelay(
                this::checkpoint,
                10, 10, TimeUnit.MINUTES
        );

        try {
            serverSocket = new ServerSocket(port);
            System.out.println("LBDB Server started on port " + port);

            while (!isShutdown) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.submit(() -> handleClient(clientSocket));
                } catch (SocketException e) {
                    if (isShutdown || isDraining) break;
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    /// Atomically write a checkpoint. Will not start writing a new
    /// checkpoint if one is already in the process of being written.
    public void checkpoint() {
        if (!checkpointLock.tryLock()) {
            return;
        }

        try {
            db.getTransactionManager().writeCheckpoint(false);
        } finally {
            checkpointLock.unlock();
        }
    }

    /// Shuts down the server by allowing unfinished transactions to commit or
    /// rollback. Disallows new clients to connect to the database. Writes a
    /// checkpoint.
    public void shutdown() {
        if (isDraining || isShutdown) return;

        System.out.println("Entering drain mode... stopping new queries but allowing COMMIT/ROLLBACK.");
        isDraining = true;

        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
        } catch (IOException ignored) { }

        checkpointScheduler.shutdown();

        if (!checkpointLock.tryLock()) {
            // if a checkpoint is being written at the time
            // of shut down simply wait for it to finish
            checkpointLock.lock();
            checkpointLock.unlock();
        } else {
            // if a checkpoint isn't being written at the time
            // of shut down, simply write it
            try {
                db.getTransactionManager().writeCheckpoint(true);
            } finally {
                checkpointLock.unlock();
            }
        }

        System.out.println("All transactions cleared. Shutting down.");

        isShutdown = true;
        threadPool.shutdownNow();

        System.out.println("Server stopped safely.");
    }

    /// Clients send packets in the format `4 BYTE PAYLOAD LENGTH | N BYTES STRING PAYLOAD`.
    /// A payload represents some command or query that should be executed on the system.
    /// The handler processes the command which outputs a [Response] object, that is then
    /// serialized using the [Protocol] implementation and sent to the client as bytes.
    private void handleClient(Socket socket) {
        try (socket; DataInputStream in = new DataInputStream(socket.getInputStream());
             OutputStream out = socket.getOutputStream()) {
            int sessionId = socket.getRemoteSocketAddress().hashCode();
            try {
                while (true) {
                    byte[] header = new byte[4];
                    in.readFully(header);
                    int payloadLength = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt();

                    byte[] payload = new byte[payloadLength];
                    in.readFully(payload);

                    String queryOrCommand = new String(payload, StandardCharsets.UTF_8);

                    if (isDraining) {
                        String upperCmd = queryOrCommand.toUpperCase();
                        if (!upperCmd.startsWith("COMMIT") && !upperCmd.startsWith("ROLLBACK")) {
                            Response error = new ErrorResponse("Server is set to shut down. Only COMMIT or ROLLBACK accepted.");
                            out.write(Protocol.serialize(error));
                            out.flush();
                            continue;
                        }
                    }

                    Response response = db.getPlanner().execute(queryOrCommand, sessionId);

                    byte[] responseBytes = Protocol.serialize(response);
                    out.write(responseBytes);
                    out.flush();
                }
            } catch (EOFException e) {
                if (db.getTransactionManager().isManual(sessionId)) {
                    db.getTransactionManager().getOrCreateTransaction(sessionId).rollback();
                }
                System.out.println("Client disconnected.");
            } catch (IOException e) {
                if (db.getTransactionManager().isManual(sessionId)) {
                    db.getTransactionManager().getOrCreateTransaction(sessionId).rollback();
                }
                System.err.println("Client handler error: " + e.getMessage());
            }
        } catch (IOException ignored) {
        }
    }
}
