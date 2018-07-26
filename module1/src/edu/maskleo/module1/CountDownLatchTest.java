package edu.maskleo.module1;

import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class CountDownLatchTest {

    public static void main(String[] args) {
        CountDownLatch latch = new CountDownLatch(4);
        Thread t1 = new Thread(new InnerThread(latch),"t1");
        Thread t2 = new Thread(new InnerThread(latch),"t2");
        Thread t3 = new Thread(new InnerThread(latch),"t3");
        Thread t4 = new Thread(new InnerThread(latch),"t4");
        t1.start();
        t2.start();
        t3.start();
        t4.start();
        try {
            latch.await();
        }catch (Exception e){
            ;
        }
        System.out.println("all finish!");
    }

    static class InnerThread implements Runnable{

        private CountDownLatch latch;

        public InnerThread(CountDownLatch latch){
            this.latch = latch;
        }

        @Override
        public void run() {
            System.out.println(Thread.currentThread().getName() + "begin to do something !");
            try {
                Thread.sleep(new Random().nextInt(1000));
            }catch (Exception e){
                ;
            }
            System.out.println(Thread.currentThread().getName() + "finish !");
            latch.countDown();
        }
    }
}
