package com.hathoute.kubernetes.operator.openhands.util;

import static java.util.Objects.requireNonNull;

public record Either<L, R>(L left, R right) {

  public static <L, R> Either<L, R> left(final L left) {
    return new Either<>(requireNonNull(left), null);
  }

  public static <L, R> Either<L, R> right(final R right) {
    return new Either<>(null, requireNonNull(right));
  }

  public boolean isLeft() {
    return left != null;
  }

  public boolean isRight() {
    return right != null;
  }
}
