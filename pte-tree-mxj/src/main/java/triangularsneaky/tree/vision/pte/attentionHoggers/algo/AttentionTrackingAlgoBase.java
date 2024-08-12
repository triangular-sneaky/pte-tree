package triangularsneaky.tree.vision.pte.attentionHoggers.algo;

import triangularsneaky.tree.vision.pte.attentionHoggers.*;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import triangularsneaky.tree.vision.pte.attentionHoggers.logging.LogManager;


import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.Logger;

public abstract class AttentionTrackingAlgoBase implements Consumer<Matrix> {

    private static final java.util.logging.Logger log = LogManager.getLogger(AttentionTrackingAlgoBase.class);

    protected int attentionSpan;
    protected double sizeImportanceCoefficient;
    protected int downsamplingStep;
    DiscreteAmpAndEnvelope stabilityAmpAndEnvelope;

    protected final BehaviorSubject<Hoggers.AttentionSlot[]> _ticks = BehaviorSubject.create();
    final MemoryBackedMatrix processingMatrix = new MemoryBackedMatrix();
    AtomicInteger timestamp = new AtomicInteger(0);


    public AttentionTrackingAlgoBase(int attentionSpan, double sizeImportanceCoefficient, int downsamplingStep, LinearAmpAndADEnvelope stabilityEnvelope) {
        this.attentionSpan = attentionSpan;
        this.sizeImportanceCoefficient = sizeImportanceCoefficient;
        this.downsamplingStep = downsamplingStep;
        this.stabilityAmpAndEnvelope = stabilityEnvelope;
    }

    protected void handleMatrix(WriteableMatrix matrix ) {

        double sizeCoefficient = 1.0;
        var maxDownsamplingFactor = getMaxDownsamplingFactor(matrix);
        for (
                int downsamplingFactor = 1, prevDownsamplingFactor = 1;
                downsamplingFactor <= maxDownsamplingFactor;
                prevDownsamplingFactor = downsamplingFactor, downsamplingFactor *= downsamplingStep) {

            int ds = downsamplingFactor;
            log.fine(() -> "DOWNSAMPLING: %s".formatted(ds));
            for (int i = 0; i < matrix.dims()[0]; i += prevDownsamplingFactor) {
                for (int j = 0; j < matrix.dims()[1]; j += prevDownsamplingFactor) {
    //                if (downsamplingFactor == 1) {
    //                    int[] values = matrix.get(i, j);
    //                    taste(i, j, downsamplingFactor, downsamplingFactor, values, coefficient);
    //                }


                    int[] sum = null;

                    int downsamplingTargetI = Math.min(i + downsamplingFactor, matrix.dims()[0]);
                    int downsamplingTargetJ = Math.min(j + downsamplingFactor, matrix.dims()[1]);
                    for (int si = i; si < downsamplingTargetI; si += prevDownsamplingFactor) {
                        for (int sj = j; sj < downsamplingTargetJ; sj += prevDownsamplingFactor) {
                            var el = matrix.get(si, sj);
                            if (sum == null)
                                sum = el;
                            else {
//                                log.debug(STR."accumulating [\{si},\{sj}]~>[\{i},\{j}]");
                                for (int plane = 0; plane < matrix.planecount(); plane++) {
                                    sum[plane] += el[plane];
                                }
                            }
                        }
                    }
                    var _i=i; var _j=j; var _sum = sum;
                    log.finer(() -> "[%d,%d]->(%d,%d)".formatted(_i, _j, _sum[0], _sum[1]));
                    matrix.set(i,j, sum);
                    taste(i, j, downsamplingFactor , downsamplingFactor,
                            Objects.requireNonNull(sum),
                            sizeCoefficient );

                }
            }
            sizeCoefficient *= sizeImportanceCoefficient;

        }

//        if (Arrays.stream(matrix.dims()).allMatch(x -> x > downsamplingFactor)) {
//            handleMatrix(matrix, downsamplingFactor * downsamplingStep, sizeCoefficient * sizeImportanceCoefficient);
//        }
    }

    private int getMaxDownsamplingFactor(WriteableMatrix matrix) {
        int maxDim = Arrays.stream(matrix.dims()).max().getAsInt();
        return getMaxDownsamplingFactor(downsamplingStep, maxDim);
    }

    public static int getMaxDownsamplingFactor(int downsamplingStep, int maxDim) {
        return (int) Math.pow(downsamplingStep, Math.ceil(Math.log(maxDim)/Math.log(2)));
//        return Math.ceilDiv(maxDim, downsamplingStep) * downsamplingStep;
    }

    protected abstract void taste(int i, int j, int sizeI, int sizeJ, int[] values, double coefficient);

    public BehaviorSubject<Hoggers.AttentionSlot[]> ticks() { return _ticks;}

    record Rect(int x, int y, int w, int h) {
        public int area() {
            return w * h;
        }
        public static Rect fromIJ(int i, int j, int sizeI, int sizeJ) {
            return new Rect(i, j,sizeI,sizeJ);
        }
    }

    class AttentionElement {
        private final int id;
        private double amplitude;
        private final double valueCoefficient;
        private double angle;
        private final Rect rect;
        private final int bornTimestamp;
        private boolean isDead = false;

        AttentionElement(int id, double amplitude, double valueCoefficient, double angle, Rect rect, int bornTimestamp) {
            this.id = id;
            this.amplitude = amplitude;
            this.valueCoefficient = valueCoefficient;
            this.angle = angle;
            this.rect = rect;
            this.bornTimestamp = bornTimestamp;
        }

        public int age() {
            return timestamp.get() - bornTimestamp;
        }

        public double effectiveValue() {
            return amplitude * valueCoefficient * stabilityAmpAndEnvelope.get(age());
        }

        public double amplitude() {
            return amplitude;
        }

        public void setAmplitude(double amplitude) {
            this.amplitude = amplitude;
        }

        public void setAngle(double angle) {
            this.angle = angle;
        }

        public double angle() {
            return angle;
        }

        public Rect rect() {
            return rect;
        }

        public int bornTimestamp() {
            return bornTimestamp;
        }

        public Hoggers.AttentionSlot toAttentionSlot() {
            return new Hoggers.AttentionSlot(id, this.age(), this.rect.x, this.rect.y, this.rect.w, this.rect.h, this.rect.area(), this.amplitude, this.angle);
        }

        public boolean isDead() {
            return isDead;
        }

        public void setDead(boolean dead) {
            isDead = dead;
        }

        @Override
        public String toString() {
            return "AttentionElement(%s)[%s]@%s{value=%3.3f, effective=%3.3f, angle=%1.3f}".formatted(getCharId(), isDead ? "💀" : age(), rect, amplitude, effectiveValue(), angle);
        }

        public int getId() {
            return id;
        }

        public char getCharId() {
            return (char) (getId() + 'a');
        }
    }


    public int getAttentionSpan() {
        return attentionSpan;
    }

    public void setAttentionSpan(int attentionSpan) {
        this.attentionSpan = attentionSpan;
    }

    public double getSizeImportanceCoefficient() {
        return sizeImportanceCoefficient;
    }

    public void setSizeImportanceCoefficient(double sizeImportanceCoefficient) {
        this.sizeImportanceCoefficient = sizeImportanceCoefficient;
    }

    public int getDownsamplingStep() {
        return downsamplingStep;
    }

    public void setDownsamplingStep(int downsamplingStep) {
        this.downsamplingStep = downsamplingStep;
    }

    public DiscreteAmpAndEnvelope getStabilityAmpAndEnvelope() {
        return stabilityAmpAndEnvelope;
    }

    public void setStabilityAmpAndEnvelope(DiscreteAmpAndEnvelope stabilityAmpAndEnvelope) {
        this.stabilityAmpAndEnvelope = stabilityAmpAndEnvelope;
    }
}
