import com.skylarkarms.compactcollections.CompactArrayBuilder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ConcurrentTest {
    private static final String TAG = "ConcurrentTest";
    static final int size = 100_001;
    public static void main(String[] args) {
        final ExecutorService pool = Executors.newFixedThreadPool(1_000);
        record Rec(int i, String s){}
        Rec[] base = new Rec[size];
        Arrays.setAll(base, i -> new Rec(i, "ID = ".concat(String.valueOf(i))));
        CompactArrayBuilder<Rec> receipt = CompactArrayBuilder.atomic(100, Rec[]::new);
        for (int i = 0; i < size; i++) {
            int finalI = i;
            pool.execute(
                    () -> {
                        Rec cur = base[finalI];
                        receipt.add(cur);
                        if (finalI == (size - 1)) {
                            try {
                                Thread.sleep(200);
                                Rec[] pub = receipt.publish();
                                System.err.println(
                                        "Array = " + Arrays.toString(pub)
                                        + "\n\n >> size = " + pub.length
                                );
                                pool.shutdown();
                                Set<Rec> checker = new HashSet<>(size);
                                for (Rec r:pub
                                     ) {
                                    if (!checker.add(r)) throw new IllegalStateException("Already contained...");
                                }

                                int y = 0;
                                for (Rec rec:receipt
                                     ) {
                                    System.out.println(Integer.toString(y++).concat(",".concat(String.valueOf(rec))));
                                }
                                System.out.println("\n\n >>> LAST = " + cur);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
            );
        }
    }
}
