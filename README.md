## LMAX Disruptor

本项目基于[disruptor](https://github.com/LMAX-Exchange/disruptor) 3.3.7 源码.   
对于其中重要的类做了中文逻辑注释,便于源码的快速阅读.

### disruptor是什么
disruptor是一个并发消息队列，可用于实现生产者/消费者模式。disruptor有点类似于ArrayBlockingQueue，
但是disruptor生产的消息是被广播给所有消费者的，一个事件会被多个消费者重复消费，这是disruptor与ArrayBlockingQueue功能上最大的区别。
当然disruptor也可以借助WorkerPool来实现一个事件只被一个消费者消费的效果，但在disruptor中不是推荐方式。
 
disruptor是十分高效的，其高效的原因主要来自以下两个方面：
 * 使用volatile + CAS进行同步，避免了使用锁带来的线程阻塞、线程切换等性能问题
 * 使用了缓存行补齐来避免了缓存的伪共享（false sharing）
 
具体更多的介绍可以参见美团的[这篇文章](https://tech.meituan.com/disruptor.html)

### disruptor的整体架构以及组件
了解disruptor的整体架构，有助于我们从宏观上了解整个框架并能够使我们注重于核心组件的阅读。
这里借用disruptor源项目中的逻辑组件图，对disruptor整体框架进行一个介绍。
![](https://raw.githubusercontent.com/wiki/LMAX-Exchange/disruptor/images/Models.png)     
其中共用组件是Sequence,其作用类似于AtomicLong,用于状态同步.其有一个具体实现类SequenceGroup,用于聚合一组Sequence,
并取这组Sequence中的最小值作为整个SequenceGroup的最小值.     

生产者组件的是Sequencer, Sequencer有两个实现类--SingleProducerSequencer与MultiProducerSequencer,
分别对应了单生产者以及多生产者的场景.两种实现都会持有一个Sequence对象用于竞争可写节点.Sequencer会持有所有
Consumer的Sequence用于判断一个位置是否可写.     

消费者组件则是SequenceBarrier以及EventHandler, 一个SequenceBarrier会持有一个Sequencer以及其他消费者的Sequence,
用于等待队列中的一个位置可读.EventHandler则是单纯的接口,定义了消费者的行为.EventHandlerGroup则用于聚集一组相互之间没有
依赖关系的Consumer,其他Consumer可以依赖于这组EventHandlerGroup中Sequence最小的那个消费者.如图中JournalConsumer以及
ReplicationConsumer,相互之间没有依赖关系,可以形成一个EventHandlerGroup(持有两个Sequence).
ApplicationConsumer则必须等到JournalConsumer以及ReplicationConsumer都消费完之后,才能消费元素,
因此直接依赖于整个EventHandlerGroup.     

### 已更新的类
Sequence  
SingleProducerSequencer
WorkerPool  
WorkProcessor