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

```java
    /**
     * 等待队列的负责人，懒初始化。 除初始化外，它仅通过方法 setHead 进行修改。 注意：如果 head 存在，则保证其 waitStatus 不被取消
     */
    private transient volatile Node head;

    /**
     * 等待队列的尾部，懒洋洋地初始化。 仅通过方法enq修改以添加新的等待节点。
     */
    private transient volatile Node tail;

    /**
     * 同步状态。
     */
    private volatile int state;

    /**
     * 返回同步状态的当前值。此操作具有{@code volatile}读取的内存语义。
     * @return current state value
     */
    protected final int getState() {
        return state;
    }

    /**
     * 设置同步状态的值。此操作具有{@code volatile}写入的内存语义。
     * @param newState the new state value
     */
    protected final void setState(int newState) {
        state = newState;
    }

    /**
     * 如果当前状态值等于预期值，则以原子方式将同步状态设置为给定的更新值。
     * 此操作具有{@code volatile}读取和写入的内存语义。
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} 如果成功的话 错误返回表示实际值不等于预期值。
     */
    protected final boolean compareAndSetState(int expect, int update) {
        // 请参阅下面的内在设置以支持此功能
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
    }

    // 排队实用程序

    /**
     * 旋转速度快而不是使用定时停车的纳秒数。粗略的估计足以通过非常短的超时来提高响应能力。
     */
    static final long spinForTimeoutThreshold = 1000L;

    /**
     * 将节点插入队列，必要时进行初始化。 见上图。
     * @param node the node to insert
     * @return node's predecessor
     */
    private Node enq(final Node node) {
        for (;;) {
            Node t = tail;
            if (t == null) { // 必须初始化
                if (compareAndSetHead(new Node()))
                    tail = head;
            } else {
                node.prev = t;
                if (compareAndSetTail(t, node)) {
                    t.next = node;
                    return t;
                }
            }
        }
    }

    /**
     * 为当前线程和给定模式创建并排队节点。
     * @param mode Node.EXCLUSIVE用于独占，Node.SHARED用于共享
     * @return the new node
     */
    private Node addWaiter(Node mode) {
        Node node = new Node(Thread.currentThread(), mode);
        // 尝试enq的快速路径; 失败时备份到完整enq
        Node pred = tail;
        if (pred != null) {
            node.prev = pred;
            if (compareAndSetTail(pred, node)) {
                pred.next = node;
                return node;
            }
        }
        enq(node);
        return node;
    }

    /**
     * 将队列头设置为节点，从而出列。 仅通过获取方法调用。 同时为了 GC 而使未使用的字段为空，并抑制不必要的信号和遍历。
     * @param node the node
     */
    private void setHead(Node node) {
        head = node;
        node.thread = null;
        node.prev = null;
    }

    /**
     * 唤醒节点的后继（如果存在）。
     * @param node the node
     */
    private void unparkSuccessor(Node node) {
        /*
         * 如果状态为负（即，可能需要信号），则尝试清除预期信令。 如果失败或者等待线程改变了状态，则可以。  
         */
        int ws = node.waitStatus;
        if (ws < 0)
            compareAndSetWaitStatus(node, ws, 0);

        /*
         * 线程到 unpark 是在后继节点，通常只是下一个节点。但是如果取消或显然为空，则从尾部向后移动以找到实际未取消的继任者。 
         */
        Node s = node.next;
        if (s == null || s.waitStatus > 0) {
            s = null;
            for (Node t = tail; t != null && t != node; t = t.prev)
                if (t.waitStatus <= 0)
                    s = t;
        }
        if (s != null)
            LockSupport.unpark(s.thread);
    }

    /**
     * 共享模式的释放操作 - 发出后续信号并确保传播。 （注意：对于独占模式，如果需要信号，释放只会调用 head 的 unparkSuccessor。） 
    */
    private void doReleaseShared() {
        /*
         * 即使存在其他正在进行的获取/释放，也要确保发布传播。 如果它需要信号，这通常会尝试取消停止头部的接入者。 但如果没有，则将状态设置为 PROPAGAT E以确保在释放时继续传播。
         * 此外，我们必须循环，以防我们这样做时添加新节点。 此外，与 unparkSuccessor 的其他用法不同，我们需要知道 CAS 重置状态是否失败，如果是这样，则重新检查。 
         */
        for (;;) {
            Node h = head;
            if (h != null && h != tail) {
                int ws = h.waitStatus;
                if (ws == Node.SIGNAL) {
                    if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                        continue;            // 循环以重新检查案例
                    unparkSuccessor(h);
                }
                else if (ws == 0 &&
                        !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                    continue;                // 循环失败的 CAS
            }
            if (h == head)                   // 如果头改变了循环
                break;
        }
    }

    /**
     * 设置队列头，并检查后继者是否可能在共享模式下等待，如果是传播，则设置 propagate  > 0 或 PROPAGATE 状态。
     * @param node the node
     * @param propagate tryAcquireShared的返回值
     */
    private void setHeadAndPropagate(Node node, int propagate) {
        Node h = head; // 记录下方检查旧头部
        setHead(node);
        /*
         * 如果以下情况尝试发信号通知下一个排队节点：传播由调用者指示，或者由前一个操作记录（在 setHead 之前或之后为 h.waitStatus）（注意：这使用 waitStatus 的符号检查，因为 PROPAGATE 状态可能转换为 SIGNAL。）并且下一个节点正在共享模式中等待，或者我们不知道，因为它看起来是空的     
        *   
        * 这两项检查中的保守性都可能导致不必要的唤醒，但只有在有多次赛车获取/释放时，大多数人现在或很快就需要信号。
        */
        if (propagate > 0 || h == null || h.waitStatus < 0 ||
            (h = head) == null || h.waitStatus < 0) {
            Node s = node.next;
            if (s == null || s.isShared())
                doReleaseShared();
        }
    }

// Utilities for various versions of acquire

/**
 * Cancels an ongoing attempt to acquire.
 *
 * @param node the node
 */
private void cancelAcquire(Node node) {
    // Ignore if node doesn't exist
    if (node == null)
        return;

    node.thread = null;

    // Skip cancelled predecessors
    Node pred = node.prev;
    while (pred.waitStatus > 0)
        node.prev = pred = pred.prev;

    // predNext is the apparent node to unsplice. CASes below will
    // fail if not, in which case, we lost race vs another cancel
    // or signal, so no further action is necessary.
    Node predNext = pred.next;

    // Can use unconditional write instead of CAS here.
    // After this atomic step, other Nodes can skip past us.
    // Before, we are free of interference from other threads.
    node.waitStatus = Node.CANCELLED;

    // If we are the tail, remove ourselves.
    if (node == tail && compareAndSetTail(node, pred)) {
        compareAndSetNext(pred, predNext, null);
    } else {
        // If successor needs signal, try to set pred's next-link
        // so it will get one. Otherwise wake it up to propagate.
        int ws;
        if (pred != head &&
            ((ws = pred.waitStatus) == Node.SIGNAL ||
             (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) &&
            pred.thread != null) {
            Node next = node.next;
            if (next != null && next.waitStatus <= 0)
                compareAndSetNext(pred, predNext, next);
        } else {
            unparkSuccessor(node);
        }

        node.next = node; // help GC
    }
}

/**
 * Checks and updates status for a node that failed to acquire.
 * Returns true if thread should block. This is the main signal
 * control in all acquire loops.  Requires that pred == node.prev.
 *
 * @param pred node's predecessor holding status
 * @param node the node
 * @return {@code true} if thread should block
 */
private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
    int ws = pred.waitStatus;
    if (ws == Node.SIGNAL)
        /*
         * This node has already set status asking a release
         * to signal it, so it can safely park.
         */
        return true;
    if (ws > 0) {
        /*
         * Predecessor was cancelled. Skip over predecessors and
         * indicate retry.
         */
        do {
            node.prev = pred = pred.prev;
        } while (pred.waitStatus > 0);
        pred.next = node;
    } else {
        /*
         * waitStatus must be 0 or PROPAGATE.  Indicate that we
         * need a signal, but don't park yet.  Caller will need to
         * retry to make sure it cannot acquire before parking.
         */
        compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
    }
    return false;
}

/**
 * Convenience method to interrupt current thread.
 */
static void selfInterrupt() {
    Thread.currentThread().interrupt();
}

/**
 * Convenience method to park and then check if interrupted
 *
 * @return {@code true} if interrupted
 */
private final boolean parkAndCheckInterrupt() {
    LockSupport.park(this);
    return Thread.interrupted();
}

/*
 * Various flavors of acquire, varying in exclusive/shared and
 * control modes.  Each is mostly the same, but annoyingly
 * different.  Only a little bit of factoring is possible due to
 * interactions of exception mechanics (including ensuring that we
 * cancel if tryAcquire throws exception) and other control, at
 * least not without hurting performance too much.
 */

/**
 * Acquires in exclusive uninterruptible mode for thread already in
 * queue. Used by condition wait methods as well as acquire.
 *
 * @param node the node
 * @param arg the acquire argument
 * @return {@code true} if interrupted while waiting
 */
final boolean acquireQueued(final Node node, int arg) {
    boolean failed = true;
    try {
        boolean interrupted = false;
        for (;;) {
            final Node p = node.predecessor();
            if (p == head && tryAcquire(arg)) {
                setHead(node);
                p.next = null; // help GC
                failed = false;
                return interrupted;
            }
            if (shouldParkAfterFailedAcquire(p, node) &&
                parkAndCheckInterrupt())
                interrupted = true;
        }
    } finally {
        if (failed)
            cancelAcquire(node);
    }
}

/**
 * Acquires in exclusive interruptible mode.
 * @param arg the acquire argument
 */
private void doAcquireInterruptibly(int arg)
    throws InterruptedException {
    final Node node = addWaiter(Node.EXCLUSIVE);
    boolean failed = true;
    try {
        for (;;) {
            final Node p = node.predecessor();
            if (p == head && tryAcquire(arg)) {
                setHead(node);
                p.next = null; // help GC
                failed = false;
                return;
            }
            if (shouldParkAfterFailedAcquire(p, node) &&
                parkAndCheckInterrupt())
                throw new InterruptedException();
        }
    } finally {
        if (failed)
            cancelAcquire(node);
    }
}

/**
 * Acquires in exclusive timed mode.
 *
 * @param arg the acquire argument
 * @param nanosTimeout max wait time
 * @return {@code true} if acquired
 */
private boolean doAcquireNanos(int arg, long nanosTimeout)
        throws InterruptedException {
    if (nanosTimeout <= 0L)
        return false;
    final long deadline = System.nanoTime() + nanosTimeout;
    final Node node = addWaiter(Node.EXCLUSIVE);
    boolean failed = true;
    try {
        for (;;) {
            final Node p = node.predecessor();
            if (p == head && tryAcquire(arg)) {
                setHead(node);
                p.next = null; // help GC
                failed = false;
                return true;
            }
            nanosTimeout = deadline - System.nanoTime();
            if (nanosTimeout <= 0L)
                return false;
            if (shouldParkAfterFailedAcquire(p, node) &&
                nanosTimeout > spinForTimeoutThreshold)
                LockSupport.parkNanos(this, nanosTimeout);
            if (Thread.interrupted())
                throw new InterruptedException();
        }
    } finally {
        if (failed)
            cancelAcquire(node);
    }
}

/**
 * Acquires in shared uninterruptible mode.
 * @param arg the acquire argument
 */
private void doAcquireShared(int arg) {
    final Node node = addWaiter(Node.SHARED);
    boolean failed = true;
    try {
        boolean interrupted = false;
        for (;;) {
            final Node p = node.predecessor();
            if (p == head) {
                int r = tryAcquireShared(arg);
                if (r >= 0) {
                    setHeadAndPropagate(node, r);
                    p.next = null; // help GC
                    if (interrupted)
                        selfInterrupt();
                    failed = false;
                    return;
                }
            }
            if (shouldParkAfterFailedAcquire(p, node) &&
                parkAndCheckInterrupt())
                interrupted = true;
        }
    } finally {
        if (failed)
            cancelAcquire(node);
    }
}

/**
 * Acquires in shared interruptible mode.
 * @param arg the acquire argument
 */
private void doAcquireSharedInterruptibly(int arg)
    throws InterruptedException {
    final Node node = addWaiter(Node.SHARED);
    boolean failed = true;
    try {
        for (;;) {
            final Node p = node.predecessor();
            if (p == head) {
                int r = tryAcquireShared(arg);
                if (r >= 0) {
                    setHeadAndPropagate(node, r);
                    p.next = null; // help GC
                    failed = false;
                    return;
                }
            }
            if (shouldParkAfterFailedAcquire(p, node) &&
                parkAndCheckInterrupt())
                throw new InterruptedException();
        }
    } finally {
        if (failed)
            cancelAcquire(node);
    }
}

/**
 * Acquires in shared timed mode.
 *
 * @param arg the acquire argument
 * @param nanosTimeout max wait time
 * @return {@code true} if acquired
 */
private boolean doAcquireSharedNanos(int arg, long nanosTimeout)
        throws InterruptedException {
    if (nanosTimeout <= 0L)
        return false;
    final long deadline = System.nanoTime() + nanosTimeout;
    final Node node = addWaiter(Node.SHARED);
    boolean failed = true;
    try {
        for (;;) {
            final Node p = node.predecessor();
            if (p == head) {
                int r = tryAcquireShared(arg);
                if (r >= 0) {
                    setHeadAndPropagate(node, r);
                    p.next = null; // help GC
                    failed = false;
                    return true;
                }
            }
            nanosTimeout = deadline - System.nanoTime();
            if (nanosTimeout <= 0L)
                return false;
            if (shouldParkAfterFailedAcquire(p, node) &&
                nanosTimeout > spinForTimeoutThreshold)
                LockSupport.parkNanos(this, nanosTimeout);
            if (Thread.interrupted())
                throw new InterruptedException();
        }
    } finally {
        if (failed)
            cancelAcquire(node);
    }
}

// Main exported methods

/**
 * Attempts to acquire in exclusive mode. This method should query
 * if the state of the object permits it to be acquired in the
 * exclusive mode, and if so to acquire it.
 *
 * <p>This method is always invoked by the thread performing
 * acquire.  If this method reports failure, the acquire method
 * may queue the thread, if it is not already queued, until it is
 * signalled by a release from some other thread. This can be used
 * to implement method {@link Lock#tryLock()}.
 *
 * <p>The default
 * implementation throws {@link UnsupportedOperationException}.
 *
 * @param arg the acquire argument. This value is always the one
 *        passed to an acquire method, or is the value saved on entry
 *        to a condition wait.  The value is otherwise uninterpreted
 *        and can represent anything you like.
 * @return {@code true} if successful. Upon success, this object has
 *         been acquired.
 * @throws IllegalMonitorStateException if acquiring would place this
 *         synchronizer in an illegal state. This exception must be
 *         thrown in a consistent fashion for synchronization to work
 *         correctly.
 * @throws UnsupportedOperationException if exclusive mode is not supported
 */
protected boolean tryAcquire(int arg) {
    throw new UnsupportedOperationException();
}

/**
 * Attempts to set the state to reflect a release in exclusive
 * mode.
 *
 * <p>This method is always invoked by the thread performing release.
 *
 * <p>The default implementation throws
 * {@link UnsupportedOperationException}.
 *
 * @param arg the release argument. This value is always the one
 *        passed to a release method, or the current state value upon
 *        entry to a condition wait.  The value is otherwise
 *        uninterpreted and can represent anything you like.
 * @return {@code true} if this object is now in a fully released
 *         state, so that any waiting threads may attempt to acquire;
 *         and {@code false} otherwise.
 * @throws IllegalMonitorStateException if releasing would place this
 *         synchronizer in an illegal state. This exception must be
 *         thrown in a consistent fashion for synchronization to work
 *         correctly.
 * @throws UnsupportedOperationException if exclusive mode is not supported
 */
protected boolean tryRelease(int arg) {
    throw new UnsupportedOperationException();
}

/**
 * Attempts to acquire in shared mode. This method should query if
 * the state of the object permits it to be acquired in the shared
 * mode, and if so to acquire it.
 *
 * <p>This method is always invoked by the thread performing
 * acquire.  If this method reports failure, the acquire method
 * may queue the thread, if it is not already queued, until it is
 * signalled by a release from some other thread.
 *
 * <p>The default implementation throws {@link
 * UnsupportedOperationException}.
 *
 * @param arg the acquire argument. This value is always the one
 *        passed to an acquire method, or is the value saved on entry
 *        to a condition wait.  The value is otherwise uninterpreted
 *        and can represent anything you like.
 * @return a negative value on failure; zero if acquisition in shared
 *         mode succeeded but no subsequent shared-mode acquire can
 *         succeed; and a positive value if acquisition in shared
 *         mode succeeded and subsequent shared-mode acquires might
 *         also succeed, in which case a subsequent waiting thread
 *         must check availability. (Support for three different
 *         return values enables this method to be used in contexts
 *         where acquires only sometimes act exclusively.)  Upon
 *         success, this object has been acquired.
 * @throws IllegalMonitorStateException if acquiring would place this
 *         synchronizer in an illegal state. This exception must be
 *         thrown in a consistent fashion for synchronization to work
 *         correctly.
 * @throws UnsupportedOperationException if shared mode is not supported
 */
protected int tryAcquireShared(int arg) {
    throw new UnsupportedOperationException();
}

/**
 * Attempts to set the state to reflect a release in shared mode.
 *
 * <p>This method is always invoked by the thread performing release.
 *
 * <p>The default implementation throws
 * {@link UnsupportedOperationException}.
 *
 * @param arg the release argument. This value is always the one
 *        passed to a release method, or the current state value upon
 *        entry to a condition wait.  The value is otherwise
 *        uninterpreted and can represent anything you like.
 * @return {@code true} if this release of shared mode may permit a
 *         waiting acquire (shared or exclusive) to succeed; and
 *         {@code false} otherwise
 * @throws IllegalMonitorStateException if releasing would place this
 *         synchronizer in an illegal state. This exception must be
 *         thrown in a consistent fashion for synchronization to work
 *         correctly.
 * @throws UnsupportedOperationException if shared mode is not supported
 */
protected boolean tryReleaseShared(int arg) {
    throw new UnsupportedOperationException();
}

/**
 * Returns {@code true} if synchronization is held exclusively with
 * respect to the current (calling) thread.  This method is invoked
 * upon each call to a non-waiting {@link ConditionObject} method.
 * (Waiting methods instead invoke {@link #release}.)
 *
 * <p>The default implementation throws {@link
 * UnsupportedOperationException}. This method is invoked
 * internally only within {@link ConditionObject} methods, so need
 * not be defined if conditions are not used.
 *
 * @return {@code true} if synchronization is held exclusively;
 *         {@code false} otherwise
 * @throws UnsupportedOperationException if conditions are not supported
 */
protected boolean isHeldExclusively() {
    throw new UnsupportedOperationException();
}

/**
 * Acquires in exclusive mode, ignoring interrupts.  Implemented
 * by invoking at least once {@link #tryAcquire},
 * returning on success.  Otherwise the thread is queued, possibly
 * repeatedly blocking and unblocking, invoking {@link
 * #tryAcquire} until success.  This method can be used
 * to implement method {@link Lock#lock}.
 *
 * @param arg the acquire argument.  This value is conveyed to
 *        {@link #tryAcquire} but is otherwise uninterpreted and
 *        can represent anything you like.
 */
public final void acquire(int arg) {
    if (!tryAcquire(arg) &&
        acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
        selfInterrupt();
}

/**
 * Acquires in exclusive mode, aborting if interrupted.
 * Implemented by first checking interrupt status, then invoking
 * at least once {@link #tryAcquire}, returning on
 * success.  Otherwise the thread is queued, possibly repeatedly
 * blocking and unblocking, invoking {@link #tryAcquire}
 * until success or the thread is interrupted.  This method can be
 * used to implement method {@link Lock#lockInterruptibly}.
 *
 * @param arg the acquire argument.  This value is conveyed to
 *        {@link #tryAcquire} but is otherwise uninterpreted and
 *        can represent anything you like.
 * @throws InterruptedException if the current thread is interrupted
 */
public final void acquireInterruptibly(int arg)
        throws InterruptedException {
    if (Thread.interrupted())
        throw new InterruptedException();
    if (!tryAcquire(arg))
        doAcquireInterruptibly(arg);
}

/**
 * Attempts to acquire in exclusive mode, aborting if interrupted,
 * and failing if the given timeout elapses.  Implemented by first
 * checking interrupt status, then invoking at least once {@link
 * #tryAcquire}, returning on success.  Otherwise, the thread is
 * queued, possibly repeatedly blocking and unblocking, invoking
 * {@link #tryAcquire} until success or the thread is interrupted
 * or the timeout elapses.  This method can be used to implement
 * method {@link Lock#tryLock(long, TimeUnit)}.
 *
 * @param arg the acquire argument.  This value is conveyed to
 *        {@link #tryAcquire} but is otherwise uninterpreted and
 *        can represent anything you like.
 * @param nanosTimeout the maximum number of nanoseconds to wait
 * @return {@code true} if acquired; {@code false} if timed out
 * @throws InterruptedException if the current thread is interrupted
 */
public final boolean tryAcquireNanos(int arg, long nanosTimeout)
        throws InterruptedException {
    if (Thread.interrupted())
        throw new InterruptedException();
    return tryAcquire(arg) ||
        doAcquireNanos(arg, nanosTimeout);
}

/**
 * Releases in exclusive mode.  Implemented by unblocking one or
 * more threads if {@link #tryRelease} returns true.
 * This method can be used to implement method {@link Lock#unlock}.
 *
 * @param arg the release argument.  This value is conveyed to
 *        {@link #tryRelease} but is otherwise uninterpreted and
 *        can represent anything you like.
 * @return the value returned from {@link #tryRelease}
 */
public final boolean release(int arg) {
    if (tryRelease(arg)) {
        Node h = head;
        if (h != null && h.waitStatus != 0)
            unparkSuccessor(h);
        return true;
    }
    return false;
}

/**
 * Acquires in shared mode, ignoring interrupts.  Implemented by
 * first invoking at least once {@link #tryAcquireShared},
 * returning on success.  Otherwise the thread is queued, possibly
 * repeatedly blocking and unblocking, invoking {@link
 * #tryAcquireShared} until success.
 *
 * @param arg the acquire argument.  This value is conveyed to
 *        {@link #tryAcquireShared} but is otherwise uninterpreted
 *        and can represent anything you like.
 */
public final void acquireShared(int arg) {
    if (tryAcquireShared(arg) < 0)
        doAcquireShared(arg);
}

/**
 * Acquires in shared mode, aborting if interrupted.  Implemented
 * by first checking interrupt status, then invoking at least once
 * {@link #tryAcquireShared}, returning on success.  Otherwise the
 * thread is queued, possibly repeatedly blocking and unblocking,
 * invoking {@link #tryAcquireShared} until success or the thread
 * is interrupted.
 * @param arg the acquire argument.
 * This value is conveyed to {@link #tryAcquireShared} but is
 * otherwise uninterpreted and can represent anything
 * you like.
 * @throws InterruptedException if the current thread is interrupted
 */
public final void acquireSharedInterruptibly(int arg)
        throws InterruptedException {
    if (Thread.interrupted())
        throw new InterruptedException();
    if (tryAcquireShared(arg) < 0)
        doAcquireSharedInterruptibly(arg);
}

/**
 * Attempts to acquire in shared mode, aborting if interrupted, and
 * failing if the given timeout elapses.  Implemented by first
 * checking interrupt status, then invoking at least once {@link
 * #tryAcquireShared}, returning on success.  Otherwise, the
 * thread is queued, possibly repeatedly blocking and unblocking,
 * invoking {@link #tryAcquireShared} until success or the thread
 * is interrupted or the timeout elapses.
 *
 * @param arg the acquire argument.  This value is conveyed to
 *        {@link #tryAcquireShared} but is otherwise uninterpreted
 *        and can represent anything you like.
 * @param nanosTimeout the maximum number of nanoseconds to wait
 * @return {@code true} if acquired; {@code false} if timed out
 * @throws InterruptedException if the current thread is interrupted
 */
public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout)
        throws InterruptedException {
    if (Thread.interrupted())
        throw new InterruptedException();
    return tryAcquireShared(arg) >= 0 ||
        doAcquireSharedNanos(arg, nanosTimeout);
}

/**
 * Releases in shared mode.  Implemented by unblocking one or more
 * threads if {@link #tryReleaseShared} returns true.
 *
 * @param arg the release argument.  This value is conveyed to
 *        {@link #tryReleaseShared} but is otherwise uninterpreted
 *        and can represent anything you like.
 * @return the value returned from {@link #tryReleaseShared}
 */
public final boolean releaseShared(int arg) {
    if (tryReleaseShared(arg)) {
        doReleaseShared();
        return true;
    }
    return false;
}

// Queue inspection methods

/**
 * Queries whether any threads are waiting to acquire. Note that
 * because cancellations due to interrupts and timeouts may occur
 * at any time, a {@code true} return does not guarantee that any
 * other thread will ever acquire.
 *
 * <p>In this implementation, this operation returns in
 * constant time.
 *
 * @return {@code true} if there may be other threads waiting to acquire
 */
public final boolean hasQueuedThreads() {
    return head != tail;
}

/**
 * Queries whether any threads have ever contended to acquire this
 * synchronizer; that is if an acquire method has ever blocked.
 *
 * <p>In this implementation, this operation returns in
 * constant time.
 *
 * @return {@code true} if there has ever been contention
 */
public final boolean hasContended() {
    return head != null;
}

/**
 * Returns the first (longest-waiting) thread in the queue, or
 * {@code null} if no threads are currently queued.
 *
 * <p>In this implementation, this operation normally returns in
 * constant time, but may iterate upon contention if other threads are
 * concurrently modifying the queue.
 *
 * @return the first (longest-waiting) thread in the queue, or
 *         {@code null} if no threads are currently queued
 */
public final Thread getFirstQueuedThread() {
    // handle only fast path, else relay
    return (head == tail) ? null : fullGetFirstQueuedThread();
}

/**
 * Version of getFirstQueuedThread called when fastpath fails
 */
private Thread fullGetFirstQueuedThread() {
    /*
     * The first node is normally head.next. Try to get its
     * thread field, ensuring consistent reads: If thread
     * field is nulled out or s.prev is no longer head, then
     * some other thread(s) concurrently performed setHead in
     * between some of our reads. We try this twice before
     * resorting to traversal.
     */
    Node h, s;
    Thread st;
    if (((h = head) != null && (s = h.next) != null &&
         s.prev == head && (st = s.thread) != null) ||
        ((h = head) != null && (s = h.next) != null &&
         s.prev == head && (st = s.thread) != null))
        return st;

    /*
     * Head's next field might not have been set yet, or may have
     * been unset after setHead. So we must check to see if tail
     * is actually first node. If not, we continue on, safely
     * traversing from tail back to head to find first,
     * guaranteeing termination.
     */

    Node t = tail;
    Thread firstThread = null;
    while (t != null && t != head) {
        Thread tt = t.thread;
        if (tt != null)
            firstThread = tt;
        t = t.prev;
    }
    return firstThread;
}

/**
 * Returns true if the given thread is currently queued.
 *
 * <p>This implementation traverses the queue to determine
 * presence of the given thread.
 *
 * @param thread the thread
 * @return {@code true} if the given thread is on the queue
 * @throws NullPointerException if the thread is null
 */
public final boolean isQueued(Thread thread) {
    if (thread == null)
        throw new NullPointerException();
    for (Node p = tail; p != null; p = p.prev)
        if (p.thread == thread)
            return true;
    return false;
}

/**
 * Returns {@code true} if the apparent first queued thread, if one
 * exists, is waiting in exclusive mode.  If this method returns
 * {@code true}, and the current thread is attempting to acquire in
 * shared mode (that is, this method is invoked from {@link
 * #tryAcquireShared}) then it is guaranteed that the current thread
 * is not the first queued thread.  Used only as a heuristic in
 * ReentrantReadWriteLock.
 */
final boolean apparentlyFirstQueuedIsExclusive() {
    Node h, s;
    return (h = head) != null &&
        (s = h.next)  != null &&
        !s.isShared()         &&
        s.thread != null;
}

/**
 * Queries whether any threads have been waiting to acquire longer
 * than the current thread.
 *
 * <p>An invocation of this method is equivalent to (but may be
 * more efficient than):
 *  <pre> {@code
 * getFirstQueuedThread() != Thread.currentThread() &&
 * hasQueuedThreads()}</pre>
 *
 * <p>Note that because cancellations due to interrupts and
 * timeouts may occur at any time, a {@code true} return does not
 * guarantee that some other thread will acquire before the current
 * thread.  Likewise, it is possible for another thread to win a
 * race to enqueue after this method has returned {@code false},
 * due to the queue being empty.
 *
 * <p>This method is designed to be used by a fair synchronizer to
 * avoid <a href="AbstractQueuedSynchronizer#barging">barging</a>.
 * Such a synchronizer's {@link #tryAcquire} method should return
 * {@code false}, and its {@link #tryAcquireShared} method should
 * return a negative value, if this method returns {@code true}
 * (unless this is a reentrant acquire).  For example, the {@code
 * tryAcquire} method for a fair, reentrant, exclusive mode
 * synchronizer might look like this:
 *
 *  <pre> {@code
 * protected boolean tryAcquire(int arg) {
 *   if (isHeldExclusively()) {
 *     // A reentrant acquire; increment hold count
 *     return true;
 *   } else if (hasQueuedPredecessors()) {
 *     return false;
 *   } else {
 *     // try to acquire normally
 *   }
 * }}</pre>
 *
 * @return {@code true} if there is a queued thread preceding the
 *         current thread, and {@code false} if the current thread
 *         is at the head of the queue or the queue is empty
 * @since 1.7
 */
public final boolean hasQueuedPredecessors() {
    // The correctness of this depends on head being initialized
    // before tail and on head.next being accurate if the current
    // thread is first in queue.
    Node t = tail; // Read fields in reverse initialization order
    Node h = head;
    Node s;
    return h != t &&
        ((s = h.next) == null || s.thread != Thread.currentThread());
}


// Instrumentation and monitoring methods

/**
 * Returns an estimate of the number of threads waiting to
 * acquire.  The value is only an estimate because the number of
 * threads may change dynamically while this method traverses
 * internal data structures.  This method is designed for use in
 * monitoring system state, not for synchronization
 * control.
 *
 * @return the estimated number of threads waiting to acquire
 */
public final int getQueueLength() {
    int n = 0;
    for (Node p = tail; p != null; p = p.prev) {
        if (p.thread != null)
            ++n;
    }
    return n;
}

/**
 * Returns a collection containing threads that may be waiting to
 * acquire.  Because the actual set of threads may change
 * dynamically while constructing this result, the returned
 * collection is only a best-effort estimate.  The elements of the
 * returned collection are in no particular order.  This method is
 * designed to facilitate construction of subclasses that provide
 * more extensive monitoring facilities.
 *
 * @return the collection of threads
 */
public final Collection<Thread> getQueuedThreads() {
    ArrayList<Thread> list = new ArrayList<Thread>();
    for (Node p = tail; p != null; p = p.prev) {
        Thread t = p.thread;
        if (t != null)
            list.add(t);
    }
    return list;
}

/**
 * Returns a collection containing threads that may be waiting to
 * acquire in exclusive mode. This has the same properties
 * as {@link #getQueuedThreads} except that it only returns
 * those threads waiting due to an exclusive acquire.
 *
 * @return the collection of threads
 */
public final Collection<Thread> getExclusiveQueuedThreads() {
    ArrayList<Thread> list = new ArrayList<Thread>();
    for (Node p = tail; p != null; p = p.prev) {
        if (!p.isShared()) {
            Thread t = p.thread;
            if (t != null)
                list.add(t);
        }
    }
    return list;
}

/**
 * Returns a collection containing threads that may be waiting to
 * acquire in shared mode. This has the same properties
 * as {@link #getQueuedThreads} except that it only returns
 * those threads waiting due to a shared acquire.
 *
 * @return the collection of threads
 */
public final Collection<Thread> getSharedQueuedThreads() {
    ArrayList<Thread> list = new ArrayList<Thread>();
    for (Node p = tail; p != null; p = p.prev) {
        if (p.isShared()) {
            Thread t = p.thread;
            if (t != null)
                list.add(t);
        }
    }
    return list;
}

/**
 * Returns a string identifying this synchronizer, as well as its state.
 * The state, in brackets, includes the String {@code "State ="}
 * followed by the current value of {@link #getState}, and either
 * {@code "nonempty"} or {@code "empty"} depending on whether the
 * queue is empty.
 *
 * @return a string identifying this synchronizer, as well as its state
 */
public String toString() {
    int s = getState();
    String q  = hasQueuedThreads() ? "non" : "";
    return super.toString() +
        "[State = " + s + ", " + q + "empty queue]";
}


// Internal support methods for Conditions

/**
 * Returns true if a node, always one that was initially placed on
 * a condition queue, is now waiting to reacquire on sync queue.
 * @param node the node
 * @return true if is reacquiring
 */
final boolean isOnSyncQueue(Node node) {
    if (node.waitStatus == Node.CONDITION || node.prev == null)
        return false;
    if (node.next != null) // If has successor, it must be on queue
        return true;
    /*
     * node.prev can be non-null, but not yet on queue because
     * the CAS to place it on queue can fail. So we have to
     * traverse from tail to make sure it actually made it.  It
     * will always be near the tail in calls to this method, and
     * unless the CAS failed (which is unlikely), it will be
     * there, so we hardly ever traverse much.
     */
    return findNodeFromTail(node);
}

/**
 * Returns true if node is on sync queue by searching backwards from tail.
 * Called only when needed by isOnSyncQueue.
 * @return true if present
 */
private boolean findNodeFromTail(Node node) {
    Node t = tail;
    for (;;) {
        if (t == node)
            return true;
        if (t == null)
            return false;
        t = t.prev;
    }
}

/**
 * Transfers a node from a condition queue onto sync queue.
 * Returns true if successful.
 * @param node the node
 * @return true if successfully transferred (else the node was
 * cancelled before signal)
 */
final boolean transferForSignal(Node node) {
    /*
     * If cannot change waitStatus, the node has been cancelled.
     */
    if (!compareAndSetWaitStatus(node, Node.CONDITION, 0))
        return false;

    /*
     * Splice onto queue and try to set waitStatus of predecessor to
     * indicate that thread is (probably) waiting. If cancelled or
     * attempt to set waitStatus fails, wake up to resync (in which
     * case the waitStatus can be transiently and harmlessly wrong).
     */
    Node p = enq(node);
    int ws = p.waitStatus;
    if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL))
        LockSupport.unpark(node.thread);
    return true;
}

/**
 * Transfers node, if necessary, to sync queue after a cancelled wait.
 * Returns true if thread was cancelled before being signalled.
 *
 * @param node the node
 * @return true if cancelled before the node was signalled
 */
final boolean transferAfterCancelledWait(Node node) {
    if (compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
        enq(node);
        return true;
    }
    /*
     * If we lost out to a signal(), then we can't proceed
     * until it finishes its enq().  Cancelling during an
     * incomplete transfer is both rare and transient, so just
     * spin.
     */
    while (!isOnSyncQueue(node))
        Thread.yield();
    return false;
}

/**
 * Invokes release with current state value; returns saved state.
 * Cancels node and throws exception on failure.
 * @param node the condition node for this wait
 * @return previous sync state
 */
final int fullyRelease(Node node) {
    boolean failed = true;
    try {
        int savedState = getState();
        if (release(savedState)) {
            failed = false;
            return savedState;
        } else {
            throw new IllegalMonitorStateException();
        }
    } finally {
        if (failed)
            node.waitStatus = Node.CANCELLED;
    }
}

// Instrumentation methods for conditions

/**
 * Queries whether the given ConditionObject
 * uses this synchronizer as its lock.
 *
 * @param condition the condition
 * @return {@code true} if owned
 * @throws NullPointerException if the condition is null
 */
public final boolean owns(ConditionObject condition) {
    return condition.isOwnedBy(this);
}

/**
 * Queries whether any threads are waiting on the given condition
 * associated with this synchronizer. Note that because timeouts
 * and interrupts may occur at any time, a {@code true} return
 * does not guarantee that a future {@code signal} will awaken
 * any threads.  This method is designed primarily for use in
 * monitoring of the system state.
 *
 * @param condition the condition
 * @return {@code true} if there are any waiting threads
 * @throws IllegalMonitorStateException if exclusive synchronization
 *         is not held
 * @throws IllegalArgumentException if the given condition is
 *         not associated with this synchronizer
 * @throws NullPointerException if the condition is null
 */
public final boolean hasWaiters(ConditionObject condition) {
    if (!owns(condition))
        throw new IllegalArgumentException("Not owner");
    return condition.hasWaiters();
}

/**
 * Returns an estimate of the number of threads waiting on the
 * given condition associated with this synchronizer. Note that
 * because timeouts and interrupts may occur at any time, the
 * estimate serves only as an upper bound on the actual number of
 * waiters.  This method is designed for use in monitoring of the
 * system state, not for synchronization control.
 *
 * @param condition the condition
 * @return the estimated number of waiting threads
 * @throws IllegalMonitorStateException if exclusive synchronization
 *         is not held
 * @throws IllegalArgumentException if the given condition is
 *         not associated with this synchronizer
 * @throws NullPointerException if the condition is null
 */
public final int getWaitQueueLength(ConditionObject condition) {
    if (!owns(condition))
        throw new IllegalArgumentException("Not owner");
    return condition.getWaitQueueLength();
}

/**
 * Returns a collection containing those threads that may be
 * waiting on the given condition associated with this
 * synchronizer.  Because the actual set of threads may change
 * dynamically while constructing this result, the returned
 * collection is only a best-effort estimate. The elements of the
 * returned collection are in no particular order.
 *
 * @param condition the condition
 * @return the collection of threads
 * @throws IllegalMonitorStateException if exclusive synchronization
 *         is not held
 * @throws IllegalArgumentException if the given condition is
 *         not associated with this synchronizer
 * @throws NullPointerException if the condition is null
 */
public final Collection<Thread> getWaitingThreads(ConditionObject condition) {
    if (!owns(condition))
        throw new IllegalArgumentException("Not owner");
    return condition.getWaitingThreads();
}

/**
 * Condition implementation for a {@link
 * AbstractQueuedSynchronizer} serving as the basis of a {@link
 * Lock} implementation.
 *
 * <p>Method documentation for this class describes mechanics,
 * not behavioral specifications from the point of view of Lock
 * and Condition users. Exported versions of this class will in
 * general need to be accompanied by documentation describing
 * condition semantics that rely on those of the associated
 * {@code AbstractQueuedSynchronizer}.
 *
 * <p>This class is Serializable, but all fields are transient,
 * so deserialized conditions have no waiters.
 */
public class ConditionObject implements Condition, java.io.Serializable {
    private static final long serialVersionUID = 1173984872572414699L;
    /** First node of condition queue. */
    private transient Node firstWaiter;
    /** Last node of condition queue. */
    private transient Node lastWaiter;

    /**
     * Creates a new {@code ConditionObject} instance.
     */
    public ConditionObject() { }

    // Internal methods

    /**
     * Adds a new waiter to wait queue.
     * @return its new wait node
     */
    private Node addConditionWaiter() {
        Node t = lastWaiter;
        // If lastWaiter is cancelled, clean out.
        if (t != null && t.waitStatus != Node.CONDITION) {
            unlinkCancelledWaiters();
            t = lastWaiter;
        }
        Node node = new Node(Thread.currentThread(), Node.CONDITION);
        if (t == null)
            firstWaiter = node;
        else
            t.nextWaiter = node;
        lastWaiter = node;
        return node;
    }

    /**
     * Removes and transfers nodes until hit non-cancelled one or
     * null. Split out from signal in part to encourage compilers
     * to inline the case of no waiters.
     * @param first (non-null) the first node on condition queue
     */
    private void doSignal(Node first) {
        do {
            if ( (firstWaiter = first.nextWaiter) == null)
                lastWaiter = null;
            first.nextWaiter = null;
        } while (!transferForSignal(first) &&
                 (first = firstWaiter) != null);
    }

    /**
     * Removes and transfers all nodes.
     * @param first (non-null) the first node on condition queue
     */
    private void doSignalAll(Node first) {
        lastWaiter = firstWaiter = null;
        do {
            Node next = first.nextWaiter;
            first.nextWaiter = null;
            transferForSignal(first);
            first = next;
        } while (first != null);
    }

    /**
     * Unlinks cancelled waiter nodes from condition queue.
     * Called only while holding lock. This is called when
     * cancellation occurred during condition wait, and upon
     * insertion of a new waiter when lastWaiter is seen to have
     * been cancelled. This method is needed to avoid garbage
     * retention in the absence of signals. So even though it may
     * require a full traversal, it comes into play only when
     * timeouts or cancellations occur in the absence of
     * signals. It traverses all nodes rather than stopping at a
     * particular target to unlink all pointers to garbage nodes
     * without requiring many re-traversals during cancellation
     * storms.
     */
    private void unlinkCancelledWaiters() {
        Node t = firstWaiter;
        Node trail = null;
        while (t != null) {
            Node next = t.nextWaiter;
            if (t.waitStatus != Node.CONDITION) {
                t.nextWaiter = null;
                if (trail == null)
                    firstWaiter = next;
                else
                    trail.nextWaiter = next;
                if (next == null)
                    lastWaiter = trail;
            }
            else
                trail = t;
            t = next;
        }
    }

    // public methods

    /**
     * Moves the longest-waiting thread, if one exists, from the
     * wait queue for this condition to the wait queue for the
     * owning lock.
     *
     * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
     *         returns {@code false}
     */
    public final void signal() {
        if (!isHeldExclusively())
            throw new IllegalMonitorStateException();
        Node first = firstWaiter;
        if (first != null)
            doSignal(first);
    }

    /**
     * Moves all threads from the wait queue for this condition to
     * the wait queue for the owning lock.
     *
     * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
     *         returns {@code false}
     */
    public final void signalAll() {
        if (!isHeldExclusively())
            throw new IllegalMonitorStateException();
        Node first = firstWaiter;
        if (first != null)
            doSignalAll(first);
    }

    /**
     * Implements uninterruptible condition wait.
     * <ol>
     * <li> Save lock state returned by {@link #getState}.
     * <li> Invoke {@link #release} with saved state as argument,
     *      throwing IllegalMonitorStateException if it fails.
     * <li> Block until signalled.
     * <li> Reacquire by invoking specialized version of
     *      {@link #acquire} with saved state as argument.
     * </ol>
     */
    public final void awaitUninterruptibly() {
        Node node = addConditionWaiter();
        int savedState = fullyRelease(node);
        boolean interrupted = false;
        while (!isOnSyncQueue(node)) {
            LockSupport.park(this);
            if (Thread.interrupted())
                interrupted = true;
        }
        if (acquireQueued(node, savedState) || interrupted)
            selfInterrupt();
    }

    /*
     * For interruptible waits, we need to track whether to throw
     * InterruptedException, if interrupted while blocked on
     * condition, versus reinterrupt current thread, if
     * interrupted while blocked waiting to re-acquire.
     */

    /** Mode meaning to reinterrupt on exit from wait */
    private static final int REINTERRUPT =  1;
    /** Mode meaning to throw InterruptedException on exit from wait */
    private static final int THROW_IE    = -1;

    /**
     * Checks for interrupt, returning THROW_IE if interrupted
     * before signalled, REINTERRUPT if after signalled, or
     * 0 if not interrupted.
     */
    private int checkInterruptWhileWaiting(Node node) {
        return Thread.interrupted() ?
            (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) :
            0;
    }

    /**
     * Throws InterruptedException, reinterrupts current thread, or
     * does nothing, depending on mode.
     */
    private void reportInterruptAfterWait(int interruptMode)
        throws InterruptedException {
        if (interruptMode == THROW_IE)
            throw new InterruptedException();
        else if (interruptMode == REINTERRUPT)
            selfInterrupt();
    }

    /**
     * Implements interruptible condition wait.
     * <ol>
     * <li> If current thread is interrupted, throw InterruptedException.
     * <li> Save lock state returned by {@link #getState}.
     * <li> Invoke {@link #release} with saved state as argument,
     *      throwing IllegalMonitorStateException if it fails.
     * <li> Block until signalled or interrupted.
     * <li> Reacquire by invoking specialized version of
     *      {@link #acquire} with saved state as argument.
     * <li> If interrupted while blocked in step 4, throw InterruptedException.
     * </ol>
     */
    public final void await() throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        Node node = addConditionWaiter();
        int savedState = fullyRelease(node);
        int interruptMode = 0;
        while (!isOnSyncQueue(node)) {
            LockSupport.park(this);
            if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                break;
        }
        if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
            interruptMode = REINTERRUPT;
        if (node.nextWaiter != null) // clean up if cancelled
            unlinkCancelledWaiters();
        if (interruptMode != 0)
            reportInterruptAfterWait(interruptMode);
    }

    /**
     * Implements timed condition wait.
     * <ol>
     * <li> If current thread is interrupted, throw InterruptedException.
     * <li> Save lock state returned by {@link #getState}.
     * <li> Invoke {@link #release} with saved state as argument,
     *      throwing IllegalMonitorStateException if it fails.
     * <li> Block until signalled, interrupted, or timed out.
     * <li> Reacquire by invoking specialized version of
     *      {@link #acquire} with saved state as argument.
     * <li> If interrupted while blocked in step 4, throw InterruptedException.
     * </ol>
     */
    public final long awaitNanos(long nanosTimeout)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        Node node = addConditionWaiter();
        int savedState = fullyRelease(node);
        final long deadline = System.nanoTime() + nanosTimeout;
        int interruptMode = 0;
        while (!isOnSyncQueue(node)) {
            if (nanosTimeout <= 0L) {
                transferAfterCancelledWait(node);
                break;
            }
            if (nanosTimeout >= spinForTimeoutThreshold)
                LockSupport.parkNanos(this, nanosTimeout);
            if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                break;
            nanosTimeout = deadline - System.nanoTime();
        }
        if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
            interruptMode = REINTERRUPT;
        if (node.nextWaiter != null)
            unlinkCancelledWaiters();
        if (interruptMode != 0)
            reportInterruptAfterWait(interruptMode);
        return deadline - System.nanoTime();
    }

    /**
     * Implements absolute timed condition wait.
     * <ol>
     * <li> If current thread is interrupted, throw InterruptedException.
     * <li> Save lock state returned by {@link #getState}.
     * <li> Invoke {@link #release} with saved state as argument,
     *      throwing IllegalMonitorStateException if it fails.
     * <li> Block until signalled, interrupted, or timed out.
     * <li> Reacquire by invoking specialized version of
     *      {@link #acquire} with saved state as argument.
     * <li> If interrupted while blocked in step 4, throw InterruptedException.
     * <li> If timed out while blocked in step 4, return false, else true.
     * </ol>
     */
    public final boolean awaitUntil(Date deadline)
            throws InterruptedException {
        long abstime = deadline.getTime();
        if (Thread.interrupted())
            throw new InterruptedException();
        Node node = addConditionWaiter();
        int savedState = fullyRelease(node);
        boolean timedout = false;
        int interruptMode = 0;
        while (!isOnSyncQueue(node)) {
            if (System.currentTimeMillis() > abstime) {
                timedout = transferAfterCancelledWait(node);
                break;
            }
            LockSupport.parkUntil(this, abstime);
            if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                break;
        }
        if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
            interruptMode = REINTERRUPT;
        if (node.nextWaiter != null)
            unlinkCancelledWaiters();
        if (interruptMode != 0)
            reportInterruptAfterWait(interruptMode);
        return !timedout;
    }

    /**
     * Implements timed condition wait.
     * <ol>
     * <li> If current thread is interrupted, throw InterruptedException.
     * <li> Save lock state returned by {@link #getState}.
     * <li> Invoke {@link #release} with saved state as argument,
     *      throwing IllegalMonitorStateException if it fails.
     * <li> Block until signalled, interrupted, or timed out.
     * <li> Reacquire by invoking specialized version of
     *      {@link #acquire} with saved state as argument.
     * <li> If interrupted while blocked in step 4, throw InterruptedException.
     * <li> If timed out while blocked in step 4, return false, else true.
     * </ol>
     */
    public final boolean await(long time, TimeUnit unit)
            throws InterruptedException {
        long nanosTimeout = unit.toNanos(time);
        if (Thread.interrupted())
            throw new InterruptedException();
        Node node = addConditionWaiter();
        int savedState = fullyRelease(node);
        final long deadline = System.nanoTime() + nanosTimeout;
        boolean timedout = false;
        int interruptMode = 0;
        while (!isOnSyncQueue(node)) {
            if (nanosTimeout <= 0L) {
                timedout = transferAfterCancelledWait(node);
                break;
            }
            if (nanosTimeout >= spinForTimeoutThreshold)
                LockSupport.parkNanos(this, nanosTimeout);
            if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                break;
            nanosTimeout = deadline - System.nanoTime();
        }
        if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
            interruptMode = REINTERRUPT;
        if (node.nextWaiter != null)
            unlinkCancelledWaiters();
        if (interruptMode != 0)
            reportInterruptAfterWait(interruptMode);
        return !timedout;
    }

    //  support for instrumentation

    /**
     * Returns true if this condition was created by the given
     * synchronization object.
     *
     * @return {@code true} if owned
     */
    final boolean isOwnedBy(AbstractQueuedSynchronizer sync) {
        return sync == AbstractQueuedSynchronizer.this;
    }

    /**
     * Queries whether any threads are waiting on this condition.
     * Implements {@link AbstractQueuedSynchronizer#hasWaiters(ConditionObject)}.
     *
     * @return {@code true} if there are any waiting threads
     * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
     *         returns {@code false}
     */
    protected final boolean hasWaiters() {
        if (!isHeldExclusively())
            throw new IllegalMonitorStateException();
        for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
            if (w.waitStatus == Node.CONDITION)
                return true;
        }
        return false;
    }

    /**
     * Returns an estimate of the number of threads waiting on
     * this condition.
     * Implements {@link AbstractQueuedSynchronizer#getWaitQueueLength(ConditionObject)}.
     *
     * @return the estimated number of waiting threads
     * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
     *         returns {@code false}
     */
    protected final int getWaitQueueLength() {
        if (!isHeldExclusively())
            throw new IllegalMonitorStateException();
        int n = 0;
        for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
            if (w.waitStatus == Node.CONDITION)
                ++n;
        }
        return n;
    }

    /**
     * Returns a collection containing those threads that may be
     * waiting on this Condition.
     * Implements {@link AbstractQueuedSynchronizer#getWaitingThreads(ConditionObject)}.
     *
     * @return the collection of threads
     * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
     *         returns {@code false}
     */
    protected final Collection<Thread> getWaitingThreads() {
        if (!isHeldExclusively())
            throw new IllegalMonitorStateException();
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
            if (w.waitStatus == Node.CONDITION) {
                Thread t = w.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }
}

/**
 * Setup to support compareAndSet. We need to natively implement
 * this here: For the sake of permitting future enhancements, we
 * cannot explicitly subclass AtomicInteger, which would be
 * efficient and useful otherwise. So, as the lesser of evils, we
 * natively implement using hotspot intrinsics API. And while we
 * are at it, we do the same for other CASable fields (which could
 * otherwise be done with atomic field updaters).
 */
private static final Unsafe unsafe = Unsafe.getUnsafe();
private static final long stateOffset;
private static final long headOffset;
private static final long tailOffset;
private static final long waitStatusOffset;
private static final long nextOffset;

static {
    try {
        stateOffset = unsafe.objectFieldOffset
            (AbstractQueuedSynchronizer.class.getDeclaredField("state"));
        headOffset = unsafe.objectFieldOffset
            (AbstractQueuedSynchronizer.class.getDeclaredField("head"));
        tailOffset = unsafe.objectFieldOffset
            (AbstractQueuedSynchronizer.class.getDeclaredField("tail"));
        waitStatusOffset = unsafe.objectFieldOffset
            (Node.class.getDeclaredField("waitStatus"));
        nextOffset = unsafe.objectFieldOffset
            (Node.class.getDeclaredField("next"));

    } catch (Exception ex) { throw new Error(ex); }
}

/**
 * CAS head field. Used only by enq.
 */
private final boolean compareAndSetHead(Node update) {
    return unsafe.compareAndSwapObject(this, headOffset, null, update);
}

/**
 * CAS tail field. Used only by enq.
 */
private final boolean compareAndSetTail(Node expect, Node update) {
    return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
}

/**
 * CAS waitStatus field of a node.
 */
private static final boolean compareAndSetWaitStatus(Node node,
                                                     int expect,
                                                     int update) {
    return unsafe.compareAndSwapInt(node, waitStatusOffset,
                                    expect, update);
}

/**
 * CAS next field of a node.
 */
private static final boolean compareAndSetNext(Node node,
                                               Node expect,
                                               Node update) {
    return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
}
```

