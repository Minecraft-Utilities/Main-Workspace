package xyz.mcutils.backend.common.packet;

import org.junit.jupiter.api.Test;
import xyz.mcutils.backend.common.packet.impl.java.JavaPacketStatusInStart;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaPacketStatusInStartTest {
    private static byte[] varInt(int value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
        return out.toByteArray();
    }

    @Test
    void rejectsResponseLengthAboveCapWithoutAllocating() throws IOException {
        ByteArrayOutputStream in = new ByteArrayOutputStream();
        in.write(varInt(10));
        in.write(varInt(0x00));
        in.write(varInt(Integer.MAX_VALUE));

        DataInputStream input = new DataInputStream(new ByteArrayInputStream(in.toByteArray()));
        DataOutputStream output = new DataOutputStream(new ByteArrayOutputStream());
        assertThrows(IOException.class, () -> new JavaPacketStatusInStart().process(input, output));
    }
}
