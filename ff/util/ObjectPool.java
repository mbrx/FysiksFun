package mbrx.ff.util;

import java.util.Collection;
import java.util.LinkedList;
import java.util.Set;

/**
 * Contains object pools for various classes in FF, intended to be as light
 * weight as possible while still thread safe.
 */
public class ObjectPool<ObjectType> {

  public static ObjectPool<CoordinateWXYZ> poolCoordinateWXYZ;
  public static ObjectPool<CoordinateWXZ>  poolCoordinateWXZ;
  public static ObjectPool<ChunkMarkUpdateTask>  poolChunkMarkUpdateTask;

  
  private LinkedList<ObjectType>           freeObjects;
  private int                              objectCount;
  private int                              maxObjectCount;
  private ObjectAllocator<ObjectType>      allocator;

  private static abstract class ObjectAllocator<OT> {
    abstract OT allocate();
  }

  public static void init() {
    ObjectAllocator<CoordinateWXYZ> coordinateWXYZAllocator = new ObjectAllocator<CoordinateWXYZ>() {
      CoordinateWXYZ allocate() {
        return new CoordinateWXYZ();
      }
    };
    poolCoordinateWXYZ = new ObjectPool<CoordinateWXYZ>(100000, coordinateWXYZAllocator);

    ObjectAllocator<CoordinateWXZ> coordinateWXZAllocator = new ObjectAllocator<CoordinateWXZ>() {
      CoordinateWXZ allocate() {
        return new CoordinateWXZ();
      }
    };
    poolCoordinateWXZ = new ObjectPool<CoordinateWXZ>(10000, coordinateWXZAllocator);

    ObjectAllocator<ChunkMarkUpdateTask> chunkMarkUpdateTaskAllocator = new ObjectAllocator<ChunkMarkUpdateTask>() {
      ChunkMarkUpdateTask allocate() {
        return new ChunkMarkUpdateTask();
      }
    };
    poolChunkMarkUpdateTask = new ObjectPool<ChunkMarkUpdateTask>(10000, chunkMarkUpdateTaskAllocator);
   
  }

  public ObjectPool(int maxObjects, ObjectAllocator<ObjectType> allocator) {
    freeObjects = new LinkedList<ObjectType>();
    objectCount = 0;
    maxObjectCount = maxObjects;
    this.allocator = allocator;
  }

  public synchronized ObjectType getObject() {
    if (objectCount > 0) {
      objectCount--;
      return freeObjects.pop();
    } else return allocator.allocate();
  }

  public synchronized void releaseObject(ObjectType object) {
    if (objectCount >= maxObjectCount) {} // Nothing to do, let GC grab it
    else {
      objectCount++;
      freeObjects.push(object);
    }
  }
  /** May put more objects into the pool than is allowed, but this is a performance trade-off for my specific application */
  public synchronized void releaseAll(Collection<ObjectType> objects) {
    objectCount+=objects.size();
    freeObjects.addAll(objects);
  }
}
