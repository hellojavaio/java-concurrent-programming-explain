package edu.maskleo.module1;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.RecursiveTask;

public class CountTaskForkJoinTest extends RecursiveTask<Long> {

    private static final long serialVersionUID = 1L;

    //临界值
    private static final int threshold = 100;

    private long start;
    private long end;

    public CountTaskForkJoinTest(long start, long end) {
        this.start = start;
        this.end = end;
    }

    /**
     * ForkJoin实现,返回计算结果
     *
     * @param start 起始值
     * @param end   结束值
     * @return
     * @throws InterruptedException
     * @throws ExecutionException
     */
    private static long forkJoinTest(long start, long end) throws InterruptedException, ExecutionException {
        ForkJoinPool pool = new ForkJoinPool();
        CountTaskForkJoinTest task = new CountTaskForkJoinTest(start, end);

        Future<Long> result = pool.submit(task);
        return result.get();
    }

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        long start = System.currentTimeMillis();
        long start_index = 1;
        long end_index = 20000;
        long ret = forkJoinTest(start_index, end_index);
        System.out.println("result: " + ret);
        long start2 = System.currentTimeMillis();
        System.out.println("执行耗时: " + (start2 - start));
        long sum = 0L;
        for (;start_index <= end_index;start_index++){
            sum += start_index;
            try {
                Thread.sleep(1L);
            }catch (Exception e){
                ;
            }
        }
        System.out.println(sum);
        System.out.println("执行耗时: " + (System.currentTimeMillis() - start2));
    }

    /**
     * 重写compute方法，判断是否将任务进行拆分计算
     */
    @Override
    protected Long compute() {
        long sum = 0;
        //判断是否是拆分完毕
        boolean canCompute = (end - start) <= threshold;
        if (canCompute) {
            for (long i = start; i <= end; i++) {
                try {
                    Thread.sleep(1L);
                }catch (Exception e){
                    ;
                }
                sum += i;
            }
        } else {
            long middle = (start + end) / 2;
            CountTaskForkJoinTest task1 = new CountTaskForkJoinTest(start, middle);
            CountTaskForkJoinTest task2 = new CountTaskForkJoinTest(middle + 1, end);

            task1.fork();
            task2.fork();

            long result1 = task1.join();
            long result2 = task2.join();
            sum = result1 + result2;
        }
        return sum;
    }
}