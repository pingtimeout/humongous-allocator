package fr.pingtimeout;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

public class HumongousAllocator implements Runnable
{
    public static void main(String[] args) throws IOException, InterruptedException
    {
        long heapSize = Runtime.getRuntime().totalMemory();
        Optional<Long> regionSize = ManagementFactory.getRuntimeMXBean()
            .getInputArguments()
            .stream()
            .filter(arg -> arg.startsWith("-XX:G1HeapRegionSize="))
            .map(arg -> Long.parseLong(arg.split("=")[1]))
            .findFirst();
        int humongousLongArraySize = (int) ((long) regionSize.orElse(8388608L)); // default: 8 MiB

        System.out.printf("# Heap size: %d GB%n", heapSize / 1000 / 1000 / 1000);
        System.out.printf("# G1GC Region size : %s%n", regionSize);
        System.out.printf("# Long array of %d elements should be a humongous object%n", humongousLongArraySize);

        System.out.println("# Press ENTER to start the test...");
        System.in.read();
        List<HumongousAllocator> allocators = IntStream.range(0, 8)
            .mapToObj(x -> new HumongousAllocator(humongousLongArraySize * 10, 100_000))
            .collect(toList());
        allocators.forEach(a -> new Thread(a).start());
        TimeUnit.SECONDS.sleep(1);

        System.out.println("# Press ENTER to stop the test...");
        System.in.read();
        allocators.forEach(a -> a.shouldContinue = false);
    }

    private final int humongousLongArraySize;
    private volatile boolean shouldContinue;
    private volatile long[] humongousArray;
    private volatile Map<Object, Object> cache;

    private HumongousAllocator(int humongousLongArraySize, final int cacheSize)
    {
        this.humongousLongArraySize = humongousLongArraySize;
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
        System.out.println("# Thread " + Thread.currentThread().getName() + " starting...");
        while (shouldContinue)
        {
            for (int i = 0; i < 1_000_000 && shouldContinue; i++)
            {
                for (int j = 0; j < 10; j++)
                {
                    switch (j)
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
}
