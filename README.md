# java-concurrent-programming-explain

## [Demo](module1/src/edu/maskleo/module1)

## Thread.join()

- 子线程执行完成才执行主线程
- [ThreadJoinTest](module1/src/edu/maskleo/module1/ThreadJoinTest.java)


## CountDownLatch

- 所有子线程完成了阻塞 `count` 计数之后就开始调用主线程的 `latch.await()` 之后的方法
- [CountDownLatchTest](module1/src/edu/maskleo/module1/CyclicBarrierTest.java)

## CyclicBarrier

- 子线程完成等待之后，调用指定的线程执行,[示例](module1/src/edu/maskleo/module1/CyclicBarrierTest.java),一个类完成上述功能,[相关示例](module1/src/edu/maskleo/module1/CyclicBarrierTest2.java).

## CountDownLatch & CyclicBarrier

- `CountDownLatch` 和 `CyclicBarrier` 都能够实现线程之间的等待，只不过它们侧重点不同：
- `CountDownLatch` 一般用于某个线程A等待若干个其他线程执行完任务之后，它才执行；
  
- 而 `CyclicBarrier` 一般用于一组线程互相等待至某个状态，然后这一组线程再同时执行；
  
- 另外，`CountDownLatch` 是不能够重用的，而 `CyclicBarrier` 是可以重用的。

## Semaphore

- `Semaphore` 其实和锁有点类似，它一般用于控制对某组资源的访问权限。
- 初始化时设置大小, 线程每次获取和存放回去都有数量可选。

## ForkJoin

- 讲任务分割最后汇集结果,类似排序中的归并

## 資料

- [The j.u.c Synchronizer Framework中文翻译版](http://ifeve.com/aqs/)

- [《The java.util.concurrent Synchronizer Framework》 JUC同步器框架（AQS框架）原文翻译](http://www.cnblogs.com/dennyzhangdd/p/7218510.html)

- [AQS解析(1)](https://ryan-hou.github.io/2018/06/12/AQS%E8%A7%A3%E6%9E%90-1/)

- [AQS解析(2)](https://ryan-hou.github.io/2018/06/13/AQS%E8%A7%A3%E6%9E%90-2/)

- [CountDownLatch、CyclicBarrier和 Semaphore](http://www.importnew.com/21889.html)

- [Fork/Join框架介绍](http://ifeve.com/talk-concurrency-forkjoin/)

## LICENSE

### [CC-BY-SA-3.0](https://creativecommons.org/licenses/by-nc-sa/3.0/cn/)

[![](LICENSE.png)](https://creativecommons.org/licenses/by-nc-sa/3.0/cn/)