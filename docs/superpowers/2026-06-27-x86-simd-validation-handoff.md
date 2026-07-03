# x86 SIMD Validation Handoff — Iris 2D Simplex Noise Kernel

**Paste this whole file to the Claude Code session on the x86 PC.** It is a self-contained task. The Mac that built this is Apple Silicon (NEON, 2 lanes, no hardware gather), where the new vectorized noise kernel is a measured *loss* and is deliberately gated OFF. This machine (x86 with AVX2 = 4 lanes or AVX-512 = 8 lanes, with hardware gather) is where the kernel is expected to *win*. Your job is to verify (a) it is still **bit-exact** at 4/8 lanes and (b) **how much faster** it actually is.

---

## Background (what exists)

A determinism-safe, coordinate-parallel (structure-of-arrays) `jdk.incubator.vector` kernel for 2D FBM simplex noise was added to Iris, with a scalar fallback:

- `core/src/main/java/art/arcane/iris/util/simd/NoiseKernels2D.java` — interface
- `core/src/main/java/art/arcane/iris/util/simd/ScalarNoiseKernels2D.java` — scalar reference (mirrors `FastNoiseDouble`)
- `core/src/main/java/art/arcane/iris/util/simd/VectorNoiseKernels2D.java` — Vector-API impl; `profitable() = lanesAligned() && DoubleVector.SPECIES_PREFERRED.length() >= 4`
- `core/src/main/java/art/arcane/iris/util/simd/SimdSupport.java` — `noiseKernels2D()` selects vector only when `profitable()`, else scalar
- `core/src/test/java/art/arcane/iris/util/simd/NoiseKernels2DParityTest.java` — 6 bit-exact (`0D`) parity tests vs `FastNoiseDouble`

Every lane computes one coordinate's identical scalar op sequence, so results are bit-identical to scalar by construction — **but this has only ever executed at 2 lanes.** You are the first to run it at 4/8.

Java 25 is required. The Gradle test JVM already adds `--add-modules jdk.incubator.vector` (in `core/build.gradle`).

---

## Your tasks (run from the Iris project root)

### 1. Confirm the host actually vectorizes ≥4 lanes

Run this throwaway check (or infer from step 2's skip count):

```bash
cat > /tmp/Lanes.java <<'EOF'
import jdk.incubator.vector.DoubleVector;
public class Lanes {
  public static void main(String[] a){
    System.out.println("arch="+System.getProperty("os.arch")
      +" doubleLanes="+DoubleVector.SPECIES_PREFERRED.length()
      +" shape="+DoubleVector.SPECIES_PREFERRED.vectorShape());
  }
}
EOF
java --add-modules jdk.incubator.vector /tmp/Lanes.java
```

Expect `doubleLanes=4` (AVX2) or `8` (AVX-512). If it prints `2`, this host is NOT a wider-vector machine and the rest of the validation won't be meaningful — report that and stop.

### 2. Bit-exactness at 4/8 lanes (the determinism gate)

```bash
./gradlew :core:test --tests 'art.arcane.iris.util.simd.NoiseKernels2DParityTest' --rerun-tasks
```

PASS criteria — read `core/build/test-results/test/TEST-art.arcane.iris.util.simd.NoiseKernels2DParityTest.xml`:
- `tests="6" failures="0" errors="0" skipped="0"`
- **`skipped="0"` is critical**: the vector parity tests are guarded by `assumeTrue(VectorNoiseKernels2D.lanesAligned())`. On this host they MUST run (not skip), exercising the vector path at this host's lane width. A skip means the vector path didn't execute — investigate.
- Any single non-zero delta is a determinism failure: the JDK's AVX2/AVX-512 `D2L`/`L2D`/mask-cast/gather intrinsics would have diverged from scalar. That is a hard blocker — capture the exact failing test, octave, index, expected vs actual.

### 3. Measure the real speedup

The benchmark harness was removed from the Mac (it was a measurement, not shipped). Re-create it here, run it, record the number, then delete it.

Create `core/src/test/java/art/arcane/iris/util/simd/NoiseKernels2DBenchmarkHarness.java`:

```java
package art.arcane.iris.util.simd;

import art.arcane.volmlib.util.math.RNG;
import org.junit.Test;

public class NoiseKernels2DBenchmarkHarness {
    @Test
    public void benchmark() {
        if (!Boolean.getBoolean("noise.bench")) {
            return;
        }
        int length = 256;
        int octaves = 4;
        double[] xs = new double[length];
        double[] zs = new double[length];
        double[] out = new double[length];
        RNG rng = new RNG(5L);
        for (int k = 0; k < length; k++) {
            xs[k] = (rng.nextDouble() - 0.5D) * 1_000_000D;
            zs[k] = (rng.nextDouble() - 0.5D) * 1_000_000D;
        }
        NoiseKernels2D scalar = new ScalarNoiseKernels2D();
        NoiseKernels2D vector = new VectorNoiseKernels2D();
        long scalarNs = time(scalar, octaves, xs, zs, out, length);
        long vectorNs = time(vector, octaves, xs, zs, out, length);
        System.out.println("BENCH lanes=" + VectorNoiseKernels2D.profitable()
                + " doubleLanes=" + jdk.incubator.vector.DoubleVector.SPECIES_PREFERRED.length()
                + " scalarNsPerCol=" + scalarNs + " vectorNsPerCol=" + vectorNs
                + " speedup=" + String.format("%.2f", (double) scalarNs / (double) vectorNs));
    }

    private static long time(NoiseKernels2D kernel, int octaves, double[] xs, double[] zs, double[] out, int length) {
        for (int w = 0; w < 20_000; w++) {
            kernel.simplexFractalFBM(123L, octaves, 0.01D, 2.0D, 0.5D, 0.6667D, xs, zs, out, length);
        }
        long start = System.nanoTime();
        int iters = 200_000;
        for (int it = 0; it < iters; it++) {
            kernel.simplexFractalFBM(123L, octaves, 0.01D, 2.0D, 0.5D, 0.6667D, xs, zs, out, length);
        }
        long elapsed = System.nanoTime() - start;
        return elapsed / ((long) iters * (long) length);
    }
}
```

Add this temporary block INSIDE the existing `tasks.named('test', Test) { ... }` in `core/build.gradle` (use the Edit tool; do not disturb the existing `jvmArgs('--add-modules', 'jdk.incubator.vector')` line):

```groovy
    if (project.hasProperty('noise.bench')) {
        systemProperty('noise.bench', project.property('noise.bench'))
        testLogging { showStandardStreams = true }
    }
```

Run:

```bash
./gradlew :core:test --tests 'art.arcane.iris.util.simd.NoiseKernels2DBenchmarkHarness' -Pnoise.bench=true
```

Capture the `BENCH ... speedup=...` line.

Then CLEAN UP (do not commit either): delete the harness file and remove ONLY the `noise.bench` lines you added from `core/build.gradle` (edit them out by hand — do NOT `git checkout`/`git restore` the file, there may be other uncommitted work). Confirm with `git diff HEAD -- core/build.gradle` showing no diff.

---

## Report back to the Mac (this exact info)

1. `os.arch` and `doubleLanes` (4 or 8) from step 1.
2. Step 2 parity result: the `tests/failures/errors/skipped` counts. (Must be `6/0/0/0`.) If any failure: the failing test name, octave, index, expected vs actual.
3. Step 3 `BENCH` line: `scalarNsPerCol`, `vectorNsPerCol`, `speedup`.
4. Confirmation the benchmark harness was deleted and `core/build.gradle` restored to no-diff.

That's it — do NOT wire the kernel into world generation, do NOT commit, do NOT touch any unrelated modified files in the working tree. This is a measurement-only task.

---

## What the numbers decide (context for whoever relays this)

- **Parity 6/0/0/0 with 0 skips** → the kernel is bit-exact at this lane width; the determinism gate holds on x86. Required before it can ever be wired into generation.
- **speedup > 1** (ideally ≥1.5–2× at 4 lanes, more at 8) → confirms the kernel is worth wiring into the live noise pipeline (Phase 2: batch the composite height/biome/cave noise paths, which is the deep part). Noise is ~33% of generation CPU, so a real per-eval win translates to a meaningful pregen/gen throughput gain.
- **speedup ≤ 1 even here** → the approach doesn't pay even with hardware gather + wide lanes; do not wire it; revisit a different optimization (e.g. the ~14% NMS chunk-write cost) or GPU compute.
