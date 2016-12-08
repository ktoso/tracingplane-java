package edu.brown.cs.systems.tracingplane.baggage_buffers.gen.xtrace;

import java.util.List;

public class XTrace {

    public Long taskId = null;
    public List<Long> parentEventIds = null;
    public boolean overflow = false;

    public XTrace(Long taskId, List<Long> parentEventIds, boolean overflow) {
        this.taskId = taskId;
        this.parentEventIds = parentEventIds;
        this.overflow = overflow;
    }

}
