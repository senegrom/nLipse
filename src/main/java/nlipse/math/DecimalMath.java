package nlipse.math;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Minimal adaptive-precision logarithm and exponential support for rare field fallbacks. */
final class DecimalMath {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal THREE_HALVES = new BigDecimal("1.5");
    private static final BigDecimal THREE_QUARTERS = new BigDecimal("0.75");
    private static final BigDecimal FIVE_QUARTERS = new BigDecimal("1.25");
    private static final ConcurrentMap<Integer, Constants> CONSTANTS = new ConcurrentHashMap<>();

    private DecimalMath() {
    }

    static BigDecimal log(final BigDecimal value, final MathContext context) {
        if (value.signum() <= 0) {
            throw new ArithmeticException("Logarithm requires a positive value");
        }
        if (value.compareTo(ONE) == 0) {
            return ZERO;
        }
        final MathContext work = AdaptiveDecimal.guard(context);
        final Constants constants = constants(work);
        final int decimalExponent = value.precision() - value.scale() - 1;
        BigDecimal mantissa = value.scaleByPowerOfTen(-decimalExponent);
        int binaryExponent = 0;
        while (mantissa.compareTo(THREE_HALVES) > 0) {
            mantissa = mantissa.divide(TWO, work);
            binaryExponent++;
        }
        while (mantissa.compareTo(THREE_QUARTERS) < 0) {
            mantissa = mantissa.multiply(TWO, work);
            binaryExponent--;
        }
        BigDecimal result = logNearOne(mantissa, work);
        if (decimalExponent != 0) {
            result = result.add(constants.logTen().multiply(
                    BigDecimal.valueOf(decimalExponent), work), work);
        }
        if (binaryExponent != 0) {
            result = result.add(constants.logTwo().multiply(
                    BigDecimal.valueOf(binaryExponent), work), work);
        }
        return result.round(context);
    }

    static BigDecimal exp(final BigDecimal value, final MathContext context) {
        if (value.signum() == 0) {
            return ONE;
        }
        final MathContext work = AdaptiveDecimal.guard(context);
        final Constants constants = constants(work);
        final BigDecimal negligible = constants.logTen().multiply(
                BigDecimal.valueOf(-(long) work.getPrecision() - 8), work);
        if (value.compareTo(negligible) < 0) {
            return ZERO;
        }
        // No caller needs a finite decimal expansion once binary64 overflow is certain.
        if (value.compareTo(BigDecimal.valueOf(2048)) > 0) {
            return AdaptiveDecimal.exact(Double.MAX_VALUE).multiply(TWO);
        }

        final BigDecimal quotient = value.divide(constants.logTwo(), 0, RoundingMode.HALF_EVEN);
        final int binaryExponent;
        try {
            binaryExponent = quotient.intValueExact();
        } catch (final ArithmeticException tooLarge) {
            return value.signum() < 0 ? ZERO
                    : AdaptiveDecimal.exact(Double.MAX_VALUE).multiply(TWO);
        }
        final BigDecimal reduced = value.subtract(constants.logTwo().multiply(
                BigDecimal.valueOf(binaryExponent), work), work);
        final BigDecimal epsilon = BigDecimal.ONE.scaleByPowerOfTen(-work.getPrecision() - 2);
        BigDecimal term = ONE;
        BigDecimal sum = ONE;
        for (int index = 1; ; index++) {
            term = term.multiply(reduced, work).divide(BigDecimal.valueOf(index), work);
            sum = sum.add(term, work);
            if (term.abs().compareTo(epsilon) <= 0) {
                break;
            }
            if (index > work.getPrecision() * 3 + 128) {
                throw new ArithmeticException("Exponential series did not converge");
            }
        }
        if (binaryExponent > 0) {
            sum = sum.multiply(powerOfTwo(binaryExponent), work);
        } else if (binaryExponent < 0) {
            sum = sum.divide(powerOfTwo(-binaryExponent), work);
        }
        return sum.round(context);
    }

    private static BigDecimal powerOfTwo(final int exponent) {
        return new BigDecimal(BigInteger.ONE.shiftLeft(exponent));
    }

    private static Constants constants(final MathContext context) {
        return CONSTANTS.computeIfAbsent(context.getPrecision(), ignored -> {
            final MathContext work = new MathContext(context.getPrecision(), RoundingMode.HALF_EVEN);
            final BigDecimal logTwo = logNearOne(TWO, work);
            final BigDecimal logTen = logNearOne(FIVE_QUARTERS, work)
                    .add(logTwo.multiply(BigDecimal.valueOf(3), work), work);
            return new Constants(logTwo, logTen);
        });
    }

    private static BigDecimal logNearOne(final BigDecimal value, final MathContext context) {
        final BigDecimal z = value.subtract(ONE).divide(value.add(ONE), context);
        if (z.signum() == 0) {
            return ZERO;
        }
        final BigDecimal zSquared = z.multiply(z, context);
        final BigDecimal epsilon = BigDecimal.ONE.scaleByPowerOfTen(-context.getPrecision() - 2);
        BigDecimal power = z;
        BigDecimal sum = z;
        for (int index = 1; ; index++) {
            power = power.multiply(zSquared, context);
            final BigDecimal addend = power.divide(BigDecimal.valueOf(2L * index + 1), context);
            sum = sum.add(addend, context);
            if (addend.abs().compareTo(epsilon) <= 0) {
                break;
            }
            if (index > context.getPrecision() * 3 + 128) {
                throw new ArithmeticException("Logarithm series did not converge");
            }
        }
        return sum.multiply(TWO, context);
    }

    private record Constants(BigDecimal logTwo, BigDecimal logTen) {
    }
}
