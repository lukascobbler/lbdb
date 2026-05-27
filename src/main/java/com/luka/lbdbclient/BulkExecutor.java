package com.luka.lbdbclient;

import com.luka.lbdb.network.protocol.Protocol;
import com.luka.lbdb.network.protocol.response.*;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

/// Not a general purpose client. Executes all queries / commands
/// in a file and disconnects.
public class BulkExecutor implements AutoCloseable {
    private final Socket socket;
    private final DataInputStream in;
    private final OutputStream out;
    private final Terminal terminal;
    private final PrintWriter writer;

    /// A bulk modifier client needs to know what is the port of the server.
    public BulkExecutor(int port) throws IOException {
        this.socket = new Socket("localhost", port);
        this.in = new DataInputStream(socket.getInputStream());
        this.out = socket.getOutputStream();
        this.terminal = TerminalBuilder.builder().system(true).build();
        this.writer = terminal.writer();
    }

    /// Executes all queries / commands from the given path one
    /// by one and exits. On encountering an error, rolls back.
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("USAGE: BulkExecutor <port> <file_path>");
            System.exit(1);
        }

        int port = -1;
        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.err.printf("Invalid port: %s%n", args[0]);
            System.exit(2);
        }

        Path filePath = Paths.get(args[1]);
        if (!Files.exists(filePath)) {
            System.err.printf("File not found: %s%n", args[1]);
            System.exit(3);
        }

        try (BulkExecutor client = new BulkExecutor(port)) {
            boolean error = false;

            client.writer.println("Connected to port " + port + ". Executing file: " + filePath);

            client.writer.println("\n--- Starting Transaction ---");
            client.handleResponse(client.executeQuery("START TRANSACTION;"));

            List<String> lines = Files.readAllLines(filePath);
            StringBuilder queryBuffer = new StringBuilder();

            long start = System.nanoTime();

            for (String line : lines) {
                line = line.trim();

                if (line.isEmpty() || line.startsWith("--")) continue;

                queryBuffer.append(line).append(" ");

                if (line.endsWith(";")) {
                    String fullQuery = queryBuffer.toString().trim();
                    queryBuffer.setLength(0);

                    client.writer.println("\nExecuting: " + fullQuery);

                    Response response = client.executeQuery(fullQuery);

                    client.handleResponse(response);

                    if (response instanceof ErrorResponse) {
                        client.writer.println("Error encountered. Aborting remaining statements.");
                        error = true;
                        break;
                    }
                }
            }

            if (!error) {
                client.writer.println("\n--- Committing Transaction ---");
                client.handleResponse(client.executeQuery("COMMIT;"));
            } else {
                client.writer.println("\n--- Rolling back Transaction ---");
                client.handleResponse(client.executeQuery("ROLLBACK;"));
            }

            long end = System.nanoTime();
            double durationMs = (end - start) / 1_000_000.0;

            client.writer.printf("\n(%.2f ms)%n", durationMs);
            client.terminal.flush();
        } catch (Exception e) {
            System.err.println("Execution error: " + e.getMessage());
        }
    }

    /// Sends a query object to the socket, and reads the whole
    /// output.
    ///
    /// @return The deserialized response object from the server.
    public Response executeQuery(String query) throws IOException {
        byte[] queryBytes = query.getBytes(StandardCharsets.UTF_8);

        ByteBuffer header = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(queryBytes.length);
        out.write(header.array());

        out.write(queryBytes);
        out.flush();

        byte[] responseHeader = new byte[4];
        in.readFully(responseHeader);
        int responseLength = ByteBuffer.wrap(responseHeader)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getInt();

        byte[] responsePayload = new byte[responseLength];
        in.readFully(responsePayload);

        return Protocol.deserialize(responsePayload);
    }

    /// Different prints based on different server responses.
    private void handleResponse(Response response) {
        switch (response) {
            case EmptySet emptySet -> writer.println("Rows affected: " + emptySet.rowsAffected());
            case ErrorResponse errorResponse -> writer.println("Database error: " + errorResponse.error());
            case QuerySet querySet -> {
                writer.print(TablePrinter.print(querySet.schema(), querySet.tuples()));
                writer.println(querySet.tuples().size() + " rows");
            }
        }
        terminal.flush();
    }

    @Override
    public void close() throws IOException {
        if (socket != null) socket.close();
        if (terminal != null) terminal.close();
    }
}
