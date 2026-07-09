package com.luka.lbdb.fileManagement;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/// A page represents an arbitrary sized chunk of memory that is
/// currently in RAM. It is a wrapper around a sequence of bytes
/// that allows easy write and read operations on various types.
public class Page {
    private final ByteBuffer byteBuffer;
    public static final Charset CHARSET = StandardCharsets.UTF_8;

    /// A constructor that initializes a page with a direct byte buffer.
    /// A direct byte buffer is the one where I/O operations are done natively,
    /// and the memory of a direct buffer lives outside regular heap allocated memory.
    public Page(int blockSize) {
        byteBuffer = ByteBuffer.allocateDirect(blockSize);
    }

    /// A constructor that initializes a page around some other buffer that is usually
    /// of the indirect type.
    public Page(byte[] b) {
        byteBuffer = ByteBuffer.wrap(b);
    }

    /// @return An integer at the given offset.
    public int getInt(int offset) {
        return byteBuffer.getInt(offset);
    }

    /// Puts an integer at the given offset.
    public void setInt(int offset, int integer) {
        byteBuffer.putInt(offset, integer);
    }

    /// The implementation of this function gets the bytes
    /// at the given offset and transforms them into a string.
    ///
    /// @return A string at the given offset.
    public String getString(int offset) {
        byte[] strbytes = getBytes(offset);

        return new String(strbytes, CHARSET);
    }

    /// The implementation of this function gets the bytes
    /// of the passed string, and places them at the given offset.
    public void setString(int offset, String string) {
        setBytes(offset, string.getBytes(CHARSET));
    }

    /// @return A boolean at the given offset.
    public boolean getBoolean(int offset) {
        return byteBuffer.get(offset) == 1;
    }

    /// Puts a boolean at the given offset.
    public void setBoolean(int offset, boolean bool) {
        byteBuffer.put(offset, (byte) (bool ? 1 : 0 ));
    }

    /// The implementation of this function first gets the bytes'
    /// length, which is stored right before the actual bytes,
    /// at the given offset.
    ///
    /// @return Bytes at the given offset.
    public byte[] getBytes(int offset) {
        byteBuffer.position(offset);
        int length = byteBuffer.getInt();
        byte[] bytes = new byte[length];
        byteBuffer.get(bytes);

        return bytes;
    }

    /// The implementation of this function firstly puts the bytes'
    /// length at the given offset, and then the actual bytes.
    public void setBytes(int offset, byte[] bytes) {
        byteBuffer.position(offset);
        byteBuffer.putInt(bytes.length);
        byteBuffer.put(bytes);
    }

    /// @return The maximum byte runtime length of a string, with the system's
    /// charset, of a given length.
    public static int maxLength(int strlen) {
        float bytesPerChar = CHARSET.newEncoder().maxBytesPerChar();
        return Integer.BYTES + (strlen * (int)bytesPerChar);
    }

    /// Internal method used for accessing the byte buffer.
    ///
    /// @return The byte buffer used by the page.
    ByteBuffer contents() {
        byteBuffer.position(0);
        return byteBuffer;
    }
}
