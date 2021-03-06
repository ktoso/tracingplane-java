package brown.tracingplane.atomlayer;

import java.nio.ByteBuffer;

/**
 * Signed varint (32 bit and 64 bit) that is encoded in such a way that the lexicographic comparison of the binary
 * encoding is consistent with the numeric comparison of the numeric values.
 * 
 * This is like unsigned varints, but with the following:
 * 
 * - the first bit is a sign bit with 0 negative and 1 positive - negative values use 0 to indicate additional bytes;
 * positive values use 1
 * 
 * So, for example, we get the following encodings:
 * 
 * Long.MIN_VALUE: 0000 0000 0000 0000 0000 0000 ... (9 bytes worth of zeros) -65: 0011 1111 1011 1111 -64: 0100 0000
 * -19: 0110 1101 -4: 0111 1100 -1: 0111 1111 0: 1000 0000 1: 1000 0001 19: 1001 0011 63: 1011 1111 64: 1100 0000 0100
 * 0000 LONG.MAX_VALUE: 1111 1111 1111 1111 1111 1111 ... (9 bytes worth of ones)
 */
public class SignedLexVarint {

    /**
     * @param value Any integer value
     * @return the length of the encoded representation of this value. Values in the range [-2^6, 2^6) use 1 byte;
     *         [-2^12, -2^6) and [2^6, 2^12) use 2 bytes, and so on
     */
    public static int encodedLength(int value) {
        if (value < 0) {
            value = -(value + 1);
        }
        if ((value & 0xFFFFFFC0) == 0) {
            return 1;
        } else if ((value & 0xFFFFE000) == 0) {
            return 2;
        } else if ((value & 0xFFF00000) == 0) {
            return 3;
        } else if ((value & 0xF8000000) == 0) {
            return 4;
        } else {
            return 5;
        }
    }

    /**
     * @param value Any long value
     * @return the length of the encoded representation of this value. Values in the range [-2^6, 2^6) use 1 byte;
     *         [-2^12, -2^6) and [2^6, 2^12) use 2 bytes, and so on
     */
    public static int encodedLength(long value) {
        if (value < 0) {
            value = -(value + 1);
        }
        if ((value & 0xFFFFFFFFFFFFFFC0L) == 0) {
            return 1;
        } else if ((value & 0xFFFFFFFFFFFFE000L) == 0) {
            return 2;
        } else if ((value & 0xFFFFFFFFFFF00000L) == 0) {
            return 3;
        } else if ((value & 0xFFFFFFFFF8000000L) == 0) {
            return 4;
        } else if ((value & 0xFFFFFFFC00000000L) == 0) {
            return 5;
        } else if ((value & 0xFFFFFE0000000000L) == 0) {
            return 6;
        } else if ((value & 0xFFFF000000000000L) == 0) {
            return 7;
        } else if ((value & 0xFF80000000000000L) == 0) {
            return 8;
        } else {
            return 9;
        }
    }

    public static int writeLexVarInt32(ByteBuffer buf, int value) {
        return writeLexVarInt64(buf, value);
    }
    
    public static int writeReverseLexVarInt32(ByteBuffer buf, int value) {
        return writeReverseLexVarInt64(buf, value);
    }

    public static int writeLexVarInt64(ByteBuffer buf, long value) {
        boolean negate = value < 0;
        if (negate) {
            value = -(value + 1);
        }
        int size = encodedLength(value);
        int remaining = size - 1;

        byte b0;
        if (size == 9) {
            buf.put((byte) (negate ? 0 : 0xff));
            b0 = (byte) (0x80 | (value >>> 56));
            remaining--;
        } else {
            b0 = (byte) (0xff << (8 - size));
            byte mask = (byte) ~b0;
            b0 = (byte) (b0 | ((value >>> (8 * remaining)) & mask));
        }

        if (negate) {
            b0 = (byte) ~b0;
            buf.put(b0);
            for (int i = remaining; i > 0; i--) {
                byte b = (byte) (value >>> (8 * (i - 1)));
                buf.put((byte) ~b);
            }
        } else {
            buf.put(b0);
            for (; remaining > 0; remaining--) {
                byte b = (byte) (value >>> (8 * (remaining - 1)));
                buf.put(b);
            }
        }

        return size;
    }
    
    public static int writeReverseLexVarInt64(ByteBuffer buf, long value) {
        return writeLexVarInt64(buf, -(value + 1));
    }

    /**
     * Interprets the next bytes of {@code buf} as a lexicographically comparable signed 32-bit integer. Reads up to 5
     * bytes from {@code buf} to decode the value.
     * 
     * @param buf a buffer
     * @return a signed integer read from buf
     * @throws AtomLayerException if the next bytes are invalid when interpreted as a lexicographically comparable
     *             varint
     */
    public static int readLexVarInt32(ByteBuffer buf) throws AtomLayerException {
        return (int) readLexVarInt64(buf);
    }

    /**
     * Interprets the next bytes of {@code buf} as a lexicographically comparable signed 32-bit integer. Reads up to 5
     * bytes from {@code buf} to decode the value. Interprets them as a reverse int, eg, s.t. the lexicographical
     * comparison is reversed
     * 
     * @param buf a buffer
     * @return a signed integer read from buf
     * @throws AtomLayerException if the next bytes are invalid when interpreted as a lexicographically comparable
     *             varint
     */
    public static int readReverseLexVarInt32(ByteBuffer buf) throws AtomLayerException {
        return -(readLexVarInt32(buf) + 1);
    }

    /**
     * Interprets the next bytes of {@code buf} as a lexicographically comparable signed 64-bit integer. Reads up to 9
     * bytes from {@code buf} to decode the value.
     * 
     * @param buf a buffer
     * @return a signed long read from buf
     * @throws AtomLayerException if the next bytes are invalid when interpreted as a lexicographically comparable
     *             varint
     */
    public static long readLexVarInt64(ByteBuffer buf) throws AtomLayerException {
        byte b0 = buf.get();
        int size = interpretSize(b0);
        if (size == 1) {
            return b0 ^ (byte) 0x80;
        }

        boolean negative = b0 >= 0; // our encoding actually encodes negative -> positive
        if (negative) {
            b0 = (byte) ~b0;
        }

        long result;
        if (size == 8) {
            byte b1 = buf.get();
            if (negative) {
                b1 = (byte) ~b1;
            }
            if ((b1 & 0x80) == 0) {
                size--;
            }

            result = b1 & (0xff >>> 1);
        } else {
            result = b0 & (0xff >>> (size + 1));
        }

        result <<= (8 * (size - 1));
        result += readInt64(buf, size - 1, negative);
        if (negative) {
            return -result - 1;
        } else {
            return result;
        }
    }

    /**
     * Interprets the next bytes of {@code buf} as a lexicographically comparable signed 64-bit integer. Reads up to 9
     * bytes from {@code buf} to decode the value. Interprets them as a reverse int, eg, s.t. the lexicographical
     * comparison is reversed
     * 
     * @param buf a buffer
     * @return a signed long read from buf
     * @throws AtomLayerException if the next bytes are invalid when interpreted as a lexicographically comparable
     *             varint
     */
    public static long readReverseLexVarInt64(ByteBuffer buf) throws AtomLayerException {
        return -(readLexVarInt64(buf) + 1);
    }

    /**
     * @param b a lexicographically encoded byte
     * @return true if {@code b > 0}, false otherwise.
     */
    static boolean interpretSign(byte b) {
        return b < 0;  // if b begins with 1, in our encoding it is positive
    }

    static int interpretSize(byte b) {
        if (b < 0) {
            b = (byte) ~b;
        }
        for (int i = 1; i < 8; i++) {
            if ((b & (0x80 >>> i)) != 0) {
                return i;
            }
        }
        return 8;
    }

    static long readInt64(ByteBuffer buf, int numBytes, boolean negative) throws AtomLayerException {
        if (numBytes > 8 || numBytes <= 0) {
            throw new AtomLayerException("Invalid Int64 with " + numBytes + " bytes");
        }
        long result = 0;
        if (negative) {
            for (int i = 0; i < numBytes; i++) {
                result = (result << 8) + (((byte) ~buf.get()) & 0xff);
            }
        } else {
            for (int i = 0; i < numBytes; i++) {
                result = (result << 8) + (buf.get() & 0xff);
            }
        }
        return result;
    }

    /**
     * @param value a 32-bit signed integer
     * @return {@code value} encoded as a lexicographically-comparable variable-length integer.
     */
    public static byte[] writeVarInt32(int value) {
        ByteBuffer buf = ByteBuffer.allocate(SignedLexVarint.encodedLength(value));
        SignedLexVarint.writeLexVarInt32(buf, value);
        return buf.array();
    }

    /**
     * @param value a 64-bit signed long
     * @return {@code value} encoded as a lexicographically-comparable variable-length long integer.
     */
    public static byte[] writeVarInt64(long value) {
        ByteBuffer buf = ByteBuffer.allocate(SignedLexVarint.encodedLength(value));
        SignedLexVarint.writeLexVarInt64(buf, value);
        return buf.array();
    }
}
