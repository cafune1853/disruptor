/*
 * Copyright 2011 LMAX Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.lmax.disruptor;

import com.lmax.disruptor.util.Util;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 由于Disruptor设计中,使用广播机制,因此多个消费者默认是重复消费队列元素的.该类用于实现多个消费者不重复消费队列元素.
 * 该实现主要是通过多个并行的多个consumer共享一个Sequence({@link WorkerPool#workSequence}),然后每个消费者({@link WorkProcessor})
 * 通过workSequence每次竞争一个队列元素,每个消费者同样会持有自身的一个Sequence,通过自身的Sequence与生产者的Sequencer协调工作
 * workSequence不和生产者进行任何的协调.Sequence直接使用Sequencer的SequencerBarrier, Sequencer直接使用每个消费者的Sequence作为gatingSequences.
 * 具体的协调过程可见:{@link WorkerPool#WorkerPool(com.lmax.disruptor.EventFactory, com.lmax.disruptor.ExceptionHandler, com.lmax.disruptor.WorkHandler[])}
 * 以及{@link WorkProcessor#run()}
 */
public final class WorkerPool<T>
{
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final Sequence workSequence = new Sequence(Sequencer.INITIAL_CURSOR_VALUE);
    private final RingBuffer<T> ringBuffer;
    // WorkProcessors are created to wrap each of the provided WorkHandlers
    private final WorkProcessor<?>[] workProcessors;

    /**
     * Create a worker pool to enable an array of {@link WorkHandler}s to consume published sequences.
     * <p>
     * This option requires a pre-configured {@link RingBuffer} which must have {@link RingBuffer#addGatingSequences(Sequence...)}
     * called before the work pool is started.
     *
     * @param ringBuffer       of events to be consumed.
     * @param sequenceBarrier  on which the workers will depend.
     * @param exceptionHandler to callback when an error occurs which is not handled by the {@link WorkHandler}s.
     * @param workHandlers     to distribute the work load across.
     */
    public WorkerPool(
        final RingBuffer<T> ringBuffer,
        final SequenceBarrier sequenceBarrier,
        final ExceptionHandler<? super T> exceptionHandler,
        final WorkHandler<? super T>... workHandlers)
    {
        this.ringBuffer = ringBuffer;
        final int numWorkers = workHandlers.length;
        workProcessors = new WorkProcessor[numWorkers];

        for (int i = 0; i < numWorkers; i++)
        {
            workProcessors[i] = new WorkProcessor<T>(
                ringBuffer,
                sequenceBarrier,
                workHandlers[i],
                exceptionHandler,
                workSequence);
        }
    }

    /**
     * Construct a work pool with an internal {@link RingBuffer} for convenience.
     * <p>
     * This option does not require {@link RingBuffer#addGatingSequences(Sequence...)} to be called before the work pool is started.
     *
     * @param eventFactory     for filling the {@link RingBuffer}
     * @param exceptionHandler to callback when an error occurs which is not handled by the {@link WorkHandler}s.
     * @param workHandlers     to distribute the work load across.
     */
    public WorkerPool(
        final EventFactory<T> eventFactory,
        final ExceptionHandler<? super T> exceptionHandler,
        final WorkHandler<? super T>... workHandlers)
    {
        ringBuffer = RingBuffer.createMultiProducer(eventFactory, 1024, new BlockingWaitStrategy());
        final SequenceBarrier barrier = ringBuffer.newBarrier();
        final int numWorkers = workHandlers.length;
        workProcessors = new WorkProcessor[numWorkers];

        for (int i = 0; i < numWorkers; i++)
        {
            workProcessors[i] = new WorkProcessor<T>(
                ringBuffer,
                barrier,
                workHandlers[i],
                exceptionHandler,
                workSequence);
        }

        ringBuffer.addGatingSequences(getWorkerSequences());
    }

    /**
     * Get an array of {@link Sequence}s representing the progress of the workers.
     *
     * @return an array of {@link Sequence}s representing the progress of the workers.
     */
    public Sequence[] getWorkerSequences()
    {
        final Sequence[] sequences = new Sequence[workProcessors.length + 1];
        for (int i = 0, size = workProcessors.length; i < size; i++)
        {
            sequences[i] = workProcessors[i].getSequence();
        }
        sequences[sequences.length - 1] = workSequence;

        return sequences;
    }

    /**
     * Start the worker pool processing events in sequence.
     *
     * @param executor providing threads for running the workers.
     * @return the {@link RingBuffer} used for the work queue.
     * @throws IllegalStateException if the pool has already been started and not halted yet
     */
    public RingBuffer<T> start(final Executor executor)
    {
        if (!started.compareAndSet(false, true))
        {
            throw new IllegalStateException("WorkerPool has already been started and cannot be restarted until halted.");
        }

        final long cursor = ringBuffer.getCursor();
        workSequence.set(cursor);

        for (WorkProcessor<?> processor : workProcessors)
        {
            processor.getSequence().set(cursor);
            executor.execute(processor);
        }

        return ringBuffer;
    }

    /**
     * Wait for the {@link RingBuffer} to drain of published events then halt the workers.
     */
    public void drainAndHalt()
    {
        Sequence[] workerSequences = getWorkerSequences();
        while (ringBuffer.getCursor() > Util.getMinimumSequence(workerSequences))
        {
            Thread.yield();
        }

        for (WorkProcessor<?> processor : workProcessors)
        {
            processor.halt();
        }

        started.set(false);
    }

    /**
     * Halt all workers immediately at the end of their current cycle.
     */
    public void halt()
    {
        for (WorkProcessor<?> processor : workProcessors)
        {
            processor.halt();
        }

        started.set(false);
    }

    public boolean isRunning()
    {
        return started.get();
    }
}
