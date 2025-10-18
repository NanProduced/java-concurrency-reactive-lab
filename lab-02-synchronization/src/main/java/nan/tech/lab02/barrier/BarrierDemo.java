package nan.tech.lab02.barrier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

/**
 * CyclicBarrier vs CountDownLatch 对比演示
 *
 * <p>【教学目标】</p>
 * <ol>
 *   <li>理解 CyclicBarrier 和 CountDownLatch 的异同</li>
 *   <li>掌握两者的使用场景和选择标准</li>
 *   <li>理解栅栏（Barrier）的工作原理</li>
 *   <li>学会处理超时和中断</li>
 * </ol>
 *
 * <p>【核心对比】</p>
 * <pre>
 * ┌─────────────────┬──────────────────────┬──────────────────────┐
 * │     特性        │   CountDownLatch     │    CyclicBarrier     │
 * ├─────────────────┼──────────────────────┼──────────────────────┤
 * │ 计数方向        │ 递减（N → 0）         │ 递增（0 → N）        │
 * │ 可重用性        │ ❌ 一次性             │ ✅ 可循环使用         │
 * │ 等待方式        │ await() 等待归零     │ await() 等待集齐      │
 * │ 触发动作        │ ❌ 无                 │ ✅ barrierAction     │
 * │ 典型场景        │ 主线程等待子任务     │ 多线程互相等待        │
 * └─────────────────┴──────────────────────┴──────────────────────┘
 *
 * CountDownLatch = 赛跑起跑枪
 *   所有选手就位 → 发令枪响 → 比赛开始
 *
 * CyclicBarrier = 旅游集合点
 *   所有游客到齐 → 继续前进 → 下一站再集合
 * </pre>
 *
 * <p>【决策树】</p>
 * <pre>
 * 需要重复使用？
 *   ├─ 是 → CyclicBarrier
 *   └─ 否 → 主线程等待多个子任务？
 *       ├─ 是 → CountDownLatch
 *       └─ 否 → 多线程互相等待？
 *           ├─ 是 → CyclicBarrier
 *           └─ 否 → 根据具体需求选择
 * </pre>
 *
 * @author nan
 * @since 2025-10-17
 */
public class BarrierDemo {

    private static final Logger logger = LoggerFactory.getLogger(BarrierDemo.class);

    public static void main(String[] args) throws InterruptedException {
        logger.info("=== CyclicBarrier vs CountDownLatch 演示开始 ===\n");

        // 场景 1：CountDownLatch - 主线程等待
        logger.info(">>> 场景 1：CountDownLatch - 赛跑发令");
        demonstrateCountDownLatch();

        // 场景 2：CyclicBarrier - 多线程互等
        logger.info("\n>>> 场景 2：CyclicBarrier - 旅游集合");
        demonstrateCyclicBarrier();

        // 场景 3：CyclicBarrier 可重用性
        logger.info("\n>>> 场景 3：CyclicBarrier 可重用（多轮游戏）");
        demonstrateReusability();

        logger.info("\n=== CyclicBarrier vs CountDownLatch 演示结束 ===");
    }

    /**
     * 场景 1：CountDownLatch - 赛跑发令
     * 所有运动员准备好后，裁判发令开始比赛
     */
    private static void demonstrateCountDownLatch() throws InterruptedException {
        int runnerCount = 5;
        CountDownLatch startSignal = new CountDownLatch(1); // 发令枪
        CountDownLatch doneSignal = new CountDownLatch(runnerCount); // 完赛计数

        logger.info("裁判：请各位运动员就位...\n");

        // 创建运动员线程
        for (int i = 1; i <= runnerCount; i++) {
            final int runnerId = i;
            new Thread(() -> {
                try {
                    logger.info("运动员-{} 就位，等待发令...", runnerId);
                    startSignal.await(); // 等待发令枪

                    // 模拟跑步
                    long runTime = (long) (Math.random() * 1000 + 500);
                    Thread.sleep(runTime);

                    logger.info("✅ 运动员-{} 冲过终点！耗时: {} ms", runnerId, runTime);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneSignal.countDown(); // 完赛计数 -1
                }
            }, "Runner-" + i).start();
        }

        Thread.sleep(1000); // 等待所有人就位

        logger.info("\n裁判：预备... 开始！🏁\n");
        startSignal.countDown(); // 发令

        doneSignal.await(); // 等待所有人完赛
        logger.info("\n✅ 裁判：比赛结束，所有运动员完赛！");
        logger.info("✅ 结论: CountDownLatch 适合主线程等待多个子任务");
    }

    /**
     * 场景 2：CyclicBarrier - 旅游集合
     * 所有游客到达集合点后，一起前往下一站
     */
    private static void demonstrateCyclicBarrier() throws InterruptedException {
        int touristCount = 4;

        // 创建栅栏，所有人到齐后执行 barrierAction
        CyclicBarrier barrier = new CyclicBarrier(touristCount, () -> {
            logger.info("\n🚌 导游：人到齐了，出发去下一站！\n");
        });

        logger.info("导游：请在景点 A 集合，人到齐一起走\n");

        // 创建游客线程
        for (int i = 1; i <= touristCount; i++) {
            final int touristId = i;
            new Thread(() -> {
                try {
                    // 模拟游玩时间不同
                    long playTime = (long) (Math.random() * 1000 + 500);
                    Thread.sleep(playTime);

                    logger.info("游客-{} 到达集合点 A", touristId);
                    barrier.await(); // 等待其他游客

                    logger.info("游客-{} 继续游玩景点 B", touristId);
                } catch (InterruptedException | BrokenBarrierException e) {
                    logger.error("游客-{} 出现异常", touristId, e);
                }
            }, "Tourist-" + i).start();
        }

        Thread.sleep(3000); // 等待演示完成
        logger.info("✅ 结论: CyclicBarrier 适合多线程互相等待");
    }

    /**
     * 场景 3：CyclicBarrier 可重用性
     * 演示 CyclicBarrier 可以重复使用（多轮游戏）
     */
    private static void demonstrateReusability() throws InterruptedException {
        int playerCount = 3;
        int rounds = 2; // 游戏轮数

        CyclicBarrier barrier = new CyclicBarrier(playerCount, () -> {
            logger.info(">>> 所有玩家准备完毕，游戏开始！\n");
        });

        for (int round = 1; round <= rounds; round++) {
            final int currentRound = round;
            logger.info("=== 第 {} 轮游戏 ===\n", currentRound);

            for (int i = 1; i <= playerCount; i++) {
                final int playerId = i;
                new Thread(() -> {
                    try {
                        Thread.sleep((long) (Math.random() * 500 + 200));
                        logger.info("[轮{}] 玩家-{} 准备完毕", currentRound, playerId);

                        barrier.await(); // 等待其他玩家

                        logger.info("[轮{}] 玩家-{} 开始游戏", currentRound, playerId);
                    } catch (InterruptedException | BrokenBarrierException e) {
                        logger.error("[轮{}] 玩家-{} 异常", currentRound, playerId, e);
                    }
                }, "Player-" + i + "-Round-" + round).start();
            }

            Thread.sleep(1500); // 等待本轮完成
        }

        Thread.sleep(500);
        logger.info("\n✅ 结论: CyclicBarrier 可重复使用，CountDownLatch 不可");
    }

    /**
     * 实际应用：并行计算任务
     * 展示如何使用 CountDownLatch 进行并行计算
     */
    static class ParallelComputation {
        private final CountDownLatch latch;
        private final int[] data;
        private long result = 0;

        public ParallelComputation(int[] data) {
            this.data = data;
            this.latch = new CountDownLatch(data.length);
        }

        /**
         * 并行计算数组和
         */
        public long compute() throws InterruptedException {
            for (int i = 0; i < data.length; i++) {
                final int index = i;
                new Thread(() -> {
                    try {
                        // 模拟复杂计算
                        Thread.sleep(100);
                        synchronized (this) {
                            result += data[index];
                        }
                        logger.info("任务-{} 完成，当前和: {}", index, result);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            latch.await(); // 等待所有任务完成
            return result;
        }
    }

    /**
     * 实际应用：分阶段任务
     * 展示如何使用 CyclicBarrier 实现分阶段并行任务
     */
    static class PhaseTask {
        private final CyclicBarrier barrier;
        private final int workerCount;

        public PhaseTask(int workerCount) {
            this.workerCount = workerCount;
            this.barrier = new CyclicBarrier(workerCount, () -> {
                logger.info(">>> 阶段完成，进入下一阶段\n");
            });
        }

        /**
         * 执行多阶段任务
         */
        public void execute(int phases) throws InterruptedException {
            for (int i = 0; i < workerCount; i++) {
                final int workerId = i;
                new Thread(() -> {
                    for (int phase = 1; phase <= phases; phase++) {
                        try {
                            logger.info("[阶段{}] Worker-{} 工作中...", phase, workerId);
                            Thread.sleep((long) (Math.random() * 500));

                            logger.info("[阶段{}] Worker-{} 完成", phase, workerId);
                            barrier.await(); // 等待其他 worker
                        } catch (InterruptedException | BrokenBarrierException e) {
                            logger.error("Worker-{} 异常", workerId, e);
                            break;
                        }
                    }
                }).start();
            }

            Thread.sleep(phases * 1000); // 等待所有阶段完成
        }
    }
}
