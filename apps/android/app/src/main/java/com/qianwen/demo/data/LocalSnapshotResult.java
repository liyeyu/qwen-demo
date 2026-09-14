package com.qianwen.demo.data;

public class LocalSnapshotResult {
    public LocalSnapshot snapshot;
    public SnapshotReadStatus status;

    public LocalSnapshotResult(LocalSnapshot snapshot, SnapshotReadStatus status) {
        this.snapshot = snapshot;
        this.status = status;
    }
}
