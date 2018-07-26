package edu.maskleo.module1;

import java.util.concurrent.CyclicBarrier;

public class CyclicBarrierTest {

    public static void main(String[] args) {
        CyclicBarrier barrier = new CyclicBarrier(4, () -> System.out.println("xxxxx"));
        Thread t1 = new Thread(new InnerThread(barrier), "t1");
        Thread t2 = new Thread(new InnerThread(barrier), "t2");
        Thread t3 = new Thread(new InnerThread(barrier), "t3");
        Thread t4 = new Thread(new InnerThread(barrier), "t4");
        t1.start();
        t2.start();
        t3.start();
        t4.start();
        System.out.println("all finish!");
    }

    static class InnerThread implements Runnable{

        private CyclicBarrier barrier;

        public InnerThread(CyclicBarrier barrier){
            this.barrier = barrier;
        }

        @Override
        public void run() {
            System.out.println(Thread.currentThread().getName() + " begin to do something !");
            try {
                barrier.await();
            }catch (Exception e){
                ;
            }
            System.out.println(Thread.currentThread().getName() + " finish !");
        }
    }

}
