package edu.maskleo.module1;

import java.util.Random;

public class ThreadJoinTest {

    public static void main(String[] args) {
        Thread t1 = new Thread(new InnerThread(),"t1");
        Thread t2 = new Thread(new InnerThread(),"t2");
        Thread t3 = new Thread(new InnerThread(),"t3");
        Thread t4 = new Thread(new InnerThread(),"t4");
        t1.start();
        t2.start();
        t3.start();
        t4.start();
        join(t1, t2, t3, t4);
        System.out.println("all finish!");
    }

    private static void join(Thread t1, Thread t2, Thread t3, Thread t4) {
        try {
            t1.join();
            t2.join();
            t3.join();
            t4.join();
        }catch (Exception e){
        }
    }

    static class InnerThread implements Runnable{
        @Override
        public void run() {
            System.out.println(Thread.currentThread().getName() + "begin to do something !");
            try {
                Thread.sleep(new Random().nextInt(1000));
            }catch (Exception e){
                ;
            }
            System.out.println(Thread.currentThread().getName() + "finish !");
        }
    }


}
