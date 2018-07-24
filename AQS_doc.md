- 为实现依赖于先进先出 (`FIFO`) 等待队列的阻塞锁和相关同步器（信号量、事件，等等）提供一个框架。此类的设计目标是成为依靠单个原子 `int` 
值来表示状态的大多数同步器的一个有用基础。子类必须定义更改此状态的受保护方法，并定义哪种状态对于此对象意味着被获取或被释放。假定这些条件
之后，此类中的其他方法就可以实现所有排队和阻塞机制。子类可以维护其他状态字段，但只是为了获得同步而只追踪使用 `getState()`、
`setState(int)` 和 `compareAndSetState(int, int)` 方法来操作以原子方式更新的 `int` 值。

- 应该将子类定义为非公共内部帮助器类，可用它们来实现其封闭类的同步属性。类 `AbstractQueuedSynchronizer` 没有实现任何同步接口。而是定
义了诸如 `acquireInterruptibly(int)` 之类的一些方法，在适当的时候可以通过具体的锁和相关同步器来调用它们，以实现其公共方法。

- 此类支持默认的独占模式和共享模式之一，或者二者都支持。处于独占模式下时，其他线程试图获取该锁将无法取得成功。在共享模式下，多个线程获取
某个锁可能（但不是一定）会获得成功。此类并不“了解”这些不同，除了机械地意识到当在共享模式下成功获取某一锁时，下一个等待线程（如果存在）也
必须确定自己是否可以成功获取该锁。处于不同模式下的等待线程可以共享相同的 `FIFO` 队列。通常，实现子类只支持其中一种模式，但两种模式都可以
在（例如）`ReadWriteLock` 中发挥作用。只支持独占模式或者只支持共享模式的子类不必定义支持未使用模式的方法。

- 此类通过支持独占模式的子类定义了一个嵌套的 `AbstractQueuedSynchronizer.ConditionObject` 类，可以将这个类用作 `Condition` 实现。
`isHeldExclusively()` 方法将报告同步对于当前线程是否是独占的；使用当前 `getState()` 值调用 `release(int)` 方法则可以完全释放此对
象；如果给定保存的状态值，那么 `acquire(int)` 方法可以将此对象最终恢复为它以前获取的状态。没有别的 `AbstractQueuedSynchronizer` 方
法创建这样的条件，因此，如果无法满足此约束，则不要使用它。`AbstractQueuedSynchronizer.ConditionObject` 的行为当然取决于其同步器实现
的语义。

- 此类为内部队列提供了检查、检测和监视方法，还为 `condition` 对象提供了类似方法。可以根据需要使用用于其同步机制的 
`AbstractQueuedSynchronizer` 将这些方法导出到类中。

- 此类的序列化只存储维护状态的基础原子整数，因此已序列化的对象拥有空的线程队列。需要可序列化的典型子类将定义一个 `readObject` 方法，该
方法在反序列化时将此对象恢复到某个已知初始状态。

使用

- 为了将此类用作同步器的基础，需要适当地重新定义以下方法，这是通过使用 `getState()`、`setState(int)` 和/或 
`compareAndSetState(int, int)` 方法来检查和/或修改同步状态来实现的：

  - tryAcquire(int)
  - tryRelease(int)
  - tryAcquireShared(int)
  - tryReleaseShared(int)
  - isHeldExclusively()
  
- 默认情况下，每个方法都抛出 `UnsupportedOperationException`。这些方法的实现在内部必须是线程安全的，通常应该很短并且不被阻塞。定义这
些方法是使用此类的 唯一 受支持的方式。其他所有方法都被声明为 `final`，因为它们无法是各不相同的。您也可以查找从 
`AbstractOwnableSynchronizer` 继承的方法，用于跟踪拥有独占同步器的线程。鼓励使用这些方法，这允许监控和诊断工具来帮助用户确定哪个线程
保持锁。

- 即使此类基于内部的某个 `FIFO` 队列，它也无法强行实施 `FIFO` 获取策略。独占同步的核心采用以下形式：

  - Acquire:
     ```java
        while (!tryAcquire(arg)) {
            enqueue thread if it is not already queued;
            possibly block current thread;
        }
     ```

  - Release:
     ```java
        if (tryRelease(arg))
           unblock the first queued thread;
     ``` 
 
（共享模式与此类似，但可能涉及级联信号。）

- 因为要在加入队列之前检查线程的获取状况，所以新获取的线程可能闯入 其他被阻塞的和已加入队列的线程之前。不过如果需要，可以内部调用一个或多
个检查方法，通过定义 `tryAcquire` 和/或 `tryAcquireShared` 来禁用闯入。特别是 `getFirstQueuedThread()` 没有返回当前线程的时候，
严格的 `FIFO` 锁定可以定义 `tryAcquire` 立即返回 `false`。只有 `hasQueuedThreads()` 返回 `true` 并且 `getFirstQueuedThread` 
不是当前线程时，更好的非严格公平的版本才可能会立即返回 `false`；如果 `getFirstQueuedThread` 不为 `null` 并且不是当前线程，则产生的
结果相同。出现进一步的变体也是有可能的。

- 对于默认闯入（也称为 `greedy`、`renouncement` 和 `convoy-avoidance`）策略，吞吐量和可伸缩性通常是最高的。尽管无法保证这是公平的或
是无偏向的，但允许更早加入队列的线程先于更迟加入队列的线程再次争用资源，并且相对于传入的线程，每个参与再争用的线程都有平等的成功机会。此
外，尽管从一般意义上说，获取并非“自旋”，它们可以在阻塞之前对用其他计算所使用的 `tryAcquire` 执行多次调用。在只保持独占同步时，这为自旋
提供了最大的好处，但不是这种情况时，也不会带来最大的负担。如果需要这样做，那么可以使用“快速路径”检查来先行调用 `acquire` 方法，以这种方
式扩充这一点，如果可能不需要争用同步器，则只能通过预先检查 `hasContended()` 和/或 `hasQueuedThreads()` 来确认这一点。

- 通过特殊化其同步器的使用范围，此类为部分同步化提供了一个有效且可伸缩的基础，同步器可以依赖于 `int` 型的 `state`、`acquire` 和 
`release` 参数，以及一个内部的 `FIFO` 等待队列。这些还不够的时候，可以使用 `atomic` 类、自己的定制 `Queue` 类和 `LockSupport` 阻
塞支持，从更低级别构建同步器。

使用示例

- 以下是一个非再进入的互斥锁类，它使用值 `0` 表示未锁定状态，使用 `1` 表示锁定状态。当非重入锁定不严格地需要当前拥有者线程的记录时，此
类使得使用监视器更加方便。它还支持一些条件并公开了一个检测方法：

```java
 class Mutex implements Lock, java.io.Serializable {

    // Our internal helper class
    private static class Sync extends AbstractQueuedSynchronizer {
      // Report whether in locked state
      protected boolean isHeldExclusively() { 
        return getState() == 1; 
      }

      // Acquire the lock if state is zero
      public boolean tryAcquire(int acquires) {
        assert acquires == 1; // Otherwise unused
       if (compareAndSetState(0, 1)) {
         setExclusiveOwnerThread(Thread.currentThread());
         return true;
       }
       return false;
      }

      // Release the lock by setting state to zero
      protected boolean tryRelease(int releases) {
        assert releases == 1; // Otherwise unused
        if (getState() == 0) throw new IllegalMonitorStateException();
        setExclusiveOwnerThread(null);
        setState(0);
        return true;
      }
       
      // Provide a Condition
      Condition newCondition() { return new ConditionObject(); }

      // Deserialize properly
      private void readObject(ObjectInputStream s) 
        throws IOException, ClassNotFoundException {
        s.defaultReadObject();
        setState(0); // reset to unlocked state
      }
    }

    // The sync object does all the hard work. We just forward to it.
    private final Sync sync = new Sync();

    public void lock()                { sync.acquire(1); }
    public boolean tryLock()          { return sync.tryAcquire(1); }
    public void unlock()              { sync.release(1); }
    public Condition newCondition()   { return sync.newCondition(); }
    public boolean isLocked()         { return sync.isHeldExclusively(); }
    public boolean hasQueuedThreads() { return sync.hasQueuedThreads(); }
    public void lockInterruptibly() throws InterruptedException { 
      sync.acquireInterruptibly(1);
    }
    public boolean tryLock(long timeout, TimeUnit unit) 
        throws InterruptedException {
      return sync.tryAcquireNanos(1, unit.toNanos(timeout));
    }
 }
```
  
以下是一个锁存器类，它类似于 `CountDownLatch`，除了只需要触发单个 `signal` 之外。因为锁存器是非独占的，所以它使用 `shared` 的获取和
释放方法。

```java
 class BooleanLatch {

    private static class Sync extends AbstractQueuedSynchronizer {
      boolean isSignalled() { return getState() != 0; }

      protected int tryAcquireShared(int ignore) {
        return isSignalled()? 1 : -1;
      }
        
      protected boolean tryReleaseShared(int ignore) {
        setState(1);
        return true;
      }
    }

    private final Sync sync = new Sync();
    public boolean isSignalled() { return sync.isSignalled(); }
    public void signal()         { sync.releaseShared(1); }
    public void await() throws InterruptedException {
      sync.acquireSharedInterruptibly(1);
    }
 }
```

#

等待队列节点类。

- 等待队列是“`CLH`”（`Craig`，`Landin` 和 `Hagersten`）锁定队列的变体。`CLH` 锁通常用于自旋锁。我们使用它们来阻止同步器，但是使用相
同的基本策略来保存关于其节点的前驱中的线程的一些控制信息。每个节点中的“状态”字段跟踪线程是否应该阻塞。在其前任发布时，将发出节点信号。否
则队列的每个节点都用作保存单个等待线程的特定通知样式监视器。但是，`status` 字段不会控制线程是否被授予锁等。线程可能会尝试获取它是否在队
列中的第一个。但首先并不能保证成功;它只给予抗争的权利。因此，当前发布的竞争者线程可能需要重新审视。

- 要排入 `CLH` 锁定，您将其原子拼接为新尾部。要出列，您只需设置头部字段即可。

```
      +------+  prev +-----+       +-----+
 head |      | <---- |     | <---- |     |  tail
      +------+       +-----+       +-----+
```

- 插入 `CLH` 队列只需要对“尾部”进行单个原子操作，因此存在从未排队到排队的简单原子点划分。同样，出列只涉及更新“头部”。但是，节点需要更多
的工作来确定他们的继任者是谁，部分是为了处理由于超时和中断而可能的取消。

- “`prev`”链接（未在原始 `CLH` 锁中使用）主要用于处理取消。如果节点被取消，则其后继者（通常）重新链接到未取消的前任。有关自旋锁的类似
机制的解释，请参阅Scott和Scherer的论文，网址为http://www.cs.rochester.edu/u/scott/synchronization/

- 我们还使用“`next`”链接来实现阻塞机制。每个节点的线程 `id` 保存在自己的节点中，因此前驱者通过遍历下一个链接来通知下一个节点以确定它是
哪个线程。后继者的确定必须避免使用新排队节点的比赛来设置其前任的“下一个”字段。 必要时，当节点的后继者看起来为空时，通过从原子更新的“尾
部”向后检查来解决这个问题。 （或者，换句话说，下一个链接是一个优化，所以我们通常不需要后向扫描。）

- 取消为基本算法带来了一些保守性。由于我们必须轮询取消其他节点，我们可能会忽略被取消的节点是在我们前面还是在我们后面。这是通过取消后始终
取消停车的继承人来处理的，这使得他们能够稳定在新的前任上，除非我们能够确定一位将承担此责任的未经撤销的前任。

- `CLH` 队列需要一个虚拟标头节点才能启动。但是我们不会在构造上创建它们，因为如果没有争用就会浪费精力。相反，构造节点并在第一次争用时设置
头尾指针。

- 等待条件的线程使用相同的节点，但使用其他链接。条件只需要链接简单（非并发）链接队列中的节点，因为它们仅在完全保持时才被访问。等待时，将
节点插入条件队列。根据信号，节点被转移到主队列。状态字段的特殊值用于标记节点所在的队列。

```java
static final class Node {
    /** 标记表示节点正在共享模式中等待 */
    static final Node SHARED = new Node();
    /** 标记表示节点正在独占模式下等待 */
    static final Node EXCLUSIVE = null;

    /** waitStatus 值表示线程已取消 */
    static final int CANCELLED =  1;
    /** waitStatus 值表示后继者的线程需要取消停放 */
    static final int SIGNAL    = -1;
    /** waitStatus 值表示线程正在等待条件 */
    static final int CONDITION = -2;
    /**
     *  waitStatus 值表示下一个 acquireShared 应无条件传播
     */
    static final int PROPAGATE = -3;

    /**
     *  状态字段，仅接受值：
     *    SIGNAL: 此节点的后继是（或将很快）被阻止（通过驻留），因此当前节点在释放或取消时必须取消其后继。 为避免竞争，获取方法必须首
     *            先指示它们需要信号，然后重试原子获取，然后在失败时阻止。
     *            
     *    CANCELED：由于超时或中断，此节点被取消。 节点永远不会离开这个状态。 特别是，具有已取消节点的线程永远不会再次阻塞。
     *    
     *    CONDITION: 此节点当前处于条件队列中。在传输之前，它不会用作同步队列节点，此时状态将设置为0.（此处使用此值与字段的其他用法无
     *               关，但可简化机制。）
     *               
     *    PROPAGATE: releaseShared 应该传播到其他节点。在 doReleaseShared 中设置（仅限头节点）以确保继续传播，即使其他操作已经介
     *               入。
     *               
     *    0: 以上都不是                    
     *    
     *    这些值以数字方式排列以简化使用。非负值意味着节点不需要发信号。因此，大多数代码不需要检查特定值，仅用于符号。
     *    对于正常的同步节点，该字段初始化为 0，对于条件节点，该字段初始化为 CONDITION。它使用CAS（或可能的情况下，无条件的易失性写
     *    入）进行修改。
     */
    volatile int waitStatus;

    /**
     *  链接到当前节点/线程依赖的前导节点以检查 waitStatus。在入队时分配，并且仅在出列时才为了（为了 GC 而）。此外，在取消前任时，
     *  我们在找到未取消的一个时短路，这将永远存在，因为头节点永远不会被取消：节点由于成功获取而变为仅头。取消的线程永远不会成功获取，
     *  并且线程仅取消自身，而不取消任何其他节点。
     */
    volatile Node prev;

    /**
     *  链接到当前节点/线程在释放时取消驻留的后继节点。在排队期间分配，在绕过取消的前任时进行调整，并在出列时排除（为了 GC ）。enq 操
     *  作直到附加后才分配前任的下一个字段，因此查看 null next 字段并不一定意味着该节点位于队列的末尾。但是，如果下一个字段看起来为空，
     *  我们可以从尾部扫描 prev's 进行仔细检查。 已取消节点的下一个字段设置为指向节点本身而不是 null，以使 isOnSyncQueue 的生活更轻
     *  松。 
     */
    volatile Node next;

    /**
     *  排队此节点的线程。 在施工时初始化并在使用后消失。
     */
    volatile Thread thread;

    /**
     *  链接到等待条件的下一个节点，或特殊值 SHARED。因为条件队列只有在保持独占模式时才被访问，所以我们只需要一个简单的链接队列来在节点
     *  等待条件时保存节点。然后将它们转移到队列中以重新获取。并且因为条件只能是独占的，所以我们通过使用特殊值来指示共享模式来保存字段。 
     */
    Node nextWaiter;

    /**
     *  如果节点在共享模式下等待，则返回 true。
     */
    final boolean isShared() {
        return nextWaiter == SHARED;
    }

    /**
     *  返回上一个节点，如果为 null，则抛出 NullPointerException。当前导者不能为 null 时使用。可以省略空检查，但是存在以帮助 VM。
     *
     * @return 此节点的前身
     */
    final Node predecessor() throws NullPointerException {
        Node p = prev;
        if (p == null)
            throw new NullPointerException();
        else
            return p;
    }

    Node() {    // 用于建立初始头或 SHARED 标记
    }

    Node(Thread thread, Node mode) {     // 由 addWaiter 使用
        this.nextWaiter = mode;
        this.thread = thread;
    }

    Node(Thread thread, int waitStatus) { // 由条件使用
        this.waitStatus = waitStatus;
        this.thread = thread;
    }
}
```

