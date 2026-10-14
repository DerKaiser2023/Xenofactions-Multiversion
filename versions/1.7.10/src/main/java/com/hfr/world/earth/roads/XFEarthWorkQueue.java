package com.hfr.world.earth.roads;

import com.hfr.config.XFConfig;
import java.util.concurrent.*;

/** Shared geographic generation/import and claim-terrain budget, drained only by the owning server tick. */
public final class XFEarthWorkQueue {
    public interface Step { boolean run() throws Exception; }
    private static final ConcurrentLinkedQueue<Task> queue=new ConcurrentLinkedQueue<Task>();
    private static final class Task {final Step step;final CompletableFuture<Void> done=new CompletableFuture<Void>();Task(Step step){this.step=step;}}
    private XFEarthWorkQueue(){}
    public static CompletableFuture<Void> submit(Step step){Task task=new Task(step);if(queue.size()>=64)task.done.completeExceptionally(new IllegalStateException("Earth work queue is full"));else queue.add(task);return task.done;}
    public static void tick(){
        long end=System.nanoTime()+XFConfig.earthRoadTickMillis*1000000L;int left=XFConfig.earthRoadStepsPerTick;
        while(left-->0&&System.nanoTime()<end){Task task=queue.poll();if(task==null)return;if(task.done.isDone())continue;
            try{if(task.step.run())task.done.complete(null);else queue.add(task);}catch(Exception e){task.done.completeExceptionally(e);}}
    }
    public static void clear(){Task task;while((task=queue.poll())!=null)task.done.completeExceptionally(new InterruptedException("Earth world/session ended"));}
}
