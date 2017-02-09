package edu.brown.cs.systems.tracingplane.atom_layer.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import org.junit.Test;

public class TestSerialization {

    @Test
    public void testSerializeNulls() {

        assertNotNull(AtomLayerSerialization.serialize(null));
        assertNotNull(AtomLayerSerialization.serialize(new ArrayList<ByteBuffer>()));
        
        assertEquals(0, AtomLayerSerialization.serialize(null).length);
        assertEquals(0, AtomLayerSerialization.serialize(new ArrayList<ByteBuffer>()).length);

    }

}
