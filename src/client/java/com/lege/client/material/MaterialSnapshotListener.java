package com.lege.client.material;

/**
 * 材料列表快照变化监听器。
 * 仅在快照数据真正发生变化时回调，避免无效通知。
 */
@FunctionalInterface
public interface MaterialSnapshotListener {
    void onSnapshotChanged(MaterialListSnapshot snapshot);
}