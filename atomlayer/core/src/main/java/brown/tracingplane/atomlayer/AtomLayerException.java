package brown.tracingplane.atomlayer;

import java.nio.BufferUnderflowException;

public class AtomLayerException extends Exception {

    private static final long serialVersionUID = -658006356579507967L;

    public AtomLayerException(String message) {
        super(message);
    }

    public AtomLayerException(String message, BufferUnderflowException e) {
        super(message, e);
    }

}
