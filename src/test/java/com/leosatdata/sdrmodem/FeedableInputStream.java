package com.leosatdata.sdrmodem;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * InputStream fed from the outside with {@link #feed(byte[])}.
 * <p>
 * read() blocks until data is available. After {@link #close()} the remaining buffered
 * bytes can still be read, then read returns -1 (end of stream). Blocked readers are woken up.
 * The internal buffer is unbounded, so a slow reader makes memory grow.
 * <p>
 * Example: {@code new SdrModemClient(host, port, 10_000, stream::feed)}
 */
public class FeedableInputStream extends InputStream {

  private final ReentrantLock lock = new ReentrantLock();
  private final Condition dataAvailable = lock.newCondition();
  private final ArrayDeque<byte[]> chunks = new ArrayDeque<>();
  private int offsetInHead;
  private int buffered;
  private boolean closed;

  /** Copies and appends data; wakes up blocked readers. Ignored once the stream is closed. */
  public void feed(byte[] data) {
    Objects.requireNonNull(data);
    if (data.length == 0) {
      return;
    }
    byte[] copy = data.clone();
    lock.lock();
    try {
      if (closed) {
        return;
      }
      chunks.add(copy);
      buffered += copy.length;
      dataAvailable.signalAll();
    } finally {
      lock.unlock();
    }
  }

  @Override
  public int read() throws IOException {
    byte[] one = new byte[1];
    int count = read(one, 0, 1);
    return count < 0 ? -1 : one[0] & 0xFF;
  }

  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    Objects.checkFromIndexSize(off, len, b.length);
    if (len == 0) {
      return 0;
    }
    lock.lock();
    try {
      while (buffered == 0) {
        if (closed) {
          return -1;
        }
        dataAvailable.await();
      }
      int total = 0;
      while (total < len && !chunks.isEmpty()) {
        byte[] head = chunks.peek();
        int n = Math.min(len - total, head.length - offsetInHead);
        System.arraycopy(head, offsetInHead, b, off + total, n);
        total += n;
        offsetInHead += n;
        if (offsetInHead == head.length) {
          chunks.remove();
          offsetInHead = 0;
        }
      }
      buffered -= total;
      return total;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new java.io.InterruptedIOException("interrupted while waiting for data");
    } finally {
      lock.unlock();
    }
  }

  @Override
  public int available() {
    lock.lock();
    try {
      return buffered;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public void close() {
    lock.lock();
    try {
      closed = true;
      dataAvailable.signalAll();
    } finally {
      lock.unlock();
    }
  }
}
