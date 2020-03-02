## Zookeeper实现的分布式锁

zookeeper是一个分布式协调中间件，既可以用来做分布式的id还可以用来做配置中心、还有我们接下来要说的分布式锁。
在说分布式锁之前要先介绍一下zookeeper，它是树形结构，每个节点都可以存储值和创建子节点。而zookeeper的节点一共分为四种分别是临时节点，持久化节点，临时有序节点，持久化有序节点。

### Zookeeper节点类型

- 临时节点
顾名思义，临时的节点，有个需要注意的是当客户端与服务端的会话关闭的时候，临时节点就会被自动删除掉

- 持久化节点
这个跟临时节点的区别就是，就算会话关闭，节点也不会被关闭。

- 临时有序节点
这个跟临时节点唯一的区别就是，创建的节点是有序的例如上一个节点创建为`/00000000001`,那下一个节点就是`/00000000002`，依次递增

- 持久化有序节点
这个跟临时有序节点的区别也是会话就算关闭，创建的节点也不会被删除掉

### 分布式锁的实现原理
多个节点需要共享一个数据或文件，节点之间同时竞争获取执行权限，当其中某个节点获取到执行权限以后，另一个就必须等待占有执行权限的执行完成并将执行权限释放出来，只要能满足条件互斥就能实现分布式锁，比如数据库的锁表，唯一索引，Redis的`setnx`函数，还有我们刚才讲的zookeeper的临时有序节点的。

### 临时节点如何创建分布式锁

我们先看个图，然后根据下面这个图来讲

![分布式锁](https://upload-images.jianshu.io/upload_images/14511933-9cb3a9103288f6b4.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

- 问题抛出

现在有两个服务节点Node1和Node2,两个节点需要同时操作共享资源中的某个文件，Node1节点需要修改age的值为18，Node2节点
需要将该值修改为19，我们知道文件在修改的时候都是只能被一个线程操作，不能收到干扰，该如何实现这个功能呢？

- 问题解决
双方在执行顺序僵持不下，这个时候我们就需要请一个比较有说服力的第三方来协调双方的问题，这个第三方就是我们的zookeeper，zookeeper对这些节点说，我们为了公平起见，你们要执行之前，都先来我这里登记一下，拿一个号牌（`临时有序节点名`），来的越早号牌就越小，我们在执行的时候拿号牌说话，号牌小的先执行，执行完以后，号牌就失效，如果下次还要来执行，又要重新登记拿号牌，这样你们总公平吧，为了解决你们的问题，当上一个节点执行完了以后，我还特意找了一个人（`Watch机制`）来专门通知下一个节点，到你执行啦。

这样zookeeper就为我们解决了多节点之间资源竞争的问题，在执行贡献资源之前，我们通过zookeeper的client去创建一个临时有序节点，然后节点内部会不断进行节点比较，看看自己是不是最小的，如果是最小的（`/00000000002`）获得到锁，如果不是最小的(`/00000000003`)则通过Watch机制去监听自己的上一个节点，当上一个节点执行完以后，节点被删除，Watch就会主动推送信息给当前节点`/00000000003`，告诉client你前面的节点已经执行完了，现在你可以执行啦。

## 代码实现

```pom
        <!-- 连接zookeeper -->
        <dependency>
            <groupId>org.apache.curator</groupId>
            <artifactId>curator-framework</artifactId>
            <version>4.2.0</version>
        </dependency>
        <!-- 操作分布式锁 -->
        <dependency>
            <groupId>org.apache.curator</groupId>
            <artifactId>curator-recipes</artifactId>
            <version>4.2.0</version>
        </dependency>
```
> 我们通过多线程来模拟多个节点的情况

```java
    public static void main(String[] args) {
        CuratorFramework curatorFramework = CuratorFrameworkFactory.newClient("120.78.149.247:2181",
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
```


