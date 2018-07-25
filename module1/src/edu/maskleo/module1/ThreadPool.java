package edu.maskleo.module1;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

public class ThreadPool {

    private static BlockingDeque<Runnable> deque = new LinkedBlockingDeque<>();
    private int maxIdle = -1;
    private int minIdle = -1;
    private int maxActive = -1;
    private int maxWait = -1;

    private ThreadPool() {
    }

    /**
     * 计算该线程是否需要销毁
     *
     * @return
     */
    private static boolean keepOn() {
        return true;
    }

    /**
     * 线程加入线程队列
     *
     * @param t
     */
    private static void put(Runnable t) {
        try {
            deque.put(t);
        } catch (Exception e) {
            ;
        }

    }

    public static ThreadPool build() {
        return new ThreadPool();
    }

    public int getMaxIdle() {
        return maxIdle;
    }

    public ThreadPool setMaxIdle(int maxIdle) {
        this.maxIdle = maxIdle;
        return this;
    }

    public int getMinIdle() {
        return minIdle;
    }

    public ThreadPool setMinIdle(int minIdle) {
        this.minIdle = minIdle;
        return this;
    }

    public int getMaxActive() {
        return maxActive;
    }

    public ThreadPool setMaxActive(int maxActive) {
        this.maxActive = maxActive;
        return this;
    }

    public int getMaxWait() {
        return maxWait;
    }

    public ThreadPool setMaxWait(int maxWait) {
        this.maxWait = maxWait;
        return this;
    }

    private static class InnerThread implements Runnable {

        private InnerThread() {
        }

        @Override
        public void run() {
            while (keepOn()) {
                try {
                    Runnable thread = deque.take();
                    thread.run();
                    put(this);
                } catch (Exception e) {
                    ;
                }

            }
        }
    }
}
