package edu.maskleo.module1;

import java.util.Random;
import java.util.concurrent.Semaphore;

public class SemaphoreTest {

    final static Object lock = new Object();

    final static int NUM = 10;

    public static void main(String[] args) {
        int N = 10;            //工人数
        Semaphore semaphore = new Semaphore(NUM); //机器数目
        for (int i = 0; i < N; i++)
            new Worker(i, semaphore).start();
    }

    static class Worker extends Thread {
        private int num;
        private Semaphore semaphore;

        public Worker(int num, Semaphore semaphore) {
            this.num = num;
            this.semaphore = semaphore;
        }

        @Override
        public void run() {
            try {
                semaphore.acquire(5);
                System.out.println("工人" + this.num + "占用2个机器在生产...");
                Thread.sleep(new Random().nextInt(10));
                System.out.println("工人" + this.num + "释放出机器");
                semaphore.release(2);
                Thread.sleep(new Random().nextInt(1000));
                System.out.println("工人" + this.num + "释放出机器");
                semaphore.release(3);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}
