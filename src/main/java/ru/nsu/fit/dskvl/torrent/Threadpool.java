package ru.nsu.fit.dskvl.torrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

public class Threadpool {
    final List<Thread> threads = new ArrayList<>();
    final BlockingQueue<Runnable> tasks = new ArrayBlockingQueue<>(32);

    public Threadpool(int numThreads) {
        for (int i = 0; i < numThreads; i++) {
            Thread t = new Thread(new Worker());
            threads.add(t);

            t.start();
        }
    }

    private class Worker implements Runnable {
        @Override
        public void run() {
            while (true) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }

                Runnable r;
                try {
                    r = tasks.take();
                } catch (InterruptedException e) {
                    continue;
                }

                r.run();
            }
        }
    }

    public void addTask(Runnable task) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                tasks.put(task);
                break;
            } catch (InterruptedException e) {
                continue;
            }
        }
    }

    void join() throws InterruptedException {
        for (Thread thread : threads) {
            thread.join();
        }
    }

    void stop() {
        for (Thread thread : threads) {
            thread.interrupt();
        }
    }
}