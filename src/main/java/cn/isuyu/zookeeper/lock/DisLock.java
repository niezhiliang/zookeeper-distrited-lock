package cn.isuyu.zookeeper.lock;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.ExponentialBackoffRetry;

public class DisLock {

    public static void main(String[] args) {
        CuratorFramework curatorFramework = CuratorFrameworkFactory.newClient("127.0.0.1:2181",
                5000,4000,new ExponentialBackoffRetry(1000,3));
        //获取一个客户端连接
        curatorFramework.start();
        //创建一个临时有序节点
        InterProcessMutex lock = new InterProcessMutex(curatorFramework,"/locks");

        for (int i = 0; i < 20; i++){
            new Thread(()->{
                    System.out.println(Thread.currentThread().getName()+"--->尝试获取锁");
                    try {
                        //尝试获取锁，如果没有获取到就会阻塞一直阻塞 知道获取锁成功
                        lock.acquire();
                        System.out.println(Thread.currentThread().getName()+"--->获取锁成功");
                        //占用锁两秒钟
                        Thread.sleep(2000);
                        //释放锁
                        lock.release();
                        System.out.println(Thread.currentThread().getName()+"--->释放锁成功");
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
            },"T"+i).start();
        }
    }
}
