package com.luka.lbdb.network.protocol;

import com.luka.lbdb.network.protocol.response.*;
import com.luka.lbdb.querying.virtualEntities.constant.*;
import com.luka.lbdb.records.DatabaseType;
import com.luka.lbdb.records.schema.Schema;
import com.luka.lbdb.network.exceptions.ProtocolException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/// Implementation of the [Redis serialization protocol](https://redis.io/docs/latest/develop/reference/protocol-spec/)
/// for different types of responses from the server. Heavily inspired by
/// [MKDB](https://github.com/antoniosarosi/mkdb/blob/master/src/tcp/proto.rs), a Rust implementation
/// of a DBMS.
public class Protocol {
    /// Serializes the response object into an array of bytes.
    /// Each response serialization consists of these elements:
    ///
    /// ```
    /// PAYLOAD LENGTH | RESPONSE TYPE | RESPONSE SERIALIZATION
    /// ```
    ///
    /// Response types (as bytes) are as follows:
    /// - [EmptySet]: `_`
    /// - [ErrorResponse]: `-`
    /// - [QuerySet]: `*`
    ///
    /// Response serializations are as follows:
    /// - [EmptySet]: `rows affected AS INTEGER`
    /// - [ErrorResponse]: `error AS ENCODED STRING`
    /// - [QuerySet]:
    /// ```
    /// schema length AS SHORT
    /// [
    ///     column name length AS SHORT
    ///     colum name AS ENCODED STRING
    ///     column type AS SHORT
    ///     column runtime length AS INTEGER (if the type is VARCHAR)
    ///     column nullability AS BYTE
    /// ]
    /// number of tuples AS INTEGER
    /// [
    ///     serialized tuple
    /// ]
    /// ```
    public static byte[] serialize(Response response) throws IOException {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();

        switch (response) {
            case EmptySet emptySet -> {
                payload.write('_');
                writeIntLE(payload, emptySet.rowsAffected());
            }
            case ErrorResponse errorResponse -> {
                payload.write('-');
                payload.write(errorResponse.error().getBytes(StandardCharsets.UTF_8));
            }
            case QuerySet querySet -> {
                payload.write('*');
                writeShortLE(payload, (short) querySet.schema().getFields().size());

                for (String column : querySet.schema().getFields()) {
                    byte[] colBytes = column.getBytes(StandardCharsets.UTF_8);
                    writeShortLE(payload, (short) colBytes.length);
                    payload.write(colBytes);

                    writeShortLE(payload, (short) querySet.schema().type(column).sqlType);

                    if (querySet.schema().type(column) == DatabaseType.VARCHAR) {
                        writeIntLE(payload, querySet.schema().runtimeLength(column));
                    }

                    payload.write(querySet.schema().isNullable(column) ? 1 : 0);
                }

                writeIntLE(payload, querySet.tuples().size());

                for (List<Constant> tuple : querySet.tuples()) {
                    serializeTuple(payload, tuple);
                }
            }
        }

        byte[] payloadBytes = payload.toByteArray();
        ByteBuffer finalPacket = ByteBuffer.allocate(4 + payloadBytes.length);
        finalPacket.order(ByteOrder.LITTLE_ENDIAN);

        finalPacket.putInt(payloadBytes.length);
        finalPacket.put(payloadBytes);

        return finalPacket.array();
    }

    /// Skips length header. Deserializes the bytes into a response.
    ///
    /// @return The deserialized response object.
    /// @throws ProtocolException if the payload type is unknown.
    public static Response deserialize(byte[] bytes) {
        ByteBuffer payload = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        byte payloadType = payload.get();

        return switch (payloadType) {
            case '-' -> new ErrorResponse(StandardCharsets.UTF_8.decode(payload).toString());
            case '_' -> new EmptySet(payload.getInt());
            case '*' -> {
                Schema outputSchema = new Schema();
                short schemaLength = payload.getShort();

                for (int i = 0; i < schemaLength; i++) {
                    short columnNameLength = payload.getShort();

                    byte[] stringBytes = new byte[columnNameLength];
                    payload.get(stringBytes);
                    String column = new String(stringBytes, StandardCharsets.UTF_8);

                    short sqlType = payload.getShort();
                    DatabaseType type = DatabaseType.get(sqlType);
                    int runtimeLength = type.length;

                    if (type == DatabaseType.VARCHAR) {
                        runtimeLength = payload.getInt();
                    }

                    boolean isNullable = payload.get() == 1;

                    outputSchema.addField(column, type, runtimeLength, isNullable);
                }

                int tuplesLength = payload.getInt();

                List<List<Constant>> tuples = new ArrayList<>(tuplesLength);

                for (int i = 0; i < tuplesLength; i++) {
                    tuples.add(deserializeRecord(payload, outputSchema));
                }

                yield new QuerySet(outputSchema, tuples);
            }
            default -> throw new ProtocolException("Unknown payload type: " + (char) payloadType);
        };
    }

    /// Serializes one tuple into bytes.
    private static void serializeTuple(ByteArrayOutputStream payload, List<Constant> tuple) throws IOException {
        for (Constant constant : tuple) {
            if (constant.isNull()) {
                payload.write(0);
                continue;
            }

            payload.write(1);

            switch (constant) {
                case IntConstant i -> writeIntLE(payload, i.value());
                case BooleanConstant b -> payload.write(b.value() ? 1 : 0);
                case StringConstant s -> {
                    byte[] strBytes = s.value().getBytes(StandardCharsets.UTF_8);
                    writeIntLE(payload, strBytes.length);
                    payload.write(strBytes);
                }
                case NullConstant n -> {}
            }
        }
    }

    /// Deserializes bytes of a tuple into a tuple.
    ///
    /// @return The deserialized tuple, according to some schema.
    private static List<Constant> deserializeRecord(ByteBuffer payload, Schema schema) {
        List<String> fields = schema.getFields();
        List<Constant> tuple = new ArrayList<>(fields.size());

        for (String col : fields) {
            byte isNullIndicator = payload.get();

            if (isNullIndicator == 0) {
                tuple.add(NullConstant.INSTANCE);
                continue;
            }

            DatabaseType type = schema.type(col);

            if (type == DatabaseType.INT) {
                tuple.add(new IntConstant(payload.getInt()));
            } else if (type == DatabaseType.BOOLEAN) {
                tuple.add(new BooleanConstant(payload.get() == 1));
            } else if (type == DatabaseType.VARCHAR) {
                int len = payload.getInt();
                byte[] strBytes = new byte[len];
                payload.get(strBytes);
                tuple.add(new StringConstant(new String(strBytes, StandardCharsets.UTF_8)));
            } else {
                throw new ProtocolException("Unknown type during record deserialization");
            }
        }

        return tuple;
    }

    /// Helper function for writing an integer little endian value.
    private static void writeIntLE(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 24) & 0xFF);
    }

    /// Helper function for writing a short little endian value.
    private static void writeShortLE(ByteArrayOutputStream out, short value) {
        out.write(value & 0xFF);
        out.write((value >> 8) & 0xFF);
    }
}
