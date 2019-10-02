package fr.pingtimeout;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

public class HumongousAllocator
{
    private static final int HUMONGOUS_LONG_ARRAY_SIZE = 8388608;
    private static final int NUMBER_OF_THREADS = Runtime.getRuntime().availableProcessors();
    private static final int CACHED_OBJECTS_PER_THREAD = 100_000;

    public static void main(String[] args) throws IOException, InterruptedException
    {
        System.out.printf("# Heap size: %d GB%n", Runtime.getRuntime().totalMemory() / 1000 / 1000 / 1000);
        System.out.printf("# Long array of %d elements should be a humongous object%n", HUMONGOUS_LONG_ARRAY_SIZE);
        System.out.println("# Press ENTER to start the test...");
        System.in.read();

        CountDownLatch endSignal = new CountDownLatch(NUMBER_OF_THREADS);
        List<Allocator> allocators = IntStream.range(0, NUMBER_OF_THREADS)
            .mapToObj(x -> new Allocator(HUMONGOUS_LONG_ARRAY_SIZE, CACHED_OBJECTS_PER_THREAD, endSignal))
            .collect(toList());
        Thread userInputWatcherThread = new Thread(new UserInputWatcher(allocators));

        userInputWatcherThread.start();
        allocators.forEach(a -> new Thread(a).start());

        endSignal.await();
        userInputWatcherThread.interrupt();
    }

    private static class Allocator implements Runnable
    {
        private final int humongousLongArraySize;
        private final CountDownLatch endSignal;
        private volatile boolean shouldContinue;
        private volatile long[] humongousArray;
        private volatile Map<Object, Object> cache;

        private Allocator(int humongousLongArraySize, final int cacheSize, CountDownLatch endSignal)
        {
            this.humongousLongArraySize = humongousLongArraySize;
            this.endSignal = endSignal;
            this.shouldContinue = true;
            this.humongousArray = new long[0];
            this.cache = new LinkedHashMap<Object, Object>(cacheSize + 1, .75F, true)
            {
                public boolean removeEldestEntry(Map.Entry eldest)
                {
                    return size() > cacheSize;
                }
            };
        }

        public void run()
        {
            try
            {
                System.out.println("# Thread " + Thread.currentThread().getName() + " starting...");
                for (int i = 0; i < 10 && shouldContinue; i++)
                {
                    for (int j = 0; j < 1_000_000; j++)
                    {
                        switch (j % 10)
                        {
                            case 0:
                                cache.put(new Byte((byte) j), new Byte((byte) j));
                                break;
                            case 1:
                                cache.put(new Float(j), new Float(j));
                                break;
                            case 2:
                                cache.put(new BigDecimal(j), new BigDecimal(j));
                                break;
                            case 3:
                                cache.put(new AtomicLong(j), new AtomicLong(j));
                                break;
                            case 4:
                                cache.put(new Long(j), new Long(j));
                                break;
                            case 5:
                                cache.put(new Double(j), new Double(j));
                                break;
                            case 6:
                                cache.put(new AtomicInteger(j), new AtomicInteger(j));
                                break;
                            case 7:
                                cache.put(new Short((short) j), new Short((short) j));
                                break;
                            case 8:
                                cache.put(new BigInteger(String.valueOf(j)), new BigInteger(String.valueOf(j)));
                                break;
                            case 9:
                                cache.put(new Integer(String.valueOf(j)), new Integer(String.valueOf(j)));
                                break;
                        }
                    }

                    if (shouldContinue)
                    {
                        System.out.printf("# Thread %s allocating a humongous object of %d MB...%n",
                            Thread.currentThread().getName(),
                            humongousLongArraySize * 8 / 1000 / 1000);
                        humongousArray = new long[humongousLongArraySize];
                    }
                }
                System.out.println("# Thread " + Thread.currentThread().getName() + " finished...");
            }
            finally
            {
                endSignal.countDown();
            }
        }
    }

    private static class UserInputWatcher implements Runnable
    {
        private final List<Allocator> allocators;

        private UserInputWatcher(List<Allocator> allocators)
        {
            this.allocators = allocators;
        }

        @Override
        public void run()
        {
            try
            {
                System.out.println("# Press ENTER to stop the test before it completes...");
                int readValue = -1;
                while (!Thread.interrupted() && readValue == -1)
                {
                    if (System.in.available() == 0)
                    {
                        TimeUnit.MILLISECONDS.sleep(100);
                    }
                    else
                    {
                        readValue = System.in.read();
                    }
                }
                allocators.forEach(a -> a.shouldContinue = false);
            }
            catch (IOException | InterruptedException ignored)
            {
            }
        }
    }
}
