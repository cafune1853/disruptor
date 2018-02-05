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

### 已更新的类
Sequence  
SingleProducerSequencer
WorkerPool  
WorkProcessor